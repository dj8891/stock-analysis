package com.springboot.stock_analysis.repository;

import com.springboot.stock_analysis.model.Tickers;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TickersRepository extends JpaRepository<Tickers, Long> {
    boolean existsByShortName(String shortName);
}
