package com.arafawood.api.service;

import com.arafawood.api.domain.Item;
import com.arafawood.api.domain.StockMovement;
import com.arafawood.api.repo.StockMovementRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final StockMovementRepository movements;

    public StockService(StockMovementRepository movements) {
        this.movements = movements;
    }

    @Transactional(readOnly = true)
    public BigDecimal onHand(Long itemId) {
        return nz(movements.onHand(itemId));
    }

    @Transactional(readOnly = true)
    public BigDecimal stockValue(Long itemId) {
        return nz(movements.stockValue(itemId)).setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public BigDecimal averageCost(Long itemId) {
        BigDecimal qty = onHand(itemId);
        if (qty.compareTo(ZERO) <= 0) {
            return ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return stockValue(itemId).divide(qty, 2, RoundingMode.HALF_UP);
    }

    @Transactional
    public StockMovement moveIn(Item item, String kind, BigDecimal qty, BigDecimal unitCost, String refType, Long refId) {
        return moveIn(item, kind, qty, unitCost, refType, refId, Instant.now());
    }

    @Transactional
    public StockMovement moveIn(
            Item item, String kind, BigDecimal qty, BigDecimal unitCost, String refType, Long refId, Instant occurredAt) {
        StockMovement movement = base(item, kind, refType, refId, unitCost, occurredAt);
        movement.setQtyIn(qty);
        movement.setQtyOut(ZERO);
        return movements.save(movement);
    }

    @Transactional
    public StockMovement moveOut(Item item, String kind, BigDecimal qty, BigDecimal unitCost, String refType, Long refId) {
        return moveOut(item, kind, qty, unitCost, refType, refId, Instant.now());
    }

    @Transactional
    public StockMovement moveOut(
            Item item, String kind, BigDecimal qty, BigDecimal unitCost, String refType, Long refId, Instant occurredAt) {
        StockMovement movement = base(item, kind, refType, refId, unitCost, occurredAt);
        movement.setQtyIn(ZERO);
        movement.setQtyOut(qty);
        return movements.save(movement);
    }

    private StockMovement base(
            Item item, String kind, String refType, Long refId, BigDecimal unitCost, Instant occurredAt) {
        StockMovement movement = new StockMovement();
        movement.setItem(item);
        movement.setKind(kind);
        movement.setRefType(refType);
        movement.setRefId(refId);
        movement.setUnitCost(unitCost);
        movement.setOccurredAt(occurredAt == null ? Instant.now() : occurredAt);
        return movement;
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? ZERO : value;
    }
}
