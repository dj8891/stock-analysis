package com.springboot.stock_analysis.dto;

public class RatedStockDTO {
    private String ticker;
    private double rating;

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public double getRating() {
        return rating;
    }

    public void setRating(double rating) {
        this.rating = rating;
    }
}
