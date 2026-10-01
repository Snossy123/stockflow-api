package com.arafawood.api.web;

import com.arafawood.api.dto.Dtos.PeriodDto;
import com.arafawood.api.service.PeriodLockService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/periods")
public class PeriodController {

    private final PeriodLockService periods;

    public PeriodController(PeriodLockService periods) {
        this.periods = periods;
    }

    @GetMapping
    public List<PeriodDto> list() {
        return periods.list();
    }

    @PostMapping("/{yearMonth}/close")
    public PeriodDto close(@PathVariable String yearMonth) {
        return periods.close(yearMonth);
    }

    @PostMapping("/{yearMonth}/reopen")
    public PeriodDto reopen(@PathVariable String yearMonth) {
        return periods.reopen(yearMonth);
    }
}
