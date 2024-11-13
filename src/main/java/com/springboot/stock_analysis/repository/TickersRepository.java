package com.springboot.stock_analysis.repository;

import com.springboot.stock_analysis.model.Tickers;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public interface TickersRepository extends JpaRepository<Tickers, Long> {
    boolean existsByShortName(String shortName);
    Optional<Tickers> findByShortName(String ticker);

    @Query("SELECT e FROM Tickers e WHERE e.shortName LIKE :ticker% ORDER BY e.shortName ASC LIMIT 5")
    List<Tickers> findByShortNameAndFullName(@Param("ticker") String ticker);
}
