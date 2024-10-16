package com.springboot.stock_analysis.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.springboot.stock_analysis.dto.PerformanceDTO;
import com.springboot.stock_analysis.dto.RatedStockDTO;
import com.springboot.stock_analysis.model.Stocks;
import com.springboot.stock_analysis.repository.StocksRepository;
import com.springboot.stock_analysis.utils.CalculatorUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
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
    public PolygonApiService(WebClient webClient, StocksRepository stocksRepository) throws JsonProcessingException {
        this.webClient = webClient;
        this.stocksRepository = stocksRepository;
        List<String> stockList = this.stocksRepository.findGroupedStocks();
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
        int lastYear = LocalDate.now().getYear();
        int startYear = lastYear - period;
        List<RatedStockDTO> ratedStocks = new ArrayList<RatedStockDTO>();
        for(String ticker: this.allTickers) {
            double stockGrowth = CalculatorUtils.overallGrowth(this.allStocks, ticker, startYear, lastYear);
            if(Double.isNaN(stockGrowth) || Double.isInfinite(stockGrowth)) continue;;
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
        for(int i = 0; i < 30; i++) {
            rtnList.add(ratedStocks.get(i));
        }
        return rtnList;
    }

    public Mono<Map<String, Double>> getAllETFData() {
        return webClient.get()
                .uri("/v2/snapshot/locale/us/markets/stocks/tickers?tickers=SPY,DIA,QQQ&apiKey={apiKey}", apiKey)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(value -> {
                    Map<String, Double> returnValue = new HashMap<>();
                    JsonNode tickers = value.path("tickers");
                    for(JsonNode item: tickers) {
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
        for(String ticker: this.allTickers) {
            double growth = CalculatorUtils.companyGrowthRate(this.allStocks, ticker, lastYear, curYear, "stock");
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
        for(int i = 0; i < 30; i++) {
            rtnList.add(performanceList.get(i));
        }
        return rtnList;
    }

    public Mono<List<Map<String, String>>> getNews(String tickers) {
        if(!tickers.isEmpty()) tickers = tickers.replaceAll("\\s", "");
        return webClient.get()
                .uri("/v2/reference/news?ticker={tickers}&order=desc&limit=5&sort=published_utc&apiKey={apiKey}", tickers, apiKey)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .publishOn(Schedulers.boundedElastic())
                .map(value -> {
                    JsonNode results = value.path("results");
                    List<Map<String, String>> mapArr = new ArrayList<>();
                    for(JsonNode result: results) {
                        Map<String, String> returnValue = new HashMap<>();
                        JsonNode tickerList = result.path("tickers");
                        List<Map<String, Double>> ratedStockDTOList = new ArrayList<>();
                        if(tickerList.isArray()) {
                            String tickersStr = "";
                            for(JsonNode ticker: tickerList) {
                                List<Stocks> relStocks = this.stocksRepository.findByTicker(ticker.toString());
                                if(!relStocks.isEmpty()) {
                                    tickersStr += (tickersStr.isEmpty() ? "" : ",") + relStocks.getFirst().getTicker();
                                }
                            }
                            if(!tickersStr.isEmpty()) {
                                JsonNode summary = webClient.get()
                                        .uri("/v2/snapshot/locale/us/markets/stocks/tickers?tickers={ticker}&apiKey={apiKey}", tickersStr, apiKey)
                                        .retrieve()
                                        .bodyToMono(JsonNode.class)
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
                });
    }

    public Mono<Map<String, String>> getStockData(String ticker) {
        JsonNode summary = webClient.get()
                .uri("/v2/snapshot/locale/us/markets/stocks/tickers/{ticker}?apiKey={apiKey}", ticker, apiKey)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
        double stockPrice = 0.0;
        double changePerc = 0.0;
        double changePrice = 0.0;
        Map<String, String> returnValue = new HashMap<String, String>();
        if(summary != null) {
            stockPrice = summary.path("ticker")
                    .path("day")
                    .path("vw")
                    .asDouble();
            changePerc = summary.path("ticker")
                    .path("todaysChangePerc").asDouble();
            changePrice = summary.path("ticker")
                    .path("todaysChange").asDouble();
        }
        returnValue.put("stockPrice", String.valueOf(stockPrice));
        returnValue.put("changePerc", String.valueOf(changePerc));
        returnValue.put("changePrice", String.valueOf(changePrice));

        int endYear = LocalDate.now().getYear();
        int startYear = endYear - 1;
        int growthRating = CalculatorUtils.growthEvaluation(allStocks, ticker, startYear, endYear);
        int valueRating = CalculatorUtils.valueCriteria(allStocks, ticker, startYear, endYear);
        int performanceRating = CalculatorUtils.performanceEvaluation(allStocks, ticker, startYear, endYear);
        double growth = (growthRating / 10.0) * 4;
        double value = (valueRating / 10.0) * 2.7;
        double performance = (performanceRating / 10.0) * 3.3;
        double stockScore = growth + value + performance;
        returnValue.put("stockScore", String.valueOf(stockScore));
        returnValue.put("growth", String.valueOf(growthRating));
        returnValue.put("value", String.valueOf(value));
        returnValue.put("performance", String.valueOf(performanceRating));
        return webClient.get()
                .uri("/v3/reference/tickers/{ticker}?apiKey={apiKey}", ticker, apiKey)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(val -> {
                    String marketCap = "";
                    marketCap = String.format("%.2fB", val.path("results")
                            .path("market_cap").asDouble() / 1000000000.0);
                    double epsTTM = CalculatorUtils.findStockByTickerAndYear(allStocks, ticker, "TTM").getEps();
                    returnValue.put("marketCap", marketCap);
                    returnValue.put("epsTTM", String.valueOf(epsTTM));

                    double revenueGrowth = CalculatorUtils.companyGrowthRate(allStocks, ticker, startYear, endYear, "revenue");
                    double stockGrowth = CalculatorUtils.companyGrowthRate(allStocks, ticker, startYear, endYear, "stock");
                    Stocks startStocks = CalculatorUtils.findStockByTickerAndYear(allStocks, ticker, String.valueOf(startYear));
                    Stocks ttmData = CalculatorUtils.findStockByTickerAndYear(allStocks, ticker, "TTM");
                    Stocks lastStocks = CalculatorUtils.findStockByTickerAndYear(allStocks, ticker, String.valueOf(endYear));
                    if(lastStocks.getTicker().isEmpty()) lastStocks = ttmData;
                    double peg = CalculatorUtils.cagrForCompanyNumerical(allStocks, startStocks, lastStocks, ticker);
                    returnValue.put("revenueGrowthRate", String.valueOf(revenueGrowth));
                    returnValue.put("stockGrowthRate", String.valueOf(stockGrowth));
                    returnValue.put("pegRatio", String.valueOf(peg));
                    return returnValue;
                });
    }

    public Mono<Map<String, Double>> getChartData(String ticker) {
        LocalDate curDate = LocalDate.now();
        LocalDate fiveYearsAgoDate = curDate.minusYears(5);
        return webClient.get()
                .uri("/v2/aggs/ticker/{ticker}/range/1/day/{from}/{to}?sort=desc&apiKey={apiKey}", ticker, fiveYearsAgoDate, curDate, apiKey)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(val -> {
                    JsonNode results = val.path("results");
                    Map<String, Double> returnValue = new HashMap<>();
                    double maxValue = 0.0;
                    for(JsonNode result: results) {
                        if(result.path("vw").asDouble() > maxValue) maxValue = result.path("vw").asDouble();
                        LocalDate prevDate = curDate.minusDays(1);
                        LocalDate fiveDate = curDate.minusDays(5);
                        LocalDate monthDate = curDate.minusMonths(1);
                        LocalDate sixMonthDate = curDate.minusMonths(6);
                        LocalDate ydtDate = curDate.withDayOfYear(1);
                        LocalDate yearDate = curDate.minusYears(1);
                        LocalDate fiveYearDate = curDate.minusYears(5);
                        double milliseconds = result.path("t").asDouble();
                        if(CalculatorUtils.getLocalDate(milliseconds).isEqual(prevDate)) {
                            returnValue.put("prevDate", result.path("vw").asDouble());
                        } else if(CalculatorUtils.getLocalDate(milliseconds).isEqual(fiveDate)) {
                            returnValue.put("fiveDate", result.path("vw").asDouble());
                        } else if(CalculatorUtils.getLocalDate(milliseconds).isEqual(monthDate)) {
                            returnValue.put("monthDate", result.path("vw").asDouble());
                        } else if(CalculatorUtils.getLocalDate(milliseconds).isEqual(sixMonthDate)) {
                            returnValue.put("sixMonthDate", result.path("vw").asDouble());
                        } else if(CalculatorUtils.getLocalDate(milliseconds).isEqual(ydtDate)) {
                            returnValue.put("ydtDate", result.path("vw").asDouble());
                        } else if(CalculatorUtils.getLocalDate(milliseconds).isEqual(yearDate)) {
                            returnValue.put("yearDate", result.path("vw").asDouble());
                        } else if(CalculatorUtils.getLocalDate(milliseconds).isEqual(fiveYearDate)) {
                            returnValue.put("fiveYearDate", result.path("vw").asDouble());
                        }

                    }
                    returnValue.put("max", maxValue);
                    return returnValue;
                });
    }
}
