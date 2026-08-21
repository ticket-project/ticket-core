# 결제 도메인 뼈대 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 결제 승인이 완료될 때 `PAYMENTS`, `ORDERS`, `PERFORMANCE_SEATS`, hold가 어떻게 변하는지를 실제 동작하는 코드로 확정한다. 실제 PG 연동은 하지 않고 가짜 게이트웨이가 즉시 응답한다.

**Architecture:** 결제 API는 준비(`READY` 행 생성)와 승인 2단계다. 게이트웨이 호출은 DB 트랜잭션 밖에서 하고, 승인 성공 시 짧은 트랜잭션 하나에서 결제·주문·좌석 상태를 전이시키고 hold 해제 outbox를 적재한다. 커밋 후 처리는 기존 `HoldReleaseAfterCommitListener` 경로를 그대로 재사용하며, outbox에 새로 추가하는 `reason` 컬럼으로 결제 확정일 때만 좌석 이벤트 발행을 건너뛴다.

**Tech Stack:** Java 17+, Spring Boot, Spring Data JPA, Flyway(h2/oracle 벤더 분리), JUnit 5 + Mockito + AssertJ, Lombok

**설계 문서:** `docs/superpowers/specs/2026-08-21-payment-skeleton-design.md`

**작업 브랜치:** `feat/payment-skeleton` (이미 생성됨, `origin/master` 기준)

---

## 사전 확인 (구현 시작 전 1회)

- [ ] **저장소 규칙 문서를 읽는다**

`AGENTS.md`, `docs/development.md`(커밋 컨벤션), `docs/testing.md`(무엇을 돌릴지), `docs/architecture.md`(아키텍처 규칙)를 읽는다. 특히 아래 규칙이 이 작업에서 실제로 걸린다.

- `core-domain`은 Redis/Redisson/`spring-messaging`/HTTP 클라이언트 애노테이션에 직접 의존할 수 없다. 가짜 게이트웨이 구현체는 반드시 `core-infra`에 둔다.
- `core-domain`의 메서드에는 `@Scheduled`, `@TransactionalEventListener`를 붙일 수 없다.
- 테스트 클래스에 `@SuppressWarnings("NonAsciiCharacters")`를 붙이고 메서드 이름을 한국어로 쓴다. `@DisplayName`, `@Nested`는 쓰지 않는다.
- 커밋 메시지는 Conventional Commits + 한국어 제목이다.

- [ ] **현재 상태에서 기준 테스트가 통과하는지 확인한다**

Run: `.\gradlew.bat :core:core-domain:test`
Expected: BUILD SUCCESSFUL. 실패가 있으면 이번 변경 때문이 아니므로 먼저 보고한다.

---

## File Structure

**새로 만드는 파일**

| 경로 | 책임 |
| --- | --- |
| `core/core-domain/.../domain/payment/model/Payment.java` | 결제 엔티티, `READY -> APPROVED/FAILED` 전이 |
| `.../domain/payment/model/PaymentStatus.java` | 결제 상태 enum |
| `.../domain/payment/model/PaymentMethod.java` | 결제수단 enum (`CARD`) |
| `.../domain/payment/repository/PaymentRepository.java` | 결제 조회/잠금 |
| `.../domain/payment/support/PaymentKeyGenerator.java` | `paymentKey` 발급 |
| `.../domain/payment/gateway/PaymentGatewayClient.java` | 승인 요청 port |
| `.../domain/payment/gateway/PaymentApprovalCommand.java` | 승인 요청 값 |
| `.../domain/payment/gateway/PaymentApprovalResult.java` | 승인 결과 값 |
| `.../domain/payment/command/PreparePaymentUseCase.java` | 준비 단계 (READY 생성/재사용) |
| `.../domain/payment/command/ConfirmPaymentUseCase.java` | 승인 단계 오케스트레이션 (트랜잭션 밖) |
| `.../domain/payment/command/PaymentConfirmationTxService.java` | 승인 단계의 DB 트랜잭션 경계 |
| `.../domain/order/command/OrderConfirmationService.java` | 확정 시 주문·좌석·이력·outbox 처리 (`OrderTerminationService`의 대칭) |
| `core/core-infra/.../infra/payment/FakePaymentGatewayClient.java` | 가짜 게이트웨이 어댑터 |
| `core/core-api/.../api/controller/PaymentController.java` | 결제 API 2개 |
| `core/core-api/.../api/controller/docs/PaymentControllerDocs.java` | Swagger 문서 인터페이스 |
| `core/core-api/.../api/controller/request/PreparePaymentRequest.java` | 준비 요청 DTO |
| `core/core-api/.../api/controller/request/ConfirmPaymentRequest.java` | 승인 요청 DTO |
| `core/core-api/src/main/resources/db/migration-vendor/{h2,oracle}/V8__create_payments.sql` | `PAYMENTS` 생성 |
| `core/core-api/src/main/resources/db/migration-vendor/{h2,oracle}/V9__add_reason_to_order_hold_release_outbox.sql` | outbox `reason` 추가 |

**수정하는 파일**

| 경로 | 변경 |
| --- | --- |
| `.../support/exception/ErrorCode.java`, `ErrorType.java` | 결제 관련 항목 추가 |
| `.../domain/hold/model/HoldHistory.java` | `confirmed` 팩토리 추가 |
| `.../domain/hold/command/HoldHistoryRecorder.java` | `recordConfirmed` 추가 |
| `.../order/command/release/HoldReleaseOutbox.java` | `reason` 필드 추가 |
| `.../order/command/release/HoldReleaseTask.java` | `reason` 추가 |
| `.../order/command/release/HoldReleaseOutboxWriter.java` | `append(result, reason)` |
| `.../order/command/release/HoldReleaseOutboxTransactionService.java` | `load`가 `reason` 전달 |
| `.../order/command/release/HoldReleaseTaskProcessor.java` | 결제 확정이면 좌석 이벤트 발행 생략 |
| `.../order/command/OrderTerminationService.java` | 취소/만료 이유 전달 |
| `docs/core-booking-lifecycle.md` | 결제 확정 절 추가 |

---

## Task 1: PAYMENTS 엔티티와 마이그레이션

**Files:**
- Create: `core/core-domain/src/main/java/com/ticket/core/domain/payment/model/PaymentStatus.java`
- Create: `core/core-domain/src/main/java/com/ticket/core/domain/payment/model/PaymentMethod.java`
- Create: `core/core-domain/src/main/java/com/ticket/core/domain/payment/model/Payment.java`
- Create: `core/core-domain/src/main/java/com/ticket/core/domain/payment/repository/PaymentRepository.java`
- Create: `core/core-api/src/main/resources/db/migration-vendor/h2/V8__create_payments.sql`
- Create: `core/core-api/src/main/resources/db/migration-vendor/oracle/V8__create_payments.sql`
- Test: `core/core-domain/src/test/java/com/ticket/core/domain/payment/model/PaymentTest.java`

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`core/core-domain/src/test/java/com/ticket/core/domain/payment/model/PaymentTest.java`

```java
package com.ticket.core.domain.payment.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("NonAsciiCharacters")
class PaymentTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 8, 21, 10, 0);

    @Test
    void 생성하면_READY_상태가_된다() {
        final Payment payment = payment();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(payment.getPaymentKey()).isEqualTo("PAY-1");
        assertThat(payment.getOrderId()).isEqualTo(10L);
        assertThat(payment.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(120000));
        assertThat(payment.getApprovedAt()).isNull();
    }

    @Test
    void 승인하면_APPROVED로_전이하고_승인정보를_남긴다() {
        final Payment payment = payment();

        payment.approve(FIXED_NOW, "FAKEPG-1");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getApprovedAt()).isEqualTo(FIXED_NOW);
        assertThat(payment.getPgTransactionId()).isEqualTo("FAKEPG-1");
        assertThat(payment.isApproved()).isTrue();
    }

    @Test
    void 거절하면_FAILED로_전이하고_실패사유를_남긴다() {
        final Payment payment = payment();

        payment.fail(FIXED_NOW, "FAKE_DECLINED", "가짜 게이트웨이가 승인을 거절했습니다.");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailedAt()).isEqualTo(FIXED_NOW);
        assertThat(payment.getFailureCode()).isEqualTo("FAKE_DECLINED");
        assertThat(payment.getFailureMessage()).isEqualTo("가짜 게이트웨이가 승인을 거절했습니다.");
    }

    @Test
    void 이미_승인된_결제는_다시_승인할_수_없다() {
        final Payment payment = payment();
        payment.approve(FIXED_NOW, "FAKEPG-1");

        assertThatThrownBy(() -> payment.approve(FIXED_NOW, "FAKEPG-2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("READY");
    }

    @Test
    void 실패한_결제는_승인할_수_없다() {
        final Payment payment = payment();
        payment.fail(FIXED_NOW, "FAKE_DECLINED", "거절");

        assertThatThrownBy(() -> payment.approve(FIXED_NOW, "FAKEPG-1"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 실패메시지가_200자를_넘으면_잘라서_저장한다() {
        final Payment payment = payment();

        payment.fail(FIXED_NOW, "FAKE_DECLINED", "가".repeat(300));

        assertThat(payment.getFailureMessage()).hasSize(200);
    }

    private Payment payment() {
        return new Payment("PAY-1", 10L, PaymentMethod.CARD, BigDecimal.valueOf(120000));
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

Run: `.\gradlew.bat :core:core-domain:test --tests "com.ticket.core.domain.payment.model.PaymentTest"`
Expected: 컴파일 실패 — `package com.ticket.core.domain.payment.model does not exist`

- [ ] **Step 3: enum 두 개를 만든다**

`core/core-domain/src/main/java/com/ticket/core/domain/payment/model/PaymentStatus.java`

```java
package com.ticket.core.domain.payment.model;

public enum PaymentStatus {
    READY("결제 준비"),
    APPROVED("결제 완료"),
    FAILED("결제 실패");

    private final String description;

    PaymentStatus(final String description) {
        this.description = description;
    }

    public String getCode() {
        return name();
    }

    public String getDescription() {
        return description;
    }
}
```

`core/core-domain/src/main/java/com/ticket/core/domain/payment/model/PaymentMethod.java`

```java
package com.ticket.core.domain.payment.model;

public enum PaymentMethod {
    CARD("카드");

    private final String description;

    PaymentMethod(final String description) {
        this.description = description;
    }

    public String getCode() {
        return name();
    }

    public String getDescription() {
        return description;
    }
}
```

- [ ] **Step 4: 엔티티를 만든다**

`core/core-domain/src/main/java/com/ticket/core/domain/payment/model/Payment.java`

```java
package com.ticket.core.domain.payment.model;

