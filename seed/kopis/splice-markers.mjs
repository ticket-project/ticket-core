// kopis-curated.sql의 병합 지점(@seed-splice 마커) 해석기.
//
// 시드 SQL에는 "실행 시점에 존재하는 행"만 대상으로 삼는 집합 기반 INSERT ... SELECT가 둘 있고,
// 새 데이터는 각각 그 앞에 놓여야 한다.
//
//   1) 공연장·공연은 좌석 복제(CROSS JOIN VENUES) 앞    -> @seed-splice: venues
//   2) 회차는 PERFORMANCE_GRADES / PERFORMANCE_SEATS 앞 -> @seed-splice: performances
//
// 파일 상단 설명 주석은 이 마커 문구를 따옴표로 인용한다. 단순 indexOf는 그 인용문을 먼저
// 만나 설명 주석 한가운데를 병합 지점으로 골랐다(실측: 실제 마커는 1673행·3870행인데 10행·15행이
// 선택됐다). 그 상태로 병합하면 SQL 입력 순서가 통째로 깨진다.
//
// 그래서 여기서는 "줄의 시작이 곧 마커인 줄"만 실제 마커로 본다 — 인용문은 줄 중간에 있으므로
// 걸리지 않는다. 그리고 누락·중복·역순을 파일을 고치기 전에 실패로 만든다.

export const VENUE_SPLICE_MARKER = '-- @seed-splice: venues';
export const PERFORMANCE_SPLICE_MARKER = '-- @seed-splice: performances';

/**
 * 줄의 시작이 marker인 줄만 골라 그 마커가 시작하는 문자 오프셋을 돌려준다.
 * @returns {{offset: number, line: number}[]}
 */
function findMarkerPositions(sql, marker) {
  const positions = [];
  let offset = 0;
  const lines = sql.split('\n');
  for (let index = 0; index < lines.length; index++) {
    const line = lines[index];
    const leading = line.length - line.trimStart().length;
    if (line.trimStart().startsWith(marker)) {
      positions.push({ offset: offset + leading, line: index + 1 });
    }
    offset += line.length + 1; // '\n' 한 글자
  }
  return positions;
}

function describe(positions) {
  return positions.length === 0 ? '없음' : positions.map((p) => `${p.line}행`).join(', ');
}

/**
 * 병합 지점 두 곳을 해석한다. 계약을 어기면 예외를 던진다 — 호출자는 파일을 고치기 전에 실패해야 한다.
 *
 * @param {string} sql kopis-curated.sql 전체 내용
 * @returns {{venueIndex: number, performanceIndex: number, venueLine: number, performanceLine: number}}
 */
export function resolveSpliceMarkers(sql) {
  const venues = findMarkerPositions(sql, VENUE_SPLICE_MARKER);
  const performances = findMarkerPositions(sql, PERFORMANCE_SPLICE_MARKER);

  const missing = [];
  if (venues.length === 0) missing.push(VENUE_SPLICE_MARKER);
  if (performances.length === 0) missing.push(PERFORMANCE_SPLICE_MARKER);
  if (missing.length > 0) {
    throw new Error(
      `병합 지점 마커가 없습니다: ${missing.join(', ')}\n` +
        '  -> 마커는 줄 맨 앞에서 시작해야 합니다. 설명 주석 안에 인용된 문구는 마커로 보지 않습니다.',
    );
  }

  const duplicated = [];
  if (venues.length > 1) duplicated.push(`${VENUE_SPLICE_MARKER} (${describe(venues)})`);
  if (performances.length > 1) {
    duplicated.push(`${PERFORMANCE_SPLICE_MARKER} (${describe(performances)})`);
  }
  if (duplicated.length > 0) {
    throw new Error(
      `병합 지점 마커가 중복됩니다: ${duplicated.join(' / ')}\n` +
        '  -> 어느 지점에 넣어야 할지 정할 수 없습니다. 마커를 하나씩만 남기세요.',
    );
  }

  const venue = venues[0];
  const performance = performances[0];
  if (performance.offset < venue.offset) {
    throw new Error(
      `병합 지점 순서가 어긋났습니다: 회차 마커(${performance.line}행)가 ` +
        `공연장 마커(${venue.line}행)보다 앞에 있습니다.\n` +
        '  -> 공연장·공연은 좌석 복제 앞에, 회차는 회차좌석 생성 앞에 놓여야 합니다.',
    );
  }

  return {
    venueIndex: venue.offset,
    performanceIndex: performance.offset,
    venueLine: venue.line,
    performanceLine: performance.line,
  };
}
