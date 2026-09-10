// 병합 결과 검증: id 연속성/중복/FK/장르 범위
// 사용: node tools/seed-kopis/verify-seed.mjs <sql파일경로>
import { readFileSync } from 'node:fs';

const sql = readFileSync(process.argv[2], 'utf8');

function ids(table) {
  const re = new RegExp(`INSERT INTO ${table} \\([^)]*\\) VALUES \\((\\d+)`, 'g');
  return [...sql.matchAll(re)].map((m) => Number(m[1]));
}

for (const t of ['SHOWS', 'PERFORMERS', 'VENUES', 'SHOW_GENRES', 'PERFORMANCES']) {
  const arr = ids(t);
  const set = new Set(arr);
  const sorted = [...set].sort((a, b) => a - b);
  const gaps = [];
  for (let i = 1; i < sorted.length; i++) {
    if (sorted[i] !== sorted[i - 1] + 1) gaps.push(`${sorted[i - 1]}->${sorted[i]}`);
  }
  console.log(
    `${t}: count=${arr.length} min=${sorted[0]} max=${sorted[sorted.length - 1]} dupes=${arr.length - set.size} gaps=${gaps.length}${gaps.length ? ' [' + gaps.slice(0, 5).join(', ') + (gaps.length > 5 ? ', ...' : '') + ']' : ''}`,
  );
}

const venueIds = new Set(ids('VENUES'));
const performerIds = new Set(ids('PERFORMERS'));
const showIds = new Set(ids('SHOWS'));

let badFk = 0;
for (const m of sql.matchAll(/INSERT INTO SHOWS \([^)]*\) VALUES \((\d+),[\s\S]*?, (\d+), (\d+), (\d+), '[^']*', '(?:KOPIS_SEED|시드)'\);/g)) {
  const venueId = Number(m[2]);
  const performerId = Number(m[4]);
  if (!venueIds.has(venueId)) badFk++;
  if (!performerIds.has(performerId)) badFk++;
}
console.log(`SHOWS FK (venue/performer 존재): mismatches=${badFk}`);

let badShowGenreFk = 0;
const badGenre = [];
for (const m of sql.matchAll(/INSERT INTO SHOW_GENRES \([^)]*\) VALUES \(\d+, (\d+), (\d+),/g)) {
  if (!showIds.has(Number(m[1]))) badShowGenreFk++;
  const g = Number(m[2]);
  if (g < 1 || g > 15) badGenre.push(g);
}
console.log(`SHOW_GENRES FK (show 존재): mismatches=${badShowGenreFk}, genre_id 범위밖(1..15): ${badGenre.length}`);

let badPerfFk = 0;
for (const m of sql.matchAll(/INSERT INTO PERFORMANCES \([^)]*\) VALUES \(\d+, (\d+),/g)) {
  if (!showIds.has(Number(m[1]))) badPerfFk++;
}
console.log(`PERFORMANCES FK (show 존재): mismatches=${badPerfFk}`);

// 홀수 따옴표(이스케이프 누락) 의심 라인 탐지
let oddQuote = 0;
for (const line of sql.split('\n')) {
  if (!line.startsWith('INSERT')) continue;
  const q = (line.match(/'/g) || []).length;
  if (q % 2 !== 0) oddQuote++;
}
console.log(`따옴표 홀수(이스케이프 누락 의심) INSERT 라인: ${oddQuote}`);
