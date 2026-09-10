package com.ticket.payment.attempt.infrastructure;

import com.ticket.payment.attempt.domain.Payment;
import com.ticket.payment.attempt.domain.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@link PaymentRepository}의 JPA 구현이다.
 */
@Repository
@RequiredArgsConstructor
public class PaymentRepositoryAdapter implements PaymentRepository {

    private final SpringDataPaymentJpaRepository jpaRepository;

    @Override
    public Payment save(final Payment payment) {
        return jpaRepository.save(payment);
    }

    @Override
    public Optional<Payment> findById(final Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<Payment> findByPaymentKey(final String paymentKey) {
        return jpaRepository.findByPaymentKey(paymentKey);
    }

    @Override
    public List<Payment> findAllByOrderIdOrderByAttemptNoAsc(final Long orderId) {
        return jpaRepository.findAllByOrderIdOrderByAttemptNoAsc(orderId);
    }
}
