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

    public WatchList(String ticker) {
        this.ticker = ticker;
    }
}
