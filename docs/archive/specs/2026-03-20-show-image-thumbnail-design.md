> ⚠️ **완료·폐기된 기록이다. 현행 기준이 아니다.**
> 마지막 갱신 2026-03-20. 이후 코드와 규칙이 바뀌었을 수 있다.
> 현재 설계는 `docs/architecture.md`, 현재 규칙은 `AGENTS.md`를 본다.

# Show Image Thumbnail Design

## 목표
- 공연 목록 계열 응답의 이미지 로딩 시간을 줄이기 위해 목록용 썸네일 이미지를 별도로 제공한다.

## 범위
- `show` 정적 이미지에 목록용 썸네일 파일을 추가한다.
- 메인 목록, 최신 공연, 검색, 오픈예정 응답의 `image` 값을 썸네일 경로로 바꾼다.
- 상세 응답은 기존 원본 이미지를 유지한다.

## 설계
- 원본 이미지 위치는 `static/api/images/shows/{id}.{ext}`를 유지한다.
- 목록용 썸네일은 `static/api/images/shows/card/{id}.jpg`로 생성한다.
- 백엔드 응답은 `show` 목록 계열에서만 원본 경로를 썸네일 경로로 변환한다.
- 변환 규칙은 `/api/images/shows/{name}.{ext}` -> `/api/images/shows/card/{name}.jpg` 이다.

## 비범위
- 요청 시점 동적 리사이징
- 상세 이미지 최적화
- 프론트 컴포넌트 수정
