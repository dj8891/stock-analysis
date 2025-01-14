package com.springboot.stock_analysis.model;

import com.springboot.stock_analysis.enums.RoleTypes;
import jakarta.persistence.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "tbl_screener")
public class Screener {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticker")
    private String ticker;

    @Column(name = "company")
    private String company;

    @Column(name = "price")
    private double price;

    @Column(name = "score")
    private double score;

    @Column(name = "market_cap")
    private double market_cap;

    @Column(name = "revenue_gr")
    private double revenue_gr;

    @Column(name = "eps_ttm")
    private double eps_ttm;

    @Column(name = "peg_ratio")
    private double peg_ratio;

    @Column(name = "stock_gr")
    private double stock_gr;

    @Column(name = "pe_ratio")
    private double pe_ratio;

    public Screener() {

    }

    public Screener(String ticker) {
        this.ticker = ticker;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public double getMarket_cap() {
        return market_cap;
    }

    public void setMarket_cap(double market_cap) {
        this.market_cap = market_cap;
    }

    public double getRevenue_gr() {
        return revenue_gr;
    }

    public void setRevenue_gr(double revenue_gr) {
        this.revenue_gr = revenue_gr;
    }

    public double getEps_ttm() {
        return eps_ttm;
    }

    public void setEps_ttm(double eps_ttm) {
        this.eps_ttm = eps_ttm;
    }

    public double getPeg_ratio() {
        return peg_ratio;
    }

    public void setPeg_ratio(double peg_ratio) {
        this.peg_ratio = peg_ratio;
    }

    public double getStock_gr() {
        return stock_gr;
    }

    public void setStock_gr(double stock_gr) {
        this.stock_gr = stock_gr;
    }

    public double getPe_ratio() {
        return pe_ratio;
    }

    public void setPe_ratio(double pe_ratio) {
        this.pe_ratio = pe_ratio;
    }
}
