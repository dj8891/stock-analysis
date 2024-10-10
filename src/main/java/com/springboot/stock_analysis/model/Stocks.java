package com.springboot.stock_analysis.model;

import jakarta.persistence.*;

@Entity
@Table(name="tbl_stocks")
public class Stocks {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String ticker;
    private String year;

    public Stocks() {}

    @Column(nullable = true)
    private double stock;

    @Column(nullable = true)
    private double eps;

    @Column(nullable = true)
    private double revenue;

    public Stocks() {
        this.stock = 0;
        this.eps = 0;
        this.revenue = 0;
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
