-- ticket-domain-module-redesign Phase 2 Task 3(ADR 0005): Seat에 필수 Venue 연관관계를 추가한다.
-- BC(Bounded Context) 재편으로 Seat·Venue가 show(옛 catalog)에서 venue module로 옮겨지며, 옛
-- catalog/V3(개명 후 show/V3)였던 이 파일도 venue module의 새 Flyway 이력(V1부터 다시 시작)으로
-- 옮겨왔다. Flyway의 모듈별 이력 테이블(flyway_schema_history_venue)은 이 module 이름으로는
-- 처음 실행되므로, 이미 이 migration이 적용된 환경(옛 catalog/show 이력으로)에 다시 실행돼도
-- ORA-01430(컬럼 중복)/ORA-01442(이미 NOT NULL)/ORA-02275(FK 중복)/ORA-00955(인덱스 중복) 없이
-- 안전하도록 전 구문을 존재 확인 가드로 감쌌다 — 아직 적용되지 않은 신규 환경에서는 원래 동작과
-- 동일하다.
--
-- 기존 SEATS row는 Flyway 이전 baseline(Hibernate ddl-auto=create가 만든) synthetic 데이터라 실제
-- Venue 소속을 추론할 근거가 없다(ADR 0005 "데이터 전환" 참고, 재생성 전략). 이미 있는 row는
-- VENUES의 첫 행에 임시로 배정해 NOT NULL 제약을 만족시킨다 — 그 값 자체가 실제 소속이라는
-- 뜻은 아니고, 이후 seed가 Venue별로 결정적으로 재생성한 row가 실제 배정을 대체한다. VENUES가
-- 아직 비어 있는 환경(SEATS도 비어 있다)에서는 이 UPDATE가 no-op이다.
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tab_columns
    WHERE table_name = 'SEATS' AND column_name = 'VENUE_ID';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE SEATS ADD venue_id NUMBER(19, 0)';
    END IF;
END;
/

UPDATE SEATS
SET venue_id = (SELECT MIN(id) FROM VENUES)
WHERE venue_id IS NULL;

DECLARE
    v_nullable VARCHAR2(1);
BEGIN
    SELECT nullable INTO v_nullable FROM user_tab_columns
    WHERE table_name = 'SEATS' AND column_name = 'VENUE_ID';
    IF v_nullable = 'Y' THEN
        EXECUTE IMMEDIATE 'ALTER TABLE SEATS MODIFY (venue_id NOT NULL)';
    END IF;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_constraints
    WHERE table_name = 'SEATS' AND constraint_name = 'FK_SEATS_VENUE';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE SEATS ADD CONSTRAINT FK_SEATS_VENUE FOREIGN KEY (venue_id) REFERENCES VENUES (id)';
    END IF;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_indexes WHERE index_name = 'UK_SEATS_VENUE_SEAT_ADDRESS';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX UK_SEATS_VENUE_SEAT_ADDRESS ON SEATS (venue_id, floor, section, row_no, seat_no)';
    END IF;
END;
/
