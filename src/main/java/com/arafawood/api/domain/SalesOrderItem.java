package com.arafawood.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "so_items")
public class SalesOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "so_id")
    private SalesOrder salesOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id")
    private Item item;

    @Column(name = "length_desc", length = 80)
    private String lengthDesc;

    @Column(name = "board_length", precision = 12, scale = 3)
    private BigDecimal boardLength;

    @Column(name = "cut_length", precision = 12, scale = 3)
    private BigDecimal cutLength;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal qty;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "surplus_generated", nullable = false)
    private boolean surplusGenerated;

    @Column(nullable = false)
    private boolean cut;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_line_id")
    private SalesOrderItem sourceLine;

    public Long getId() {
        return id;
    }

    public SalesOrder getSalesOrder() {
        return salesOrder;
    }

    public void setSalesOrder(SalesOrder salesOrder) {
        this.salesOrder = salesOrder;
    }

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public String getLengthDesc() {
        return lengthDesc;
    }

    public void setLengthDesc(String lengthDesc) {
        this.lengthDesc = lengthDesc;
    }

    public BigDecimal getBoardLength() {
        return boardLength;
    }

    public void setBoardLength(BigDecimal boardLength) {
        this.boardLength = boardLength;
    }

    public BigDecimal getCutLength() {
        return cutLength;
    }

    public void setCutLength(BigDecimal cutLength) {
        this.cutLength = cutLength;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public void setUnitCost(BigDecimal unitCost) {
        this.unitCost = unitCost;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public boolean isSurplusGenerated() {
        return surplusGenerated;
    }

    public void setSurplusGenerated(boolean surplusGenerated) {
        this.surplusGenerated = surplusGenerated;
    }

    public boolean isCut() {
        return cut;
    }

    public void setCut(boolean cut) {
        this.cut = cut;
    }

    public SalesOrderItem getSourceLine() {
        return sourceLine;
    }

    public void setSourceLine(SalesOrderItem sourceLine) {
        this.sourceLine = sourceLine;
    }
}
