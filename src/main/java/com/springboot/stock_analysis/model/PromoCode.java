package com.springboot.stock_analysis.model;

import jakarta.persistence.*;
import jakarta.persistence.GenerationType;

import java.time.LocalDate;

@Entity
@Table(name = "tbl_promocode")
public class PromoCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code")
    private String code;

    @Column(name = "discount")
    private Double discount;

    @Column(name = "expiryDate", nullable = false)
    private Long expiryDate;

    public PromoCode(String code, Double discount, Long expiryDate ) {
        this.code = code;
        this.discount = discount;
        this.expiryDate = expiryDate;
    }

    public PromoCode() {

    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Double getDiscount() {
        return discount;
    }

    public void setDiscount(Double discount) {
        this.discount = discount;
    }

    public Long getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(Long expiryDate) {
        this.expiryDate = expiryDate;
    }
}
