package com.arafawood.api.service;

import com.arafawood.api.common.ApiException;
import com.arafawood.api.domain.AccountingPeriod;
import com.arafawood.api.domain.AppUser;
import com.arafawood.api.dto.Dtos.PeriodDto;
import com.arafawood.api.repo.AccountingPeriodRepository;
import com.arafawood.api.security.CurrentUserService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PeriodLockService {

    public static final ZoneId CAIRO = ZoneId.of("Africa/Cairo");
    private static final int LIST_MONTHS = 18;

    private final AccountingPeriodRepository periods;
    private final CurrentUserService currentUser;
    private final AuditService audit;

    public PeriodLockService(
            AccountingPeriodRepository periods, CurrentUserService currentUser, AuditService audit) {
        this.periods = periods;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    public LocalDate today() {
        return LocalDate.now(CAIRO);
    }

    public boolean isClosed(LocalDate date) {
        if (date == null) {
            return false;
        }
        return isClosed(YearMonth.from(date));
    }

    public boolean isClosed(YearMonth month) {
        return periods.findById(month.toString())
                .map(row -> "closed".equals(row.getStatus()))
                .orElse(false);
    }

    public void assertOpen(LocalDate... dates) {
        for (LocalDate date : dates) {
            if (date == null) {
                continue;
            }
            YearMonth month = YearMonth.from(date);
            if (isClosed(month)) {
                throw ApiException.conflict(
                        "الفترة " + month + " مقفولة. الاعتماد والدفع والوارد والجرد واقفين.",
                        "PERIOD_LOCKED");
            }
        }
    }

    @Transactional(readOnly = true)
    public List<PeriodDto> list() {
        currentUser.require("periods", "view");
        YearMonth now = YearMonth.now(CAIRO);
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < LIST_MONTHS; i++) {
            keys.add(now.minusMonths(i).toString());
        }
        Map<String, AccountingPeriod> stored = periods.findByYearMonthIn(keys).stream()
                .collect(Collectors.toMap(AccountingPeriod::getYearMonth, Function.identity()));
        List<PeriodDto> rows = new ArrayList<>();
        for (String key : keys) {
            rows.add(toDto(key, stored.get(key)));
        }
        return rows;
    }

    @Transactional
    public PeriodDto close(String yearMonth) {
        AppUser user = currentUser.require("periods", "close");
        YearMonth month = parseMonth(yearMonth);
        if (month.isAfter(YearMonth.now(CAIRO))) {
            throw ApiException.badRequest("مفيش قفل لشهر لسه مجاش");
        }
        AccountingPeriod row = periods.findById(month.toString()).orElseGet(() -> {
            AccountingPeriod created = new AccountingPeriod();
            created.setYearMonth(month.toString());
            return created;
        });
        if ("closed".equals(row.getStatus())) {
            throw ApiException.badRequest("الفترة " + month + " مقفولة بالفعل");
        }
        row.setStatus("closed");
        row.setClosedAt(Instant.now());
        row.setClosedBy(user);
        periods.save(row);
        audit.record(user, "close", "accounting_period", null, "open", month.toString());
        return toDto(month.toString(), row);
    }

    @Transactional
    public PeriodDto reopen(String yearMonth) {
        AppUser user = currentUser.require("periods", "reopen");
        YearMonth month = parseMonth(yearMonth);
        AccountingPeriod row = periods.findById(month.toString())
                .orElseThrow(() -> ApiException.badRequest("الفترة " + month + " مفتوحة"));
        if (!"closed".equals(row.getStatus())) {
            throw ApiException.badRequest("الفترة " + month + " مفتوحة");
        }
        row.setStatus("open");
        row.setReopenedAt(Instant.now());
        row.setReopenedBy(user);
        periods.save(row);
        audit.record(user, "reopen", "accounting_period", null, "closed", month.toString());
        return toDto(month.toString(), row);
    }

    private YearMonth parseMonth(String yearMonth) {
        try {
            return YearMonth.parse(yearMonth.trim());
        } catch (DateTimeParseException ex) {
            throw ApiException.badRequest("صيغة الشهر لازم تكون YYYY-MM");
        }
    }

    private PeriodDto toDto(String yearMonth, AccountingPeriod row) {
        if (row == null) {
            return new PeriodDto(yearMonth, "open", null, null, null, null);
        }
        return new PeriodDto(
                yearMonth,
                row.getStatus(),
                row.getClosedAt(),
                row.getClosedBy() == null ? null : row.getClosedBy().getName(),
                row.getReopenedAt(),
                row.getReopenedBy() == null ? null : row.getReopenedBy().getName());
    }
}
