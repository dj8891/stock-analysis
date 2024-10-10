package com.springboot.stock_analysis.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springboot.stock_analysis.dto.RatedStockDTO;
import com.springboot.stock_analysis.service.PolygonApiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/polygon")
public class PolygonController {

    private final PolygonApiService polygonApiService;
    public PolygonController(PolygonApiService polygonApiService) {
        this.polygonApiService = polygonApiService;
    }

    @GetMapping("/{symbol}")
    public Mono<String> getStock(@PathVariable String symbol) {
        return polygonApiService.getDowData(symbol);
    }

    @GetMapping("/dow")
    public List<String> getDji() {
        return polygonApiService.getTickersList();
    }

    @GetMapping("/rated-stock")
    public ResponseEntity<List<RatedStockDTO>> getRatedStocks() {
       return ResponseEntity.ok(polygonApiService.getRatedStocks());
    }
}
