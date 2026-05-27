package com.voicepay.payment.repository;

import com.voicepay.payment.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    List<Subscription> findByUserId(Long userId);
    List<Subscription> findByStatus(Subscription.SubscriptionStatus status);
    List<Subscription> findByStatusAndNextPaymentDateBefore(Subscription.SubscriptionStatus status, LocalDateTime dateTime);
}
