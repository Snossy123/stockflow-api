package com.arafawood.api.web;

import com.arafawood.api.dto.Dtos.PartyWrite;
import com.arafawood.api.dto.Dtos.PaymentDto;
import com.arafawood.api.dto.Dtos.PaymentWrite;
import com.arafawood.api.dto.Dtos.PurchaseDto;
import com.arafawood.api.dto.Dtos.SupplierDto;
import com.arafawood.api.service.PartyService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final PartyService parties;

    public SupplierController(PartyService parties) {
        this.parties = parties;
    }

    @GetMapping
    public List<SupplierDto> list() {
        return parties.suppliers();
    }

    @PostMapping
    public SupplierDto create(@RequestBody PartyWrite write) {
        return parties.createSupplier(write);
    }

    @GetMapping("/{id}/purchases")
    public List<PurchaseDto> purchases(@PathVariable Long id) {
        return parties.supplierPurchases(id);
    }

    @GetMapping("/{id}/payments")
    public List<PaymentDto> payments(@PathVariable Long id) {
        return parties.supplierPayments(id);
    }

    @PostMapping("/{id}/payments")
    public PaymentDto pay(@PathVariable Long id, @RequestBody PaymentWrite write) {
        return parties.paySupplier(id, write);
    }
}
