package com.springboot.stock_analysis.dto;

public class UserDTO {
    private String email;
    private String plan_status;
    private String role;
    private String expiry_date;
    private String is_subscribe;

    public UserDTO(String email, String plan_status, String role, String expiry_date, String is_subscribe) {
        this.email = email;
        this.plan_status = plan_status;
        this.role = role;
        this.expiry_date = expiry_date;
        this.is_subscribe = is_subscribe;
    }


    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPlan_status() {
        return plan_status;
    }

    public void setPlan_status(String plan_status) {
        this.plan_status = plan_status;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getExpiry_date() {
        return expiry_date;
    }

    public void setExpiry_date(String expiry_date) {
        this.expiry_date = expiry_date;
    }

    public String getIs_subscribe() {
        return is_subscribe;
    }

    public void setIs_subscribe(String is_subscribe) {
        this.is_subscribe = is_subscribe;
    }
}
