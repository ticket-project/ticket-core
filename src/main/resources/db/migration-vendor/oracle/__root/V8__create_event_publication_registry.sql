-- Spring Modulith 2.1.1 JPA event publication registry(spring-modulith-events-jpa).
-- DDL은 Hibernate 7.4.5 + Boot 4.1.1 기본 naming strategy(SpringImplicitNamingStrategy,
-- PhysicalNamingStrategySnakeCaseImpl)로 실제 schema export를 실행해 캡처한 결과를 옮긴 것이다
-- (추측 아님). serialized_event는 @Lob이 없어 CLOB이 아니라 VARCHAR2(255 CHAR)로 매핑된다.
CREATE TABLE EVENT_PUBLICATION (
    id RAW(16) NOT NULL,
    publication_date TIMESTAMP(9) WITH TIME ZONE NOT NULL,
    listener_id VARCHAR2(255 CHAR) NOT NULL,
    serialized_event VARCHAR2(255 CHAR) NOT NULL,
    event_type VARCHAR2(255 CHAR) NOT NULL,
    completion_date TIMESTAMP(9) WITH TIME ZONE,
    last_resubmission_date TIMESTAMP(9) WITH TIME ZONE,
    completion_attempts NUMBER(10, 0) NOT NULL,
    status VARCHAR2(255 CHAR),
    CONSTRAINT pk_event_publication PRIMARY KEY (id),
    CONSTRAINT ck_event_publication_status
        CHECK (status IN ('PUBLISHED', 'PROCESSING', 'COMPLETED', 'FAILED', 'RESUBMITTED'))
);

CREATE TABLE EVENT_PUBLICATION_ARCHIVE (
    id RAW(16) NOT NULL,
    publication_date TIMESTAMP(9) WITH TIME ZONE NOT NULL,
    listener_id VARCHAR2(255 CHAR) NOT NULL,
    serialized_event VARCHAR2(255 CHAR) NOT NULL,
    event_type VARCHAR2(255 CHAR) NOT NULL,
    completion_date TIMESTAMP(9) WITH TIME ZONE,
    last_resubmission_date TIMESTAMP(9) WITH TIME ZONE,
    completion_attempts NUMBER(10, 0) NOT NULL,
    status VARCHAR2(255 CHAR),
    CONSTRAINT pk_event_publication_archive PRIMARY KEY (id),
    CONSTRAINT ck_event_pub_archive_status
        CHECK (status IN ('PUBLISHED', 'PROCESSING', 'COMPLETED', 'FAILED', 'RESUBMITTED'))
);
