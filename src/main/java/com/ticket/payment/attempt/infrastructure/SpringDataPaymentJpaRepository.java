package com.ticket.payment.attempt.infrastructure;

import com.ticket.payment.attempt.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface SpringDataPaymentJpaRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentKey(String paymentKey);

    List<Payment> findAllByOrderIdOrderByAttemptNoAsc(Long orderId);
}
