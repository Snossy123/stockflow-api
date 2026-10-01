package com.arafawood.api.web;

import com.arafawood.api.dto.Dtos.ApproveEffect;
import com.arafawood.api.dto.Dtos.PurchaseDto;
import com.arafawood.api.dto.Dtos.PurchaseWrite;
import com.arafawood.api.service.PurchaseService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/purchases")
public class PurchaseController {

    private final PurchaseService purchases;

    public PurchaseController(PurchaseService purchases) {
        this.purchases = purchases;
    }

    @GetMapping
    public List<PurchaseDto> list() {
        return purchases.list();
    }

    @GetMapping("/{id}")
    public PurchaseDto get(@PathVariable Long id) {
        return purchases.get(id);
    }

    @PostMapping
    public PurchaseDto create(@RequestBody PurchaseWrite write) {
        return purchases.create(write);
    }

    @PutMapping("/{id}")
    public PurchaseDto update(@PathVariable Long id, @RequestBody PurchaseWrite write) {
        return purchases.update(id, write);
    }

    @PostMapping("/{id}/approve")
    public ApproveEffect approve(@PathVariable Long id) {
        return purchases.approve(id);
    }

    @PostMapping("/{id}/reverse")
    public ApproveEffect reverse(@PathVariable Long id) {
        return purchases.reverse(id);
    }
}
