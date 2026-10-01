package com.arafawood.api.web;

import com.arafawood.api.dto.Dtos.CustomerDto;
import com.arafawood.api.dto.Dtos.PartyWrite;
import com.arafawood.api.dto.Dtos.PaymentDto;
import com.arafawood.api.dto.Dtos.PaymentWrite;
import com.arafawood.api.dto.Dtos.SaleDto;
import com.arafawood.api.service.PartyService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final PartyService parties;

    public CustomerController(PartyService parties) {
        this.parties = parties;
    }

    @GetMapping
    public List<CustomerDto> list() {
        return parties.customers();
    }

    @PostMapping
    public CustomerDto create(@RequestBody PartyWrite write) {
        return parties.createCustomer(write);
    }

    @GetMapping("/{id}/sales")
    public List<SaleDto> sales(@PathVariable Long id) {
        return parties.customerSales(id);
    }

    @GetMapping("/{id}/receipts")
    public List<PaymentDto> receipts(@PathVariable Long id) {
        return parties.customerPayments(id);
    }

    @PostMapping("/{id}/receipts")
    public PaymentDto receive(@PathVariable Long id, @RequestBody PaymentWrite write) {
        return parties.collectCustomer(id, write);
    }

    @GetMapping("/{id}/payments")
    public List<PaymentDto> payments(@PathVariable Long id) {
        return parties.customerPayments(id);
    }

    @PostMapping("/{id}/payments")
    public PaymentDto collect(@PathVariable Long id, @RequestBody PaymentWrite write) {
        return parties.collectCustomer(id, write);
    }
}
