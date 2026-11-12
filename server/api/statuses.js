const crypto = require('crypto');
const U = require('../lib/util');
const { cmd } = U;

const TTL_SECONDS = 3 * 60 * 60;
const MAX_FEED = 100;
const MAX_CAPTION = 240;
const CREATE_LIMIT_PER_DAY = 12;
const PRUNE_INDEX_LUA = `
redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
return redis.call('ZCARD', KEYS[1])
`;
const ADD_STATUS_LUA = `
local count = tonumber(redis.call('INCR', KEYS[1]))
if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
if count > tonumber(ARGV[2]) then
  redis.call('DECR', KEYS[1])
  return 0
end
redis.call('SET', KEYS[2], ARGV[3], 'EX', ARGV[4])
redis.call('ZADD', KEYS[3], ARGV[5], ARGV[6])
return 1
`;
const ADD_VIEW_LUA = `
if redis.call('EXISTS', KEYS[1]) == 0 then return {-1, 0} end
local added = redis.call('SADD', KEYS[2], ARGV[1])
if added == 1 then redis.call('EXPIRE', KEYS[2], ARGV[2]) end
return {added, redis.call('SCARD', KEYS[2])}
`;
const DELETE_STATUS_LUA = `
local raw = redis.call('GET', KEYS[1])
if not raw then return -1 end
local ok, item = pcall(cjson.decode, raw)
if not ok or type(item) ~= 'table' then return -1 end
if item.from ~= ARGV[1] then return -2 end
redis.call('DEL', KEYS[1], KEYS[2])
redis.call('ZREM', KEYS[3], ARGV[2])
return 1
`;

const uploadKey = (url) => `status:upload:${crypto.createHash('sha256').update(url).digest('hex')}`;
const statusKey = (id) => `status:item:${id}`;

async function liveStatus(id, now) {
  const raw = await cmd('GET', statusKey(id));
  if (!raw) return null;
  try {
    const item = JSON.parse(raw);
    if (!item || Number(item.expiresAt) <= now || typeof item.id !== 'string') {
      await cmd('ZREM', 'statuses:index', id);
      return null;
    }
    return item;
  } catch {
    await cmd('DEL', statusKey(id));
    await cmd('ZREM', 'statuses:index', id);
    return null;
  }
}

module.exports = async (req, res) => {
  try {
    if (!['GET', 'POST', 'DELETE'].includes(req.method)) return U.fail(res, 405, 'Method tidak didukung');
    const auth = await U.auth(req, res); if (!auth) return;
    const me = auth.user;
    const now = Date.now();

    if (req.method === 'GET') {
      await cmd('EVAL', PRUNE_INDEX_LUA, '1', 'statuses:index', String(now));
      const ids = await cmd('ZREVRANGEBYSCORE', 'statuses:index', '+inf', String(now), 'LIMIT', '0', String(MAX_FEED));
      const statuses = [];
      for (const id of ids || []) {
        const item = await liveStatus(String(id), now);
        if (!item) continue;
        const user = await U.getUser(item.from);
        if (!user || user.banned || user.archivedAt || user.deletedAt) continue;
        item.author = U.publicUser(user);
        item.views = item.from === me.username ? Number(await cmd('SCARD', `status:views:${item.id}`)) || 0 : 0;
        statuses.push(item);
      }
      return U.send(res, 200, { ok: true, statuses, serverTime: now, ttlSeconds: TTL_SECONDS });
    }

    if (req.method === 'DELETE') {
      const id = String(req.query.id || '').slice(0, 64);
      if (!/^[a-f0-9-]{36}$/i.test(id)) return U.fail(res, 400, 'ID status tidak valid');
      const result = Number(await cmd('EVAL', DELETE_STATUS_LUA, '3', statusKey(id), `status:views:${id}`, 'statuses:index', me.username, id));
      if (result === -1) return U.fail(res, 404, 'Status tidak ditemukan atau sudah kedaluwarsa');
      if (result === -2) return U.fail(res, 403, 'Kamu hanya dapat menghapus status milikmu.');
      return U.send(res, 200, { ok: true });
    }

    const body = U.body(req);
    if (body.action === 'view') {
      const id = String(body.id || '').slice(0, 64);
      if (!/^[a-f0-9-]{36}$/i.test(id)) return U.fail(res, 400, 'ID status tidak valid');
      const item = await liveStatus(id, now);
      if (!item) return U.fail(res, 404, 'Status tidak ditemukan atau sudah kedaluwarsa');
      const user = await U.getUser(item.from);
      if (!user || user.banned || user.archivedAt || user.deletedAt) return U.fail(res, 404, 'Status tidak ditemukan');
      const remaining = Math.max(1, Math.ceil((Number(item.expiresAt) - now) / 1000));
      if (item.from === me.username) {
        const views = Number(await cmd('SCARD', `status:views:${id}`)) || 0;
        return U.send(res, 200, { ok: true, views, added: false });
      }
      const result = await cmd('EVAL', ADD_VIEW_LUA, '2', statusKey(id), `status:views:${id}`, me.username, String(remaining));
      if (Number(result?.[0]) < 0) return U.fail(res, 404, 'Status tidak ditemukan atau sudah kedaluwarsa');
      return U.send(res, 200, { ok: true, views: Number(result[1]) || 0, added: Number(result[0]) === 1 });
    }

    if (body.action !== 'create') return U.fail(res, 400, 'Aksi status tidak valid');
    const photoUrl = typeof body.photoUrl === 'string' ? body.photoUrl.trim() : '';
    const caption = typeof body.caption === 'string' ? body.caption.trim() : '';
    if (!photoUrl || photoUrl.length > 600 || !U.allowedMediaUrl(photoUrl)) return U.fail(res, 400, 'Foto status tidak valid');
    if (caption.length > MAX_CAPTION) return U.fail(res, 400, `Deskripsi maksimal ${MAX_CAPTION} karakter.`);
    const uploadedBy = await cmd('GET', uploadKey(photoUrl));
    if (!uploadedBy || uploadedBy !== me.username) return U.fail(res, 403, 'Foto harus diunggah dari akun kamu melalui fitur status.');

    const id = crypto.randomUUID();
    const expiresAt = now + TTL_SECONDS * 1000;
    const status = {
      id,
      from: me.username,
      displayName: me.displayName || me.username,
      photoUrl,
      caption,
      createdAt: now,
      expiresAt,
      views: 0,
    };
    const result = Number(await cmd(
      'EVAL', ADD_STATUS_LUA, '3', `status:rate:${me.username}`, statusKey(id), 'statuses:index',
      '86400', String(CREATE_LIMIT_PER_DAY), JSON.stringify(status), String(TTL_SECONDS), String(expiresAt), id,
    ));
    if (!result) return U.fail(res, 429, 'Batas 12 status dalam 24 jam tercapai.', { code: 'rate_limited' });
    const user = await U.getUser(me.username);
    status.author = U.publicUser(user);
    return U.send(res, 201, { ok: true, status, ttlSeconds: TTL_SECONDS });
  } catch (e) {
    return U.fail(res, 500, 'Server error: ' + e.message);
  }
};

module.exports.constants = { TTL_SECONDS, MAX_FEED, MAX_CAPTION, CREATE_LIMIT_PER_DAY, uploadKey, statusKey };
module.exports.scripts = { PRUNE_INDEX_LUA, ADD_STATUS_LUA, ADD_VIEW_LUA, DELETE_STATUS_LUA };
