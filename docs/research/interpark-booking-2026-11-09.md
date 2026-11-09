# NOL 인터파크 예매 흐름 관찰(2026-11-09)

인터파크(NOL 티켓)가 좌석 상태와 대용량 트래픽을 어떻게 다루는지 실제 화면으로 따라가며 기록했다. Core 사용자당 CPU 설계(점유 좌석 응답 형식)를 정하는 근거로 썼다.

- **방법**: 로그인한 Chrome에서 화면을 직접 따라가며 브라우저가 보낸 요청(경로, 크기, 응답 헤더, 걸린 시간)과 화면 JavaScript 코드를 읽었다. 서버 내부는 보지 못했으므로 서버 쪽 설명은 모두 추정이다.
- **대상 공연**: 뮤지컬 〈레베카〉·〈오페라의 유령〉(거의 매진, 조회·대기열·좌석 화면까지), 〈캣츠〉 광주(남은 좌석 939석, 좌석 선택부터 결제 화면까지).
- **하지 않은 것**: 결제. 잡은 좌석은 해제했고, 만든 주문은 결제 시간이 지나 만료된 것을 확인했다. 약관 동의는 사용자가 직접 했다.

## 요약

1. 안 바뀌는 정보(좌석 위치)와 바뀌는 정보(좌석 상태)를 나눈다. 좌석 위치는 큰 응답을 gzip으로 한 번, 좌석 상태는 **좌석당 1비트 hex 문자열**을 약 4초마다 폴링한다.
2. 실시간 연결(WebSocket·SSE)은 웹 어디에도 없다. 대기열 순번도, 좌석 상태도 폴링이다.
3. 대기열이 입장 인원을 조절하고, 화면은 요청을 무작위 간격으로 흩고 실패하면 간격을 늘린다.
4. 앞단(Cloudflare·CloudFront)은 지나가기만 하고 API 응답을 캐시하지 않는다(전부 `no-store`).
5. 쓰기는 단계마다 서버가 다시 확인한다(좌석 선택 완료, 가격 조합, 세션).

## 단계별 관찰

### 1. 조회: 홈, 상품 페이지

- `nol.yanolja.com`이 Next.js로 서버에서 HTML을 그린다. 문서 응답은 `cache-control: private, no-cache, no-store`, `cf-cache-status: DYNAMIC`, brotli 압축이다.
- 상품 페이지가 따로 부르는 API(전부 `no-store`, gzip):

| 경로 | 내용 | 크기(원본 → 전송) |
| --- | --- | --- |
| `/ticket/products/api/schedules` | 날짜별 회차 | 16.8KB → 1.6KB |
| `/ticket/products/api/remaining-seats?goodsCode&playSeq` | 회차의 등급별 남은 좌석 수 | 0.7~1KB → 0.3KB |
| `/ticket/products/api/auth` | 로그인·본인인증 여부, 회원 등급 | 135B |

- 결제 대기 중인 주문이 좌석 1개를 잡고 있는 동안에도 `remaining-seats`의 해당 등급 수는 그대로였다. 잡힌 좌석을 빼지 않거나 캐시된 값으로 보인다(어느 쪽인지는 모름).
- 페이지에 Cloudflare 봇 검사 스크립트(`cdn-cgi/challenge-platform`)가 함께 로드된다.

### 2. 예매 진입: 인증, 대기열

1. 로그인·본인인증이 안 돼 있으면 `accounts.yanolja.com`의 인증 창으로 보낸다.
2. 대기열 `tickets.interpark.com/waiting`을 거친다. 관찰 시점에는 대기자가 없어 바로 통과했고, 아래는 화면 코드에서 읽었다.
   - `POST /waiting/api/line-up`: 줄 서기. 6초 안에 응답이 없으면 끊는다.
   - `GET /waiting/api/rank?waitingId`: `myRank`, `totalRank`, `bookingRate`, 입장 주소 `oneStopUrl`을 받는다.
   - 순번 확인 간격은 `1000 × (2 + 무작위)` = 2~3초다. 실패하면 간격을 두 배씩 늘려 최대 10초까지 벌리고, 요청 하나는 5초에 끊는다.
   - 남은 대기 인원이 50명을 넘을 때만 대기 화면을 보여 준다.
