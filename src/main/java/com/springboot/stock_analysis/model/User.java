package com.springboot.stock_analysis.model;

import com.springboot.stock_analysis.enums.RoleTypes;
import jakarta.persistence.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import javax.management.relation.Role;
import java.time.LocalDate;
import java.util.*;

@Entity
@Table(name = "tbl_users")
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email")
    private String email;

    @Column(name = "password")
    private String password;

    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    private RoleTypes role;

    @Column(name = "cus_id")
    private String cusId;

    @Column(name = "plan_status")
    private String planStatus;

    @Column(name = "sub_id")
    private String subId;

    @Column(name = "expiryDate")
    private LocalDate expiryDate;

    @Column(name = "isSubscribe")
    private Boolean isSubscribe;

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public User setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
        return this;
    }

    public String getSubId() {
        return subId;
    }

    public void setSubId(String subId) {
        this.subId = subId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public User setEmail(String email) {
        this.email = email;
        return this;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> list = new ArrayList<GrantedAuthority>();

        list.add(new SimpleGrantedAuthority(String.valueOf(role)));

        return list;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return "";
    }

    @Override
    public boolean isAccountNonExpired() {
        return UserDetails.super.isAccountNonExpired();
    }

    @Override
    public boolean isAccountNonLocked() {
        return UserDetails.super.isAccountNonLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return UserDetails.super.isCredentialsNonExpired();
    }

    @Override
    public boolean isEnabled() {
        return UserDetails.super.isEnabled();
    }

    public User setPassword(String password) {
        this.password = password;
        return this;
    }

    public RoleTypes getRole() {
        return role;
    }

    public User setRole(RoleTypes role) {
        this.role = role;
        return this;
    }

    public String getCusId() {
        return cusId;
    }

    public void setCusId(String cusId) {
        this.cusId = cusId;
    }

    public String getPlanStatus() {
        return planStatus;
    }

    public void setPlanStatus(String planStatus) {
        this.planStatus = planStatus;
    }

    public Boolean getSubscribe() {
        return isSubscribe;
    }

    public User setSubscribe(Boolean subscribe) {
        isSubscribe = subscribe;
        return this;
    }
}
