package com.arafawood.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class Dtos {
    private Dtos() {}

    public record IdName(Long id, String name) {}

    public record ItemDto(
            Long id,
            String code,
            String name,
            Long categoryId,
            String categoryName,
            String length,
            String width,
            String thickness,
            Long unitId,
            String unitName,
            BigDecimal minQty,
            BigDecimal onHand,
            BigDecimal avgCost,
            BigDecimal stockValue) {}

    public record ItemWrite(
            String code,
            String name,
            Long categoryId,
            String length,
            String width,
            String thickness,
            Long unitId,
            BigDecimal minQty,
            BigDecimal openingQty,
            BigDecimal openingUnitCost) {}

    public record ExpenseTypeDto(Long id, String name, boolean affectsStock) {}

    public record ExpenseWrite(Long typeId, BigDecimal amount, String note, Long itemId, BigDecimal qty) {}

    public record ExpenseDto(
            Long id,
            Long typeId,
            String typeName,
            boolean affectsStock,
            BigDecimal amount,
            String note,
            Instant occurredAt,
            Long itemId,
            String itemName,
            BigDecimal qty,
            String status,
            Long stockCountId,
            Long reversedOfId) {}

    public record LineWrite(Long itemId, BigDecimal qty, BigDecimal unitCost, BigDecimal unitPrice,
            String lengthDesc, BigDecimal boardLength, BigDecimal cutLength) {}

    public record PurchaseWrite(Long supplierId, LocalDate docDate, BigDecimal paidNow, List<LineWrite> items) {}

    public record PurchaseLineDto(
            Long id, Long itemId, String itemCode, String itemName, String categoryName,
            String length, String width, String thickness,
            String unitName, BigDecimal qty, BigDecimal unitCost, BigDecimal total) {}

    public record PurchaseDto(
            Long id, String no, Long supplierId, String supplierName, LocalDate docDate, LocalDate postingDate,
            String status, BigDecimal paidNow, BigDecimal remaining, BigDecimal total,
            Long reversedOfId, String reversedOfNo, List<PurchaseLineDto> items) {}

    public record ApproveEffect(String message, Map<String, BigDecimal> numbers) {}

    public record SaleWrite(
            Long customerId, LocalDate docDate, String kind, BigDecimal paidNow, List<LineWrite> items) {}

    public record ApproveSaleLineWrite(Long lineId, boolean cut, BigDecimal cutQty, boolean surplus) {}

    public record ApproveSaleWrite(List<ApproveSaleLineWrite> lines) {}

    public record ReturnSaleLineWrite(Long lineId, BigDecimal qty) {}

    public record ReturnSaleWrite(List<ReturnSaleLineWrite> items, BigDecimal receivedNow) {}

    public record SaleLineDto(
            Long id, Long itemId, String itemCode, String itemName, String unitName, BigDecimal onHand,
            String lengthDesc, BigDecimal boardLength, BigDecimal cutLength, BigDecimal qty,
            BigDecimal unitPrice, BigDecimal unitCost, BigDecimal total, boolean surplusGenerated,
            boolean cut, BigDecimal cutQty, boolean surplusItem, BigDecimal returnableQty, boolean oversell) {}

    public record SaleDto(
            Long id, String no, Long customerId, String customerName, LocalDate docDate, LocalDate postingDate,
            String kind, String status, BigDecimal paidNow, BigDecimal remaining, BigDecimal total, BigDecimal cogs,
            BigDecimal profit, boolean oversell, Long reversedOfId, String reversedOfNo, List<SaleLineDto> items) {}

    public record SupplierDto(
            Long id, String name, String phone, BigDecimal debt, BigDecimal paid, List<String> itemNames) {}

    public record CustomerDto(Long id, String name, String phone, BigDecimal owed, BigDecimal paid, BigDecimal remaining) {}

    public record PartyWrite(String name, String phone) {}

    public record PaymentWrite(BigDecimal amount, String method, String ref) {}

    public record PaymentDto(Long id, Instant occurredAt, BigDecimal amount, String method, String ref, String kind) {}

    public record StockRow(
            Long itemId, String code, String name, String categoryName,
            String length, String width, String thickness, String unitName,
            BigDecimal onHand, BigDecimal avgCost, BigDecimal stockValue, String status) {}

    public record MovementDto(
            Long id, Instant occurredAt, String kind, Long itemId, String itemName, BigDecimal qtyIn,
            BigDecimal qtyOut, BigDecimal unitCost, String refType, Long refId, String refNo, BigDecimal balanceAfter) {}

    public record CountLineWrite(Long itemId, BigDecimal actualQty, String reason) {}

    public record CountWrite(List<CountLineWrite> lines) {}

    public record CountLineDto(
            Long id, Long itemId, String itemName, BigDecimal bookQty, BigDecimal actualQty,
            BigDecimal diff, String reason) {}

    public record CountDto(Long id, String no, String status, Instant startedAt, List<CountLineDto> lines) {}

    public record DashboardKpi(String label, BigDecimal value, String hint) {}

    public record DashboardAlert(String kind, String message) {}

    public record DashboardResponse(
            List<DashboardKpi> kpis, String purchaseNote, List<MovementDto> recent, List<DashboardAlert> alerts) {}

    public record UserDto(Long id, String name, String username, String phone, String status,
            Long roleId, String roleCode, String roleName, Instant lastLoginAt) {}

    public record UserWrite(String name, String username, String password, String phone, Long roleId, String status) {}

    public record RoleMatrix(Long id, String code, String name, List<AuthPerm> permissions) {}

    public record AuthPerm(String resource, String action) {}

    public record LoginEventDto(Long id, String username, String userName, boolean success, Instant occurredAt) {}

    public record PeriodDto(
            String yearMonth,
            String status,
            Instant closedAt,
            String closedByName,
            Instant reopenedAt,
            String reopenedByName) {}

    public record PnlDayRow(
            String date,
            BigDecimal revenue,
            BigDecimal cogs,
            BigDecimal gross,
            BigDecimal expenses,
            BigDecimal net) {}

    public record PnlWeekRow(
            String key,
            String label,
            String fromDate,
            String toDate,
            BigDecimal revenue,
            BigDecimal cogs,
            BigDecimal gross,
            BigDecimal expenses,
            BigDecimal net,
            List<PnlDayRow> days) {}

    public record PnlRow(
            String period,
            BigDecimal revenue,
            BigDecimal cogs,
            BigDecimal gross,
            BigDecimal expenses,
            BigDecimal net,
            boolean closed,
            List<PnlWeekRow> weeks) {}

    public record CashRow(
            Instant at, String label, BigDecimal in, BigDecimal out, BigDecimal balance,
            String refType, Long refId, String refNo,
            String targetType, Long targetId, String targetRef) {}

    public record CashReport(BigDecimal opening, BigDecimal closing, List<CashRow> rows) {}

    public record AgingBucket(String partyName, BigDecimal d0, BigDecimal d31, BigDecimal d60) {}

    public record AgingReport(List<AgingBucket> customers, List<AgingBucket> suppliers) {}

    public record ItemProfitRow(Long itemId, String itemName, BigDecimal revenue, BigDecimal cost,
            BigDecimal profit, BigDecimal margin, BigDecimal weight) {}

    public record ReorderRow(
            Long itemId, String itemName, BigDecimal onHand, BigDecimal minQty, BigDecimal shortage,
            BigDecimal weeklyUse, BigDecimal suggested, Long cheapestSupplierId, String cheapestSupplier,
            BigDecimal cheapestCost) {}
}
