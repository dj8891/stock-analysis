package com.springboot.stock_analysis.service;

import com.springboot.stock_analysis.model.WatchList;
import com.springboot.stock_analysis.repository.WatchListRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class StockDetailService {
    private final WebClient webClient;
    private final WatchListRepository watchListRepository;

    public StockDetailService(WebClient webClient, WatchListRepository watchListRepository) {
        this.webClient = webClient;
        this.watchListRepository = watchListRepository;
    }

    public WatchList saveToWatchList(String ticker, String ipAddress) {
        WatchList watchList = new WatchList(ticker, ipAddress);
        return watchListRepository.save(watchList);
    }
}
