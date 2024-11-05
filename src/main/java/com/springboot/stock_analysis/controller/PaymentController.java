package com.springboot.stock_analysis.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.springboot.stock_analysis.dto.CheckoutRequestDto;
import com.springboot.stock_analysis.model.User;
import com.springboot.stock_analysis.repository.UserRepository;
import com.springboot.stock_analysis.service.JwtService;
import com.springboot.stock_analysis.service.StripeService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payment")
@CrossOrigin("*")
public class PaymentController {

    private final StripeService stripeService;
    private final UserRepository userRepository;
    private JwtService jwtService;

    public PaymentController(StripeService stripeService, UserRepository userRepository, JwtService jwtService) {
        this.stripeService = stripeService;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @PostMapping("/subscribe")
    public ResponseEntity<String> createCheckoutSession(@RequestBody CheckoutRequestDto checkoutRequestDto,
                                         @RequestHeader("Authorization") String token) throws Exception {
        String jwt = token.substring(7);
        String email = jwtService.extractEamil(jwt);
        Session session = stripeService.createCheckoutSession(checkoutRequestDto, email);
        if(session != null) return ResponseEntity.ok(session.getId());
        else return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Create Customer Failed");
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebHook(@RequestBody String payload, @RequestHeader("Stripe-Signature") String sigHeader) throws JsonProcessingException {
        return this.stripeService.handleEventHook(payload, sigHeader);
    }

    @GetMapping("/is-subscribed")
    public ResponseEntity<User> isSubscribed(@RequestHeader("Authorization") String token) {
        String jwt = token.substring(7);
        String email = jwtService.extractEamil(jwt);
        return ResponseEntity.ok(this.userRepository.findByEmail(email).orElseThrow());
    }

    @DeleteMapping("/cancel-subscription/{subId}")
    public ResponseEntity<String> cancelSubscription(@PathVariable String subId) throws StripeException {
        try {
            stripeService.cancelSubscription(subId);
            return ResponseEntity.ok("success");
        } catch (StripeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Not Found Such Subscription");
        }
    }
}
