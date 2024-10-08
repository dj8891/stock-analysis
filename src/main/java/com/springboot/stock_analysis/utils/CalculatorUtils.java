package com.springboot.stock_analysis.utils;
import java.util.Collections;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Comparator;
import java.util.List;

public class CalculatorUtils {
    public static double getGrowthRate(double startPrice, double endPrice, int period) {
        double rate = 0;
        if(endPrice < 0 && startPrice < 0) {
            double endPriceAbs = Math.abs(endPrice);
            double startPriceAbs = Math.abs(startPrice);
            rate = startPriceAbs / endPriceAbs;
        } else rate = endPrice / startPrice;
        double avgRate = Math.pow(rate, 1.0 / period);
        return (avgRate - 1.0) * 100;
    }

    public static String convertToArrayString(List<List<String>> data) {
        StringBuilder sb = new StringBuilder();
        data.forEach(row -> {
            for (String item : row) {
                sb.append(item).append(" ");
            }
            sb.append("\n");
        });
        return sb.toString();
    }

    public static String convertToString(List<String> data) {
        return String.join("\n", data);
    }

    public static int findTickerRow(List<String[]> data, String ticker) {
        for (int i = 5; i < data.size(); i++) {
            String[] row = data.get(i);
            if (row[0].trim().equalsIgnoreCase(ticker)) {
                return i;
            }
        }
        return -1;
    }

    public static double cagr(double currenteps, double epsfiveyearsago, double epsttm, double ttmStockPrice) {
        double value1 = 0.0;
        if (currenteps < 0 && epsfiveyearsago < 0) {
            currenteps = Math.abs(currenteps);
            epsfiveyearsago = Math.abs(epsfiveyearsago);
            value1 = (epsfiveyearsago / currenteps);
        } else {
            value1 = (currenteps / epsfiveyearsago);
        }
        double z = Math.pow(value1, 1.0 / 5);
        double growthRate = (z - 1.0) * 100;
        if (growthRate > 25.0) {
            growthRate = 25.0;
        }
        double pe = (ttmStockPrice / currenteps);
        return pe / growthRate;
    }
}
