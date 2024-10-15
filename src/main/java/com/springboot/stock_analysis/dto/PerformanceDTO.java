package com.springboot.stock_analysis.dto;

public class PerformanceDTO {
    String ticker;
    Double growth;

    public PerformanceDTO(String ticker, Double growth) {
        this.ticker = ticker;
        this.growth = growth;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public Double getGrowth() {
        return growth;
    }

    public void setGrowth(Double growth) {
        this.growth = growth;
    }
}