3. 차례가 오면 `oneStopUrl`로 이동하고, 예매 세션 ID(`공연코드_회원번호시각`)가 생긴다.

### 3. 좌석 화면: 조회

처음 한 번 받는 것:

| 경로 | 내용 | 크기(원본 → 전송) |
| --- | --- | --- |
| `/onestop/api/seats/grades` | 등급·가격 | 0.7KB, 압축 없음 |
| `/onestop/api/seats/block-data` | 구역 위치 | 1~2KB → 0.3KB |
| `/onestop/api/seatMeta?blockKeys…`(구역 묶음마다 4~7번) | 좌석 ID, 층·열·번호, 화면 좌표 | 17~165KB → 1~8KB(gzip 약 20배) |
| GraphQL `InitSeat` | 최대 매수, 연동 여부 | 작음 |

계속 반복하는 것, `/onestop/api/seatStatus?goodsCode&placeCode&playSeq&blockKeys…`:

- **형식**: 구역마다 hex 문자열 하나. 글자 하나가 좌석 4개, 즉 좌석당 1비트이고 1이 "고를 수 있음"이다. 화면 코드의 이름도 `statusHex`, `seatStatusBinary`다.
  - 근거: 〈오페라의 유령〉에서 구역 좌석 수와 문자열 길이가 모두 "좌석 수 ÷ 4"였다(354석 → 89글자, 307석 → 77글자). 남은 좌석이 "A석 1"일 때 문자열 전체에 `8`(이진수 1000)이 한 번만 있었다.
  - 1,674석 공연 전체가 약 450바이트라 압축하지 않는다.
- **좌석 순서**: 상태 문자열의 n번째 비트는 같은 구역 `seatMeta`의 n번째 좌석이다. 구역(`blockKey`) 단위로 맞춘다.
- **주기**: 24번 관찰에서 간격 3.7~5.0초.
- **묶음 요청**: 화면이 구역별 요청을 10ms 동안 모아 최대 16개 구역을 한 요청으로 보낸다(관찰 요청은 `blockKeys` 8~14개).
- **버전**: 응답 헤더 `last-seat-modified`(밀리초 시각). 요청에 조건 헤더는 없어 서버는 매번 전체를 보낸다. 자동 배정 기능은 좌석을 3번 연속 못 잡으면 상태를 다시 받고, 이 시각이 그대로면 후보를 다시 거르지 않는다.
- **캐시**: `no-store`, CloudFront `Miss`.
- 구장 시스템과 연동하는 상품은 `externalSeatMeta`·`externalSeatStatus`로 같은 형식을 받는다.

### 4. 좌석 고르기: 쓰기

| 동작 | 요청 | 걸린 시간 |
| --- | --- | --- |
| 좌석 클릭 | GraphQL `PreselectSeat` → `true` | 342ms(첫 번째), 73ms(두 번째) |
| 선택 취소 | `DeselectSeat` | 60ms |
| 팔린 좌석 클릭 | 요청 없음(화면이 막음) | - |
| 선택 완료 | `ExpiredSession` → `POST /onestop/api/seats/select` → `{"unselectableSeatInfoIds":[]}` | 143ms |
| 이전 단계로 돌아가 좌석 다시 고르기 | `BulkDeselectSeats` | 34ms |

- 클릭할 때마다 서버에 좌석을 잡고, 선택 완료 때 고른 좌석을 다시 확인해 그새 못 쓰게 된 좌석 목록을 돌려준다.
- 좌석 선택은 10분 제한이다. 이전 단계로 돌아가도 타이머는 처음으로 돌아가지 않고, 10분이 지나면 "처음부터 다시 예매" 안내가 뜬다.
- 화면 코드에 보안문자(`IssueCaptcha`, `VerifyCaptcha`, `CaptchaAudio`), 자동 배정(`AutoAssignSeats`), 예매대기가 있다. 관찰한 공연에서는 보안문자가 뜨지 않았다.

