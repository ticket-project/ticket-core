// KOPIS 신규 공연 수집 → kopis-curated.sql 누적 병합
// 설계: docs/superpowers/specs/2026-06-06-kopis-data-refresh-design.md
// 실행: KOPIS_SERVICE_KEY=xxxx node tools/seed-kopis/fetch-kopis.mjs --target 100 --from 20260606 --to 20260906 [--dry-run]

import { readFileSync, writeFileSync, copyFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import {
  mapGenre,
  mapRegion,
  genreLabel,
  parseRuntime,
  parseKopisDate,
  deterministicViewCount,
  sqlStr,
  normalizeKey,
  cleanPerformerName,
} from './genre-map.mjs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const SQL_PATH = resolve(__dirname, '../../core/core-api/src/main/resources/seed/kopis-curated.sql');
const BASE = 'http://www.kopis.or.kr/openApi/restful';
const SPLICE_MARKER = 'INSERT INTO SHOW_GRADES';

const KEY = process.env.KOPIS_SERVICE_KEY;

// ---------- args ----------
function parseArgs() {
  const a = process.argv.slice(2);
  const get = (name, def) => {
    const i = a.indexOf(`--${name}`);
    return i >= 0 && a[i + 1] ? a[i + 1] : def;
  };
  return {
    target: parseInt(get('target', '100'), 10),
    from: get('from', '20260606'),
    to: get('to', '20260906'),
    rows: parseInt(get('rows', '100'), 10),
    maxPages: parseInt(get('max-pages', '50'), 10),
    dryRun: a.includes('--dry-run'),
  };
}

// ---------- http ----------
function decodeEntities(s) {
  return (s || '')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#0?39;/g, "'")
    .replace(/&apos;/g, "'");
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function fetchText(url, attempt = 1) {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), 20000);
  try {
    const res = await fetch(url, { signal: ctrl.signal });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return await res.text();
  } catch (err) {
    if (attempt >= 3) throw err;
    await sleep(500 * 2 ** (attempt - 1));
    return fetchText(url, attempt + 1);
  } finally {
    clearTimeout(timer);
  }
}

function dbBlocks(xml) {
  return [...xml.matchAll(/<db>([\s\S]*?)<\/db>/g)].map((m) => m[1]);
}
function tag(xml, name) {
  const m = xml.match(new RegExp(`<${name}>([\\s\\S]*?)</${name}>`));
  return m ? decodeEntities(m[1]).trim() : '';
}

// ---------- date utils ----------
const pad = (n) => String(n).padStart(2, '0');

function dateRange(startStr, endStr) {
  const out = [];
  const [ys, ms, ds] = startStr.split('-').map(Number);
  const [ye, me, de] = endStr.split('-').map(Number);
  let t = Date.UTC(ys, ms - 1, ds);
  const end = Date.UTC(ye, me - 1, de);
  while (t <= end) {
    const d = new Date(t);
    out.push(`${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}`);
    t += 86400000;
    if (out.length > 1000) break; // 안전장치
  }
  return out;
}

function sampleDates(startStr, endStr, n) {
  const all = dateRange(startStr, endStr);
  if (all.length <= n) return all;
  const picked = [];
  for (let i = 0; i < n; i++) {
    picked.push(all[Math.round((i * (all.length - 1)) / (n - 1))]);
  }
  return [...new Set(picked)];
}

function isWeekend(dateStr) {
  const [y, m, d] = dateStr.split('-').map(Number);
  const day = new Date(Date.UTC(y, m - 1, d)).getUTCDay();
  return day === 0 || day === 6;
}

function shiftDateTime(dateStr, hh, mm, deltaMin) {
  const [y, mo, d] = dateStr.split('-').map(Number);
  const dt = new Date(Date.UTC(y, mo - 1, d, hh, mm) + deltaMin * 60000);
  return `${dt.getUTCFullYear()}-${pad(dt.getUTCMonth() + 1)}-${pad(dt.getUTCDate())} ${pad(dt.getUTCHours())}:${pad(dt.getUTCMinutes())}:00`;
}

function minusOneMonth(dateStr) {
  const [y, mo, d] = dateStr.split('-').map(Number);
  const t = new Date(Date.UTC(y, mo - 1, d));
  t.setUTCMonth(t.getUTCMonth() - 1);
  return `${t.getUTCFullYear()}-${pad(t.getUTCMonth() + 1)}-${pad(t.getUTCDate())}`;
}

function parseShowHour(dtguidance) {
  const m = (dtguidance || '').match(/(\d{1,2})\s*[:시]\s*(\d{2})?/);
  if (m) {
    const h = parseInt(m[1], 10);
    const mn = m[2] ? parseInt(m[2], 10) : 0;
    if (h >= 0 && h <= 23 && mn >= 0 && mn < 60) return [h, mn];
  }
  return null;
}

