package com.arafawood.api.service;

import com.arafawood.api.common.ApiException;
import com.arafawood.api.domain.AppUser;
import com.arafawood.api.domain.Expense;
import com.arafawood.api.domain.ExpenseType;
import com.arafawood.api.domain.Item;
import com.arafawood.api.domain.StockCount;
import com.arafawood.api.domain.StockCountLine;
import com.arafawood.api.domain.StockMovement;
import com.arafawood.api.dto.Dtos.ApproveEffect;
import com.arafawood.api.dto.Dtos.CountDto;
import com.arafawood.api.dto.Dtos.CountLineDto;
import com.arafawood.api.dto.Dtos.CountLineWrite;
import com.arafawood.api.dto.Dtos.CountWrite;
import com.arafawood.api.dto.Dtos.MovementDto;
import com.arafawood.api.dto.Dtos.StockRow;
import com.arafawood.api.repo.ExpenseRepository;
import com.arafawood.api.repo.ExpenseTypeRepository;
import com.arafawood.api.repo.ItemRepository;
import com.arafawood.api.repo.StockCountRepository;
import com.arafawood.api.repo.StockMovementRepository;
import com.arafawood.api.security.CurrentUserService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private final ItemRepository items;
    private final StockMovementRepository movements;
    private final StockCountRepository counts;
    private final ExpenseTypeRepository expenseTypes;
    private final ExpenseRepository expenses;
    private final StockService stock;
    private final SequenceService sequences;
    private final AuditService audit;
    private final PeriodLockService periodLock;
    private final CurrentUserService currentUser;

    public InventoryService(
            ItemRepository items,
            StockMovementRepository movements,
            StockCountRepository counts,
            ExpenseTypeRepository expenseTypes,
            ExpenseRepository expenses,
            StockService stock,
            SequenceService sequences,
            AuditService audit,
            PeriodLockService periodLock,
            CurrentUserService currentUser) {
        this.items = items;
        this.movements = movements;
        this.counts = counts;
        this.expenseTypes = expenseTypes;
        this.expenses = expenses;
        this.stock = stock;
        this.sequences = sequences;
        this.audit = audit;
        this.periodLock = periodLock;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<StockRow> onHand() {
        currentUser.require("inventory", "view");
        return items.findAll().stream().map(this::toRow).toList();
    }

    @Transactional(readOnly = true)
    public List<MovementDto> movements() {
        currentUser.require("inventory", "view");
        List<StockMovement> all = movements.findAllByOrderByOccurredAtDescIdDesc();
        Map<Long, BigDecimal> running = new HashMap<>();
        List<StockMovement> chronological = new ArrayList<>(all);
        chronological.sort((a, b) -> {
            int cmp = a.getOccurredAt().compareTo(b.getOccurredAt());
            return cmp != 0 ? cmp : a.getId().compareTo(b.getId());
        });
        Map<Long, BigDecimal> after = new HashMap<>();
        for (StockMovement movement : chronological) {
            BigDecimal bal = running.getOrDefault(movement.getItem().getId(), BigDecimal.ZERO)
                    .add(movement.getQtyIn())
                    .subtract(movement.getQtyOut());
            running.put(movement.getItem().getId(), bal);
            after.put(movement.getId(), bal);
        }
        return all.stream()
                .map(m -> new MovementDto(
                        m.getId(),
                        m.getOccurredAt(),
                        m.getKind(),
                        m.getItem().getId(),
                        m.getItem().getName(),
                        m.getQtyIn(),
                        m.getQtyOut(),
                        m.getUnitCost(),
                        m.getRefType(),
                        m.getRefId(),
                        after.get(m.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CountDto> counts() {
        currentUser.require("counts", "view");
        return counts.findAllByOrderByStartedAtDesc().stream().map(this::toCount).toList();
    }

    @Transactional
    public CountDto createCount(CountWrite write) {
        AppUser user = currentUser.require("counts", "create");
        StockCount count = new StockCount();
        count.setNo(sequences.next("CNT"));
        count.setCreatedBy(user);
        count.setStartedAt(Instant.now());
        applyLines(count, write);
        return toCount(counts.save(count));
    }

    @Transactional
    public CountDto updateCount(Long id, CountWrite write) {
        currentUser.require("counts", "create");
        StockCount count = counts.findById(id).orElseThrow(() -> ApiException.notFound("الجرد غير موجود"));
        if (!"draft".equals(count.getStatus())) {
            throw ApiException.badRequest("الجرد المعتمد لا يُعدّل");
        }
        applyLines(count, write);
        return toCount(counts.save(count));
    }

    @Transactional
    public ApproveEffect approveCount(Long id) {
        AppUser user = currentUser.require("counts", "approve");
        periodLock.assertOpen(periodLock.today());
        StockCount count = counts.findById(id).orElseThrow(() -> ApiException.notFound("الجرد غير موجود"));
        if (!"draft".equals(count.getStatus())) {
            throw ApiException.badRequest("الجرد معتمد بالفعل");
        }
        ExpenseType shortageType = expenseTypes.findByName("عجز جرد").orElse(null);
        BigDecimal wasteTotal = BigDecimal.ZERO;
        BigDecimal surplusTotal = BigDecimal.ZERO;
        for (StockCountLine line : count.getLines()) {
            BigDecimal diff = line.getActualQty().subtract(line.getBookQty());
            if (diff.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            BigDecimal avg = stock.averageCost(line.getItem().getId());
            if (diff.compareTo(BigDecimal.ZERO) > 0) {
                stock.moveIn(line.getItem(), "count_adj", diff, avg, "count", count.getId());
                surplusTotal = surplusTotal.add(diff.multiply(avg));
            } else {
                if (line.getReason() == null || line.getReason().isBlank()) {
                    throw ApiException.badRequest("سبب العجز لازم على " + line.getItem().getName());
                }
                BigDecimal out = diff.abs();
                stock.moveOut(line.getItem(), "count_adj", out, avg, "count", count.getId());
                wasteTotal = wasteTotal.add(out.multiply(avg));
                if (shortageType != null) {
                    Expense expense = new Expense();
                    expense.setType(shortageType);
                    expense.setAmount(out.multiply(avg));
                    expense.setNote(line.getReason());
                    expense.setItem(line.getItem());
                    expense.setQty(out);
                    expense.setStockCount(count);
                    expense.setStatus("approved");
                    expense.setCreatedBy(user);
                    expenses.save(expense);
                }
            }
        }
        count.setStatus("approved");
        count.setApprovedBy(user);
        count.setApprovedAt(Instant.now());
        counts.save(count);
        audit.record(user, "approve", "stock_count", count.getId(), "draft", "approved");
        return new ApproveEffect(
                "اتسجلت فروقات الجرد. العجز وارد عجز جرد.",
                Map.of("waste", wasteTotal, "surplus", surplusTotal));
    }

    private void applyLines(StockCount count, CountWrite write) {
        count.getLines().clear();
        for (CountLineWrite line : write.lines() == null ? List.<CountLineWrite>of() : write.lines()) {
            Item item = items.findById(line.itemId()).orElseThrow(() -> ApiException.badRequest("اختار صنف من الدليل"));
            StockCountLine row = new StockCountLine();
            row.setStockCount(count);
            row.setItem(item);
            row.setBookQty(stock.onHand(item.getId()));
            row.setActualQty(line.actualQty() == null ? BigDecimal.ZERO : line.actualQty());
            row.setReason(line.reason());
            count.getLines().add(row);
        }
    }

    private StockRow toRow(Item item) {
        BigDecimal qty = stock.onHand(item.getId());
        BigDecimal avg = stock.averageCost(item.getId());
        String status = qty.compareTo(item.getMinQty()) < 0 ? "منخفض" : "متاح";
        return new StockRow(
                item.getId(),
                item.getCode(),
                item.getName(),
                item.getCategory().getName(),
                item.getLength(),
                item.getWidth(),
                item.getThickness(),
                item.getUnit().getName(),
                qty,
                avg,
                qty.multiply(avg),
                status);
    }

    private CountDto toCount(StockCount count) {
        return new CountDto(
                count.getId(),
                count.getNo(),
                count.getStatus(),
                count.getStartedAt(),
                count.getLines().stream()
                        .map(line -> new CountLineDto(
                                line.getId(),
                                line.getItem().getId(),
                                line.getItem().getName(),
                                line.getBookQty(),
                                line.getActualQty(),
                                line.getActualQty().subtract(line.getBookQty()),
                                line.getReason()))
                        .toList());
    }
}
