package com.connecthub.authservice.service;

import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.BitSet;
import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
/**
 * Provides a fast probabilistic pre-check for username uniqueness before the
 * service falls back to the database for definitive validation.
 */
public class UsernameBloomFilter {

    private static final int BITSET_SIZE = 1 << 20;
    private static final int HASH_FUNCTIONS = 7;
    private static final long SECOND_HASH_FALLBACK = 0x9E3779B97F4A7C15L;

    private final BitSet bitSet = new BitSet(BITSET_SIZE);
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public void clear() {
        lock.writeLock().lock();
        try {
            bitSet.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void seed(Collection<String> usernames) {
        lock.writeLock().lock();
        try {
            bitSet.clear();
            if (usernames == null) {
                return;
            }

            for (String username : usernames) {
                String normalized = normalize(username);
                if (normalized != null) {
                    addNormalized(normalized);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void add(String username) {
        String normalized = normalize(username);
        if (normalized == null) {
            return;
        }

        lock.writeLock().lock();
        try {
            addNormalized(normalized);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean mightContain(String username) {
        String normalized = normalize(username);
        if (normalized == null) {
            return false;
        }

        lock.readLock().lock();
        try {
            return mightContainNormalized(normalized);
        } finally {
            lock.readLock().unlock();
        }
    }

    private void addNormalized(String normalized) {
        if (normalized == null) {
            return;
        }

        for (int position : hashPositions(normalized)) {
            bitSet.set(position);
        }
    }

    private boolean mightContainNormalized(String normalized) {
        for (int position : hashPositions(normalized)) {
            if (!bitSet.get(position)) {
                return false;
            }
        }
        return true;
    }

    private String normalize(String username) {
        if (username == null) {
            return null;
        }

        String normalized = username.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private int[] hashPositions(String normalized) {
        byte[] digest = digest(normalized);
        long hash1 = toPositiveLong(digest, 0);
        long hash2 = toPositiveLong(digest, Long.BYTES);
        if (hash2 == 0L) {
            hash2 = SECOND_HASH_FALLBACK;
        }

        int[] positions = new int[HASH_FUNCTIONS];
        for (int i = 0; i < HASH_FUNCTIONS; i++) {
            long combined = hash1 + (long) i * hash2;
            positions[i] = (int) Math.floorMod(combined, BITSET_SIZE);
        }
        return positions;
    }

    private byte[] digest(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            return messageDigest.digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private long toPositiveLong(byte[] bytes, int offset) {
        return ByteBuffer.wrap(bytes, offset, Long.BYTES).getLong() & Long.MAX_VALUE;
    }
}
