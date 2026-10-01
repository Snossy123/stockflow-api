package com.arafawood.api.repo;

import com.arafawood.api.domain.PurchaseOrder;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    List<PurchaseOrder> findBySupplierIdOrderByDocDateDesc(Long supplierId);

    List<PurchaseOrder> findByStatusAndPostingDate(String status, LocalDate postingDate);

    List<PurchaseOrder> findAllByOrderByDocDateDescIdDesc();
}
