package com.arafawood.api.web;

import com.arafawood.api.dto.Dtos.ApproveEffect;
import com.arafawood.api.dto.Dtos.CountDto;
import com.arafawood.api.dto.Dtos.CountWrite;
import com.arafawood.api.dto.Dtos.MovementDto;
import com.arafawood.api.dto.Dtos.StockRow;
import com.arafawood.api.service.InventoryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventory;

    public InventoryController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @GetMapping("/on-hand")
    public List<StockRow> onHand() {
        return inventory.onHand();
    }

    @GetMapping("/movements")
    public List<MovementDto> movements() {
        return inventory.movements();
    }

    @GetMapping("/counts")
    public List<CountDto> counts() {
        return inventory.counts();
    }

    @PostMapping("/counts")
    public CountDto createCount(@RequestBody CountWrite write) {
        return inventory.createCount(write);
    }

    @PutMapping("/counts/{id}")
    public CountDto updateCount(@PathVariable Long id, @RequestBody CountWrite write) {
        return inventory.updateCount(id, write);
    }

    @PostMapping("/counts/{id}/approve")
    public ApproveEffect approveCount(@PathVariable Long id) {
        return inventory.approveCount(id);
    }
}
