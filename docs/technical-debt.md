# 기술 부채

아래 항목은 구조적으로 아직 해결하지 않았고, 현재 경계와 권장 방향만 기록한다.

대부분은 **설계** 부채(코드/구조 문제, 결정 없이도 손댈 수 있다)지만 일부는 **제품 결정**이
먼저 필요한 사안이다(코드 문제가 아니라 정책을 정해야 풀린다). "성격" 열로 구분한다.

| ID | 성격 | 현재 위치 | 현재 구조 | 왜 기술 부채인가 | 권장 해결 방향 | 보류 이유 | 호환성 주의 | 완료 조건 | 관련 코드 |
|---|---|---|---|---|---|---|---|---|---|
| TD-01 | 설계 | `member.oauth.application` | OAuth provider 원본 attributes가 app 흐름으로 유입될 여지가 있다 | provider 변경이 유스케이스에 전파된다 | infra에서 중립 DTO로 변환 | 이번 범위는 포트/이름 이동 | OAuth 응답 계약 유지 | provider별 adapter DTO와 테스트 분리 | `OAuth2MemberProvisioningService` |
| TD-02 | 설계 | `member.oauth.application` | `KakaoUnlinkService`가 provider protocol/config를 일부 안다 | 외부 API 세부사항이 app에 남는다 | Kakao unlink 전용 중립 포트로 캡슐화 | 동작 변경 위험 | unlink API 동작 유지 | app이 provider 타입을 참조하지 않음 | `KakaoUnlinkService` |
| TD-03 | 설계 | `show.domain` | 도메인이 HTTP 이미지 경로를 만든다 | 표현 계층 규칙이 domain에 유입된다 | image URL 변환을 API/infra adapter로 이동 | 응답 호환성 영향 분석 필요 | 기존 image JSON 유지 | domain이 경로 문자열을 생성하지 않음 | `ShowCardImagePathConverter` |
| TD-04 | 설계 | 각 module의 `web`[^td04] | app UseCase Output/View가 API 응답 타입으로 직접 노출된다 | API와 app 변경이 강하게 결합된다 | API response DTO로 변환 | 전 endpoint 계약 검토 필요 | JSON 필드/상태 유지 | controller 반환 타입이 API DTO | 각 `*Controller` |
| TD-06 | 설계 | `booking.seat.domain` | `PerformanceSeatService` 사용 여부가 불명확하다 | 미사용 코드 제거 판단이 어렵다 | 호출 그래프 확인 후 제거 또는 명시적 역할 부여 | 삭제는 이번 범위 밖 | public class 삭제 금지 | 사용처/삭제 결정 문서화 | `PerformanceSeatService` |
| TD-08 | 설계 | `booking.salespolicy.domain` | `QueueMode`, `QueueLevel` 명명이 책임을 충분히 설명하지 않는다 | 정책 의미와 실행 단계가 혼동될 수 있다 | 정책/단계 용어를 ADR로 확정 후 rename | API/claim 영향 검토 필요 | token claim 유지 | 의미와 wire mapping 고정 | `QueueMode`, `QueueLevel` |
| TD-09 | 설계 | `booking.admission.infrastructure` | admission token 책임을 설정/검증 관점으로 함께 표현한다 | 설정과 검증 책임이 분리되지 않는다 | token settings와 guard의 경계를 문서화 | 기존 구성키 유지 필요 | JWT claim/TTL 유지 | 책임별 테스트와 문서 일치 | `AdmissionTokenSettings`, `JwtAdmissionVerifier`(구 `JwtAdmissionGuard`) |
| TD-13 | 제품 결정 | `show.domain` | `Show.viewCount`를 증가시키는 코드가 없는데 `POPULAR` 정렬·커서 키로 쓰인다 | 제품 결정이 필요한 사안(비동기 증가 도입 or 정렬 폐기) | 상세 조회 시 비동기 증가, 또는 `POPULAR` 정렬 폐기 | 제품 결정 사안, 코드 문제 아님 | 정렬 API 계약 영향 분석 필요 | 결정 후 반영 | `Show.viewCount`, `ShowSort.POPULAR` |
| TD-14 | 설계 | `venue.domain` | `Venue.gapX`/`gapY`(좌석 간격)를 읽는 코드가 없다 | 죽은 컬럼일 가능성 | 소비자 없음을 재확인 후 컬럼 제거 여부 결정 | 컬럼 drop은 운영 migration이라 신중히 별도 결정 | 응답 영향 없음(비노출 필드) | 컬럼 제거 또는 실제 소비자 확인 | `Venue`, `VenueSummary.SeatMapLayout` |
| TD-15 | 설계 | `show.domain` | "판매 오픈 전에만 가격을 바꿀 수 있다"는 `PerformanceGrade`의 불변식인데, 그 판단 근거(접수 시각·좌석 편성 여부)가 Booking BC에 있다 | show가 혼자 판정할 수 없고 `show -> booking`은 순환이라 금지다. 지금은 가격 변경 메서드 자체가 없어 드러나지 않을 뿐 강제되는 규칙이 아니다 | (A) 잠금 기준을 "좌석 편성"으로 바꾸고 `EditPerformanceSeatsUseCase`가 show의 공개 command API로 잠금을 알린다(`booking -> show`라 순환 없고 snapshot 시점과 일치) / (B) booking이 편성 시 단가 불일치를 사후 감지 / (C) 문서 규칙으로만 유지 | 가격 변경 기능이 아직 없어 실제로 깨지지 않는다 — 관리자 CRUD 착수 시점에 결정한다 | 가격 snapshot 체인(`PerformanceGrade.price` -> `PerformanceSeat.unitPrice` -> `OrderSeat.unitPrice`) 의미 보존 | 잠금 주체·시점을 결정하고 테스트로 고정 | `PerformanceGrade`, `EditPerformanceSeatsUseCase`, `PerformanceSalesPolicy` |
| TD-16 | 설계 | `src/test/java/com/ticket/core/infra/support` | `ReadRepositoryTestSupport`/`InfraReadRepositoryTestSupport` 2개 파일만 legacy 이름 패키지 아래 남아 있다 | 옮길 legacy 코드가 아니라 별도 결정할 test 인프라 소유권 문제다 — 어느 module의 test support로 볼지, 아니면 module-neutral 공용 test 유틸 자리를 새로 둘지 결정이 필요하다 | test 지원 클래스의 소유권 모델을 정하고 그에 맞는 패키지로 옮긴다 | 여러 module 테스트가 참조해 이동 범위가 넓다 | 테스트 클래스 위치만 바뀌고 동작 불변 | 소유권 결정 후 실제 이동 | `ReadRepositoryTestSupport`, `InfraReadRepositoryTestSupport` |