// ---------- 기존 SQL 파싱 ----------
function maxId(sql, table) {
  const re = new RegExp(`INSERT INTO ${table} \\([^)]*\\) VALUES \\((\\d+)`, 'g');
  let max = 0;
  let m;
  while ((m = re.exec(sql))) max = Math.max(max, parseInt(m[1], 10));
  return max;
}

function existingShowKeys(sql) {
  const keys = new Set();
  const re = /INSERT INTO SHOWS \([^)]*\) VALUES \(\d+,\s*'((?:[^']|'')*)',\s*'((?:[^']|'')*)',/g;
  let m;
  while ((m = re.exec(sql))) {
    keys.add(normalizeKey(m[1].replace(/''/g, "'"), m[2].replace(/''/g, "'")));
  }
  return keys;
}

function existingPfIds(sql) {
  return new Set([...sql.matchAll(/PF\d{4,}/g)].map((m) => m[0]));
}

// ---------- 회차 생성 ----------
function buildPerformances(show, startPerfId) {
  const days = dateRange(show.startDate, show.endDate);
  const dates = days.length <= 3 ? days : sampleDates(show.startDate, show.endDate, Math.min(6, days.length));
  const showHour = parseShowHour(show.dtguidance);
  const slots = [];
  for (const date of dates) {
    const [hh, mm] = showHour ? showHour : [isWeekend(date) ? 14 : 19, 0];
    slots.push({ date, hh, mm });
    if (slots.length >= 6) break;
  }
  if (slots.length === 1) {
    const p = slots[0];
    slots.push({ date: p.date, hh: p.hh < 17 ? 19 : 14, mm: 0 });
  }

  return slots.map((slot, idx) => {
    const start = shiftDateTime(slot.date, slot.hh, slot.mm, 0);
    const end = shiftDateTime(slot.date, slot.hh, slot.mm, show.runningMinutes);
    const close = shiftDateTime(slot.date, slot.hh, slot.mm, -60);
    return {
      id: startPerfId + idx,
      showId: show.id,
      no: idx + 1,
      start,
      end,
      open: `${show.saleStart} 10:00:00`,
      close,
    };
  });
}

// ---------- info 문구 ----------
function buildInfo(venueName, genrenm, minutes, prfage, cast) {
  const label = genreLabel(genrenm);
  let s = `${venueName}에서 진행되는 ${label} 공연입니다. 러닝타임 ${minutes}분`;
  if (prfage) s += `, 관람연령 ${prfage}`;
  s += '.';
  if (cast) s += ` 출연: ${cast}.`;
  s += ' 회차별 세부 안내는 KOPIS 공연정보를 참고하세요.';
  return s;
}

function shortCast(prfcast) {
  const c = (prfcast || '').trim();
  if (!c) return '';
  const names = c.split(/[,，]/).map((x) => x.trim()).filter(Boolean).slice(0, 5);
  return names.join(', ') + (c.split(/[,，]/).length > 5 ? ' 등' : '');
}

// ---------- main ----------
async function main() {
  if (!KEY) {
    console.error('환경변수 KOPIS_SERVICE_KEY 가 필요합니다.');
    process.exit(1);
  }
  const opts = parseArgs();
  console.log(`[설정] target=${opts.target}, 기간=${opts.from}~${opts.to}, rows=${opts.rows}, dryRun=${opts.dryRun}`);

  const sql = readFileSync(SQL_PATH, 'utf8');
  const next = {
    show: maxId(sql, 'SHOWS') + 1,
    performer: maxId(sql, 'PERFORMERS') + 1,
    venue: maxId(sql, 'VENUES') + 1,
    showGenre: maxId(sql, 'SHOW_GENRES') + 1,
    performance: maxId(sql, 'PERFORMANCES') + 1,
  };
  console.log('[기존 max id]', {
    SHOWS: next.show - 1,
    PERFORMERS: next.performer - 1,
    VENUES: next.venue - 1,
    SHOW_GENRES: next.showGenre - 1,
    PERFORMANCES: next.performance - 1,
  });

  const seenKeys = existingShowKeys(sql);
  const seenPf = existingPfIds(sql);

  // 1) 목록 수집 (필터 + dedupe)
  const candidates = [];
  const want = opts.target + 20; // 상세 실패 대비 버퍼
  for (let page = 1; page <= opts.maxPages && candidates.length < want; page++) {
    const url = `${BASE}/pblprfr?service=${KEY}&stdate=${opts.from}&eddate=${opts.to}&cpage=${page}&rows=${opts.rows}`;
    const xml = await fetchText(url);
    const blocks = dbBlocks(xml);
    if (blocks.length === 0) break;
    for (const b of blocks) {
      const mt20id = tag(b, 'mt20id');
      const prfnm = tag(b, 'prfnm');
      const genrenm = tag(b, 'genrenm');
      const fcltynm = tag(b, 'fcltynm');
      const poster = tag(b, 'poster');
      const prfstate = tag(b, 'prfstate');
      if (!mt20id || !prfnm || !poster) continue;
      if (!['공연예정', '공연중'].includes(prfstate)) continue;
      if (mapGenre(genrenm, prfnm) === null) continue;
      if (seenPf.has(mt20id)) continue;
      const key = normalizeKey(prfnm, fcltynm);
      if (seenKeys.has(key)) continue;
      seenKeys.add(key);
      seenPf.add(mt20id);
      candidates.push({ mt20id, prfnm, genrenm, fcltynm, poster, from: tag(b, 'prfpdfrom'), to: tag(b, 'prfpdto') });
    }
    process.stdout.write(`\r[목록] page ${page}, 후보 ${candidates.length}개`);
  }
  console.log(`\n[목록] 최종 후보 ${candidates.length}개`);

  // 2) 상세 + 시설 수집, row 생성
  const performerMap = new Map(); // name -> id
  const venueMap = new Map(); // name -> {id, ...}
  const shows = [];
  const performances = [];
  const showGenres = [];

  for (const c of candidates) {
    if (shows.length >= opts.target) break;
    let detail;
    try {
      detail = await fetchText(`${BASE}/pblprfr/${c.mt20id}?service=${KEY}`);
    } catch {
      continue;
    }
    await sleep(150);

    const mt10id = tag(detail, 'mt10id');
    const prfruntime = tag(detail, 'prfruntime');
    const prfage = tag(detail, 'prfage');
    const prfcast = tag(detail, 'prfcast');
    const entrpsnm = tag(detail, 'entrpsnm');
    const dtguidance = tag(detail, 'dtguidance');
    const poster = tag(detail, 'poster') || c.poster;
    const genrenm = tag(detail, 'genrenm') || c.genrenm;
    const fcltynm = tag(detail, 'fcltynm') || c.fcltynm;

    const startDate = parseKopisDate(c.from);
    const endDate = parseKopisDate(c.to);
    if (!/^\d{4}-\d{2}-\d{2}$/.test(startDate) || !/^\d{4}-\d{2}-\d{2}$/.test(endDate)) continue;
    const minutes = parseRuntime(prfruntime);

    // 공연자(기획사) dedupe
    const performerName = cleanPerformerName(entrpsnm) || shortCast(prfcast).split(',')[0]?.trim() || 'KOPIS';
    let performerId = performerMap.get(performerName);
    if (!performerId) {
      performerId = next.performer++;
      performerMap.set(performerName, performerId);
    }

    // 공연장 dedupe (+시설 상세)
    const venueKey = fcltynm.replace(/\s+/g, '');
    let venue = venueMap.get(venueKey);
    if (!venue) {
      let adres = '';
      let la = '';
      let lo = '';
      let chartr = '';
      let telno = '';
      if (mt10id) {
        try {
          const v = await fetchText(`${BASE}/prfplc/${mt10id}?service=${KEY}`);
          await sleep(150);
          adres = tag(v, 'adres');
          la = tag(v, 'la');
          lo = tag(v, 'lo');
          chartr = tag(v, 'fcltychartr');
          telno = tag(v, 'telno');
        } catch {
          /* 시설 조회 실패 시 기본값 */
        }
      }
      venue = {
        id: next.venue++,
        name: fcltynm,
        address: adres,
        region: mapRegion(adres),
        addressDetail: chartr,
        lat: la && !Number.isNaN(Number(la)) ? Number(la).toFixed(8) : 'NULL',
        lon: lo && !Number.isNaN(Number(lo)) ? Number(lo).toFixed(8) : 'NULL',
        phone: telno || '정보없음',
      };
      venueMap.set(venueKey, venue);
    }

    const showId = next.show++;
    const saleStart = minusOneMonth(startDate);
    const cast = shortCast(prfcast);
    shows.push({
      id: showId,
      title: c.prfnm,
      subTitle: fcltynm,
      info: buildInfo(fcltynm, genrenm, minutes, prfage, cast),
      startDate,
      endDate,
      viewCount: deterministicViewCount(c.mt20id),
      saleStart,
      saleEnd: `${endDate} 23:59:00`,
      image: poster,
      venueId: venue.id,
      runningMinutes: minutes,
      performerId,
    });
    showGenres.push({ id: next.showGenre++, showId, genreId: mapGenre(genrenm, c.prfnm) });

    const perfs = buildPerformances({ id: showId, startDate, endDate, runningMinutes: minutes, saleStart, dtguidance }, next.performance);
    next.performance += perfs.length;
    performances.push(...perfs);

    process.stdout.write(`\r[상세] 공연 ${shows.length}/${opts.target}`);
  }
  console.log(`\n[수집 완료] SHOWS=${shows.length}, VENUES=${venueMap.size}, PERFORMERS=${performerMap.size}, SHOW_GENRES=${showGenres.length}, PERFORMANCES=${performances.length}`);

  if (shows.length === 0) {
    console.error('수집된 신규 공연이 없습니다. 종료합니다.');
    process.exit(1);
  }

  // 3) SQL 조각 생성
  const lines = [];
  lines.push('');
  lines.push('-- ============================================================');
  lines.push(`-- 신규 KOPIS 시드 추가분 (생성: tools/seed-kopis/fetch-kopis.mjs, 기간 ${opts.from}~${opts.to})`);
  lines.push('-- ============================================================');

  lines.push('-- ===== 추가 공연자 =====');
  for (const [name, id] of performerMap) {
    lines.push(
      `INSERT INTO PERFORMERS (id, name, profile_image_url, created_at, created_by) VALUES (${id}, ${sqlStr(name)}, '', '2026-01-01 10:00:00', 'KOPIS_SEED');`,
    );
  }

  lines.push('-- ===== 추가 공연장 =====');
  for (const v of venueMap.values()) {
    lines.push(
      `INSERT INTO VENUES (id, name, address, region, address_detail, zip_code, latitude, longitude, phone, image_url, view_box_width, view_box_height, seat_diameter, gap_x, gap_y, created_at, created_by) VALUES (${v.id}, ${sqlStr(v.name)}, ${sqlStr(v.address)}, '${v.region}', ${sqlStr(v.addressDetail)}, '', ${v.lat}, ${v.lon}, ${sqlStr(v.phone)}, '', 500, 356, 4.8, 2.5, 2.5, '2026-01-01 10:00:00', 'KOPIS_SEED');`,
    );
  }

  lines.push('-- ===== 추가 공연 =====');
  for (const s of shows) {
    lines.push(
      `INSERT INTO SHOWS (id, title, sub_title, info, start_date, end_date, view_count, sale_type, sale_start_date, sale_end_date, image, venue_id, running_minutes, performer_id, created_at, created_by) VALUES (${s.id}, ${sqlStr(s.title)}, ${sqlStr(s.subTitle)}, ${sqlStr(s.info)}, '${s.startDate}', '${s.endDate}', ${s.viewCount}, 'GENERAL', '${s.saleStart} 10:00:00', '${s.saleEnd}', ${sqlStr(s.image)}, ${s.venueId}, ${s.runningMinutes}, ${s.performerId}, '2026-01-01 10:00:00', 'KOPIS_SEED');`,
    );
  }

  lines.push('-- ===== 추가 공연-장르 매핑 =====');
  for (const sg of showGenres) {
    lines.push(
      `INSERT INTO SHOW_GENRES (id, show_id, genre_id, created_at, created_by) VALUES (${sg.id}, ${sg.showId}, ${sg.genreId}, '2026-01-01 10:00:00', 'KOPIS_SEED');`,
    );
  }

  lines.push('-- ===== 추가 공연 회차 =====');
  for (const p of performances) {
    lines.push(
      `INSERT INTO PERFORMANCES (id, show_id, performance_no, start_time, end_time, order_open_time, order_close_time, max_can_hold_count, hold_time, created_at, created_by) VALUES (${p.id}, ${p.showId}, ${p.no}, '${p.start}', '${p.end}', '${p.open}', '${p.close}', 4, 600, '2026-01-01 10:00:00', 'KOPIS_SEED');`,
    );
  }
  lines.push('');

  const block = lines.join('\n');

  if (opts.dryRun) {
    console.log('\n===== DRY-RUN: 미리보기(처음 25줄) =====');
    console.log(block.split('\n').slice(0, 25).join('\n'));
    console.log('...');
    console.log(`\n[DRY-RUN] 파일을 수정하지 않았습니다. 생성될 INSERT 라인 수: ${lines.filter((l) => l.startsWith('INSERT')).length}`);
    return;
  }

  // 4) 병합
  const idx = sql.indexOf(SPLICE_MARKER);
  if (idx < 0) {
    console.error(`병합 지점(${SPLICE_MARKER})을 찾지 못했습니다. 중단합니다.`);
    process.exit(1);
  }
  copyFileSync(SQL_PATH, `${SQL_PATH}.bak`);
  const merged = sql.slice(0, idx) + block + '\n' + sql.slice(idx);
  writeFileSync(SQL_PATH, merged, 'utf8');
  console.log(`\n[병합 완료] 백업: ${SQL_PATH}.bak`);
  console.log(`[병합 완료] 신규 SHOWS ${shows.length}개 (id ${shows[0].id}~${shows[shows.length - 1].id}) 추가됨.`);
}

main().catch((err) => {
  console.error('실패:', err);
  process.exit(1);
});
