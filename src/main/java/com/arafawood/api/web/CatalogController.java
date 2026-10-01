package com.arafawood.api.web;

import com.arafawood.api.dto.Dtos.ExpenseTypeDto;
import com.arafawood.api.dto.Dtos.IdName;
import com.arafawood.api.dto.Dtos.ItemDto;
import com.arafawood.api.dto.Dtos.ItemWrite;
import com.arafawood.api.service.CatalogService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CatalogController {

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/items")
    public List<ItemDto> items() {
        return catalog.items();
    }

    @PostMapping("/items")
    public ItemDto createItem(@RequestBody ItemWrite write) {
        return catalog.createItem(write);
    }

    @PutMapping("/items/{id}")
    public ItemDto updateItem(@PathVariable Long id, @RequestBody ItemWrite write) {
        return catalog.updateItem(id, write);
    }

    @GetMapping("/categories")
    public List<IdName> categories() {
        return catalog.categories();
    }

    @PostMapping("/categories")
    public IdName createCategory(@RequestBody IdName write) {
        return catalog.createCategory(write);
    }

    @GetMapping("/units")
    public List<IdName> units() {
        return catalog.units();
    }

    @PostMapping("/units")
    public IdName createUnit(@RequestBody IdName write) {
        return catalog.createUnit(write);
    }

    @GetMapping("/expense-types")
    public List<ExpenseTypeDto> expenseTypes() {
        return catalog.expenseTypes();
    }

    @PostMapping("/expense-types")
    public ExpenseTypeDto createExpenseType(@RequestBody ExpenseTypeDto write) {
        return catalog.createExpenseType(write);
    }

    @GetMapping("/settings")
    public Map<String, String> settings() {
        return catalog.settings();
    }

    @PutMapping("/settings")
    public Map<String, String> saveSettings(@RequestBody Map<String, String> body) {
        return catalog.saveSettings(body);
    }
}
