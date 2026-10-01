package com.arafawood.api.repo;

import com.arafawood.api.domain.AccountingPeriod;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountingPeriodRepository extends JpaRepository<AccountingPeriod, String> {
    @Query("select p from AccountingPeriod p left join fetch p.closedBy left join fetch p.reopenedBy where p.yearMonth in :yearMonths")
    List<AccountingPeriod> findByYearMonthIn(@Param("yearMonths") Collection<String> yearMonths);

    List<AccountingPeriod> findByStatus(String status);
}
