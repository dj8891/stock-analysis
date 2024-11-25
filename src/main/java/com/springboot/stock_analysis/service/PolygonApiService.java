package com.springboot.stock_analysis.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.springboot.stock_analysis.dto.PageInfoDTO;
import com.springboot.stock_analysis.dto.PerformanceDTO;
import com.springboot.stock_analysis.dto.RatedStockDTO;
import com.springboot.stock_analysis.model.Stocks;
import com.springboot.stock_analysis.model.Tickers;
import com.springboot.stock_analysis.repository.StocksRepository;
import com.springboot.stock_analysis.repository.TickersRepository;
import com.springboot.stock_analysis.utils.CalculatorUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.*;
import java.util.*;

@Service
public class PolygonApiService {
    @Value("${polygon.api.key}")
    String apiKey;
    @Value("${growth.period}")
    int period;

    private final WebClient webClient;
    private final StocksRepository stocksRepository;
    private final List<String> allTickers;
    private final Map<String, List<Stocks>> allStocks = new HashMap<>();
    private final TickersRepository tickersRepository;
    private final CalculatorUtils calculatorUtils;
    public PolygonApiService(WebClient webClient, StocksRepository stocksRepository, TickersRepository tickersRepository, CalculatorUtils calculatorUtils) throws JsonProcessingException {
        this.webClient = webClient;
        this.calculatorUtils = calculatorUtils;
        this.stocksRepository = stocksRepository;
        List<String> stockList = this.stocksRepository.findGroupedStocks();
        this.tickersRepository = tickersRepository;
        for(String stock: stockList) {
            String jsonString = "[" + stock + "]";
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                JsonNode jsonNode = objectMapper.readTree(jsonString);
                ArrayList<Stocks> stocks = new ArrayList<>();
                String ticker = "";
                for(JsonNode node: jsonNode) {
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
        this.allTickers = this.stocksRepository.findDistinctByTicker();
    }

    public Map<String, List<Stocks>> getAllStocks() {
        return this.allStocks;
    }
    public List<String> getTest() {
        return this.stocksRepository.findGroupedStocks();
    }
    /**
     * get Sorted stocks ranking with tickers
     * @return Array of stocks sorted
     */
    public List<RatedStockDTO> getRatedStocks() {

        List<RatedStockDTO> ratedStocks = new ArrayList<RatedStockDTO>();
        if(!this.allStocks.isEmpty()) {
            for(String ticker: this.allTickers) {
                double stockGrowth = this.calculatorUtils.overallGrowth(this.allStocks, ticker);
                if(Double.isNaN(stockGrowth) || Double.isInfinite(stockGrowth)) continue;
                if(this.calculatorUtils.findStockByTickerAndYear(allStocks, ticker, "TTM").getStock() < 10) continue;
                RatedStockDTO ratedStockDTO = new RatedStockDTO(ticker, stockGrowth);
                ratedStocks.add(ratedStockDTO);
            }
            ratedStocks.sort(new Comparator<RatedStockDTO>() {
                @Override
                public int compare(RatedStockDTO o1, RatedStockDTO o2) {
                    return Double.compare(o2.getRating(), o1.getRating());
                }
            });
            List<RatedStockDTO> rtnList = new ArrayList<>();
            for(int i = 0; i < ratedStocks.size(); i++) {
                if(i == 30) break;
                rtnList.add(ratedStocks.get(i));
            }
            return rtnList;
        }
        return null;
    }

    public Mono<Map<String, Double>> getAllETFData() {
            return webClient.get()
                    .uri("/v2/snapshot/locale/us/markets/stocks/tickers?tickers=SPY,DIA,QQQ,IWM&apiKey={apiKey}", apiKey)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .map(value -> {
                        Map<String, Double> returnValue = new HashMap<>();
                        JsonNode tickers = value.path("tickers");
                        for (JsonNode item : tickers) {
                            String ticker = item.path("ticker").asText();
                            double todaysChangePerc = item.path("todaysChangePerc").asDouble();
                            returnValue.put(ticker, todaysChangePerc);
                        }
                        return returnValue;
                    });
    }

    public List<PerformanceDTO> getPerformanceList() {
        int curYear = LocalDate.now().getYear();
        int lastYear = curYear - 1;
        List<PerformanceDTO> performanceList = new ArrayList<>();
        if(!this.allStocks.isEmpty()) {
            for(String ticker: this.allTickers) {
                Stocks ttmStock = this.calculatorUtils.findStockByTickerAndYear(this.allStocks, ticker, "TTM");
                if(ttmStock.getStock() < 10) continue;
                double growth = this.calculatorUtils.companyGrowthRate(this.allStocks, ticker, (lastYear - 1), lastYear, "stock");
                if(Double.isNaN(growth) || Double.isInfinite(growth)) continue;
                PerformanceDTO performanceDTO = new PerformanceDTO(ticker, growth);
                performanceList.add(performanceDTO);
            }
            performanceList.sort(new Comparator<PerformanceDTO>() {
                @Override
                public int compare(PerformanceDTO o1, PerformanceDTO o2) {
                    return Double.compare(o2.getGrowth(), o1.getGrowth());
                }
            });
            List<PerformanceDTO> rtnList = new ArrayList<>();
            for(int i = 0; i < performanceList.size(); i++) {
                if( i == 30 ) break;
                rtnList.add(performanceList.get(i));
            }
            return rtnList;
        }
        return null;
    }

    private List<Map<String, String>> processNews(JsonNode value) {
        JsonNode results = value.path("results");
        List<Map<String, String>> mapArr = new ArrayList<>();
        for(JsonNode result: results) {
            Map<String, String> returnValue = new HashMap<>();
            JsonNode tickerList = result.path("tickers");
            StringBuilder tickersStr = new StringBuilder();
            for(JsonNode ticker: tickerList) {
                tickersStr.append((tickersStr.isEmpty()) ? "" : ",").append(ticker);
            }
            List<Map<String, Double>> ratedStockDTOList = new ArrayList<>();

            if(!tickersStr.isEmpty()) {
                JsonNode summary = webClient.get()
                        .uri("/v2/snapshot/locale/us/markets/stocks/tickers?tickers={ticker}&apiKey={apiKey}", tickersStr.toString(), apiKey)
                        .retrieve()
                        .bodyToMono(JsonNode.class)
                        .onErrorResume( e -> {
                            return Mono.empty();
                        })
                        .block();
                double changePerc = 0.0;
                if (summary != null) {
                    for(JsonNode node: summary.path("tickers")) {
                        changePerc = node.path("todaysChangePerc").asDouble();
                        String nodeTicker = node.path("ticker").asText();
                        Map<String, Double> tmpMap = new HashMap<>();
                        tmpMap.put('"' + nodeTicker + '"', changePerc);
                        ratedStockDTOList.add(tmpMap);
                    }
                }
            }

            LocalDateTime curDate = LocalDateTime.now();
            LocalDateTime publishDate = ZonedDateTime.parse(result.path("published_utc").asText())
                    .withZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDateTime();
            Duration duration = Duration.between(publishDate, curDate);
            long totalSeconds = duration.getSeconds();
            long hours = totalSeconds / 3600;    // 1 hour = 3600 seconds
            long minutes = (totalSeconds % 3600) / 60; // Remaining minutes
            long seconds = totalSeconds % 60;    // Remaining seconds

            // Build user-friendly format
            StringBuilder timeFormatted = new StringBuilder();
            if (hours > 0) {
                timeFormatted.append(hours).append(" hour").append(hours > 1 ? "s " : " ");
            }
            if (minutes > 0) {
                timeFormatted.append(minutes).append(" minute").append(minutes > 1 ? "s " : " ");
            }
            if (seconds > 0 || (hours == 0 && minutes == 0)) {
                timeFormatted.append(seconds).append(" second").append(seconds > 1 ? "s" : "");
            }
            returnValue.put("publisher", result.path("publisher").path("name").asText());
            returnValue.put("publisher_url", result.path("publisher").path("homepage_url").asText());
            returnValue.put("title", result.path("title").asText());
            returnValue.put("description", result.path("description").asText());
            returnValue.put("article_url", result.path("article_url").asText());
            returnValue.put("image_url", result.path("image_url").asText());
            returnValue.put("published_utc", timeFormatted.toString().trim());
            if(!ratedStockDTOList.isEmpty()) returnValue.put("relTickers", ratedStockDTOList.toString());
            mapArr.add(returnValue);
        }
        return mapArr;
    }

    private Mono<JsonNode> fetchNewsWithCursor(String tickers, String cursor, int limit) {
        return webClient.get()
                .uri("/v2/reference/news?ticker={tickers}&order=desc&limit={limit}&sort=published_utc&cursor={cursor}&apiKey={apiKey}", tickers, limit, cursor, apiKey)
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    public Mono<List<Map<String, String>>> getNews(String tickers, String limit, int page) {
        if(!tickers.isEmpty()) tickers = tickers.replaceAll("\\s", "");
        if(page == 0) {  // no pagination
            return webClient.get()
                    .uri("/v2/reference/news?ticker={tickers}&order=desc&limit={limit}&sort=published_utc&apiKey={apiKey}", tickers, limit, apiKey)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .publishOn(Schedulers.boundedElastic())
                    .map(this::processNews);
        } else { // pagination
            String finalTickers = tickers;
            return Mono.just(new PageInfoDTO("", 0))
                    .expand(pageInfo -> {
                        if(pageInfo.getPageNumber() >= page) return Mono.empty();
                        return this.fetchNewsWithCursor(finalTickers, pageInfo.getCursor(), Integer.parseInt(limit))
                                .flatMap(response -> {
                                    if (response.path("next_url").isMissingNode() || response.path("results").isEmpty()) {
                                        return Mono.empty();  // No more pages to fetch
                                    }
                                    // Increment page count and continue fetching with next cursor
                                    List<String> arr = List.of(response.path("next_url").asText().split("="));
                                    return Mono.just(new PageInfoDTO(arr.size() > 1 ? arr.get(1) : "", pageInfo.getPageNumber() + 1));
                                });
                    }).filter(pageInfoDTO -> pageInfoDTO.getPageNumber() == page)
                    .flatMap(pageInfoDTO -> fetchNewsWithCursor(finalTickers, pageInfoDTO.getCursor(), Integer.parseInt(limit)))
                    .publishOn(Schedulers.boundedElastic())
                    .map(this::processNews)
                    .flatMap(Flux::fromIterable) // Flatten the emitted lists to individual maps
                    .collectList();
        }
    }

    public Mono<Map<String, String>> getStockData(String ticker) {
        JsonNode summary = webClient.get()
                .uri("/v2/snapshot/locale/us/markets/stocks/tickers/{ticker}?apiKey={apiKey}", ticker, apiKey)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .onErrorResume( e -> {
                    return Mono.empty();
                })
                .block();
        if(summary != null) {
            double stockPrice = 0.0;
            double changePerc = 0.0;
            double changePrice = 0.0;
            Map<String, String> returnValue = new HashMap<String, String>();

            stockPrice = summary.path("ticker")
                    .path("day")
                    .path("vw")
                    .asDouble();
            changePerc = summary.path("ticker")
                    .path("todaysChangePerc").asDouble();
            changePrice = summary.path("ticker")
                    .path("todaysChange").asDouble();

            returnValue.put("stockPrice", String.valueOf(stockPrice));
            returnValue.put("changePerc", String.valueOf(changePerc));
            returnValue.put("changePrice", String.valueOf(changePrice));

            int endYear = LocalDate.now().getYear() - 1;
            int growthRating = this.calculatorUtils.growthEvaluation(allStocks, ticker, endYear);
            int valueRating = this.calculatorUtils.valueCriteria(allStocks, ticker, endYear);
            int performanceRating = this.calculatorUtils.performanceEvaluation(allStocks, ticker, endYear);
            double growth = (growthRating / 10.0) * 4;
            double value = (valueRating / 10.0) * 2.7;
            double performance = (performanceRating / 10.0) * 3.3;
            double stockScore = growth + value + performance;
            returnValue.put("stockScore", String.valueOf(stockScore));
            returnValue.put("growth", String.valueOf(growthRating));
            returnValue.put("value", String.valueOf(valueRating));
            returnValue.put("performance", String.valueOf(performanceRating));
            Tickers tickerModel = this.tickersRepository.findByShortName(ticker).orElse(new Tickers(ticker, ""));
            String company = tickerModel.getFullName();
            returnValue.put("company", company);
            return webClient.get()
                    .uri("/v3/reference/tickers/{ticker}?apiKey={apiKey}", ticker, apiKey)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .map(val -> {
                        String marketCap = "";
                        marketCap = String.format("%.2fB", val.path("results")
                                .path("market_cap").asDouble() / 1000000000.0);
                        double epsTTM = this.calculatorUtils.findStockByTickerAndYear(allStocks, ticker, "TTM").getEps();
                        returnValue.put("marketCap", marketCap);
                        returnValue.put("epsTTM", String.valueOf(epsTTM));

                        double revenueGrowth = this.calculatorUtils.companyGrowthRate(allStocks, ticker, endYear - 2, endYear, "revenue");
                        double stockGrowth = this.calculatorUtils.companyGrowthRate(allStocks, ticker, endYear - 2, endYear, "stock");
                        Stocks startStocks = this.calculatorUtils.findStockByTickerAndYear(allStocks, ticker, String.valueOf(endYear - 4));
                        Stocks lastStocks = this.calculatorUtils.findStockByTickerAndYear(allStocks, ticker, String.valueOf(endYear));

                        double peg = 0;

                        double epsPrice = lastStocks.getEps();
                        double stockP = lastStocks.getStock();
                        double pe = epsPrice == 0 ? 0 : stockP / epsPrice;

                        if(startStocks.getEps() != 0)
                         peg = this.calculatorUtils.cagrForCompanyNumerical(allStocks, startStocks, lastStocks, ticker);
                        returnValue.put("revenueGrowthRate", String.valueOf(revenueGrowth));
                        returnValue.put("stockGrowthRate", String.valueOf(stockGrowth));
                        returnValue.put("pegRatio", String.valueOf(peg));
                        returnValue.put("peRatio", String.valueOf(pe));
                        return returnValue;
                    });
        }
        else return null;
    }

    public Mono<JsonNode> getChartData(String ticker, String date) {
        LocalDate curDate = LocalDate.now();
        LocalDate destDate = switch (date) {
            case "1-D" -> curDate.minusDays(1);
            case "5-D" -> curDate.minusDays(5);
            case "1-M" -> curDate.minusMonths(1);
            case "6-M" -> curDate.minusMonths(6);
            case "YTD" -> curDate.withDayOfYear(1);
            case "1-Y" -> curDate.minusYears(1);
            case "5-Y" -> curDate.minusYears(5);
            default -> LocalDate.of(2000, 1, 1);
        };
        String timespan = "day";
        if(date.equals("1-D") || date.equals("5-D") || date.equals("1-M")) {
            timespan = "hour";
        }
        return webClient.get()
                .uri("/v2/aggs/ticker/{ticker}/range/1/{timespan}/{from}/{to}?sort=asc&limit=50000&apiKey={apiKey}", ticker, timespan, destDate, curDate, apiKey)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(val -> {
                    return val.path("results");
                });
    }

    public List<Tickers> getTickerList(String ticker) {
        return this.tickersRepository.findByShortNameAndFullName(ticker);
    }
}