import com.ticket.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "PAYMENTS",
        indexes = {
                @Index(name = "IDX_PAYMENTS_ORDER_ID", columnList = "order_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    private static final int MAX_FAILURE_MESSAGE_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String paymentKey;

    @Column(nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(length = 64)
    private String pgTransactionId;

    private LocalDateTime approvedAt;

    private LocalDateTime failedAt;

    @Column(length = 40)
    private String failureCode;

    @Column(length = MAX_FAILURE_MESSAGE_LENGTH)
    private String failureMessage;

    public Payment(
            final String paymentKey,
            final Long orderId,
            final PaymentMethod method,
            final BigDecimal amount
    ) {
        this.paymentKey = paymentKey;
        this.orderId = orderId;
        this.method = method;
        this.amount = amount;
        this.status = PaymentStatus.READY;
    }

    public void approve(final LocalDateTime now, final String pgTransactionId) {
        validateReadyTransition("approve");
        this.status = PaymentStatus.APPROVED;
        this.approvedAt = now;
        this.pgTransactionId = pgTransactionId;
    }

    public void fail(final LocalDateTime now, final String failureCode, final String failureMessage) {
        validateReadyTransition("fail");
        this.status = PaymentStatus.FAILED;
        this.failedAt = now;
        this.failureCode = failureCode;
        this.failureMessage = truncate(failureMessage);
    }

    public boolean isReady() {
        return status == PaymentStatus.READY;
    }

    public boolean isApproved() {
        return status == PaymentStatus.APPROVED;
    }

    private void validateReadyTransition(final String action) {
        if (!isReady()) {
            throw new IllegalStateException("READY 결제만 " + action + " 할 수 있습니다. currentStatus=" + status);
        }
    }

    private static String truncate(final String failureMessage) {
        if (failureMessage == null || failureMessage.length() <= MAX_FAILURE_MESSAGE_LENGTH) {
            return failureMessage;
        }
        return failureMessage.substring(0, MAX_FAILURE_MESSAGE_LENGTH);
    }
}
```

- [ ] **Step 5: 테스트가 통과하는지 확인한다**

Run: `.\gradlew.bat :core:core-domain:test --tests "com.ticket.core.domain.payment.model.PaymentTest"`
Expected: PASS (6 tests)

- [ ] **Step 6: repository를 만든다**

`core/core-domain/src/main/java/com/ticket/core/domain/payment/repository/PaymentRepository.java`

```java
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
```

- [ ] **Step 7: Flyway 마이그레이션을 만든다**

`core/core-api/src/main/resources/db/migration-vendor/h2/V8__create_payments.sql`

```sql
CREATE TABLE PAYMENTS (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    payment_key VARCHAR(40) NOT NULL,
    order_id BIGINT NOT NULL,
    method VARCHAR(20) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    pg_transaction_id VARCHAR(64),
    approved_at TIMESTAMP,
    failed_at TIMESTAMP,
    failure_code VARCHAR(40),
    failure_message VARCHAR(200),
    created_at TIMESTAMP NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255)
);

CREATE UNIQUE INDEX UK_PAYMENTS_PAYMENT_KEY ON PAYMENTS (payment_key);

CREATE INDEX IDX_PAYMENTS_ORDER_ID ON PAYMENTS (order_id);
```

`core/core-api/src/main/resources/db/migration-vendor/oracle/V8__create_payments.sql`

```sql
CREATE TABLE PAYMENTS (
    id NUMBER(19, 0) GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    payment_key VARCHAR2(40) NOT NULL,
    order_id NUMBER(19, 0) NOT NULL,
    method VARCHAR2(20) NOT NULL,
    amount NUMBER(19, 2) NOT NULL,
    status VARCHAR2(20) NOT NULL,
    pg_transaction_id VARCHAR2(64),
    approved_at TIMESTAMP,
    failed_at TIMESTAMP,
    failure_code VARCHAR2(40),
    failure_message VARCHAR2(200),
    created_at TIMESTAMP NOT NULL,
    created_by VARCHAR2(255) NOT NULL,
    updated_at TIMESTAMP,
    updated_by VARCHAR2(255)
);

CREATE UNIQUE INDEX UK_PAYMENTS_PAYMENT_KEY ON PAYMENTS (payment_key);

CREATE INDEX IDX_PAYMENTS_ORDER_ID ON PAYMENTS (order_id);
```

- [ ] **Step 8: 마이그레이션 테스트를 돌린다**

`CoreQueryIndexMigrationTest`는 합성 베이스라인 스키마에 `classpath:db/migration-vendor/h2`의 모든 스크립트를 적용한다. 새 `V8`이 그 환경에서 깨지지 않아야 한다.

Run: `.\gradlew.bat :core:core-api:test --tests "com.ticket.core.migration.CoreQueryIndexMigrationTest"`
Expected: PASS (2 tests)

- [ ] **Step 9: 커밋한다**

```bash
git add core/core-domain/src/main/java/com/ticket/core/domain/payment core/core-domain/src/test/java/com/ticket/core/domain/payment core/core-api/src/main/resources/db/migration-vendor/h2/V8__create_payments.sql core/core-api/src/main/resources/db/migration-vendor/oracle/V8__create_payments.sql
git commit -m "feat(payment): 결제 엔티티와 PAYMENTS 스키마 추가"
```

---

## Task 2: 결제 오류 타입과 paymentKey 발급기

**Files:**
- Modify: `core/core-domain/src/main/java/com/ticket/core/support/exception/ErrorCode.java`
- Modify: `core/core-domain/src/main/java/com/ticket/core/support/exception/ErrorType.java`
- Create: `core/core-domain/src/main/java/com/ticket/core/domain/payment/support/PaymentKeyGenerator.java`
- Test: `core/core-domain/src/test/java/com/ticket/core/domain/payment/support/PaymentKeyGeneratorTest.java`

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`core/core-domain/src/test/java/com/ticket/core/domain/payment/support/PaymentKeyGeneratorTest.java`

```java
package com.ticket.core.domain.payment.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NonAsciiCharacters")
class PaymentKeyGeneratorTest {

    @Test
    void 발급한_키는_접두어를_가지고_매번_다르다() {
        final PaymentKeyGenerator generator = new PaymentKeyGenerator();

        final String first = generator.generate();
        final String second = generator.generate();

        assertThat(first).startsWith("PAY-");
        assertThat(first).hasSize(36);
        assertThat(first).isNotEqualTo(second);
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

Run: `.\gradlew.bat :core:core-domain:test --tests "com.ticket.core.domain.payment.support.PaymentKeyGeneratorTest"`
Expected: 컴파일 실패 — `PaymentKeyGenerator` 없음

- [ ] **Step 3: 발급기를 만든다**

`HoldKeyGenerator`와 같은 방식이다. `"PAY-"`(4자) + UUID 32자 = 36자로 `payment_key VARCHAR(40)`에 들어간다.

`core/core-domain/src/main/java/com/ticket/core/domain/payment/support/PaymentKeyGenerator.java`

```java
package com.ticket.core.domain.payment.support;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PaymentKeyGenerator {

    public String generate() {
        return "PAY-" + UUID.randomUUID().toString().replace("-", "");
    }
}
```

- [ ] **Step 4: 테스트 통과를 확인한다**

Run: `.\gradlew.bat :core:core-domain:test --tests "com.ticket.core.domain.payment.support.PaymentKeyGeneratorTest"`
Expected: PASS (1 test)

- [ ] **Step 5: 오류 코드를 추가한다**

`ErrorCode.java`의 `//좌석` 블록(`E4002`) 뒤에 한 줄을 추가한다.

```java
    E4003("이미 예매 완료된 좌석"),
```

`ErrorCode.java`의 마지막 블록(`E8002`) 뒤, `;` 앞에 결제 블록을 추가한다.

```java
    //결제
    E9000("결제 금액 불일치"),
    E9001("결제 정보 없음"),
    E9002("준비 상태 결제 아님"),
    E9003("결제 거절"),
```

`ErrorType.java`의 `//좌석` 블록 마지막(`SEAT_NOT_OWNED`) 뒤에 추가한다.

```java
    SEAT_ALREADY_RESERVED(HttpStatus.CONFLICT, ErrorCode.E4003, "이미 예매 완료된 좌석입니다."),
```

`ErrorType.java`의 `ADMISSION_TOKEN_INVALID` 뒤, `;` 앞에 추가한다.

```java
    //결제
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, ErrorCode.E9000, "결제 금액이 주문 금액과 일치하지 않습니다."),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCode.E9001, "결제 정보를 찾을 수 없습니다."),
    PAYMENT_NOT_READY(HttpStatus.CONFLICT, ErrorCode.E9002, "준비 상태의 결제만 승인할 수 있습니다."),
    PAYMENT_DECLINED(HttpStatus.CONFLICT, ErrorCode.E9003, "결제가 거절되었습니다."),
```

- [ ] **Step 6: 컴파일을 확인한다**

Run: `.\gradlew.bat :core:core-domain:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: 커밋한다**

```bash
git add core/core-domain/src/main/java/com/ticket/core/support/exception/ErrorCode.java core/core-domain/src/main/java/com/ticket/core/support/exception/ErrorType.java core/core-domain/src/main/java/com/ticket/core/domain/payment/support core/core-domain/src/test/java/com/ticket/core/domain/payment/support
git commit -m "feat(payment): 결제 오류 타입과 paymentKey 발급기 추가"
```

---

## Task 3: 가짜 게이트웨이 port와 adapter

**Files:**
- Create: `core/core-domain/src/main/java/com/ticket/core/domain/payment/gateway/PaymentGatewayClient.java`
- Create: `core/core-domain/src/main/java/com/ticket/core/domain/payment/gateway/PaymentApprovalCommand.java`
- Create: `core/core-domain/src/main/java/com/ticket/core/domain/payment/gateway/PaymentApprovalResult.java`
- Create: `core/core-infra/src/main/java/com/ticket/core/infra/payment/FakePaymentGatewayClient.java`
- Test: `core/core-infra/src/test/java/com/ticket/core/infra/payment/FakePaymentGatewayClientTest.java`

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`core/core-infra/src/test/java/com/ticket/core/infra/payment/FakePaymentGatewayClientTest.java`

```java
package com.ticket.core.infra.payment;

import com.ticket.core.domain.payment.gateway.PaymentApprovalCommand;
import com.ticket.core.domain.payment.gateway.PaymentApprovalResult;
import com.ticket.core.domain.payment.model.PaymentMethod;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NonAsciiCharacters")
class FakePaymentGatewayClientTest {

    private static final PaymentApprovalCommand COMMAND = new PaymentApprovalCommand(
            "PAY-1", 10L, BigDecimal.valueOf(120000), PaymentMethod.CARD
    );

    @Test
    void 기본_설정이면_승인하고_승인번호를_발급한다() {
        final PaymentApprovalResult result = new FakePaymentGatewayClient(true).approve(COMMAND);

        assertThat(result.approved()).isTrue();
        assertThat(result.pgTransactionId()).startsWith("FAKEPG-");
        assertThat(result.failureCode()).isNull();
    }

    @Test
    void 거절_설정이면_실패사유를_담아_거절한다() {
        final PaymentApprovalResult result = new FakePaymentGatewayClient(false).approve(COMMAND);

        assertThat(result.approved()).isFalse();
        assertThat(result.pgTransactionId()).isNull();
        assertThat(result.failureCode()).isEqualTo("FAKE_DECLINED");
        assertThat(result.failureMessage()).isNotBlank();
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

Run: `.\gradlew.bat :core:core-infra:test --tests "com.ticket.core.infra.payment.FakePaymentGatewayClientTest"`
Expected: 컴파일 실패 — `FakePaymentGatewayClient` 없음

- [ ] **Step 3: port를 만든다**

`core/core-domain/src/main/java/com/ticket/core/domain/payment/gateway/PaymentApprovalCommand.java`

```java
package com.ticket.core.domain.payment.gateway;

import com.ticket.core.domain.payment.model.PaymentMethod;

import java.math.BigDecimal;

public record PaymentApprovalCommand(
        String paymentKey,
        Long orderId,
        BigDecimal amount,
        PaymentMethod method
) {
}
```

`core/core-domain/src/main/java/com/ticket/core/domain/payment/gateway/PaymentApprovalResult.java`

```java
package com.ticket.core.domain.payment.gateway;

public record PaymentApprovalResult(
        boolean approved,
        String pgTransactionId,
        String failureCode,
        String failureMessage
) {

    public static PaymentApprovalResult approved(final String pgTransactionId) {
        return new PaymentApprovalResult(true, pgTransactionId, null, null);
    }

    public static PaymentApprovalResult declined(final String failureCode, final String failureMessage) {
        return new PaymentApprovalResult(false, null, failureCode, failureMessage);
    }
}
```

`core/core-domain/src/main/java/com/ticket/core/domain/payment/gateway/PaymentGatewayClient.java`

```java
package com.ticket.core.domain.payment.gateway;

public interface PaymentGatewayClient {

    PaymentApprovalResult approve(PaymentApprovalCommand command);
}
```

- [ ] **Step 4: adapter를 만든다**

`core-domain`은 외부 통신 구현을 가질 수 없으므로 구현체는 `core-infra`에 둔다.

`core/core-infra/src/main/java/com/ticket/core/infra/payment/FakePaymentGatewayClient.java`

```java
package com.ticket.core.infra.payment;

import com.ticket.core.domain.payment.gateway.PaymentApprovalCommand;
import com.ticket.core.domain.payment.gateway.PaymentApprovalResult;
import com.ticket.core.domain.payment.gateway.PaymentGatewayClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class FakePaymentGatewayClient implements PaymentGatewayClient {

    private final boolean approveAll;

    public FakePaymentGatewayClient(
            @Value("${ticket.payment.fake.approve:true}") final boolean approveAll
    ) {
        this.approveAll = approveAll;
    }

    @Override
    public PaymentApprovalResult approve(final PaymentApprovalCommand command) {
        if (!approveAll) {
            return PaymentApprovalResult.declined("FAKE_DECLINED", "가짜 게이트웨이가 승인을 거절했습니다.");
        }
        return PaymentApprovalResult.approved("FAKEPG-" + UUID.randomUUID().toString().replace("-", ""));
    }
}
```

- [ ] **Step 5: 테스트 통과를 확인한다**

Run: `.\gradlew.bat :core:core-infra:test --tests "com.ticket.core.infra.payment.FakePaymentGatewayClientTest"`
Expected: PASS (2 tests)

- [ ] **Step 6: 커밋한다**

```bash
git add core/core-domain/src/main/java/com/ticket/core/domain/payment/gateway core/core-infra/src/main/java/com/ticket/core/infra/payment core/core-infra/src/test/java/com/ticket/core/infra/payment
git commit -m "feat(payment): 결제 승인 port와 가짜 게이트웨이 구현 추가"
```

---

## Task 4: 준비 단계 use case

**Files:**
- Create: `core/core-domain/src/main/java/com/ticket/core/domain/payment/command/PreparePaymentUseCase.java`
- Test: `core/core-domain/src/test/java/com/ticket/core/domain/payment/command/PreparePaymentUseCaseTest.java`

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`core/core-domain/src/test/java/com/ticket/core/domain/payment/command/PreparePaymentUseCaseTest.java`

```java
package com.ticket.core.domain.payment.command;

import com.ticket.core.domain.member.query.MemberFinder;
import com.ticket.core.domain.order.model.Order;
import com.ticket.core.domain.order.repository.OrderRepository;
import com.ticket.core.domain.payment.model.Payment;
import com.ticket.core.domain.payment.model.PaymentMethod;
import com.ticket.core.domain.payment.model.PaymentStatus;
import com.ticket.core.domain.payment.repository.PaymentRepository;
import com.ticket.core.domain.payment.support.PaymentKeyGenerator;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class PreparePaymentUseCaseTest {

    private static final BigDecimal TOTAL_AMOUNT = BigDecimal.valueOf(120000);
    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 8, 21, 10, 0);

    @Mock
    private MemberFinder memberFinder;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentKeyGenerator paymentKeyGenerator;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-08-21T01:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Test
    void 준비_요청이면_READY_결제를_생성한다() {
        final Order order = order(FIXED_NOW.plusMinutes(5));
        when(orderRepository.findByOrderKeyAndMemberId("order-key", 1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findFirstByOrderIdAndStatusOrderByIdDesc(10L, PaymentStatus.READY))
                .thenReturn(Optional.empty());
        when(paymentKeyGenerator.generate()).thenReturn("PAY-1");
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final PreparePaymentUseCase.Output output = useCase().execute(input());

        assertThat(output.paymentKey()).isEqualTo("PAY-1");
        assertThat(output.orderKey()).isEqualTo("order-key");
        assertThat(output.status()).isEqualTo(PaymentStatus.READY);
        assertThat(output.amount()).isEqualByComparingTo(TOTAL_AMOUNT);
        assertThat(output.expiresAt()).isEqualTo(FIXED_NOW.plusMinutes(5));
        verify(memberFinder).findActiveMemberById(1L);
    }

    @Test
    void 이미_READY_결제가_있으면_그것을_반환한다() {
        final Order order = order(FIXED_NOW.plusMinutes(5));
        final Payment existing = new Payment("PAY-EXISTING", 10L, PaymentMethod.CARD, TOTAL_AMOUNT);
        when(orderRepository.findByOrderKeyAndMemberId("order-key", 1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findFirstByOrderIdAndStatusOrderByIdDesc(10L, PaymentStatus.READY))
                .thenReturn(Optional.of(existing));

        final PreparePaymentUseCase.Output output = useCase().execute(input());

        assertThat(output.paymentKey()).isEqualTo("PAY-EXISTING");
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void 본인_주문이_아니면_예외를_던진다() {
        when(orderRepository.findByOrderKeyAndMemberId("order-key", 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().execute(input()))
                .isInstanceOf(CoreException.class)
                .satisfies(error -> assertThat(((CoreException) error).getErrorType())
                        .isEqualTo(ErrorType.ORDER_NOT_OWNED));
    }

    @Test
    void PENDING_주문이_아니면_예외를_던진다() {
        final Order order = order(FIXED_NOW.plusMinutes(5));
        order.cancel(FIXED_NOW);
        when(orderRepository.findByOrderKeyAndMemberId("order-key", 1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> useCase().execute(input()))
                .isInstanceOf(CoreException.class)
                .satisfies(error -> assertThat(((CoreException) error).getErrorType())
                        .isEqualTo(ErrorType.ORDER_NOT_PENDING));
    }

    @Test
    void 만료_시각을_지난_주문이면_예외를_던진다() {
        final Order order = order(FIXED_NOW.minusSeconds(1));
        when(orderRepository.findByOrderKeyAndMemberId("order-key", 1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> useCase().execute(input()))
                .isInstanceOf(CoreException.class)
                .satisfies(error -> assertThat(((CoreException) error).getErrorType())
                        .isEqualTo(ErrorType.ORDER_HOLD_EXPIRED));
    }

    @Test
    void 요청_금액이_주문_금액과_다르면_예외를_던진다() {
        final Order order = order(FIXED_NOW.plusMinutes(5));
        when(orderRepository.findByOrderKeyAndMemberId("order-key", 1L)).thenReturn(Optional.of(order));

        final PreparePaymentUseCase.Input wrongAmount = new PreparePaymentUseCase.Input(
                "order-key", 1L, PaymentMethod.CARD, BigDecimal.valueOf(1000)
        );

        assertThatThrownBy(() -> useCase().execute(wrongAmount))
                .isInstanceOf(CoreException.class)
                .satisfies(error -> assertThat(((CoreException) error).getErrorType())
                        .isEqualTo(ErrorType.PAYMENT_AMOUNT_MISMATCH));
    }

    private PreparePaymentUseCase useCase() {
        return new PreparePaymentUseCase(
                memberFinder,
                orderRepository,
                paymentRepository,
                paymentKeyGenerator,
                fixedClock
        );
    }

    private PreparePaymentUseCase.Input input() {
        return new PreparePaymentUseCase.Input("order-key", 1L, PaymentMethod.CARD, TOTAL_AMOUNT);
    }

    private Order order(final LocalDateTime expiresAt) {
        final Order order = new Order(1L, 100L, "order-key", "hold-key", TOTAL_AMOUNT, expiresAt);
        ReflectionTestUtils.setField(order, "id", 10L);
        return order;
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

Run: `.\gradlew.bat :core:core-domain:test --tests "com.ticket.core.domain.payment.command.PreparePaymentUseCaseTest"`
Expected: 컴파일 실패 — `PreparePaymentUseCase` 없음

- [ ] **Step 3: use case를 만든다**

`core/core-domain/src/main/java/com/ticket/core/domain/payment/command/PreparePaymentUseCase.java`

```java
package com.ticket.core.domain.payment.command;

import com.ticket.core.domain.member.query.MemberFinder;
import com.ticket.core.domain.order.model.Order;
import com.ticket.core.domain.order.repository.OrderRepository;
import com.ticket.core.domain.payment.model.Payment;
import com.ticket.core.domain.payment.model.PaymentMethod;
import com.ticket.core.domain.payment.model.PaymentStatus;
import com.ticket.core.domain.payment.repository.PaymentRepository;
import com.ticket.core.domain.payment.support.PaymentKeyGenerator;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PreparePaymentUseCase {

    private final MemberFinder memberFinder;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentKeyGenerator paymentKeyGenerator;
    private final Clock clock;

    public record Input(String orderKey, Long memberId, PaymentMethod method, BigDecimal amount) {}

    public record Output(
            String paymentKey,
            String orderKey,
            BigDecimal amount,
            PaymentStatus status,
            LocalDateTime expiresAt
    ) {}

    @Transactional
    public Output execute(final Input input) {
        memberFinder.findActiveMemberById(input.memberId());
        final Order order = getPayableOrder(input);
        final Payment payment = paymentRepository
                .findFirstByOrderIdAndStatusOrderByIdDesc(order.getId(), PaymentStatus.READY)
                .orElseGet(() -> paymentRepository.save(new Payment(
                        paymentKeyGenerator.generate(),
                        order.getId(),
                        input.method(),
                        input.amount()
                )));
        return new Output(
                payment.getPaymentKey(),
                order.getOrderKey(),
                payment.getAmount(),
                payment.getStatus(),
                order.getExpiresAt()
        );
    }

    private Order getPayableOrder(final Input input) {
        final Order order = orderRepository.findByOrderKeyAndMemberId(input.orderKey(), input.memberId())
                .orElseThrow(() -> new CoreException(ErrorType.ORDER_NOT_OWNED));
        if (!order.isPending()) {
            throw new CoreException(ErrorType.ORDER_NOT_PENDING);
        }
        if (order.isExpired(LocalDateTime.now(clock))) {
            throw new CoreException(ErrorType.ORDER_HOLD_EXPIRED);
        }
        if (order.getTotalAmount().compareTo(input.amount()) != 0) {
            throw new CoreException(ErrorType.PAYMENT_AMOUNT_MISMATCH);
        }
        return order;
    }
}
```

- [ ] **Step 4: 테스트 통과를 확인한다**

Run: `.\gradlew.bat :core:core-domain:test --tests "com.ticket.core.domain.payment.command.PreparePaymentUseCaseTest"`
Expected: PASS (6 tests)

- [ ] **Step 5: 커밋한다**

```bash
git add core/core-domain/src/main/java/com/ticket/core/domain/payment/command core/core-domain/src/test/java/com/ticket/core/domain/payment/command
git commit -m "feat(payment): 결제 준비 use case 추가"
```

---

## Task 5: 해제 outbox에 해제 이유를 기록한다

**Files:**
- Modify: `core/core-domain/src/main/java/com/ticket/core/domain/order/command/release/HoldReleaseOutbox.java`
- Modify: `core/core-domain/src/main/java/com/ticket/core/domain/order/command/release/HoldReleaseTask.java`
- Modify: `core/core-domain/src/main/java/com/ticket/core/domain/order/command/release/HoldReleaseOutboxWriter.java`
- Modify: `core/core-domain/src/main/java/com/ticket/core/domain/order/command/release/HoldReleaseOutboxTransactionService.java`
- Modify: `core/core-domain/src/main/java/com/ticket/core/domain/order/command/OrderTerminationService.java`
- Create: `core/core-api/src/main/resources/db/migration-vendor/h2/V9__add_reason_to_order_hold_release_outbox.sql`
- Create: `core/core-api/src/main/resources/db/migration-vendor/oracle/V9__add_reason_to_order_hold_release_outbox.sql`
- Test: `core/core-domain/src/test/java/com/ticket/core/domain/order/command/release/HoldReleaseOutboxWriterTest.java` (수정)
- Test: `core/core-domain/src/test/java/com/ticket/core/domain/order/command/OrderTerminationServiceTest.java` (수정)

이 태스크는 저장까지만 바꾼다. 발행 분기는 Task 6에서 한다.

- [ ] **Step 1: 기존 Writer 테스트를 새 시그니처로 바꾼다**

`HoldReleaseOutboxWriterTest.java`의 `append_uses_clock_now_as_next_attempt_at`를 아래로 교체하고 두 번째 테스트를 추가한다. import에 `com.ticket.core.domain.hold.model.HoldReleaseReason`를 더한다.

```java
    @Test
    void append_uses_clock_now_as_next_attempt_at() {
        OrderTerminationResult result = new OrderTerminationResult(1L, "hold-key", List.of(10L, 20L));
        LocalDateTime expectedNow = LocalDateTime.of(2026, 3, 15, 10, 0);
        HoldReleaseOutbox saved = HoldReleaseOutbox.create(
                1L, "hold-key", List.of(10L, 20L), expectedNow, HoldReleaseReason.USER_CANCELED
        );
        ReflectionTestUtils.setField(saved, "id", 99L);
        when(holdReleaseOutboxRepository.save(argThat(outbox ->
                outbox.getPerformanceId().equals(1L)
                        && outbox.getHoldKey().equals("hold-key")
                        && outbox.seatIds().equals(List.of(10L, 20L))
                        && outbox.getNextAttemptAt().equals(expectedNow)
                        && outbox.getReason() == HoldReleaseReason.USER_CANCELED
        ))).thenReturn(saved);

        Long outboxId = writer().append(result, HoldReleaseReason.USER_CANCELED);

        assertThat(outboxId).isEqualTo(99L);
    }

    @Test
    void append_records_payment_confirmed_reason() {
        OrderTerminationResult result = new OrderTerminationResult(1L, "hold-key", List.of(10L));
        LocalDateTime expectedNow = LocalDateTime.of(2026, 3, 15, 10, 0);
        HoldReleaseOutbox saved = HoldReleaseOutbox.create(
                1L, "hold-key", List.of(10L), expectedNow, HoldReleaseReason.PAYMENT_CONFIRMED
        );
        ReflectionTestUtils.setField(saved, "id", 77L);
        when(holdReleaseOutboxRepository.save(argThat(outbox ->
                outbox.getReason() == HoldReleaseReason.PAYMENT_CONFIRMED
        ))).thenReturn(saved);

        Long outboxId = writer().append(result, HoldReleaseReason.PAYMENT_CONFIRMED);

        assertThat(outboxId).isEqualTo(77L);
    }
```

- [ ] **Step 2: 실패를 확인한다**

Run: `.\gradlew.bat :core:core-domain:test --tests "com.ticket.core.domain.order.command.release.HoldReleaseOutboxWriterTest"`
Expected: 컴파일 실패 — `create`/`append` 인자 개수 불일치, `getReason()` 없음

- [ ] **Step 3: 엔티티에 reason을 추가한다**

`HoldReleaseOutbox.java`에 `import com.ticket.core.domain.hold.model.HoldReleaseReason;`를 추가하고 `lastError` 필드 뒤에 필드를 넣는다. `EnumType`, `Enumerated`는 이미 import되어 있다.

```java
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private HoldReleaseReason reason;
```

생성자와 정적 팩토리에 `reason`을 더한다. **NULL을 허용**하므로 기존 데이터 백필이 필요 없다.

```java
    private HoldReleaseOutbox(
            final Long performanceId,
            final String holdKey,
            final String seatIdsPayload,
            final LocalDateTime nextAttemptAt,
            final HoldReleaseReason reason
    ) {
        this.performanceId = performanceId;
        this.holdKey = holdKey;
        this.seatIdsPayload = seatIdsPayload;
        this.nextAttemptAt = nextAttemptAt;
        this.retryCount = 0;
        this.status = HoldReleaseOutboxStatus.PENDING;
        this.reason = reason;
    }

    public static HoldReleaseOutbox create(
            final Long performanceId,
            final String holdKey,
            final List<Long> seatIds,
            final LocalDateTime nextAttemptAt,
            final HoldReleaseReason reason
    ) {
        return new HoldReleaseOutbox(performanceId, holdKey, serializeSeatIds(seatIds), nextAttemptAt, reason);
    }
```

- [ ] **Step 4: Task, Writer, TransactionService를 맞춘다**

`HoldReleaseTask.java` 전체를 교체한다.

```java
package com.ticket.core.domain.order.command.release;

import com.ticket.core.domain.hold.model.HoldReleaseReason;

import java.util.List;

public record HoldReleaseTask(
        Long performanceId,
        String holdKey,
        List<Long> seatIds,
        boolean holdReleased,
        HoldReleaseReason reason
) {
}
```

`HoldReleaseOutboxWriter.java`의 `append`를 교체하고 import에 `com.ticket.core.domain.hold.model.HoldReleaseReason`를 추가한다.

```java
    public Long append(final OrderTerminationResult result, final HoldReleaseReason reason) {
        final HoldReleaseOutbox outbox = holdReleaseOutboxRepository.save(HoldReleaseOutbox.create(
                result.performanceId(),
                result.holdKey(),
                result.seatIds(),
                LocalDateTime.now(clock),
                reason
        ));
        return outbox.getId();
    }
```

`HoldReleaseOutboxTransactionService.java`의 `load`에서 `HoldReleaseTask` 생성부에 `outbox.getReason()`을 더한다.

```java
                .map(outbox -> new HoldReleaseTask(
                        outbox.getPerformanceId(),
                        outbox.getHoldKey(),
                        outbox.seatIds(),
                        outbox.isHoldReleased(),
                        outbox.getReason()
                ))
```

- [ ] **Step 5: OrderTerminationService가 이유를 전달하게 한다**

`OrderTerminationService.java`에 `import com.ticket.core.domain.hold.model.HoldReleaseReason;`를 추가하고 아래처럼 바꾼다.

```java
    public void cancel(final Order order, final LocalDateTime now) {
        final List<OrderSeat> orderSeats = loadAndValidateOrderSeats(order);
        order.cancel(now);
        holdHistoryRecorder.recordCanceled(
                order.getMemberId(), order.getPerformanceId(), order.getHoldKey(), now, orderSeats
        );
        requestHoldRelease(toResult(order, orderSeats), HoldReleaseReason.USER_CANCELED);
    }

    public void expire(final Order order, final LocalDateTime now) {
        final List<OrderSeat> orderSeats = loadAndValidateOrderSeats(order);
        order.expire(now);
        holdHistoryRecorder.recordExpired(
                order.getMemberId(), order.getPerformanceId(), order.getHoldKey(), now, orderSeats
        );
        requestHoldRelease(toResult(order, orderSeats), HoldReleaseReason.ORDER_EXPIRED);
    }
```

```java
    private void requestHoldRelease(final OrderTerminationResult result, final HoldReleaseReason reason) {
        final Long outboxId = holdReleaseOutboxWriter.append(result, reason);
        applicationEventPublisher.publishEvent(new HoldReleaseRequestedEvent(outboxId));
    }
```

`OrderTerminationServiceTest.java`의 두 `when(...)` 스텁도 새 시그니처로 바꾼다. import에 `com.ticket.core.domain.hold.model.HoldReleaseReason`를 추가한다.

```java
        when(holdReleaseOutboxWriter.append(
                new OrderTerminationResult(100L, "hold-key", List.of(42L)),
                HoldReleaseReason.USER_CANCELED
        )).thenReturn(99L);
```

```java
        when(holdReleaseOutboxWriter.append(
                new OrderTerminationResult(100L, "hold-key", List.of(42L)),
                HoldReleaseReason.ORDER_EXPIRED
        )).thenReturn(99L);
```

- [ ] **Step 6: Flyway 마이그레이션을 만든다**

`core/core-api/src/main/resources/db/migration-vendor/h2/V9__add_reason_to_order_hold_release_outbox.sql`

```sql
ALTER TABLE ORDER_HOLD_RELEASE_OUTBOX
    ADD reason VARCHAR(32);
```

`core/core-api/src/main/resources/db/migration-vendor/oracle/V9__add_reason_to_order_hold_release_outbox.sql`

```sql
ALTER TABLE ORDER_HOLD_RELEASE_OUTBOX
    ADD reason VARCHAR2(32);
```

- [ ] **Step 7: 남은 컴파일 오류를 고친다**

`HoldReleaseTask` 생성자와 `HoldReleaseOutbox.create`를 쓰는 다른 테스트(`HoldReleaseTaskProcessorTest`, `HoldReleaseOutboxTransactionServiceTest`, `HoldReleaseOutboxExecutorTest`)가 인자 부족으로 깨진다. 각 호출에 마지막 인자로 `HoldReleaseReason.ORDER_EXPIRED`를 넣어 기존 동작(발행함)을 유지한다.

Run: `.\gradlew.bat :core:core-domain:test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: 마이그레이션 테스트를 돌린다**

Run: `.\gradlew.bat :core:core-api:test --tests "com.ticket.core.migration.CoreQueryIndexMigrationTest"`
Expected: PASS (2 tests)

- [ ] **Step 9: 커밋한다**

```bash
git add core/core-domain/src/main/java/com/ticket/core/domain/order core/core-domain/src/test/java/com/ticket/core/domain/order core/core-api/src/main/resources/db/migration-vendor/h2/V9__add_reason_to_order_hold_release_outbox.sql core/core-api/src/main/resources/db/migration-vendor/oracle/V9__add_reason_to_order_hold_release_outbox.sql
git commit -m "feat(order): hold 해제 outbox에 해제 이유를 기록"
```

---

## Task 6: 결제 확정이면 좌석 이벤트를 발행하지 않는다

**Files:**
- Modify: `core/core-domain/src/main/java/com/ticket/core/domain/order/command/release/HoldReleaseTaskProcessor.java`
- Test: `core/core-domain/src/test/java/com/ticket/core/domain/order/command/release/HoldReleaseTaskProcessorTest.java`

프론트엔드는 `HELD`와 `RESERVED`를 모두 점유로 취급하므로 확정 시 새로 발행할 이벤트가 없다. 지켜야 할 것은 `RELEASED`를 발행하지 않는 것이다.

- [ ] **Step 1: 실패하는 테스트를 추가한다**

`HoldReleaseTaskProcessorTest.java`에 아래 두 테스트를 추가한다. import에 `com.ticket.core.domain.hold.model.HoldReleaseReason`를 추가한다.

```java
    @Test
    void 결제_확정_해제는_hold만_지우고_좌석_이벤트를_발행하지_않는다() {
        final HoldReleaseTask task = new HoldReleaseTask(
                1L, "old-hold", List.of(10L, 20L), false, HoldReleaseReason.PAYMENT_CONFIRMED
        );

        taskProcessor.process(99L, task, FIXED_NOW);

        verify(holdManager).release(1L, "old-hold", List.of(10L, 20L));
        verify(transactionService).markHoldReleased(99L, FIXED_NOW);
        verifyNoInteractions(seatStatusPublisher, seatSelectionService);
    }

    @Test
    void 해제_이유가_없으면_기존처럼_RELEASED를_발행한다() {
        final HoldReleaseTask task = new HoldReleaseTask(
                1L, "old-hold", List.of(10L), false, null
        );
        when(seatSelectionService.getSelectingSeatIds(1L)).thenReturn(Set.of());
        when(holdManager.isHeld(1L, 10L)).thenReturn(false);

        taskProcessor.process(99L, task, FIXED_NOW);

        verify(seatStatusPublisher).publishReleased(1L, List.of(10L));
    }
```

- [ ] **Step 2: 실패를 확인한다**

Run: `.\gradlew.bat :core:core-domain:test --tests "com.ticket.core.domain.order.command.release.HoldReleaseTaskProcessorTest"`
Expected: FAIL — `결제_확정_해제는...`에서 `seatSelectionService`/`seatStatusPublisher`가 호출되어 `verifyNoInteractions` 실패

- [ ] **Step 3: processor에 분기를 넣는다**

`HoldReleaseTaskProcessor.java`의 `process`를 교체하고 import에 `com.ticket.core.domain.hold.model.HoldReleaseReason`를 추가한다.

```java
    @DistributedLock(
            prefix = "hold",
            dynamicKey = "#task.seatIds().![#task.performanceId() + ':' + #this]"
    )
    public void process(final Long outboxId, final HoldReleaseTask task, final LocalDateTime now) {
        releaseHoldOnce(outboxId, task, now);
        if (task.reason() == HoldReleaseReason.PAYMENT_CONFIRMED) {
            return;
        }
        final List<Long> publishableSeatIds = findCurrentlyAvailableSeats(task);
        if (publishableSeatIds.isEmpty()) {
            return;
        }
        seatStatusPublisher.publishReleased(task.performanceId(), publishableSeatIds);
    }
```

- [ ] **Step 4: 테스트 통과를 확인한다**

Run: `.\gradlew.bat :core:core-domain:test --tests "com.ticket.core.domain.order.command.release.HoldReleaseTaskProcessorTest"`
Expected: PASS (기존 테스트 + 추가한 2개)

- [ ] **Step 5: 커밋한다**

```bash
git add core/core-domain/src/main/java/com/ticket/core/domain/order/command/release/HoldReleaseTaskProcessor.java core/core-domain/src/test/java/com/ticket/core/domain/order/command/release/HoldReleaseTaskProcessorTest.java
git commit -m "feat(order): 결제 확정 hold 해제는 좌석 이벤트를 발행하지 않도록 분기"
```

---

## Task 7: 확정 hold 이력 기록

**Files:**
- Modify: `core/core-domain/src/main/java/com/ticket/core/domain/hold/model/HoldHistory.java`
- Modify: `core/core-domain/src/main/java/com/ticket/core/domain/hold/command/HoldHistoryRecorder.java`
- Test: `core/core-domain/src/test/java/com/ticket/core/domain/hold/command/HoldHistoryRecorderTest.java`

- [ ] **Step 1: 실패하는 테스트를 작성한다**

파일이 이미 있으면 테스트 메서드만 추가한다. 없으면 아래 내용으로 만든다.

```java
package com.ticket.core.domain.hold.command;

import com.ticket.core.domain.hold.model.HoldHistory;
import com.ticket.core.domain.hold.model.HoldHistoryEventType;
import com.ticket.core.domain.hold.model.HoldReleaseReason;
import com.ticket.core.domain.hold.repository.HoldHistoryRepository;
import com.ticket.core.domain.order.model.Order;
import com.ticket.core.domain.order.model.OrderSeat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class HoldHistoryRecorderTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 8, 21, 10, 0);

    @Mock
    private HoldHistoryRepository holdHistoryRepository;

    @Test
    void 확정_이력은_CONFIRMED와_PAYMENT_CONFIRMED로_기록된다() {
        final Order order = new Order(1L, 100L, "order-key", "hold-key", BigDecimal.TEN, FIXED_NOW.plusMinutes(5));
        final OrderSeat orderSeat = new OrderSeat(order, 501L, 42L, BigDecimal.TEN);

        new HoldHistoryRecorder(holdHistoryRepository)
                .recordConfirmed(1L, 100L, "hold-key", FIXED_NOW, List.of(orderSeat));

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<List<HoldHistory>> captor = ArgumentCaptor.forClass(List.class);
        verify(holdHistoryRepository).saveAll(captor.capture());
        final HoldHistory saved = captor.getValue().get(0);
        assertThat(saved.getEventType()).isEqualTo(HoldHistoryEventType.CONFIRMED);
        assertThat(saved.getReleaseReason()).isEqualTo(HoldReleaseReason.PAYMENT_CONFIRMED);
        assertThat(saved.getHoldKey()).isEqualTo("hold-key");
        assertThat(saved.getPerformanceSeatId()).isEqualTo(501L);
        assertThat(saved.getSeatId()).isEqualTo(42L);
        assertThat(saved.getOccurredAt()).isEqualTo(FIXED_NOW);
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

Run: `.\gradlew.bat :core:core-domain:test --tests "com.ticket.core.domain.hold.command.HoldHistoryRecorderTest"`
Expected: 컴파일 실패 — `recordConfirmed` 없음

- [ ] **Step 3: HoldHistory에 팩토리를 추가한다**

`HoldHistory.java`의 `canceled` 팩토리 뒤에 같은 형태로 추가한다.

```java
    public static HoldHistory confirmed(
            final String holdKey,
            final Long memberId,
            final Long performanceId,
            final Long performanceSeatId,
            final Long seatId,
            final LocalDateTime occurredAt
    ) {
        return new HoldHistory(holdKey, memberId, performanceId, performanceSeatId, seatId,
                HoldHistoryEventType.CONFIRMED, occurredAt, null, HoldReleaseReason.PAYMENT_CONFIRMED);
    }
```

- [ ] **Step 4: Recorder에 메서드를 추가한다**

`HoldHistoryRecorder.java`의 `recordExpired` 뒤에 추가한다.

```java
    public void recordConfirmed(
            final Long memberId,
            final Long performanceId,
            final String holdKey,
            final LocalDateTime occurredAt,
            final List<OrderSeat> orderSeats
    ) {
        holdHistoryRepository.saveAll(orderSeats.stream()
                .map(seat -> HoldHistory.confirmed(
                        holdKey,
                        memberId,
                        performanceId,
                        seat.getPerformanceSeatId(),
                        seat.getSeatId(),
                        occurredAt
                ))
                .toList());
    }
```

- [ ] **Step 5: 테스트 통과를 확인한다**

Run: `.\gradlew.bat :core:core-domain:test --tests "com.ticket.core.domain.hold.command.HoldHistoryRecorderTest"`
Expected: PASS

- [ ] **Step 6: 커밋한다**

```bash
git add core/core-domain/src/main/java/com/ticket/core/domain/hold core/core-domain/src/test/java/com/ticket/core/domain/hold
git commit -m "feat(hold): 주문 확정 hold 이력 기록 추가"
```

---

## Task 8: 확정 트랜잭션 (주문·좌석 전이)

**Files:**
- Create: `core/core-domain/src/main/java/com/ticket/core/domain/order/command/OrderConfirmationService.java`
- Test: `core/core-domain/src/test/java/com/ticket/core/domain/order/command/OrderConfirmationServiceTest.java`

`OrderTerminationService`와 같은 위치·같은 성격이다. 상태 전이와 outbox 적재만 하고 외부 I/O는 하지 않는다.

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`PerformanceSeat`의 `performance`와 `seat`는 이 검증에 쓰이지 않으므로 `null`로 만든다. 검증 대상은 `state` 전이뿐이다.

`core/core-domain/src/test/java/com/ticket/core/domain/order/command/OrderConfirmationServiceTest.java`

```java
package com.ticket.core.domain.order.command;

import com.ticket.core.domain.hold.command.HoldHistoryRecorder;
import com.ticket.core.domain.hold.model.HoldReleaseReason;
import com.ticket.core.domain.order.OrderTerminationResult;
import com.ticket.core.domain.order.command.release.HoldReleaseOutboxWriter;
import com.ticket.core.domain.order.command.release.HoldReleaseRequestedEvent;
import com.ticket.core.domain.order.model.Order;
import com.ticket.core.domain.order.model.OrderSeat;
import com.ticket.core.domain.order.model.OrderState;
import com.ticket.core.domain.order.repository.OrderSeatRepository;
import com.ticket.core.domain.performanceseat.model.PerformanceSeat;
import com.ticket.core.domain.performanceseat.model.PerformanceSeatState;
import com.ticket.core.domain.performanceseat.repository.PerformanceSeatRepository;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class OrderConfirmationServiceTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 8, 21, 10, 0);

    @Mock
    private OrderSeatRepository orderSeatRepository;

    @Mock
    private PerformanceSeatRepository performanceSeatRepository;

    @Mock
    private HoldHistoryRecorder holdHistoryRecorder;

    @Mock
    private HoldReleaseOutboxWriter holdReleaseOutboxWriter;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Test
    void 확정하면_주문과_좌석을_전이하고_결제확정_이유로_해제를_요청한다() {
        final Order order = order();
        final OrderSeat orderSeat = new OrderSeat(order, 501L, 42L, BigDecimal.TEN);
        final PerformanceSeat performanceSeat = performanceSeat(501L, PerformanceSeatState.AVAILABLE);
        when(orderSeatRepository.findAllByOrder_IdOrderByIdAsc(10L)).thenReturn(List.of(orderSeat));
        when(performanceSeatRepository.findAllById(List.of(501L))).thenReturn(List.of(performanceSeat));
        when(holdReleaseOutboxWriter.append(
                new OrderTerminationResult(100L, "hold-key", List.of(42L)),
                HoldReleaseReason.PAYMENT_CONFIRMED
        )).thenReturn(99L);

        service().confirm(order, FIXED_NOW);

        assertThat(order.getStatus()).isEqualTo(OrderState.CONFIRMED);
        assertThat(order.getConfirmedAt()).isEqualTo(FIXED_NOW);
        assertThat(performanceSeat.getState()).isEqualTo(PerformanceSeatState.RESERVED);
        verify(holdHistoryRecorder).recordConfirmed(1L, 100L, "hold-key", FIXED_NOW, List.of(orderSeat));
        verify(applicationEventPublisher).publishEvent(new HoldReleaseRequestedEvent(99L));
    }

    @Test
    void 좌석이_이미_예매완료면_아무것도_바꾸지_않고_예외를_던진다() {
        final Order order = order();
        final OrderSeat orderSeat = new OrderSeat(order, 501L, 42L, BigDecimal.TEN);
        final PerformanceSeat performanceSeat = performanceSeat(501L, PerformanceSeatState.RESERVED);
        when(orderSeatRepository.findAllByOrder_IdOrderByIdAsc(10L)).thenReturn(List.of(orderSeat));
        when(performanceSeatRepository.findAllById(List.of(501L))).thenReturn(List.of(performanceSeat));

        assertThatThrownBy(() -> service().confirm(order, FIXED_NOW))
                .isInstanceOf(CoreException.class)
                .satisfies(error -> assertThat(((CoreException) error).getErrorType())
                        .isEqualTo(ErrorType.SEAT_ALREADY_RESERVED));

        assertThat(order.getStatus()).isEqualTo(OrderState.PENDING);
        verifyNoInteractions(holdHistoryRecorder, holdReleaseOutboxWriter, applicationEventPublisher);
    }

    @Test
    void 회차좌석을_일부만_찾으면_예외를_던진다() {
        final Order order = order();
        final OrderSeat first = new OrderSeat(order, 501L, 42L, BigDecimal.TEN);
        final OrderSeat second = new OrderSeat(order, 502L, 43L, BigDecimal.TEN);
        when(orderSeatRepository.findAllByOrder_IdOrderByIdAsc(10L)).thenReturn(List.of(first, second));
        when(performanceSeatRepository.findAllById(List.of(501L, 502L)))
                .thenReturn(List.of(performanceSeat(501L, PerformanceSeatState.AVAILABLE)));

        assertThatThrownBy(() -> service().confirm(order, FIXED_NOW))
                .isInstanceOf(CoreException.class)
                .satisfies(error -> assertThat(((CoreException) error).getErrorType())
                        .isEqualTo(ErrorType.SEAT_MISMATCH_IN_PERFORMANCE));

        assertThat(order.getStatus()).isEqualTo(OrderState.PENDING);
    }

    @Test
    void 다른_주문의_좌석이_섞여_있으면_예외를_던진다() {
        final Order order = order();
        final Order otherOrder = new Order(2L, 100L, "other-key", "other-hold", BigDecimal.TEN, FIXED_NOW.plusMinutes(5));
        ReflectionTestUtils.setField(otherOrder, "id", 11L);
        final OrderSeat foreign = new OrderSeat(otherOrder, 501L, 42L, BigDecimal.TEN);
        when(orderSeatRepository.findAllByOrder_IdOrderByIdAsc(10L)).thenReturn(List.of(foreign));

        assertThatThrownBy(() -> service().confirm(order, FIXED_NOW))
                .isInstanceOf(CoreException.class)
                .satisfies(error -> assertThat(((CoreException) error).getErrorType())
                        .isEqualTo(ErrorType.INVALID_REQUEST));

        assertThat(order.getStatus()).isEqualTo(OrderState.PENDING);
        verifyNoInteractions(performanceSeatRepository, holdHistoryRecorder, holdReleaseOutboxWriter, applicationEventPublisher);
    }

    private OrderConfirmationService service() {
        return new OrderConfirmationService(
                orderSeatRepository,
                performanceSeatRepository,
                holdHistoryRecorder,
                holdReleaseOutboxWriter,
                applicationEventPublisher
        );
    }

    private Order order() {
        final Order order = new Order(1L, 100L, "order-key", "hold-key", BigDecimal.TEN, FIXED_NOW.plusMinutes(5));
        ReflectionTestUtils.setField(order, "id", 10L);
        return order;
    }

    private PerformanceSeat performanceSeat(final Long id, final PerformanceSeatState state) {
        final PerformanceSeat performanceSeat = new PerformanceSeat(null, null, state, BigDecimal.TEN);
        ReflectionTestUtils.setField(performanceSeat, "id", id);
        return performanceSeat;
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

Run: `.\gradlew.bat :core:core-domain:test --tests "com.ticket.core.domain.order.command.OrderConfirmationServiceTest"`
Expected: 컴파일 실패 — `OrderConfirmationService` 없음

- [ ] **Step 3: 서비스를 만든다**

`core/core-domain/src/main/java/com/ticket/core/domain/order/command/OrderConfirmationService.java`

```java
package com.ticket.core.domain.order.command;

import com.ticket.core.domain.hold.command.HoldHistoryRecorder;
import com.ticket.core.domain.hold.model.HoldReleaseReason;
import com.ticket.core.domain.order.OrderTerminationResult;
import com.ticket.core.domain.order.command.release.HoldReleaseOutboxWriter;
import com.ticket.core.domain.order.command.release.HoldReleaseRequestedEvent;
import com.ticket.core.domain.order.model.Order;
import com.ticket.core.domain.order.model.OrderSeat;
import com.ticket.core.domain.order.repository.OrderSeatRepository;
import com.ticket.core.domain.performanceseat.model.PerformanceSeat;
import com.ticket.core.domain.performanceseat.model.PerformanceSeatState;
import com.ticket.core.domain.performanceseat.repository.PerformanceSeatRepository;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class OrderConfirmationService {

    private final OrderSeatRepository orderSeatRepository;
    private final PerformanceSeatRepository performanceSeatRepository;
    private final HoldHistoryRecorder holdHistoryRecorder;
    private final HoldReleaseOutboxWriter holdReleaseOutboxWriter;
    private final ApplicationEventPublisher applicationEventPublisher;

    public void confirm(final Order order, final LocalDateTime now) {
        final List<OrderSeat> orderSeats = loadAndValidateOrderSeats(order);
        final List<PerformanceSeat> performanceSeats = loadAvailablePerformanceSeats(orderSeats);

        order.confirm(now);
        performanceSeats.forEach(PerformanceSeat::reserve);
        holdHistoryRecorder.recordConfirmed(
                order.getMemberId(), order.getPerformanceId(), order.getHoldKey(), now, orderSeats
        );
        requestHoldRelease(order, orderSeats);
    }

    private List<OrderSeat> loadAndValidateOrderSeats(final Order order) {
        final List<OrderSeat> orderSeats =
                orderSeatRepository.findAllByOrder_IdOrderByIdAsc(order.getId());
        if (orderSeats.isEmpty()) {
            throw new CoreException(ErrorType.INVALID_REQUEST, "확정할 주문 좌석이 없습니다.");
        }
        final boolean hasForeignOrderSeat = orderSeats.stream()
                .anyMatch(orderSeat -> !Objects.equals(orderSeat.getOrder().getId(), order.getId()));
        if (hasForeignOrderSeat) {
            throw new CoreException(ErrorType.INVALID_REQUEST, "orderSeats는 같은 order에 속해야 합니다.");
        }
        return orderSeats;
    }

    private List<PerformanceSeat> loadAvailablePerformanceSeats(final List<OrderSeat> orderSeats) {
        final List<Long> performanceSeatIds = orderSeats.stream()
                .map(OrderSeat::getPerformanceSeatId)
                .toList();
        final List<PerformanceSeat> performanceSeats =
                performanceSeatRepository.findAllById(performanceSeatIds);
        if (performanceSeats.size() != performanceSeatIds.size()) {
            throw new CoreException(ErrorType.SEAT_MISMATCH_IN_PERFORMANCE);
        }
        final boolean hasUnavailableSeat = performanceSeats.stream()
                .anyMatch(seat -> seat.getState() != PerformanceSeatState.AVAILABLE);
        if (hasUnavailableSeat) {
            throw new CoreException(ErrorType.SEAT_ALREADY_RESERVED);
        }
        return performanceSeats;
    }

    private void requestHoldRelease(final Order order, final List<OrderSeat> orderSeats) {
        final OrderTerminationResult result = new OrderTerminationResult(
                order.getPerformanceId(),
                order.getHoldKey(),
                orderSeats.stream().map(OrderSeat::getSeatId).toList()
        );
        final Long outboxId = holdReleaseOutboxWriter.append(result, HoldReleaseReason.PAYMENT_CONFIRMED);
        applicationEventPublisher.publishEvent(new HoldReleaseRequestedEvent(outboxId));
    }
}
```

- [ ] **Step 4: 테스트 통과를 확인한다**

Run: `.\gradlew.bat :core:core-domain:test --tests "com.ticket.core.domain.order.command.OrderConfirmationServiceTest"`
Expected: PASS (4 tests)

- [ ] **Step 5: 커밋한다**

```bash
git add core/core-domain/src/main/java/com/ticket/core/domain/order/command/OrderConfirmationService.java core/core-domain/src/test/java/com/ticket/core/domain/order/command/OrderConfirmationServiceTest.java
git commit -m "feat(order): 결제 확정 시 주문과 회차 좌석을 전이하는 서비스 추가"
```

---

## Task 9: 승인 단계 use case

**Files:**
- Create: `core/core-domain/src/main/java/com/ticket/core/domain/payment/command/PaymentConfirmationTxService.java`
- Create: `core/core-domain/src/main/java/com/ticket/core/domain/payment/command/ConfirmPaymentUseCase.java`
- Test: `core/core-domain/src/test/java/com/ticket/core/domain/payment/command/ConfirmPaymentUseCaseTest.java`

게이트웨이 호출은 트랜잭션 밖에서 한다. `CreateOrderUseCase`(트랜잭션 없음) + `CreatePendingOrderTxService`(`@Transactional`)와 같은 구조다.

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`core/core-domain/src/test/java/com/ticket/core/domain/payment/command/ConfirmPaymentUseCaseTest.java`

```java
package com.ticket.core.domain.payment.command;

import com.ticket.core.domain.member.query.MemberFinder;
import com.ticket.core.domain.payment.gateway.PaymentApprovalCommand;
import com.ticket.core.domain.payment.gateway.PaymentApprovalResult;
import com.ticket.core.domain.payment.gateway.PaymentGatewayClient;
import com.ticket.core.domain.payment.model.PaymentMethod;
import com.ticket.core.domain.payment.model.PaymentStatus;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class ConfirmPaymentUseCaseTest {

    private static final BigDecimal AMOUNT = BigDecimal.valueOf(120000);
    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 8, 21, 10, 0);
    private static final PaymentApprovalCommand EXPECTED_COMMAND = new PaymentApprovalCommand(
            "PAY-1", 10L, AMOUNT, PaymentMethod.CARD
    );

    @Mock
    private MemberFinder memberFinder;

    @Mock
    private PaymentGatewayClient paymentGatewayClient;

    @Mock
    private PaymentConfirmationTxService txService;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-08-21T01:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Test
    void 승인되면_확정_트랜잭션을_호출하고_결과를_반환한다() {
        when(txService.loadConfirmable("PAY-1", 1L, AMOUNT, FIXED_NOW)).thenReturn(confirmable(false));
        when(paymentGatewayClient.approve(EXPECTED_COMMAND))
                .thenReturn(PaymentApprovalResult.approved("FAKEPG-1"));
        when(txService.approve(20L, "FAKEPG-1", FIXED_NOW)).thenReturn(new ConfirmPaymentUseCase.Output(
                "PAY-1", PaymentStatus.APPROVED, FIXED_NOW, "order-key", "CONFIRMED"
        ));

        final ConfirmPaymentUseCase.Output output = useCase().execute(input());

        assertThat(output.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(output.orderStatus()).isEqualTo("CONFIRMED");
        verify(memberFinder).findActiveMemberById(1L);
    }

    @Test
    void 이미_승인된_결제면_게이트웨이를_호출하지_않고_같은_결과를_반환한다() {
        when(txService.loadConfirmable("PAY-1", 1L, AMOUNT, FIXED_NOW)).thenReturn(confirmable(true));

        final ConfirmPaymentUseCase.Output output = useCase().execute(input());

        assertThat(output.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(output.approvedAt()).isEqualTo(FIXED_NOW);
        verifyNoInteractions(paymentGatewayClient);
    }

    @Test
    void 거절되면_실패를_기록하고_예외를_던진다() {
        when(txService.loadConfirmable("PAY-1", 1L, AMOUNT, FIXED_NOW)).thenReturn(confirmable(false));
        when(paymentGatewayClient.approve(EXPECTED_COMMAND))
                .thenReturn(PaymentApprovalResult.declined("FAKE_DECLINED", "거절되었습니다."));

        assertThatThrownBy(() -> useCase().execute(input()))
                .isInstanceOf(CoreException.class)
                .satisfies(error -> assertThat(((CoreException) error).getErrorType())
                        .isEqualTo(ErrorType.PAYMENT_DECLINED));

        verify(txService).fail(20L, "FAKE_DECLINED", "거절되었습니다.", FIXED_NOW);
    }

    @Test
    void execute는_DB_트랜잭션을_직접_시작하지_않는다() throws Exception {
        assertThat(ConfirmPaymentUseCase.class
                .getDeclaredMethod("execute", ConfirmPaymentUseCase.Input.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class))
                .isNull();
        assertThat(ConfirmPaymentUseCase.class
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class))
                .isNull();
    }

    private ConfirmPaymentUseCase useCase() {
        return new ConfirmPaymentUseCase(memberFinder, paymentGatewayClient, txService, fixedClock);
    }

    private ConfirmPaymentUseCase.Input input() {
        return new ConfirmPaymentUseCase.Input("PAY-1", 1L, AMOUNT);
    }

    private PaymentConfirmationTxService.ConfirmablePayment confirmable(final boolean alreadyApproved) {
        return new PaymentConfirmationTxService.ConfirmablePayment(
                20L,
                "PAY-1",
                10L,
                PaymentMethod.CARD,
                AMOUNT,
                alreadyApproved,
                alreadyApproved ? FIXED_NOW : null,
                "order-key",
                alreadyApproved ? "CONFIRMED" : "PENDING"
        );
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

Run: `.\gradlew.bat :core:core-domain:test --tests "com.ticket.core.domain.payment.command.ConfirmPaymentUseCaseTest"`
Expected: 컴파일 실패 — `ConfirmPaymentUseCase`, `PaymentConfirmationTxService` 없음

- [ ] **Step 3: 트랜잭션 서비스를 만든다**

`core/core-domain/src/main/java/com/ticket/core/domain/payment/command/PaymentConfirmationTxService.java`

```java
package com.ticket.core.domain.payment.command;

import com.ticket.core.domain.order.command.OrderConfirmationService;
import com.ticket.core.domain.order.model.Order;
import com.ticket.core.domain.order.model.OrderState;
import com.ticket.core.domain.order.repository.OrderRepository;
import com.ticket.core.domain.payment.model.Payment;
import com.ticket.core.domain.payment.model.PaymentMethod;
import com.ticket.core.domain.payment.repository.PaymentRepository;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class PaymentConfirmationTxService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderConfirmationService orderConfirmationService;

    public record ConfirmablePayment(
            Long paymentId,
            String paymentKey,
            Long orderId,
            PaymentMethod method,
            BigDecimal amount,
            boolean alreadyApproved,
            LocalDateTime approvedAt,
            String orderKey,
            String orderStatus
    ) {}

    @Transactional(readOnly = true)
    public ConfirmablePayment loadConfirmable(
            final String paymentKey,
            final Long memberId,
            final BigDecimal amount,
            final LocalDateTime now
    ) {
        final Payment payment = paymentRepository.findByPaymentKey(paymentKey)
                .orElseThrow(() -> new CoreException(ErrorType.PAYMENT_NOT_FOUND));
        final Order order = orderRepository.findById(payment.getOrderId())
                .orElseThrow(() -> new CoreException(ErrorType.PAYMENT_NOT_FOUND));
        if (!order.getMemberId().equals(memberId)) {
            throw new CoreException(ErrorType.ORDER_NOT_OWNED);
        }
        if (payment.getAmount().compareTo(amount) != 0) {
            throw new CoreException(ErrorType.PAYMENT_AMOUNT_MISMATCH);
        }
        if (payment.isApproved()) {
            return toConfirmable(payment, order, true);
        }
        if (!payment.isReady()) {
            throw new CoreException(ErrorType.PAYMENT_NOT_READY);
        }
        if (!order.isPending()) {
            throw new CoreException(ErrorType.ORDER_NOT_PENDING);
        }
        if (order.isExpired(now)) {
            throw new CoreException(ErrorType.ORDER_HOLD_EXPIRED);
        }
        return toConfirmable(payment, order, false);
    }

    @Transactional
    public ConfirmPaymentUseCase.Output approve(
            final Long paymentId,
            final String pgTransactionId,
            final LocalDateTime now
    ) {
        final Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new CoreException(ErrorType.PAYMENT_NOT_FOUND));
        if (payment.isApproved()) {
            final Order confirmedOrder = orderRepository.findById(payment.getOrderId())
                    .orElseThrow(() -> new CoreException(ErrorType.PAYMENT_NOT_FOUND));
            return toOutput(payment, confirmedOrder);
        }
        if (!payment.isReady()) {
            throw new CoreException(ErrorType.PAYMENT_NOT_READY);
        }
        final Order order = orderRepository
                .findByIdAndStatusForUpdate(payment.getOrderId(), OrderState.PENDING)
                .orElseThrow(() -> new CoreException(ErrorType.ORDER_NOT_PENDING));
        if (order.isExpired(now)) {
            throw new CoreException(ErrorType.ORDER_HOLD_EXPIRED);
        }
        payment.approve(now, pgTransactionId);
        orderConfirmationService.confirm(order, now);
        return toOutput(payment, order);
    }

    @Transactional
    public void fail(
            final Long paymentId,
            final String failureCode,
            final String failureMessage,
            final LocalDateTime now
    ) {
        final Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new CoreException(ErrorType.PAYMENT_NOT_FOUND));
        if (!payment.isReady()) {
            return;
        }
        payment.fail(now, failureCode, failureMessage);
    }

    private ConfirmablePayment toConfirmable(
            final Payment payment,
            final Order order,
            final boolean alreadyApproved
    ) {
        return new ConfirmablePayment(
                payment.getId(),
                payment.getPaymentKey(),
                payment.getOrderId(),
                payment.getMethod(),
                payment.getAmount(),
                alreadyApproved,
                payment.getApprovedAt(),
                order.getOrderKey(),
                order.getStatus().name()
        );
    }

    private ConfirmPaymentUseCase.Output toOutput(final Payment payment, final Order order) {
        return new ConfirmPaymentUseCase.Output(
                payment.getPaymentKey(),
                payment.getStatus(),
                payment.getApprovedAt(),
                order.getOrderKey(),
                order.getStatus().name()
        );
    }
}
```

- [ ] **Step 4: use case를 만든다**

`core/core-domain/src/main/java/com/ticket/core/domain/payment/command/ConfirmPaymentUseCase.java`

```java
package com.ticket.core.domain.payment.command;

import com.ticket.core.domain.member.query.MemberFinder;
import com.ticket.core.domain.payment.gateway.PaymentApprovalCommand;
import com.ticket.core.domain.payment.gateway.PaymentApprovalResult;
import com.ticket.core.domain.payment.gateway.PaymentGatewayClient;
import com.ticket.core.domain.payment.model.PaymentStatus;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ConfirmPaymentUseCase {

    private final MemberFinder memberFinder;
    private final PaymentGatewayClient paymentGatewayClient;
    private final PaymentConfirmationTxService txService;
    private final Clock clock;

    public record Input(String paymentKey, Long memberId, BigDecimal amount) {}

    public record Output(
            String paymentKey,
            PaymentStatus status,
            LocalDateTime approvedAt,
            String orderKey,
            String orderStatus
    ) {}

    public Output execute(final Input input) {
        memberFinder.findActiveMemberById(input.memberId());
        final LocalDateTime now = LocalDateTime.now(clock);
        final PaymentConfirmationTxService.ConfirmablePayment confirmable =
                txService.loadConfirmable(input.paymentKey(), input.memberId(), input.amount(), now);

        if (confirmable.alreadyApproved()) {
            return new Output(
                    confirmable.paymentKey(),
                    PaymentStatus.APPROVED,
                    confirmable.approvedAt(),
                    confirmable.orderKey(),
                    confirmable.orderStatus()
            );
        }

        final PaymentApprovalResult result = paymentGatewayClient.approve(new PaymentApprovalCommand(
                confirmable.paymentKey(),
                confirmable.orderId(),
                confirmable.amount(),
                confirmable.method()
        ));

        if (!result.approved()) {
            txService.fail(confirmable.paymentId(), result.failureCode(), result.failureMessage(), now);
            throw new CoreException(ErrorType.PAYMENT_DECLINED, result.failureMessage());
        }

        return txService.approve(confirmable.paymentId(), result.pgTransactionId(), now);
    }
}
```

`CoreException`의 두 인자 생성자는 `OrderTerminationService`가 이미 쓰고 있으므로 존재한다. 시그니처가 다르면 그 사용례에 맞춘다.

- [ ] **Step 5: 테스트 통과를 확인한다**

Run: `.\gradlew.bat :core:core-domain:test --tests "com.ticket.core.domain.payment.command.*"`
Expected: PASS (Prepare 6 + Confirm 4)

- [ ] **Step 6: 커밋한다**

```bash
git add core/core-domain/src/main/java/com/ticket/core/domain/payment/command core/core-domain/src/test/java/com/ticket/core/domain/payment/command
git commit -m "feat(payment): 결제 승인 use case와 확정 트랜잭션 추가"
```

---

## Task 10: 결제 API

**Files:**
- Create: `core/core-api/src/main/java/com/ticket/core/api/controller/request/PreparePaymentRequest.java`
- Create: `core/core-api/src/main/java/com/ticket/core/api/controller/request/ConfirmPaymentRequest.java`
- Create: `core/core-api/src/main/java/com/ticket/core/api/controller/docs/PaymentControllerDocs.java`
- Create: `core/core-api/src/main/java/com/ticket/core/api/controller/PaymentController.java`
- Test: `core/core-api/src/test/java/com/ticket/core/api/controller/PaymentControllerContractTest.java`

`SecurityConfig`는 `anyRequest().authenticated()`이므로 새 경로를 따로 등록할 필요가 없다.

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`core/core-api/src/test/java/com/ticket/core/api/controller/PaymentControllerContractTest.java`

```java
package com.ticket.core.api.controller;

import com.ticket.core.config.security.MemberPrincipal;
import com.ticket.core.config.security.MemberPrincipalArgumentResolver;
import com.ticket.core.domain.payment.command.ConfirmPaymentUseCase;
import com.ticket.core.domain.payment.command.PreparePaymentUseCase;
import com.ticket.core.domain.payment.model.PaymentMethod;
import com.ticket.core.domain.payment.model.PaymentStatus;
import com.ticket.core.support.ApiControllerAdvice;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("NonAsciiCharacters")
class PaymentControllerContractTest {

    private static final MemberPrincipal MEMBER = new MemberPrincipal(100L, "MEMBER");
    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 8, 21, 10, 0);

    private final PreparePaymentUseCase preparePaymentUseCase = Mockito.mock(PreparePaymentUseCase.class);
    private final ConfirmPaymentUseCase confirmPaymentUseCase = Mockito.mock(ConfirmPaymentUseCase.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PaymentController(preparePaymentUseCase, confirmPaymentUseCase))
                .setCustomArgumentResolvers(new MemberPrincipalArgumentResolver())
                .setControllerAdvice(new ApiControllerAdvice())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(MEMBER, null, java.util.List.of())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 결제_준비_API는_201과_준비된_결제를_반환한다() throws Exception {
        when(preparePaymentUseCase.execute(any(PreparePaymentUseCase.Input.class)))
                .thenReturn(new PreparePaymentUseCase.Output(
                        "PAY-1", "order-key", BigDecimal.valueOf(120000), PaymentStatus.READY, FIXED_NOW.plusMinutes(5)
                ));

        mockMvc.perform(post("/api/v1/orders/order-key/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"CARD\",\"amount\":120000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.paymentKey").value("PAY-1"))
                .andExpect(jsonPath("$.data.status").value("READY"));

        verify(preparePaymentUseCase).execute(new PreparePaymentUseCase.Input(
                "order-key", 100L, PaymentMethod.CARD, BigDecimal.valueOf(120000)
        ));
    }

    @Test
    void 결제_승인_API는_200과_확정된_주문_상태를_반환한다() throws Exception {
        when(confirmPaymentUseCase.execute(any(ConfirmPaymentUseCase.Input.class)))
                .thenReturn(new ConfirmPaymentUseCase.Output(
                        "PAY-1", PaymentStatus.APPROVED, FIXED_NOW, "order-key", "CONFIRMED"
                ));

        mockMvc.perform(post("/api/v1/payments/PAY-1/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":120000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.orderStatus").value("CONFIRMED"));

        verify(confirmPaymentUseCase).execute(new ConfirmPaymentUseCase.Input(
                "PAY-1", 100L, BigDecimal.valueOf(120000)
        ));
    }

    @Test
    void 금액이_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/payments/PAY-1/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

Run: `.\gradlew.bat :core:core-api:test --tests "com.ticket.core.api.controller.PaymentControllerContractTest"`
Expected: 컴파일 실패 — `PaymentController` 없음

- [ ] **Step 3: 요청 DTO를 만든다**

`core/core-api/src/main/java/com/ticket/core/api/controller/request/PreparePaymentRequest.java`

```java
package com.ticket.core.api.controller.request;

import com.ticket.core.domain.payment.model.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Schema(description = "결제 준비 요청")
public class PreparePaymentRequest {

    @NotNull(message = "method는 null일 수 없습니다.")
    @Schema(description = "결제수단", example = "CARD")
    private PaymentMethod method;

    @NotNull(message = "amount는 null일 수 없습니다.")
    @Positive(message = "amount는 양수여야 합니다.")
    @Schema(description = "결제 금액", example = "120000")
    private BigDecimal amount;

    public PreparePaymentRequest() {
    }
}
```

`core/core-api/src/main/java/com/ticket/core/api/controller/request/ConfirmPaymentRequest.java`

```java
package com.ticket.core.api.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Schema(description = "결제 승인 요청")
public class ConfirmPaymentRequest {

    @NotNull(message = "amount는 null일 수 없습니다.")
    @Positive(message = "amount는 양수여야 합니다.")
    @Schema(description = "결제 금액", example = "120000")
    private BigDecimal amount;

    public ConfirmPaymentRequest() {
    }
}
```

- [ ] **Step 4: 문서 인터페이스를 만든다**

`core/core-api/src/main/java/com/ticket/core/api/controller/docs/PaymentControllerDocs.java`

```java
package com.ticket.core.api.controller.docs;

import com.ticket.core.api.controller.request.ConfirmPaymentRequest;
import com.ticket.core.api.controller.request.PreparePaymentRequest;
import com.ticket.core.config.security.MemberPrincipal;
import com.ticket.core.domain.payment.command.ConfirmPaymentUseCase;
import com.ticket.core.domain.payment.command.PreparePaymentUseCase;
import com.ticket.core.support.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "결제", description = "결제 준비와 승인 API")
public interface PaymentControllerDocs {

    @Operation(
            summary = "결제 준비",
            description = """
                    PENDING 주문에 대해 READY 상태 결제를 만들고 paymentKey를 발급한다.
                    같은 주문에 READY 결제가 이미 있으면 그 결제를 그대로 반환한다.
                    """
    )
    ResponseEntity<ApiResponse<PreparePaymentUseCase.Output>> preparePayment(
            String orderKey,
            PreparePaymentRequest request,
            MemberPrincipal memberPrincipal
    );

    @Operation(
            summary = "결제 승인",
            description = """
                    결제를 승인하고 주문을 CONFIRMED로, 회차 좌석을 RESERVED로 전이시킨다.
                    이미 승인된 결제를 다시 호출하면 같은 결과를 반환한다.
                    승인이 거절되면 결제만 FAILED가 되고 주문은 PENDING으로 남는다.
                    """
    )
    ApiResponse<ConfirmPaymentUseCase.Output> confirmPayment(
            String paymentKey,
            ConfirmPaymentRequest request,
            MemberPrincipal memberPrincipal
    );
}
```

- [ ] **Step 5: 컨트롤러를 만든다**

`core/core-api/src/main/java/com/ticket/core/api/controller/PaymentController.java`

```java
package com.ticket.core.api.controller;

import com.ticket.core.api.controller.docs.PaymentControllerDocs;
import com.ticket.core.api.controller.request.ConfirmPaymentRequest;
import com.ticket.core.api.controller.request.PreparePaymentRequest;
import com.ticket.core.config.security.MemberPrincipal;
import com.ticket.core.domain.payment.command.ConfirmPaymentUseCase;
import com.ticket.core.domain.payment.command.PreparePaymentUseCase;
import com.ticket.core.support.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PaymentController implements PaymentControllerDocs {

    private final PreparePaymentUseCase preparePaymentUseCase;
    private final ConfirmPaymentUseCase confirmPaymentUseCase;

    @Override
    @PostMapping("/orders/{orderKey}/payments")
    public ResponseEntity<ApiResponse<PreparePaymentUseCase.Output>> preparePayment(
            @PathVariable final String orderKey,
            @Valid @RequestBody final PreparePaymentRequest request,
            final MemberPrincipal memberPrincipal
    ) {
        final PreparePaymentUseCase.Output output = preparePaymentUseCase.execute(
                new PreparePaymentUseCase.Input(
                        orderKey,
                        memberPrincipal.getMemberId(),
                        request.getMethod(),
                        request.getAmount()
                )
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(output));
    }

    @Override
    @PostMapping("/payments/{paymentKey}/confirm")
    public ApiResponse<ConfirmPaymentUseCase.Output> confirmPayment(
            @PathVariable final String paymentKey,
            @Valid @RequestBody final ConfirmPaymentRequest request,
            final MemberPrincipal memberPrincipal
    ) {
        final ConfirmPaymentUseCase.Output output = confirmPaymentUseCase.execute(
                new ConfirmPaymentUseCase.Input(
                        paymentKey,
                        memberPrincipal.getMemberId(),
                        request.getAmount()
                )
        );
        return ApiResponse.success(output);
    }
}
```

`ApiResponse.success`의 시그니처와 `MemberPrincipal.getMemberId()`는 `OrderController`가 쓰는 형태를 그대로 따른다. 다르면 그쪽에 맞춘다.

- [ ] **Step 6: 테스트 통과를 확인한다**

Run: `.\gradlew.bat :core:core-api:test --tests "com.ticket.core.api.controller.PaymentControllerContractTest"`
Expected: PASS (3 tests)

- [ ] **Step 7: 커밋한다**

```bash
git add core/core-api/src/main/java/com/ticket/core/api/controller core/core-api/src/test/java/com/ticket/core/api/controller/PaymentControllerContractTest.java
git commit -m "feat(payment): 결제 준비와 승인 API 추가"
```

---

## Task 11: 문서 갱신과 전체 검증

**Files:**
- Modify: `docs/core-booking-lifecycle.md`

- [ ] **Step 1: 수명주기 문서에 확정 절을 추가한다**

`## 주문 취소와 만료` 절의 끝(문단 마지막 줄) 뒤, `## TTL 폭주와 보정` 앞에 아래 내용을 넣는다.

    ## 결제 확정

    결제는 준비와 승인 2단계다. 준비는 PENDING 주문에 READY 결제를 만들고, 승인은 게이트웨이 응답을
    받은 뒤 상태 전이를 한 트랜잭션에서 처리한다.

    ~~~text
    ConfirmPaymentUseCase
      -> 결제/주문 검증                (짧은 read transaction)
      -> 게이트웨이 승인 요청           (DB 트랜잭션 밖)
      -> PaymentConfirmationTxService  (짧은 DB 트랜잭션)
           -> 결제 row lock, APPROVED 전이
           -> PENDING 주문 row lock
           -> OrderConfirmationService
                -> 주문 좌석 검증
                -> CONFIRMED 전이
                -> 회차 좌석 RESERVED 전이
                -> hold history 저장 (CONFIRMED, PAYMENT_CONFIRMED)
                -> hold release outbox 저장 (reason=PAYMENT_CONFIRMED)
      -> DB 커밋 및 connection 반환
      -> 이후는 취소·만료와 같은 경로
           -> Redis hold 해제
           -> reason이 PAYMENT_CONFIRMED이면 좌석 이벤트를 발행하지 않는다
    ~~~

    확정 시점부터 `PERFORMANCE_SEATS.state`가 영구 점유의 기준이 된다. 선점 중 좌석은 이미 점유로
    보이고 있고 클라이언트는 `HELD`와 `RESERVED`를 같은 점유 상태로 취급하므로, 확정 시 새로 발행할
    좌석 이벤트는 없다. 확정 경로가 지켜야 할 것은 `RELEASED`를 발행하지 않는 것이다.

    승인이 거절되면 결제만 FAILED가 되고 주문과 hold는 그대로 남는다. 사용자는 만료 전까지 준비부터
    다시 시도할 수 있고, 시한을 넘기면 기존 만료 경로가 정리한다.

(위 블록은 들여쓰기를 제거한 상태로 문서에 넣는다.)

`## 주요 코드` 절의 목록에 두 줄을 추가한다.

```markdown
- 결제 준비: domain.payment.command.PreparePaymentUseCase
- 결제 승인: domain.payment.command.ConfirmPaymentUseCase, domain.order.command.OrderConfirmationService
```

- [ ] **Step 2: 구조 테스트를 돌린다**

새 패키지(`domain.payment`, `infra.payment`)가 아키텍처 규칙을 위반하지 않는지 확인한다.

Run:
```
.\gradlew.bat :core:core-domain:test --tests "com.ticket.core.domain.CoreDomainArchitectureTest" --tests "com.ticket.core.domain.CoreDomainModuleStructureTest"
.\gradlew.bat :core:core-api:test --tests "com.ticket.core.CoreApiArchitectureTest"
```
Expected: 모두 PASS

- [ ] **Step 3: 전체 검증을 돌린다**

Run: `.\gradlew.bat test :core:core-api:bootJar`
Expected: BUILD SUCCESSFUL

`:core:core-infra:integrationTest`는 Docker가 필요하다. Redis 경로를 바꾸지 않았으므로 필수는 아니지만, 돌리지 않았다면 보고에 그 사실을 적는다.

- [ ] **Step 4: 커밋한다**

```bash
git add docs/core-booking-lifecycle.md
git commit -m "docs: 결제 확정 흐름을 예매 수명주기 문서에 추가"
```

- [ ] **Step 5: 결과를 보고한다**

`docs/testing.md`의 보고 기준을 따른다. 통과·실패 수를 그대로 적고, 돌리지 않은 범위(통합 테스트 등)를 밝힌다.

---

## 수동 확인 (선택)

로컬에서 데이터 전이를 눈으로 보려면 `local` 프로파일로 띄운다. 이 프로파일은 `ddl-auto: create`라 Flyway 없이 `PAYMENTS`가 생성된다.

1. 주문 생성 → `POST /api/v1/orders`
2. 결제 준비 → `POST /api/v1/orders/{orderKey}/payments`
3. 결제 승인 → `POST /api/v1/payments/{paymentKey}/confirm`
4. 확인 쿼리

```sql
SELECT status, approved_at FROM PAYMENTS WHERE payment_key = '...';
SELECT status, confirmed_at FROM ORDERS WHERE order_key = '...';
SELECT state FROM PERFORMANCE_SEATS WHERE id IN (...);
SELECT event_type, release_reason FROM HOLD_HISTORY WHERE hold_key = '...';
SELECT reason, status, hold_released_at FROM ORDER_HOLD_RELEASE_OUTBOX ORDER BY id DESC;
```

거절 경로는 `ticket.payment.fake.approve: false`로 띄운 뒤 같은 순서로 호출하고, `PAYMENTS.status = FAILED`와 `ORDERS.status = PENDING`을 확인한다.
