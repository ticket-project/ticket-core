# 기술 부채

이번 리팩터링에서는 아래 항목을 구조적으로 해결하지 않고, 현재 경계와 권장 방향만 기록한다.

| ID | 현재 위치 | 현재 구조 | 왜 기술 부채인가 | 권장 해결 방향 | 보류 이유 | 호환성 주의 | 완료 조건 | 관련 코드 |
|---|---|---|---|---|---|---|---|---|
| TD-01 | `core-app/auth/oauth2` | OAuth provider 원본 attributes가 app 흐름으로 유입될 여지가 있다 | provider 변경이 유스케이스에 전파된다 | infra에서 중립 DTO로 변환 | 이번 범위는 포트/이름 이동 | OAuth 응답 계약 유지 | provider별 adapter DTO와 테스트 분리 | `OAuth2MemberProvisioningService` |
| TD-02 | `core-app/auth/oauth2` | `KakaoUnlinkService`가 provider protocol/config를 일부 안다 | 외부 API 세부사항이 app에 남는다 | Kakao unlink 전용 중립 포트로 캡슐화 | 동작 변경 위험 | unlink API 동작 유지 | app이 provider 타입을 참조하지 않음 | `KakaoUnlinkService` |
| TD-03 | `core-domain/show/image` | 도메인이 HTTP 이미지 경로를 만든다 | 표현 계층 규칙이 domain에 유입된다 | image URL 변환을 API/infra adapter로 이동 | 응답 호환성 영향 분석 필요 | 기존 image JSON 유지 | domain이 경로 문자열을 생성하지 않음 | `ShowCardImagePathConverter` |
| TD-04 | `core-api/controller` | app UseCase Output/View가 API 응답 타입으로 직접 노출된다 | API와 app 변경이 강하게 결합된다 | API response DTO로 변환 | 전 endpoint 계약 검토 필요 | JSON 필드/상태 유지 | controller 반환 타입이 API DTO | 각 `*Controller` |
| TD-05 | `core-api` | support 패키지 경계가 일부 루트와 불일치한다 | 공통 기능의 소유 계층이 모호하다 | support 기능별 module ownership 재정의 | 이번 작업에서 범위 제외 | cursor/cookie wire 유지 | architecture 문서와 구조 테스트 일치 | `core-api/support` |
| TD-06 | `core-domain` | `PerformanceSeatService`, `HoldReleaseLockException` 사용 여부가 불명확하다 | 미사용 코드 제거 판단이 어렵다 | 호출 그래프 확인 후 제거 또는 명시적 역할 부여 | 삭제는 이번 범위 밖 | public class 삭제 금지 | 사용처/삭제 결정 문서화 | 해당 클래스 |
| TD-07 | `core-domain/commoncode` | CommonCode entity 사용처가 불명확하다 | 모델 소유와 persistence 목적이 모호하다 | 사용처 조사 후 aggregate/model 재분류 | DB 영향 분석 필요 | 테이블/컬럼 유지 | 사용처와 owner 확정 | `CommonCode*Entity` |
| TD-08 | `core-domain/queue` | `QueueMode`, `QueueLevel` 명명이 책임을 충분히 설명하지 않는다 | 정책 의미와 실행 단계가 혼동될 수 있다 | 정책/단계 용어를 ADR로 확정 후 rename | API/claim 영향 검토 필요 | token claim 유지 | 의미와 wire mapping 고정 | `QueueMode`, `QueueLevel` |
| TD-09 | `core-infra/admission` | admission token 책임을 설정/검증 관점으로 함께 표현한다 | 설정과 검증 책임이 분리되지 않는다 | token settings와 guard의 경계를 문서화 | 기존 구성키 유지 필요 | JWT claim/TTL 유지 | 책임별 테스트와 문서 일치 | `AdmissionTokenSettings`, `JwtAdmissionGuard` |
| TD-10 | `core-domain` | command 패키지에 정책/값 객체/서비스가 혼재한다 | 계층과 책임 판별이 어렵다 | policy/model/feature root로 재분류 | 대규모 이동으로 별도 단계 필요 | import만 변경, 동작 유지 | domain command 잔존 목록과 예외 문서화 | `domain/**/command` |

## 보류 원칙

위 항목은 구현하지 않는다. 특히 외부 JSON, HTTP 상태, DB schema, Redis key, JWT claim, WebSocket destination을 바꾸지 않고 해결할 수 있는 설계와 단계별 호환성 계획을 먼저 확정한다.
