package com.arafawood.api.web;

import com.arafawood.api.dto.Dtos.ApproveEffect;
import com.arafawood.api.dto.Dtos.ApproveSaleWrite;
import com.arafawood.api.dto.Dtos.ReturnSaleWrite;
import com.arafawood.api.dto.Dtos.SaleDto;
import com.arafawood.api.dto.Dtos.SaleWrite;
import com.arafawood.api.service.SaleService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sales")
public class SaleController {

    private final SaleService sales;

    public SaleController(SaleService sales) {
        this.sales = sales;
    }

    @GetMapping
    public List<SaleDto> list() {
        return sales.list();
    }

    @GetMapping("/{id}")
    public SaleDto get(@PathVariable Long id) {
        return sales.get(id);
    }

    @PostMapping
    public SaleDto create(@RequestBody SaleWrite write) {
        return sales.create(write);
    }

    @PutMapping("/{id}")
    public SaleDto update(@PathVariable Long id, @RequestBody SaleWrite write) {
        return sales.update(id, write);
    }

    @PostMapping("/{id}/approve")
    public ApproveEffect approve(@PathVariable Long id, @RequestBody ApproveSaleWrite write) {
        return sales.approve(id, write);
    }

    @PostMapping("/{id}/return")
    public ApproveEffect returnSale(@PathVariable Long id, @RequestBody ReturnSaleWrite write) {
        return sales.returnSale(id, write);
    }
}
