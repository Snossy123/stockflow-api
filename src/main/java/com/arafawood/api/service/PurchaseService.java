package com.arafawood.api.service;

import com.arafawood.api.common.ApiException;
import com.arafawood.api.common.BusinessDates;
import com.arafawood.api.domain.AppUser;
import com.arafawood.api.domain.Item;
import com.arafawood.api.domain.Payment;
import com.arafawood.api.domain.PurchaseOrder;
import com.arafawood.api.domain.PurchaseOrderItem;
import com.arafawood.api.domain.Supplier;
import com.arafawood.api.dto.Dtos.ApproveEffect;
import com.arafawood.api.dto.Dtos.LineWrite;
import com.arafawood.api.dto.Dtos.PurchaseDto;
import com.arafawood.api.dto.Dtos.PurchaseLineDto;
import com.arafawood.api.dto.Dtos.PurchaseWrite;
import com.arafawood.api.repo.ItemRepository;
import com.arafawood.api.repo.PaymentRepository;
import com.arafawood.api.repo.PurchaseOrderRepository;
import com.arafawood.api.repo.SupplierRepository;
import com.arafawood.api.security.CurrentUserService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseService {

    private final PurchaseOrderRepository purchases;
    private final SupplierRepository suppliers;
    private final ItemRepository items;
    private final PaymentRepository payments;
    private final SequenceService sequences;
    private final StockService stock;
    private final AuditService audit;
    private final PeriodLockService periodLock;
    private final CurrentUserService currentUser;

    public PurchaseService(
            PurchaseOrderRepository purchases,
            SupplierRepository suppliers,
            ItemRepository items,
            PaymentRepository payments,
            SequenceService sequences,
            StockService stock,
            AuditService audit,
            PeriodLockService periodLock,
            CurrentUserService currentUser) {
        this.purchases = purchases;
        this.suppliers = suppliers;
        this.items = items;
        this.payments = payments;
        this.sequences = sequences;
        this.stock = stock;
        this.audit = audit;
        this.periodLock = periodLock;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<PurchaseDto> list() {
        currentUser.require("purchases", "view");
        return purchases.findAllByOrderByDocDateDescIdDesc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public PurchaseDto get(Long id) {
        currentUser.require("purchases", "view");
        return toDto(load(id));
    }

    @Transactional
    public PurchaseDto create(PurchaseWrite write) {
        AppUser user = currentUser.require("purchases", "create");
        PurchaseOrder po = new PurchaseOrder();
        po.setNo(sequences.next("PO"));
        po.setCreatedBy(user);
        po.setCreatedAt(Instant.now());
        apply(po, write);
        purchases.save(po);
        return toDto(po);
    }

    @Transactional
    public PurchaseDto update(Long id, PurchaseWrite write) {
        currentUser.require("purchases", "create");
        PurchaseOrder po = load(id);
        assertDraft(po);
        apply(po, write);
        return toDto(purchases.save(po));
    }

    @Transactional
    public ApproveEffect approve(Long id) {
        AppUser user = currentUser.require("purchases", "approve");
        PurchaseOrder po = load(id);
        assertDraft(po);
        LocalDate postingDate = BusinessDates.resolvePostingDate(po.getDocDate());
        periodLock.assertOpen(postingDate);
        if (po.getItems().isEmpty()) {
            throw ApiException.badRequest("فاتورة الشراء محتاجة بنود");
        }
        Instant occurredAt = BusinessDates.occurredAt(postingDate);
        po.setStatus("approved");
        po.setPostingDate(postingDate);
        po.setApprovedBy(user);
        po.setApprovedAt(Instant.now());
        purchases.save(po);
        for (PurchaseOrderItem line : po.getItems()) {
            stock.moveIn(line.getItem(), "purchase", line.getQty(), line.getUnitCost(), "purchase", po.getId(), occurredAt);
        }
        if (po.getPaidNow().compareTo(BigDecimal.ZERO) > 0) {
            Payment payment = new Payment();
            payment.setPartyType("supplier");
            payment.setPartyId(po.getSupplier().getId());
            payment.setAmount(po.getPaidNow());
            payment.setKind("payment");
            payment.setMethod("cash");
            payment.setRef(po.getNo());
            payment.setCreatedBy(user);
            payment.setOccurredAt(occurredAt);
            payments.save(payment);
        }
        audit.record(user, "approve", "purchase_order", po.getId(), "draft", "approved");
        Map<String, BigDecimal> numbers = new LinkedHashMap<>();
        numbers.put("addedQty", po.getItems().stream().map(PurchaseOrderItem::getQty).reduce(BigDecimal.ZERO, BigDecimal::add));
        numbers.put("total", po.getTotal());
        numbers.put("paidNow", po.getPaidNow());
        numbers.put("onAccount", po.getTotal().subtract(po.getPaidNow()));
        return new ApproveEffect("اتضافت الكميات للمخزون واتسجلت القيمة على حساب المورد. الفاتورة اتقفلت.", numbers);
    }

    @Transactional
    public ApproveEffect reverse(Long id) {
        AppUser user = currentUser.require("purchases", "approve");
        PurchaseOrder original = load(id);
        if (!"approved".equals(original.getStatus())) {
            throw ApiException.badRequest("القيد العكسي للفواتير المعتمدة فقط");
        }
        LocalDate postingDate = BusinessDates.today();
        LocalDate originalPosted = original.getPostingDate() != null ? original.getPostingDate() : original.getDocDate();
        periodLock.assertOpen(originalPosted, postingDate);
        Instant occurredAt = BusinessDates.occurredAt(postingDate);
        PurchaseOrder reversal = new PurchaseOrder();
        reversal.setNo(sequences.next("PO"));
        reversal.setSupplier(original.getSupplier());
        reversal.setDocDate(postingDate);
        reversal.setPostingDate(postingDate);
        reversal.setStatus("reversed");
        reversal.setPaidNow(original.getPaidNow().negate());
        reversal.setTotal(original.getTotal().negate());
        reversal.setCreatedBy(user);
        reversal.setApprovedBy(user);
        reversal.setApprovedAt(Instant.now());
        reversal.setReversedOf(original);
        for (PurchaseOrderItem line : original.getItems()) {
            PurchaseOrderItem copy = new PurchaseOrderItem();
            copy.setPurchaseOrder(reversal);
            copy.setItem(line.getItem());
            copy.setQty(line.getQty());
            copy.setUnitCost(line.getUnitCost());
            copy.setTotal(line.getTotal().negate());
            reversal.getItems().add(copy);
            stock.moveOut(line.getItem(), "reversal", line.getQty(), line.getUnitCost(), "purchase", original.getId(), occurredAt);
        }
        purchases.save(reversal);
        original.setStatus("reversed");
        purchases.save(original);
        if (original.getPaidNow().compareTo(BigDecimal.ZERO) > 0) {
            Payment payment = new Payment();
            payment.setPartyType("supplier");
            payment.setPartyId(original.getSupplier().getId());
            payment.setAmount(original.getPaidNow().negate());
            payment.setKind("payment");
            payment.setMethod("cash");
            payment.setRef("عكس " + original.getNo());
            payment.setCreatedBy(user);
            payment.setOccurredAt(occurredAt);
            payments.save(payment);
        }
        audit.record(user, "reverse", "purchase_order", original.getId(), "approved", "reversed");
        return new ApproveEffect("اتعمل قيد عكسي مربوط بالفاتورة الأصلية.", Map.of("total", reversal.getTotal()));
    }

    private void apply(PurchaseOrder po, PurchaseWrite write) {
        Supplier supplier = suppliers.findById(write.supplierId()).orElseThrow(() -> ApiException.notFound("المورد غير موجود"));
        po.setSupplier(supplier);
        po.setDocDate(write.docDate() == null ? BusinessDates.today() : write.docDate());
        po.setPaidNow(nz(write.paidNow()));
        po.getItems().clear();
        BigDecimal total = BigDecimal.ZERO;
        for (LineWrite line : write.items() == null ? List.<LineWrite>of() : write.items()) {
            Item item = items.findById(line.itemId()).orElseThrow(() -> ApiException.badRequest("اختار صنف من الدليل"));
            BigDecimal qty = requirePositive(line.qty(), "العدد");
            BigDecimal cost = requirePositive(line.unitCost(), "التكلفة");
            PurchaseOrderItem row = new PurchaseOrderItem();
            row.setPurchaseOrder(po);
            row.setItem(item);
            row.setQty(qty);
            row.setUnitCost(cost);
            row.setTotal(qty.multiply(cost));
            po.getItems().add(row);
            total = total.add(row.getTotal());
        }
        po.setTotal(total);
    }

    private PurchaseOrder load(Long id) {
        return purchases.findById(id).orElseThrow(() -> ApiException.notFound("فاتورة الشراء غير موجودة"));
    }

    private void assertDraft(PurchaseOrder po) {
        if (!"draft".equals(po.getStatus())) {
            throw ApiException.badRequest("الفاتورة المعتمدة لا تُعدّل — التصحيح بقيد عكسي");
        }
    }

    private PurchaseDto toDto(PurchaseOrder po) {
        List<PurchaseLineDto> lines = new ArrayList<>();
        for (PurchaseOrderItem line : po.getItems()) {
            Item item = line.getItem();
            lines.add(new PurchaseLineDto(
                    line.getId(),
                    item.getId(),
                    item.getCode(),
                    item.getName(),
                    item.getCategory().getName(),
                    item.getLength(),
                    item.getWidth(),
                    item.getThickness(),
                    item.getUnit().getName(),
                    line.getQty(),
                    line.getUnitCost(),
                    line.getTotal()));
        }
        return new PurchaseDto(
                po.getId(),
                po.getNo(),
                po.getSupplier().getId(),
                po.getSupplier().getName(),
                po.getDocDate(),
                po.getPostingDate(),
                po.getStatus(),
                po.getPaidNow(),
                po.getTotal().subtract(po.getPaidNow()),
                po.getTotal(),
                po.getReversedOf() == null ? null : po.getReversedOf().getId(),
                po.getReversedOf() == null ? null : po.getReversedOf().getNo(),
                lines);
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal requirePositive(BigDecimal value, String label) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest(label + " لازم يكون أكبر من صفر");
        }
        return value;
    }
}
