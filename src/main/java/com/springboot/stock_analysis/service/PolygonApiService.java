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

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.*;

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
        List<RatedStockDTO> ratedStocks = new ArrayList<RatedStockDTO>();
        for(String ticker: tickerList) {
            double stockGrowth = this.overallGrowth(ticker, startYear, lastYear);
            RatedStockDTO ratedStockDTO = new RatedStockDTO(ticker, stockGrowth);
            ratedStocks.add(ratedStockDTO);
        }
        ratedStocks.sort(new Comparator<RatedStockDTO>() {
            @Override
            public int compare(RatedStockDTO o1, RatedStockDTO o2) {
                return Double.compare(o2.getRating(), o1.getRating());
            }
        });
        return ratedStocks;
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
        int performanceRating = this.performanceEvaluation(ticker, startYear, lastYear);
        double growth = (growthRating / 10.0) * 4;
        double value = (valueRating / 10.0) * 2.7;
        double performance = (performanceRating / 10.0) * 3.3;

        double overallScore = growth + value + performance;
        DecimalFormat f = new DecimalFormat("##.0");
        return Double.parseDouble(f.format(overallScore));
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
        Stocks ttmData = this.stocksRepository.findByTickerAndYear(ticker, "TTM").orElse(new Stocks());
        double revTTM = ttmData.getRevenue();
        Stocks prvData = this.stocksRepository.getPrevYearStock(ticker).orElse(new Stocks());
        double revP = prvData.getRevenue();
        double revenueGrowth = this.companyGrowthRate(ticker, startYear, lastYear, "revenue");
        double epsGrowth = this.companyGrowthRate(ticker, startYear, lastYear, "eps");

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
     * @param indicator : revenue: company's revenue growthRate, eps: company's eps growthRate, stock: company's stock growthRate, in this case, ticker="SPY", "DIA", "QQQ" possible
     * @return get growth rate per year
     */
    private Double companyGrowthRate(String ticker, int startYear, int lastYear, String indicator) {
        Stocks startStocks = this.stocksRepository.findByTickerAndYear(ticker, String.valueOf(startYear)).orElse(new Stocks());
        Stocks ttmData = this.stocksRepository.findByTickerAndYear(ticker, "TTM").orElse(new Stocks());
        Stocks lastStocks = this.stocksRepository.findByTickerAndYear(ticker, String.valueOf(lastYear)).orElse(ttmData);
        double lastData = 0;
        double firstData = 0;
        if(indicator.equals("revenue")) {
            lastData = lastStocks.getRevenue();
            firstData = startStocks.getRevenue();
        } else if(indicator.equals("eps")) {
            lastData = lastStocks.getEps();
            firstData = startStocks.getEps();
        } else if(indicator.equals("stock")) {
            lastData = lastStocks.getStock();
            firstData = startStocks.getStock();
        }
        return CalculatorUtils.getGrowthRate(firstData, lastData, (lastYear - startYear + 1));
    }

    public Integer valueCriteria(String ticker, int startYear, int lastYear) {
        Stocks startStocks = this.stocksRepository.findByTickerAndYear(ticker, String.valueOf(startYear)).orElse(new Stocks());
        Stocks ttmData = this.stocksRepository.findByTickerAndYear(ticker, "TTM").orElse(new Stocks());
        Stocks lastStocks = this.stocksRepository.findByTickerAndYear(ticker, String.valueOf(lastYear)).orElse(ttmData);
        double stockPrice = lastStocks.getStock();
        double epsPrice = lastStocks.getEps();
        double pe = stockPrice / epsPrice;
        double epsGrowth = this.companyGrowthRate(ticker, startYear, lastYear, "eps");
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
        Stocks ttmData = this.stocksRepository.findByTickerAndYear(ticker, "TTM").orElse(new Stocks());
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
    public Integer performanceEvaluation(String ticker, int firstYear, int lastYear) {
        double stockGrowth = this.companyGrowthRate(ticker, firstYear, lastYear, "stock");
        double spyGrowth = this.companyGrowthRate("SPY", firstYear, lastYear, "stock");
        int performanceEvaluation = 1;
        if (stockGrowth < (spyGrowth - 5.0)) {
            performanceEvaluation = 2;
        }
        if (stockGrowth < spyGrowth) {
            performanceEvaluation = 3;
        }
        /* if (performanceCalculator(stockPrices, SPY, ticker, "2021", "2022") < -5.0) {
            performanceEvaluation = 4;
        }
        if (performanceCalculator(stockPrices, SPY, ticker, "2021", "2022") < 0) {
            performanceEvaluation = 5;
        }*/
        if ((Double.compare(stockGrowth, spyGrowth)) == 0) {
            performanceEvaluation = 6;
        }
        if (stockGrowth > spyGrowth) {
            performanceEvaluation = 7;
        }
        /*
        if ((performanceCalculator(stockPrices, SPY, ticker, "2020", "2022") > 0)) {
            performanceEvaluation = 8;
        }
        if ((performanceCalculator(stockPrices, SPY, ticker, "2021", "2022") > 7.0)) {
            performanceEvaluation = 9;
        }*/
        if(stockGrowth - spyGrowth > 7.0) performanceEvaluation = 9;
        return performanceEvaluation;
    }

}
