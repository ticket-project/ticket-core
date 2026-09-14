// 실행: node --test "seed/kopis/*.test.mjs"
//
// 네트워크를 타지 않는 순수 함수만 본다 — 조회 기간 분할, 카테고리 균형, 등록일 형식.

import { strict as assert } from 'node:assert';
import { test } from 'node:test';

import {
  addMonths,
  interleaveBalanced,
  nowStamp,
  splitIntoWindows,
  toCompact,
} from './fetch-kopis.mjs';
import { featuredViewCount, normalizePosterUrl } from './genre-map.mjs';

const KOPIS_MAX_WINDOW_DAYS = 31;

/** 구간의 길이(일). 양끝을 포함하므로 +1이다. */
function windowDays(window) {
  const day = 86400000;
  const start = Date.UTC(+window.from.slice(0, 4), +window.from.slice(4, 6) - 1, +window.from.slice(6, 8));
  const end = Date.UTC(+window.to.slice(0, 4), +window.to.slice(4, 6) - 1, +window.to.slice(6, 8));
  return (end - start) / day + 1;
}

test('조회 기간을 31일 이하 구간으로 나눈다', () => {
  const windows = splitIntoWindows('20260914', '20261214', KOPIS_MAX_WINDOW_DAYS);

  assert.ok(windows.length > 1, '3개월은 한 구간에 담기지 않는다');
  for (const window of windows) {
    assert.ok(
      windowDays(window) <= KOPIS_MAX_WINDOW_DAYS,
      `구간이 ${KOPIS_MAX_WINDOW_DAYS}일을 넘는다: ${window.from}~${window.to}`,
    );
  }
});

test('나눈 구간이 겹치지도 비지도 않고 요청 기간을 정확히 덮는다', () => {
  const windows = splitIntoWindows('20260914', '20261214', KOPIS_MAX_WINDOW_DAYS);

  assert.equal(windows[0].from, '20260914');
  assert.equal(windows[windows.length - 1].to, '20261214');

  const day = 86400000;
  for (let i = 1; i < windows.length; i++) {
    const previousEnd = Date.UTC(
      +windows[i - 1].to.slice(0, 4),
      +windows[i - 1].to.slice(4, 6) - 1,
      +windows[i - 1].to.slice(6, 8),
    );
    const currentStart = Date.UTC(
      +windows[i].from.slice(0, 4),
      +windows[i].from.slice(4, 6) - 1,
      +windows[i].from.slice(6, 8),
    );
    assert.equal(currentStart - previousEnd, day, `${windows[i - 1].to} 다음이 ${windows[i].from}이면 안 된다`);
  }
});

test('기간이 하루면 구간도 하나다', () => {
  const windows = splitIntoWindows('20260914', '20260914', KOPIS_MAX_WINDOW_DAYS);

  assert.deepEqual(windows, [{ from: '20260914', to: '20260914' }]);
});

test('기본 조회 기간은 실행일부터 3개월 뒤까지다', () => {
  const today = new Date();

  const from = toCompact(today);
  const to = toCompact(addMonths(today, 3));

  assert.match(from, /^\d{8}$/);
  assert.match(to, /^\d{8}$/);
  assert.ok(Number(to) > Number(from), '조회 종료일이 시작일보다 뒤여야 한다');
  // 고정 기본값(20260606~20260906)으로 되돌아가면 다시 실행할 때 과거만 조회한다.
  assert.notEqual(from, '20260606');
});

test('카테고리를 번갈아 꺼낸다', () => {
  const candidates = [
    { mt20id: 'a1', categoryId: 1, window: 'W1' },
    { mt20id: 'a2', categoryId: 1, window: 'W1' },
    { mt20id: 'a3', categoryId: 1, window: 'W1' },
    { mt20id: 'b1', categoryId: 2, window: 'W1' },
    { mt20id: 'b2', categoryId: 2, window: 'W1' },
    { mt20id: 'c1', categoryId: 3, window: 'W1' },
  ];

  const ordered = interleaveBalanced(candidates);

  assert.deepEqual(
    ordered.map((c) => c.mt20id),
    ['a1', 'b1', 'c1', 'a2', 'b2', 'a3'],
  );
});

test('앞에서부터 잘라도 카테고리가 한쪽으로 몰리지 않는다', () => {
  // KOPIS 목록이 콘서트만 40개 먼저 주는 상황.
  const candidates = [
    ...Array.from({ length: 40 }, (_, i) => ({ mt20id: `a${i}`, categoryId: 1, window: 'W1' })),
    ...Array.from({ length: 20 }, (_, i) => ({ mt20id: `b${i}`, categoryId: 2, window: 'W1' })),
    ...Array.from({ length: 20 }, (_, i) => ({ mt20id: `c${i}`, categoryId: 3, window: 'W1' })),
  ];

  const picked = interleaveBalanced(candidates).slice(0, 30);
  const counts = new Map();
  for (const c of picked) counts.set(c.categoryId, (counts.get(c.categoryId) ?? 0) + 1);

  assert.deepEqual([...counts.entries()].sort(), [
    [1, 10],
    [2, 10],
    [3, 10],
  ]);
});

