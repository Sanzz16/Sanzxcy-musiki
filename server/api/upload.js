const crypto = require('crypto');
const U = require('../lib/util');
const { cmd } = U;
const { put } = require('@vercel/blob');
const LIMITS = { avatar: 450 * 1024, banner: 3 * 1024 * 1024, sticker: 700 * 1024, roomWallpaper: 3 * 1024 * 1024, pollImage: 2 * 1024 * 1024, statusPhoto: 3 * 1024 * 1024 };
const ROLLING_QUOTAS = { avatar: 3, banner: 2 };
const QUOTA_WINDOW_MS = 7 * 24 * 60 * 60 * 1000;
const EXT = { 'image/jpeg': 'jpg', 'image/png': 'png', 'image/webp': 'webp', 'video/mp4': 'mp4' };
const SUPPORTED = new Set(['image/jpeg', 'image/png', 'image/webp']);

function imageSignatureMatches(buf, type) {
  if (type === 'image/jpeg') return buf.length >= 3 && buf[0] === 0xff && buf[1] === 0xd8 && buf[2] === 0xff;
  if (type === 'image/png') return buf.length >= 8 && buf.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]));
  if (type === 'image/webp') return buf.length >= 12 && buf.toString('ascii', 0, 4) === 'RIFF' && buf.toString('ascii', 8, 12) === 'WEBP';
  return true;
}

// Redis TIME keeps all Vercel instances on one clock. Pruning, checking, and
// reserving in one Lua invocation makes the quota safe under concurrent uploads.
const RESERVE_QUOTA_LUA = `
local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
local window = 604800000
redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now - window)
local used = redis.call('ZCARD', KEYS[1])
local limit = tonumber(ARGV[1])
if used >= limit then
  local oldest = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES')
  return {0, used, tonumber(oldest[2]) + window}
end
redis.call('ZADD', KEYS[1], now, ARGV[2])
redis.call('PEXPIRE', KEYS[1], window + 60000)
local oldest = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES')
return {1, used + 1, tonumber(oldest[2]) + window}
`;
const RELEASE_QUOTA_LUA = `return redis.call('ZREM', KEYS[1], ARGV[1])`;

async function reserveQuota(username, kind) {
  const limit = ROLLING_QUOTAS[kind];
  const key = `upload:quota:${kind}:${U.lower(username)}`;
  const reservationId = crypto.randomUUID();
  const result = await cmd('EVAL', RESERVE_QUOTA_LUA, '1', key, String(limit), reservationId);
  if (!Array.isArray(result) || result.length < 3) throw new Error('Unexpected upload quota response from Redis.');
  return {
    allowed: Number(result[0]) === 1,
    used: Number(result[1]),
    limit,
    resetAt: Number(result[2]),
    key,
    reservationId,
  };
}

async function releaseQuota(reservation) {
  if (reservation) await cmd('EVAL', RELEASE_QUOTA_LUA, '1', reservation.key, reservation.reservationId);
}

async function supabaseUpload(kind, filename, buf, type) {
  const cfg = U.supabaseStorageConfig();
  if (!cfg) return { configured: false, ok: false, error: 'Supabase storage is not configured.' };
  if (!SUPPORTED.has(type)) return { configured: true, ok: false, error: 'This media type is not supported by the Supabase replica.' };
  const authHeaders = { apikey: cfg.key };
  // Supabase sb_secret_* keys are not JWTs; sending them as Bearer triggers Invalid JWT.
  if (!cfg.key.startsWith('sb_secret_')) authHeaders.Authorization = `Bearer ${cfg.key}`;
  const headers = { ...authHeaders, 'Content-Type': 'application/json' };
  const bucketUrl = `${cfg.url}/storage/v1/bucket`;
  // Safe to retry: bucket creation is idempotent, and an already-existing bucket is accepted.
  const create = await fetch(bucketUrl, { method: 'POST', headers, body: JSON.stringify({ id: cfg.bucket, name: cfg.bucket, public: true, file_size_limit: 3 * 1024 * 1024, allowed_mime_types: [...SUPPORTED] }) });
  if (!create.ok && create.status !== 409) {
    const existing = await fetch(`${bucketUrl}/${encodeURIComponent(cfg.bucket)}`, { headers });
    if (!existing.ok) return { configured: true, ok: false, error: `Supabase bucket setup failed (${create.status}).` };
  }
  const path = `${kind}/${filename}`;
  const response = await fetch(`${cfg.url}/storage/v1/object/${encodeURIComponent(cfg.bucket)}/${path.split('/').map(encodeURIComponent).join('/')}`, {
    method: 'POST', headers: { ...authHeaders, 'Content-Type': type, 'x-upsert': 'true' }, body: buf,
  });
  if (!response.ok) return { configured: true, ok: false, error: `Supabase upload failed (${response.status}).` };
  return { configured: true, ok: true, url: `${cfg.url}/storage/v1/object/public/${encodeURIComponent(cfg.bucket)}/${path.split('/').map(encodeURIComponent).join('/')}` };
}

