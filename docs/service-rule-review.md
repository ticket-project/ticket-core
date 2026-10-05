# 서비스 규칙 점검 — 미정 정책과 구현 보완 후보

상태: **공동 검토 초안**. 조사 기준일: 2026-10-04. 공통 규칙 표는 [서비스 업무 규칙](service-rules.md)에 있다.
이 문서는 정적 코드 조사 결과다. 운영 환경·장애·브라우저·부하를 실행해 모든 현상을 재현한 보고서가 아니다.
초기 조사에서는 정책 결정이나 서비스 코드 수정을 수행하지 않았다. 이후 문답으로 확정한 정책은
[업무 규칙의 결정 기록](service-rules.md#결정-기록)에 반영한다. 미확정 항목은 계속 검토 대상이다.

## 조사 범위와 기존 문서의 역할

| 저장소 | 조사 기준 커밋 | 확인한 범위 |
| --- | --- | --- |
| ticket | 35d8cd92, feat/order | production Java의 업무 모델·조회·유스케이스·HTTP·인증·Redis·이벤트 경로, 관련 migration·설정·문서, 테스트 구조·선언과 대표 경계 검증 |
| ticket-queue | 0ee52e4, master | 참여·순서 진행·공개 상태·입장·토큰·Redis Lua·설정 및 배포 설명 |
| ticket-fe | cbb667e, main | 공연 조회·예매 진입·좌석·주문·결제 화면의 API·상태·시간·이탈 처리, 인증 갱신·회원·찜 소비 경로 |
| gatling-test | fa24164, main | 현재·레거시 부하 시나리오의 호출·응답 검사·성공 정의 및 실행 도구의 역할 |

전체 저장소의 파일·공개 엔드포인트를 탐색하고 업무 규칙을 실행하는 경로를 중심으로 읽었다. FE의 스타일·이미지,
생성물, 모든 테스트 본문·부하 콘솔 구현까지 한 줄씩 완독했다는 의미는 아니다. 별도 운영자 서비스나 외부 기획
자료는 제공되지 않았다. 운영 DB의 실제 데이터·migration 적용 상태·환경 변수·CDN 설정은 확인하지 않았다.

기존 문서가 없는 프로젝트는 아니다. 다만 문서마다 역할이 다르다.

| 기존 자료 | 현재 역할 | 이번에 보완한 부분 |
| --- | --- | --- |
| [예매 수명주기](core-booking-lifecycle.md) | 선택·선점·주문·보상·이벤트의 현재 구현 | 기획·QA가 읽을 조건·결과와 기능 간 정책 질문 |
| [용어집](glossary.md) | 공연·회차·선택·선점 등의 공통 용어 | 규칙·수용 기준과 연결 |
| [ADR](adr/README.md) | 기술·도메인 결정 이력. 채택·대체·미적용 구분 | 현재 정책 승인 여부와 별도 표시 |
| [아키텍처](architecture.md) | 모듈 소유권·경계 | 회원·공연·찜·Queue·FE를 연결한 사용자 동작 |
| [테스트 기준](testing.md) | 도구·검사 범위·실행 기준 | 무엇이 정답인지 먼저 결정하는 단계 |

## 먼저 결정할 정책

아래 순서는 **검토 우선순위 제안**이다. 확정된 일정이나 이슈 상태가 아니다.

| 우선 | 정책 질문 | 이유 | 연결 규칙 |
| --- | --- | --- | --- |
| 먼저 | 이번 서비스의 완료 범위가 결제 대기 주문까지인가, 결제·발권까지인가? | 결제 화면·모델과 실제 제공 기능이 다르다 | P-01~07 |
| 먼저 | 예매는 선택한 회차의 좌석·단가와 서버 주문 금액을 기준으로 표시하는가? | 회차·화면·주문 금액이 달라질 수 있다 | S-11, O-05~07 |
| 먼저 | 대기열을 언제·어떤 회차에 강제하고, 판매 전 입장을 허용하는가? | Core·Queue·FE 흐름이 일치하지 않는다 | E-03~07, Q-07 |
| 먼저 | 기한 기준 만료·재예매·종료 사유는 확정. DB·좌석 정리의 허용 지연과 취소 응답은 무엇인가? | 확정 정책은 코드 미적용. 현재 PENDING·남은 0초·새 주문 거절이 함께 생길 수 있다 | O-03, O-09~14 |
| 먼저 | 성공 응답 유실·반복 요청·새로고침 때 기존 선택·주문을 복원하는가? | 사용자는 성공 여부를 알 수 없다 | B-05, O-12, O-16~17 |
| 먼저 | 탈퇴·로그아웃이 진행 중 예매와 이미 발급된 자격에 즉시 영향을 주는가? | 접근 권한과 좌석 자원 정리가 연결돼 있다 | M-07~10 |
| 다음 | 1석 상한·총 구매 한도와 합산 대상, 선택별 5분·회차별 선점 기간은 확정. 실제 수량 값과 구매 완료 후 취소·환불의 한도 복구는 무엇인가? | 서로 다른 수량·시간 제한을 혼동하기 쉽다 | B-04, B-11, O-01, O-03, O-08 |
| 다음 | 검색·최신·인기·임박순·오픈 예정의 사용자 정의는 무엇인가? | 이름과 실제 결과·집계가 다를 수 있다 | S-03~10 |
| 다음 | 존재하지 않는 공연의 찜·탈퇴 회원 찜·미완성 상품을 어떻게 처리하는가? | 데이터가 불완전할 때 고객 결과가 정해져 있지 않다 | L-03~05, S-12 |
| 결제 전 | 승인·만료 경합, 환불, 수령, 동의 정보, 티켓 사용을 어떻게 정의하는가? | 결제 구현을 현재 상태 모델만 보고 진행할 수 없다 | P-01~07 |

## 코드 사이에서 확인한 불일치

‘불일치’는 양쪽 코드의 계약이 다르다는 뜻이다. 해당 데이터·설정이 운영에 존재하는지는 별도로 확인해야 한다.

### 선택 회차와 화면의 좌석·가격

- **현재:** FE는 `showId`로 `/shows/{showId}/seats`를 호출하고, 상태·선택·주문에는 `performanceId`를 사용한다.
  공연 좌석 API는 최소 회차 ID의 정보를 대표로 반환한다. Core에는 `/performances/{id}/seat-map`도 있다.
- **영향:** 회차별 좌석·등급·가격이 다르면 화면의 좌석·가격과 실제 주문 대상이 다를 수 있다.
- **제안:** 예매 화면은 선택한 회차의 배치도·단가를 사용한다. 공연 대표 정보는 상품 소개에서 사용한다.
  주문 단계에서는 주문 상세의 저장된 좌석·금액을 표시한다. 서로 다른 회차 데이터를 수용 기준에 포함한다.
- **수정 후보:** FE API·query key·좌석 조립·결제 요약. 회차가 다른 공연에 속한 잘못된 URL도 검증한다.

근거: [FE 좌석 API](../../ticket-fe/src/features/booking/api/index.ts),
[FE 좌석 조립](../../ticket-fe/src/features/booking/hooks/useSeatViewModel.ts),
[대표 회차 좌석](../src/main/java/com/ticket/booking/seat/usecase/GetShowSeatMapUseCase.java),
[회차 좌석](../src/main/java/com/ticket/booking/seat/usecase/GetPerformanceSeatMapUseCase.java).

### 화면의 배송비와 서버 주문 합계

- **현재:** FE는 배송 선택에 3,700원을 추가하고 로컬 선택 좌석 가격을 합산한다. 서버 주문 상세의 배송비·예매
  수수료·할인은 모두 0이다. FE는 서버 주문 상세를 결제 요약의 원본으로 조회하지 않는다.
- **영향:** 실제 청구 대상 금액과 화면 금액이 달라질 수 있다. 새로고침하면 메모리의 선택 목록도 소실된다.
- **결정:** 배송을 이번 범위에 넣을지 먼저 정한다. 넣는다면 수령방법·배송비·주소·적용 시점을 서버 계약에
  포함하고, 화면은 서버가 계산한 주문 요약을 사용한다. 제외한다면 예정 기능 표현도 범위에 맞춘다.

근거: [결제 화면](../../ticket-fe/src/features/booking/components/page/PaymentPageClient.tsx),
[주문 상세](../src/main/java/com/ticket/booking/order/usecase/GetOrderDetailUseCase.java).

### 대기열 진입 경로

- **현재:** FE 예매 버튼은 좌석 화면으로 바로 이동한다. `booking-mode` 조회·Queue 참여/입장·admission token
  전달은 현재 `src`에서 확인되지 않는다. FE HEAD는 대기열 진입 기능을 되돌린 커밋이다.
- **조건부 영향:** Core 자격 검증을 켠 QUEUE 회차에서는 이 화면의 상태 조회·선택·주문 요청이 거절된다.
  기본 검증 설정이 꺼져 있다는 사실만으로 운영에서 꺼졌다고 단정할 수 없다.
- **제안:** 진입 방식 조회 → DIRECT면 예매 / QUEUE면 참여·진행 확인·입장 → 자격을 포함한 예매라는
  공통 흐름을 먼저 합의한다. 만료·회원 변경·회차 변경 시 자격의 폐기·재진입도 정의한다.

근거: [예매 버튼](../../ticket-fe/src/features/shows/components/booking/BookingPanel.tsx),
[FE 예매 API](../../ticket-fe/src/features/booking/api/index.ts),
[진입 방식](../src/main/java/com/ticket/booking/salespolicy/usecase/GetPerformanceEntryModeUseCase.java),
[Core 검증 기본 설정](../src/main/resources/application.yml).

### 대기열 부하 시나리오의 응답 위치

- **현재:** Queue의 enter는 `EnterResponse`를 JSON 최상위로 반환한다. `TicketOpenEndToEndSimulation`은
  `$.data.admissionToken`을 검사한다. `QueueEnterSimulation`은 `$.admissionToken`을 검사한다.
- **영향:** 현재 Queue API를 직접 대상으로 하는 E2E 시나리오는 토큰 추출 계약이 맞지 않는다.
- **수정 후보:** 공통 API 계약에 맞춰 JSON 경로와 계약 검증을 정리한다. 프록시가 별도 응답 변환을 한다면 그
  계약을 먼저 확인한다. 시나리오 이름의 E2E는 현재 Queue→PENDING 주문까지이며 결제 완료 검증은 아니다.

근거: [Queue controller](../../ticket-queue/src/main/java/com/ticket/queue/api/AdmissionController.java),
[E2E 시나리오](../../gatling-test/load-tests/gatling/src/gatling/java/com/ticket/loadtest/simulation/TicketOpenEndToEndSimulation.java),
[입장 시나리오](../../gatling-test/load-tests/gatling/src/gatling/java/com/ticket/loadtest/simulation/QueueEnterSimulation.java).

### 실패 이유를 화면·부하 판정에 전달하는 계약

- **현재:** Core 오류는 `error.code`·`error.message`에 있다. FE 공통 fetch는 최상위 `message`를 읽고 오류 코드·
  상세 데이터는 호출부에 보존하지 않는다. 예매 화면은 대부분 일반 실패 안내만 표시한다.
- **영향:** ‘다른 사람의 좌석’, ‘선택 시간 만료’, ‘기존 주문 있음’, ‘대기열 자격 만료’에 맞는 복구를 하기 어렵다.
- **추가 불일치:** 좌석 경합 부하 시나리오의 주문 거절 허용 목록에 현재 Core 코드에 없는 E5000/E5001이 있고,
  현재 선택 필수 오류 E4006/E4007 등과 일치하지 않는다. 무엇을 정상 업무 거절로 볼지 재검토해야 한다.
- **제안:** 고객 안내·재조회·재선택·기존 주문 이동·재입장으로 이어지는 오류 표를 먼저 정하고 FE·시나리오를 맞춘다.

근거: [Core 응답](../src/main/java/com/ticket/shared/web/ApiResponse.java),
[Core 예매 오류](../src/main/java/com/ticket/booking/exception/BookingErrorCode.java),
[FE fetch](../../ticket-fe/src/lib/api.ts),
[좌석 경합 시나리오](../../gatling-test/load-tests/gatling/src/gatling/java/com/ticket/loadtest/simulation/SeatContentionSimulation.java).

## 예매 정책에서 빠진 결정과 정적 위험

### 사전 대기열의 의미와 판매 마감 경계

- **현재:** 사전 대기열 시작 시각이 있어도 진입 방식 조회는 접수 전 UNAVAILABLE을 우선 반환한다. Queue는
  Core 판매 시각을 확인하지 않는다. 접수 마감 시각과 정확히 같으면 허용한다.
- **결정:** 판매 전 대기열 참여만 허용하는지, 입장 자격 발급까지 허용하는지, 좌석 조회까지 허용하는지 각각
  구분해야 한다. 접수 마감은 `현재 < 마감`인지 `현재 <= 마감`인지 예시로 확정한다.
- **정적 위험:** 선택은 좌석 락 안에서 마감을 재확인하지만 주문 생성은 앞서 읽은 시각을 사용한다.
  처리 중 마감이 지나도 주문을 생성할 수 있는 구조다. ‘요청 도착 시각’과 ‘좌석 확보 시각’ 중 기준을 정한다.
- **확정·유지:** 2026-10-05 문답에서 접수 마감 전에 생성한 주문·선점은 자신의 만료 시각까지 유지하기로 했다.
  접수 마감만을 이유로 기한을 줄이거나 종료하지 않는다. 마감 후 기존 선택으로 새 주문을 생성하거나,
  취소·만료 후 재예매하는 것은 접수 기간 조건을 다시 충족해야 한다. 정확한 마감 순간의 허용 여부와
  요청 처리 중 마감을 넘었을 때의 판정 단계는 여전히 검토 대상이다.

근거: [진입 방식](../src/main/java/com/ticket/booking/salespolicy/usecase/GetPerformanceEntryModeUseCase.java),
[선택 시각 확인](../src/main/java/com/ticket/booking/selection/usecase/SeatSelectionWriter.java),
[접수 기간의 정의](../src/main/java/com/ticket/booking/salespolicy/domain/BookingWindow.java),
[주문 생성 시각](../src/main/java/com/ticket/booking/order/usecase/CreateOrderUseCase.java).

### 수량 상한과 시간 제한의 적용 단위

- **현재:** 상한은 null 또는 2 이상이다. 1석 주문은 가능하지만 상한을 1로 설정할 수 없다. 선택 수와 한 주문
  수는 검사하지만 회원이 여러 번 구매한 누적 수량 제한은 구현된 흐름에서 확인되지 않는다.
- **확정·미적용:** 2026-10-05 문답에서 사용자가 ‘권장대로’ 결정해, 회차별 동시 선택 수와 한 주문의 좌석 수
  상한을 1석으로도 설정할 수 있게 한다. 기존 2석 이상과 제한 없음은 유지하며, 모든 회차를 1석으로 바꾸지는 않는다.
  상한은 제한 없음 또는 1 이상의 정수이며 0·음수는 거절한다. 상한 1석이면 같은 회원·회차의 다른 탭과 동시 요청도
  합산해 두 번째 좌석 선택을 막고, 2석 이상 주문은 전체를 거절한다. 실제 회차별 값은 미정이다.
- **확정·미적용:** 이어진 문답에서 사용자가 ‘권장대로’ 결정해, 같은 회원·같은 회차의 여러 구매를 합산하는
  총 구매 한도도 회차별로 설정할 수 있게 한다. 동시 선택·한 주문의 수량 상한과 별도다. 설명용 총 한도 4석이면
  기존 3석 구매 후 추가 구매는 1석까지만 허용하며 2석 추가 주문은 전체 거절한다. 다른 회원·회차는 합산하지 않고,
  동시·반복 요청으로 우회하거나 중복 합산하지 않아야 한다. 현재 정책·주문 생성에는 해당 설정·합산 검사가 없다.
- **확정·미적용:** 다음 문답에서 사용자가 ‘권장대로’ 결정해 구매 완료(CONFIRMED)와 기한이 남은 결제 대기
  (PENDING)의 좌석을 함께 합산한다. 기존 합계와 새 주문 수량이 총 한도 이내여야 주문을 생성할 수 있다.
  예시로 구매 완료 3석·유효한 대기 1석은 4석이며, 대기 1석이 완료돼도 같은 좌석을 중복 합산하지 않는다.
  결제 대기의 취소·기한 도달 후에는 그 수량을 제외한다. DB에 오래된 PENDING이 남거나 좌석 정리가 늦어도
  계속 합산하지 않으며 구매 완료 좌석은 유지한다. 남은 한도와 좌석 재확보 가능 여부는 다르다.
- **추가 결정:** 총 한도의 실제 값·기본값·설정 누락 회차, 구매 완료 후 취소·환불의 한도 복구,
  기존 구매가 새 한도를 초과한 경우를 하나씩 정한다. 남은 수량 감소로 기존 선택이 초과한 경우의 처리도 미정이다.
  FE가 실제 한도와 남은 구매 가능 수량을 안내할 방법도 필요하다.
- **확정·미적용:** 다음 문답에서 사용자가 ‘어’로 동의해 선택 단계부터 남은 구매 가능 수량까지만 고르게 한다.
  기존 동시 선택 상한도 함께 적용한다. 예시로 총 한도 4석·구매 완료 3석이면 선택은 최대 1석이며, 유효한 대기 1석도
  있으면 추가 선택은 불가하다. 다른 탭·동시 요청도 합산하고 유효한 같은 선택의 재요청은 수량을 늘리지 않는다.
  주문 생성 시 최신 상태로 한도를 다시 검증한다. 현재 선택 처리는 회차 상한만 사용하고 구매·대기 합산은 없다.
- **확정·미적용:** 다음 문답에서 사용자가 ‘어’로 동의해 회차별 총 한도에 숫자 제한과 명시적인 ‘총 제한 없음’을
  모두 지원한다. 총 제한이 없어도 기존 동시 선택·한 주문의 수량 상한과 유효 주문 수 제한은 유지한다.
  설정 누락을 자동으로 총 제한 없음으로 간주하는 결정은 아니다. 현재 정책 모델에는 별도 총 한도 설정 자체가 없다.
- **제안 미채택:** 이어진 문답에서 사용자가 ‘아니’로 답변해 총 구매 한도 설정 완료를 예매 개시의 필수 조건으로
  두지 않기로 했다. 누락 시 총 제한 없음 또는 숫자 기본값 중 무엇을 적용할지는 추가 확인이 필요하다.
  판매 정책 전체의 누락·조회 실패나 다른 예매 조건을 생략하는 결정은 아니다.
- **현재:** 선택은 좌석별 5분, 선점은 회차별 기간, Queue 입장은 별도 기간이다. 어느 자격이 먼저 끝났을 때
  선택·주문을 유지하고 무엇을 금지할지 공통 규칙이 필요하다.
- **보완 필요:** 현재 Java 검증과 H2/Oracle CHECK 제약은 1석 상한을 거절하므로 함께 바꾼다. 이미 적용된
  migration은 수정하지 않고 새 migration을 추가하며 기존 2석 이상·제한 없음 데이터는 유지한다.
  설정 입력·초기 데이터·API 소비자와 화면의 실제 회차별 상한 안내도 확인한다. 서비스 코드·DB 변경은 미적용이다.
- **보완 필요:** 총 구매 한도는 설정·저장 계약과 구매 수량 합산, 주문 생성 및 상태 변경의 동시 처리, 화면·API 안내를
  함께 설계해야 한다. 기존 데이터 전환·호환성도 확인한다. 결제·구매 확정 흐름은 현재 연결되지 않았으므로 모델에
  상태가 있다는 이유만으로 총 한도가 구현됐다고 취급하지 않는다.
- **보완 필요:** 남은 수량 조회와 실제 선택 저장·주문 생성 사이의 경합으로 선택 제한을 우회하지 않도록 서버에서
  일관되게 판정해야 한다. 선택과 주문의 전환도 고려하며, 화면의 제한·남은 수량 갱신과 서버 검증을 함께 보완한다.
- **보완 필요:** 숫자 총 한도·명시적인 총 제한 없음·설정 누락을 구분하는 설정·저장·응답 계약과 화면 안내를
  설계한다. 기존 데이터 전환 값은 미정이며, 기존 동시 선택·주문 수량의 제한 없음과 혼동해서는 안 된다.

근거: [수량·기간](../src/main/java/com/ticket/booking/salespolicy/domain/HoldPolicy.java),
[회차 정책 모델](../src/main/java/com/ticket/booking/salespolicy/domain/PerformanceSalesPolicy.java),
[H2 정책 제약](../src/main/resources/db/migration-vendor/h2/booking/V6__create_booking_performance_sales_policies.sql),
[Oracle 정책 제약](../src/main/resources/db/migration-vendor/oracle/booking/V6__create_booking_performance_sales_policies.sql),
[주문 수량 검사](../src/main/java/com/ticket/booking/order/usecase/CreateOrderUseCase.java),
[주문 가능 여부 확인](../src/main/java/com/ticket/booking/order/usecase/BookingAvailabilityChecker.java),
[주문 상태·기한](../src/main/java/com/ticket/booking/order/domain/Order.java),
[선택 처리](../src/main/java/com/ticket/booking/selection/usecase/SelectSeatUseCase.java),
[선택 수 저장 검사](../src/main/java/com/ticket/booking/selection/persistence/RedissonSeatSelectionStore.java).

### 만료된 주문의 상태와 재예매

- **현재:** Redis 선점 만료, 주문 기한 도달, DB 상태 변경, 화면 알림은 동시에 끝나는 하나의 처리가 아니다.
  조회는 DB 상태를 반환하고 자동 만료시키지 않는다. 만료 보완 작업은 기본 5분 간격이다.
- **영향:** 선점이 없어지고 남은 초가 0인데 DB는 PENDING일 수 있다. 이 주문은 새 주문 생성의 중복 검사에
  걸린다. 장애·worker 비활성화 시 지연이 더 길어질 수 있다.
- **확정·미적용:** 2026-10-05 문답에서 서버 시각이 결제 대기 주문의 만료 시각에 도달하면 만료로 판단하고,
  DB 만료 처리가 지연돼도 그 주문 자체가 재예매를 막지 않도록 결정했다. 새 유효한 좌석 선택과 나머지 예매
  조건은 다시 확인한다. 현재 조회·중복 검사 코드는 보완해야 하며, 서비스 코드 변경은 이번 문답 범위에 없다.
- **종료 사유 확정·미적용:** 같은 날 문답에서 서버의 취소 판정 시 만료 시각 전이면 CANCELED,
  만료 시각 이상이면 EXPIRED로 기록하기로 했다. 현재 취소는 기한을 검사하지 않아 보완이 필요하다.
  기한 전에 이미 취소된 주문의 사유를 뒤집지 않으며, 주문 상태·종료 이력·후속 이벤트의 사유를 일치시켜야 한다.
- **만료 취소 응답 확정·미적용:** 2026-10-05 문답에서 본인의 만료된 주문에 취소를 요청하면 정상 응답으로
  EXPIRED를 전달하고 화면에 “기한이 지나 만료된 주문입니다”라고 안내하기로 했다. DB에 PENDING으로 남아 있어도
  기한이 끝났다면 같은 기준을 적용한다. CANCELED로 바꾸거나 취소 이력을 새로 생성하지 않는다.
  현재 Core의 취소 성공 본문에는 상태가 없고 FE는 응답 상태를 소비하지 않아 양쪽 계약을 보완해야 한다.
- **남은 결정:** 실제 DB·좌석 정리와 오류 복구의 허용 지연, 확정 주문에 대한 취소 API 응답, 향후 결제
  승인과의 경합을 별도로 합의한다. 본인의 CANCELED 주문 반복 취소는 아래 결정에 따른다.
  화면의 정수 남은 초는 실제 만료 판정의 기준이 아니다.
- **수정 후보:** 요청 시 만료 정리, 논리적 만료 판정, 진행 중 주문 조회·복구, 주기적 대사 중 필요한 방식을 선택한다.
  기존 만료 알림·보완 작업·후속 이벤트의 중복 안전성을 유지해야 한다.

근거: [상태 조회](../src/main/java/com/ticket/booking/order/usecase/GetOrderStatusUseCase.java),
[중복 검사](../src/main/java/com/ticket/booking/order/usecase/BookingAvailabilityChecker.java),
[취소 처리](../src/main/java/com/ticket/booking/order/usecase/CancelOrderUseCase.java),
[종료 상태·이력·이벤트](../src/main/java/com/ticket/booking/order/usecase/OrderTerminationService.java),
[주문 API 응답](../src/main/java/com/ticket/booking/order/endpoint/OrderController.java),
[FE 취소 요청](../../ticket-fe/src/features/booking/api/index.ts),
[만료 트리거](../src/main/java/com/ticket/booking/order/usecase/OrderExpirationTrigger.java).

### 반복 요청·응답 유실·복원

- **현재:** 본인 선택 재요청은 충돌, 주문 재요청은 진행 중 주문 충돌, 취소 반복은 충돌이다. 실패했다고 해서
  서버에 아무 상태도 남지 않았다는 뜻은 아니다. 요청 식별자에 대응하는 기존 주문 조회는 없다.
- **확정·미적용:** 2026-10-05 문답에서 본인의 CANCELED 주문에 반복 취소를 요청하면 성공으로 응답하기로 했다.
  상태·최초 취소 시각을 유지하고 취소 이력·종료 이벤트를 추가 생성하지 않는다. 통신 실패 후 재시도와 동시 요청에도
  하나의 취소 결과를 유지해야 한다. 현재 취소 경로는 PENDING 외 상태를 거절하므로 보완이 필요하다.
- **만료 주문의 응답:** 위 결정에 따라 본인의 만료 주문도 정상 응답으로 EXPIRED를 전달한다. 이미 만료 처리된
  주문의 반복 요청은 기존 종료 이력을 유지하며 종료 이벤트를 추가 생성하지 않는다.
- **유효한 본인 선택 재요청 확정·미적용:** 2026-10-05 문답에서 같은 선택이 아직 유효한 본인 선택이면 기존 성공으로
  응답하기로 했다. 선택을 하나만 유지하고 원래 기한을 연장하거나 선택 성공 알림을 추가 생성하지 않는다.
  현재 본인·타인 선택을 모두 충돌로 처리하므로 저장 판정·응답·알림 발행의 보완이 필요하다.
- **만료된 예전 요청 확정·미적용:** 같은 날 쉬운 예시로 설명하고 동의를 받아, 만료된 예전 선택 요청은 다시 도착해도
  만료 안내만 하고 좌석을 자동으로 다시 확보하지 않기로 했다. 사용자가 직접 다시 고른 새 요청은 당시 예매 조건을
  확인한 뒤 별도 선택으로 처리하고, 성공하면 그 시점부터 5분을 부여한다. 같은 좌석의 재확보는 보장하지 않는다.
- **현재 구현의 차이:** 요청 구분 정보가 없고 Redis의 선택 키가 없으면 새 선택을 만들 수 있다. 따라서 선택 기한이
  끝난 예전 요청도 다른 조건을 충족하면 새 5분 선택이 될 수 있어 API·저장·화면 재시도 처리를 함께 보완해야 한다.
- **남은 결정:** 주문 생성의 반복 요청, 해제된 선택의 재시도, 접수 마감·입장 자격 만료 후 재시도,
  확정 주문의 취소 응답과 구체적인 요청 식별·응답 필드를 각각 정한다.
  같은 요청을 여러 번 처리해도 결과가 유지되는 성질을 멱등성이라고 부른다. 서로 다른 요청을 임의로 같은
  요청으로 취급하면 안 된다.
- **제안:** 재시도별 기대 결과, 진행 중 주문 찾기, 본인 선택·만료 시각 조회, 성공 여부가 불명확할 때 재조회
  경로를 먼저 정의한다. 결제 도입 시에는 승인 재시도와 업무 주문 재시도를 구분한다.

근거: [선택 서비스](../src/main/java/com/ticket/booking/selection/domain/SeatSelectionService.java),
[선택 저장 판정](../src/main/java/com/ticket/booking/selection/persistence/RedissonSeatSelectionStore.java),
[선택 알림](../src/main/java/com/ticket/booking/selection/usecase/SeatSelectionWriter.java),
[선택 API 요청](../src/main/java/com/ticket/booking/selection/endpoint/SeatSelectionController.java),
[주문 API](../src/main/java/com/ticket/booking/order/endpoint/OrderController.java),
[취소 처리](../src/main/java/com/ticket/booking/order/usecase/CancelOrderUseCase.java).

### 새로고침·탭·이탈·실시간 정보

- **현재:** 선택 목록은 FE 메모리 상태이며 좌석 페이지 진입 시 초기화한다. 서버 현황은 본인·타인 모두
  OCCUPIED다. 화면 이탈 때 전체 선택 해제·주문 취소를 시도한다. 새로고침도 pagehide 경로의 영향을 받을 수 있다.
- **영향:** 다른 탭의 선택까지 해제할 수 있다. 이탈 요청은 전달이 보장되지 않는다. 결제 화면은 주문 상세를
  복원하지 않아 새로고침 후 선택 좌석·금액 표시가 불완전해질 수 있다.
- **현재:** WebSocket 재연결 후 구독은 하지만 해당 onConnect 경로에서 서버 현황을 재조회하지 않는다.
  이벤트에 버전·순번이 없고, 자기 선택 ID에 대한 점유 이벤트를 무시한다.
- **정적 위험:** 자신의 선택이 만료돼 다른 사람이 점유해도 로컬의 ‘내 선택’이 남아 충돌하는 표시를 만들 수 있다.
- **주문 새로고침 확정·미적용:** 2026-10-05 문답에서 결제 대기 주문 화면은 새로고침만으로 취소하지 않고,
  서버의 본인 주문 좌석·금액·상태·남은 시간으로 복원하기로 했다. 새 주문을 만들거나 기존 기한을 연장하지 않는다.
  기한이 끝났으면 만료 화면을 표시한다. 조회 실패만으로 주문을 취소·재생성하지 않으며 재조회 경로가 필요하다.
  현재 결제 대기 화면의 상세 조회와 새로고침에도 영향을 줄 수 있는 이탈 취소 경로를 보완해야 한다.
- **주문 뒤로가기 확정·유지:** 같은 날 문답에서 좌석 선택 화면으로 뒤로갈 때는 기존 주문·선점 종료 확인을 받고,
  거절하면 주문 화면에 머물며 동의하면 종료 결과 확인 후 이동하는 현재 흐름을 유지하기로 했다.
  기한이 끝났다면 만료 상태를 안내하고, 응답 유실 시에는 종료를 단정하지 않고 재조회·재시도로 확인해야 한다.
  기존 좌석의 재확보는 보장하지 않으며 새 선택·주문에는 나머지 예매 조건을 다시 적용한다.
- **주문 닫기 확정·취소 시도 유지:** 같은 날 문답에서 탭·브라우저를 닫으면 현재처럼 취소 요청을 시도하고,
  처리되지 않으면 원래 기한 만료로 정리하기로 했다. 즉시 취소·좌석 해제 완료는 보장하지 않는다.
  다시 방문했을 때는 서버의 실제 상태에 따라 종료 안내 또는 유효한 기존 주문 복원을 적용한다.
- **새로고침과 닫기의 구분 보완 필요:** 현재 pagehide 처리는 둘을 구분하지 않고 같은 취소 경로를 호출한다.
  새로고침 유지·복원 결정은 변경하지 않는다. 두 정책을 구분하는 설계·구현 가능성·검증은 남은 기술 검토이며,
  현재 구현이 모두 충족한다고 보지 않는다. 닫기 감지나 취소 요청 전달의 성공을 보장하는 정책도 아니다.
- **선택 새로고침 확정·유지:** 같은 날 문답에서 주문 전 선택 화면은 새로고침 시 목록 초기화·서버 선택 해제 시도의
  현재 방식을 유지하기로 했다. 자동 복원·자동 재선택은 하지 않는다. 해제 실패 시 원래 좌석별 기한까지 선택이 남을 수
  있으므로 화면 초기화와 실제 해제 성공을 구분한다. 주문 화면의 새로고침 유지·복원과는 다른 정책이다.
- **전체 선택 해제 확정·범위 유지:** 같은 날 문답에서 같은 회원·같은 회차의 다른 탭 선택도 함께 해제하는 범위를
  유지하고 사용자에게 그 영향을 안내하기로 했다. 현재 이탈 안내에는 다른 탭에 대한 설명이 없어 보완이 필요하다.
  해제 후 다른 탭의 표시도 서버 실제 상태에 맞춰야 하며, 그 갱신 방식은 별도 검토한다.
- **지연된 해제의 새 선택 보호 확정·미적용:** 같은 날 문답에서 예전 전체 해제 요청이 지연·재시도돼도 이후의
  새 선택을 해제하지 않도록 보완하기로 했다. 같은 회원이 같은 좌석을 다시 선택한 경우도 구분해야 한다.
  회원·회차 단위의 전체 해제 범위는 유지하고, 기존 요청의 대상 선택과 이후의 새 선택을 구분한다.
- **현재 구현의 차이:** 전체 해제는 처리 시점의 회원 선택을 읽으며 개별 해제 스크립트는 회원 소유 여부만 검사한다.
  지연된 요청이 새 선택도 해제할 수 있는 구조이므로 선택 식별·해제 판정·API 소비자의 보완이 필요하다.
  새 선택의 만료 시각을 유지하고 잘못된 해제 알림을 만들지 않는 기준도 검증해야 한다. 실행으로 재현한 결과는 아니다.
- **남은 결정·제안:** 선택 화면의 닫기·다른 화면 이동, 선택·해제 요청 구분의 구현 방식,
  해제 실패 후 복구 경로, 같은 주문의 다중 탭에서 닫기 적용 범위,
  다른 화면 이동, 입력값 저장 여부를 따로 정한다.
  재연결·포커스 복귀·오류·만료 후 서버 기준으로 선택·현황·주문을 재조정할 수용 기준을 만든다.
  화면은 참고 정보이고 확보 결과는 서버로 확인한다.

근거: [FE 선택 저장](../../ticket-fe/src/store/bookingStore.ts),
[좌석 페이지](../../ticket-fe/src/features/booking/components/page/SeatPageClient.tsx),
[선택 이탈 처리](../../ticket-fe/src/features/booking/hooks/useSeatLeaveGuard.ts),
[서버 전체 선택 해제](../src/main/java/com/ticket/booking/selection/domain/SeatSelectionService.java),
[해제 대상·소유권 판정](../src/main/java/com/ticket/booking/selection/persistence/RedissonSeatSelectionStore.java),
[결제 대기 화면](../../ticket-fe/src/features/booking/components/page/PaymentPageClient.tsx),
[서버 주문 상세](../src/main/java/com/ticket/booking/order/usecase/GetOrderDetailUseCase.java),
[주문 이탈 처리](../../ticket-fe/src/features/booking/hooks/useOrderLeaveGuard.ts),
[백그라운드 취소 요청](../../ticket-fe/src/features/booking/api/index.ts),
[이탈 처리](../../ticket-fe/src/features/booking/hooks/useBookingLeaveGuard.ts),
[실시간 처리](../../ticket-fe/src/features/booking/hooks/useSeatSocket.ts).

### 시간 표시와 서버 기한

- **현재:** Core 업무 시간대는 Asia/Seoul이며 주문 기한은 LocalDateTime으로 반환한다. FE는 절대 기한을
  `new Date(...)`와 브라우저 `Date.now()`로 비교한다. 기한이 있으면 서버의 remainingSeconds는 쓰지 않는다.
- **조건부 영향:** 기기 시계가 어긋나거나 브라우저 시간대가 다르면 표시 시간과 서버 판정이 다를 수 있다.
  타이머 00:00 표시만으로 자동 취소·페이지 이동·버튼 차단을 완료하는 흐름도 없다.
- **제안:** 시간대가 명확한 서버 시각·기한 또는 서버 기준 남은 시간으로 계약을 통일하고, 0초 후 상태 확인 및
  허용 행동을 정한다. URL의 holdExpiresAt은 사용자가 바꿀 수 있으므로 서버 상태의 원본으로 사용하지 않는다.

근거: [서버 Clock](../src/main/java/com/ticket/shared/config/SystemClockConfig.java),
[FE 주문 응답 처리](../../ticket-fe/src/features/booking/api/index.ts),
[타이머](../../ticket-fe/src/features/booking/components/common/bookingTimer/BookingTimer.tsx).

### 상태 변경 후 알림 실패·보상 실패

- **현재:** 선택 변경은 Redis를 바꾼 다음 WebSocket 발행을 같은 락 안에서 수행한다. 발행 실패가 전파되면
  요청 실패와 실제 선택 상태가 다를 수 있다. DB 주문 실패 후 Redis 선점 해제도 실패할 수 있다.
- **현재 보완:** 주문 이벤트는 DB publication으로 후속 처리를 재시도한다. 후속 좌석 해제는 새 소유권을 확인해
  늦은 해제로 새로운 선점을 덮어쓰지 않도록 처리한다. 이러한 방어는 유지할 가치가 있다.
- **결정·제안:** 알림 실패와 핵심 상태 변경 성공을 어떤 응답으로 표현할지 정한다. 실패 후 재조회·선점 정리와
  장기간 처리되지 않은 이벤트의 운영 복구 기준을 마련한다. 단순히 ‘실패면 전부 롤백된다’고 명세하지 않는다.

근거: [선택 변경](../src/main/java/com/ticket/booking/selection/usecase/SeatSelectionWriter.java),
[주문 실패 보상](../src/main/java/com/ticket/booking/order/usecase/CreateOrderUseCase.java),
[후속 해제](../src/main/java/com/ticket/booking/event/HoldReleaser.java),
[이벤트 재처리](../src/main/java/com/ticket/shared/config/EventPublicationMaintenance.java).

## 회원·권한·대기열의 보완 후보

| 항목 | 확인한 사실·영향 | 필요한 결정·수정 후보 | 근거 |
| --- | --- | --- | --- |
| 소셜 자동 연결 | 검증된 같은 이메일이면 기존 회원에 새 공급자 연결 | 자동 연결의 계정 소유 확인·동의·충돌 안내 | [회원 처리](../src/main/java/com/ticket/member/usecase/SocialAccountProvisioningService.java) |
| 로그아웃·탈퇴 효력 | 접근 토큰은 DB 회원 상태를 조회하지 않고 인증된다. 탈퇴는 진행 중 주문·선점을 정리하지 않는다 | 즉시 차단 범위·전체 기기·진행 중 주문 처리. 모든 쓰기·구독 경로의 활성 회원 조건 통일 | [접근 토큰](../src/main/java/com/ticket/security/token/AccessTokenAuthenticatorService.java), [탈퇴](../src/main/java/com/ticket/security/auth/WithdrawCurrentMemberUseCase.java) |
| 갱신 동시성 | 서버는 갱신 토큰을 한 번 소비한다. FE 공통 fetch에서 여러 401 요청이 각각 갱신할 수 있다 | FE 갱신 요청 공유·탭 간 조정, 서버 중복 갱신·응답 유실 정책 | [갱신](../src/main/java/com/ticket/security/auth/RefreshAuthTokenUseCase.java), [fetch](../../ticket-fe/src/lib/api.ts) |
| 실시간 권한 | STOMP CONNECT 인증만 확인한다. 명시적 SUBSCRIBE/SEND 목적지 권한·회차 입장 검사는 확인되지 않는다 | 구독에 입장 자격이 필요한지 결정. 클라이언트의 상태 이벤트 송신은 차단하도록 검토하고 재현 검사 | [인터셉터](../src/main/java/com/ticket/booking/websocket/WebSocketAuthInterceptor.java), [설정](../src/main/java/com/ticket/booking/websocket/WebSocketConfig.java) |
| 연결 중 자격 만료 | CONNECT 이후 토큰 만료·탈퇴를 다시 검사하는 경로가 없다 | 연결 최대 시간·재인증·강제 종료·구독 권한 회수 | [인터셉터](../src/main/java/com/ticket/booking/websocket/WebSocketAuthInterceptor.java) |
| 탈퇴 데이터 | 이메일·소셜 식별자를 변경하지만 이름·찜·주문은 남는다 | 보관 목적·기간·접근 범위·집계 제외·재가입 연결. 외부 해제 실패의 재처리 | [회원](../src/main/java/com/ticket/member/domain/Member.java), [소셜 해제](../src/main/java/com/ticket/security/oauth/ProviderSocialAccountUnlinker.java) |
| Queue 대상 검증 | Queue는 Core 회차 존재·판매 기간을 조회하지 않는다 | 회차 등록·접수 조건을 동기화할 계약과 갱신 실패 시 처리 | [Queue 서비스](../../ticket-queue/src/main/java/com/ticket/queue/application/AdmissionService.java) |
| Queue 공정성 | 시간 구간·분할별 순서·순환 처리가 있다. 응답의 seq는 전역 순번이 아니다 | 사용자 순위·동일 구간 순서·시간 오차·엄격한 FIFO 여부를 정의 | [순서 계산](../../ticket-queue/src/main/java/com/ticket/queue/application/QueueShardSlotCalculator.java), [진행](../../ticket-queue/src/main/java/com/ticket/queue/infra/RedisAdmissionStateStore.java) |
| Queue 재입장·퇴장 | 기존 입장 토큰은 유효 중 재사용. 만료 뒤 유효 대기표로 새 입장 가능. 완료·취소 시 즉시 세션 반환 API는 없다 | 예매당 입장 횟수·재대기·세션 연장·종료 통지 | [입장 Lua](../../ticket-queue/src/main/resources/redis/admit_queue_session.lua) |
| Queue 처리량 | `maxAdmitPerSecond` 설정을 진행 호출당 예산으로 사용한다. 시간당 토큰 예산 계산은 없다 | 스케줄 간격 변경·수평 확장에도 지킬 실제 입장률 정의, 활성 세션과 미입장 허용 순서 구분 | [진행](../../ticket-queue/src/main/java/com/ticket/queue/infra/RedisAdmissionStateStore.java), [스케줄러](../../ticket-queue/src/main/java/com/ticket/queue/application/AdvancementScheduler.java) |
| Queue 캐시 | 공개 상태는 캐시 가능 구조이고 실제 입장은 origin에서 다시 확인한다 | 상태 지연·역행·예상 대기 시간의 허용치. 캐시값만으로 입장 성공 판단 금지 | [Queue API](../../ticket-queue/src/main/java/com/ticket/queue/api/AdmissionController.java), [입장 검사](../../ticket-queue/src/main/resources/redis/enter_queue.lua) |

실시간 SEND가 실제 악용 가능한지, 외부 프록시가 추가 권한을 검사하는지는 실행으로 검증하지 않았다.
위 항목은 권한 정책과 구현의 빈틈을 확인할 우선 검증 대상으로 기록한다.

## 상품·검색·찜·데이터의 보완 후보

| 항목 | 확인한 사실·영향 | 필요한 결정·수정 후보 | 근거 |
| --- | --- | --- | --- |
| 표시와 실제 판매 | 공연 표시 기간과 회차 접수 정책은 독립. FE는 공연 표시 상태로 버튼 영역을 결정 | 회차별 진입 방식으로 최종 행동을 결정하고 표시 불일치 안내 | [표시](../src/main/java/com/ticket/show/domain/show/DisplaySaleWindow.java), [상세 화면](../../ticket-fe/src/features/shows/components/detail/ShowDetailView.tsx) |
| 최신 API 설명 | controller는 최신 등록 10개로 설명하지만 구현은 마감하지 않은 상품 우선 | 정책 유지 시 API·화면 설명 보완, 변경 시 정렬·커서 함께 변경 | [controller](../src/main/java/com/ticket/show/endpoint/ShowController.java), [조회](../src/main/java/com/ticket/show/persistence/ShowQuerydslRepository.java) |
| 인기 기준 | 저장된 조회수 정렬. 상세 조회에서 증가 경로 없음 | 실제 조회 집계·어뷰징·집계 주기 또는 다른 인기 지표 | [상세](../src/main/java/com/ticket/show/usecase/GetShowDetailUseCase.java) |
| 검색 개수·범위 | 제목만 검색. 공연 임박순은 오늘 이후 제한, count는 제한 없음 | 같은 조건의 결과 수·검색어 공백·검색 대상 확정 | [검색 조회](../src/main/java/com/ticket/show/persistence/ShowQuerydslRepository.java) |
| 오픈 시각 경계 | 정확히 오픈 시각에는 ON_SALE이면서 오픈 예정 조건에도 포함될 수 있다 | 예정/판매 중 경계 통일·경계 후 캐시 갱신 | [조회](../src/main/java/com/ticket/show/persistence/ShowQuerydslRepository.java) |
| 커서 연속성 | 정렬 검증은 있지만 필터의 동일성을 커서에 묶지 않는다. 시간·조회수·상품 변경 중 결과는 변할 수 있다 | 필터 변경 시 초기화·목록 일관성 허용 범위 | [커서](../src/main/java/com/ticket/show/usecase/ShowCursor.java) |
| 찜 대상·빈 페이지 | 없는 공연의 찜 허용, 목록에서만 제거하여 찜 페이지와 표시 건수가 다를 수 있다 | 존재 검사·삭제 후 정리·빈 페이지 다음 조회·탈퇴 집계 | [찜 API](../src/main/java/com/ticket/like/endpoint/LikeController.java), [목록](../src/main/java/com/ticket/show/usecase/GetMyShowLikesUseCase.java) |
| 찜 중복 경합 | 선행 존재 검사 후 저장. 중복 저장 예외를 정상 성공으로 통일하지 않으며 실제 flush 시점도 영향 | 동시 추가도 성공으로 볼지 결정하고 DB 제약·트랜잭션 경로 검증 | [추가](../src/main/java/com/ticket/like/usecase/AddLikeUseCase.java) |
| 가격 원본 | 등급 표시 가격과 회차 판매 좌석 단가는 별도 저장. 주문은 후자를 사용 | 가격 동기화·변경 가능 시점·선택 후 가격 변경 안내·주문 스냅샷 유지 | [가격 모델](../src/main/java/com/ticket/show/domain/performance/PerformanceGrade.java), [주문 저장](../src/main/java/com/ticket/booking/order/usecase/PendingOrderCreator.java) |
| 무료 가격 보완 | 옛 좌석 단가 null을 migration에서 0으로 채운다 | 실제 무료 상품과 누락 가격을 구분·게시 전 점검. 기존 migration 수정 대신 데이터 검증·새 변경 사용 | [가격 migration](../src/main/resources/db/migration-vendor/h2/booking/V3__tighten_performance_seat_grade_and_price.sql), [Oracle](../src/main/resources/db/migration-vendor/oracle/booking/V3__tighten_performance_seat_grade_and_price.sql) |
| 상품 게시 완전성 | 운영자 게시 흐름은 없고 초기 데이터로 제공. 연결 누락 시 제외·404·500 처리가 혼재 | 게시 전 회차·좌석·등급·가격·판매 정책 필수 검사와 변경 책임 | [좌석 조립](../src/main/java/com/ticket/booking/seat/usecase/GetPerformanceSeatMapUseCase.java), [주문 저장](../src/main/java/com/ticket/booking/order/usecase/PendingOrderCreator.java) |
| 회원별 화면 캐시 | 찜·내 정보 query key에 회원 ID가 없다 | 로그인·로그아웃·계정 전환 시 사용자 데이터 캐시 초기화가 충분한지 확인 | [query key](../../ticket-fe/src/lib/queryKeys.ts), [인증 상태](../../ticket-fe/src/store/authStore.ts) |

## 결제 구현 전에 필요한 명세

현재 Payment·Ticket 모델을 근거로 ‘결제·발권이 완료됐다’고 간주하면 안 된다. 다음 항목은 선택한 제공 범위에
포함되는 경우에 한해 정의한다. 지금 모든 결제수단·배송·환불 기능을 추가하라는 뜻은 아니다.

| 주제 | 반드시 답할 질문 |
| --- | --- |
| 승인과 확정 | PG 승인·서버 검증·주문 확정·좌석 판매 완료·발권 중 무엇이 고객의 성공 기준인가? |
| 승인과 만료 경합 | 선점 기한 직후 승인됐다면 확정할지 환불할지? 다른 회원에게 좌석이 넘어간 경우는? |
| 결제 재시도 | 이전 승인 결과가 불명확하면 조회 먼저 할지? 같은 주문의 여러 결제 시도를 어떻게 식별할지? |
| 승인 금액 | 서버 주문 금액·통화·할인·수수료·배송비와 PG 결과를 어떻게 대조할지? |
| 실패 복구 | 승인됐는데 주문 확정·발권·알림에 실패하면 재처리·환불·고객 안내를 누가 수행할지? |
| 티켓 | 주문 좌석당 발급 수·중복 발급 방지·이름/양도·입장 확인·재입장·사용 후 취소는? |
| 수령 | 실제 지원 수령방법·배송 마감·연락처·주소 유효성·변경 가능 시점은? |
| 취소·환불 | 결제 전 취소와 결제 후 환불, 부분 취소, 기한·수수료, 공연 취소 처리를 어떻게 나눌지? |
| 동의 | 필요한 동의 내용·버전·시각·철회와 조회 권한을 어떻게 보관할지? |
| 운영 | 승인·주문·좌석·티켓의 불일치를 어떤 주기·기준으로 찾아 복구할지? |

근거: [주문 confirm](../src/main/java/com/ticket/booking/order/domain/Order.java),
[결제 상태](../src/main/java/com/ticket/payment/domain/Payment.java),
[티켓 상태](../src/main/java/com/ticket/booking/ticket/domain/Ticket.java),
[결제 화면](../../ticket-fe/src/features/booking/components/page/PaymentPageClient.tsx).

## QA 검토 시나리오

아래는 **검토용 수용 기준 후보**다. 정책 미정인 행은 먼저 결과를 확정해야 한다. 지금 모두 자동 테스트로
구현돼 있거나 모두 실행해 통과한 목록이 아니다.

| 관련 규칙 | 조건·행동 | 합의하거나 검증할 결과 |
| --- | --- | --- |
| M-02~04 | 같은 검증 이메일의 다른 소셜 계정 / 검증되지 않은 이메일 | 연결 여부·본인 확인·동의·거절 안내가 정책과 일치 |
| M-06 | 여러 요청이 동시에 401 / 갱신 응답 유실 | 불필요한 로그아웃 없이 정책에 맞는 복구, 기존 토큰 재사용 결과 명확 |
| M-07~10 | 로그아웃·탈퇴 후 기존 토큰·WebSocket·진행 중 주문 | 허용 범위가 모든 경로에서 일치, 좌석 정리·데이터 보관 정책 확인 |
| S-03~10 | 마감 상품·진행 중 공연·동일 조회수·공백 검색·필터 변경 | 목록 정의·정렬·결과 수·커서가 일치 |
| S-11, O-05 | 같은 공연의 서로 다른 배치·가격을 가진 두 회차 | 선택 회차의 좌석·단가·서버 주문 요약 일치 |
| S-12 | 판매 정책·좌석 등급·공연장·가격 누락 상품 | 게시·조회·예매의 결정된 실패 결과, 조용한 0원 판매 없음 |
| L-01~05 | 없는 공연 찜·동시 중복 찜·탈퇴 후 목록 | 존재·멱등·집계·빈 페이지 정책 일치 |
| E-01 | 접수 시작 전 / 정확히 시작 / 정확히 마감 / 마감 직후 | 시간 경계 규칙 일치 |
| E-01, O-08 | 마감 직전 생성한 주문·마감 후 기존 선택으로 주문·주문 처리 및 락 대기 중 마감 도달 | 기존 주문 기한은 마감으로 줄이지 않음, 마감 후 새 주문은 불가. 처리 중 마감 판정 시점은 추가 합의 필요 |
| E-03~07 | DIRECT/QUEUE/판매 전 사전 대기열 | 화면 이동·자격 요구·허용 행동 일치 |
| E-07 | 자격 없음·만료·다른 회원·다른 회차 | 거절 이유별 재입장·안내, 확보된 자원 처리 확인 |
| Q-01~06 | 같은 회원 두 탭 참여·재입장·입장 시간 만료 | 대기표·순서·입장 기한 공유와 재대기 규칙 일치 |
| Q-02~04 | 같은 시간 구간·다른 분할·캐시 지연·활성 수용량 가득 참 | 정한 공정성·재시도·최종 입장 판정 유지 |
| B-03, B-11 | 상한-1 / 상한 / 상한+1석, 동시 좌석 선택 | 설정과 화면 안내 일치, 상한 우회 없음 |
| B-11 | 1석·2석 이상·제한 없음 설정, 0·음수 설정, 1석 상한의 두 탭·동시 선택 및 2석 주문 | 1석 설정 허용·기존 설정 유지·0과 음수 거절, 같은 회원·회차의 동시 선택은 최대 1석, 초과 주문 전체 거절. Java·H2·Oracle·화면 계약 일치 |
| B-11, O-01, O-03 | 같은 회원·회차의 여러 구매, 총 한도 직전·도달·초과, 다른 회원·회차, 동시·반복 요청 | 회차별 총 한도 지원, 나눠 구매해도 초과 불가·초과 주문 전체 거절, 회원·회차 구분 및 중복 합산 방지. 실제 값·구매 완료 후 취소·환불의 복구는 추가 합의 필요 |
| B-11, O-03, O-09~11 | 구매 완료와 유효한 결제 대기, 대기→완료 전환, 대기 취소·정확한 기한 도달, DB 만료 처리 지연 | 완료와 유효 대기 모두 합산·전환 중 중복 합산 없음, 대기 취소·기한 도달 후 제외·기존 완료 수량 유지, 새 주문에 합계와 요청 수량을 더해 검사. 좌석 재확보와 나머지 예매 조건은 별도 확인 |
| B-03, B-11, O-01 | 남은 총 한도 0·1석, 기존 선택 상한이 더 작은 경우, 다른 탭·동시 선택, 선택 후 주문 생성 시 재검사 | 남은 수량과 기존 상한 모두 적용, 탭·동시 요청으로 초과 선택 불가, 초과 선택은 기존 자원 유지·새 성공 알림 없음, 주문 시 최신 한도와 나머지 조건 재검사 |
| B-11, O-01, O-03 | 명시적인 총 제한 없음과 숫자 총 한도, 기존 선택·주문 상한이 있는 회차 | 총 제한 없음을 선택해도 기존 상한·유효 주문 수·나머지 조건 유지, 누적 수량만으로 거절하지 않음, 화면·API에서 0석과 구분. 설정 누락·기존 데이터 전환은 추가 합의 필요 |
| B-04~08 | A의 선택 직전·정확한 만료·만료 후 B가 재선택 | 이전 선택으로 주문 불가, B 소유권 유지, A 표시 재조정 |
| B-04~05 | 본인 선택 성공 응답 유실 후 재요청·동시 중복 요청·타인 선택 | 유효한 같은 본인 선택만 기존 성공으로 응답, 선택 수·기한 유지, 선택 성공 알림 추가 생성 없음, 타인 선택을 본인 성공으로 반환하지 않음 |
| B-04~05, B-08 | 정확한 선택 만료·만료 후 예전 요청 재도착·사용자의 새 선택·새 선택 후 예전 요청 재도착 | 예전 요청은 만료 안내·새 선택 및 알림 없음, 새 요청은 당시 조건 확인 후 별도 5분, 이미 생긴 새 선택·선점의 소유권과 기한 보호 |
| B-07, O-17 | 예전 전체 해제 요청 지연·재시도 후 새 좌석 선택 또는 같은 좌석 재선택 | 이전 요청의 대상만 해제, 새 선택 소유권·기한 유지, 다른 탭의 대상 선택은 포함, 잘못된 새 선택 해제 알림 없음 |
| O-01 | 선택 없음·타인 선택·부분 만료로 주문 | 거절되고 타인 선택·선점이 변경되지 않음 |
| O-03 | 같은 회원·회차 동시 주문 / 다른 회차 주문 | 정책에 맞는 진행 중 주문 수 유지 |
| O-04, O-15 | 여러 좌석 중 하나 충돌 / Redis 일부 실패 / DB 실패 / 보상 실패 | 부분 주문 없음, 남은 선점의 정리·복구 기준 확인 |
| O-05~07 | 화면·요청 값 조작 / 상품 가격 변경 / 배송 선택 | 서버 계산 금액·스냅샷·지원 범위와 일치 |
| O-03, O-09~14 | 정확한 기한 도달·만료 이벤트 누락·worker 비활성·취소와 만료 동시 | 기한 기준 만료 표시, 오래된 PENDING 자체가 재예매를 막지 않음, 새 선택·선점 소유권 보호. 기한 후 취소는 정상 응답에 EXPIRED 전달·만료 안내. 정리 지연·응답 필드는 추가 합의 필요 |
| O-12, O-16 | 주문·취소 성공 직후 응답 유실 후 반복 요청·동시 취소 | 본인의 CANCELED 반복 취소는 성공, EXPIRED는 정상 응답에 만료 상태 전달. 기존 종료 이력 유지·이벤트 추가 생성 없음. 주문 복구 경로와 확정 주문의 응답은 추가 합의 필요 |
| O-17, B-07, B-10 | 주문 및 선택 새로고침·다중 탭의 전체 선택 해제·뒤로가기·닫기·해제 요청 및 응답 유실 | 전체 해제는 같은 회원·회차의 다른 탭도 포함·영향 안내, 예전 요청 이후의 새 선택은 보호. 주문 새로고침은 유지·복원, 선택 새로고침은 초기화·해제 시도. 선택·요청 구분 구현·선택 닫기·다른 이탈·재연결은 후속 검토 필요 |
| B-10 | Redis 변경 후 알림 실패·누락·늦은 이벤트 | 성공 여부 확인·재조회로 회복, 새 점유를 빈자리로 확정하지 않음 |
| O-08~10 | 기기 시계 ±5분·다른 시간대·기한 URL 조작 | 표시의 한계가 서버 판정을 바꾸지 않음, 만료 후 허용 행동 일치 |
| P-01~07 | 승인·만료 동시, 승인 후 확정/발권 실패, 승인 응답 유실 | 결제 범위 채택 후 확정할 보상·대사·발권·환불 기준 |

## 처음부터 다시 진행한다면

1. **제공 범위를 정한다.** 지금은 ‘공연 조회 → 로그인 → 좌석 선택 → 결제 대기 주문 → 취소·만료’까지를
   하나의 범위 후보로 검토할 수 있다. 결제·발권을 포함한다면 해당 정책을 먼저 추가한다.
2. **공통 용어와 자원 소유 단위를 정한다.** 공연/회차, 선택/선점/확정, 회원/탭, 주문/결제 시도를 구분한다.
3. **핵심 흐름 하나의 규칙을 확정한다.** 한 회차 1석 주문의 조건·기한·가격·취소·실패를 먼저 결정한다.
   모든 기능의 정책을 완벽히 끝낼 때까지 개발을 멈추는 방식은 필요하지 않다.
4. **예시와 반례를 함께 쓴다.** 정상 예매와 함께 타인 좌석·마감 경계·중복 클릭·응답 유실을 적는다.
   기획·개발·QA가 동일한 결과를 말할 수 있으면 수용 기준으로 사용한다.
5. **구현·화면·API를 맞춘다.** 현재 불일치를 바꾸거나 의도된 차이로 명시한다. 정책 변경은 근거·영향·적용
   상태를 기록하고 데이터 변경은 새 migration과 전환 계획을 세운다.
6. **규칙에 맞는 테스트를 선택한다.** 계산·상태 전이는 단위 검사, 실제 Redis/DB·경합·보상은 통합 검사,
   회차·가격·진입·새로고침 연결은 브라우저 흐름 검사로 확인한다. 부하는 별도 수용량 목표를 정한 뒤 실행한다.

완료 기준은 테스트 개수가 아니라 **범위 내 각 규칙의 결정 상태·근거·예시·실패 결과·수정 영향이 연결돼 있고,
기획·개발·QA가 같은 기대 결과로 검토할 수 있는가**다. 미정인 부분을 테스트의 정답으로 자동 고정하지 않는다.

## 문서 작성의 검증 범위

새 문서는 기존 규칙·소스와 대조하고 링크·UTF-8 BOM 여부·저장소 문서 검사·diff 형식 검사로 검증한다.
서비스 동작은 변경하지 않았으므로 이번 작업에서 서비스 빌드·전체 테스트·실제 로그인·결제·부하 실행을
통과했다고 보고하지 않는다. 정책 결정 뒤 관련 수정과 재현 검사를 별도 작업으로 진행한다.
