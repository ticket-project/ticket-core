// 실행: node --test "seed/kopis/*.test.mjs"
//
// 네트워크를 타지 않는 순수 함수만 본다 — 조회 기간 분할.

import { strict as assert } from 'node:assert';
import { test } from 'node:test';

import { addMonths, splitIntoWindows, toCompact } from './fetch-kopis.mjs';

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
