'use strict';

const U = require('../lib/util');
const { cmd } = U;

const TTL_SECONDS = 14 * 24 * 60 * 60;
const MAX_INBOX = 100;
const CREATE_LUA = `
if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end
redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2])
redis.call('SET', KEYS[2], ARGV[3], 'EX', ARGV[2])
redis.call('LPUSH', KEYS[3], ARGV[4])
redis.call('LTRIM', KEYS[3], 0, tonumber(ARGV[5]) - 1)
redis.call('EXPIRE', KEYS[3], ARGV[2])
redis.call('LPUSH', KEYS[4], ARGV[4])
redis.call('LTRIM', KEYS[4], 0, tonumber(ARGV[5]) - 1)
redis.call('EXPIRE', KEYS[4], ARGV[2])
return 1
`;
const TRANSITION_LUA = `
local raw = redis.call('GET', KEYS[1])
if not raw then return {0, ''} end
local ok, invite = pcall(cjson.decode, raw)
if not ok or type(invite) ~= 'table' then return {0, ''} end
if invite.to ~= ARGV[1] then return {-1, raw} end
if invite.status == ARGV[2] then return {2, raw} end
if invite.status ~= 'pending' then return {-2, raw} end
invite.status = ARGV[2]
invite.respondedAt = tonumber(ARGV[3])
local updated = cjson.encode(invite)
redis.call('SET', KEYS[1], updated, 'EX', ARGV[4])
redis.call('DEL', ARGV[5] .. invite.from .. ':' .. invite.to)
return {1, updated}
`;
const safeInvite = (invite) => ({
  id: invite.id,
  from: invite.from,
  fromDisplayName: invite.fromDisplayName,
  to: invite.to,
  status: invite.status,
  createdAt: invite.createdAt,
  expiresAt: invite.expiresAt,
  ...(invite.respondedAt ? { respondedAt: invite.respondedAt } : {}),
});

module.exports = async (req, res) => {
  try {
    const auth = await U.auth(req, res); if (!auth) return;
    const me = auth.user;
    if (req.method === 'GET') {
      const ids = await cmd('LRANGE', `invites:inbox:${me.username}`, '0', String(MAX_INBOX - 1));
      const invites = [];
      for (const id of ids) {
        const invite = await U.getJson(`invite:${id}`);
        if (invite && invite.to === me.username && invite.expiresAt > Date.now()) invites.push(safeInvite(invite));
      }
      const sentIds = await cmd('LRANGE', `invites:sent:${me.username}`, '0', String(MAX_INBOX - 1));
      const sent = [];
      for (const id of sentIds) {
        const invite = await U.getJson(`invite:${id}`);
        if (invite && invite.from === me.username && invite.expiresAt > Date.now()) sent.push(safeInvite(invite));
      }
      return U.send(res, 200, { ok: true, invites, sent });
    }
    if (req.method !== 'POST') return U.fail(res, 405, 'Method tidak didukung');
    const body = U.body(req);
    const action = String(body.action || 'create');
    if (action === 'create') {
      const target = U.lower(body.username);
      if (!/^[a-z0-9_.]{3,20}$/.test(target)) return U.fail(res, 400, 'Username tujuan tidak valid');
      if (target === me.username) return U.fail(res, 400, 'Tidak dapat mengundang diri sendiri');
      const recipient = await U.getUser(target);
      if (!recipient || recipient.banned || recipient.archivedAt || recipient.deletedAt) return U.fail(res, 404, 'Pengguna tidak ditemukan');
      const now = Date.now();
      const id = require('crypto').randomUUID();
      const invite = {
        id, from: me.username, fromDisplayName: String(me.displayName || me.username).slice(0, 60),
        to: target, status: 'pending', createdAt: now, expiresAt: now + TTL_SECONDS * 1000,
      };
      const created = Number(await cmd('EVAL', CREATE_LUA, '4', `invite:pending:${me.username}:${target}`, `invite:${id}`, `invites:inbox:${target}`, `invites:sent:${me.username}`, id, String(TTL_SECONDS), JSON.stringify(invite), id, String(MAX_INBOX)));
      if (!created) return U.fail(res, 409, 'Permintaan pertemanan masih menunggu jawaban', { code: 'invite_pending' });
      return U.send(res, 201, { ok: true, invite: safeInvite(invite) });
    }
    if (action === 'accept' || action === 'decline') {
      const id = String(body.id || '');
      if (!/^[0-9a-f-]{36}$/i.test(id)) return U.fail(res, 400, 'ID undangan tidak valid');
      const status = action === 'accept' ? 'accepted' : 'declined';
      const result = await cmd('EVAL', TRANSITION_LUA, '1', `invite:${id}`, me.username, status, String(Date.now()), String(TTL_SECONDS), 'invite:pending:');
      const code = Number(result?.[0]);
      if (code === 0) return U.fail(res, 404, 'Undangan tidak ditemukan atau kedaluwarsa');
      if (code === -1) return U.fail(res, 404, 'Undangan tidak ditemukan');
      if (code === -2) return U.fail(res, 409, 'Undangan sudah diproses', { code: 'invite_resolved' });
      const invite = JSON.parse(result[1]);
      return U.send(res, 200, { ok: true, invite: safeInvite(invite), unchanged: code === 2 });
    }
    return U.fail(res, 400, 'Aksi tidak valid');
  } catch (e) { return U.fail(res, 500, 'Server error: ' + e.message); }
};

module.exports.CREATE_LUA = CREATE_LUA;
module.exports.TRANSITION_LUA = TRANSITION_LUA;
