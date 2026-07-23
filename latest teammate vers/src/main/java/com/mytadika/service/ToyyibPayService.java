package com.mytadika.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mytadika.model.Fee;
import com.mytadika.repository.FeeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

// Second payment gateway option (alongside Stripe) — ToyyibPay is a Malaysian FPX/card
// gateway with a free sandbox at dev.toyyibpay.com. See application.properties for setup.
@Service
public class ToyyibPayService {

    private final FeeRepository feeRepository;
    private final FeeService feeService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.base-url}")
    private String appBaseUrl;

    @Value("${toyyibpay.base-url}")
    private String toyyibBaseUrl;

    @Value("${toyyibpay.user-secret-key}")
    private String userSecretKey;

    @Value("${toyyibpay.category-code}")
    private String categoryCode;

    public ToyyibPayService(FeeRepository feeRepository, FeeService feeService) {
        this.feeRepository = feeRepository;
        this.feeService = feeService;
    }

    // Creates a ToyyibPay bill and returns the hosted payment page URL to redirect to.
    public String createBill(Long feeId) {
        Fee fee = feeRepository.findById(feeId)
                .orElseThrow(() -> new IllegalArgumentException("Fee record not found"));
        if ("PAID".equals(fee.getStatus()))
            throw new IllegalArgumentException("This fee is already paid");

        double lateFee = fee.getLateFeeAmount() != null ? fee.getLateFeeAmount() : 0.0;
        double total = fee.getAmount() + lateFee;
        // ToyyibPay's sandbox also rejects amounts that are effectively zero/negative.
        if (total < 1.0)
            throw new IllegalArgumentException("This fee (RM " + String.format("%.2f", total)
                    + ") is too small for online payment. Please contact the school to pay this another way.");
        long amountInCents = Math.round(total * 100);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("userSecretKey", userSecretKey);
        form.add("categoryCode", categoryCode);
        form.add("billName", sanitize(fee.getDescription(), 30));
        form.add("billDescription", sanitize(fee.getDescription() + " MyTadika fee payment", 100));
        form.add("billPriceSetting", "1");
        form.add("billPayorInfo", "1");
        form.add("billAmount", String.valueOf(amountInCents));
        // Deliberately no query string of our own here — ToyyibPay appends status_id/billcode/order_id
        // itself, and we recover which fee this was from order_id (== billExternalReferenceNo below).
        form.add("billReturnUrl", appBaseUrl + "/parent/parentfees.html");
        form.add("billExternalReferenceNo", String.valueOf(fee.getId()));
        form.add("billTo", "Parent");
        form.add("billEmail", "parent@example.com");
        form.add("billPhone", "0123456789");
        form.add("billPaymentChannel", "2"); // 2 = FPX + card

        String response = post("/index.php/api/createBill", form);
        String billCode = extractBillCode(response);
        if (billCode == null)
            throw new IllegalStateException("ToyyibPay did not return a bill code: " + response);

        return toyyibBaseUrl + "/" + billCode;
    }

    // Called when the parent is redirected back from ToyyibPay. Verifies the bill's real
    // payment status via getBillTransactions rather than trusting the redirect's query
    // params directly (those could be tampered with client-side).
    public Map<String, Object> confirmBill(String billCode, Long feeId) {
        Fee fee = feeRepository.findById(feeId)
                .orElseThrow(() -> new IllegalArgumentException("Fee record not found"));

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("userSecretKey", userSecretKey);
        form.add("billCode", billCode);

        boolean paid = false;
        try {
            String response = post("/index.php/api/getBillTransactions", form);
            JsonNode arr = objectMapper.readTree(response);
            if (arr.isArray() && !arr.isEmpty()) {
                JsonNode txn = arr.get(0);
                String status = firstNonBlank(txn, "billpaymentStatus", "billPaymentStatus", "status");
                paid = "1".equals(status);
            }
        } catch (Exception ignored) { }

        if (paid && !"PAID".equals(fee.getStatus())) {
            feeService.markPaid(feeId, "TOYYIBPAY");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("feeId", feeId);
        result.put("paid", paid);
        return result;
    }

    // Production-correct path: ToyyibPay calls this server-to-server on payment completion.
    // Requires a public callback URL — not required for the demo flow above to work.
    public void handleCallback(Map<String, String> params) {
        String status = params.get("status");
        String orderId = params.get("order_id");
        if (!"1".equals(status) || orderId == null) return;

        Long feeId = Long.valueOf(orderId);
        feeRepository.findById(feeId).ifPresent(fee -> {
            if (!"PAID".equals(fee.getStatus())) feeService.markPaid(feeId, "TOYYIBPAY");
        });
    }

    private String post(String path, MultiValueMap<String, String> form) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);
        return restTemplate.postForObject(toyyibBaseUrl + path, request, String.class);
    }

    private String extractBillCode(String response) {
        try {
            JsonNode arr = objectMapper.readTree(response);
            if (arr.isArray() && !arr.isEmpty()) {
                return firstNonBlank(arr.get(0), "BillCode", "billCode", "bill_code");
            }
        } catch (Exception ignored) { }
        return null;
    }

    private String firstNonBlank(JsonNode node, String... fields) {
        for (String f : fields) {
            JsonNode v = node.get(f);
            if (v != null && !v.isNull() && !v.asText().isBlank()) return v.asText();
        }
        return null;
    }

    // ToyyibPay only allows alphanumeric, space and underscore in billName/billDescription.
    private String sanitize(String text, int maxLen) {
        String cleaned = (text == null ? "" : text).replaceAll("[^a-zA-Z0-9 _]", "").trim();
        if (cleaned.isEmpty()) cleaned = "MyTadika fee";
        return cleaned.length() > maxLen ? cleaned.substring(0, maxLen) : cleaned;
    }
}
