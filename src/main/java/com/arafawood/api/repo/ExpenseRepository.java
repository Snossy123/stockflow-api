package com.arafawood.api.repo;

import com.arafawood.api.domain.Expense;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findAllByOrderByOccurredAtDesc();

    List<Expense> findByReversedOfId(Long reversedOfId);

    boolean existsByTypeId(Long typeId);
}
