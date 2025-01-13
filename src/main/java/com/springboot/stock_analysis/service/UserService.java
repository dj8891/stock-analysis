package com.springboot.stock_analysis.service;

import com.springboot.stock_analysis.dto.UserDTO;
import com.springboot.stock_analysis.enums.RoleTypes;
import com.springboot.stock_analysis.model.PasswordResetToken;
import com.springboot.stock_analysis.model.User;
import com.springboot.stock_analysis.repository.PasswordResetTokenRepository;
import com.springboot.stock_analysis.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserService implements UserDetailsService {
    @Value("${server.url}")
    String serverUrl;
    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public UserService(UserRepository userRepository,
                       PasswordResetTokenRepository passwordResetTokenRepository,
                       PasswordEncoder passwordEncoder,
                       EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = this.userRepository.findByEmail(email).orElseThrow(() -> new UsernameNotFoundException("User not found with email:" + email));
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(), user.getPassword(), user.getAuthorities());
    }

    public void generatePasswordResetToken(String email) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            String token = UUID.randomUUID().toString();
            LocalDateTime expiredDate = LocalDateTime.now().plusHours(4);
            PasswordResetToken resetToken = new PasswordResetToken(token, user, expiredDate);
            passwordResetTokenRepository.save(resetToken);

            // Send the reset link via email
            String resetUrl = serverUrl + "/reset-password?token=" + token;
            emailService.sendPasswordResetEmail(user.getEmail(), resetUrl);
        }
    }

    public boolean resetPassword(String token, String newPassword) {
        Optional<PasswordResetToken> resetTokenOptional = passwordResetTokenRepository.findByToken(token);
        if (resetTokenOptional.isPresent()) {
            if (!resetTokenOptional.get().isExpired()) {
                User user = resetTokenOptional.get().getUser();
                user.setPassword(passwordEncoder.encode(newPassword));
                userRepository.save(user);
                passwordResetTokenRepository.delete(resetTokenOptional.get()); // Invalidate the token
                return true;
            } else {
                passwordResetTokenRepository.delete(resetTokenOptional.get()); // Invalidate the token
                return false;
            }
        }
        return false;
    }

    public boolean checkPermission(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new UsernameNotFoundException("User not found with email:" + email));
        if (user.getRole() != RoleTypes.PREMIUM && user.getRole() != RoleTypes.ADMIN && Optional.ofNullable(user.getExpiryDate())
                .map(expiryDate -> expiryDate.isBefore(LocalDate.now()))
                .orElse(false)) {
            user.setRole(RoleTypes.USER);
            userRepository.save(user);
            return true;
        } else return false;
    }

    public String getRole(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new UsernameNotFoundException("User not found with email:" + email));
        return user.getRole() == RoleTypes.ADMIN ? "ADMIN" : "USER";
    }

    public boolean subScribeCheck(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if(user == null) {
            return true;
        }
        else {
            return user.getSubscribe();
        }
    }

    public boolean setSubScribe(String email, Boolean subscribe) {
        User user = userRepository.findByEmail(email).orElse(null);
        if(user != null) {
            user.setSubscribe(subscribe);
            userRepository.save(user);
            return true;
        }
        else {
            return false;
        }
    }

    public List<UserDTO> getAllUsers() {
        List<User> users = userRepository.findAll();
        List<UserDTO> userDtos = new ArrayList<>();
        for(User user : users) {
            userDtos.add(new UserDTO(
                user.getEmail(),
                user.getPlanStatus()!= null ? user.getPlanStatus() : null,
                user.getRole() != null ? user.getRole().toString() : null,
                user.getExpiryDate() != null ? user.getExpiryDate().toString() : null,
                user.getSubscribe() != null ? user.getSubscribe().toString() : null
            ));
        }
        return userDtos;
    }
}
