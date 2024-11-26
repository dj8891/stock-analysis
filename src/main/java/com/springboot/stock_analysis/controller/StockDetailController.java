package com.springboot.stock_analysis.controller;

import com.springboot.stock_analysis.service.JwtService;
import com.springboot.stock_analysis.service.StockDetailService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin
@RequestMapping("/stock")
public class StockDetailController {

    private final StockDetailService stockDetailService;
    private final JwtService jwtService;
    public StockDetailController(StockDetailService stockDetailService, JwtService jwtService) {
        this.stockDetailService = stockDetailService;
        this.jwtService = jwtService;
    }

    @PostMapping("/save")
    public ResponseEntity<Object> getStockDetail(@RequestParam("ticker") String ticker, @RequestHeader("Authorization") String token) {
        String jwt = token.substring(7);
        String email = jwtService.extractEamil(jwt);
        return ResponseEntity.ok(stockDetailService.saveToWatchList(ticker, email));
    }
}
