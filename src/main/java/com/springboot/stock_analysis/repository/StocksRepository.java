package com.springboot.stock_analysis.repository;

import com.springboot.stock_analysis.model.Stocks;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StocksRepository extends JpaRepository<Stocks, Long> {
    @Query("SELECT DISTINCT s.ticker FROM Stocks s")
    public List<String> findDistinctByTicker();

    Optional<Stocks> findByTickerAndYear(String ticker, String year);

    @Query("SELECT s FROM Stocks s WHERE s.ticker = :ticker AND s.year != 'ttm' ORDER BY s.year DESC")
    Optional<Stocks> getPrevYearStock(@Param("ticker") String ticker);
}
