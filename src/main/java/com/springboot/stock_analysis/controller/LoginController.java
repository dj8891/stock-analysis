package com.springboot.stock_analysis.controller;

import com.springboot.stock_analysis.model.User;
import com.springboot.stock_analysis.service.AuthenticationService;
import com.springboot.stock_analysis.service.JwtService;
import com.springboot.stock_analysis.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<String> register(@RequestBody User user) {
        return authenticationService.register(user);
    }

    @PostMapping(path = "/login")
    public ResponseEntity<String> login(@RequestBody User loginUserDto) {
        User authenticatedUser = authenticationService.authenticate(loginUserDto);
        if(authenticatedUser != null) {
            String jwtToken = jwtService.generateToken(authenticatedUser);
            return ResponseEntity.ok(jwtToken);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Not Found User");
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
    public ResponseEntity<String> resetPassword(@RequestParam("token") String token, @RequestParam("newPassword") String newPassword) {
        boolean result = userService.resetPassword(token, newPassword);
        if (result) {
            return ResponseEntity.ok("Password has been reset successfully");
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid or expired token");
        }
    }
}
