package com.springboot.stock_analysis.utils;
import com.fasterxml.jackson.databind.JsonNode;
import com.springboot.stock_analysis.model.Stocks;
import org.springframework.cglib.core.Local;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.text.DecimalFormat;
import java.time.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CalculatorUtils {

    public static double getGrowthRate(double startPrice, double endPrice, int period) {
        double rate = 0;
        if(startPrice == 0) return 0;
        if(endPrice < 0 && startPrice < 0) {
            double endPriceAbs = Math.abs(endPrice);
            double startPriceAbs = Math.abs(startPrice);
            rate = startPriceAbs / endPriceAbs;
        } else rate = endPrice / startPrice;
        double avgRate = Math.pow(rate, 1.0 / period);
        return (avgRate - 1.0) * 100;
    }

    public static Stocks findStockByTickerAndYear(Map<String, List<Stocks>> data, String ticker, String year) {
        List<Stocks> dataList = data.get(ticker);
        for(Stocks stockData: dataList) {
            if(stockData.getYear().equals(year)) return stockData;
        }
        return new Stocks();
    }

    public static Stocks findLastStockByTicker(Map<String, List<Stocks>> data, String ticker) {
        List<Stocks> dataList = data.get(ticker);
        Map<Integer, Stocks> maxStocks = new HashMap<>();
        for(Stocks row: dataList) {
            if(!row.getYear().equals("TTM")) {
                maxStocks.put(Integer.valueOf(row.getYear()), row);
            }
        }
        if(!maxStocks.isEmpty()) return Collections.max(maxStocks.entrySet(), Map.Entry.comparingByKey()).getValue();
        else return new Stocks();
    }

    public static double overallGrowth(Map<String, List<Stocks>> allStocks, String ticker, int startYear, int endYear) {
        int growthRating = growthEvaluation(allStocks, ticker, startYear, endYear);
        int valueRating = valueCriteria(allStocks, ticker, startYear, endYear);
        int performanceRating = performanceEvaluation(allStocks, ticker, startYear, endYear);
        double growth = (growthRating / 10.0) * 4;
        double value = (valueRating / 10.0) * 2.7;
        double performance = (performanceRating / 10.0) * 3.3;

        double overallScore = growth + value + performance;
        DecimalFormat f = new DecimalFormat("##.0");
        return Double.parseDouble(f.format(overallScore));
    }

    public static Integer growthEvaluation(Map<String, List<Stocks>> allStocks, String ticker, int startYear, int endYear) {
        int growthEvaluating = 1;
        Stocks ttmData = findStockByTickerAndYear(allStocks, ticker, "TTM");
        double revTTM = ttmData.getRevenue();
        Stocks prvData = findLastStockByTicker(allStocks, ticker);
        double revP = prvData.getRevenue();
        double revenueGrowth = companyGrowthRate(allStocks, ticker, startYear, endYear, "revenue");
        double epsGrowth = companyGrowthRate(allStocks, ticker, startYear, endYear, "eps");

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
     * @param endYear
     * @param indicator : revenue: company's revenue growthRate, eps: company's eps growthRate, stock: company's stock growthRate, in this case, ticker="SPY", "DIA", "QQQ" possible
     * @return get growth rate per year
     */
    public static Double companyGrowthRate(Map<String, List<Stocks>> allStocks, String ticker, int startYear, int endYear, String indicator) {
        Stocks startStocks = findStockByTickerAndYear(allStocks, ticker, String.valueOf(startYear));
        Stocks ttmData = findStockByTickerAndYear(allStocks, ticker, "TTM");
        Stocks lastStocks = findStockByTickerAndYear(allStocks, ticker, String.valueOf(endYear));
        if(lastStocks.getTicker().isEmpty()) {
          if(indicator.equals("stock")) lastStocks = ttmData;
          else {
            startStocks = findStockByTickerAndYear(allStocks, ticker, String.valueOf(startYear - 1));
            lastStocks = findStockByTickerAndYear(allStocks, ticker, String.valueOf(endYear - 1));
          }
        }
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
        return getGrowthRate(firstData, lastData, (endYear - startYear));
    }

    public static Integer valueCriteria(Map<String, List<Stocks>> allStocks, String ticker, int startYear, int endYear) {
        Stocks startStocks = findStockByTickerAndYear(allStocks, ticker, String.valueOf(startYear));
        Stocks ttmData = findStockByTickerAndYear(allStocks, ticker, "TTM");
        Stocks lastStocks = findStockByTickerAndYear(allStocks, ticker, String.valueOf(endYear));
        if(lastStocks.getTicker().isEmpty()) lastStocks = ttmData;
        double stockPrice = lastStocks.getStock();
        double epsPrice = lastStocks.getEps();
        double pe = stockPrice / epsPrice;
        double epsGrowth = companyGrowthRate(allStocks, ticker, startYear, endYear, "eps");
        double peg = cagrForCompanyNumerical(allStocks, startStocks, lastStocks, ticker);
        int valueEvaluation = 1;
        if (epsGrowth < 0.0 && (peg > 10.0) && (peg < 12.0)) {
            valueEvaluation = 2;
        }
        if ((peg < 0) && (pe < 50.0)) {
            return 2;
        }
        if (((epsGrowth > 0) && (peg > 8.0))) {
            if (peg < 10.0) {
                valueEvaluation = 3;
            }
        }
        if ((peg < 0) && (pe > 50.0)) {
            return 3;
        }
        if (((epsGrowth > 0) && (peg > 5.0))) {
            if (peg < 8.0) {
                valueEvaluation = 4;
            }
        }
        if ((epsGrowth > 0) && (peg > 2.0)) {
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
//return here later to create a 8
        if ((peg < 1.2) && (peg > 1.0)) {
            valueEvaluation = 9;
        }
        if (peg < 1.0) {
            valueEvaluation = 10;
        }
        return valueEvaluation;
    }

    public static Double cagrForCompanyNumerical(Map<String, List<Stocks>> allStocks, Stocks startStock, Stocks lastStock, String ticker) {
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

        Stocks ttmData = findStockByTickerAndYear(allStocks, ticker, "TTM");
        double epsTtm = ttmData.getEps();
        return cagr(lastEpsValue, startEpsValue, epsTtm, stockPrice);
    }

    public static Double cagr(double lastEpsValue, double startEpsValue, double epsTtm, double stockPrice) {
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

    public static Integer performanceEvaluation(Map<String, List<Stocks>> allStocks, String ticker, int firstYear, int lastYear) {
        double stockGrowth = companyGrowthRate(allStocks, ticker, firstYear, lastYear, "stock");
        double spyGrowth = companyGrowthRate(allStocks, "SPY", firstYear, lastYear, "stock");
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

    public static LocalDate getLocalDate(double miliseconds) {
        Instant instant = Instant.ofEpochMilli((long) miliseconds);

        // Step 3: Convert Instant to ZonedDateTime using system's default time zone
        ZonedDateTime zonedDateTime = instant.atZone(ZoneId.systemDefault());

        // Step 4: Extract LocalDate from ZonedDateTime
        return zonedDateTime.toLocalDate();
    }
}
