package com.arafawood.api.service;

import com.arafawood.api.common.ApiException;
import com.arafawood.api.domain.AppUser;
import com.arafawood.api.domain.Expense;
import com.arafawood.api.domain.ExpenseType;
import com.arafawood.api.domain.Item;
import com.arafawood.api.dto.Dtos.ApproveEffect;
import com.arafawood.api.dto.Dtos.ExpenseDto;
import com.arafawood.api.dto.Dtos.ExpenseWrite;
import com.arafawood.api.repo.ExpenseRepository;
import com.arafawood.api.repo.ExpenseTypeRepository;
import com.arafawood.api.repo.ItemRepository;
import com.arafawood.api.security.CurrentUserService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpenseService {

    private final ExpenseRepository expenses;
    private final ExpenseTypeRepository types;
    private final ItemRepository items;
    private final StockService stock;
    private final AuditService audit;
    private final PeriodLockService periodLock;
    private final CurrentUserService currentUser;

    public ExpenseService(
            ExpenseRepository expenses,
            ExpenseTypeRepository types,
            ItemRepository items,
            StockService stock,
            AuditService audit,
            PeriodLockService periodLock,
            CurrentUserService currentUser) {
        this.expenses = expenses;
        this.types = types;
        this.items = items;
        this.stock = stock;
        this.audit = audit;
        this.periodLock = periodLock;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<ExpenseDto> list() {
        currentUser.require("expenses", "view");
        return expenses.findAllByOrderByOccurredAtDesc().stream().map(this::toDto).toList();
    }

    @Transactional
    public ExpenseDto create(ExpenseWrite write) {
        AppUser user = currentUser.require("expenses", "create");
        periodLock.assertOpen(periodLock.today());
        ExpenseType type = types.findById(write.typeId()).orElseThrow(() -> ApiException.notFound("نوع الوارد غير موجود"));
        Expense expense = new Expense();
        if ("عجز جرد".equals(type.getName())) {
            throw ApiException.badRequest("عجز الجرد بيتسجل من اعتماد الجرد");
        }
        expense.setType(type);
        expense.setNote(write.note());
        expense.setOccurredAt(Instant.now());
        expense.setStatus("approved");
        expense.setCreatedBy(user);
        if (type.isAffectsStock()) {
            if (write.itemId() == null || write.qty() == null || write.qty().compareTo(BigDecimal.ZERO) <= 0) {
                throw ApiException.badRequest("الهالك محتاج صنف وكمية");
            }
            Item item = items.findById(write.itemId()).orElseThrow(() -> ApiException.badRequest("اختار صنف من الدليل"));
            BigDecimal onHand = stock.onHand(item.getId());
            if (write.qty().compareTo(onHand) > 0) {
                throw ApiException.conflict("الكمية أكبر من المتاح في المخزون", "OVERSELL");
            }
            BigDecimal avg = stock.averageCost(item.getId());
            expense.setItem(item);
            expense.setQty(write.qty());
            expense.setAmount(write.qty().multiply(avg));
            expenses.save(expense);
            stock.moveOut(item, "waste", write.qty(), avg, "expense", expense.getId());
        } else {
            if (write.amount() == null || write.amount().compareTo(BigDecimal.ZERO) <= 0) {
                throw ApiException.badRequest("قيمة الوارد لازمة");
            }
            expense.setAmount(write.amount());
            expenses.save(expense);
        }
        audit.record(user, "create", "expense", expense.getId(), null, expense.getAmount().toPlainString());
        return toDto(expense);
    }

    @Transactional
    public ApproveEffect reverse(Long id) {
        AppUser user = currentUser.require("expenses", "create");
        periodLock.assertOpen(periodLock.today());
        Expense original = expenses.findById(id).orElseThrow(() -> ApiException.notFound("الوارد غير موجود"));
        if (!"approved".equals(original.getStatus())) {
            throw ApiException.badRequest("القيد العكسي للوارد المرحّل فقط");
        }
        if (original.getStockCount() != null) {
            throw ApiException.badRequest("عجز الجرد يتصلح بجرد جديد — مفيش قيد عكسي");
        }
        Expense reversal = new Expense();
        reversal.setType(original.getType());
        reversal.setAmount(original.getAmount().negate());
        reversal.setNote("عكس " + (original.getNote() == null || original.getNote().isBlank() ? original.getType().getName() : original.getNote()));
        reversal.setOccurredAt(Instant.now());
        reversal.setItem(original.getItem());
        reversal.setQty(original.getQty());
        reversal.setStatus("reversed");
        reversal.setReversedOf(original);
        reversal.setCreatedBy(user);
        expenses.save(reversal);
        original.setStatus("reversed");
        expenses.save(original);
        if (original.getType().isAffectsStock() && original.getItem() != null && original.getQty() != null) {
            BigDecimal unit = original.getQty().compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : original.getAmount().divide(original.getQty(), 2, java.math.RoundingMode.HALF_UP);
            stock.moveIn(original.getItem(), "reversal", original.getQty(), unit, "expense", original.getId());
        }
        audit.record(user, "reverse", "expense", original.getId(), "approved", "reversed");
        return new ApproveEffect("اتعمل قيد عكسي للوارد.", Map.of("amount", reversal.getAmount()));
    }

    private ExpenseDto toDto(Expense expense) {
        return new ExpenseDto(
                expense.getId(),
                expense.getType().getId(),
                expense.getType().getName(),
                expense.getType().isAffectsStock(),
                expense.getAmount(),
                expense.getNote(),
                expense.getOccurredAt(),
                expense.getItem() == null ? null : expense.getItem().getId(),
                expense.getItem() == null ? null : expense.getItem().getName(),
                expense.getQty(),
                expense.getStatus(),
                expense.getStockCount() == null ? null : expense.getStockCount().getId(),
                expense.getReversedOf() == null ? null : expense.getReversedOf().getId());
    }
}
