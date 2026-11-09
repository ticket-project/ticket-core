# 사용자당 Core CPU 줄이기(설계)

상태: 설계 확정(2026-11-09). 구현은 브랜치 `feat/scale-out-prep`에서 항목마다 커밋한다.
코드 위치는 운영에 배포된 master `9e65d670` 기준이다.

## 쉬운 말 요약

- 2026-11-09 운영 03-3(인기 공연 오픈)에서 첫 FAIL이 90 u/s였다. 막은 것은 호스트 CPU(2 vCPU)이고 사용자 한 명이 Core CPU를 약 16ms 쓴다.
- 운영 프로파일러로 그 16ms를 나눠 일곱 덩어리로 보고, 덩어리마다 줄일 안을 하나씩 정했다.
- 정한 것을 모두 하면 사용자당 약 3.6ms(약 23%)가 준다(프로파일러를 끈 상태로 환산한 추정). 같은 CPU로 받을 수 있는 사용자는 단순 계산으로 약 1.3배다.
- 큰 몫은 점유 좌석 응답을 좌석당 1비트로 줄이는 것(약 1.0ms)과 WebSocket 메시지 압축을 끄는 것(약 0.9ms)이다.
- 백그라운드(JIT·GC·Datadog, 사용자당 6.2ms)는 이번에 손대지 않는다.

## 근거: 2026-11-09 운영 프로파일

