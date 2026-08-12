package com.ticket.core.infra.metrics;

import com.ticket.core.infra.metrics.CoreBookingMetrics.HoldOperation;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class BookingMetricsAspect {

    private final CoreBookingMetrics metrics;

    @Around("execution(* com.ticket.core.domain.order.command.create.CreateOrderUseCase+.execute(..))")
    public Object recordOrderCreate(final ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            Object result = joinPoint.proceed();
            metrics.recordOrderCreate(true);
            return result;
        } catch (Throwable throwable) {
            metrics.recordOrderCreate(false);
            throw throwable;
        }
    }

    @Around("execution(* com.ticket.core.domain.hold.command.HoldManager+.createHold(..))")
    public Object recordHoldCreate(final ProceedingJoinPoint joinPoint) throws Throwable {
        return recordHold(joinPoint, HoldOperation.CREATE);
    }

    @Around("execution(* com.ticket.core.domain.hold.command.HoldManager+.release(..))")
    public Object recordHoldRelease(final ProceedingJoinPoint joinPoint) throws Throwable {
        return recordHold(joinPoint, HoldOperation.RELEASE);
    }

    @Around("execution(* com.ticket.core.domain.hold.command.HoldHistoryRecorder+.recordExpired(..))")
    public Object recordHoldExpire(final ProceedingJoinPoint joinPoint) throws Throwable {
        return recordHold(joinPoint, HoldOperation.EXPIRE);
    }

    private Object recordHold(
            final ProceedingJoinPoint joinPoint,
            final HoldOperation operation
    ) throws Throwable {
        try {
            Object result = joinPoint.proceed();
            metrics.recordHold(operation, true);
            return result;
        } catch (Throwable throwable) {
            metrics.recordHold(operation, false);
            throw throwable;
        }
    }
}
