package com.arafawood.api.repo;

import com.arafawood.api.domain.DocSequence;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocSequenceRepository extends JpaRepository<DocSequence, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DocSequence s where s.name = :name")
    Optional<DocSequence> lockByName(@Param("name") String name);
}
