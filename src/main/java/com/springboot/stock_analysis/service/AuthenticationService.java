package com.springboot.stock_analysis.service;

import com.springboot.stock_analysis.dto.RegisterRequestDTO;
import com.springboot.stock_analysis.enums.RoleTypes;
import com.springboot.stock_analysis.model.User;
import com.springboot.stock_analysis.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

@Service
public class AuthenticationService {
  private final UserRepository userRepository;
  private final PromoService promoService;
  private final PasswordEncoder passwordEncoder;
  private final AuthenticationManager authenticationManager;
  private final JwtService jwtService;

  public AuthenticationService(
      UserRepository userRepository,
      AuthenticationManager authenticationManager,
      PasswordEncoder passwordEncoder,
      PromoService promoService,
      JwtService jwtService) {
    this.authenticationManager = authenticationManager;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.promoService = promoService;
  }

  public ResponseEntity<String> register(RegisterRequestDTO request) {
    if(userRepository.existsByEmail(request.getEmail())) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Account Exists");
    } else {
      Long expireDate = 0L;
        if(request.getPromoCode() != null && !request.getPromoCode().isEmpty()) {
          try {
            expireDate = this.promoService.validatePromoCode(request.getPromoCode()).getExpiryDate();
          }
          catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("promo code invalid");
          }
        } else expireDate = 7L;

        User user = new User()
                .setEmail(request.getEmail())
                .setPassword(passwordEncoder.encode(request.getPassword()))
                .setRole(RoleTypes.FREE)
                .setExpiryDate(LocalDate.now().plusDays(expireDate))
                .setSubscribe(request.getSubscribe());

        userRepository.save(user);
        return ResponseEntity.ok(jwtService.generateToken(user));
      }
    }

  public User authenticate(User input) {
    try {
      Authentication auth = authenticationManager.authenticate(
          new UsernamePasswordAuthenticationToken(
              input.getEmail(),
              input.getPassword()
          )
      );
    } catch (AuthenticationException e) {
      return null;
    }
    return userRepository.findByEmail(input.getEmail())
        .orElseThrow();
  }
}