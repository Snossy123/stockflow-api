package com.arafawood.api.repo;

import com.arafawood.api.domain.StockCount;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockCountRepository extends JpaRepository<StockCount, Long> {
    List<StockCount> findAllByOrderByStartedAtDesc();
}
