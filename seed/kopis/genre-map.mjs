// KOPIS 데이터 → 앱 스키마 매핑/파서 유틸

/**
 * KOPIS genrenm(+제목)을 앱 GENRES(1~15) id로 매핑한다.
 * 매핑 불가(복합/기타 등)는 null → 후보에서 스킵.
 * @returns {number|null} genre_id
 */
export function mapGenre(genrenm, title = '') {
  const g = (genrenm || '').trim();
  const t = title || '';
  const isFamily = /아동|가족|어린이|키즈/.test(t);

  switch (g) {
    case '뮤지컬':
      return isFamily ? 14 : 11; // 14=아동/가족뮤지컬, 11=창작
    case '연극':
      return isFamily ? 13 : 8; // 13=아동/가족극, 8=연극
    case '서양음악(클래식)':
      if (/오케스트라|심포니|필하모닉|관현악/.test(t)) return 2; // 오케스트라
      if (/독주|리사이틀|독창/.test(t)) return 3; // 독주/리사이틀
      if (/성악|합창|오페라|칸타타/.test(t)) return 5; // 성악/합창
      return 1; // 클래식
    case '대중음악':
      return 6; // 밴드/라이브
    case '무용':
    case '무용(서양/한국무용)':
    case '대중무용':
      return 9; // 무용/퍼포먼스
    case '서커스/마술':
      return 12; // 마술/서커스
    case '한국음악(국악)':
      return 7; // 크로스오버 (전용 국악 장르 부재 → 근사)
    default:
      return null; // 복합/기타/미매핑 → 스킵
  }
}

/**
 * GENRES id -> CATEGORIES id. 시드 SQL의 GENRES 리터럴과 같은 사실이다
 * (1~7 = 콘서트, 8/9/12/13 = 연극, 10/11/14/15 = 뮤지컬).
 */
const CATEGORY_OF_GENRE = {
  1: 1,
  2: 1,
  3: 1,
  4: 1,
  5: 1,
  6: 1,
  7: 1,
  8: 2,
  9: 2,
  12: 2,
  13: 2,
  10: 3,
  11: 3,
  14: 3,
  15: 3,
};

/** 카테고리별 표시 이름. 수집 요약 출력용. */
export const CATEGORY_LABEL = { 1: '콘서트', 2: '연극', 3: '뮤지컬' };

/**
 * 장르 id가 속한 카테고리 id. 매핑 불가는 null.
 * @returns {number|null}
 */
export function categoryOfGenre(genreId) {
  return CATEGORY_OF_GENRE[genreId] ?? null;
}

/**
 * 주소/지역명 접두에서 region enum을 결정한다.
 * enum: SEOUL, GYEONGGI, INCHEON, GANGWON, CHUNGCHEONG, JEOLLA, GYEONGSANG, JEJU
 */
export function mapRegion(sidoText = '') {
  const s = sidoText || '';
  if (/^서울/.test(s)) return 'SEOUL';
  if (/^경기/.test(s)) return 'GYEONGGI';
  if (/^인천/.test(s)) return 'INCHEON';
  if (/^강원/.test(s)) return 'GANGWON';
  if (/^(충청|대전|세종|충북|충남)/.test(s)) return 'CHUNGCHEONG';
  if (/^(전라|광주|전북|전남)/.test(s)) return 'JEOLLA';
  if (/^(경상|부산|대구|울산|경북|경남)/.test(s)) return 'GYEONGSANG';
  if (/^제주/.test(s)) return 'JEJU';
  return 'SEOUL'; // fallback
}

/** 카테고리명(한글, info 문구용) */
export function genreLabel(genrenm) {
  const g = (genrenm || '').trim();
  if (g === '뮤지컬') return '뮤지컬';
  if (g === '연극') return '연극';
  if (g.includes('클래식')) return '클래식';
  if (g === '대중음악') return '대중음악';
  if (g.includes('국악')) return '국악';
  if (g.includes('무용')) return '무용';
  if (g.includes('서커스') || g.includes('마술')) return '서커스/마술';
  return '공연';
}

/** 기획사/단체명 정리: 콤마 중복 제거, 공백 정리, 길이 제한 */
export function cleanPerformerName(raw) {
  const parts = (raw || '')
    .split(/[,，]/)
    .map((x) => x.replace(/\s+/g, ' ').trim())
    .filter(Boolean);
  const unique = [...new Set(parts)];
  const joined = unique.join(', ');
  return joined.length > 100 ? joined.slice(0, 100) : joined;
}

/**
 * KOPIS prfruntime("2시간 30분" / "90분" / "1시간") → 분(number).
 * 파싱 실패 시 90.
 */
