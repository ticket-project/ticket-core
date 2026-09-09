# favorite module을 like로 개명하고 찜 대상을 LikeType으로 일반화한다

## 상태(2026-09-09): 채택·구현됨. ADR 0006 §1·§2를 module 이름 범위에서만 supersede

**2026-09-09 갱신**: 아래 §4("show 쪽 배치는 바꾸지 않는다")는
[ADR 0009](0009-like-owns-write-and-status-usecases.md)가 supersede했다 — 찜하기·찜
해제·찜 상태 조회는 like로 옮겼고, "내 찜 목록"만 show에 남는다. 이 문서의 나머지 결정
(module 개명, 대상 일반화, 스키마)은 그대로 유효하다.

## 배경

ADR 0006 §1·§2가 찜(당시 `ShowLike`) 데이터를 `favorite` module로 분리했다. 그 결정 자체(찜
데이터는 별도 module이 소유하고, HTTP endpoint·use case는 show가 조합한다)는 여전히 유효하다 —
이 ADR이 바꾸는 것은 두 가지뿐이다: module 이름과, 찜 대상이 공연 하나로 고정돼 있던 것.

module 안의 실제 어휘(엔티티 `ShowLike`, URL `/api/v1/likes/shows/{showId}`,
`/api/v1/members/me/likes`, 오류 메시지 "이미 찜한 공연")는 이미 전부 "like"였다. module
이름만 `favorite`였다.

또한 찜 대상은 지금 공연(Show) 하나뿐이지만, 공연장이나 출연자를 찜하는 기능이 미래에 추가될
가능성이 있다. 지금 구조로는 새 대상마다 별도 module과 별도 테이블(`VenueLike`, `PerformerLike`
같은)이 필요해진다.

## 결정

1. **module 개명**: `com.ticket.favorite` → `com.ticket.like`. `FavoriteErrorCode` →
   `LikeErrorCode`, `FavoriteException` → `LikeException`, `FavoriteAuditedEntity` →
   `LikeAuditedEntity`, `ShowLikeAlreadyExistsException` → `LikeAlreadyExistsException`,
   `FavoriteExceptionHandler` → `LikeExceptionHandler` 등. 한국어 도메인 용어 "찜"과 그
   `_Avoid_: 좋아요`는 그대로 유지한다 — 개명은 영어 식별자 어휘일 뿐, 도메인 용어 결정을
   뒤집는 게 아니다.
2. **대상 일반화**: `ShowLike(showId)` → `Like(targetId, likeType)`. `LikeType` enum을
   module 공개 계약에 신설한다 — 지금은 `SHOW` 하나만 있다. 공개 계약도 함께 바뀐다:
   `ShowLikeQuery.countByShowId(showId)` → `LikeQuery.countByTarget(LikeType, targetId)`,
   `getShowLike` → `get(LikeType, targetId, memberId)`, `findLikedShows` →
   `findLiked(LikeType, memberId, cursor, size)`, `ShowLikeCommand.like/unlike(memberId,
   showId)` → `LikeCommand.like/unlike(memberId, LikeType, targetId)`.
3. **스키마**: `SHOW_LIKES` → `LIKES`, `show_id` → `target_id`, `like_type` 컬럼 신설(기존 행은
   `'SHOW'`로 backfill), 유니크 제약을 `(member_id, show_id)`에서 `(member_id, like_type,
   target_id)`로 넓힌다.
4. **show 쪽 배치는 바꾸지 않는다.** 찜의 HTTP endpoint·use case(`AddShowLikeUseCase`,
   `MyShowLikesController` 등)는 여전히 `show` module에 있다. 사실의 소유자를 기준으로
   나눈 배치이기 때문이다 — 공연이 존재하는가는 show가 아는 사실이고, 찜이 중복인가는
   like가 아는 사실이다. `GetShowDetailUseCase`가 공연 상세의 찜 개수를 위해
   `LikeQuery.countByTarget`을 부르는 한 `show -> like` 의존은 지울 수 없고, 전부 like
   module로 옮기면 존재·회원 확인 때문에 `like -> show`, `like -> member`가 새로 생겨
   ADR 0006 §2가 없앤 순환이 복원된다.

## 결정하지 않는 것 — 일반화가 줄여주지 않는 것

**대상별 표시값 조립과 대상 존재 확인은 여전히 그 대상을 아는 module에 남는다.** 예를 들어
공연장 찜이 추가되면:

- 찜 생성 전 "그 공연장이 존재하는가" 확인은 `venue` module의 책임이고, `like`는 여전히 이를
  확인하지 않는다(`allowedDependencies = {}` leaf를 유지한다).
- "내 찜 목록"에 공연장 이름·이미지 같은 표시값을 채우는 조립은 `venue`(또는 그걸 아는 module)
  가 `like`의 `LikeEntry.targetId()`로 자기 데이터를 다시 조회해서 한다 — `show`의
  `GetMyShowLikesUseCase`가 지금 하는 것과 같은 패턴이다.

즉 `LikeType` 도입이 줄여주는 것은 **테이블·리포지토리·중복 방지 불변식**뿐이고, 대상별 업무
로직(존재 확인, 표시값 조립, HTTP endpoint)은 새 대상이 생길 때마다 여전히 그 대상을 아는
module에 새로 작성해야 한다. 이 사실을 미리 적어 두는 이유는, 나중에 "LikeType을 도입했으니
새 대상 추가가 공짜"라고 오해하지 않게 하기 위해서다.

## 근거

- `docs/architecture.md`의 Favorite/Like 조합 규칙: "공연이 존재하는가는 show가 아는 사실이고,
  찜이 중복인가는 like가 아는 사실이다."
- ADR 0005 §2가 이미 같은 판단을 내린 선례다 — "Show 전체 회차에 적용할 좌석 템플릿이 실제로
  필요해지면 그때 `ShowSeatTemplate` 같은 별도 개념을 추가한다 — 지금은 요구가 없으므로 기존
  개념을 이름만 바꿔 남기지 않는다." 이번에도 필요 이상으로 앞서 추상화하지 않기 위해, 대상별
  업무 로직까지 미리 일반화하지 않았다.

## 영향

- HTTP 계약(`/api/v1/likes/shows/{showId}`, `/api/v1/members/me/likes`)과 오류 코드(`E7001`)는
  바뀌지 않는다.
- Flyway 이력 테이블 이름이 module 이름에서 나온다(`flyway_schema_history_{module}`). 개명 후
  `..._favorite`는 남고 `..._like`가 새로 생겨 V1·V2를 재적용한다 — 두 migration 모두 존재
  확인 가드가 있어 재적용이 안전하다. 옛 이력 테이블 정리는 이 ADR의 범위 밖이다.
