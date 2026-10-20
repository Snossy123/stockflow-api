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
import com.arafawood.api.dto.Dtos.PnlDayRow;
import com.arafawood.api.dto.Dtos.PnlRow;
import com.arafawood.api.dto.Dtos.PnlWeekRow;
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
    public List<PnlRow> pnl(LocalDate from, LocalDate to) {
        currentUser.require("reports", "view");
        List<SalesOrder> approvedSales = sales.findAll().stream()
                .filter(so -> "approved".equals(so.getStatus()))
                .toList();
        List<Expense> approvedExpenses = expenses.findAll().stream()
                .filter(e -> "approved".equals(e.getStatus()))
                .toList();
        if (from == null && to == null) {
            YearMonth now = YearMonth.now(CAIRO);
            List<PnlRow> rows = new ArrayList<>();
            for (int i = 5; i >= 0; i--) {
                YearMonth month = now.minusMonths(i);
                rows.add(buildMonthPnlRow(month, month.atDay(1), month.atEndOfMonth(), approvedSales, approvedExpenses));
            }
            return rows;
        }
        LocalDate today = LocalDate.now(CAIRO);
        LocalDate start = from != null ? from : (to != null ? to : today);
        LocalDate end = to != null ? to : start;
        if (end.isBefore(start)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        List<PnlRow> rows = new ArrayList<>();
        if (days > 62) {
            YearMonth ymStart = YearMonth.from(start);
            YearMonth ymEnd = YearMonth.from(end);
            for (YearMonth ym = ymStart; !ym.isAfter(ymEnd); ym = ym.plusMonths(1)) {
                LocalDate sliceStart = ym.atDay(1).isBefore(start) ? start : ym.atDay(1);
                LocalDate sliceEnd = ym.atEndOfMonth().isAfter(end) ? end : ym.atEndOfMonth();
                rows.add(buildMonthPnlRow(ym, sliceStart, sliceEnd, approvedSales, approvedExpenses));
            }
        } else {
            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                PnlRow row = buildSlicePnlRow(d.toString(), d, d, periodLock.isClosed(YearMonth.from(d)), approvedSales, approvedExpenses);
                if (days == 1
                        || row.revenue().compareTo(BigDecimal.ZERO) != 0
                        || row.cogs().compareTo(BigDecimal.ZERO) != 0
                        || row.expenses().compareTo(BigDecimal.ZERO) != 0) {
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private PnlRow buildMonthPnlRow(
            YearMonth month,
            LocalDate start,
            LocalDate end,
            List<SalesOrder> approvedSales,
            List<Expense> approvedExpenses) {
        PnlRow base = buildSlicePnlRow(month.toString(), start, end, periodLock.isClosed(month), approvedSales, approvedExpenses);
        List<PnlWeekRow> weeks = buildMonthWeeks(month, start, end, approvedSales, approvedExpenses);
        return new PnlRow(
                base.period(),
                base.revenue(),
                base.cogs(),
                base.gross(),
                base.expenses(),
                base.net(),
                base.closed(),
                weeks);
    }

    private PnlRow buildSlicePnlRow(
            String label,
            LocalDate start,
            LocalDate end,
            boolean closed,
            List<SalesOrder> approvedSales,
            List<Expense> approvedExpenses) {
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal cogs = BigDecimal.ZERO;
        for (SalesOrder so : approvedSales) {
            LocalDate posted = so.getPostingDate() != null ? so.getPostingDate() : so.getDocDate();
            if (!inRange(posted, start, end)) {
                continue;
            }
            revenue = revenue.add(so.getTotal());
            cogs = cogs.add(so.getCogs());
        }
        BigDecimal sliceExpenses = approvedExpenses.stream()
                .filter(e -> inRange(e.getOccurredAt().atZone(CAIRO).toLocalDate(), start, end))
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal gross = revenue.subtract(cogs);
        return new PnlRow(
                label,
                revenue,
                cogs,
                gross,
                sliceExpenses,
                gross.subtract(sliceExpenses),
                closed,
                List.of());
    }

    private List<PnlWeekRow> buildMonthWeeks(
            YearMonth month,
            LocalDate clipStart,
            LocalDate clipEnd,
            List<SalesOrder> approvedSales,
            List<Expense> approvedExpenses) {
        int endDay = month.lengthOfMonth();
        int[][] ranges = {
                {1, Math.min(7, endDay)},
                {8, Math.min(14, endDay)},
                {15, Math.min(21, endDay)},
                {22, endDay}
        };
        String[] labels = {"الأسبوع ١", "الأسبوع ٢", "الأسبوع ٣", "الأسبوع ٤"};
        List<PnlWeekRow> weeks = new ArrayList<>();
        for (int idx = 0; idx < ranges.length; idx++) {
            int fromDay = ranges[idx][0];
            int toDay = ranges[idx][1];
            if (fromDay > toDay) {
                continue;
            }
            LocalDate weekStart = month.atDay(fromDay);
            LocalDate weekEnd = month.atDay(toDay);
            if (weekEnd.isBefore(clipStart) || weekStart.isAfter(clipEnd)) {
                continue;
            }
            LocalDate effectiveStart = weekStart.isBefore(clipStart) ? clipStart : weekStart;
            LocalDate effectiveEnd = weekEnd.isAfter(clipEnd) ? clipEnd : weekEnd;
            List<PnlDayRow> dayRows = new ArrayList<>();
            BigDecimal weekRevenue = BigDecimal.ZERO;
            BigDecimal weekCogs = BigDecimal.ZERO;
            BigDecimal weekExpenses = BigDecimal.ZERO;

            for (LocalDate day = effectiveStart; !day.isAfter(effectiveEnd); day = day.plusDays(1)) {
                final LocalDate targetDay = day;
                BigDecimal dayRevenue = BigDecimal.ZERO;
                BigDecimal dayCogs = BigDecimal.ZERO;
                for (SalesOrder so : approvedSales) {
                    LocalDate posted = so.getPostingDate() != null ? so.getPostingDate() : so.getDocDate();
                    if (targetDay.equals(posted)) {
                        dayRevenue = dayRevenue.add(so.getTotal());
                        dayCogs = dayCogs.add(so.getCogs());
                    }
                }
                BigDecimal dayExpenses = approvedExpenses.stream()
                        .filter(e -> targetDay.equals(e.getOccurredAt().atZone(CAIRO).toLocalDate()))
                        .map(Expense::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                if (dayRevenue.compareTo(BigDecimal.ZERO) == 0
                        && dayCogs.compareTo(BigDecimal.ZERO) == 0
                        && dayExpenses.compareTo(BigDecimal.ZERO) == 0) {
                    continue;
                }

                BigDecimal dayGross = dayRevenue.subtract(dayCogs);
                BigDecimal dayNet = dayGross.subtract(dayExpenses);
                dayRows.add(new PnlDayRow(
                        targetDay.toString(),
                        dayRevenue,
                        dayCogs,
                        dayGross,
                        dayExpenses,
                        dayNet));

                weekRevenue = weekRevenue.add(dayRevenue);
                weekCogs = weekCogs.add(dayCogs);
                weekExpenses = weekExpenses.add(dayExpenses);
            }

            if (dayRows.isEmpty()) {
                continue;
            }

            BigDecimal weekGross = weekRevenue.subtract(weekCogs);
            BigDecimal weekNet = weekGross.subtract(weekExpenses);
            weeks.add(new PnlWeekRow(
                    month + "-W" + (idx + 1),
                    labels[idx],
                    weekStart.toString(),
                    weekEnd.toString(),
                    weekRevenue,
                    weekCogs,
                    weekGross,
                    weekExpenses,
                    weekNet,
                    dayRows));
        }
        return weeks;
    }

    @Transactional(readOnly = true)
    public CashReport cash(LocalDate from, LocalDate to) {
        currentUser.require("reports", "view");
        LocalDate today = LocalDate.now(CAIRO);
        LocalDate startDate = from != null ? from : (to != null ? to : today);
        LocalDate endDate = to != null ? to : startDate;
        if (endDate.isBefore(startDate)) {
            LocalDate tmp = startDate;
            startDate = endDate;
            endDate = tmp;
        }
        Instant start = startDate.atStartOfDay(CAIRO).toInstant();
        Instant end = endDate.plusDays(1).atStartOfDay(CAIRO).toInstant();
        BigDecimal opening = cashBefore(start);
        BigDecimal running = opening;
        List<CashRow> rows = new ArrayList<>();
        Map<String, SalesOrder> salesByNo = new HashMap<>();
        for (SalesOrder so : sales.findAll()) {
            if (so.getNo() != null) {
                salesByNo.put(so.getNo(), so);
            }
        }
        Map<String, PurchaseOrder> purchasesByNo = new HashMap<>();
        for (PurchaseOrder po : purchases.findAll()) {
            if (po.getNo() != null) {
                purchasesByNo.put(po.getNo(), po);
            }
        }
        Map<Long, String> customerNames = new HashMap<>();
        for (var c : customers.findAll()) {
            customerNames.put(c.getId(), c.getName());
        }
        Map<Long, String> supplierNames = new HashMap<>();
        for (var s : suppliers.findAll()) {
            supplierNames.put(s.getId(), s.getName());
        }
        List<Payment> dayPayments = payments.findAll().stream()
                .filter(p -> !p.getOccurredAt().isBefore(start) && p.getOccurredAt().isBefore(end))
                .sorted(Comparator.comparing(Payment::getOccurredAt))
                .toList();
        for (Payment payment : dayPayments) {
            boolean inflow = "customer".equals(payment.getPartyType());
            BigDecimal in = inflow ? payment.getAmount() : BigDecimal.ZERO;
            BigDecimal out = inflow ? BigDecimal.ZERO : payment.getAmount();
            running = running.add(in).subtract(out);
            String ref = nz(payment.getRef()).trim();
            String refType = null;
            Long refId = null;
            String refNo = null;
            String targetType;
            Long targetId;
            String targetRef;
            String partyName = inflow
                    ? customerNames.getOrDefault(payment.getPartyId(), "")
                    : supplierNames.getOrDefault(payment.getPartyId(), "");
            String label;
            if (inflow) {
                String candidateNo = ref.startsWith("مرتجع ") ? ref.substring("مرتجع ".length()).trim() : ref;
                SalesOrder matched = salesByNo.get(candidateNo);
                if (matched != null) {
                    refType = "sale";
                    refId = matched.getId();
                    refNo = matched.getNo();
                    targetType = "sale";
                    targetId = matched.getId();
                    targetRef = matched.getNo();
                    label = "استلام " + ref + (partyName.isEmpty() ? "" : " — " + partyName);
                } else {
                    targetType = "customer";
                    targetId = payment.getPartyId();
                    targetRef = ref.isEmpty() ? null : ref;
                    label = "استلام" + (partyName.isEmpty() ? "" : " من " + partyName) + (ref.isEmpty() ? "" : " (" + ref + ")");
                }
            } else {
                String candidateNo = ref.startsWith("عكس ") ? ref.substring("عكس ".length()).trim() : ref;
                PurchaseOrder matched = purchasesByNo.get(candidateNo);
                if (matched != null) {
                    refType = "purchase";
                    refId = matched.getId();
                    refNo = matched.getNo();
                    targetType = "purchase";
                    targetId = matched.getId();
                    targetRef = matched.getNo();
                    label = "دفع " + ref + (partyName.isEmpty() ? "" : " — " + partyName);
                } else {
                    targetType = "supplier";
                    targetId = payment.getPartyId();
                    targetRef = ref.isEmpty() ? null : ref;
                    label = "دفع" + (partyName.isEmpty() ? "" : " لـ " + partyName) + (ref.isEmpty() ? "" : " (" + ref + ")");
                }
            }
            rows.add(new CashRow(
                    payment.getOccurredAt(),
                    label,
                    in,
                    out,
                    running,
                    refType,
                    refId,
                    refNo,
                    targetType,
                    targetId,
                    targetRef));
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
            String note = nz(expense.getNote()).trim();
            String typeName = expense.getType().getName();
            String expLabel = "مصروف " + typeName + (note.isEmpty() ? "" : " — " + note);
            rows.add(new CashRow(
                    expense.getOccurredAt(),
                    expLabel,
                    BigDecimal.ZERO,
                    expense.getAmount(),
                    running,
                    "expense",
                    expense.getId(),
                    typeName,
                    "expense",
                    expense.getId(),
                    note.isEmpty() ? typeName : note));
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
    public List<ItemProfitRow> itemProfit(LocalDate from, LocalDate to) {
        currentUser.require("reports", "view");
        LocalDate start = from;
        LocalDate end = to;
        if (start != null && end != null && end.isBefore(start)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }
        Map<Long, BigDecimal> revenue = new HashMap<>();
        Map<Long, BigDecimal> cost = new HashMap<>();
        Map<Long, String> names = new HashMap<>();
        for (SalesOrder so : sales.findAll()) {
            if (!"approved".equals(so.getStatus())) {
                continue;
            }
            LocalDate posted = so.getPostingDate() != null ? so.getPostingDate() : so.getDocDate();
            if (start != null && posted.isBefore(start)) {
                continue;
            }
            if (end != null && posted.isAfter(end)) {
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
