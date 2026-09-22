-- 공연은 공연장을 항상 갖는다. 좌석은 venue가 소유하고 ORDERS.VENUE_NAME_SNAPSHOT이 NOT NULL이라,
-- 공연장 없는 공연은 애초에 팔 수 없다(booking의 PendingOrderCreator 참고). 그런데 VENUE_ID가
-- nullable이어서 "등록은 되지만 절대 팔 수 없는 공연"이 만들어질 수 있었다. 그 상태를 스키마에서
-- 막는다.
--
-- 값을 임의로 채우지 않는다. 존재하지 않는 공연장을 가리키는 dangling id를 만드는 쪽이 nullable을
-- 남기는 것보다 나쁘다(booking/V6의 원칙). 큐레이션 시드는 SHOWS 전건에 VENUE_ID를 채우므로
-- 백필이 필요 없고, 잔여 NULL이 있으면 이 ALTER가 ORA-02296으로 실패해 원본을 먼저 확인하게 한다.
-- 운영 반영 전 SELECT COUNT(*) FROM shows WHERE venue_id IS NULL 이 0인지 확인한다.
--
-- SHOWS는 어떤 Flyway migration도 만들지 않은 pre-Flyway baseline table이다(docs/operations.md
-- 참고). local(ddl-auto=create) 환경과 module 단독 실행 검증 baseline에는 컬럼이 없을 수 있으므로,
-- V9와 같은 형태로 존재할 때만 조인다.

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tab_columns
    WHERE table_name = 'SHOWS' AND column_name = 'VENUE_ID' AND nullable = 'Y';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE SHOWS MODIFY (VENUE_ID NOT NULL)';
    END IF;
END;
/
