package com.connecthub.authservice.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsernameBloomFilterTest {

    @Test
    void addAndSeedUseNormalizedUsernames() {
        UsernameBloomFilter filter = new UsernameBloomFilter();

        assertFalse(filter.mightContain("connecthub"));

        filter.add("ConnectHub");
        assertTrue(filter.mightContain("connecthub"));
        assertTrue(filter.mightContain("  CONNECTHUB  "));

        filter.clear();
        assertFalse(filter.mightContain("connecthub"));

        filter.seed(List.of("Alpha", "Beta"));
        assertTrue(filter.mightContain("alpha"));
        assertTrue(filter.mightContain("beta"));
    }
}
