package com.springboot.stock_analysis.controller;

import com.springboot.stock_analysis.dto.RatedStockDTO;
import com.springboot.stock_analysis.service.PolygonApiService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;


import java.util.List;

@RestController
@RequestMapping("/polygon")
@CrossOrigin
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
    public Mono<ResponseEntity<?>> getMarketSummary() {
        return polygonApiService.getAllETFData()
                .map(ResponseEntity::ok);
    }

    @GetMapping("/best-performance")
    public ResponseEntity<?> getPerformanceList() {
        if(polygonApiService.getPerformanceList() != null) {
            return ResponseEntity.ok(polygonApiService.getPerformanceList());
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Database error");
        }
    }

    @PostMapping("/news")
    public Mono<ResponseEntity<?>> getNews(@RequestParam("tickers") String tickers) {
        return polygonApiService.getNews(tickers)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/stock/{ticker}")
    public Mono<ResponseEntity<?>> getStockData(@PathVariable String ticker) {
        return polygonApiService.getStockData(ticker)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/chart/{ticker}/{date}")
    public Mono<ResponseEntity<?>> getChartData(@PathVariable(name="ticker") String ticker, @PathVariable(name="date") String date) {
        return polygonApiService.getChartData(ticker, date)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/test")
    public ResponseEntity<?> getTest() {
        return ResponseEntity.ok(polygonApiService.getTest());
    }
}
