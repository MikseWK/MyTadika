package com.mytadika.controller;

import com.mytadika.service.StripePaymentService;
import com.mytadika.service.ToyyibPayService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final StripePaymentService stripePaymentService;
    private final ToyyibPayService toyyibPayService;

    public PaymentController(StripePaymentService stripePaymentService, ToyyibPayService toyyibPayService) {
        this.stripePaymentService = stripePaymentService;
        this.toyyibPayService = toyyibPayService;
    }

    @PostMapping("/checkout-session/{feeId}")
    public ResponseEntity<?> createCheckoutSession(@PathVariable Long feeId) {
        try {
            String url = stripePaymentService.createCheckoutSession(feeId);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (StripeException e) {
            return ResponseEntity.status(502).body(Map.of("error", "Payment gateway error: " + e.getMessage()));
        }
    }

    @GetMapping("/checkout-session/{sessionId}/confirm")
    public ResponseEntity<?> confirmSession(@PathVariable String sessionId) {
        try {
            return ResponseEntity.ok(stripePaymentService.confirmSession(sessionId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (StripeException e) {
            return ResponseEntity.status(502).body(Map.of("error", "Payment gateway error: " + e.getMessage()));
        }
    }

    // Only reachable if a public URL is registered in the Stripe Dashboard (or via
    // `stripe listen --forward-to`) — the confirm endpoint above covers the local demo path.
    @PostMapping("/webhook")
    public ResponseEntity<?> webhook(@RequestBody String payload,
                                      @RequestHeader("Stripe-Signature") String sigHeader) {
        try {
            stripePaymentService.handleWebhookEvent(payload, sigHeader);
            return ResponseEntity.ok().build();
        } catch (SignatureVerificationException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid signature"));
        }
    }

    @PostMapping("/toyyibpay/bill/{feeId}")
    public ResponseEntity<?> createToyyibPayBill(@PathVariable Long feeId) {
        try {
            String url = toyyibPayService.createBill(feeId);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(502).body(Map.of("error", "Payment gateway error: " + e.getMessage()));
        }
    }

    @GetMapping("/toyyibpay/confirm")
    public ResponseEntity<?> confirmToyyibPayBill(@RequestParam String billCode, @RequestParam Long feeId) {
        try {
            return ResponseEntity.ok(toyyibPayService.confirmBill(billCode, feeId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Only reachable if a public callback URL is registered — the confirm endpoint above
    // covers the local demo path.
    @PostMapping("/toyyibpay/callback")
    public ResponseEntity<?> toyyibPayCallback(@RequestParam Map<String, String> params) {
        toyyibPayService.handleCallback(params);
        return ResponseEntity.ok().build();
    }
}
