package com.arafawood.api.service;

import com.arafawood.api.common.ApiException;
import com.arafawood.api.domain.DocSequence;
import com.arafawood.api.repo.DocSequenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SequenceService {

    private final DocSequenceRepository sequences;

    public SequenceService(DocSequenceRepository sequences) {
        this.sequences = sequences;
    }

    @Transactional
    public String next(String prefix) {
        DocSequence seq = sequences.lockByName(prefix)
                .orElseThrow(() -> ApiException.badRequest("تسلسل غير معروف: " + prefix));
        long next = seq.getLastValue() + 1;
        seq.setLastValue(next);
        sequences.save(seq);
        return prefix + "-" + String.format("%04d", next);
    }
}
