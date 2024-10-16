package com.springboot.stock_analysis.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.springboot.stock_analysis.model.Tickers;
import com.springboot.stock_analysis.model.Stocks;
import com.springboot.stock_analysis.repository.TickersRepository;
import com.springboot.stock_analysis.repository.StocksRepository;
import com.springboot.stock_analysis.utils.CalculatorUtils;

import java.util.concurrent.CompletableFuture;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.time.Year;

@Service
public class ScheduledService {
    @Value("${polygon.api.key}")
    String apiKey;

    private final WebClient webClient;
    private final ObjectMapper mapper;
    private TickersRepository tickersRepository = null;
    private StocksRepository stocksRepository = null;

    public ScheduledService(WebClient webClient, TickersRepository tickersRepository, StocksRepository stocksRepository) {
        this.webClient = webClient;
        this.tickersRepository = tickersRepository;
        this.stocksRepository = stocksRepository;
        this.mapper = new ObjectMapper();
    }

    public void addTicker(String shortName, String fullName) {
        if (!tickersRepository.existsByShortName(shortName)) {
            Tickers newTicker = new Tickers(shortName, fullName);

            tickersRepository.save(newTicker);
        }
    }

    public void addStock(String ticker, String year, double stock) {
        CompletableFuture.runAsync(() -> {
            try {
                Optional<Stocks> existingEntity = stocksRepository.findByTickerAndYear(ticker, year);

                if (existingEntity.isPresent()) {
                    Stocks entityToUpdate = existingEntity.get();

                    entityToUpdate.setStock(stock);

                    stocksRepository.save(entityToUpdate);
                } else {
                    Stocks newStocks = new Stocks();

                    newStocks.setTicker(ticker);
                    newStocks.setYear(year);
                    newStocks.setStock(stock);

                    stocksRepository.save(newStocks);
                }
            } catch (Exception e) {
                System.out.println("ERROR (addStock): " + e.getMessage());
            }
        });
    }

    public void addRevenueAndEps(String ticker, String year, double revenue, double eps) {
        try {
            Optional<Stocks> existingEntity = stocksRepository.findByTickerAndYear(ticker, year);

            if (existingEntity.isPresent()) {
                Stocks entityToUpdate = existingEntity.get();

                entityToUpdate.setRevenue(revenue);
                entityToUpdate.setEps(eps);

                stocksRepository.save(entityToUpdate);
            } else {
                Stocks newStocks = new Stocks();

                newStocks.setTicker(ticker);
                newStocks.setYear(year);
                newStocks.setRevenue(revenue);
                newStocks.setEps(eps);

                stocksRepository.save(newStocks);
            }
        } catch (Exception e) {
            System.out.println("ERROR (addStock): " + e.getMessage());
        }
    }

    public void getAllTickers() {
        JsonNode allTickers = null;

        try {
            System.out.println("LOG: Getting all tickers started!");

            String response = webClient.get()
                    .uri("/v3/reference/tickers?active=true&limit=1000&apiKey={apiKey}", apiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            allTickers = mapper.readTree(response);

            while (true) {
                allTickers.path("results").forEach(result -> {
                    addTicker(result.path("ticker").asText(), result.path("name").asText());
                });

                if (!allTickers.has("next_url")) {
                    break;
                }

                final WebClient _webClient = WebClient
                        .builder()
                        .codecs(configurer -> configurer
                            .defaultCodecs()
                            .maxInMemorySize(128 * 1024 * 1024))
                        .build();

                response = _webClient.get()
                        .uri(allTickers.path("next_url").asText() + "&apiKey={apiKey}", apiKey)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                allTickers = mapper.readTree(response);
            }

            System.out.println("LOG: Getting all tickers completed!");
        } catch (Exception e) {
            System.out.println("ERROR: Getting all tickers error - " + e.getMessage());
        }
    }

    public JsonNode getAllActiveTickers() {
        try {
            String response = webClient.get()
                    .uri("/v2/snapshot/locale/us/markets/stocks/tickers?apiKey={apiKey}", apiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return mapper.readTree(response).get("tickers");
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public void asyncGetOneStockInfo(String ticker) {
        CompletableFuture.runAsync(() -> {
            try {
                String response = webClient.get()
                        .uri("/v2/aggs/ticker/{ticker}/range/1/year/2021-01-01/2024-10-10?adjusted=true&sort=asc&apiKey={apiKey}", ticker, apiKey)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                JsonNode stocksData = mapper.readTree(response);

                stocksData.get("results").forEach(result -> {
                    String year = CalculatorUtils.convertTimestampToDate(result.path("t").asLong());

                    if (year.equals(String.valueOf(Year.now().getValue()))) {
                        return;
                    }

                    addStock(ticker, year, result.path("vw").asDouble());
                });
            } catch (Exception e) {
                System.out.println("ERROR (asyncGetOneStockInfo): [" + ticker + "]" + e.getMessage());
            }
        });
    }

    public void asyncGetOneFinancialInfo(String ticker, boolean isTTM) {
        CompletableFuture.runAsync(() -> {
            try {
                String tmpUri = "/vX/reference/financials?ticker={ticker}&timeframe=annual&limit=4&apiKey={apiKey}";

                if (isTTM) {
                    tmpUri = "/vX/reference/financials?ticker={ticker}&timeframe=ttm&limit=4&apiKey={apiKey}";
                }

                String response = webClient.get()
                        .uri(tmpUri, ticker, apiKey)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                JsonNode financialData = mapper.readTree(response);

                financialData.get("results").forEach(result -> {
                    String year = result.get("fiscal_year").asText();

                    if (isTTM) {
                        year = "TTM";
                    }

                    addRevenueAndEps(ticker, year,
                            result.path("financials").path("income_statement").path("revenues").path("value").asDouble(),
                            result.path("financials").path("income_statement").path("diluted_earnings_per_share").path("value").asDouble());
                });
            } catch (Exception e) {
                System.out.println("ERROR (asyncGetOneFinancialInfo): [" + ticker + "]" + e.getMessage());
            }
        });
    }

    public void getInfo() {
        CompletableFuture.runAsync(() -> {
            try {
                JsonNode allTickers = getAllActiveTickers();

                System.out.println("LOG: Getting info started!");

                for (int i = 0; i < allTickers.size(); i++) {
                    String ticker = allTickers.get(i).get("ticker").asText();

                    addStock(ticker, "TTM", allTickers.get(i).path("day").path("vw").asDouble());

                    if (i > 0 && i % 50 == 0) Thread.sleep(500);
                }

                System.out.println("LOG: Getting Stock TTM Completed!");

                for (int i = 0; i < allTickers.size(); i++) {
                    String ticker = allTickers.get(i).get("ticker").asText();

                    asyncGetOneFinancialInfo(ticker, false);
                    asyncGetOneFinancialInfo(ticker, true);

                    if (i > 0 && i % 50 == 0) Thread.sleep(5000);
                }

                System.out.println("LOG: Getting Financial Info Completed!");

                for (int i = 0; i < allTickers.size(); i++) {
                    String ticker = allTickers.get(i).get("ticker").asText();

                    asyncGetOneStockInfo(ticker);

                    if (i > 0 && i % 50 == 0) Thread.sleep(5000);
                }

                System.out.println("LOG: Getting info completed!");
            } catch (Exception e) {
                System.out.println("ERROR (getInfo): " + e.getMessage());
            }
        }).thenRun(this::getInfo);
    }

    @PostConstruct
    public void onStartup() {
        getInfo();
    }
}