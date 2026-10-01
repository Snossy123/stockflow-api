package com.arafawood.api.repo;

import com.arafawood.api.domain.SalesOrder;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {
    List<SalesOrder> findByCustomerIdOrderByDocDateDesc(Long customerId);

    List<SalesOrder> findByStatusAndPostingDate(String status, LocalDate postingDate);

    List<SalesOrder> findAllByOrderByDocDateDescIdDesc();

    List<SalesOrder> findByReversedOfId(Long reversedOfId);
}
