package com.springboot.stock_analysis.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springboot.stock_analysis.dto.CheckoutRequestDto;
import com.springboot.stock_analysis.enums.RoleTypes;
import com.springboot.stock_analysis.model.User;
import com.springboot.stock_analysis.repository.UserRepository;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class StripeService {

    @Value("${free-trial}")
    Long freeTrial;

    @Value("${stripe.webhook.secret}")
    String webhookSecret;

    private final UserRepository userRepository;

    @Autowired
    public StripeService(UserRepository userRepository, @Value("${stripe.secret.key}") String stripeSecretKey) {
        this.userRepository = userRepository;
        Stripe.apiKey = stripeSecretKey;
    }

    private void updateSecurityContext(User user) {
        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                user.getAuthorities()
        );

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userDetails,
                userDetails.getPassword(),
                userDetails.getAuthorities()
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String handleSubscriptionEvent(Event event, int status) throws JsonProcessingException {
        String data = event.getData().toJson();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode actualObj = mapper.readTree(data);
        String customer = actualObj.path("object").path("customer").asText();
        Optional<User> user = this.userRepository.findByCusId(customer);
        if(user.isPresent()) {
            User optionalUser = user.get();
            optionalUser.setPlanStatus(status);
            if(status == 1) optionalUser.setRole(RoleTypes.PREMIUM);
            else if(status == 0) optionalUser.setRole(RoleTypes.USER);
            this.userRepository.save(optionalUser);
            this.updateSecurityContext(optionalUser);
            return "SUCCESS";
        } else {
            return "Not Found CUSTOMER";
        }
    }

    public ResponseEntity<String> handleEventHook(String payload, String sigHeader) throws JsonProcessingException {
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Webhook error: " + e.getMessage());
        }

        String response = switch (event.getType()) {
            case "payment_intent.succeeded" -> this.handleSubscriptionEvent(event, 1);
            case "payment_intent.payment_failed", "payment_intent.canceled" -> this.handleSubscriptionEvent(event, 0);
//            case "customer.subscription.trial_will_end" -> this.handleSubscriptionEvent(event, 2);
            default -> {
                System.out.println("Invalid event type: " + event.getType());
                yield ("Unhandled Event Type: " + event.getType());
            }
        };
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    public Session createCheckoutSession(CheckoutRequestDto checkoutRequestDto, String email) throws Exception {
        String priceId = checkoutRequestDto.getPriceId();
        String successUrl = checkoutRequestDto.getSuccessUrl();
        String cancelUrl = checkoutRequestDto.getCancelUrl();
        String plan = checkoutRequestDto.getPlan();
        Optional<User> user = this.userRepository.findByEmail(email);
        if(user.isPresent()) {
            User optionalUser = user.get();
            String customerId = optionalUser.getCusId();
            System.out.println("customerId Error: " + customerId);
            if(customerId.isEmpty()) {
                try {
                    CustomerCreateParams params = CustomerCreateParams.builder()
                            .setEmail(email)
                            .build();
                    customerId = Customer.create(params).getId();
                    optionalUser.setCusId(customerId);
                    this.userRepository.save(optionalUser);
                } catch (Exception e) {
                    System.out.println("Create Customer Failed: " + e.getMessage());
                    return null;
                }
            }
            SessionCreateParams params;
            if(plan.equals("free")) {
                params = SessionCreateParams.builder()
                        .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                        .setCustomer(customerId)
                        .setSuccessUrl(successUrl)
                        .setCancelUrl(cancelUrl)
                        .setSubscriptionData(SessionCreateParams.SubscriptionData.builder()
                                .setTrialPeriodDays(freeTrial).build())
                        .addLineItem(
                                SessionCreateParams.LineItem.builder()
                                        .setQuantity(1L)
                                        .setPrice(priceId)
                                        .build()
                        ).build();
            } else {
                params = SessionCreateParams.builder()
                        .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                        .setCustomer(customerId)
                        .setSuccessUrl(successUrl)
                        .setCancelUrl(cancelUrl)
                        .addLineItem(
                                SessionCreateParams.LineItem.builder()
                                        .setQuantity(1L)
                                        .setPrice(priceId)
                                        .build()
                        ).build();
            }

            return Session.create(params);
        } else {
            return null;
        }
    }
}
