const crypto = require('crypto');
const { cmd, getJson, setJson } = require('./db');

const ROLE_RANK = { user: 0, staff: 1, admin: 2, owner: 3 };

function send(res, status, body) {
  res.status(status).setHeader('Content-Type', 'application/json; charset=utf-8').send(JSON.stringify(body));
}
function fail(res, status, message, extra) { send(res, status, { ok: false, error: message, ...(extra || {}) }); }

function ipOf(req) {
  const xf = (req.headers['x-forwarded-for'] || '').split(',')[0].trim();
  return xf || req.headers['x-real-ip'] || (req.socket && req.socket.remoteAddress) || 'unknown';
}

function body(req) {
  if (req.body && typeof req.body === 'object') return req.body;
  try { return JSON.parse(req.body || '{}'); } catch { return {}; }
}

// ---- Password: scrypt + salt (tidak pernah disimpan atau ditampilkan dalam bentuk asli) ----
function hashPassword(password, salt = crypto.randomBytes(16).toString('hex')) {
  const hash = crypto.scryptSync(password, salt, 64).toString('hex');
  return { salt, hash };
}
function verifyPassword(password, salt, hash) {
  const test = crypto.scryptSync(password, salt, 64);
  const real = Buffer.from(hash, 'hex');
  return real.length === test.length && crypto.timingSafeEqual(real, test);
}

const lower = (s) => String(s || '').trim().toLowerCase();
const userKey = (name) => `u:${lower(name)}`;
async function getUser(name) { return getJson(userKey(name)); }
async function saveUser(u) { return setJson(userKey(u.username), u); }

async function createSession(user) {
  const token = crypto.randomBytes(32).toString('hex');
  await cmd('SET', `sess:${token}`, `${lower(user.username)}|${user.ver || 0}`, 'EX', String(60 * 60 * 24 * 30));
  return token;
}

// IP banned? (dipanggil di setiap endpoint)
async function isIpBanned(ip) { return (await cmd('SISMEMBER', 'bannedips', ip)) === 1; }

/** Autentikasi: kembalikan {user, ip} atau kirim error dan return null. */
async function auth(req, res, { minRole = 'user', allowMuted = true } = {}) {
  const ip = ipOf(req);
  if (await isIpBanned(ip)) { fail(res, 403, 'IP kamu diblokir dari komunitas.', { code: 'ip_banned' }); return null; }
  const h = req.headers.authorization || '';
  const token = h.startsWith('Bearer ') ? h.slice(7) : '';
  if (!token) { fail(res, 401, 'Belum login.', { code: 'unauthorized' }); return null; }
  const sess = await cmd('GET', `sess:${token}`);
  if (!sess) { fail(res, 401, 'Sesi berakhir, silakan login lagi.', { code: 'unauthorized' }); return null; }
  const [name, ver] = String(sess).split('|');
  const user = await getUser(name);
  if (!user) { fail(res, 401, 'Akun tidak ditemukan.', { code: 'unauthorized' }); return null; }
  if (user.archivedAt || user.deletedAt) { fail(res, 401, 'Akun tidak aktif. Hubungi owner komunitas.', { code: 'unauthorized' }); return null; }
  if ((user.ver || 0) !== Number(ver || 0)) { fail(res, 401, 'Password diubah, silakan login lagi.', { code: 'unauthorized' }); return null; }
  if (user.banned) { fail(res, 403, 'Akun kamu diblokir permanen dari fitur komunitas.', { code: 'banned' }); return null; }
  if ((ROLE_RANK[user.role] || 0) < (ROLE_RANK[minRole] || 0)) { fail(res, 403, 'Tidak punya izin.', { code: 'forbidden' }); return null; }
  user.lastIp = ip; user.lastSeen = Date.now();
  user.ips = user.ips || [];
  if (!user.ips.find((x) => x.ip === ip)) { user.ips.push({ ip, first: Date.now(), last: Date.now() }); }
  else user.ips.find((x) => x.ip === ip).last = Date.now();
  if (user.ips.length > 30) user.ips = user.ips.slice(-30);
  await saveUser(user);
  await cmd('SADD', `ip:${ip}`, lower(user.username));
  return { user, ip, token };
}

