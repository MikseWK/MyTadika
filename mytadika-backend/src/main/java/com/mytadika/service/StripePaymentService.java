package com.mytadika.service;

import com.mytadika.model.Fee;
import com.mytadika.repository.FeeRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class StripePaymentService {

    private final FeeRepository feeRepository;
    private final FeeService feeService;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    public StripePaymentService(FeeRepository feeRepository, FeeService feeService) {
        this.feeRepository = feeRepository;
        this.feeService = feeService;
    }

    // Creates a Stripe-hosted Checkout Session for one fee and returns the URL to redirect to.
    public String createCheckoutSession(Long feeId) throws StripeException {
        Fee fee = feeRepository.findById(feeId)
                .orElseThrow(() -> new IllegalArgumentException("Fee record not found"));
        if ("PAID".equals(fee.getStatus()))
            throw new IllegalArgumentException("This fee is already paid");

        double lateFee = fee.getLateFeeAmount() != null ? fee.getLateFeeAmount() : 0.0;
        double total = fee.getAmount() + lateFee;
        // Stripe rejects MYR charges below RM2.00 — surface a clear message instead of a raw gateway error.
        if (total < 2.0)
            throw new IllegalArgumentException("This fee (RM " + String.format("%.2f", total)
                    + ") is below the RM2.00 minimum for online payment. Please contact the school to pay this another way.");
        long amountInCents = Math.round(total * 100);

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .setSuccessUrl(baseUrl + "/parent/parentfees.html?stripe_session_id={CHECKOUT_SESSION_ID}&studentId=" + fee.getStudentId())
                .setCancelUrl(baseUrl + "/parent/parentfees.html?payment=canceled&studentId=" + fee.getStudentId())
                .putMetadata("feeId", String.valueOf(fee.getId()))
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency("myr")
                                .setUnitAmount(amountInCents)
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(fee.getDescription())
                                        .build())
                                .build())
                        .build())
                .build();

        Session session = Session.create(params);
        return session.getUrl();
    }

    // Called when the parent is redirected back from Stripe's success_url. Confirms the
    // session actually paid and marks the fee PAID if the webhook hasn't already done so —
    // this is what makes the flow work end-to-end on localhost with no public webhook URL.
    public Map<String, Object> confirmSession(String sessionId) throws StripeException {
        Session session = Session.retrieve(sessionId);
        Long feeId = Long.valueOf(session.getMetadata().get("feeId"));
        Fee fee = feeRepository.findById(feeId)
                .orElseThrow(() -> new IllegalArgumentException("Fee record not found"));

        boolean paid = "paid".equals(session.getPaymentStatus());
        if (paid && !"PAID".equals(fee.getStatus())) {
            feeService.markPaid(feeId, "STRIPE");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("feeId", feeId);
        result.put("paid", paid);
        return result;
    }

    // Production-correct path: Stripe calls this directly once the payment completes.
    // Requires a public webhook URL registered in the Stripe Dashboard (or `stripe listen`
    // locally) — not required for the demo flow above to work.
    public void handleWebhookEvent(String payload, String sigHeader) throws SignatureVerificationException {
        Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        if (!"checkout.session.completed".equals(event.getType())) return;

        StripeObject obj = event.getDataObjectDeserializer().getObject()
                .orElseGet(() -> {
                    try {
                        return event.getDataObjectDeserializer().deserializeUnsafe();
                    } catch (Exception e) {
                        return null;
                    }
                });
        if (!(obj instanceof Session session)) return;

        String feeIdStr = session.getMetadata() != null ? session.getMetadata().get("feeId") : null;
        if (feeIdStr == null) return;

        Long feeId = Long.valueOf(feeIdStr);
        feeRepository.findById(feeId).ifPresent(fee -> {
            if (!"PAID".equals(fee.getStatus())) feeService.markPaid(feeId, "STRIPE");
        });
    }
}
