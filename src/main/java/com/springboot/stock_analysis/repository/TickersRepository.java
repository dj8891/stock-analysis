package com.springboot.stock_analysis.repository;

import com.springboot.stock_analysis.model.Tickers;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
@Transactional
public interface TickersRepository extends JpaRepository<Tickers, Long> {
    boolean existsByShortName(String shortName);
}
