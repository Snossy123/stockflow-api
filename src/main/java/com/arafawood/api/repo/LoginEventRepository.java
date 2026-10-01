package com.arafawood.api.repo;

import com.arafawood.api.domain.LoginEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginEventRepository extends JpaRepository<LoginEvent, Long> {
    List<LoginEvent> findAllByOrderByOccurredAtDesc();
}
