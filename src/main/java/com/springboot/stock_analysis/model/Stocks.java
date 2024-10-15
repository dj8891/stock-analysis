package com.springboot.stock_analysis.model;

import jakarta.persistence.*;

@Entity
@Table(name="tbl_stocks")
public class Stocks {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticker", nullable = false)
    private String ticker;

    @Column(name = "year")
    private String year;

    @Column(name = "stock")
    private double stock;

    @Column(name = "eps")
    private double eps;

    @Column(name = "revenue")
    private double revenue;

    public Stocks() {
        this.stock = 0;
        this.eps = 0;
        this.revenue = 0;
        this.ticker = "";
    }
    public Stocks(String ticker, String year, Double stock, Double eps, Double revenue) {
        this.ticker = ticker;
        this.revenue = revenue;
        this.year = year;
        this.eps = eps;
        this.stock = stock;
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

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public double getStock() {
        return stock;
    }

    public void setStock(double stock) {
        this.stock = stock;
    }

    public double getEps() {
        return eps;
    }

    public void setEps(double eps) {
        this.eps = eps;
    }

    public double getRevenue() {
        return revenue;
    }

    public void setRevenue(double revenue) {
        this.revenue = revenue;
    }
}
