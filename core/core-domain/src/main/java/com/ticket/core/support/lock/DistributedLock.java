package com.ticket.core.support.lock;

import com.ticket.core.support.exception.ErrorType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    /**
     * 도메인 구분용 prefix
     */
    String prefix() default "";

    /**
     * 락 키를 생성할 SpEL 표현식들.
     *
     * 결과가 Collection/배열이면 각 요소가 개별 락으로 처리됩니다.
     *
     * 예시:
     * - 단일: "#memberId"
     * - 멀티: {"#a", "#b"} 또는 "#request.getIds()"
     */
    String[] dynamicKey();

    /**
     * 락의 시간 단위
     */
    TimeUnit timeUnit() default TimeUnit.MILLISECONDS;

    /**
     * 락을 기다리는 시간 (default - 5000ms)
     * 락 획득을 위해 waitTime 만큼 대기한다.
     */
    long waitTime() default 5000L;

    /**
     * 락 임대 시간. 기본값 -1은 Redisson watchdog으로 실행 중인 락을 자동 연장한다.
     * 양수로 지정하면 해당 시간이 지난 뒤 락을 자동 해제한다.
     */
    long leaseTime() default -1L;

    /**
     * 락 획득 실패 시 반환할 도메인 에러
     */
    ErrorType errorType() default ErrorType.HOLD_BUSY;

    /**
     * 락 획득 실패 시 사용할 추가 메시지
     */
    String message() default "";
}
