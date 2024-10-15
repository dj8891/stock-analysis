package com.springboot.stock_analysis.controller;

import com.springboot.stock_analysis.service.StockDetailService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin
@RequestMapping("/stock")
public class StockDetailController {

    private final StockDetailService stockDetailService;
    public StockDetailController(StockDetailService stockDetailService) {
        this.stockDetailService = stockDetailService;
    }

    @PostMapping("/save")
    public ResponseEntity<Object> getStockDetail(@RequestParam("ticker") String ticker) {
        return ResponseEntity.ok(stockDetailService.saveToWatchList(ticker));
    }
}
