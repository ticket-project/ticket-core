-- ADR 0006 "Performance의 책임 혼재" A2(2026-09-07 이후 후속 구현): 예매 접수 기간·Hold 한도·대기열
-- 진입 정책의 원본과 판단을 Booking BC로 이관한다. BOOKING_PERFORMANCE_SALES_POLICIES가 그 정책
-- table이고, performance_id는 show가 소유한 PERFORMANCES에 대한 scalar 식별자일 뿐 cross-module FK가
-- 아니다.
--
-- 이 booking migration은 예외적으로 show/__root 소유였던 PERFORMANCE_QUEUE_POLICIES(__root V2)와
-- PERFORMANCES의 정책 컬럼 4개(pre-Flyway baseline)를 읽고 제거한다 — 정책 소유권 이관(ownership
-- handoff)이라는 일회성 예외이며, 근거는 docs/architecture.md와 docs/operations.md의 "DB
-- 마이그레이션" 절에 기록했다. create -> backfill -> drop을 한 파일 안에서 원자적으로 수행하고,
-- show/booking의 독립 migration 실행 순서에 기대지 않는다.
--
-- 컬럼 존재 확인 가드: PERFORMANCES.order_open_time이 없는 환경(BookingModuleSlicingSchemaTest,
-- OracleMigrationCompatibilityTest 등 booking 자신의 migration만 적용하는 최소 legacy baseline)에서는
-- backfill/drop이 no-op이다 — 이 넷(정책 컬럼 4개 + PERFORMANCE_QUEUE_POLICIES)은 실제 운영/개발
-- 환경에서는 항상 함께 존재했다(__root V2가 PERFORMANCE_QUEUE_POLICIES를 만들 때부터).

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tables WHERE table_name = 'BOOKING_PERFORMANCE_SALES_POLICIES';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE TABLE BOOKING_PERFORMANCE_SALES_POLICIES (
                performance_id NUMBER(19, 0) NOT NULL PRIMARY KEY,
                order_opens_at TIMESTAMP NOT NULL,
                order_closes_at TIMESTAMP NOT NULL,
                max_hold_seat_count NUMBER(10, 0),
                hold_duration_seconds NUMBER(19, 0) NOT NULL,
                queue_mode VARCHAR2(20 CHAR),
                queue_level VARCHAR2(20 CHAR),
                preopen_queue_starts_at TIMESTAMP,
                waiting_room_message VARCHAR2(255 CHAR),
                queue_policy_reason VARCHAR2(255 CHAR),
                version NUMBER(19, 0) DEFAULT 0 NOT NULL,
                created_at TIMESTAMP NOT NULL,
                created_by VARCHAR2(255 CHAR) NOT NULL,
                updated_at TIMESTAMP,
                updated_by VARCHAR2(255 CHAR),
                CONSTRAINT ck_booking_perf_sales_policies_window CHECK (order_opens_at < order_closes_at),
                CONSTRAINT ck_booking_perf_sales_policies_max_hold CHECK (max_hold_seat_count IS NULL OR max_hold_seat_count >= 2),
                CONSTRAINT ck_booking_perf_sales_policies_hold_duration CHECK (hold_duration_seconds > 0)
            )';
    END IF;
END;
/

-- Backfill: PERFORMANCES.order_open_time/order_close_time이 둘 다 null인 회차는 "정책 미구성"이라
-- 정책 row를 만들지 않는다(WHERE 절). 한쪽만 null이면 대상 컬럼(order_opens_at/order_closes_at)이
-- NOT NULL이라 INSERT 자체가 실패해 migration이 멈춘다 — 원본 데이터를 먼저 확인하게 하려는 의도다.
-- opens_at >= closes_at인 데이터도 CHECK 제약 위반으로 같은 이유로 실패한다. hold_time이 null이면
-- Performance.holdTime의 기존 Java 기본값(600초)을 그대로 적용한다(BookingPerformanceSalesPolicyMigrationTest가
-- 이 결정을 고정한다).
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tab_columns
    WHERE table_name = 'PERFORMANCES' AND column_name = 'ORDER_OPEN_TIME';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE '
            INSERT INTO BOOKING_PERFORMANCE_SALES_POLICIES (
              performance_id, order_opens_at, order_closes_at, max_hold_seat_count, hold_duration_seconds,
              queue_mode, queue_level, preopen_queue_starts_at, waiting_room_message, queue_policy_reason,
              version, created_at, created_by
            )
            SELECT p.id, p.order_open_time, p.order_close_time, p.max_can_hold_count,
                   COALESCE(p.hold_time, 600), q.queue_mode, q.queue_level, q.preopen_queue_start_at,
                   q.waiting_room_message, q.reason, 0, CURRENT_TIMESTAMP, ''MIGRATION_V6_BACKFILL''
            FROM PERFORMANCES p
            LEFT JOIN PERFORMANCE_QUEUE_POLICIES q ON q.performance_id = p.id
            WHERE p.order_open_time IS NOT NULL OR p.order_close_time IS NOT NULL';
    END IF;
END;
/

-- Backfill 검증 후 구 schema를 정리한다: PERFORMANCE_QUEUE_POLICIES와 PERFORMANCES의 정책 컬럼 4개.
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tables WHERE table_name = 'PERFORMANCE_QUEUE_POLICIES';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'DROP TABLE PERFORMANCE_QUEUE_POLICIES';
    END IF;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tab_columns
    WHERE table_name = 'PERFORMANCES' AND column_name = 'ORDER_OPEN_TIME';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE PERFORMANCES DROP COLUMN order_open_time';
    END IF;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tab_columns
    WHERE table_name = 'PERFORMANCES' AND column_name = 'ORDER_CLOSE_TIME';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE PERFORMANCES DROP COLUMN order_close_time';
    END IF;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tab_columns
    WHERE table_name = 'PERFORMANCES' AND column_name = 'MAX_CAN_HOLD_COUNT';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE PERFORMANCES DROP COLUMN max_can_hold_count';
    END IF;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tab_columns
    WHERE table_name = 'PERFORMANCES' AND column_name = 'HOLD_TIME';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE PERFORMANCES DROP COLUMN hold_time';
    END IF;
END;
/
