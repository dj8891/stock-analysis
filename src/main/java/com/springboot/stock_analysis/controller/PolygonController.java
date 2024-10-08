package com.springboot.stock_analysis.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springboot.stock_analysis.service.PolygonApiService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;

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
    public Mono<JsonNode> getDji() {
        return polygonApiService.getAllStockData();
    }
}
