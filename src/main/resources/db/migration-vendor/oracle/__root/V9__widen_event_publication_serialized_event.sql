-- serialized_event를 VARCHAR2(255 CHAR)에서 넓힌다. 배경은 같은 버전의 H2 migration 주석 참고.
--
-- CLOB으로 바꾸지 않는 이유가 Oracle에서 특히 분명하다 — Modulith의
-- JpaEventPublicationRepository가 `where p.serialized_event = ?`로 동등 비교를 하는데 Oracle은
-- CLOB에 `=`를 허용하지 않는다(ORA-00932). 길이만 넓히면 ddl-auto=validate도 그대로 통과한다.
ALTER TABLE EVENT_PUBLICATION MODIFY (serialized_event VARCHAR2(4000 CHAR));
ALTER TABLE EVENT_PUBLICATION_ARCHIVE MODIFY (serialized_event VARCHAR2(4000 CHAR));
