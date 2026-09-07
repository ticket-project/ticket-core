-- ticket-domain-module-redesign Phase 2 Task 3(ADR 0005): Seat에 필수 Venue 연관관계를 추가한다.
--
-- 기존 SEATS row는 Flyway 이전 baseline(Hibernate ddl-auto=create가 만든) synthetic 데이터라 실제
-- Venue 소속을 추론할 근거가 없다(ADR 0005 "데이터 전환" 참고, 재생성 전략). 이미 있는 row는
-- VENUES의 첫 행에 임시로 배정해 NOT NULL 제약을 만족시킨다 — 그 값 자체가 실제 소속이라는
-- 뜻은 아니고, 이후 seed가 Venue별로 결정적으로 재생성한 row가 실제 배정을 대체한다. VENUES가
-- 아직 비어 있는 환경(SEATS도 비어 있다)에서는 이 UPDATE가 no-op이다.
ALTER TABLE SEATS ADD venue_id BIGINT;

UPDATE SEATS
SET venue_id = (SELECT MIN(id) FROM VENUES)
WHERE venue_id IS NULL;

ALTER TABLE SEATS ALTER COLUMN venue_id SET NOT NULL;

ALTER TABLE SEATS ADD CONSTRAINT FK_SEATS_VENUE FOREIGN KEY (venue_id) REFERENCES VENUES (id);

CREATE UNIQUE INDEX IF NOT EXISTS UK_SEATS_VENUE_SEAT_ADDRESS
    ON SEATS (venue_id, floor, section, row_no, seat_no);
