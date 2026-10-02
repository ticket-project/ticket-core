-- 이벤트 클래스 com.ticket.booking.OrderStarted가 com.ticket.booking.OrderCreated로 바뀌었다.
-- Spring Modulith는 event_type을 Class로 읽으므로, 옛 이름이 남은 행은 클래스 로딩에 실패해 재처리되지 않고
-- 다른 이벤트의 재처리까지 막을 수 있다. 미완료·보관 행 모두 새 이름으로 옮긴다.
-- listener_id는 BookingEventListeners가 옛 문자열로 고정하므로 건드리지 않는다.
UPDATE EVENT_PUBLICATION
SET event_type = 'com.ticket.booking.OrderCreated'
WHERE event_type = 'com.ticket.booking.OrderStarted';

UPDATE EVENT_PUBLICATION_ARCHIVE
SET event_type = 'com.ticket.booking.OrderCreated'
WHERE event_type = 'com.ticket.booking.OrderStarted';
