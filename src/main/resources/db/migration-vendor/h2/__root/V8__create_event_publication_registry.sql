-- Spring Modulith 2.1.1 JPA event publication registry(spring-modulith-events-jpa).
-- DDL은 Hibernate 7.4.5 + Boot 4.1.1 기본 naming strategy(SpringImplicitNamingStrategy,
-- PhysicalNamingStrategySnakeCaseImpl)로 실제 schema export를 실행해 캡처한 결과를 옮긴 것이다
-- (추측 아님). serialized_event는 @Lob이 없어 CLOB이 아니라 VARCHAR(255)로 매핑된다.
CREATE TABLE EVENT_PUBLICATION (
    id UUID NOT NULL,
    publication_date TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    listener_id VARCHAR(255) NOT NULL,
    serialized_event VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    completion_date TIMESTAMP(6) WITH TIME ZONE,
    last_resubmission_date TIMESTAMP(6) WITH TIME ZONE,
    completion_attempts INTEGER NOT NULL,
    status ENUM ('PUBLISHED', 'PROCESSING', 'COMPLETED', 'FAILED', 'RESUBMITTED'),
    CONSTRAINT pk_event_publication PRIMARY KEY (id)
);

CREATE TABLE EVENT_PUBLICATION_ARCHIVE (
    id UUID NOT NULL,
    publication_date TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    listener_id VARCHAR(255) NOT NULL,
    serialized_event VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    completion_date TIMESTAMP(6) WITH TIME ZONE,
    last_resubmission_date TIMESTAMP(6) WITH TIME ZONE,
    completion_attempts INTEGER NOT NULL,
    status ENUM ('PUBLISHED', 'PROCESSING', 'COMPLETED', 'FAILED', 'RESUBMITTED'),
    CONSTRAINT pk_event_publication_archive PRIMARY KEY (id)
);