export function parseRuntime(prfruntime) {
  const s = (prfruntime || '').trim();
  if (!s) return 90;
  let minutes = 0;
  const h = s.match(/(\d+)\s*시간/);
  const m = s.match(/(\d+)\s*분/);
  if (h) minutes += parseInt(h[1], 10) * 60;
  if (m) minutes += parseInt(m[1], 10);
  if (minutes === 0) {
    const only = s.match(/(\d+)/);
    if (only) minutes = parseInt(only[1], 10);
  }
  return minutes > 0 ? minutes : 90;
}

/** "YYYY.MM.DD" → "YYYY-MM-DD" */
export function parseKopisDate(s) {
  const d = (s || '').trim().replace(/\./g, '-').replace(/-+$/, '');
  return d;
}

/**
 * 새로 수집하는 공연의 view_count. 기존 시드(12,000~125,000)보다 확실히 높은 대역이라
 * 인기순 목록 상단을 신규 공연이 차지한다.
 *
 * view_count는 실측 지표가 아니라 시드가 만드는 합성값이다. 그래서 이 값은 "얼마나 인기 있는가"가
 * 아니라 "테스트 화면에서 어디에 노출할 것인가"를 정하는 손잡이다. 실제 조회수 집계가 들어오면
 * 이 값은 덮여야 한다.
 *
 * 대역 안에서는 mt20id 해시로 흩어 놓는다 — 재실행해도 값이 같아 diff가 안정적이고, 배너(최신순)와
 * 목록(인기순)의 상위 구성이 서로 달라져 같은 공연이 두 번 보이지 않는다.
 */
export function featuredViewCount(mt20id) {
  return 130000 + (hash32(mt20id || '') % 90001); // 130,000~220,000
}

/**
 * FNV-1a + fmix32 마무리. mt20id는 'PF300745'처럼 접두가 같고 끝자리만 다르다. 단순
 * {@code h * 31 + c} 해시는 그런 입력에서 값이 서로 붙어 나와, 대역을 90,000이나 잡아도 실제로는
 * 7,000 폭에만 몰렸다. 마무리 단계(avalanche)가 그 뭉침을 흩어 준다.
 */
function hash32(text) {
  let hash = 2166136261 >>> 0;
  for (let i = 0; i < text.length; i++) {
    hash ^= text.charCodeAt(i);
    hash = Math.imul(hash, 16777619) >>> 0;
  }
  hash ^= hash >>> 16;
  hash = Math.imul(hash, 2246822507) >>> 0;
  hash ^= hash >>> 13;
  hash = Math.imul(hash, 3266489909) >>> 0;
  hash ^= hash >>> 16;
  return hash >>> 0;
}

/** mt20id 해시 기반 결정값 view_count (20000~80000) */
export function deterministicViewCount(mt20id) {
  let hash = 0;
  const s = mt20id || '';
  for (let i = 0; i < s.length; i++) {
    hash = (hash * 31 + s.charCodeAt(i)) >>> 0;
  }
  return 20000 + (hash % 60001); // 20000~80000
}

/** SQL 문자열 리터럴 이스케이프: '→'' + 개행/제어문자 공백 정리 */
export function sqlStr(value) {
  if (value === null || value === undefined) return 'NULL';
  const cleaned = String(value)
    .replace(/[\r\n\t]+/g, ' ')
    .replace(/\s{2,}/g, ' ')
    .trim()
    .replace(/'/g, "''");
  return `'${cleaned}'`;
}

/**
 * KOPIS 포스터 URL을 프론트가 허용하는 형태로 정규화한다.
 *
 * KOPIS는 포스터를 'http://www.kopis.or.kr/upload/...'로 준다. 그런데 ticket-fe의 next/image는
 * remotePatterns에 'https://kopis.or.kr/upload/**'만 허용한다 — www 하위도메인도, http도 통과하지
 * 못하고 페이지 전체가 렌더 오류로 죽는다. 기존 시드가 이미 https://kopis.or.kr 형태만 쓰고 있으므로
 * 같은 형태로 맞춘다.
 *
 * kopis.or.kr이 아닌 호스트는 손대지 않는다 — 허용 목록을 여기서 추측하지 않는다.
 */
export function normalizePosterUrl(url) {
  const raw = (url || '').trim();
  if (!raw) return raw;
  return raw.replace(/^https?:\/\/(?:www\.)?kopis\.or\.kr\//i, 'https://kopis.or.kr/');
}

/** 정규화 dedupe 키 (제목+공연장): 공백/대괄호/구두점 제거 후 소문자 */
export function normalizeKey(title, venue) {
  const norm = (x) =>
    (x || '')
      .toLowerCase()
      .replace(/\[[^\]]*\]/g, '')
      .replace(/[\s()[\]{}._,~!:+\-/]/g, '');
  return `${norm(title)}|${norm(venue)}`;
}
