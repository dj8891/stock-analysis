package com.springboot.stock_analysis.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.springboot.stock_analysis.model.Tickers;
import com.springboot.stock_analysis.model.Stocks;
import com.springboot.stock_analysis.model.Screener;
import com.springboot.stock_analysis.repository.TickersRepository;
import com.springboot.stock_analysis.repository.StocksRepository;
import com.springboot.stock_analysis.repository.ScreenerRepository;
import com.springboot.stock_analysis.utils.CalculatorUtils;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cglib.core.Local;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.PrematureCloseException;
import reactor.util.retry.Retry;

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
    private ScreenerRepository screenerRepository = null;
    private final CalculatorUtils calculatorUtils;
    private final Map<String, List<Stocks>> allStocks = new HashMap<>();

    public ScheduledService(WebClient webClient, TickersRepository tickersRepository, StocksRepository stocksRepository, ScreenerRepository screenerRepository,  CalculatorUtils calculatorUtils) {
        this.webClient = webClient;
        this.tickersRepository = tickersRepository;
        this.stocksRepository = stocksRepository;
        this.screenerRepository = screenerRepository;
        this.mapper = new ObjectMapper();
        this.calculatorUtils = calculatorUtils;

        List<String> stockList = this.stocksRepository.findGroupedStocks();
        for(String stock: stockList) {
            String jsonString = "[" + stock + "]";
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                JsonNode jsonNode = objectMapper.readTree(jsonString);
                ArrayList<Stocks> stocks = new ArrayList<>();
                String ticker = "";
                for (JsonNode node : jsonNode) {
                    Double revenue = node.path("revenue").asDouble();
                    Double eps = node.path("eps").asDouble();
                    Double stockPrice = node.path("stock").asDouble();
                    String year = node.path("year").asText();
                    ticker = node.path("ticker").asText();
                    stocks.add(new Stocks(ticker, year, stockPrice, eps, revenue));
                }
                this.allStocks.put(ticker, stocks);
            } catch (JsonProcessingException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    public void addTicker(String shortName, String fullName, String type) {
        if (!tickersRepository.existsByShortName(shortName)) {
            Tickers newTicker = new Tickers(shortName, fullName, type);

            tickersRepository.save(newTicker);
        }
    }

    public void addStock(String ticker, String year, double stock) {
        CompletableFuture.runAsync(() -> {
            try {
                List<Stocks> existingEntity = stocksRepository.findByTickerAndYear(ticker, year);

                if (!existingEntity.isEmpty()) {
                    Stocks entityToUpdate = existingEntity.get(0); // Use the first record
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
            List<Stocks> existingEntity = stocksRepository.findByTickerAndYear(ticker, year);

            if (!existingEntity.isEmpty()) {
                Stocks entityToUpdate = existingEntity.get(0);

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

    public void addScreener(String ticker) {
        try {
            Screener screener = new Screener(ticker);

            JsonNode summary = webClient.get()
                    .uri("/v2/snapshot/locale/us/markets/stocks/tickers/{ticker}?apiKey={apiKey}", ticker, apiKey)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .onErrorResume(e -> Mono.empty())
                    .block();

            if (summary != null) {
                double stockPrice = summary.path("ticker")
                        .path("day")
                        .path("vw")
                        .asDouble(0.0);
                screener.setPrice(stockPrice);

                int endYear = LocalDate.now().getYear() - 1;
                int growthRating = this.calculatorUtils.growthEvaluation(allStocks, ticker, endYear);
                int valueRating = this.calculatorUtils.valueCriteria(allStocks, ticker, endYear);
                int performanceRating = this.calculatorUtils.performanceEvaluation(allStocks, ticker, endYear);

                double growth = (growthRating / 10.0) * 4;
                double value = (valueRating / 10.0) * 2.7;
                double performance = (performanceRating / 10.0) * 3.3;
                double stockScore = growth + value + performance;

                screener.setScore(stockScore);

                Tickers tickerModel = this.tickersRepository.findByShortName(ticker).orElse(new Tickers(ticker, "", ""));
                String company = tickerModel.getFullName();
                screener.setCompany(company);

                JsonNode referenceData = webClient.get()
                        .uri("/v3/reference/tickers/{ticker}?apiKey={apiKey}", ticker, apiKey)
                        .retrieve()
                        .bodyToMono(JsonNode.class)
                        .block();

                if (referenceData != null) {
                    double marketCap = referenceData.path("results")
                            .path("market_cap")
                            .asDouble(0.0) / 1_000_000_000.0; // Convert to billions
                    screener.setMarket_cap(marketCap);

                    double epsTTM = this.calculatorUtils.findStockByTickerAndYear(allStocks, ticker, "TTM").getEps();
                    screener.setEps_ttm(epsTTM);

                    double revenueGrowth = this.calculatorUtils.companyGrowthRate(allStocks, ticker, endYear - 2, endYear, "revenue");
                    double stockGrowth = this.calculatorUtils.companyGrowthRate(allStocks, ticker, endYear - 2, endYear, "stock");
                    Stocks startStocks = this.calculatorUtils.findStockByTickerAndYear(allStocks, ticker, String.valueOf(endYear - 4));
                    Stocks lastStocks = this.calculatorUtils.findStockByTickerAndYear(allStocks, ticker, String.valueOf(endYear));
                    double peg = 0;

                    Stocks ttmStock = this.calculatorUtils.findStockByTickerAndYear(allStocks, ticker, "TTM");
                    double pe = epsTTM == 0 ? 0 : ttmStock.getStock() / epsTTM;

                    if (startStocks.getEps() != 0) {
                        peg = this.calculatorUtils.cagrForCompanyNumerical(allStocks, startStocks, lastStocks, ticker);
                    }

                    // Validate and set values, replace invalid values with defaults
                    screener.setRevenue_gr(Double.isFinite(revenueGrowth) ? revenueGrowth : 0);
                    screener.setStock_gr(Double.isFinite(stockGrowth) ? stockGrowth : 0);
                    screener.setPeg_ratio(Double.isFinite(peg) ? peg : 0);
                    screener.setPe_ratio(Double.isFinite(pe) ? pe : 0);
                }
            }

            List<Screener> existingEntity = screenerRepository.findByTicker(ticker);

            if (!existingEntity.isEmpty()) {
                Screener entityToUpdate = existingEntity.get(0);

                entityToUpdate.setPrice(screener.getPrice());
                entityToUpdate.setScore(screener.getScore());
                entityToUpdate.setEps_ttm(screener.getEps_ttm());
                entityToUpdate.setMarket_cap(screener.getMarket_cap());
                entityToUpdate.setRevenue_gr(screener.getRevenue_gr());
                entityToUpdate.setStock_gr(screener.getStock_gr());
                entityToUpdate.setPeg_ratio(screener.getPeg_ratio());
                entityToUpdate.setPe_ratio(screener.getPe_ratio());

                screenerRepository.save(entityToUpdate);
            } else {
                screenerRepository.save(screener);
            }
        } catch (Exception e) {
            System.out.println("ERROR (addScreener): " + e.getMessage());
            e.printStackTrace(); // Log stack trace for debugging
        }
    }

    public void getAllScreenerData() {
        Mono<JsonNode> allTickers = getAllActiveTickers();
        System.out.println("Get All Screener Data Started");
        allTickers.subscribe(tickers -> {
            if (tickers != null && tickers.has("tickers")) {
                JsonNode tickerArray = tickers.path("tickers");
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                int batchSize = 10;
                int totalTickers = tickerArray.size();

                Flux.range(0, totalTickers)
                    .concatMap(i -> {
                        String tickerName = tickerArray.get(i).path("ticker").asText();

                        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                            addScreener(tickerName);
                        });

                        futures.add(future);

                        if (futures.size() == batchSize || i == totalTickers - 1) {
                            CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                            allOf.join();
                            futures.clear();
                        }

                        if (i % 500 == 0) {
                            System.out.println("Screener Info Waiting: " + i / 500);
                            return Mono.delay(Duration.ofSeconds(90)).then();
                        }

                        if( i == totalTickers - 1) {
                            System.out.println("Get Stocks of All Tickers Completed");
                        }
                        return Mono.empty();
                    })
                    .then()
                    .subscribe();
            }
        });
    }

    private Mono<JsonNode> fetchNewsWithCursor(String cursor) {
        return webClient.get()
            .uri("/v3/reference/tickers?active=true&limit=1000&cursor={cursor}&apiKey={apiKey}", cursor, apiKey)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .timeout(Duration.ofSeconds(60))
            .retryWhen(Retry.backoff(5, Duration.ofSeconds(5))
                .maxBackoff(Duration.ofSeconds(30))
                .jitter(0.5)
                .filter(e -> e instanceof PrematureCloseException))
            .onErrorResume(e -> {
                if (e instanceof PrematureCloseException) {
                    System.out.println("PrematureCloseException for cursor " + cursor + ": Retrying...");
                } else {
                    System.out.println("Error fetching cursor " + cursor + ": " + e.getMessage());
                }
                return Mono.empty();
            });
    }

    public void getAllTickers() {
        System.out.println("Getting All tickers started!!!");
        Mono.just("")
            .expand(cursor -> this.fetchNewsWithCursor(cursor)
                .delaySubscription(Duration.ofMillis(1000))
                .flatMap(response -> {
                    JsonNode results = response.path("results");
                    String nextUrl = response.path("next_url").asText();

                    for (JsonNode result : results) {
                        String ticker = result.path("ticker").asText();
                        String name = result.has("name") ? result.path("name").asText() : "";

                        addTicker(ticker, name, "");
                    }

                    if (nextUrl.isEmpty() || results.isEmpty()) {
                        return Mono.empty();
                    }

                    String nextCursor = "";
                    try {
                        nextCursor = URI.create(nextUrl).getQuery().split("cursor=")[1].split("&")[0];
                    } catch (Exception e) {
                        System.out.println("Failed to extract cursor: " + e.getMessage());
                    }

                    return Mono.just(nextCursor);
                })
            )
            .doOnComplete(() -> System.out.println("LOG: Getting all tickers completed!"))
            .onErrorResume(e -> {
                System.out.println("ERROR: Getting all tickers error - " + e.getMessage());
                return Mono.empty();
            })
            .subscribe();
    }

    public Mono<JsonNode> getAllActiveTickers() {
        return webClient.get()
            .uri("/v2/snapshot/locale/us/markets/stocks/tickers?apiKey={apiKey}", apiKey)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .onErrorResume(e -> {
                System.out.println("ERROR: Get All Tickers Failed: " + e.getMessage());
                return Mono.empty();
            });
    }

    public void asyncGetOneStockInfo(String ticker) {
        try {
            LocalDate curDate = LocalDate.now();
            LocalDate fiveYearsAgo = curDate.minusYears(6);
            webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v2/aggs/ticker/{ticker}/range/1/year/{fiveYears}/{curDate}")
                        .queryParam("adjusted", "true")
                        .queryParam("sort", "asc")
                        .queryParam("apiKey", apiKey)
                        .build(ticker, fiveYearsAgo.toString(), curDate.toString()))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .onErrorResume(e -> {
                    System.out.println("ERROR (asyncGetOneStockInfo): [" + ticker + "] " + e.getMessage());
                    return Mono.empty();
                })
                .subscribe(response -> {
                    JsonNode results = response.path("results");

                    if (results.isArray() && results.size() > 0) {
                        for (JsonNode result : results) {
                            String year = CalculatorUtils.convertTimestampToDate(result.path("t").asLong());

                            if (!year.equals(String.valueOf(Year.now().getValue()))) {
                                addStock(ticker, year, result.path("vw").asDouble());
                            }
                        }

                    } else {
                        System.out.println("No results found for ticker: " + ticker);
                    }
                });
        } catch (Exception e) {
            System.out.println("ERROR (asyncGetOneStockInfo): [" + ticker + "] " + e.getMessage());
        }
    }

    public void asyncGetOneFinancialInfo(String ticker, boolean isTTM) {
        try {
            String tmpUri = "/vX/reference/financials?ticker={ticker}&timeframe=annual&limit=6&apiKey={apiKey}";

            if (isTTM) {
                tmpUri = "/vX/reference/financials?ticker={ticker}&timeframe=ttm&apiKey={apiKey}";
            }

            webClient.get()
                .uri(tmpUri, ticker, apiKey)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(5)))
                .onErrorResume(e -> {
                    System.out.println("ERROR (asyncGetOneFinancialInfo): [" + ticker + "]" + e.getMessage());
                    return Mono.empty();
                })
                .subscribe(financialData -> {
                    financialData.path("results").forEach(result -> {
                        String year = result.path("fiscal_year").asText();
                        if (isTTM) {
                            year = "TTM";
                        }
                        addRevenueAndEps(ticker, year,
                                result.path("financials").path("income_statement").path("revenues").path("value").asDouble(),
                                result.path("financials").path("income_statement").path("diluted_earnings_per_share").path("value").asDouble());
                    });
                });
        } catch (Exception e) {
            System.out.println("ERROR (asyncGetOneFinancialInfo): [" + ticker + "]" + e.getMessage());
        }
    }

    public void getAllTickersStock() {
        Mono<JsonNode> allTickers = getAllActiveTickers();
        System.out.println("Get Stocks of All Tickers Started");
        allTickers.subscribe(tickers -> {
            if (tickers != null && tickers.has("tickers")) {
                JsonNode tickerArray = tickers.path("tickers");
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                int batchSize = 10;
                int totalTickers = tickerArray.size();

                Flux.range(0, totalTickers)
                    .concatMap(i -> {
                        String tickerName = tickerArray.get(i).path("ticker").asText();

                        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                            asyncGetOneStockInfo(tickerName);
                        });

                        futures.add(future);

                        if (futures.size() == batchSize || i == totalTickers - 1) {
                            CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                            allOf.join();
                            futures.clear();
                        }

                        if (i % 500 == 0) {
                            System.out.println("Stock Info Waiting: " + i / 500);
                            return Mono.delay(Duration.ofSeconds(120)).then();
                        }

                        if( i == totalTickers - 1) {
                            System.out.println("Get Stocks of All Tickers Completed");
                        }
                        return Mono.empty();
                    })
                    .then()
                    .subscribe();
            }
        });
    }

    public void getAllTickersTTMStock() {
        Mono<JsonNode> allTickers = getAllActiveTickers();
        System.out.println("Get Stocks of All Tickers TTM Started");
        allTickers.subscribe(tickers -> {
            if (tickers != null && tickers.has("tickers")) {
                JsonNode tickerArray = tickers.path("tickers");
                int totalTickers = tickerArray.size();

                Flux.range(0, totalTickers)
                    .concatMap(i -> {
                        String tickerName = tickerArray.get(i).path("ticker").asText();

                        addStock(tickerName, "TTM", tickerArray.get(i).path("day").path("vw").asDouble());

                        if( i == totalTickers - 1) {
                            System.out.println("Get Stocks of All Tickers TTM Completed");
                        }
                        return Mono.empty();
                    })
                    .then()
                    .subscribe();
            }
        });
    }

    public void getAllTickersFinancialInfo() {
        Mono<JsonNode> allTickers = getAllActiveTickers();
        System.out.println("Get Financial of All Tickers Started");
        allTickers.subscribe(tickers -> {
            if (tickers != null && tickers.has("tickers")) {
                JsonNode tickerArray = tickers.path("tickers");
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                int batchSize = 10;
                int totalTickers = tickerArray.size();

                Flux.range(0, totalTickers)
                    .concatMap(i -> {
                        String tickerName = tickerArray.get(i).path("ticker").asText();

                        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                            asyncGetOneFinancialInfo(tickerName, false);
                            asyncGetOneFinancialInfo(tickerName, true);
                        });

                        futures.add(future);

                        if (futures.size() == batchSize || i == totalTickers - 1) {
                            CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                            allOf.join();
                            futures.clear();
                        }

                        if (i % 100 == 0) {
                            System.out.println("Stock Info Waiting: " + i / 100);
                            return Mono.delay(Duration.ofSeconds(20)).then();
                        }

                        if( i == totalTickers - 1) {
                            System.out.println("Get Financial of All Tickers Completed");
                        }

                        return Mono.empty();
                    })
                    .then()
                    .subscribe();
            }
        });
    }

    public void runDailyTask() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        ScheduledExecutorService schedulerForAllTickers = Executors.newSingleThreadScheduledExecutor();

        Runnable dailyTaskForTickers = () -> CompletableFuture.runAsync(this::getAllTickers);
        Runnable dailyTaskForStockInfo = () ->CompletableFuture.runAsync(this::getAllTickersStock);
        Runnable dailyTaskForFinancialInfo = () ->CompletableFuture.runAsync(this::getAllTickersFinancialInfo);
        Runnable dailyTaskForScreenerInfo = () ->CompletableFuture.runAsync(this::getAllScreenerData);
        Runnable hourlyTaskforTTMStockInfo = () ->CompletableFuture.runAsync(this::getAllTickersTTMStock);
        long period = 24 * 60 * 60; // 24 hours in seconds

        // Schedule the task to run daily
        scheduler.scheduleAtFixedRate(dailyTaskForScreenerInfo,5 * 60, period, TimeUnit.SECONDS);
//        schedulerForAllTickers.scheduleAtFixedRate(dailyTaskForTickers, 60 * 60, period, TimeUnit.SECONDS );
//        scheduler.scheduleAtFixedRate(dailyTaskForStockInfo, 120 * 60, period, TimeUnit.SECONDS);
//        scheduler.scheduleAtFixedRate(dailyTaskForFinancialInfo, 300 * 60, period, TimeUnit.SECONDS);
//        scheduler.scheduleAtFixedRate(hourlyTaskforTTMStockInfo, 20 * 60, 150 * 60, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down executors...");
            scheduler.shutdown();
            schedulerForAllTickers.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
                if (!schedulerForAllTickers.awaitTermination(5, TimeUnit.SECONDS)) {
                    schedulerForAllTickers.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                schedulerForAllTickers.shutdownNow();
            }
            System.out.println("Executors shut down");
        }));
    }

    @PostConstruct
    public void onStartup() {
        runDailyTask();
    }
}