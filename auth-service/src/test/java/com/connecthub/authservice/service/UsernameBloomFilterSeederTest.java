package com.connecthub.authservice.service;

import com.connecthub.authservice.entity.User;
import com.connecthub.authservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsernameBloomFilterSeederTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UsernameBloomFilter usernameBloomFilter;

    @Test
    void seedFilterFromDatabase_SeedsFilterWithUsernames() {
        UsernameBloomFilterSeeder seeder = new UsernameBloomFilterSeeder(userRepository, usernameBloomFilter);
        User user1 = User.builder().username("user1").build();
        User user2 = User.builder().username("user2").build();
        when(userRepository.findAll()).thenReturn(List.of(user1, user2));

        seeder.seedFilterFromDatabase();

        verify(usernameBloomFilter).seed(List.of("user1", "user2"));
    }
}