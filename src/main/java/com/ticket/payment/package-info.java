/**
 * Payment BC 자리다. 코드는 없고 {@code db/migration-vendor/{h2|oracle}/payment}의 {@code PAYMENTS} migration만 소유한다.
 *
 * <p>entity-only로 두었던 {@code Payment} entity·Repository는 호출자가 없어 제거했다(ADR 0005의 2026-10-07 갱신). 이 package는 Modulith가
 * {@code payment} module을 계속 찾아 그 migration을 {@code flyway_schema_history_payment}로 적용하게 하려고 남긴다. 실제 결제 연동을 시작할 때 이
 * module에 코드를 다시 둔다.
 */
@NullMarked
@org.springframework.modulith.ApplicationModule(
        displayName = "Payment",
        allowedDependencies = {})
package com.ticket.payment;

import org.jspecify.annotations.NullMarked;
