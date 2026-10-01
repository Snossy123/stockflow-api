package com.arafawood.api.service;

import com.arafawood.api.domain.Payment;
import com.arafawood.api.domain.PurchaseOrder;
import com.arafawood.api.domain.SalesOrder;
import com.arafawood.api.repo.PaymentRepository;
import com.arafawood.api.repo.PurchaseOrderRepository;
import com.arafawood.api.repo.SalesOrderRepository;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PartyBalanceService {

    private final PurchaseOrderRepository purchases;
    private final SalesOrderRepository sales;
    private final PaymentRepository payments;

    public PartyBalanceService(
            PurchaseOrderRepository purchases, SalesOrderRepository sales, PaymentRepository payments) {
        this.purchases = purchases;
        this.sales = sales;
        this.payments = payments;
    }

    @Transactional(readOnly = true)
    public BigDecimal supplierDebt(Long supplierId) {
        BigDecimal invoices = purchases.findBySupplierIdOrderByDocDateDesc(supplierId).stream()
                .filter(po -> "approved".equals(po.getStatus()))
                .map(PurchaseOrder::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paid = payments.findByPartyTypeAndPartyIdOrderByOccurredAtDesc("supplier", supplierId).stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return invoices.subtract(paid);
    }

    @Transactional(readOnly = true)
    public BigDecimal supplierPaid(Long supplierId) {
        return payments.findByPartyTypeAndPartyIdOrderByOccurredAtDesc("supplier", supplierId).stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional(readOnly = true)
    public BigDecimal customerDebt(Long customerId) {
        BigDecimal invoices = sales.findByCustomerIdOrderByDocDateDesc(customerId).stream()
                .filter(so -> "approved".equals(so.getStatus()))
                .map(SalesOrder::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paid = payments.findByPartyTypeAndPartyIdOrderByOccurredAtDesc("customer", customerId).stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return invoices.subtract(paid);
    }

    @Transactional(readOnly = true)
    public BigDecimal customerPaid(Long customerId) {
        return payments.findByPartyTypeAndPartyIdOrderByOccurredAtDesc("customer", customerId).stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional(readOnly = true)
    public BigDecimal allSupplierDebt() {
        return purchases.findAll().stream()
                .filter(po -> "approved".equals(po.getStatus()))
                .map(PurchaseOrder::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .subtract(payments.findAll().stream()
                        .filter(p -> "supplier".equals(p.getPartyType()))
                        .map(Payment::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
    }
}
