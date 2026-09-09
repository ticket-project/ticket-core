-- ADR 0008: 찜 대상을 값(LikeType)으로 일반화한다. SHOW_LIKES(show_id 전용) -> LIKES(대상 종류
-- like_type + target_id). 지금은 공연만 찜 대상이라 기존 행 전부에 'SHOW'를 backfill한다.
--
-- SHOWS/SHOW_LIKES는 어떤 Flyway migration도 만들지 않은 pre-Flyway baseline table이다
-- (docs/operations.md 참고). local(ddl-auto=create) 환경에서는 Hibernate가 이미
-- LIKES(target_id, like_type 포함)를 만들어 두므로 이 migration은 그 환경에서 no-op이어야
-- 한다 — 매 단계마다 "아직 옛 상태인가"를 확인하고 아니면 손대지 않는다.

-- 1) table rename: SHOW_LIKES가 있고 LIKES가 아직 없을 때만.
DECLARE
    v_show_likes_count NUMBER;
    v_likes_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_show_likes_count FROM user_tables WHERE table_name = 'SHOW_LIKES';
    SELECT COUNT(*) INTO v_likes_count FROM user_tables WHERE table_name = 'LIKES';
    IF v_show_likes_count > 0 AND v_likes_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE SHOW_LIKES RENAME TO LIKES';
    END IF;
END;
/

-- 2) column rename: LIKES.SHOW_ID가 남아있을 때만.
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tab_columns
    WHERE table_name = 'LIKES' AND column_name = 'SHOW_ID';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE LIKES RENAME COLUMN SHOW_ID TO TARGET_ID';
    END IF;
END;
/

-- 3) like_type 컬럼 신설 + backfill + NOT NULL. 컬럼이 이미 있으면(Hibernate가 만들었거나 이전
-- 실행에서 이미 추가됐으면) 전부 no-op이다.
DECLARE
    v_table_count NUMBER;
    v_column_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_table_count FROM user_tables WHERE table_name = 'LIKES';
    SELECT COUNT(*) INTO v_column_count FROM user_tab_columns
    WHERE table_name = 'LIKES' AND column_name = 'LIKE_TYPE';
    IF v_table_count > 0 AND v_column_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE LIKES ADD (LIKE_TYPE VARCHAR2(20))';
        EXECUTE IMMEDIATE 'UPDATE LIKES SET LIKE_TYPE = ''SHOW'' WHERE LIKE_TYPE IS NULL';
        EXECUTE IMMEDIATE 'ALTER TABLE LIKES MODIFY (LIKE_TYPE VARCHAR2(20) NOT NULL)';
    END IF;
END;
/

-- 4) UK 재설정: UK_SHOW_LIKES_MEMBER_SHOW(member_id, show_id) -> UK_LIKES_MEMBER_TARGET(member_id,
-- like_type, target_id). 옛 이름 제약이 남아있을 때만 지우고, 새 이름 제약이 아직 없을 때만 만든다.
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_constraints
    WHERE table_name = 'LIKES' AND constraint_name = 'UK_SHOW_LIKES_MEMBER_SHOW';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE LIKES DROP CONSTRAINT UK_SHOW_LIKES_MEMBER_SHOW';
    END IF;
END;
/

DECLARE
    v_table_count NUMBER;
    v_constraint_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_table_count FROM user_tables WHERE table_name = 'LIKES';
    SELECT COUNT(*) INTO v_constraint_count FROM user_constraints
    WHERE table_name = 'LIKES' AND constraint_name = 'UK_LIKES_MEMBER_TARGET';
    IF v_table_count > 0 AND v_constraint_count = 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE LIKES ADD CONSTRAINT UK_LIKES_MEMBER_TARGET UNIQUE (member_id, like_type, target_id)';
    END IF;
END;
/
