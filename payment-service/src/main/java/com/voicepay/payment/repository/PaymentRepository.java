package com.voicepay.payment.repository;

import com.voicepay.payment.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByUserId(Long userId);
    long countByStatus(Payment.PaymentStatus status);
    List<Payment> findTop10ByOrderByCreatedAtDesc();

    @Query("SELECT p FROM Payment p WHERE " +
           "(:userId IS NULL OR p.userId = :userId) AND " +
           "(:status IS NULL OR p.status = :status) AND " +
           "(cast(:startDate as timestamp) IS NULL OR p.createdAt >= :startDate) AND " +
           "(cast(:endDate as timestamp) IS NULL OR p.createdAt <= :endDate) " +
           "ORDER BY p.createdAt DESC")
    List<Payment> findFilteredPayments(
        @Param("userId") Long userId,
        @Param("status") Payment.PaymentStatus status,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
}
