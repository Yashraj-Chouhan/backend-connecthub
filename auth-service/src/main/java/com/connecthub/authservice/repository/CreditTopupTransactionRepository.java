package com.connecthub.authservice.repository;

import com.connecthub.authservice.entity.CreditTopupTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CreditTopupTransactionRepository extends JpaRepository<CreditTopupTransaction, Long> {

    Optional<CreditTopupTransaction> findByOrderId(String orderId);
}
