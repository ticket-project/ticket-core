/**
 * booking의 분산락 계약이다.
 *
 * <p>잠글 대상을 업무 의미로 표현하는 {@link com.ticket.booking.application.concurrency.LockScope}·{@link
 * com.ticket.booking.application.concurrency.LockKey}, 획득 조건인 {@link
 * com.ticket.booking.application.concurrency.LockOptions}, 그리고 이들을 받는 {@link
 * com.ticket.booking.application.concurrency.LockManager}가 한 묶음이다. {@code LockManager}만 {@code
 * port}로 떼면 계약 넷이 두 package로 갈라진다 — 셋은 값이고 하나는 추상이지만, 함께 바뀌고 함께 읽힌다.
 *
 * <p>여기 있는 타입은 저장 기술을 드러내지 않는다. key 형식과 임대 방식은 infrastructure가 정한다 — {@code
 * com.ticket.booking.BookingLayerDependencyTest}가 이 package를 대상으로 강제한다.
 */
@NullMarked
package com.ticket.booking.application.concurrency;

import org.jspecify.annotations.NullMarked;
