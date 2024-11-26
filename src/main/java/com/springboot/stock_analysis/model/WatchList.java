package com.springboot.stock_analysis.model;

import jakarta.persistence.*;

@Entity
@Table(name = "tbl_watchlist")
public class WatchList {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticker")
    private String ticker;

    @Column(name = "email", nullable = false)
    private String email;

    public WatchList() {

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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public WatchList(String ticker, String email) {
        this.ticker = ticker;
        this.email = email;
    }
}
