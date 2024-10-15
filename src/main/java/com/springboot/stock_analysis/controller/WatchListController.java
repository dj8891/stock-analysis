package com.springboot.stock_analysis.controller;

import com.springboot.stock_analysis.model.WatchList;
import com.springboot.stock_analysis.service.WatchListService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@CrossOrigin
@RequestMapping("/watchlist")
public class WatchListController {

    private final WatchListService watchListService;
    public WatchListController(WatchListService watchListService) {
        this.watchListService = watchListService;
    }
    @GetMapping("/list/{ticker}")
    public ResponseEntity<WatchList> getWatchList(@PathVariable String ticker) {
        Optional<WatchList> watchList = this.watchListService.getWatchByTicker(ticker);
        return ResponseEntity.ok(watchList.orElse(null));
    }

    @GetMapping("/data")
    public Mono<ResponseEntity<?>> getAllData() {
        return this.watchListService.getALlData()
                .map(ResponseEntity::ok);
    }

    @GetMapping("/news")
    public ResponseEntity<List<Map<String, String>>> getNewsData() {
        return ResponseEntity.ok(this.watchListService.getNewsData());
    }
}
