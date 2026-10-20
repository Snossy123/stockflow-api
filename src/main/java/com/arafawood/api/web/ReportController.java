package com.arafawood.api.web;

import com.arafawood.api.dto.Dtos.AgingReport;
import com.arafawood.api.dto.Dtos.CashReport;
import com.arafawood.api.dto.Dtos.ItemProfitRow;
import com.arafawood.api.dto.Dtos.PnlRow;
import com.arafawood.api.dto.Dtos.ReorderRow;
import com.arafawood.api.service.ReportService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/pnl")
    public List<PnlRow> pnl(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return reports.pnl(from, to);
    }

    @GetMapping("/cash")
    public CashReport cash(
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        if (from != null || to != null) {
            return reports.cash(from, to);
        }
        return reports.cash(date, date);
    }

    @GetMapping("/aging")
    public AgingReport aging() {
        return reports.aging();
    }

    @GetMapping("/item-profit")
    public List<ItemProfitRow> itemProfit(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return reports.itemProfit(from, to);
    }

    @GetMapping("/reorder")
    public List<ReorderRow> reorder() {
        return reports.reorder();
    }
}