test('앞에서부터 잘라도 조회 구간이 한쪽으로 몰리지 않는다', () => {
  // 첫 구간이 후보를 다 채워 버리던 결함. 그러면 공연 기간이 앞으로 몰려 오픈 예정이 빈다.
  const candidates = [
    ...Array.from({ length: 60 }, (_, i) => ({ mt20id: `w1-${i}`, categoryId: 1, window: 'W1' })),
    ...Array.from({ length: 30 }, (_, i) => ({ mt20id: `w2-${i}`, categoryId: 1, window: 'W2' })),
    ...Array.from({ length: 30 }, (_, i) => ({ mt20id: `w3-${i}`, categoryId: 1, window: 'W3' })),
  ];

  const picked = interleaveBalanced(candidates).slice(0, 30);
  const counts = new Map();
  for (const c of picked) counts.set(c.window, (counts.get(c.window) ?? 0) + 1);

  assert.deepEqual([...counts.entries()].sort(), [
    ['W1', 10],
    ['W2', 10],
    ['W3', 10],
  ]);
});

test('카테고리와 구간을 함께 고르게 담는다', () => {
  const candidates = [];
  for (const w of ['W1', 'W2', 'W3']) {
    for (const cat of [1, 2, 3]) {
      for (let i = 0; i < 20; i++) {
        candidates.push({ mt20id: `${w}-${cat}-${i}`, categoryId: cat, window: w });
      }
    }
  }

  const picked = interleaveBalanced(candidates).slice(0, 90);
  const byCat = new Map();
  const byWin = new Map();
  for (const c of picked) {
    byCat.set(c.categoryId, (byCat.get(c.categoryId) ?? 0) + 1);
    byWin.set(c.window, (byWin.get(c.window) ?? 0) + 1);
  }

  assert.deepEqual([...byCat.values()], [30, 30, 30]);
  assert.deepEqual([...byWin.values()], [30, 30, 30]);
});

test('한 칸만 있어도 순서를 유지한 채 전부 돌려준다', () => {
  const candidates = [
    { mt20id: 'a1', categoryId: 1, window: 'W1' },
    { mt20id: 'a2', categoryId: 1, window: 'W1' },
  ];

  assert.deepEqual(
    interleaveBalanced(candidates).map((c) => c.mt20id),
    ['a1', 'a2'],
  );
});

test('신규 공연 조회수는 기존 시드 최대치보다 높은 대역에 들어간다', () => {
  // 기존 시드 view_count는 12,000~125,000이다. 신규는 그보다 위여야 인기순 상단에 온다.
  for (const id of ['PF300611', 'PF299999', 'PF123456', 'PF000001']) {
    const v = featuredViewCount(id);
    assert.ok(v >= 130000 && v <= 220000, `${id} -> ${v}`);
  }
});

test('같은 공연은 항상 같은 조회수를 받는다', () => {
  // 재실행 시 diff가 흔들리지 않아야 한다.
  assert.equal(featuredViewCount('PF300611'), featuredViewCount('PF300611'));
  assert.notEqual(featuredViewCount('PF300611'), featuredViewCount('PF300612'));
});

test('포스터 URL을 프론트가 허용하는 https://kopis.or.kr 형태로 맞춘다', () => {
  // ticket-fe의 next/image remotePatterns는 'https://kopis.or.kr/upload/**'만 허용한다.
  // KOPIS 원본(http://www...)을 그대로 넣으면 목록 페이지 전체가 렌더 오류로 죽는다.
  assert.equal(
    normalizePosterUrl('http://www.kopis.or.kr/upload/pfmPoster/PF_1.jpg'),
    'https://kopis.or.kr/upload/pfmPoster/PF_1.jpg',
  );
  assert.equal(
    normalizePosterUrl('https://www.kopis.or.kr/upload/pfmPoster/PF_2.gif'),
    'https://kopis.or.kr/upload/pfmPoster/PF_2.gif',
  );
  assert.equal(
    normalizePosterUrl('https://kopis.or.kr/upload/pfmPoster/PF_3.png'),
    'https://kopis.or.kr/upload/pfmPoster/PF_3.png',
  );
});

test('kopis.or.kr이 아닌 포스터 URL은 건드리지 않는다', () => {
  assert.equal(normalizePosterUrl('https://example.com/a.jpg'), 'https://example.com/a.jpg');
  assert.equal(normalizePosterUrl(''), '');
  assert.equal(normalizePosterUrl(undefined), '');
});

test('등록일은 앱이 읽는 타임스탬프 형식이고 고정값이 아니다', () => {
  const stamp = nowStamp(new Date(2026, 8, 14, 7, 5, 3));

  assert.equal(stamp, '2026-09-14 07:05:03');
  assert.notEqual(nowStamp(), '2026-01-01 10:00:00');
});
