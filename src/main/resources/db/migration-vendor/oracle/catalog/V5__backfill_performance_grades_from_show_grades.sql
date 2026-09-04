-- ticket-domain-module-redesign Phase 2 Task 5(ADR 0005): 기존 SHOW_GRADES(Show 단위 등급 가격)를
-- 같은 Show의 모든 Performance에 대한 PERFORMANCE_GRADES row로 복제해 PerformanceGrade가 가격의
-- 원본이 되도록 준비한다. SHOW_GRADES/SHOW_SEATS table은 아직 drop하지 않는다(Task 8에서 한다).
--
-- GRADES는 재사용 가능한 등급 코드/이름만 갖는다(code UNIQUE). 같은 grade_code가 여러 Show에서
-- 서로 다른 grade_name으로 나타나도 GRADES row는 code당 하나만 만든다 -- 대표 이름은
-- MIN(grade_name)으로 결정론적으로 고른다.
--
-- SHOWS/PERFORMANCES/SHOW_GRADES는 어떤 Flyway migration도 만들지 않은 pre-Flyway baseline이다
-- (docs/operations.md 참고). OracleMigrationCompatibilityTest의 legacy baseline은 SHOW_GRADES를
-- 아직 모르는 상태를 재현하므로, 이 migration은 catalog V1(동적 FK 제거)과 같은 PL/SQL 패턴으로
-- SHOW_GRADES 존재 여부를 먼저 확인하고 없으면 no-op이다.
--
-- **알려진 한계**: Flyway migration은 애플리케이션 기동 시 seed(SeedDataLoader)보다 먼저 실행된다.
-- 따라서 이 migration이 실행되는 시점에 SHOW_GRADES가 비어 있는 신규/로컬 환경(seed를 아직 돌리지
-- 않은 환경)에서는 이 backfill이 no-op이고, 이후 seed가 넣는 SHOW_GRADES는 이 migration이 다시
-- 자동으로 반영하지 않는다. 이미 SHOW_GRADES 데이터가 있는 환경(예: 이번 migration 배포 시점의
-- 운영/개발 DB)에 대해서만 유효한 1회성 backfill이다. 이 한계와 재실행 시 결과가 일치함은
-- ShowGradePerformanceGradeBackfillMigrationTest(H2)가 검증한다.
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tables WHERE table_name = 'SHOW_GRADES';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE '
            INSERT INTO GRADES (code, name, created_at, created_by)
            SELECT dedup.grade_code, dedup.grade_name, CURRENT_TIMESTAMP, ''MIGRATION_V5_BACKFILL''
            FROM (
                SELECT grade_code, MIN(grade_name) AS grade_name
                FROM SHOW_GRADES
                GROUP BY grade_code
            ) dedup
            WHERE NOT EXISTS (
                SELECT 1 FROM GRADES g WHERE g.code = dedup.grade_code
            )';

        EXECUTE IMMEDIATE '
            INSERT INTO PERFORMANCE_GRADES (performance_id, grade_id, price, sort_order, created_at, created_by)
            SELECT p.id, g.id, sg.price, sg.sort_order, CURRENT_TIMESTAMP, ''MIGRATION_V5_BACKFILL''
            FROM PERFORMANCES p
            JOIN SHOW_GRADES sg ON sg.show_id = p.show_id
            JOIN GRADES g ON g.code = sg.grade_code
            WHERE NOT EXISTS (
                SELECT 1 FROM PERFORMANCE_GRADES pg
                WHERE pg.performance_id = p.id AND pg.grade_id = g.id
            )';
    END IF;
END;
/
