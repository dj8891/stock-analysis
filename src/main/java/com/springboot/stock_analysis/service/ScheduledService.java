package com.springboot.stock_analysis.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.springboot.stock_analysis.dto.PageInfoDTO;
import com.springboot.stock_analysis.model.Tickers;
import com.springboot.stock_analysis.model.Stocks;
import com.springboot.stock_analysis.repository.TickersRepository;
import com.springboot.stock_analysis.repository.StocksRepository;
import com.springboot.stock_analysis.utils.CalculatorUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Optional;
import java.time.Year;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

    private Mono<JsonNode> fetchNewsWithCursor(String cursor) {
        return webClient.get()
                .uri("/v3/reference/tickers?active=true&limit=1000&cursor={cursor}&apiKey={apiKey}", cursor, apiKey)
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    public void getAllTickers() {
        Mono.just("")
                .expand(cursor -> this.fetchNewsWithCursor(cursor)
                        .flatMap(response -> {
                            JsonNode results = response.path("results");
                            String nextUrl = response.path("next_url").asText();
                            for(JsonNode result: results) {
                                String ticker = result.path("ticker").asText();
                                String name = result.path("name").asText();
                                addTicker(ticker, name);
                            }
                            // Stop fetching if no more pages or results are empty
                            if (nextUrl.isEmpty() || results.isEmpty()) {
                                return Mono.empty();
                            }

                            // Extract the next cursor
                            List<String> urlParts = List.of(nextUrl.split("="));
                            String nextCursor = urlParts.size() > 1 ? urlParts.get(1) : "";

                            // Continue with the next cursor
                            return Mono.just(nextCursor);
                        })
                )
                .doOnComplete(() -> System.out.println("LOG: Getting all tickers completed!"))
                .onErrorResume(e -> {
                    System.out.println("ERROR: Getting all tickers error - " + e.getMessage());
                    return Mono.empty(); // Handle error gracefully
                })
                .subscribe();
    }

    public JsonNode getAllActiveTickers() {
        JsonNode tickers = webClient.get()
                .uri("/v2/snapshot/locale/us/markets/stocks/tickers?apiKey={apiKey}", apiKey)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
        assert tickers != null;
        return tickers.get("tickers");
    }

    public void asyncGetOneStockInfo(String ticker) {
        CompletableFuture.runAsync(() -> {
            try {
                LocalDate curDate = LocalDate.now();
                LocalDate fiveYearsAgo = curDate.minusYears(6);
                String response = webClient.get()
                        .uri("/v2/aggs/ticker/{ticker}/range/1/year/{fiveYears}/{curDate}?adjusted=true&sort=asc&apiKey={apiKey}", ticker, fiveYearsAgo.toString(), curDate.toString(), apiKey)
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
                String tmpUri = "/vX/reference/financials?ticker={ticker}&timeframe=annual&limit=5&apiKey={apiKey}";

                if (isTTM) {
                    tmpUri = "/vX/reference/financials?ticker={ticker}&timeframe=ttm&apiKey={apiKey}";
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

                    asyncGetOneFinancialInfo(ticker, true);

                    if (i > 0 && i % 50 == 0) Thread.sleep(5000);
                }

                System.out.println("LOG: Getting Financial Info Completed!");
            } catch (Exception e) {
                System.out.println("ERROR (getInfo): " + e.getMessage());
            }
        }).thenRun(this::getInfo);

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        Runnable dailyTask = () -> CompletableFuture.runAsync(() -> {
            System.out.println("GET Annual Data Started!");
            try {
                JsonNode allTickers = getAllActiveTickers();
                for (int i = 0; i < allTickers.size(); i++) {
                    String ticker = allTickers.get(i).get("ticker").asText();
                    asyncGetOneStockInfo(ticker);
                    asyncGetOneFinancialInfo(ticker, false);
                    if (i > 0 && i % 50 == 0) Thread.sleep(5000);
                }
            } catch (Exception e) {
                System.out.println("ERROR: GET Annual Data" + e.getMessage());
            }

            System.out.println("GET Annual Data completed!");
        });

        // Calculate the initial delay until the first execution
        long period = 24 * 60 * 60; // 24 hours in seconds

        // Schedule the task to run daily
        scheduler.scheduleAtFixedRate(dailyTask, 0, period, TimeUnit.SECONDS);
    }

    @PostConstruct
    public void onStartup() {
        getInfo();
        getAllTickers();
    }
}