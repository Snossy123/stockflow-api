package com.arafawood.api.repo;

import com.arafawood.api.domain.ExpenseType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseTypeRepository extends JpaRepository<ExpenseType, Long> {
    Optional<ExpenseType> findByName(String name);
}
