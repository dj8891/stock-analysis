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

    @Column(name = "ip")
    private String ip;

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

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public WatchList(String ticker, String ipAddress) {
        this.ticker = ticker;
        this.ip = ipAddress;
    }
}
