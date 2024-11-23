package com.springboot.stock_analysis.dto;

public class LoginResponseDTO {
    private String token;
    private Boolean isTrialEnded;
    public LoginResponseDTO(String token, Boolean isTrialEnded) {
        this.token = token;
        this.isTrialEnded = isTrialEnded;
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
}
