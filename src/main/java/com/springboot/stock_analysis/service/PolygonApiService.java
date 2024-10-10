package com.springboot.stock_analysis.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.springboot.stock_analysis.dto.RatedStockDTO;
import com.springboot.stock_analysis.model.Stocks;
import com.springboot.stock_analysis.repository.StocksRepository;
import com.springboot.stock_analysis.utils.CalculatorUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class PolygonApiService {
    @Value("${polygon.api.key}")
    String apiKey;
    @Value("${growth.period}")
    int period;

    private final WebClient webClient;
    private final StocksRepository stocksRepository;
    public PolygonApiService(WebClient webClient, StocksRepository stocksRepository) {
        this.webClient = webClient;
        this.stocksRepository = stocksRepository;
    }

    /**
     * get Distinct Tickers from database
     * @return tickers' array by distinct
     */
    public List<String> getTickersList() {
        return this.stocksRepository.findDistinctByTicker();
    }

    /**
     * get Sorted stocks ranking with tickers
     * @return Array of stocks sorted
     */
    public List<RatedStockDTO> getRatedStocks() {
        List<String> tickerList = this.getTickersList();
        int lastYear = LocalDate.now().getYear();
        int startYear = lastYear - period + 1;
        for(String ticker: tickerList) {
            double stockGrowth = this.overallGrowth(ticker, startYear, lastYear);
        }
    }

    /**
     *
     * @param ticker: ticker
     * @param startYear: start year being evalueated
     * @param lastYear: last year being evaluated
     *
     * @return overall growth rating
     */
    public double overallGrowth(String ticker, int startYear, int lastYear) {
        int growthRating = this.growthEvaluation(ticker, startYear, lastYear);
        int valueRating = this.valueCriteria(ticker, startYear, lastYear);
        int performanceRating = this.performanceEvaluation();
        return 1.0;
    }

    /**
     * get company growthEvaluation
     * @param ticker : ticker
     * @param startYear: start Year
     * @param lastYear: last Year
     * @return :
     */
    private Integer growthEvaluation(String ticker, int startYear, int lastYear) {
        int growthEvaluating = 1;
        Stocks ttmData = this.stocksRepository.findByTickerAndYear(ticker, "ttm").orElse(new Stocks());
        double revTTM = ttmData.getRevenue();
        Stocks prvData = this.stocksRepository.getPrevYearStock(ticker).orElse(new Stocks());
        double revP = prvData.getRevenue();
        double revenueGrowth = this.companyGrowthRate(ticker, startYear, lastYear, true);
        double epsGrowth = this.companyGrowthRate(ticker, startYear, lastYear, false);

        if ((revenueGrowth < 0.0)) {
            growthEvaluating = 2;
        }
        if ((revenueGrowth == 0.0) && (epsGrowth == 0.0)) {
            growthEvaluating = 3;
        }
        if (((revenueGrowth < 10.0) && (revenueGrowth > 0.0))) {
            growthEvaluating = 4;
        }
        if (revenueGrowth > 10.0 && (revTTM < (1.2 * revP))) {
            growthEvaluating = 5;
        }
        if ((revenueGrowth > 10.0) && (revenueGrowth < 15.0)) {
            growthEvaluating = 6;
        }
        if ((revenueGrowth > 15.0) && (revenueGrowth < 20.0)) {
            if (epsGrowth > 15.0) {
                growthEvaluating = 8;
            } else {
                growthEvaluating = 7;
            }
        }
        if (revenueGrowth > 20.0) {
            growthEvaluating = 9;
        }
        if ((revenueGrowth > 20.0) && (epsGrowth > 20.0)) {
            growthEvaluating = 10;
        }
        return growthEvaluating;
    }

    /**
     * @param ticker
     * @param startYear
     * @param lastYear
     * @param isRevenue : true: get revenue rate, else, eps rate
     * @return get growth rate per year
     */
    private Double companyGrowthRate(String ticker, int startYear, int lastYear, boolean isRevenue) {
        Stocks startStocks = this.stocksRepository.findByTickerAndYear(ticker, String.valueOf(startYear)).orElse(new Stocks());
        Stocks ttmData = this.stocksRepository.findByTickerAndYear(ticker, "ttm").orElse(new Stocks());
        Stocks lastStocks = this.stocksRepository.findByTickerAndYear(ticker, String.valueOf(lastYear)).orElse(ttmData);
        double lastData = isRevenue ? lastStocks.getRevenue() : lastStocks.getEps();
        double firstData = isRevenue ? startStocks.getRevenue() : startStocks.getEps();
        double z = Math.pow(lastData / firstData, 1.0 / (lastYear - startYear + 1));
        return (z - 1.0) * 100;
    }

    public Integer valueCriteria(String ticker, int startYear, int lastYear) {
        Stocks startStocks = this.stocksRepository.findByTickerAndYear(ticker, String.valueOf(startYear)).orElse(new Stocks());
        Stocks ttmData = this.stocksRepository.findByTickerAndYear(ticker, "ttm").orElse(new Stocks());
        Stocks lastStocks = this.stocksRepository.findByTickerAndYear(ticker, String.valueOf(lastYear)).orElse(ttmData);
        double stockPrice = lastStocks.getStock();
        double epsPrice = lastStocks.getEps();
        double pe = stockPrice / epsPrice;
        double epsGrowth = this.companyGrowthRate(ticker, startYear, lastYear, false);
        double peg = this.cagrForCompanyNumerical(startStocks, lastStocks, ticker, startYear, lastYear);
    }

    public Double cagrForCompanyNumerical(Stocks startStock, Stocks lastStock, String ticker, int startYear, int lastYear) {
        double stockPrice = lastStock.getStock();
        double startEpsValue = startStock.getEps();
        double lastEpsValue = lastStock.getEps();
        double peRatio = stockPrice / lastEpsValue;
        if ((startEpsValue > 0) && (lastEpsValue < 0)) {
            return 1.00;
        }
        if ((startEpsValue < 0) && (lastEpsValue > 0) && (peRatio < 50.0)) { // and PE ratio is less than 50
            return 4.00;
        }
        Stocks ttmData = this.stocksRepository.findByTickerAndYear(ticker, "ttm").orElse(new Stocks());
        double epsTtm = ttmData.getEps();
        return this.cagr(lastEpsValue, startEpsValue, epsTtm, stockPrice);
    }

    public Double cagr(double lastEpsValue, double startEpsValue, double epsTtm, double stockPrice) {
        double value1 = 0.0;
        if (lastEpsValue < 0 && startEpsValue < 0) {
            double currentEps = Math.abs(lastEpsValue);
            double prevEps = Math.abs(startEpsValue);
            value1 = (prevEps / currentEps);
        } else {
            value1 = (lastEpsValue / startEpsValue);
        }
        double z = Math.pow(value1, 1.0 / 5);
        double growthRate = (z - 1.0) * 100;
        if (growthRate > 25.0) {
            growthRate = 25.0;
        }
        double pe = (stockPrice / lastEpsValue);
        return pe / growthRate;
    }
    public Integer performanceEvaluation() {

    }
    public Mono<String> getDowData(String stockSymbol) {
        return webClient.get()
                .uri("/v3/snapshot/indices?ticker.any_of=I:DJI&limit=10&apiKey={apiKey}", apiKey)
                .retrieve()
                .bodyToMono(String.class);
    }

    public Mono<JsonNode> getAllStockData() {
        return webClient.get()
                .uri("/v2/snapshot/locale/us/markets/stocks/tickers?apiKey={apiKey}", apiKey)
                .retrieve()
                .bodyToMono(JsonNode.class);
    }
    public JsonNode getStockData(String symbol) {
        try {
            return webClient.get()
                    .uri("/v2/snapshot/locale/us/markets/stocks/tickers/{symbol}?apiKey={apiKey}", symbol, apiKey)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
        } catch (Exception e) {
            return null;
        }
    }
    /**
     * retrieving revenue array data by symbol, timeframe
     * @param stockSymbol: ticker, eg, AAPL
     * @param timeframe: timeframe, eg: annual, quarterly
     * @return Array of revenue
     */
    public Mono<JsonNode> getRevenuesByTickerAndTimeframe(String stockSymbol, String timeframe) {
        return webClient.get()
                .uri("/vX/reference/financials?ticker={symbol}&timeframe={timeframe}&apiKey={apiKey}", stockSymbol, timeframe, apiKey)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .subscribeOn(Schedulers.boundedElastic())
                .map(value -> value.path("results"))
                .onErrorReturn(JsonNodeFactory.instance.objectNode().put("error", "failed to fetch revenue"));
    }

    public Mono<Integer> getRevenueGrowth(String symbol) {
        return getRevenuesByTickerAndTimeframe(symbol, "annual").map(
            value -> {
                double curRevenue = value.get(0)
                        .path("financials")
                        .path("income_statement")
                        .path("revenues")
                        .path("value")
                        .asDouble();
                double preRevenue = value.get(this.period - 1)
                        .path("financials")
                        .path("income_statement")
                        .path("revenues")
                        .path("value")
                        .asDouble();
                double rateRevenue = CalculatorUtils.getGrowthRate(preRevenue, curRevenue, this.period);
                double curEps = value.get(0)
                        .path("financials")
                        .path("income_statement")
                        .path("diluted_earnings_per_share")
                        .path("value")
                        .asDouble();
                double prvEps = value.get(this.period - 1)
                        .path("financials")
                        .path("income_statement")
                        .path("diluted_earnings_per_share")
                        .path("value")
                        .asDouble();
                double rateEps = CalculatorUtils.getGrowthRate(prvEps, curEps, this.period);
                int growthEvaluation = 0;
                double revTTM = this.getTtmData(symbol).get(0)
                        .path("financials")
                        .path("income_statement")
                        .path("revenues")
                        .path("value")
                        .asDouble();
                // Both revenue and EPS growth are negative
                if (rateRevenue < 0.0 && rateEps < 0.0) {
                    growthEvaluation = 1;
                }
                // Only revenue growth is negative
                else if (rateRevenue < 0.0) {
                    growthEvaluation = 2;
                }
                // Both revenue and EPS growth are zero
                else if (rateRevenue == 0.0 && rateEps == 0.0) {
                    growthEvaluation = 3;
                }
                // Positive revenue growth below 10%
                else if (rateRevenue > 0.0 && rateRevenue < 10.0) {
                    growthEvaluation = 4;
                }
                // Revenue growth above 10% and TTM revenue is less than 120% of previous revenue
                else if (rateRevenue > 10.0 && revTTM < (1.2 * curRevenue)) {
                    growthEvaluation = 5;
                }
                // Revenue growth between 10% and 15%
                else if (rateRevenue > 10.0 && rateRevenue < 15.0) {
                    growthEvaluation = 6;
                }
                // Revenue growth between 15% and 20%
                else if (rateRevenue > 15.0 && rateRevenue < 20.0) {
                    growthEvaluation = (rateEps > 15.0) ? 8 : 7;
                }
                // Revenue growth above 20%
                else if (rateRevenue > 20.0) {
                    growthEvaluation = (rateEps > 20.0) ? 10 : 9;
                }

                return growthEvaluation;
            })
            .onErrorResume(error -> Mono.just(-1));
    }

    public JsonNode getTtmData(String symbol) {
        try {
            JsonNode node = webClient.get()
                    .uri("/vX/reference/financials?ticker={symbol}&timeframe=ttm&apiKey={apiKey}", symbol, apiKey)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            if(node != null && !node.path("results").isEmpty()) {
                return node.path("results");
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public Mono<Double> getValueCriteria(String symbol) {
        Mono<Double> epsRating = getRevenuesByTickerAndTimeframe(symbol, "annual").map(
                value -> {
                    double curEps = value.get(0)
                            .path("financials")
                            .path("income_statement")
                            .path("diluted_earnings_per_share")
                            .path("value")
                            .asDouble();
                    double prvEps = value.get(this.period - 1)
                            .path("financials")
                            .path("income_statement")
                            .path("diluted_earnings_per_share")
                            .path("value")
                            .asDouble();
                    return CalculatorUtils.getGrowthRate(prvEps, curEps, this.period);
                })
                .onErrorResume(error -> Mono.just(-1.0));
        Mono<Double> pegRating = this.cagrForCompanyNumerical(symbol);
        Mono<Double> curEps = this.getCurrentEps(symbol);
        return Mono.zip(epsRating, pegRating, curEps)
                .map(tuple -> {
                    double eps = tuple.getT1();
                    double peg = tuple.getT2();
                    double currentEps = tuple.getT3();
                    double valueEvaluation = 0.0;
                    double stockPrice = this.getStockData(symbol).path("ticker")
                            .path("day")
                            .path("o")
                            .asDouble(0);
                    double pe = stockPrice / currentEps;
                    if ((eps < 0.0) && (peg > 12.0)) {
                        valueEvaluation = 1;
                    }
                    if (eps < 0.0 && (peg > 10.0) && (peg < 12.0)) {
                        valueEvaluation = 2;
                    }
                    if ((peg < 0) && (pe < 50.0)) {
                        return 2.0;
                    }
                    if (((eps > 0) && (peg > 8.0))) {
                        if (peg < 10.0) {
                            valueEvaluation = 3;
                        }
                    }
                    if ((peg < 0) && (pe > 50.0)) {
                        return 3.0;
                    }
                    if (((eps > 0) && (peg > 5.0))) {
                        if (peg < 8.0) {
                            valueEvaluation = 4;
                        }
                    }
                    if ((eps > 0) && (peg > 2.0)) {
                        if (peg < 5.0) {
                            valueEvaluation = 5;
                        }
                    }
                    if ((peg < 2.0) || (Double.compare(peg, 2.0) == 0)) {
                        valueEvaluation = 6;
                    }
                    if ((peg < 1.5) && (peg > 1.2)) {
                        valueEvaluation = 7;
                    }
                    if ((peg < 1.2) && (peg > 1.0)) {
                        valueEvaluation = 9;
                    }
                    if (peg < 1.0) {
                        valueEvaluation = 10;
                    }
                    return valueEvaluation;
                });
    }

    public Mono<Double> cagrForCompanyNumerical(String symbol) {
        JsonNode stockData = this.getStockData(symbol);
        double stockPrice = stockData.path("ticker")
                .path("day")
                .path("o")
                .asDouble();
        return getRevenuesByTickerAndTimeframe(symbol, "annual").map(
                value -> {
                    double curEps = value.get(0)
                            .path("financials")
                            .path("income_statement")
                            .path("diluted_earnings_per_share")
                            .path("value")
                            .asDouble();
                    double prvEps = value.get(this.period - 1)
                            .path("financials")
                            .path("income_statement")
                            .path("diluted_earnings_per_share")
                            .path("value")
                            .asDouble();
                    double peRatio = stockPrice / curEps;
                    if ((prvEps > 0) && (curEps < 0)) {
                        return 1.00;
                    }
                    if ((prvEps < 0) && (curEps > 0) && (peRatio < 50.0)) { // and PE ratio is less than 50
                        return 4.00;
                    }
                    return CalculatorUtils.cagr(curEps, prvEps, stockPrice, period);
                })
                .onErrorResume(error -> Mono.just(-1.0));
    }

    public Mono<Double> getCurrentEps(String symbol) {
        return getRevenuesByTickerAndTimeframe(symbol, "annual").map(
                        value -> value.get(0)
                                .path("financials")
                                .path("income_statement")
                                .path("diluted_earnings_per_share")
                                .path("value")
                                .asDouble())
                .onErrorResume(error -> Mono.just(-1.0));
    }
}
