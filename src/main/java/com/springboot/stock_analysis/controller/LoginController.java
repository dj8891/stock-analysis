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
    public LoginController(AuthenticationService
                           authenticationService, JwtService jwtService) {
        this.authenticationService = authenticationService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody User user) {
        return ResponseEntity.ok(authenticationService.register(user));
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
}
