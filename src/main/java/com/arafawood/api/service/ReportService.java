package com.arafawood.api.service;

import com.arafawood.api.domain.Expense;
import com.arafawood.api.domain.Payment;
import com.arafawood.api.domain.PurchaseOrder;
import com.arafawood.api.domain.PurchaseOrderItem;
import com.arafawood.api.domain.SalesOrder;
import com.arafawood.api.domain.SalesOrderItem;
import com.arafawood.api.domain.StockMovement;
import com.arafawood.api.dto.Dtos.AgingBucket;
import com.arafawood.api.dto.Dtos.AgingReport;
import com.arafawood.api.dto.Dtos.CashReport;
import com.arafawood.api.dto.Dtos.CashRow;
import com.arafawood.api.dto.Dtos.ItemProfitRow;
import com.arafawood.api.dto.Dtos.PnlRow;
import com.arafawood.api.dto.Dtos.ReorderRow;
import com.arafawood.api.repo.CustomerRepository;
import com.arafawood.api.repo.ExpenseRepository;
import com.arafawood.api.repo.ItemRepository;
import com.arafawood.api.repo.PaymentRepository;
import com.arafawood.api.repo.PurchaseOrderRepository;
import com.arafawood.api.repo.SalesOrderRepository;
import com.arafawood.api.repo.StockMovementRepository;
import com.arafawood.api.repo.SupplierRepository;
import com.arafawood.api.security.CurrentUserService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {

    private static final ZoneId CAIRO = ZoneId.of("Africa/Cairo");

    private final SalesOrderRepository sales;
    private final ExpenseRepository expenses;
    private final PaymentRepository payments;
    private final PurchaseOrderRepository purchases;
    private final ItemRepository items;
    private final StockMovementRepository movements;
    private final CustomerRepository customers;
    private final SupplierRepository suppliers;
    private final PartyBalanceService balances;
    private final StockService stock;
    private final PeriodLockService periodLock;
    private final CurrentUserService currentUser;

    public ReportService(
            SalesOrderRepository sales,
            ExpenseRepository expenses,
            PaymentRepository payments,
            PurchaseOrderRepository purchases,
            ItemRepository items,
            StockMovementRepository movements,
            CustomerRepository customers,
            SupplierRepository suppliers,
            PartyBalanceService balances,
            StockService stock,
            PeriodLockService periodLock,
            CurrentUserService currentUser) {
        this.sales = sales;
        this.expenses = expenses;
        this.payments = payments;
        this.purchases = purchases;
        this.items = items;
        this.movements = movements;
        this.customers = customers;
        this.suppliers = suppliers;
        this.balances = balances;
        this.stock = stock;
        this.periodLock = periodLock;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<PnlRow> pnl() {
        currentUser.require("reports", "view");
        YearMonth now = YearMonth.now(CAIRO);
        List<PnlRow> rows = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth month = now.minusMonths(i);
            LocalDate start = month.atDay(1);
            LocalDate end = month.atEndOfMonth();
            BigDecimal revenue = BigDecimal.ZERO;
            BigDecimal cogs = BigDecimal.ZERO;
            for (SalesOrder so : sales.findAll()) {
                if (!"approved".equals(so.getStatus())) {
                    continue;
                }
                LocalDate posted = so.getPostingDate() != null ? so.getPostingDate() : so.getDocDate();
                if (!inRange(posted, start, end)) {
                    continue;
                }
                revenue = revenue.add(so.getTotal());
                cogs = cogs.add(so.getCogs());
            }
            BigDecimal monthExpenses = expenses.findAll().stream()
                    .filter(e -> "approved".equals(e.getStatus()))
                    .filter(e -> inRange(e.getOccurredAt().atZone(CAIRO).toLocalDate(), start, end))
                    .map(Expense::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal gross = revenue.subtract(cogs);
            rows.add(new PnlRow(
                    month.toString(),
                    revenue,
                    cogs,
                    gross,
                    monthExpenses,
                    gross.subtract(monthExpenses),
                    periodLock.isClosed(month)));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public CashReport cash(LocalDate day) {
        currentUser.require("reports", "view");
        LocalDate date = day == null ? LocalDate.now(CAIRO) : day;
        Instant start = date.atStartOfDay(CAIRO).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(CAIRO).toInstant();
        BigDecimal opening = cashBefore(start);
        BigDecimal running = opening;
        List<CashRow> rows = new ArrayList<>();
        List<Payment> dayPayments = payments.findAll().stream()
                .filter(p -> !p.getOccurredAt().isBefore(start) && p.getOccurredAt().isBefore(end))
                .sorted(Comparator.comparing(Payment::getOccurredAt))
                .toList();
        for (Payment payment : dayPayments) {
            boolean inflow = "customer".equals(payment.getPartyType());
            BigDecimal in = inflow ? payment.getAmount() : BigDecimal.ZERO;
            BigDecimal out = inflow ? BigDecimal.ZERO : payment.getAmount();
            running = running.add(in).subtract(out);
            rows.add(new CashRow(payment.getOccurredAt(), (inflow ? "استلام " : "دفع ") + nz(payment.getRef()), in, out, running));
        }
        for (Expense expense : expenses.findAll()) {
            if (!"approved".equals(expense.getStatus())) {
                continue;
            }
            if (expense.getType().isAffectsStock()) {
                continue;
            }
            if (expense.getOccurredAt().isBefore(start) || !expense.getOccurredAt().isBefore(end)) {
                continue;
            }
            running = running.subtract(expense.getAmount());
            rows.add(new CashRow(expense.getOccurredAt(), "وارد " + expense.getType().getName(), BigDecimal.ZERO, expense.getAmount(), running));
        }
        rows.sort(Comparator.comparing(CashRow::at));
        return new CashReport(opening, running, rows);
    }

    @Transactional(readOnly = true)
    public AgingReport aging() {
        currentUser.require("reports", "view");
        LocalDate today = LocalDate.now(CAIRO);
        List<AgingBucket> customerBuckets = customers.findAll().stream()
                .map(c -> bucket(
                        c.getName(),
                        sales.findByCustomerIdOrderByDocDateDesc(c.getId()).stream()
                                .filter(so -> "approved".equals(so.getStatus()))
                                .map(so -> remainingSale(so, today))
                                .toList()))
                .filter(this::hasAging)
                .toList();
        List<AgingBucket> supplierBuckets = suppliers.findAll().stream()
                .map(s -> bucket(
                        s.getName(),
                        purchases.findBySupplierIdOrderByDocDateDesc(s.getId()).stream()
                                .filter(po -> "approved".equals(po.getStatus()))
                                .map(po -> remainingPurchase(po, today))
                                .toList()))
                .filter(this::hasAging)
                .toList();
        return new AgingReport(customerBuckets, supplierBuckets);
    }

    @Transactional(readOnly = true)
    public List<ItemProfitRow> itemProfit() {
        currentUser.require("reports", "view");
        Map<Long, BigDecimal> revenue = new HashMap<>();
        Map<Long, BigDecimal> cost = new HashMap<>();
        Map<Long, String> names = new HashMap<>();
        for (SalesOrder so : sales.findAll()) {
            if (!"approved".equals(so.getStatus())) {
                continue;
            }
            for (SalesOrderItem line : so.getItems()) {
                names.put(line.getItem().getId(), line.getItem().getName());
                revenue.merge(line.getItem().getId(), line.getTotal(), BigDecimal::add);
                cost.merge(line.getItem().getId(), line.getQty().multiply(line.getUnitCost()), BigDecimal::add);
            }
        }
        BigDecimal totalProfit = revenue.keySet().stream()
                .map(id -> revenue.get(id).subtract(cost.getOrDefault(id, BigDecimal.ZERO)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return names.entrySet().stream()
                .map(entry -> {
                    BigDecimal rev = revenue.getOrDefault(entry.getKey(), BigDecimal.ZERO);
                    BigDecimal cogs = cost.getOrDefault(entry.getKey(), BigDecimal.ZERO);
                    BigDecimal profit = rev.subtract(cogs);
                    BigDecimal margin = rev.compareTo(BigDecimal.ZERO) == 0
                            ? BigDecimal.ZERO
                            : profit.multiply(new BigDecimal("100")).divide(rev, 1, RoundingMode.HALF_UP);
                    BigDecimal weight = totalProfit.compareTo(BigDecimal.ZERO) == 0
                            ? BigDecimal.ZERO
                            : profit.multiply(new BigDecimal("100")).divide(totalProfit, 1, RoundingMode.HALF_UP);
                    return new ItemProfitRow(entry.getKey(), entry.getValue(), rev, cogs, profit, margin, weight);
                })
                .sorted(Comparator.comparing(ItemProfitRow::profit).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReorderRow> reorder() {
        currentUser.require("reports", "view");
        Instant weekAgo = Instant.now().minusSeconds(7L * 24 * 60 * 60);
        return items.findAll().stream()
                .map(item -> {
                    BigDecimal onHand = stock.onHand(item.getId());
                    if (onHand.compareTo(item.getMinQty()) >= 0) {
                        return null;
                    }
                    BigDecimal weekly = movements.findByItemIdOrderByOccurredAtAscIdAsc(item.getId()).stream()
                            .filter(m -> "sale".equals(m.getKind()) && !m.getOccurredAt().isBefore(weekAgo))
                            .map(StockMovement::getQtyOut)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal shortage = item.getMinQty().subtract(onHand);
                    BigDecimal suggested = weekly.multiply(new BigDecimal("2")).max(shortage);
                    Cheapest cheapest = cheapestSupplier(item.getId());
                    return new ReorderRow(
                            item.getId(),
                            item.getName(),
                            onHand,
                            item.getMinQty(),
                            shortage,
                            weekly,
                            suggested,
                            cheapest.id,
                            cheapest.name,
                            cheapest.cost);
                })
                .filter(row -> row != null)
                .toList();
    }

    private Cheapest cheapestSupplier(Long itemId) {
        Cheapest best = new Cheapest(null, null, null);
        for (PurchaseOrder po : purchases.findAll()) {
            if (!"approved".equals(po.getStatus())) {
                continue;
            }
            for (PurchaseOrderItem line : po.getItems()) {
                if (!line.getItem().getId().equals(itemId)) {
                    continue;
                }
                if (best.cost == null || line.getUnitCost().compareTo(best.cost) < 0) {
                    best = new Cheapest(po.getSupplier().getId(), po.getSupplier().getName(), line.getUnitCost());
                }
            }
        }
        return best;
    }

    private record AgeSlice(int days, BigDecimal remaining) {}

    private record Cheapest(Long id, String name, BigDecimal cost) {}

    private AgeSlice remainingSale(SalesOrder so, LocalDate today) {
        BigDecimal paid = payments.findByPartyTypeAndPartyIdOrderByOccurredAtDesc("customer", so.getCustomer().getId())
                .stream()
                .filter(p -> so.getNo().equals(p.getRef()))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDate posted = so.getPostingDate() != null ? so.getPostingDate() : so.getDocDate();
        return new AgeSlice((int) java.time.temporal.ChronoUnit.DAYS.between(posted, today), so.getTotal().subtract(paid).max(BigDecimal.ZERO));
    }

    private AgeSlice remainingPurchase(PurchaseOrder po, LocalDate today) {
        BigDecimal paid = payments.findByPartyTypeAndPartyIdOrderByOccurredAtDesc("supplier", po.getSupplier().getId())
                .stream()
                .filter(p -> po.getNo().equals(p.getRef()))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDate posted = po.getPostingDate() != null ? po.getPostingDate() : po.getDocDate();
        return new AgeSlice((int) java.time.temporal.ChronoUnit.DAYS.between(posted, today), po.getTotal().subtract(paid).max(BigDecimal.ZERO));
    }

    private AgingBucket bucket(String name, List<AgeSlice> slices) {
        BigDecimal d0 = BigDecimal.ZERO;
        BigDecimal d31 = BigDecimal.ZERO;
        BigDecimal d60 = BigDecimal.ZERO;
        for (AgeSlice slice : slices) {
            if (slice.remaining().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (slice.days() <= 30) {
                d0 = d0.add(slice.remaining());
            } else if (slice.days() <= 60) {
                d31 = d31.add(slice.remaining());
            } else {
                d60 = d60.add(slice.remaining());
            }
        }
        return new AgingBucket(name, d0, d31, d60);
    }

    private boolean hasAging(AgingBucket bucket) {
        return bucket.d0().add(bucket.d31()).add(bucket.d60()).compareTo(BigDecimal.ZERO) > 0;
    }

    private BigDecimal cashBefore(Instant start) {
        BigDecimal value = BigDecimal.ZERO;
        for (Payment payment : payments.findAll()) {
            if (payment.getOccurredAt().isBefore(start)) {
                value = "customer".equals(payment.getPartyType())
                        ? value.add(payment.getAmount())
                        : value.subtract(payment.getAmount());
            }
        }
        for (Expense expense : expenses.findAll()) {
            if ("approved".equals(expense.getStatus())
                    && !expense.getType().isAffectsStock()
                    && expense.getOccurredAt().isBefore(start)) {
                value = value.subtract(expense.getAmount());
            }
        }
        return value;
    }

    private boolean inRange(LocalDate date, LocalDate start, LocalDate end) {
        return !date.isBefore(start) && !date.isAfter(end);
    }

    private String nz(String value) {
        return value == null ? "" : value;
    }
}
