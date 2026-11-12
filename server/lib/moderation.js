// Filter otomatis chat. Menormalkan teks (leetspeak, spasi, huruf mirip) sebelum diperiksa.
const { normalizeForCheck } = require('./util');

const LEET = { '0': 'o', '1': 'i', '3': 'e', '4': 'a', '5': 's', '7': 't', '8': 'b', '@': 'a', '$': 's', '!': 'i', '|': 'i', '+': 't' };

function prep(text) {
  let t = String(text || '').normalize('NFKD').replace(/[\u0300-\u036f]/g, '').normalize('NFKC').toLowerCase();
  t = Array.from(t).map((c) => LEET[c] || c).join('');
  const tokens = t.split(/[^a-z0-9]+/).filter(Boolean);
  const compact = t.replace(/[^a-z]/g, '');
  const squeezed = compact.replace(/(.)\1+/g, '$1');
  return { raw: String(text || ''), tokens, compact, squeezed };
}

const has = (p, words) => words.some((w) => p.compact.includes(w) || p.squeezed.includes(w.replace(/(.)\1+/g, '$1')));
const hasToken = (p, words) => p.tokens.some((t) => words.includes(t));

// This list and the severe SARA patterns are intentionally not configurable.
const SEVERE = ['yahudi', 'kafir', 'cina', 'chinese', 'negro', 'nigger', 'nigga', 'bangsaagama', 'agamasampah', 'islamteroris', 'kristensampah'];
const SEVERE_COMBO = [/(hina|ejek|bakar|sampah|jelek|goblok|bodoh|anjing|babi).{0,12}(islam|kristen|hindu|budha|buddha|katolik|agama|nabi|tuhan|allah|yesus)/,
  /(islam|kristen|hindu|budha|buddha|katolik|agama|nabi|tuhan|allah|yesus).{0,12}(sampah|anjing|babi|goblok|bodoh|bangsat|jelek)/,
  /(ras|suku).{0,8}(rendah|sampah|hina)/];
