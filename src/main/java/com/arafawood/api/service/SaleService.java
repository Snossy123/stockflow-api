package com.arafawood.api.service;

import com.arafawood.api.common.ApiException;
import com.arafawood.api.common.BusinessDates;
import com.arafawood.api.domain.AppUser;
import com.arafawood.api.domain.Category;
import com.arafawood.api.domain.Customer;
import com.arafawood.api.domain.Item;
import com.arafawood.api.domain.Payment;
import com.arafawood.api.domain.SalesOrder;
import com.arafawood.api.domain.SalesOrderItem;
import com.arafawood.api.domain.Unit;
import com.arafawood.api.dto.Dtos.ApproveEffect;
import com.arafawood.api.dto.Dtos.ApproveSaleLineWrite;
import com.arafawood.api.dto.Dtos.ApproveSaleWrite;
import com.arafawood.api.dto.Dtos.LineWrite;
import com.arafawood.api.dto.Dtos.ReturnSaleLineWrite;
import com.arafawood.api.dto.Dtos.ReturnSaleWrite;
import com.arafawood.api.dto.Dtos.SaleDto;
import com.arafawood.api.dto.Dtos.SaleLineDto;
import com.arafawood.api.dto.Dtos.SaleWrite;
import com.arafawood.api.repo.CategoryRepository;
import com.arafawood.api.repo.CustomerRepository;
import com.arafawood.api.repo.ItemRepository;
import com.arafawood.api.repo.PaymentRepository;
import com.arafawood.api.repo.SalesOrderRepository;
import com.arafawood.api.repo.UnitRepository;
import com.arafawood.api.security.CurrentUserService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SaleService {

    private final SalesOrderRepository sales;
    private final CustomerRepository customers;
    private final ItemRepository items;
    private final CategoryRepository categories;
    private final UnitRepository units;
    private final PaymentRepository payments;
    private final SequenceService sequences;
    private final StockService stock;
    private final SettingService settings;
    private final AuditService audit;
    private final PeriodLockService periodLock;
    private final CurrentUserService currentUser;

    public SaleService(
            SalesOrderRepository sales,
            CustomerRepository customers,
            ItemRepository items,
            CategoryRepository categories,
            UnitRepository units,
            PaymentRepository payments,
            SequenceService sequences,
            StockService stock,
            SettingService settings,
            AuditService audit,
            PeriodLockService periodLock,
            CurrentUserService currentUser) {
        this.sales = sales;
        this.customers = customers;
        this.items = items;
        this.categories = categories;
        this.units = units;
        this.payments = payments;
        this.sequences = sequences;
        this.stock = stock;
        this.settings = settings;
        this.audit = audit;
        this.periodLock = periodLock;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<SaleDto> list() {
        currentUser.require("sales", "view");
        return sales.findAllByOrderByDocDateDescIdDesc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public SaleDto get(Long id) {
        currentUser.require("sales", "view");
        return toDto(load(id));
    }

    @Transactional
    public SaleDto create(SaleWrite write) {
        AppUser user = currentUser.require("sales", "create");
        SalesOrder so = new SalesOrder();
        so.setNo(sequences.next("SO"));
        so.setCreatedBy(user);
        so.setCreatedAt(Instant.now());
        so.setPricing("avg");
        apply(so, write);
        return toDto(sales.save(so));
    }

    @Transactional
    public SaleDto update(Long id, SaleWrite write) {
        currentUser.require("sales", "create");
        SalesOrder so = load(id);
        assertDraft(so);
        apply(so, write);
        return toDto(sales.save(so));
    }

    @Transactional
    public ApproveEffect approve(Long id, ApproveSaleWrite write) {
        AppUser user = currentUser.require("sales", "approve");
        SalesOrder so = load(id);
        assertDraft(so);
        LocalDate postingDate = BusinessDates.resolvePostingDate(so.getDocDate());
        periodLock.assertOpen(postingDate);
        if (so.getItems().isEmpty()) {
            throw ApiException.badRequest("فاتورة البيع محتاجة بنود");
        }
        if (write == null || write.lines() == null) {
            throw ApiException.badRequest("اعتماد البيع محتاج تأكيد القص والفائض لكل بند");
        }
        Map<Long, ApproveSaleLineWrite> choices = new HashMap<>();
        for (ApproveSaleLineWrite line : write.lines()) {
            if (line.lineId() != null) {
                choices.put(line.lineId(), line);
            }
        }
        BigDecimal surplusCost = settings.decimal("surplus_cost", "0");
        Instant occurredAt = BusinessDates.occurredAt(postingDate);

        BigDecimal cogs = BigDecimal.ZERO;
        for (SalesOrderItem line : so.getItems()) {
            boolean surplusCatalog = isSurplusItem(line.getItem());
            BigDecimal cutQty = BigDecimal.ZERO;
            if (surplusCatalog) {
                line.setCut(false);
                line.setCutQty(BigDecimal.ZERO);
            } else {
                ApproveSaleLineWrite choice = choices.get(line.getId());
                if (choice == null) {
                    throw ApiException.badRequest("أكد القص والفائض على كل البنود");
                }
                if (choice.surplus() && !choice.cut()) {
                    throw ApiException.badRequest("الفائض يتولد بعد القص فقط");
                }
                if (choice.cut()) {
                    cutQty = choice.cutQty() == null ? line.getQty() : choice.cutQty();
                    if (cutQty.compareTo(BigDecimal.ZERO) <= 0) {
                        throw ApiException.badRequest("حدد عدد الألواح المقصوصة لـ " + line.getItem().getName());
                    }
                    if (cutQty.compareTo(line.getQty()) > 0) {
                        throw ApiException.badRequest(
                                line.getItem().getName() + " — عدد الألواح المقصوصة (" + cutQty + ") أكبر من كمية البند (" + line.getQty() + ")");
                    }
                    line.setCut(true);
                    line.setCutQty(cutQty);
                } else {
                    line.setCut(false);
                    line.setCutQty(BigDecimal.ZERO);
                }
            }
            BigDecimal onHand = stock.onHand(line.getItem().getId());
            if (line.getQty().compareTo(onHand) > 0) {
                throw ApiException.conflict(
                        line.getItem().getName() + " — المطلوب " + line.getQty() + " والمتاح " + onHand,
                        "OVERSELL");
            }
            BigDecimal avg = stock.averageCost(line.getItem().getId());
            line.setUnitCost(avg);
            cogs = cogs.add(line.getQty().multiply(avg));
            stock.moveOut(line.getItem(), "sale", line.getQty(), avg, "sale", so.getId(), occurredAt);

            if (!surplusCatalog && choices.get(line.getId()).surplus()) {
                Item surplusItem = resolveSurplusItem(line.getItem());
                line.setSurplusGenerated(true);
                stock.moveIn(surplusItem, "surplus_in", cutQty, surplusCost, "sale", so.getId(), occurredAt);
            }
        }
        so.setCogs(cogs);
        so.setKind("sale");
        so.setStatus("approved");
        so.setPostingDate(postingDate);
        so.setApprovedBy(user);
        so.setApprovedAt(Instant.now());
        sales.save(so);
        if (so.getPaidNow().compareTo(BigDecimal.ZERO) > 0) {
            saveCash("customer", so.getCustomer().getId(), so.getPaidNow(), "receipt", so.getNo(), user, occurredAt);
        }
        audit.record(user, "approve", "sales_order", so.getId(), "draft", "approved");
        Map<String, BigDecimal> numbers = new LinkedHashMap<>();
        numbers.put("total", so.getTotal());
        numbers.put("cogs", so.getCogs());
        numbers.put("profit", so.getTotal().subtract(so.getCogs()));
        numbers.put("paidNow", so.getPaidNow());
        numbers.put("onAccount", so.getTotal().subtract(so.getPaidNow()));
        return new ApproveEffect("اتخصم من المخزون واتجمّدت تكلفة المبيعات. الفاتورة اتقفلت.", numbers);
    }

    @Transactional
    public ApproveEffect returnSale(Long id, ReturnSaleWrite write) {
        AppUser user = currentUser.require("sales", "approve");
        SalesOrder original = load(id);
        if (!"approved".equals(original.getStatus()) || !"sale".equals(original.getKind())) {
            throw ApiException.badRequest("المرتجع لفواتير البيع المعتمدة فقط");
        }
        if (write == null || write.items() == null) {
            throw ApiException.badRequest("اختار بنود المرتجع");
        }
        LocalDate postingDate = BusinessDates.today();
        LocalDate originalPosted = original.getPostingDate() != null ? original.getPostingDate() : original.getDocDate();
        periodLock.assertOpen(originalPosted, postingDate);
        Instant occurredAt = BusinessDates.occurredAt(postingDate);

        Map<Long, BigDecimal> alreadyReturned = returnedQtyBySource(original.getId());
        Map<Long, SalesOrderItem> originalLines = new HashMap<>();
        for (SalesOrderItem line : original.getItems()) {
            originalLines.put(line.getId(), line);
        }

        SalesOrder reversal = new SalesOrder();
        reversal.setNo(sequences.next("SO"));
        reversal.setCustomer(original.getCustomer());
        reversal.setDocDate(postingDate);
        reversal.setPostingDate(postingDate);
        reversal.setKind("return");
        reversal.setPricing(original.getPricing());
        reversal.setStatus("approved");
        reversal.setCreatedBy(user);
        reversal.setApprovedBy(user);
        reversal.setApprovedAt(Instant.now());
        reversal.setReversedOf(original);

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal cogs = BigDecimal.ZERO;
        int returnedLines = 0;
        for (ReturnSaleLineWrite row : write.items()) {
            if (row.lineId() == null || row.qty() == null || row.qty().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            SalesOrderItem source = originalLines.get(row.lineId());
            if (source == null) {
                throw ApiException.badRequest("بند المرتجع مش على الفاتورة الأصلية");
            }
            BigDecimal sourceCutQty = source.isCut()
                    ? (source.getCutQty() != null && source.getCutQty().compareTo(BigDecimal.ZERO) > 0 ? source.getCutQty() : source.getQty())
                    : BigDecimal.ZERO;
            BigDecimal unCutQty = source.getQty().subtract(sourceCutQty).max(BigDecimal.ZERO);
            if (unCutQty.compareTo(BigDecimal.ZERO) <= 0) {
                throw ApiException.badRequest(source.getItem().getName() + " اتقص بالكامل — مينفعش يرجع");
            }
            BigDecimal used = alreadyReturned.getOrDefault(source.getId(), BigDecimal.ZERO);
            BigDecimal remaining = unCutQty.subtract(used).max(BigDecimal.ZERO);
            if (row.qty().compareTo(remaining) > 0) {
                throw ApiException.badRequest(
                        source.getItem().getName() + " — المطلوب " + row.qty() + " والمتبقي للرجوع (غير المقصوص) " + remaining);
            }
            BigDecimal lineTotal = source.getUnitPrice().multiply(row.qty()).negate();
            BigDecimal lineCogs = source.getUnitCost().multiply(row.qty()).negate();
            SalesOrderItem copy = new SalesOrderItem();
            copy.setSalesOrder(reversal);
            copy.setItem(source.getItem());
            copy.setQty(row.qty());
            copy.setUnitPrice(source.getUnitPrice());
            copy.setUnitCost(source.getUnitCost());
            copy.setTotal(lineTotal);
            copy.setLengthDesc(source.getLengthDesc());
            copy.setBoardLength(source.getBoardLength());
            copy.setCutLength(source.getCutLength());
            copy.setCut(false);
            copy.setCutQty(BigDecimal.ZERO);
            copy.setSourceLine(source);
            reversal.getItems().add(copy);
            total = total.add(lineTotal);
            cogs = cogs.add(lineCogs);
            returnedLines++;
        }
        if (returnedLines == 0) {
            throw ApiException.badRequest("المرتجع محتاج بند واحد على الأقل");
        }
        BigDecimal receivedNow = nz(write.receivedNow());
        if (receivedNow.compareTo(BigDecimal.ZERO) < 0) {
            throw ApiException.badRequest("المستلم يُرد الآن لازم يكون صفر أو أكبر");
        }
        BigDecimal maxRefund = maxRefund(original, total.abs());
        if (receivedNow.compareTo(maxRefund) > 0) {
            throw ApiException.badRequest("رد الاستلام أكبر من المتاح: " + maxRefund);
        }
        reversal.setTotal(total);
        reversal.setCogs(cogs);
        reversal.setPaidNow(receivedNow.negate());
        sales.save(reversal);
        for (SalesOrderItem line : reversal.getItems()) {
            stock.moveIn(line.getItem(), "return", line.getQty(), line.getUnitCost(), "sale", reversal.getId(), occurredAt);
        }
        if (receivedNow.compareTo(BigDecimal.ZERO) > 0) {
            saveCash(
                    "customer",
                    original.getCustomer().getId(),
                    receivedNow.negate(),
                    "receipt",
                    "مرتجع " + original.getNo(),
                    user,
                    occurredAt);
        }
        audit.record(user, "return", "sales_order", reversal.getId(), "approved", original.getNo());
        return new ApproveEffect(
                "اتعمل مرتجع مربوط بالفاتورة الأصلية. الكمية رجعت للمخزون.",
                Map.of("total", reversal.getTotal(), "cogs", reversal.getCogs(), "receivedNow", receivedNow.negate()));
    }

    private void apply(SalesOrder so, SaleWrite write) {
        Customer customer = customers.findById(write.customerId()).orElseThrow(() -> ApiException.notFound("العميل غير موجود"));
        so.setCustomer(customer);
        so.setDocDate(write.docDate() == null ? BusinessDates.today() : write.docDate());
        so.setKind("sale");
        so.setPaidNow(nz(write.paidNow()));
        so.getItems().clear();
        BigDecimal total = BigDecimal.ZERO;
        for (LineWrite line : write.items() == null ? List.<LineWrite>of() : write.items()) {
            Item item = items.findById(line.itemId()).orElseThrow(() -> ApiException.badRequest("اختار صنف من الدليل"));
            BigDecimal qty = requirePositive(line.qty(), "العدد");
            BigDecimal price = requirePositive(line.unitPrice(), "سعر الوحدة");
            SalesOrderItem row = new SalesOrderItem();
            row.setSalesOrder(so);
            row.setItem(item);
            row.setQty(qty);
            row.setUnitPrice(price);
            row.setUnitCost(stock.averageCost(item.getId()));
            row.setTotal(qty.multiply(price));
            row.setLengthDesc(line.lengthDesc());
            row.setBoardLength(line.boardLength());
            row.setCutLength(line.cutLength());
            so.getItems().add(row);
            total = total.add(row.getTotal());
        }
        so.setTotal(total);
    }

    private SalesOrder load(Long id) {
        return sales.findById(id).orElseThrow(() -> ApiException.notFound("فاتورة البيع غير موجودة"));
    }

    private void assertDraft(SalesOrder so) {
        if (!"draft".equals(so.getStatus())) {
            throw ApiException.badRequest("الفاتورة المعتمدة لا تُعدّل — التصحيح بمرتجع أو قيد عكسي");
        }
    }

    private SaleDto toDto(SalesOrder so) {
        Map<Long, BigDecimal> returned = "sale".equals(so.getKind()) ? returnedQtyBySource(so.getId()) : Map.of();
        List<SaleLineDto> lines = new ArrayList<>();
        boolean oversell = false;
        for (SalesOrderItem line : so.getItems()) {
            BigDecimal onHand = stock.onHand(line.getItem().getId());
            boolean surplusItem = isSurplusItem(line.getItem());
            boolean lineOver = line.getQty().compareTo(onHand) > 0 && "draft".equals(so.getStatus());
            oversell = oversell || lineOver;
            BigDecimal lineCutQty = line.isCut()
                    ? (line.getCutQty() != null && line.getCutQty().compareTo(BigDecimal.ZERO) > 0 ? line.getCutQty() : line.getQty())
                    : BigDecimal.ZERO;
            BigDecimal returnable = BigDecimal.ZERO;
            if ("approved".equals(so.getStatus()) && "sale".equals(so.getKind())) {
                BigDecimal unCut = line.getQty().subtract(lineCutQty).max(BigDecimal.ZERO);
                returnable = unCut.subtract(returned.getOrDefault(line.getId(), BigDecimal.ZERO)).max(BigDecimal.ZERO);
            }
            lines.add(new SaleLineDto(
                    line.getId(),
                    line.getItem().getId(),
                    line.getItem().getCode(),
                    line.getItem().getName(),
                    line.getItem().getUnit().getName(),
                    onHand,
                    line.getLengthDesc(),
                    line.getBoardLength(),
                    line.getCutLength(),
                    line.getQty(),
                    line.getUnitPrice(),
                    line.getUnitCost(),
                    line.getTotal(),
                    line.isSurplusGenerated(),
                    line.isCut(),
                    lineCutQty,
                    surplusItem,
                    returnable,
                    lineOver));
        }
        return new SaleDto(
                so.getId(),
                so.getNo(),
                so.getCustomer().getId(),
                so.getCustomer().getName(),
                so.getDocDate(),
                so.getPostingDate(),
                so.getKind(),
                so.getStatus(),
                so.getPaidNow(),
                so.getTotal().subtract(so.getPaidNow()),
                so.getTotal(),
                so.getCogs(),
                so.getTotal().subtract(so.getCogs()),
                oversell,
                so.getReversedOf() == null ? null : so.getReversedOf().getId(),
                so.getReversedOf() == null ? null : so.getReversedOf().getNo(),
                lines);
    }

    private Map<Long, BigDecimal> returnedQtyBySource(Long originalId) {
        Map<Long, BigDecimal> used = new HashMap<>();
        for (SalesOrder ret : sales.findByReversedOfId(originalId)) {
            if (!"approved".equals(ret.getStatus())) {
                continue;
            }
            for (SalesOrderItem line : ret.getItems()) {
                if (line.getSourceLine() == null) {
                    continue;
                }
                used.merge(line.getSourceLine().getId(), line.getQty(), BigDecimal::add);
            }
        }
        return used;
    }

    private BigDecimal maxRefund(SalesOrder original, BigDecimal returnedValue) {
        BigDecimal already = sales.findByReversedOfId(original.getId()).stream()
                .map(so -> so.getPaidNow().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remainingCash = original.getPaidNow().subtract(already).max(BigDecimal.ZERO);
        if (original.getTotal().compareTo(BigDecimal.ZERO) <= 0) {
            return remainingCash;
        }
        BigDecimal proportional = original.getPaidNow()
                .multiply(returnedValue)
                .divide(original.getTotal(), 2, java.math.RoundingMode.HALF_UP);
        return proportional.min(remainingCash);
    }

    private void saveCash(
            String partyType,
            Long partyId,
            BigDecimal amount,
            String kind,
            String ref,
            AppUser user,
            Instant occurredAt) {
        Payment payment = new Payment();
        payment.setPartyType(partyType);
        payment.setPartyId(partyId);
        payment.setAmount(amount);
        payment.setKind(kind);
        payment.setMethod("cash");
        payment.setRef(ref);
        payment.setCreatedBy(user);
        payment.setOccurredAt(occurredAt);
        payments.save(payment);
    }

    private Item resolveSurplusItem(Item source) {
        String rawCode = "SUR-" + source.getCode();
        String code = rawCode.length() > 32 ? rawCode.substring(0, 32) : rawCode;
        return items.findByCode(code).orElseGet(() -> {
            Category surplusCat = categories.findByName("فائض").orElseGet(() -> {
                Category c = new Category();
                c.setName("فائض");
                return categories.save(c);
            });
            Unit pieceUnit = units.findByName("قطعة").orElse(source.getUnit());
            Item created = new Item();
            created.setCode(code);
            created.setName(source.getName());
            created.setCategory(surplusCat);
            created.setThickness(source.getThickness());
            created.setWidth(source.getWidth());
            created.setLength(null);
            created.setUnit(pieceUnit);
            created.setMinQty(BigDecimal.ZERO);
            return items.save(created);
        });
    }

    private boolean isSurplusItem(Item item) {
        return item.getCategory() != null && "فائض".equals(item.getCategory().getName());
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
