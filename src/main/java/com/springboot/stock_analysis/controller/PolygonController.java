package com.springboot.stock_analysis.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.springboot.stock_analysis.dto.RatedStockDTO;
import com.springboot.stock_analysis.service.PolygonApiService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;


import java.util.*;

@RestController
@RequestMapping("/polygon")
@CrossOrigin("*")
public class PolygonController {

    private final PolygonApiService polygonApiService;
    public PolygonController(PolygonApiService polygonApiService) {
        this.polygonApiService = polygonApiService;
    }

    @GetMapping("/rated-stock")
    public ResponseEntity<?> getRatedStocks() {
       if(polygonApiService.getRatedStocks() != null) return ResponseEntity.ok(polygonApiService.getRatedStocks());
       else return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Database Error");
    }

    @GetMapping("/market-summary")
    public ResponseEntity<?> getMarketSummary() {
        return polygonApiService.getAllETFData()
                .map(ResponseEntity::ok)  // Map the successful response to ResponseEntity
                .onErrorReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null)).block(); // Handle errors
    }

    @GetMapping("/best-performance")
    public ResponseEntity<?> getPerformanceList() {
        if(polygonApiService.getPerformanceList() != null) {
            return ResponseEntity.ok(polygonApiService.getPerformanceList());
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Database error");
        }
    }
//
    @PostMapping("/news")
    public ResponseEntity<List<Map<String, String>>> getNews(@RequestParam("tickers") String tickers, @RequestParam("limit") String limit, @RequestParam("page") String page) {
        return polygonApiService.getNews(tickers, limit, Integer.parseInt(page))
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null)).block(); // Handle errors
//                .onErrorReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.emptyList()));
    }

    @GetMapping("/stock/{ticker}")
    public ResponseEntity<?> getStockData(@PathVariable String ticker) {
        if(polygonApiService.getStockData(ticker) != null)
            return polygonApiService.getStockData(ticker)
                .map(ResponseEntity::ok)
                    .onErrorReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null)).block(); // Handle errors
        else return ResponseEntity.notFound().build();
    }

    @GetMapping("/chart/{ticker}/{date}")
    public ResponseEntity<?> getChartData(@PathVariable(name="ticker") String ticker, @PathVariable(name="date") String date) {
        return polygonApiService.getChartData(ticker, date)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null)).block(); // Handle errors
    }

    @GetMapping("/test")
    public ResponseEntity<?> getTest() {
        return ResponseEntity.ok(polygonApiService.getTest());
    }

    @GetMapping("/tickers/{ticker}")
    public ResponseEntity<?> getTickers(@PathVariable String ticker) {
        try {
            return ResponseEntity.ok(this.polygonApiService.getTickerList(ticker));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }
}
