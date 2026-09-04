-- 예매 E2E 테스트를 매번 같은 상태에서 시작시킨다. fixture보다 먼저 실행된다.
--
-- 컨텍스트는 테스트 클래스 사이에 재사용되고 @Sql은 메서드마다 돌기 때문에, 이전 테스트가
-- 만든 주문과 회원이 남아 있다. 특히 좌석 경합 테스트는 깨끗한 좌석을 전제로 한다.
--
-- FK 역순으로 지운다. 스키마는 Hibernate가 ddl-auto=create-drop으로 만든 것을 따른다.

DELETE FROM order_hold_release_progress;
DELETE FROM event_publication;
DELETE FROM event_publication_archive;
DELETE FROM hold_history;
DELETE FROM order_seats;
DELETE FROM orders;

DELETE FROM performance_seats;
DELETE FROM performance_grades;
DELETE FROM performance_queue_policies;
DELETE FROM performances;
DELETE FROM show_seats;
DELETE FROM show_grades;
DELETE FROM shows;
DELETE FROM seats;
DELETE FROM venues;

-- 회원은 테스트마다 새 이메일로 가입하므로 남겨도 되지만, 소셜 계정까지 쌓이면
-- 이메일 중복 판정이 흐려진다. 함께 지운다.
DELETE FROM member_social_accounts;
DELETE FROM members;
