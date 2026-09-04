-- ticket-domain-module-redesign Phase 3 Task 6(ADR 0005): PerformanceSeat entity가
-- performanceGradeId/unitPrice를 매핑하고 @Version 낙관적 락을 쓰도록 바뀌었다. 두 컬럼은 catalog
-- V6가 이미 nullable로 추가했으므로(운영/전체 migration 순서에서는 catalog가 booking보다 먼저
-- 실행된다) 보통은 존재하지만, 이 migration만(또는 catalog와만) 단독으로 도는 환경(module slicing·
-- Oracle 호환성 검증)을 대비해 없으면 먼저(nullable로) 만든 뒤 NOT NULL로 조인다. 옛 `price` 컬럼은
-- 이 migration이 전제하지 않는다 — 값이 없으면 0으로 채운다.
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tab_columns
    WHERE table_name = 'PERFORMANCE_SEATS' AND column_name = 'PERFORMANCE_GRADE_ID';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE performance_seats ADD performance_grade_id NUMBER(19, 0)';
    END IF;
END;
/

UPDATE performance_seats SET performance_grade_id = 0 WHERE performance_grade_id IS NULL;
ALTER TABLE performance_seats MODIFY (performance_grade_id NOT NULL);

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tab_columns
    WHERE table_name = 'PERFORMANCE_SEATS' AND column_name = 'UNIT_PRICE';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE performance_seats ADD unit_price NUMBER(19, 2)';
    END IF;
END;
/

UPDATE performance_seats SET unit_price = 0 WHERE unit_price IS NULL;
ALTER TABLE performance_seats MODIFY (unit_price NOT NULL);
ALTER TABLE performance_seats ADD CONSTRAINT ck_performance_seats_unit_price CHECK (unit_price >= 0);

-- @Version 낙관적 락. 이 컬럼은 이번에 처음 추가하므로 존재 여부를 확인할 필요가 없다.
ALTER TABLE performance_seats ADD version NUMBER(19, 0) DEFAULT 0 NOT NULL;

CREATE INDEX idx_performance_seats_grade ON performance_seats (performance_grade_id);
