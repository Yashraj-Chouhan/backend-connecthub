package com.connecthub.authservice.service;

import com.connecthub.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UsernameBloomFilterSeeder {

    private final UserRepository userRepository;
    private final UsernameBloomFilter usernameBloomFilter;

    @EventListener(ApplicationReadyEvent.class)
    public void seedFilterFromDatabase() {
        usernameBloomFilter.seed(
                userRepository.findAll()
                        .stream()
                        .map(user -> user.getUsername())
                        .toList()
        );
    }
}
