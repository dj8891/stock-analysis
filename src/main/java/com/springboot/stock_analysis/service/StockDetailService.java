package com.springboot.stock_analysis.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.springboot.stock_analysis.model.WatchList;
import com.springboot.stock_analysis.repository.WatchListRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Service
public class StockDetailService {
    private final WebClient webClient;
    private final WatchListRepository watchListRepository;

    public StockDetailService(WebClient webClient, WatchListRepository watchListRepository) {
        this.webClient = webClient;
        this.watchListRepository = watchListRepository;
    }

    public WatchList saveToWatchList(String ticker) {
        WatchList watchList = new WatchList(ticker);
        return watchListRepository.save(watchList);
    }
}