module.exports = async (req, res) => {
  let reservation = null;
  try {
    if (req.method !== 'POST') return U.fail(res, 405, 'Method tidak didukung');
    const a = await U.auth(req, res); if (!a) return;
    const b = U.body(req);
    const kind = b.kind; const type = b.contentType;
    if (kind === 'pollImage' && a.user.role !== 'owner') return U.fail(res, 403, 'Hanya owner yang dapat mengunggah gambar poll.');
    if (!LIMITS[kind]) return U.fail(res, 400, 'Jenis upload tidak valid');
    if (kind === 'statusPhoto' && !SUPPORTED.has(type)) return U.fail(res, 400, 'Foto status harus berupa JPEG, PNG, atau WebP.');
    if (!EXT[type] || (type === 'video/mp4' && kind !== 'banner')) return U.fail(res, 400, 'Tipe file tidak didukung');
    const buf = Buffer.from(String(b.data || ''), 'base64');
    if (!buf.length) return U.fail(res, 400, 'File kosong');
    if (buf.length > LIMITS[kind]) return U.fail(res, 413, `Ukuran file maksimal ${Math.round(LIMITS[kind] / 1024)} KB untuk ${kind}.`);
    if (kind === 'statusPhoto' && !imageSignatureMatches(buf, type)) return U.fail(res, 400, 'Format foto tidak cocok dengan tipe file.');

    // Preserve the existing per-user daily total upload limit and its behavior.
    const rl = Number(await cmd('INCR', `rl:up:${a.user.username}`)); if (rl === 1) await cmd('EXPIRE', `rl:up:${a.user.username}`, '86400');
    if (rl > 40) return U.fail(res, 429, 'Batas upload harian tercapai.');
    if (kind === 'statusPhoto') {
      const statusUploads = Number(await cmd('INCR', `rl:up:statusPhoto:${a.user.username}`));
      if (statusUploads === 1) await cmd('EXPIRE', `rl:up:statusPhoto:${a.user.username}`, '86400');
      if (statusUploads > 20) return U.fail(res, 429, 'Batas unggah foto status harian tercapai.');
    }

    // Sticker uploads deliberately do not consume avatar/banner rolling quota.
    if (ROLLING_QUOTAS[kind]) {
      reservation = await reserveQuota(a.user.username, kind);
      if (!reservation.allowed) {
        return U.fail(res, 429, `Batas ${kind} ${reservation.limit} unggahan dalam 7 hari tercapai.`, {
          code: 'upload_quota_exceeded', kind, used: reservation.used, limit: reservation.limit, resetAt: reservation.resetAt,
        });
      }
    }

    try {
      const filename = `${a.user.username}-${Date.now()}.${EXT[type]}`;
      const results = [];
      try {
        const blob = await put(`${kind}/${filename}`, buf, { access: 'public', contentType: type, addRandomSuffix: true, token: process.env.BLOB_READ_WRITE_TOKEN });
        results.push({ provider: 'vercel-blob', ok: true, url: blob.url });
      } catch (_) { results.push({ provider: 'vercel-blob', ok: false }); }
      if (kind === 'avatar' || kind === 'banner' || kind === 'roomWallpaper') {
        try {
          const result = await supabaseUpload(kind, filename, buf, type);
          results.push({ provider: 'supabase', ok: result.ok, ...(result.url ? { url: result.url } : {}), ...(result.error ? { error: result.error } : {}) });
        } catch (_) { results.push({ provider: 'supabase', ok: false, error: 'Supabase upload failed.' }); }
      }
      const urls = results.filter((r) => r.ok && r.url).map((r) => r.url);
      if (!urls.length) {
        // A failed attempt must not consume a rolling-window slot.
        await releaseQuota(reservation).catch(() => {});
        reservation = null;
        return U.fail(res, 502, 'Upload gagal di semua storage.', { replicas: results.map(({ provider, ok, error }) => ({ provider, ok, ...(error ? { error } : {}) })), replicaCount: 0, replicaTarget: kind === 'avatar' || kind === 'banner' ? 2 : 1 });
      }
      if (kind === 'statusPhoto') {
        const { uploadKey } = require('./statuses').constants;
        for (const url of urls) await cmd('SET', uploadKey(url), a.user.username, 'EX', '86400');
      }
      const supabaseConfigured = results.some((r) => r.provider === 'supabase' && r.ok) || !!U.supabaseStorageConfig();
      return U.send(res, 200, { ok: true, url: urls[0], urls, replicaCount: urls.length, replicaTarget: kind === 'avatar' || kind === 'banner' ? 2 : 1, replicas: results.map(({ provider, ok, error }) => ({ provider, ok, ...(error ? { error } : {}) })), ...(kind === 'avatar' || kind === 'banner' || kind === 'roomWallpaper' ? { setup: supabaseConfigured ? 'configured' : 'supabase_not_configured' } : {}) });
    } catch (e) {
      // Release reservations if an unexpected exception interrupts storage work.
      await releaseQuota(reservation).catch(() => {});
      reservation = null;
      throw e;
    }
  } catch (e) { return U.fail(res, 500, 'Server error: ' + e.message); }
};
