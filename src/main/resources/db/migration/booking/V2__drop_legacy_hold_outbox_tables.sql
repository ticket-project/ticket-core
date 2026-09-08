-- Task 8이 hold 생성/해제 후처리를 custom outbox(V5/V6/V7)에서 Spring Modulith 이벤트 발행
-- registry(V8: EVENT_PUBLICATION/EVENT_PUBLICATION_ARCHIVE)로 대체했다. Task 8의 publication
-- success/failure/retry 테스트가 이미 통과했고, 두 outbox table/entity를 참조하는 Java 코드가
-- 더 이상 없음을 확인했다(HoldCreationOutbox/HoldReleaseOutbox 및 관련 Executor/Relay 전부 삭제됨,
-- docs/core-booking-lifecycle.md 참고). 인덱스는 테이블과 함께 제거된다.
DROP TABLE ORDER_HOLD_RELEASE_OUTBOX;
DROP TABLE ORDER_HOLD_CREATION_OUTBOX;
