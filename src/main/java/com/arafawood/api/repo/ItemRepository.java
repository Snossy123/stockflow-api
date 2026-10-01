package com.arafawood.api.repo;

import com.arafawood.api.domain.Item;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, Long> {
    Optional<Item> findByCode(String code);
}
