package com.springboot.stock_analysis.repository;

import com.springboot.stock_analysis.model.WatchList;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public interface WatchListRepository extends JpaRepository<WatchList, Long> {
    List<WatchList> findAllByEmail(String email);
    Optional<WatchList> findByTickerAndEmail(String ticker, String email);
    WatchList findByTicker(String ticker);
    void deleteByTicker(String ticker);
}


