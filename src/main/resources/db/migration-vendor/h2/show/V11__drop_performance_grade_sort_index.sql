-- PK·유니크가 아닌 보조 인덱스를 당분간 두지 않는다(사용자 결정, 2026-09-29). 필요해지면 새
-- migration으로 다시 만든다. performance_id 조회는 uk_performance_grades_performance_grade
-- (performance_id, grade_id)가 남아 받친다.
DROP INDEX IF EXISTS idx_performance_grades_perf_sort;