**해소된 항목**: TD-10(`<module>.domain.**.command`에 정책·값 객체·서비스가 혼재)은 패키지를
모듈 → 계층으로 평탄화하면서 `command`/`model`/`query`/`store` 하위 패키지 자체가 사라져 전제가
없어졌다. ID는 재사용하지 않는다.

[^td04]: 컨트롤러가 booking/show/member 각 module의 `web`로 옮겨지며
문제의 소재도 함께 옮겨졌다 — module 경계와는 무관하게 여전히 유효한 항목이다.

## 제품 정책 결정 대기

TD 표와 달리 코드 문제가 아니라 **제품 결정이 없어 손대지 않은** 것들이다. 결정 없이 되살리거나
바꾸지 않는다.

| ID | 항목 | 왜 결정이 필요한가 | 관련 코드 |
|---|---|---|---|
| PD-01 | `RawPassword`의 주석 처리된 복잡도 정책(길이·영문·숫자·특수문자) | 되살리면 기존 회원의 로그인·가입 흐름에 영향이 있다 | `RawPassword` |
| PD-02 | `Email`의 `null` → 빈 문자열 변환 | OAuth2 흐름과 기존 데이터를 조사하기 전에는 바꾸지 않는다 | `Email` |
| PD-03 | `size` 파라미터의 최대값 불일치 | `GetShowsUseCase`(API 문서에 최대 100)와 `GetMyShowLikesUseCase`(`MAX_SIZE` 100) 외에는 근거가 없어 양수 조건만 적용했다. 검색과 판매 오픈 예정 목록의 상한은 결정이 필요하다 | `GetShowsUseCase`, `GetMyShowLikesUseCase` |
| PD-04 | `category` 파라미터의 필수 여부 불일치 | Swagger는 `required = true`지만 조회 구현은 필터로 다룬다. 필수 여부 결정이 필요하다 | `GetLatestShowsUseCase`, `GetSaleStartApproachingShowsUseCase` |
| PD-05 | path·query 타입 불일치 시 500 응답(예: `/api/v1/shows/abc`) | Bean Validation 이전 단계인 binding 실패라 이번 범위 밖이며, 400으로 바꾸려면 공개 status 변경 결정이 필요하다 | — |

## 보류 원칙

위 항목은 구현하지 않는다. 특히 외부 JSON, HTTP 상태, DB schema, Redis key, JWT claim, WebSocket destination을 바꾸지 않고 해결할 수 있는 설계와 단계별 호환성 계획을 먼저 확정한다.
