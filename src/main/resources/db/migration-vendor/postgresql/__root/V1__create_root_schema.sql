-- PostgreSQL 전환 시 새 DB에 적용하는 __root 소유 초기 스키마다.
-- 기존 H2/Oracle 전체 migration 결과의 컬럼·제약을 보존하며, 과거 Flyway 이력은 가져오지 않는다.

CREATE TABLE event_publication(
    id UUID NOT NULL,
    publication_date TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    listener_id VARCHAR(255) NOT NULL,
    serialized_event VARCHAR(4000) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    completion_date TIMESTAMP(6) WITH TIME ZONE,
    last_resubmission_date TIMESTAMP(6) WITH TIME ZONE,
    completion_attempts INTEGER NOT NULL,
    status VARCHAR(255) CHECK (status IN ('PUBLISHED', 'PROCESSING', 'COMPLETED', 'FAILED', 'RESUBMITTED'))
);

ALTER TABLE event_publication ADD CONSTRAINT pk_event_publication PRIMARY KEY(id);

CREATE TABLE event_publication_archive(
    id UUID NOT NULL,
    publication_date TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    listener_id VARCHAR(255) NOT NULL,
    serialized_event VARCHAR(4000) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    completion_date TIMESTAMP(6) WITH TIME ZONE,
    last_resubmission_date TIMESTAMP(6) WITH TIME ZONE,
    completion_attempts INTEGER NOT NULL,
    status VARCHAR(255) CHECK (status IN ('PUBLISHED', 'PROCESSING', 'COMPLETED', 'FAILED', 'RESUBMITTED'))
);

ALTER TABLE event_publication_archive ADD CONSTRAINT pk_event_publication_archive PRIMARY KEY(id);