### 5. 가격 선택

- `GET /onestop/api/prices/{…}`: 등급별 가격·할인(2.9KB).
- 매수 선택은 화면 안에서만 바뀌고, "예매하기" 때 `ValidatePricingSelection`으로 서버가 조합을 검증한다(88ms).

### 6. 예매 정보 입력

- `GET /onestop/api/paymentInitEssential`: 수령 방법, 포장 옵션(2KB). 예매자 정보는 계정에서 채워진다.
- 이전으로 가면 "작성 중인 정보가 사라집니다", 좌석 단계로 가면 "선택했던 좌석은 해제됩니다" 확인 창이 뜬다.

### 7. 주문 생성, 결제 화면, 만료

- 약관 동의 뒤 "예매 진행하기"가 GraphQL `PreOrder`로 주문을 만들고 `tickets.interpark.com/payment/order/{주문 ID}`로 이동한다. 요청 본문과 응답은 페이지 이동으로 기록이 지워져 보지 못했다.
- 주문 ID는 UUIDv7(셋째 묶음이 `7`로 시작), 화면의 주문 번호는 `날짜 + 영문 7자`다.
- 결제 화면은 서버가 그린다(Next.js, `no-store`, 화면이 따로 부르는 API 없음). 결제 가능 시간 10분이 새로 시작한다.
- "나가기"는 주문을 취소하지 않았다. 같은 주소를 다시 열면 남은 시간과 함께 그대로였고, 10분 뒤에는 "결제 마감 시간이 만료되었습니다"로 바뀌었다.

### 단계마다 붙는 요청

- `POST /onestop/api/session-check/{세션 ID}`: 세션 확인·연장(`"status":"refresh"`).
- GraphQL `ExpiredSession`, `POST /onestop/log/access`.

## 실시간 연결을 쓰는가

웹에서는 쓰지 않는다. 각 화면이 받는 JavaScript 전체(NOL 상품 26개, 대기열 8개, 좌석·가격·예매 정보 55개와 지연 로딩 묶음 `1910`·`6157`, 결제 19개)에서 `WebSocket`, `wss:`, SockJS, STOMP, socket.io, `EventSource`, SignalR을 찾았다. 나온 것은 Next.js 개발용 자동 새로고침(HMR)과 주소 처리 코드의 기본 포트 목록뿐이다.

- 2025년 4월 개편 전 예매창(`poticket.interpark.com/Book/BookSession.asp`)은 직접 열면 멈춰 코드를 보지 못했다. 공개된 매크로 코드로 보면 iframe(`ifrmSeat` → `ifrmSeatDetail`)에 서버가 그린 좌석 이미지 태그를 넣고, 새로고침으로 갱신하는 구조였다.
- 개편 소개글은 "수동 새로고침 대신 자동 갱신"이라고 설명한다. 좌석이 실시간으로 사라지는 것처럼 보이는 것은 약 4초 폴링으로 추정한다.
- 앱은 브라우저로 볼 수 없어 확인하지 못했다.

## 보지 못한 것

- `PreOrder` 요청 본문과 응답.
- 보안문자가 뜨는 흐름, 실제로 줄이 쌓인 대기열.
- 비지정석(`isNonReservedSeat`, 〈캣츠〉 젤리클석), 스포츠 경기(비시즌), 자동 배정·예매대기 실행.
- 서버 내부(캐시, 저장소, 처리 방식).

## Core 설계에 반영한 것

- 점유 좌석 조회는 좌석당 1비트 상태를 hex로 돌려주는 새 API로 바꾸고 측정 시나리오가 쓴다(사용자 결정 2026-11-09). 좌석 순서는 좌석 배치도와 맞추고, 마지막 변경 시각을 함께 준다. 기존 점유 좌석 API와 WebSocket 알림은 FE 호환을 위해 그대로 둔다([프론트엔드 호환](../architecture.md#프론트엔드-호환)).
