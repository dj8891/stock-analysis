package com.springboot.stock_analysis.dto;

public class PageInfoDTO {
    String cursor;
    int pageNumber;

    public PageInfoDTO(String cursor, int pageNumber) {
        this.cursor = cursor;
        this.pageNumber = pageNumber;
    }

    public String getCursor() {
        return cursor;
    }

    public void setCursor(String cursor) {
        this.cursor = cursor;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }
}