const RUDE = ['anjing', 'anjir', 'bangsat', 'bangsad', 'kontol', 'memek', 'ngentot', 'jancok', 'jancuk', 'tolol', 'goblok', 'bajingan', 'asu', 'kampret', 'brengsek', 'sialan', 'pantek', 'pepek', 'itil', 'tai', 'tahi', 'fuck', 'bitch', 'shit', 'asshole', 'bastard', 'idiot', 'babi', 'monyet', 'bego', 'bacot', 'goblok', 'goblog', 'keparat', 'kimak', 'pukimak', 'perek', 'lonte', 'pelacur', 'sundal', 'ngehe', 'bloon', 'dungu', 'bajingan', 'fuck you', 'motherfucker'];
const RUDE_TOKENS = ['asu', 'tai', 'goblok', 'goblog', 'tolol', 'bego', 'idiot', 'babi', 'bacot', 'bloon', 'dungu', 'fck', 'fuck', 'fucking', 'fuckyou', 'shit', 'shitty', 'wtf', 'dick', 'pussy'];
const PROMO = ['openorder', 'openpo', 'readystock', 'readystok', 'jualapk', 'jualbeli', 'dmaja', 'dmsaya', 'wame', 'wasaya', 'chatwa', 'chatpribadi', 'hubungiwa', 'orderdm', 'cod', 'pricelist', 'harga', 'diskon', 'promo', 'murah', 'terjangkau', 'bayar', 'transfer', 'rekening', 'admin fee', 'sewa', 'jasa'];
const PLATFORM = ['telegram', 'instagram', 'tiktok', 'tik tok', 'whatsapp', 'youtube', 'facebook', 'discord', 'linktr', 'bio.link'];
const PLATFORM_TOKENS = ['tele', 'tili', 'tg', 'ig', 'igoy', 'wa', 'fb', 'dc', 'yt'];
const MOVE_CHAT = ['pindahchat', 'pindahke', 'chatpribadi', 'japri', 'dmaku', 'dmsaya', 'inboxaja'];
const MOVE_TOKENS = ['dm', 'pm', 'inbox', 'japri'];
const BRAND = ['wamod', 'gbwhatsapp', 'gbwa', 'fmwhatsapp', 'whatsappplus', 'yowhatsapp', 'competitor', 'spotify', 'resso', 'joox', 'youtubevanced', 'revanced', 'musicmod', 'metrolist'];
const OFFTOPIC = ['pulsa', 'kuota', 'judi', 'slot', 'gacor', 'togel', 'casino', 'poker', 'taruhan', 'crypto', 'kripto', 'bitcoin', 'nft', 'rekrut', 'recruit', 'lowongan', 'hiring', 'followback', 'followers', 'subscribe', 'giveaway', 'pinjol', 'investasi', 'trading'];
const CODE = [/```/, /\bfunction\s*\w*\s*\(/, /\bconsole\.log\b/, /\bimport\s+[\w{*]/, /\bpublic\s+(static|class|void)\b/, /\bdef\s+\w+\s*\(/, /#include\s*</, /<\/?[a-z][\s\S]*>/, /=>\s*{/, /\bSELECT\b.+\bFROM\b/i, /\bcurl\s+-/, /\bsudo\s+\w+/, /\bpip\s+install\b/, /\bnpm\s+(i|install)\b/, /\bgit\s+(clone|push|commit)\b/, /\bfun\s+\w+\s*\(.*\)\s*[:{]/, /\b(val|var|let|const)\s+\w+\s*=/];

function phoneInText(raw) {
  const digits = raw.replace(/[^\d+]/g, '');
  if (/(\+?62|0)8\d{7,12}/.test(digits)) return true;
  // 9+ digit berturut-turut setelah menghapus pemisah umum
  const squashed = raw.replace(/[\s.\-_()]/g, '');
  if (/\d{9,}/.test(squashed)) return true;
  // angka ditulis dengan kata
  const words = ['nol', 'kosong', 'satu', 'dua', 'tiga', 'empat', 'lima', 'enam', 'tujuh', 'delapan', 'sembilan'];
  const t = raw.toLowerCase().split(/[^a-z]+/).filter(Boolean);
  let run = 0;
  for (const w of t) { if (words.includes(w)) { run++; if (run >= 7) return true; } else run = 0; }
  return false;
}

/** @returns {{ok:true}|{ok:false, severe:boolean, reason:string}} */
function checkMessage(text, { global = true, policy = {} } = {}) {
  const p = prep(text);
  if (SEVERE_COMBO.some((r) => r.test(p.compact)) || SEVERE.some((w) => p.compact.includes(w) && /(hina|sampah|anjing|babi|bunuh|usir|musnah|benci)/.test(p.compact))) {
    return { ok: false, severe: true, reason: 'Penghinaan agama/ras (SARA)' };
  }
  if (policy.rudeEnabled !== false && (has(p, RUDE.filter((w) => w.length > 4)) || hasToken(p, RUDE_TOKENS))) {
    return { ok: false, severe: false, reason: 'Bahasa kasar / hinaan' };
  }
  if (policy.spamEnabled !== false && (/(.)\1{9,}/.test(p.raw) || /(\b\w+\b)(\s+\1){4,}/i.test(p.raw))) {
    return { ok: false, severe: false, reason: 'Spam' };
  }
  const customWords = Array.isArray(policy.extraBlockedWords) ? policy.extraBlockedWords : [];
  if (customWords.length) {
    const normalized = customWords.map((word) => normalizeForCheck(word)).filter(Boolean);
    if (has(p, normalized.filter((word) => word.length > 4)) || hasToken(p, normalized.filter((word) => word.length <= 4))) {
      return { ok: false, severe: false, reason: 'Kata yang diblokir komunitas' };
    }
  }
  if (!global) return { ok: true };
  // Code/script detection remains enabled; link policy controls URL and contact-number checks.
  if (CODE.some((r) => r.test(p.raw))) return { ok: false, severe: false, reason: 'Source code / script / command dilarang' };
  if (policy.linksEnabled !== false) {
    if (phoneInText(p.raw)) return { ok: false, severe: false, reason: 'Menulis nomor HP / WA dilarang' };
    if (/(?:https?:\/\/|www\d?\.)|\b(?:[a-z0-9-]+\.)+(?:com|net|org|id|me|link|xyz|site|co|io|app|gg|tv|ly|to|dev|info|biz)\b|t\.me\/|wa\.me\//i.test(p.raw)) return { ok: false, severe: false, reason: 'Link dilarang di Global Chat' };
  }
  if (policy.promotionEnabled !== false) {
    if (has(p, ['tiktok', 'tik tok'])) return { ok: false, severe: false, reason: 'TikTok / promosi platform lain dilarang di Global Chat' };
    if (has(p, PLATFORM) || hasToken(p, PLATFORM_TOKENS) || has(p, MOVE_CHAT) || hasToken(p, MOVE_TOKENS)) return { ok: false, severe: false, reason: 'Menyebut platform lain / ajakan pindah chat' };
    if (has(p, PROMO.filter((w) => w.length > 4)) || (hasToken(p, ['jual', 'jualan', 'order', 'po', 'cod']) && (p.tokens.length > 1))) return { ok: false, severe: false, reason: 'Promo / jualan dilarang' };
    if (has(p, BRAND)) return { ok: false, severe: false, reason: 'Promosi APK/brand lain dilarang' };
    if (has(p, OFFTOPIC)) return { ok: false, severe: false, reason: 'Konten off-topic (pulsa/judi/crypto/rekrut/spam)' };
  }
  return { ok: true };
}

module.exports = { checkMessage };
