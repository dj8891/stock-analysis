package com.springboot.stock_analysis.service;

import com.springboot.stock_analysis.model.Tickers;
import com.springboot.stock_analysis.model.Stocks;
import com.springboot.stock_analysis.repository.TickersRepository;
import com.springboot.stock_analysis.repository.StocksRepository;
import com.springboot.stock_analysis.utils.CalculatorUtils;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
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
import reactor.netty.http.client.PrematureCloseException;
import reactor.util.retry.Retry;

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
    private TickersRepository tickersRepository = null;
    private StocksRepository stocksRepository = null;
    public ScheduledService(WebClient webClient, TickersRepository tickersRepository, StocksRepository stocksRepository) {
        this.webClient = webClient;
        this.tickersRepository = tickersRepository;
        this.stocksRepository = stocksRepository;
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
        CompletableFuture.runAsync(() -> {
            try {
                LocalDate curDate = LocalDate.now();
                LocalDate fiveYearsAgo = curDate.minusYears(6);
                webClient.get()
                        .uri("/v2/aggs/ticker/{ticker}/range/1/day/{fiveYears}/{curDate}?adjusted=true&sort=asc&apiKey={apiKey}", ticker, fiveYearsAgo.toString(), curDate.toString(), apiKey)
                        .retrieve()
                        .bodyToMono(JsonNode.class)
                        .onErrorResume(e -> {
                            System.out.println("ERROR (asyncGetOneStockInfo): [" + ticker + "]" + e.getMessage());
                            return Mono.empty();
                        })
                        .subscribe(response -> {
                            JsonNode results = response.path("results");

                            if (results.isArray() && results.size() > 0) {
                                JsonNode firstResult = results.get(0);
                                String year = CalculatorUtils.convertTimestampToDate(firstResult.path("t").asLong());

                                if (!year.equals(String.valueOf(Year.now().getValue()))) {
                                    addStock(ticker, year, firstResult.path("vw").asDouble());
                                }
                            } else {
                                System.out.println("No results found for ticker: " + ticker);
                            }
                        });
            } catch (Exception e) {
                System.out.println("ERROR (asyncGetOneStockInfo): [" + ticker + "]" + e.getMessage());
            }
        });
    }

    public void asyncGetOneFinancialInfo(String ticker, boolean isTTM) {
        CompletableFuture.runAsync(() -> {
            try {
                String tmpUri = "/vX/reference/financials?ticker={ticker}&timeframe=annual&limit=6&apiKey={apiKey}";

                if (isTTM) {
                    tmpUri = "/vX/reference/financials?ticker={ticker}&timeframe=ttm&apiKey={apiKey}";
                }

                webClient.get()
                        .uri(tmpUri, ticker, apiKey)
                        .retrieve()
                        .bodyToMono(JsonNode.class)
                        .onErrorResume(e -> {
                            System.out.println("ERROR: Get One Financial Info Failed");
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
        });
    }

    public void getInfo() {
        // Use a CompletableFuture to execute the async logic
        CompletableFuture.runAsync(() -> {
            getAllActiveTickers()
                    .flatMap(tickers -> {
                        if (tickers != null && tickers.has("tickers")) {
                            JsonNode allTickers = tickers.path("tickers");
                            System.out.println("LOG: Getting info started!");
                            return Flux.fromIterable(allTickers)
                                    .index()  // Get the index to control batching
                                    .flatMap(indexed -> {
                                        int i = Math.toIntExact(indexed.getT1());
                                        JsonNode tickerData = indexed.getT2();
                                        String ticker = tickerData.path("ticker").asText();

                                        addStock(ticker, "TTM", tickerData.path("day").path("vw").asDouble());
                                        asyncGetOneFinancialInfo(ticker, true);

                                        // Introduce a non-blocking delay after every 50 tickers
                                        if (i > 0 && i % 50 == 0) {
                                            return Mono.delay(Duration.ofMillis(500));
                                        }
                                        return Mono.empty();
                                    })
                                    .then(Mono.fromRunnable(() -> {
                                        System.out.println("LOG: Getting Stock TTM Completed!");
                                        System.out.println("LOG: Getting Financial Info Completed!");
                                    }))
                                    .onErrorResume(e -> {
                                        System.out.println("LOG: ERROR____: " + e.getMessage());
                                        return Mono.empty();
                                    });
                        } else {
                            // If tickers can't be fetched, fall back to using the local repository
                            System.out.println("LOG: CAN'T GET TICKERS");
                            return Flux.fromIterable(tickersRepository.findAll())
                                    .index()
                                    .flatMap(indexed -> {
                                        int i = Math.toIntExact(indexed.getT1());
                                        Tickers tickerEntity = indexed.getT2();
                                        String ticker = tickerEntity.getShortName();

                                        asyncGetOneFinancialInfo(ticker, true);

                                        // Make individual requests for each ticker
                                        return webClient.get()
                                                .uri("/v2/snapshot/locale/us/markets/stocks/tickers/{ticker}?apiKey={apiKey}", ticker, apiKey)
                                                .retrieve()
                                                .bodyToMono(JsonNode.class)
                                                .onErrorResume(e -> {
                                                    System.out.println("ERROR: Get Ticker Data By Ticker");
                                                    return Mono.empty();
                                                })
                                                .flatMap(tickerData -> {
                                                    if (tickerData != null) {
                                                        addStock(ticker, "TTM", tickerData.path("ticker").path("day").path("vw").asDouble());
                                                    } else {
                                                        System.out.println("ERROR: Ticker Data Get Failed!");
                                                    }

                                                    // Introduce a longer delay after every 50 tickers
                                                    if (i > 0 && i % 50 == 0) {
                                                        return Mono.delay(Duration.ofMillis(500));
                                                    }
                                                    return Mono.empty();
                                                });
                                    })
                                    .then(Mono.fromRunnable(() -> {
                                        System.out.println("LOG: Getting Financial Info Completed!");
                                    }));
                        }
                    })
                    .then(Mono.delay(Duration.ofMinutes(30)))
                    .doOnSuccess(ignored -> this.getInfo())
                    .doOnError(e -> System.out.println("ERROR (getInfo): " + e.getMessage()))
                    .subscribe(); // Start the reactive flow asynchronously
        });
    }

    public void runDailyTask() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        ScheduledExecutorService schedulerForAllTickers = Executors.newSingleThreadScheduledExecutor();
        Runnable dailyTask = () -> CompletableFuture.runAsync(() -> {
            System.out.println("GET Annual Data Started!");
            try {
                getAllActiveTickers()
                        .flatMap(tickers -> {
                            if (tickers != null && tickers.has("tickers")) {
                                JsonNode allTickers = tickers.path("tickers");
                                return Flux.fromIterable(allTickers)
                                        .index()  // Add index to control batching
                                        .flatMap(indexed -> {
                                            int i = Math.toIntExact(indexed.getT1());
                                            JsonNode tickerData = indexed.getT2();
                                            String ticker = tickerData.path("ticker").asText();

                                            asyncGetOneStockInfo(ticker);
                                            asyncGetOneFinancialInfo(ticker, false);

                                            // Add a non-blocking delay after every 50 tickers
                                            if (i > 0 && i % 50 == 0) {
                                                return Mono.delay(Duration.ofMillis(500));
                                            }
                                            return Mono.empty();
                                        })
                                        .then(Mono.fromRunnable(() -> {
                                            System.out.println("GET Annual Data completed!");
                                        }));
                            } else {
                                System.out.println("LOG: CAN'T GET TICKERS");
                                return Flux.fromIterable(tickersRepository.findAll())
                                        .index()
                                        .flatMap(indexed -> {
                                            int i = Math.toIntExact(indexed.getT1());
                                            Tickers tickerEntity = indexed.getT2();
                                            String ticker = tickerEntity.getShortName();

                                            asyncGetOneStockInfo(ticker);
                                            asyncGetOneFinancialInfo(ticker, false);

                                            // Delay after every 50 tickers
                                            if (i > 0 && i % 50 == 0) {
                                                return Mono.delay(Duration.ofMillis(500));
                                            }
                                            return Mono.empty();
                                        })
                                        .then(Mono.fromRunnable(() -> {
                                            System.out.println("GET Annual Data completed!");
                                        }));
                            }
                        })
                        .doOnError(e -> System.out.println("ERROR: GET Annual Data " + e.getMessage()))
                        .subscribe();
            } catch (Exception e) {
                System.out.println("ERROR: GET Annual Data " + e.getMessage());
            }
        });

        Runnable dailyTaskForTickers = () -> CompletableFuture.runAsync(this::getAllTickers);
        // Calculate the initial delay until the first execution
        long period = 24 * 60 * 60; // 24 hours in seconds

        // Schedule the task to run daily
        scheduler.scheduleAtFixedRate(dailyTask, 5 * 60, period, TimeUnit.SECONDS);
        schedulerForAllTickers.scheduleAtFixedRate(dailyTaskForTickers, 80 * 60, period, TimeUnit.SECONDS );

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
            System.out.println("Executors shut down.");
        }));
    }

    @PostConstruct
    public void onStartup() {
        getInfo();
        runDailyTask();
    }
}