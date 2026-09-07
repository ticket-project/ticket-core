# 기술 부채

이번 리팩터링에서는 아래 항목을 구조적으로 해결하지 않고, 현재 경계와 권장 방향만 기록한다.

> **경로 갱신(2026-09-02)**: Spring Modulith 전환(Task 4~10)으로 아래 클래스는 모두 옛 계층형
> `core-api/core-app/core-domain/core-infra` 경로에서 각자의 Application Module
> `<module>.**`로 옮겨졌다. "현재 위치"·"관련 코드" 열은 실제 현재 경로로 갱신했다 —
> 다만 각 항목이 가리키는 설계 문제 자체(app이 provider 세부사항을 안다, 계층/책임 혼재 등)는
> 모듈이 바뀌었다고 해소되지 않았고 이번 작업 범위도 아니었다. TD-04·TD-07은 모듈 전환 과정에서
> 상태가 바뀌어 별도 각주를 달았다.

| ID | 현재 위치 | 현재 구조 | 왜 기술 부채인가 | 권장 해결 방향 | 보류 이유 | 호환성 주의 | 완료 조건 | 관련 코드 |
|---|---|---|---|---|---|---|---|---|
| TD-01 | `member.application.auth.oauth2` | OAuth provider 원본 attributes가 app 흐름으로 유입될 여지가 있다 | provider 변경이 유스케이스에 전파된다 | infra에서 중립 DTO로 변환 | 이번 범위는 포트/이름 이동 | OAuth 응답 계약 유지 | provider별 adapter DTO와 테스트 분리 | `OAuth2MemberProvisioningService` |
| TD-02 | `member.application.auth.oauth2` | `KakaoUnlinkService`가 provider protocol/config를 일부 안다 | 외부 API 세부사항이 app에 남는다 | Kakao unlink 전용 중립 포트로 캡슐화 | 동작 변경 위험 | unlink API 동작 유지 | app이 provider 타입을 참조하지 않음 | `KakaoUnlinkService` |
| TD-03 | `show.domain.show.image` | 도메인이 HTTP 이미지 경로를 만든다 | 표현 계층 규칙이 domain에 유입된다 | image URL 변환을 API/infra adapter로 이동 | 응답 호환성 영향 분석 필요 | 기존 image JSON 유지 | domain이 경로 문자열을 생성하지 않음 | `ShowCardImagePathConverter` |
| TD-04 | 각 module의 `web`[^td04] | app UseCase Output/View가 API 응답 타입으로 직접 노출된다 | API와 app 변경이 강하게 결합된다 | API response DTO로 변환 | 전 endpoint 계약 검토 필요 | JSON 필드/상태 유지 | controller 반환 타입이 API DTO | 각 `*Controller` |
| TD-05 | 해결됨[^td05] | — | — | — | — | — | — | — |
| TD-06 | `booking.domain.{performanceseat.command, hold.command}` | `PerformanceSeatService`, `HoldReleaseLockException` 사용 여부가 불명확하다 | 미사용 코드 제거 판단이 어렵다 | 호출 그래프 확인 후 제거 또는 명시적 역할 부여 | 삭제는 이번 범위 밖 | public class 삭제 금지 | 사용처/삭제 결정 문서화 | 해당 클래스 |
| TD-07 | 해결됨[^td07] | — | — | — | — | — | — | — |
| TD-08 | `show.domain.queue` | `QueueMode`, `QueueLevel` 명명이 책임을 충분히 설명하지 않는다 | 정책 의미와 실행 단계가 혼동될 수 있다 | 정책/단계 용어를 ADR로 확정 후 rename | API/claim 영향 검토 필요 | token claim 유지 | 의미와 wire mapping 고정 | `QueueMode`, `QueueLevel` |
| TD-09 | `booking.infrastructure.admission` | admission token 책임을 설정/검증 관점으로 함께 표현한다 | 설정과 검증 책임이 분리되지 않는다 | token settings와 guard의 경계를 문서화 | 기존 구성키 유지 필요 | JWT claim/TTL 유지 | 책임별 테스트와 문서 일치 | `AdmissionTokenSettings`, `JwtAdmissionVerifier`(구 `JwtAdmissionGuard`) |
| TD-10 | 각 module의 `domain` | command 패키지에 정책/값 객체/서비스가 혼재한다 | 계층과 책임 판별이 어렵다 | policy/model/feature root로 재분류 | 대규모 이동으로 별도 단계 필요 | import만 변경, 동작 유지 | domain command 잔존 목록과 예외 문서화 | `<module>.domain.**.command` |
| TD-11 | 해결됨[^td11] | — | — | — | — | — | — | — |
| TD-12 | `show.domain.show` | `Show.getBookingStatus`와 `infrastructure`의 `BookingStatusPredicateFactory`가 "예매 가능 여부" 규칙을 각자 구현한다 | 한쪽만 바뀌면 조용히 어긋난다 | `@Embeddable SaleWindow` + `statusAt(now)`로 통합, 두 구현이 같은 결과를 내는지 파라미터화 테스트로 고정 | Q-path 변경이 venue 분리와 겹쳐 별도 단계로 미룸 | 응답 값 불변 | 단일 구현 + 회귀 테스트 | `Show`, `BookingStatusPredicateFactory` |
| TD-13 | `show.domain.show` | `Show.viewCount`를 증가시키는 코드가 없는데 `POPULAR` 정렬·커서 키로 쓰인다 | 제품 결정이 필요한 사안(비동기 증가 도입 or 정렬 폐기) | 상세 조회 시 비동기 증가, 또는 `POPULAR` 정렬 폐기 | 제품 결정 사안, 코드 문제 아님 | 정렬 API 계약 영향 분석 필요 | 결정 후 반영 | `Show.viewCount`, `ShowSort.POPULAR` |
| TD-14 | `venue.domain.venue` | `Venue.gapX`/`gapY`(좌석 간격)를 읽는 코드가 없다 | 죽은 컬럼일 가능성 | 소비자 없음을 재확인 후 컬럼 제거 여부 결정 | 컬럼 drop은 운영 migration이라 신중히 별도 결정 | 응답 영향 없음(비노출 필드) | 컬럼 제거 또는 실제 소비자 확인 | `Venue`, `VenueSummary.SeatMapLayout` |

