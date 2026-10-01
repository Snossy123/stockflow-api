package com.arafawood.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "accounting_periods")
public class AccountingPeriod {

    @Id
    @Column(name = "year_month", length = 7)
    private String yearMonth;

    @Column(nullable = false, length = 16)
    private String status = "open";

    @Column(name = "closed_at")
    private Instant closedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by")
    private AppUser closedBy;

    @Column(name = "reopened_at")
    private Instant reopenedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reopened_by")
    private AppUser reopenedBy;

    public String getYearMonth() {
        return yearMonth;
    }

    public void setYearMonth(String yearMonth) {
        this.yearMonth = yearMonth;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public AppUser getClosedBy() {
        return closedBy;
    }

    public void setClosedBy(AppUser closedBy) {
        this.closedBy = closedBy;
    }

    public Instant getReopenedAt() {
        return reopenedAt;
    }

    public void setReopenedAt(Instant reopenedAt) {
        this.reopenedAt = reopenedAt;
    }

    public AppUser getReopenedBy() {
        return reopenedBy;
    }

    public void setReopenedBy(AppUser reopenedBy) {
        this.reopenedBy = reopenedBy;
    }
}
