package com.ticket.payment.internal.domain.payment.repository;

import com.ticket.payment.internal.domain.payment.model.Payment;

import java.util.List;
import java.util.Optional;

/**
 * Payment(결제 시도) aggregate의 저장과 복원을 담당하는 도메인 Repository다.
 *
 * <p>조회 실패는 {@link Optional}로 돌려주고, 그것을 어떤 오류로 볼지는 호출하는 유스케이스가
 * 고른다. 예외를 던지는 {@code getXxx}/{@code requireXxx} 편의 메서드는 두지 않는다.
 */
public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(Long id);

    Optional<Payment> findByPaymentKey(String paymentKey);

    List<Payment> findAllByOrderIdOrderByAttemptNoAsc(Long orderId);
}
