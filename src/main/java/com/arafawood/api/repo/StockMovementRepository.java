package com.arafawood.api.repo;

import com.arafawood.api.domain.StockMovement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    List<StockMovement> findByItemIdOrderByOccurredAtAscIdAsc(Long itemId);

    List<StockMovement> findAllByOrderByOccurredAtDescIdDesc();

    @Query("select coalesce(sum(m.qtyIn - m.qtyOut), 0) from StockMovement m where m.item.id = :itemId")
    java.math.BigDecimal onHand(@Param("itemId") Long itemId);

    @Query("select coalesce(sum((m.qtyIn - m.qtyOut) * m.unitCost), 0) from StockMovement m where m.item.id = :itemId")
    java.math.BigDecimal stockValue(@Param("itemId") Long itemId);
}
