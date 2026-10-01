package com.arafawood.api.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class BusinessDatesTest {

    @Test
    void intraDayUsesDocDate() {
        LocalDate today = BusinessDates.today();
        assertEquals(today, BusinessDates.resolvePostingDate(today));
    }

    @Test
    void oldDocDatePostsToday() {
        assertEquals(BusinessDates.today(), BusinessDates.resolvePostingDate(LocalDate.of(2020, 1, 1)));
    }
}
