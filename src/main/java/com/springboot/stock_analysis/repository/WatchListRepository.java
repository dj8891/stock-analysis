package com.springboot.stock_analysis.repository;

import com.springboot.stock_analysis.model.WatchList;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WatchListRepository extends JpaRepository<WatchList, Long> {
    Optional<WatchList> findAllByTicker(String ticker);
}
