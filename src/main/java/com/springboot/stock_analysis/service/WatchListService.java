package com.springboot.stock_analysis.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.springboot.stock_analysis.model.Stocks;
import com.springboot.stock_analysis.model.WatchList;
import com.springboot.stock_analysis.repository.WatchListRepository;
import com.springboot.stock_analysis.utils.CalculatorUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Stream;

@Service
public class WatchListService {

    @Value("${polygon.api.key}")
    String apiKey;

    private final WatchListRepository watchListRepository;
    private final WebClient webClient;
    private final PolygonApiService polygonApiService;
    public WatchListService(WatchListRepository watchListRepository,
                            WebClient webClient,
                            PolygonApiService polygonApiService) {
        this.watchListRepository = watchListRepository;
        this.webClient = webClient;
        this.polygonApiService = polygonApiService;
    }

    public Optional<WatchList> getWatchByTicker(String ticker) {
        return this.watchListRepository.findAllByTicker(ticker);
    }

    public Mono<?> getALlData() {
        List<WatchList> watchLists = this.watchListRepository.findAll();
        if(!watchLists.isEmpty()) {
            StringBuilder tickerListStr = new StringBuilder();
            int curYear = LocalDate.now().getYear();
            int prevYear = curYear - 1;
            List<Map<String, String>> returnVal = new ArrayList<>();
            double totalPrice = 0.0;
            for(WatchList watchList: watchLists) {
                tickerListStr.append((tickerListStr.isEmpty()) ? "" : ",").append(watchList.getTicker());
                double companyGrowthRate = CalculatorUtils.companyGrowthRate(this.polygonApiService.getAllStocks(), watchList.getTicker(), prevYear, curYear, "stock");
                Stocks stock = CalculatorUtils.findStockByTickerAndYear(this.polygonApiService.getAllStocks(), watchList.getTicker(), "TTM");
                Stocks prevStock = CalculatorUtils.findStockByTickerAndYear(this.polygonApiService.getAllStocks(), watchList.getTicker(), String.valueOf(prevYear));
                double stockPrice = stock.getStock();
                double growthPrice = stockPrice - prevStock.getStock();
                Map<String, String> tmpRtn = new HashMap<>();
                tmpRtn.put("\"ticker\"", '"' + watchList.getTicker() + '"');
                tmpRtn.put("\"stockPrice\"", String.valueOf(stockPrice));
                tmpRtn.put("\"growthPrice\"", String.valueOf(growthPrice));
                tmpRtn.put("\"growthRate\"", String.valueOf(companyGrowthRate));
                totalPrice += companyGrowthRate;
                returnVal.add(tmpRtn);
            }
            Map<String, String> returnValue = new HashMap<>();
            returnValue.put("watchListStock", returnVal.toString());
            returnValue.put("annualGrowth", String.valueOf(totalPrice / watchLists.size()));
            return webClient.get()
                    .uri("/v2/snapshot/locale/us/markets/stocks/tickers?tickers={tickerList}&apiKey={apiKey}", tickerListStr, apiKey)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .map(value -> {
                        JsonNode datas = value.path("tickers");
                        double totalChangePerc = 0.0;
                        if(datas.isArray()) {
                            for(JsonNode data: datas) {
                                double dailyChangePerc = data.path("todaysChangePerc").asDouble();
                                totalChangePerc += dailyChangePerc;
                            }
                        }
                        returnValue.put("dailyGrowth", String.valueOf(totalChangePerc / watchLists.size()));
                        return returnValue;
                    });
        }
        return null;
    }

    public List<Map<String, String>> getNewsData() {
        List<WatchList> watchLists = this.watchListRepository.findAll();
        List<Map<String, String>> returnList = new ArrayList<>();
        if (!watchLists.isEmpty()) {
            StringBuilder tickerListStr = new StringBuilder();
            List<Map<String, String>> returnVal = new ArrayList<>();
            double totalPrice = 0.0;
            for (WatchList watchList : watchLists) {
                List<Map<String, String>> newsList = this.polygonApiService.getNews(watchList.getTicker()).block();
                if (newsList != null)
                    returnList = Stream.concat(returnList.stream(), newsList.stream())
                            .toList();
            }
        }
        return returnList;
    }
}
