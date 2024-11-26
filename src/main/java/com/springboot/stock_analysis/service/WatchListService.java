package com.springboot.stock_analysis.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.springboot.stock_analysis.model.Stocks;
import com.springboot.stock_analysis.model.WatchList;
import com.springboot.stock_analysis.repository.WatchListRepository;
import com.springboot.stock_analysis.utils.CalculatorUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    private final CalculatorUtils calculatorUtils;
    public WatchListService(WatchListRepository watchListRepository,
                            WebClient webClient,
                            PolygonApiService polygonApiService,
                            CalculatorUtils calculatorUtils) {
        this.watchListRepository = watchListRepository;
        this.webClient = webClient;
        this.polygonApiService = polygonApiService;
        this.calculatorUtils = calculatorUtils;
    }

    public Optional<WatchList> getWatchByTickerAndEmail(String ticker, String email) {
        return this.watchListRepository.findByTickerAndEmail(ticker, email);
    }

    public Mono<?> getALlData(List<WatchList> watchLists) {

        if(!watchLists.isEmpty()) {
            StringBuilder tickerListStr = new StringBuilder();
            int curYear = LocalDate.now().getYear() - 1;
            int prevYear = curYear - 1;
            List<Map<String, String>> returnVal = new ArrayList<>();
            double totalPrice = 0.0;
            for(WatchList watchList: watchLists) {
                tickerListStr.append((tickerListStr.isEmpty()) ? "" : ",").append(watchList.getTicker());
                double companyGrowthRate = this.calculatorUtils.companyGrowthRate(this.polygonApiService.getAllStocks(), watchList.getTicker(), curYear, curYear + 1, "stock");
                Stocks stock = this.calculatorUtils.findStockByTickerAndYear(this.polygonApiService.getAllStocks(), watchList.getTicker(), "TTM");
                Stocks prevStock = this.calculatorUtils.findStockByTickerAndYear(this.polygonApiService.getAllStocks(), watchList.getTicker(), String.valueOf(curYear));
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
            returnVal.sort(new Comparator<Map<String, String>>() {
                @Override
                public int compare(Map<String, String> o1, Map<String, String> o2) {
                    return Double.compare(Double.parseDouble(o2.get("\"stockPrice\"")), Double.parseDouble(o1.get("\"stockPrice\"")));
                }
            });
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
        } else {
            return null;
        }
    }

    public List<Map<String, String>> getNewsData(List<WatchList> watchLists) {
        List<Map<String, String>> returnList = new ArrayList<>();

        int limit = switch (watchLists.size()) {
            case 1 -> 4;
            case 2, 3 -> 2;
            default -> 1;
        };
        if (!watchLists.isEmpty()) {
            int maxLimit = 0;
            for (WatchList watchList : watchLists) {
                List<Map<String, String>> newsList = this.polygonApiService.getNews(watchList.getTicker(), String.valueOf(limit), 0)
                        .onErrorResume(e -> Mono.empty())
                        .block();
                if (newsList != null) {
                    returnList = Stream.concat(returnList.stream(), newsList.stream())
                            .toList();
                    maxLimit += limit;
                }
                if(maxLimit > 4) break;
            }
            return returnList;
        }
        return null;
    }

    public List<WatchList> getTickerList(String email) {
        return this.watchListRepository.findAllByEmail(email);
    }

    public ResponseEntity<String> deleteTicker(String ticker) {
        try {
            this.watchListRepository.deleteByTicker(ticker);
            return ResponseEntity.ok("Successfully deleted ticker: " + ticker);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }
}
