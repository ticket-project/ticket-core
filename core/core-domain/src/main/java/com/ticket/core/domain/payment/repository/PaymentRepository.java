package com.ticket.core.domain.payment.repository;

import com.ticket.core.domain.payment.model.Payment;
import com.ticket.core.domain.payment.model.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentKey(String paymentKey);

    Optional<Payment> findFirstByOrderIdAndStatusOrderByIdDesc(Long orderId, PaymentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
            from Payment p
            where p.id = :paymentId
            """)
    Optional<Payment> findByIdForUpdate(@Param("paymentId") Long paymentId);
}