/** Owner key (header x-owner-key) -> akses penuh untuk owner.html */
function ownerKeyOk(req) {
  const k = req.headers['x-owner-key'] || '';
  const real = process.env.OWNER_KEY || '';
  if (!real || !k) return false;
  const a = Buffer.from(String(k)); const b = Buffer.from(real);
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

function supabaseStorageConfig() {
  const url = String(process.env.SUPABASE_URL || '').replace(/\/$/, '');
  const key = process.env.SUPABASE_SECRET_KEY || process.env.SUPABASE_SERVICE_ROLE_KEY || '';
  if (!url || !key) return null;
  try { const parsed = new URL(url); if (parsed.protocol !== 'https:' || parsed.username || parsed.password) return null; }
  catch { return null; }
  return { url, key, bucket: process.env.SUPABASE_STORAGE_BUCKET || 'community-media' };
}
function allowedMediaUrl(value) {
  if (typeof value !== 'string' || !value) return false;
  try {
    const u = new URL(value);
    if (u.protocol !== 'https:' || u.username || u.password) return false;
    if (/^[a-z0-9-]+\.public\.blob\.vercel-storage\.com$/i.test(u.hostname)) return true;
    const cfg = supabaseStorageConfig();
    if (!cfg) return false;
    const host = new URL(cfg.url).host.toLowerCase();
    const prefix = `/storage/v1/object/public/${encodeURIComponent(cfg.bucket)}/`.toLowerCase();
    return u.host.toLowerCase() === host && u.pathname.toLowerCase().startsWith(prefix);
  } catch { return false; }
}
function mediaUrls(u, singular, plural) {
  const values = Array.isArray(u[plural]) ? u[plural] : (u[singular] ? [u[singular]] : []);
  return [...new Set(values.filter(allowedMediaUrl))].slice(0, 2);
}
function publicUser(u, { full = false } = {}) {
  if (!u) return null;
  const customAvatarUrls = mediaUrls(u, 'avatarUrl', 'avatarUrls');
  const generatedAvatar = `https://api.dicebear.com/9.x/adventurer/png?seed=${encodeURIComponent(String(u.username || 'user'))}&size=256`;
  const o = {
    username: u.username,
    displayName: u.displayName || u.username,
    bio: u.bio || '',
    avatarUrl: customAvatarUrls[0] || generatedAvatar,
    avatarUrls: customAvatarUrls.length ? customAvatarUrls : [generatedAvatar],
    bannerUrl: mediaUrls(u, 'bannerUrl', 'bannerUrls')[0] || '',
    bannerUrls: mediaUrls(u, 'bannerUrl', 'bannerUrls'),
    bannerType: u.bannerType || 'image',
    role: u.role || 'user',
    title: u.title || null,
    joinedAt: u.joinedAt,
  };
  if (full) {
    o.mutedUntil = u.mutedUntil || 0;
    o.warns = u.warns || 0;
    o.email = u.email || '';
    o.emailVerified = !!u.emailVerified;
  }
  return o;
}

// ---- Validasi nama/title: tolak owner/admin/staff dalam font/leetspeak apa pun ----
const LEET = { '0': 'o', '1': 'i', '3': 'e', '4': 'a', '5': 's', '7': 't', '8': 'b', '9': 'g', '@': 'a', '$': 's', '!': 'i', '|': 'i', '+': 't' };
const HOMO = { 'а': 'a', 'е': 'e', 'о': 'o', 'р': 'p', 'с': 'c', 'х': 'x', 'у': 'y', 'і': 'i', 'ӏ': 'l', 'ѕ': 's', 'ј': 'j', 'ɑ': 'a', 'ο': 'o', 'ν': 'v', 'τ': 't', 'α': 'a', 'ε': 'e', 'ι': 'i', 'κ': 'k', 'ρ': 'p', 'ѵ': 'v' };
function normalizeForCheck(s) {
  let t = String(s || '').normalize('NFKD').replace(/[\u0300-\u036f]/g, '').normalize('NFKC').toLowerCase();
  t = Array.from(t).map((c) => HOMO[c] || LEET[c] || c).join('');
  return t.replace(/[^a-z]/g, '');
}
const RESERVED = ['owner', 'admin', 'staff', 'administrator', 'moderator', 'sanzxcyofficial'];
function hasReservedWord(s) {
  const n = normalizeForCheck(s);
  // juga cek tanpa huruf ganda: "ooowner" -> "owner"
  const squeezed = n.replace(/(.)\1+/g, '$1');
  return RESERVED.some((w) => n.includes(w) || squeezed.includes(w.replace(/(.)\1+/g, '$1')));
}

module.exports = {
  cmd, getJson, setJson, send, fail, ipOf, body, hashPassword, verifyPassword, lower, getUser, saveUser,
  createSession, auth, ownerKeyOk, publicUser, hasReservedWord, normalizeForCheck, isIpBanned, ROLE_RANK,
  supabaseStorageConfig, allowedMediaUrl, mediaUrls,
};
