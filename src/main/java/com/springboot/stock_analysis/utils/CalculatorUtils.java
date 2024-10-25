package com.springboot.stock_analysis.utils;
import com.springboot.stock_analysis.model.Stocks;
import org.springframework.stereotype.Component;

import java.text.DecimalFormat;
import java.time.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Component
public class CalculatorUtils {

    public int minusPoint;
    public CalculatorUtils() {

    }
    public int getMinusPoint() {
        return minusPoint;
    }

    public void setMinusPoint(int minusPoint) {
        this.minusPoint = minusPoint;
    }

    public double getGrowthRate(double startPrice, double endPrice, int period) {
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

    public Stocks findStockByTickerAndYear(Map<String, List<Stocks>> data, String ticker, String year) {
        List<Stocks> dataList = data.get(ticker);
        if(dataList != null) {
            for(Stocks stockData: dataList) {
                if(stockData.getYear().equals(year)) return stockData;
            }
        }
        return new Stocks();
    }

    public static Stocks findLastStockByTicker(Map<String, List<Stocks>> data, String ticker) {
        List<Stocks> dataList = data.get(ticker);
        if(dataList != null) {
            Map<Integer, Stocks> maxStocks = new HashMap<>();
            for(Stocks row: dataList) {
                if(!row.getYear().equals("TTM")) {
                    maxStocks.put(Integer.valueOf(row.getYear()), row);
                }
            }
            if(!maxStocks.isEmpty()) return Collections.max(maxStocks.entrySet(), Map.Entry.comparingByKey()).getValue();
            else return new Stocks();
        }
        else return new Stocks();
    }

    public double overallGrowth(Map<String, List<Stocks>> allStocks, String ticker) {
        int lastYear = LocalDate.now().getYear() - 1;

        // when calculating growth score, there need to use 3 years of data
        int growthRating = growthEvaluation(allStocks, ticker, lastYear);
        // 5 years of data for value score
        int valueRating = valueCriteria(allStocks, ticker, lastYear);
        // 1 year of data for best performance
        int performanceRating = performanceEvaluation(allStocks, ticker, lastYear);
        double growth = (growthRating / 10.0) * 4;
        double value = (valueRating / 10.0) * 2.7;
        double performance = (performanceRating / 10.0) * 3.3;

        double overallScore = growth + value + performance;
        DecimalFormat f = new DecimalFormat("##.0");
        return Double.parseDouble(f.format(overallScore));
    }

    public Integer growthEvaluation(Map<String, List<Stocks>> allStocks, String ticker, int endYear) {
        int growthEvaluating = 1;
        int startYear = endYear - 2;
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
        if(this.getMinusPoint() > 0) {
            growthEvaluating = growthEvaluating - this.getMinusPoint();
            this.setMinusPoint(0);
        }
        return Math.max(growthEvaluating, 0);
    }

    /**
     * @param ticker
     * @param startYear
     * @param endYear
     * @param indicator : revenue: company's revenue growthRate, eps: company's eps growthRate, stock: company's stock growthRate, in this case, ticker="SPY", "DIA", "QQQ" possible
     * @return get growth rate per year
     */
    public Double companyGrowthRate(Map<String, List<Stocks>> allStocks, String ticker, int startYear, int endYear, String indicator) {
        int tmpStartYear = startYear;
        int tmpEndYear = endYear;

        if(indicator.equals("revenue") || indicator.equals("eps")) {
            tmpStartYear = endYear - 3;
        }
        Stocks lastStocks = findStockByTickerAndYear(allStocks, ticker, (endYear == LocalDate.now().getYear() ? "TTM" : String.valueOf(endYear)));
        Stocks startStocks = findStockByTickerAndYear(allStocks, ticker, String.valueOf(tmpStartYear));
        if(lastStocks.getTicker().isEmpty() && !indicator.equals("stock")) {
                tmpStartYear--;
                tmpEndYear--;
                lastStocks = findStockByTickerAndYear(allStocks, ticker, String.valueOf(tmpEndYear));
                startStocks = findStockByTickerAndYear(allStocks, ticker, String.valueOf(tmpStartYear));
        }

        Stocks startStock1 = findStockByTickerAndYear(allStocks, ticker, String.valueOf(tmpStartYear + 1));
        Stocks startStock2 = findStockByTickerAndYear(allStocks, ticker, String.valueOf(tmpStartYear + 2));

        double lastData = 0;
        double firstData = 0;
        switch (indicator) {
            case "revenue" -> {
                lastData = lastStocks.getRevenue();
                firstData = startStocks.getRevenue();
                if(firstData == 0.0) {
                    if(startStock1.getRevenue() == 0.0) {
                        firstData = startStock2.getRevenue();
                        tmpStartYear = tmpStartYear + 2;
                        this.setMinusPoint(5);
                    } else {
                        firstData = startStock1.getRevenue();
                        tmpStartYear++;
                        if(this.getMinusPoint() != 5) this.setMinusPoint(1);
                    }
                }
            }
            case "eps" -> {
                lastData = lastStocks.getEps();
                firstData = startStocks.getEps();
                if(firstData == 0.0) {
                    if(startStock1.getEps() == 0.0) {
                        firstData = startStock2.getEps();
                        tmpStartYear = tmpStartYear + 2;
                        this.setMinusPoint(5);
                    } else {
                        firstData = startStock1.getEps();
                        tmpStartYear++;
                        if(this.getMinusPoint() !=5 ) this.setMinusPoint(1);
                    }
                }
            }
            case "stock" -> {
                lastData = lastStocks.getStock();
                firstData = startStocks.getStock();
            }
        }
        if(firstData <= 0 || lastData < 0) return (double) 0;
        else return getGrowthRate(firstData, lastData, (tmpEndYear - tmpStartYear + 1));
    }

    public Integer valueCriteria(Map<String, List<Stocks>> allStocks, String ticker, int endYear) {
        int startYear = endYear - 4;
        Stocks startStocks = findStockByTickerAndYear(allStocks, ticker, String.valueOf(startYear));
        Stocks lastStocks = findStockByTickerAndYear(allStocks, ticker, String.valueOf(endYear));

        double stockPrice = lastStocks.getStock();
        double epsPrice = lastStocks.getEps();
        if(epsPrice == 0) return 2;
        double pe = stockPrice / epsPrice;
        double epsGrowth = companyGrowthRate(allStocks, ticker, startYear, endYear, "eps");
        int valueEvaluation = 1;

        if(startStocks.getEps() == 0) {
            if(pe < 15) return 8;
            if(pe > 15 && pe < 30) return 7;
            if(pe > 30 && pe < 50) return 4;
            if(pe > 50 && pe < 100) return 3;
            if(pe > 100) return 2;
        } else {
            double peg = cagrForCompanyNumerical(allStocks, startStocks, lastStocks, ticker);

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
        }

        return valueEvaluation;
    }

    public Double cagrForCompanyNumerical(Map<String, List<Stocks>> allStocks, Stocks startStock, Stocks lastStock, String ticker) {
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

    public static String convertTimestampToDate(long unixMsec) {
        ZonedDateTime dateTime = Instant.ofEpochMilli(unixMsec)
                .atZone(ZoneId.of("UTC"));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        return String.valueOf(dateTime.getYear());
    }
    public Integer performanceEvaluation(Map<String, List<Stocks>> allStocks, String ticker, int lastYear) {
        int firstYear = lastYear - 1;
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

    public LocalDate getLocalDate(double miliseconds) {
        Instant instant = Instant.ofEpochMilli((long) miliseconds);

        // Step 3: Convert Instant to ZonedDateTime using system's default time zone
        ZonedDateTime zonedDateTime = instant.atZone(ZoneId.systemDefault());

        // Step 4: Extract LocalDate from ZonedDateTime
        return zonedDateTime.toLocalDate();
    }
}
