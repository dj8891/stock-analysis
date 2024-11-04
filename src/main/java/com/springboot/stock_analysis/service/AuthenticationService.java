package com.springboot.stock_analysis.service;

import com.springboot.stock_analysis.enums.RoleTypes;
import com.springboot.stock_analysis.model.User;
import com.springboot.stock_analysis.service.JwtService;
import com.springboot.stock_analysis.repository.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {
  private final UserRepository userRepository;

  private final PasswordEncoder passwordEncoder;
  private final AuthenticationManager authenticationManager;
  private final JwtService jwtService;

  public AuthenticationService(
      UserRepository userRepository,
      AuthenticationManager authenticationManager,
      PasswordEncoder passwordEncoder,
      JwtService jwtService) {
    this.authenticationManager = authenticationManager;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
  }

  public String register(User request) {
    User user = new User()
        .setEmail(request.getEmail())
        .setPassword(passwordEncoder.encode(request.getPassword())).setRole(RoleTypes.USER);
    userRepository.save(user);
    return jwtService.generateToken(user);
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