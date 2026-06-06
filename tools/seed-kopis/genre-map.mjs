// KOPIS 데이터 → 앱 스키마 매핑/파서 유틸
// 설계: docs/superpowers/specs/2026-06-06-kopis-data-refresh-design.md §5,§6

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

/** 정규화 dedupe 키 (제목+공연장): 공백/대괄호/구두점 제거 후 소문자 */
export function normalizeKey(title, venue) {
  const norm = (x) =>
    (x || '')
      .toLowerCase()
      .replace(/\[[^\]]*\]/g, '')
      .replace(/[\s()[\]{}._,~!:+\-/]/g, '');
  return `${norm(title)}|${norm(venue)}`;
}
