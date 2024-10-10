package com.springboot.stock_analysis.repository;

import com.springboot.stock_analysis.model.Stocks;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StocksRepository extends JpaRepository<Stocks, Long> {
    Optional<Stocks> findByTickerAndYear(String ticker, String year);
    List<Stocks> findByTickerAndYearNot(String ticker, String year);
}
