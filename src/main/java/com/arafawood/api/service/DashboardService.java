package com.arafawood.api.service;

import com.arafawood.api.common.BusinessDates;
import com.arafawood.api.domain.Expense;
import com.arafawood.api.domain.SalesOrder;
import com.arafawood.api.dto.Dtos.DashboardAlert;
import com.arafawood.api.dto.Dtos.DashboardKpi;
import com.arafawood.api.dto.Dtos.DashboardResponse;
import com.arafawood.api.dto.Dtos.MovementDto;
import com.arafawood.api.dto.Dtos.StockRow;
import com.arafawood.api.repo.ExpenseRepository;
import com.arafawood.api.repo.SalesOrderRepository;
import com.arafawood.api.repo.SupplierRepository;
import com.arafawood.api.security.CurrentUserService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private static final ZoneId CAIRO = ZoneId.of("Africa/Cairo");

    private final SalesOrderRepository sales;
    private final ExpenseRepository expenses;
    private final InventoryService inventory;
    private final PartyBalanceService balances;
    private final SupplierRepository suppliers;
    private final CurrentUserService currentUser;

    public DashboardService(
            SalesOrderRepository sales,
            ExpenseRepository expenses,
            InventoryService inventory,
            PartyBalanceService balances,
            SupplierRepository suppliers,
            CurrentUserService currentUser) {
        this.sales = sales;
        this.expenses = expenses;
        this.inventory = inventory;
        this.balances = balances;
        this.suppliers = suppliers;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public DashboardResponse load() {
        currentUser.require("dashboard", "view");
        LocalDate today = BusinessDates.today();
        List<SalesOrder> todaySales = sales.findByStatusAndPostingDate("approved", today);
        BigDecimal revenue = todaySales.stream().map(SalesOrder::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cogs = todaySales.stream().map(SalesOrder::getCogs).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal dayExpenses = expenses.findAll().stream()
                .filter(e -> "approved".equals(e.getStatus()))
                .filter(e -> e.getOccurredAt().atZone(CAIRO).toLocalDate().equals(today))
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal costPlus = cogs.add(dayExpenses);
        BigDecimal net = revenue.subtract(costPlus);
        BigDecimal supplierDebt = balances.allSupplierDebt();

        List<DashboardKpi> kpis = List.of(
                new DashboardKpi("صادرات اليوم", revenue, todaySales.size() + " فواتير بيع"),
                new DashboardKpi("تكلفة المبيعات + الواردات", costPlus, "مشتريات اليوم دخلت المخزون"),
                new DashboardKpi("صافي اليوم", net, "صادر − تكلفة − وارد"),
                new DashboardKpi("مديونية الموردين", supplierDebt, "إجمالي الباقي على الحساب"));

        List<MovementDto> recent = inventory.movements().stream().limit(8).toList();
        List<DashboardAlert> alerts = new ArrayList<>();
        for (StockRow row : inventory.onHand()) {
            if ("منخفض".equals(row.status())) {
                alerts.add(new DashboardAlert("low-stock", row.name() + " تحت حد التنبيه — المتاح " + row.onHand()));
            }
        }
        suppliers.findAll().forEach(supplier -> {
            BigDecimal debt = balances.supplierDebt(supplier.getId());
            if (debt.compareTo(new BigDecimal("1000")) > 0) {
                alerts.add(new DashboardAlert("supplier-debt", supplier.getName() + " عليه " + debt + " ج.م"));
            }
        });
        return new DashboardResponse(
                kpis,
                "مشتريات اليوم دخلت المخزون ومش محسوبة وارد.",
                recent,
                alerts);
    }
}
