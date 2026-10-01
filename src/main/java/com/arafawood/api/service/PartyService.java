package com.arafawood.api.service;

import com.arafawood.api.common.ApiException;
import com.arafawood.api.domain.AppUser;
import com.arafawood.api.domain.Customer;
import com.arafawood.api.domain.Payment;
import com.arafawood.api.domain.PurchaseOrder;
import com.arafawood.api.domain.PurchaseOrderItem;
import com.arafawood.api.domain.Supplier;
import com.arafawood.api.dto.Dtos.CustomerDto;
import com.arafawood.api.dto.Dtos.PartyWrite;
import com.arafawood.api.dto.Dtos.PaymentDto;
import com.arafawood.api.dto.Dtos.PaymentWrite;
import com.arafawood.api.dto.Dtos.PurchaseDto;
import com.arafawood.api.dto.Dtos.SaleDto;
import com.arafawood.api.dto.Dtos.SupplierDto;
import com.arafawood.api.repo.CustomerRepository;
import com.arafawood.api.repo.PaymentRepository;
import com.arafawood.api.repo.PurchaseOrderRepository;
import com.arafawood.api.repo.SalesOrderRepository;
import com.arafawood.api.repo.SupplierRepository;
import com.arafawood.api.security.CurrentUserService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PartyService {

    private final SupplierRepository suppliers;
    private final CustomerRepository customers;
    private final PaymentRepository payments;
    private final PurchaseOrderRepository purchases;
    private final SalesOrderRepository sales;
    private final PartyBalanceService balances;
    private final PurchaseService purchaseService;
    private final SaleService saleService;
    private final PeriodLockService periodLock;
    private final CurrentUserService currentUser;

    public PartyService(
            SupplierRepository suppliers,
            CustomerRepository customers,
            PaymentRepository payments,
            PurchaseOrderRepository purchases,
            SalesOrderRepository sales,
            PartyBalanceService balances,
            PurchaseService purchaseService,
            SaleService saleService,
            PeriodLockService periodLock,
            CurrentUserService currentUser) {
        this.suppliers = suppliers;
        this.customers = customers;
        this.payments = payments;
        this.purchases = purchases;
        this.sales = sales;
        this.balances = balances;
        this.purchaseService = purchaseService;
        this.saleService = saleService;
        this.periodLock = periodLock;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<SupplierDto> suppliers() {
        currentUser.require("suppliers", "view");
        return suppliers.findAll().stream().map(this::toSupplier).toList();
    }

    @Transactional
    public SupplierDto createSupplier(PartyWrite write) {
        currentUser.require("suppliers", "create");
        Supplier supplier = new Supplier();
        supplier.setName(write.name());
        supplier.setPhone(write.phone());
        suppliers.save(supplier);
        return toSupplier(supplier);
    }

    @Transactional(readOnly = true)
    public List<PurchaseDto> supplierPurchases(Long id) {
        currentUser.require("suppliers", "view");
        return purchases.findBySupplierIdOrderByDocDateDesc(id).stream()
                .map(po -> purchaseService.get(po.getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentDto> supplierPayments(Long id) {
        currentUser.require("suppliers", "view");
        return payments.findByPartyTypeAndPartyIdOrderByOccurredAtDesc("supplier", id).stream()
                .map(this::toPayment)
                .toList();
    }

    @Transactional
    public PaymentDto paySupplier(Long id, PaymentWrite write) {
        AppUser user = currentUser.require("suppliers", "create");
        suppliers.findById(id).orElseThrow(() -> ApiException.notFound("المورد غير موجود"));
        return savePayment("supplier", id, write, user);
    }

    @Transactional(readOnly = true)
    public List<CustomerDto> customers() {
        currentUser.require("customers", "view");
        return customers.findAll().stream().map(this::toCustomer).toList();
    }

    @Transactional
    public CustomerDto createCustomer(PartyWrite write) {
        currentUser.require("customers", "create");
        Customer customer = new Customer();
        customer.setName(write.name());
        customer.setPhone(write.phone());
        customers.save(customer);
        return toCustomer(customer);
    }

    @Transactional(readOnly = true)
    public List<SaleDto> customerSales(Long id) {
        currentUser.require("customers", "view");
        return sales.findByCustomerIdOrderByDocDateDesc(id).stream()
                .map(so -> saleService.get(so.getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentDto> customerPayments(Long id) {
        currentUser.require("customers", "view");
        return payments.findByPartyTypeAndPartyIdOrderByOccurredAtDesc("customer", id).stream()
                .map(this::toPayment)
                .toList();
    }

    @Transactional
    public PaymentDto collectCustomer(Long id, PaymentWrite write) {
        AppUser user = currentUser.require("customers", "create");
        customers.findById(id).orElseThrow(() -> ApiException.notFound("العميل غير موجود"));
        return savePayment("customer", id, write, user);
    }

    private PaymentDto savePayment(String type, Long partyId, PaymentWrite write, AppUser user) {
        if (write.amount() == null || write.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("customer".equals(type) ? "قيمة الاستلام لازمة" : "قيمة الدفعة لازمة");
        }
        periodLock.assertOpen(periodLock.today());
        Payment payment = new Payment();
        payment.setPartyType(type);
        payment.setPartyId(partyId);
        payment.setAmount(write.amount());
        payment.setKind("customer".equals(type) ? "receipt" : "payment");
        payment.setMethod(write.method() == null ? "cash" : write.method());
        payment.setRef(write.ref());
        payment.setCreatedBy(user);
        payments.save(payment);
        return toPayment(payment);
    }

    private SupplierDto toSupplier(Supplier supplier) {
        List<String> itemNames = purchases.findBySupplierIdOrderByDocDateDesc(supplier.getId()).stream()
                .filter(po -> "approved".equals(po.getStatus()))
                .flatMap(po -> po.getItems().stream())
                .map(PurchaseOrderItem::getItem)
                .map(item -> item.getName())
                .distinct()
                .toList();
        return new SupplierDto(
                supplier.getId(),
                supplier.getName(),
                supplier.getPhone(),
                balances.supplierDebt(supplier.getId()),
                balances.supplierPaid(supplier.getId()),
                itemNames);
    }

    private CustomerDto toCustomer(Customer customer) {
        BigDecimal owed = sales.findByCustomerIdOrderByDocDateDesc(customer.getId()).stream()
                .filter(so -> "approved".equals(so.getStatus()))
                .map(so -> so.getTotal())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paid = balances.customerPaid(customer.getId());
        return new CustomerDto(customer.getId(), customer.getName(), customer.getPhone(), owed, paid, owed.subtract(paid));
    }

    private PaymentDto toPayment(Payment payment) {
        return new PaymentDto(
                payment.getId(),
                payment.getOccurredAt(),
                payment.getAmount(),
                payment.getMethod(),
                payment.getRef(),
                payment.getKind());
    }
}
