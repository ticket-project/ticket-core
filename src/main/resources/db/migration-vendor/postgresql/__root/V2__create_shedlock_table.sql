-- ShedLock 락 테이블. 인스턴스가 여러 대여도 주기 작업(@SchedulerLock)을 한 대만 실행하게 한다.
-- 어느 업무 module에도 속하지 않는 기술 테이블이라 __root가 소유한다. 컬럼은 ShedLock JdbcTemplateLockProvider의 기본 형식이다.
CREATE TABLE shedlock(
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at TIMESTAMP(3) NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);

ALTER TABLE shedlock ADD CONSTRAINT pk_shedlock PRIMARY KEY(name);