gatling-test [11-09 일지](https://github.com/ticket-project/gatling-test/blob/master/docs/capacity-log/2026-11-09-prod-03-3-fanout.md)의 "사용자당 CPU 분해"가 원본이다. Datadog 프로파일러를 켜고 60 u/s × 120초(7,200명)를 돌렸다. 프로파일러 자신이 약 11%를 써서 사용자당 22.6ms로 잡혔다. 끈 상태로 보려면 약 0.7을 곱한다.

| 어디에 | 사용자당(켠 상태) | 비율 | 요청 1번당 |
| --- | --- | --- | --- |
| 요청 밖 백그라운드(JIT·GC·소켓·Datadog) | 6.2ms | 27% | - |
| 주문 생성 | 4.2ms | 19% | 5.6ms |
| 점유 좌석 조회 | 2.7ms | 12% | 2.3ms |
| 좌석 선택 | 2.0ms | 9% | 2.1ms |
| venue-layout | 1.5ms | 7% | 1.7ms |
| WebSocket 연결(SockJS) | 1.2ms | 5% | - |
| 좌석 배치도 | 1.2ms | 5% | 1.3ms |
| 그 밖(STOMP 받기, 취소·해제, 지표 수집, 분류 안 됨) | 3.6ms | 16% | - |

같은 프로파일을 2026-11-09에 다시 조회해 나눈 것(전체 CPU 대비 포함 시간):

- WebSocket 보내기 9.7%, 그중 메시지 압축(`PerMessageDeflate`) 5.7%. 연결 맺기(`GET /ws`) 5.5%, 그중 SockJS 처리 1.5%. STOMP 받기 2.9%.
- HTTP 요청 전체 60.4% 중 고정 비용: Tomcat·Datadog 계측 약 8.6%, 요청 시간 계측 0.8%, 보안 필터 묶음 7.9%, Spring MVC 4.9%. 요청 안 Micrometer 계측 약 5~6.6%.
- 백그라운드: 프로파일러(JFR) 13.7%, JIT(C2) 8.4%(재시작 직후 측정이라 크게 잡힌 것으로 추정), GC(G1) 5.8%, Redis 클라이언트 4~8%.

## 1. 주문 생성

### 지금

운영 trace(11-09 07:50, 80 u/s, 프로파일러 끔)에서 주문 하나는 5.2ms였고 이 순서로 DB·Redis를 불렀다.

| 순서 | 하는 일 | 걸린 시간 |
| --- | --- | --- |
| 1 | `SELECT members` 탈퇴 여부 | 0.31ms |
| 2 | `SELECT orders` 같은 회차 PENDING 여부 | 0.29ms |
| 3 | `SELECT performance_seats` 좌석 판매 가능 여부 | 0.21ms |
| 4 | Redis 선점 스크립트 | 0.18ms |
| 5 | `INSERT orders` | 0.48ms |
| 6 | `INSERT order_seats` | 0.32ms |
| 7 | `INSERT hold_history` | 0.34ms |
| 8 | Redis 선택 정리 + HELD 발행 | 0.76ms |

DB 왕복 하나가 Spring Data·Hibernate·드라이버·Datadog 계측을 지나며 0.3~0.5ms의 CPU를 쓴다. `orders`에는 무작위 UUID 유일 인덱스가 두 개(`uk_orders_order_key`, `uk_orders_hold_key`)라 INSERT p50이 다른 표의 2배다(0.62ms, 추정 원인).

### A-2. 선점 이력 쓰기를 모두 멈춘다

- **왜**: `hold_history`를 읽는 코드가 없다(`HoldHistoryRepository`는 `saveAll`뿐). 생성 행의 내용은 `orders`·`order_seats`에, 취소·만료는 `orders.canceled_at`·`expired_at`에 있다.
- **바꿀 곳**: `OrderHoldHistoryRecorder`와 호출, `HoldHistory`·`HoldHistoryRepository`, 관련 테스트와 문서.
- **남기는 것**: `hold_history` 표와 운영 데이터. Hibernate `validate`는 매핑된 entity만 보므로 entity를 지워도 기동한다.
- **효과**: 주문당 CPU 약 0.27ms, 연결 쥐는 시간 약 0.34ms.

### B-1. 진행 중 주문 SELECT와 만료 즉시 정리를 뺀다

- **왜**: 회원·회차당 PENDING 하나는 부분 유일 인덱스 `uk_orders_pending_member_performance`가 지키고, 위반은 이미 `E5004`로 바꾼다.
- **바꿀 곳**: `BookingPreflightReader`의 진행 중 주문 확인, `CreateOrderUseCase`의 `E5004` 재시도와 `ExpireOrderUseCase.expireIfStale`, 쓰는 곳이 없어지는 저장소 메서드와 테스트.
- **바뀌는 동작**: 중복 주문은 선점 → INSERT 유일 인덱스 위반 → 선점 되돌림(알림 없음) → `E5004`. 만료 시각이 지났지만 청소 worker(5초 간격)가 아직 정리하지 않은 PENDING이 있으면 그 몇 초 동안 같은 회원·회차 주문은 `E5004`다.
- **효과**: 주문당 CPU 약 0.24ms, 연결 쥐는 시간 약 0.29ms.

### F-1. 주문 키와 선점 키를 시간순 UUID(v7)로 만든다

- **왜**: 무작위 키는 인덱스의 아무 페이지에나 들어가 RDS가 페이지를 읽고 WAL에 페이지 전체를 쓴다. 시간순 키는 늘 같은 구간에 들어간다(로컬 11-06: 2,000건에 인덱스 디스크 읽기 1,933 → 7번).
- **바꿀 곳**: booking 모듈의 키 생성 클래스 하나를 `PendingOrderCreator`(주문 키)와 `HoldRegistry`(선점 키)가 쓴다.
- **만드는 법**(RFC 9562): 상위 64비트 = 주입한 `Clock`의 epoch 밀리초 48비트 | 버전 `0111` | 무작위 12비트, 하위 64비트 = variant `10` | 무작위 62비트. 무작위는 `SecureRandom`. 문자열은 지금처럼 접두어 + 하이픈 없는 32글자라 migration이 없다.
- **효과**: Core CPU는 거의 없다. 주문 INSERT p50 약 0.6 → 0.3ms(추정), RDS 디스크 읽기·WAL 감소.

### 하지 않기로 한 것

| 안 | 이유 |
| --- | --- |
| 회원 확인 SELECT 캐시 | 탈퇴 회원 토큰을 막는 판정이라 판정 경로 캐시 금지와 충돌한다 |
| 좌석 조회를 필요한 값만 | 절감 약 0.1ms인데 `PerformanceSeat`을 넘겨받는 곳이 많다 |
| 주문·주문 좌석 INSERT를 SQL 한 문장으로 | 주문 aggregate를 JPA로 다루는 구조를 버리는 변경이다 |
| 읽기·쓰기 트랜잭션 합치기 | Redis 선점 동안 DB 연결을 쥐지 않으려고 일부러 나눴다(capacity-log P-013) |

## 2. 점유 좌석 조회

### 지금

`GET /api/v1/performances/{id}/seats/occupied`는 못 고르는 좌석 ID 목록과 회차 요약을 준다. 목록은 회차별 1초 재사용하지만 JSON 변환과 gzip은 요청마다 한다. 매진 직전 15,000석이면 약 100KB이고, 요청당 2.3ms 중 JSON·gzip이 1.0~1.3ms였다.

### 정한 것: 좌석당 1비트 새 API

인터파크도 좌석 상태를 좌석당 1비트로 보낸다([관찰 기록](../research/interpark-booking-2026-11-09.md)).

```
GET /api/v1/performances/{performanceId}/seats/occupancy
{"success":true,"data":{"seatCount":8,"available":"tg==","summary":{…}}}
```

| # | 정할 것 | 정한 것 |
| --- | --- | --- |
| 2-1 | 주소 | 새 경로. 옛 `occupied`는 같은 빌드로 전후를 잰 뒤 지운다 |
| 2-2 | 글자로 바꾸는 법 | base64(`byte[]`를 Jackson이 바꾼다) |
| 2-3 | 좌석 순서 | 회차 좌석의 seatId 오름차순. `seatCount`로 배치도와 어긋났는지 받는 쪽이 확인한다 |
| 2-4 | 비트 뜻 | 1 = 고를 수 있음(판매·선택·선점 아님). 바이트 안에서는 앞 좌석이 높은 비트, 남는 칸은 0 |
| 2-5 | 재사용 | 회차별 상태 묶음(비트, 좌석 수, 요약)을 별도 bean의 `@Cacheable`로 1초(`sync = true`). JSON은 요청마다 |
| 2-6 | 버전 값 | 넣지 않는다(쓰는 곳이 없다) |
| 2-7 | 옛 API | 전후 측정 뒤 삭제(`seats/status`·`seats/availability`는 FE가 써서 남긴다) |

- 입장권·판매 기간 검사는 요청마다 한다.
- 바뀐 좌석은 지금처럼 WebSocket 알림으로 받는다. 알림은 seatId로 맞추므로 비트 순서와 무관하다.
- **효과**: 사용자당 약 1.0ms. 응답은 매진 직전 약 100KB → 약 3KB.

### 함께 정한 것

- **좌석 알림 주소 분리**: 2026-11-09 배포(#304)로 `/topic/performance/{id}/seats`가 250ms 묶음 형식이 되어 운영 FE가 읽지 못한다([프론트엔드 호환](../architecture.md#프론트엔드-호환)). 옛 주소는 옛 형식(좌석 변경 하나에 `SeatStatusEvent` 하나)으로 되살리고, 묶음은 새 주소 `/topic/performance/{id}/seats/batch`로 옮겨 측정 시나리오가 쓴다.
- **새 주소 구독 때 입장권 검사**: SUBSCRIBE 프레임의 `X-Admission-Token`으로 `BookingEntryGuard`를 통과해야 새 주소를 구독할 수 있다. 옛 주소는 FE 호환을 위해 그대로 둔다.
- **Core HTTP 압축 제거**: `server.compression.enabled`를 지우고 운영 nginx(`gzip on`, json, 1,000바이트 이상)에 맡긴다. 압축 CPU가 nginx로 옮겨 가므로 비교는 호스트 전체 CPU로 한다. 배포 전에 운영 nginx의 `gzip_comp_level`·`gzip_proxied`를 확인한다. 로컬에는 nginx가 없어 압축이 사라진다.

## 3. 좌석 선택

- **3-A. DB 좌석 확인을 필요한 값만 읽는다**: 엔티티 대신 회차 좌석 ID와 상태만 읽는다. 요청당 약 0.1ms.
- **3-B. 업무 예외는 스택을 만들지 않는다**: `TicketException`이 `writableStackTrace = false`로 만든다. 500(`InternalErrorException`)만 스택을 남긴다. 좌석 선택·주문의 409에서 스택 수집이 CPU 1.9%를 썼다. 업무 예외 핸들러는 오류 코드만 로그로 남기므로 잃는 것이 없다. 사용자당 약 0.26ms.
- **하지 않은 것**: Redis에 판매 완료 표시를 두고 DB 확인을 없애는 안. 판매 상태가 두 곳에 생긴다.

## 4. venue-layout

- 공연장 이름과 좌석 그림 크기를 공연별로 10분 재사용한다(Caffeine `@Cacheable`, 트랜잭션 유스케이스 밖 bean). 요청마다 돌던 DB 두 번과 트랜잭션이 사라진다. 사용자당 약 0.5ms.
- **하지 않은 것**: nginx가 공개 GET 응답을 저장하는 안, venue-layout과 배치도를 합친 새 주소.

## 5. WebSocket

- **메시지 압축(permessage-deflate)을 끈다**: 같은 알림을 구독자마다 따로 압축해 CPU 5.7%를 썼다. 연결 때 서버가 압축 확장을 받아들이지 않게 한다. 압축은 합의하는 선택 기능이라 FE는 압축 없이 그대로 동작한다. 사용자당 약 0.9ms.
- **대가**: 알림 전송량이 5~10배가 된다. 11-09 호스트 송신은 초당 약 2MB였고 최악으로 잡아도 90 u/s에서 약 180Mbps다. 전송량은 사용자 수의 제곱에 가깝게 늘어나므로 측정 때 호스트 송신량을 함께 기록한다.
- **하지 않은 것**: SockJS 없는 새 주소(측정 시나리오는 이미 SockJS 감싸기를 건너뛰어 아낄 것이 약 0.1ms).

## 6. 요청 고정 비용

- **보안 계측(Spring Security Observation)을 끈다**: 요청마다 보안 필터 묶음·인증·권한 확인 계측이 붙고 필터마다 계측 포장이 감싼다. 읽는 곳이 없다. 사용자당 약 0.5ms(추정).
- **남기는 것**: 요청 시간 계측(`http.server.requests`). gatling 판정 도구가 읽는다.
- **하지 않은 것**: 모듈 계측(Spring Modulith insight) 끄기, 보안 필터 줄이기.

## 7. 백그라운드

이번에는 손대지 않는다. 후보는 실제 부하로 충분히 예열하기(JIT), 힙 크기·GC 종류 정하기, Datadog 추적기 비용 재기였다.

## 합친 효과(끈 상태 추정)

| 항목 | 사용자당 |
| --- | --- |
| 1. 주문 생성 | 약 0.4ms(+ DB 연결 쥐는 시간 약 0.7ms) |
| 2. 점유 좌석 | 약 1.0ms |
| 3. 좌석 선택 | 약 0.33ms |
| 4. venue-layout | 약 0.5ms |
| 5. WebSocket 압축 | 약 0.9ms |
| 6. 보안 계측 | 약 0.5ms |
| 합계 | 약 3.6ms(16ms의 약 23%) |

추정은 포함 시간의 차이로 나눈 값이다. 구현 뒤 같은 조건으로 측정하고 프로파일을 다시 떠서 확인한다.
