package com.arafawood.api.repo;

import com.arafawood.api.domain.Payment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByPartyTypeAndPartyIdOrderByOccurredAtDesc(String partyType, Long partyId);

    List<Payment> findAllByOrderByOccurredAtDesc();
}
