package com.springboot.stock_analysis.dto;

public class LoginResponseDTO {
    private String token;
    private Boolean isTrialEnded;
    private String permission;
    public LoginResponseDTO(String token, Boolean isTrialEnded, String permission) {
        this.token = token;
        this.isTrialEnded = isTrialEnded;
        this.permission = permission;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Boolean getTrialEnded() {
        return isTrialEnded;
    }

    public void setTrialEnded(Boolean trialEnded) {
        isTrialEnded = trialEnded;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }
}
