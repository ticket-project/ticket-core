// 실행: node --test seed/kopis/
//
// 축소 SQL만으로는 이 결함을 잡지 못했다. 결함의 원인이 "실제 파일 상단 설명 주석이 마커 문구를
// 인용한다"는 점이라서, 그 설명 주석을 가진 진짜 kopis-curated.sql을 대상으로 확인한다.

import { strict as assert } from 'node:assert';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';

import {
  PERFORMANCE_SPLICE_MARKER,
  VENUE_SPLICE_MARKER,
  resolveSpliceMarkers,
} from './splice-markers.mjs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const CURATED_SQL_PATH = resolve(__dirname, '../sql/kopis-curated.sql');
const curatedSql = readFileSync(CURATED_SQL_PATH, 'utf8');

/** 오프셋이 몇 번째 줄인지. 오류 메시지와 단언을 사람이 읽는 행 번호로 맞춘다. */
function lineAt(sql, offset) {
  return sql.slice(0, offset).split('\n').length;
}

test('실제 kopis-curated.sql에서 설명 주석이 아니라 줄 맨 앞 마커를 고른다', () => {
  const { venueIndex, performanceIndex } = resolveSpliceMarkers(curatedSql);

  const venueLine = curatedSql.slice(venueIndex).split('\n')[0];
  const performanceLine = curatedSql.slice(performanceIndex).split('\n')[0];

  assert.ok(
    venueLine.startsWith(VENUE_SPLICE_MARKER),
    `공연장 병합 지점이 마커 줄이 아니다: ${venueLine}`,
  );
  assert.ok(
    performanceLine.startsWith(PERFORMANCE_SPLICE_MARKER),
    `회차 병합 지점이 마커 줄이 아니다: ${performanceLine}`,
  );

  // 예전 구현(indexOf)이 고르던 설명 주석 인용문보다 훨씬 뒤에 있어야 한다.
  assert.ok(
    lineAt(curatedSql, venueIndex) > 100,
    '공연장 병합 지점이 파일 상단 설명 주석에 걸렸다',
  );
  assert.ok(
    lineAt(curatedSql, performanceIndex) > 100,
    '회차 병합 지점이 파일 상단 설명 주석에 걸렸다',
  );
});

test('실제 kopis-curated.sql에서 회차 마커가 공연장 마커보다 뒤에 있다', () => {
  const { venueIndex, performanceIndex } = resolveSpliceMarkers(curatedSql);

  assert.ok(venueIndex < performanceIndex);
});

test('공연장 병합 지점 앞에만 좌석 복제가 없고 뒤에 있다', () => {
  const { venueIndex } = resolveSpliceMarkers(curatedSql);

  const before = curatedSql.slice(0, venueIndex);
  const after = curatedSql.slice(venueIndex);

  // 설명 주석도 'CROSS JOIN VENUES'를 언급하므로 실제 INSERT 문만 본다.
  const seatReplication = /^INSERT INTO SEATS[\s\S]*?CROSS JOIN VENUES/m;

  assert.ok(
    !seatReplication.test(before),
    '좌석 복제가 공연장 병합 지점 앞에 있다 — 새 공연장이 좌석을 받지 못한다',
  );
  assert.ok(seatReplication.test(after), '좌석 복제를 병합 지점 뒤에서 찾지 못했다');
});

test('회차 병합 지점 뒤에 회차좌석 생성이 있다', () => {
  const { performanceIndex } = resolveSpliceMarkers(curatedSql);

  const before = curatedSql.slice(0, performanceIndex);
  const after = curatedSql.slice(performanceIndex);

  assert.ok(
    !/INSERT INTO PERFORMANCE_SEATS[\s\S]*?SELECT/.test(before),
    '회차좌석 생성이 회차 병합 지점 앞에 있다 — 새 회차가 등급·가격·좌석을 받지 못한다',
  );
  assert.ok(
    /INSERT INTO PERFORMANCE_SEATS[\s\S]*?SELECT/.test(after),
    '회차좌석 생성을 병합 지점 뒤에서 찾지 못했다',
  );
});

test('마커가 없으면 실패한다', () => {
  assert.throws(() => resolveSpliceMarkers('-- 마커가 없는 SQL\nINSERT INTO VENUES ...;\n'), {
    message: /병합 지점 마커가 없습니다/,
  });
});

test('마커가 인용문으로만 있으면 없는 것으로 본다', () => {
  const quotedOnly = [
    `--     INSERT는 전부 '${VENUE_SPLICE_MARKER}' 마커 앞에 둔다.`,
    `--     회차는 '${PERFORMANCE_SPLICE_MARKER}' 마커 앞에 둔다.`,
    'INSERT INTO VENUES (id) VALUES (1);',
  ].join('\n');

  assert.throws(() => resolveSpliceMarkers(quotedOnly), {
    message: /병합 지점 마커가 없습니다/,
  });
});

test('마커가 중복되면 실패한다', () => {
  const duplicated = [
    VENUE_SPLICE_MARKER,
    'INSERT INTO SEATS (id) SELECT 1 FROM DUAL CROSS JOIN VENUES v;',
    VENUE_SPLICE_MARKER,
    PERFORMANCE_SPLICE_MARKER,
  ].join('\n');

  assert.throws(() => resolveSpliceMarkers(duplicated), {
    message: /병합 지점 마커가 중복됩니다/,
  });
});

test('회차 마커가 공연장 마커보다 앞에 있으면 실패한다', () => {
  const reversed = [PERFORMANCE_SPLICE_MARKER, 'INSERT INTO X (id) VALUES (1);', VENUE_SPLICE_MARKER].join(
    '\n',
  );

  assert.throws(() => resolveSpliceMarkers(reversed), {
    message: /병합 지점 순서가 어긋났습니다/,
  });
});

test('마커 뒤에 설명이 붙어 있어도 마커로 인정한다', () => {
  const withTrailingText = [
    `${VENUE_SPLICE_MARKER} — 새로 수집한 공연장·공연은 반드시 이 지점 앞에 넣는다.`,
    'INSERT INTO SEATS (id) SELECT 1 FROM DUAL CROSS JOIN VENUES v;',
    `${PERFORMANCE_SPLICE_MARKER} — 새로 수집한 회차는 반드시 이 지점 앞에 넣는다.`,
  ].join('\n');

  const { venueIndex, performanceIndex } = resolveSpliceMarkers(withTrailingText);

  assert.equal(venueIndex, 0);
  assert.ok(performanceIndex > venueIndex);
});

test('CRLF 줄바꿈에서도 오프셋이 마커 시작과 정확히 맞는다', () => {
  const crlf = [
    '-- 머리말',
    `${VENUE_SPLICE_MARKER} — 설명`,
    'INSERT INTO SEATS (id) SELECT 1 FROM DUAL CROSS JOIN VENUES v;',
    `${PERFORMANCE_SPLICE_MARKER} — 설명`,
  ].join('\r\n');

  const { venueIndex, performanceIndex } = resolveSpliceMarkers(crlf);

  assert.ok(crlf.slice(venueIndex).startsWith(VENUE_SPLICE_MARKER));
  assert.ok(crlf.slice(performanceIndex).startsWith(PERFORMANCE_SPLICE_MARKER));
});
