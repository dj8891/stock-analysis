package com.springboot.stock_analysis.controller;

import com.springboot.stock_analysis.dto.LoginResponseDTO;
import com.springboot.stock_analysis.dto.RegisterRequestDTO;
import com.springboot.stock_analysis.enums.RoleTypes;
import com.springboot.stock_analysis.model.User;
import com.springboot.stock_analysis.service.AuthenticationService;
import com.springboot.stock_analysis.service.JwtService;
import com.springboot.stock_analysis.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@CrossOrigin("*")
public class LoginController {

    private final AuthenticationService authenticationService;
    private final JwtService jwtService;
    private final UserService userService;
    public LoginController(AuthenticationService
                           authenticationService, JwtService jwtService, UserService userService) {
        this.authenticationService = authenticationService;
        this.jwtService = jwtService;
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody RegisterRequestDTO user) {
        return authenticationService.register(user);
    }

    @PostMapping(path = "/login")
    public ResponseEntity<?> login(@RequestBody User loginUserDto) {
        try {
            boolean isTrialEnded = userService.checkPermission(loginUserDto.getEmail());
            User authenticatedUser = authenticationService.authenticate(loginUserDto);
            if(authenticatedUser != null) {
                String jwtToken = jwtService.generateToken(authenticatedUser);
                LoginResponseDTO response = new LoginResponseDTO(jwtToken, isTrialEnded);
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Not Found User");
            }
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            // Catch unexpected errors and log them
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An error occurred during login");
        }
    }

    @PostMapping(path = "/subscribe-check")
    public ResponseEntity<Boolean> subScribeCheck(@RequestBody Map<String, Object> data) {
        String email = (String) data.get("email");
        Boolean isSubscribe = userService.subScribeCheck(email);
        return ResponseEntity.ok(isSubscribe);
    }

    @PostMapping(path = "/setsubscribe")
    public ResponseEntity<String> setSubScribe(@RequestParam("email") String email, @RequestParam("subscribe") Boolean subscribe) {
        boolean result = userService.setSubScribe(email, subscribe);
        if (result) {
            return ResponseEntity.ok("SubScribe has been set successfully");
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid or expired token");
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout() {
        SecurityContextHolder.getContext().setAuthentication(null);
        return ResponseEntity.ok("Successfully logged out");
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestParam String email) {
        userService.generatePasswordResetToken(email);
        return ResponseEntity.ok("Password reset link sent to your email");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestParam("email") String token, @RequestParam("newPassword") String newPassword) {
        boolean result = userService.resetPassword(token, newPassword);
        if (result) {
            return ResponseEntity.ok("Password has been reset successfully");
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid or expired token");
        }
    }
}