[^td04]: 컨트롤러가 booking/show/member 각 module의 `web`로 옮겨지며
문제의 소재도 함께 옮겨졌다 — module 경계와는 무관하게 여전히 유효한 항목이다.

[^td05]: `core.api.support`에 마지막까지 남아 있던 `ShowLikeCursorCodec`은 찜(showlike)이 catalog
module로 흡수되며 `catalog.web.support.cursor`로 옮겨졌다. 이후 ADR 0006의 BC 재편으로
`catalog`가 `show`로 개명되며 지금은 `show.web.support.cursor`에 있다(찜 use case·endpoint는
favorite 분리 이후에도 show에 남는다). `core.api.support`는 이제 없다 — 이 항목은 완료됐다.

[^td07]: `CommonCode*Entity`는 Task 10(metadata module 추출)에서 사용처가 0임을 확인하고
삭제했다 — 참조하는 Flyway migration이나 테이블도 없었다. 이 항목은 완료됐다.

[^td11]: A2(정책을 Booking BC의 `PerformanceSalesPolicy`로 이관)를 구현했다(ADR 0006 "Performance의
책임 혼재" 후속 결정, 2026-09-07). `Performance`(show)는 이제 회차 일정만 소유하고,
`booking.domain.performancepolicy.model.PerformanceSalesPolicy`가 예매 접수 기간·Hold 한도·대기열
진입 정책을 소유한다. `show.BookingPolicyLookup`/`BookingPolicySnapshot`/`PerformanceQueuePolicy`/
`QueueActivation`/`BookingPolicyValidator`/`BookingEntryResolver`는 제거됐고, booking이
`GET /api/v1/booking/performances/{id}/entry`로 진입 상태를 공개한다. 기존 정책 데이터는 booking
V6 migration이 손실 없이 backfill했다. FE 후속(`ticket-fe`가 이 새 API로 DIRECT/QUEUE를 분기하는
작업)은 이 항목의 범위가 아니다 — `development.md`의 "미구현 또는 후속 범위"를 본다.

## 보류 원칙

위 항목은 구현하지 않는다. 특히 외부 JSON, HTTP 상태, DB schema, Redis key, JWT claim, WebSocket destination을 바꾸지 않고 해결할 수 있는 설계와 단계별 호환성 계획을 먼저 확정한다.
