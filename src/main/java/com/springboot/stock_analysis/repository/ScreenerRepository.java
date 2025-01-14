package com.springboot.stock_analysis.repository;

import com.springboot.stock_analysis.model.Screener;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScreenerRepository extends JpaRepository<Screener, Long> {
    List<Screener> findByTicker(String ticker);
}
