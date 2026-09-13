-- serialized_event를 VARCHAR(255)에서 넓힌다.
--
-- V8은 Hibernate 기본 매핑(@Lob 없는 String -> VARCHAR(255))을 그대로 캡처했지만, 실제
-- OrderTerminated 직렬화 결과는 이 길이를 넘는다. eventId(UUID 36) + holdKey("HOLD-" + 32) +
-- performanceSeatIds 배열 + ISO-8601 Instant만으로 이미 255자를 초과해 주문 취소·만료가
-- publication 저장 단계에서 실패한다.
--
-- CLOB으로 바꾸지 않는다. Spring Modulith의 JpaEventPublicationRepository가 완료·재제출 처리에서
-- `where p.serialized_event = ?`로 이 컬럼을 동등 비교하는데 Oracle은 CLOB의 `=` 비교를 허용하지
-- 않는다(ORA-00932). 또 dev/prod는 ddl-auto=validate라 JDBC 타입이 VARCHAR에서 바뀌면 기동이
-- 막힌다. VARCHAR 길이만 넓히면 Hibernate 스키마 검증은 타입 코드만 보므로 영향이 없다.
ALTER TABLE EVENT_PUBLICATION ALTER COLUMN serialized_event VARCHAR(4000) NOT NULL;
ALTER TABLE EVENT_PUBLICATION_ARCHIVE ALTER COLUMN serialized_event VARCHAR(4000) NOT NULL;
