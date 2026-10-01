package com.arafawood.api.web;

import com.arafawood.api.dto.Dtos.ApproveEffect;
import com.arafawood.api.dto.Dtos.ExpenseDto;
import com.arafawood.api.dto.Dtos.ExpenseWrite;
import com.arafawood.api.service.ExpenseService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final ExpenseService expenses;

    public ExpenseController(ExpenseService expenses) {
        this.expenses = expenses;
    }

    @GetMapping
    public List<ExpenseDto> list() {
        return expenses.list();
    }

    @PostMapping
    public ExpenseDto create(@RequestBody ExpenseWrite write) {
        return expenses.create(write);
    }

    @PostMapping("/{id}/reverse")
    public ApproveEffect reverse(@PathVariable Long id) {
        return expenses.reverse(id);
    }
}
