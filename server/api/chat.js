const U = require('../lib/util');
const { cmd } = U;
const { checkMessage } = require('../lib/moderation');

const BLOB_HOST = /^https:\/\/[a-z0-9-]+\.public\.blob\.vercel-storage\.com\//i;
const MAX_KEEP = { global: 400, dm: 300 };
const DAY_SECONDS = 24 * 60 * 60;
const MAX_POLL_VOTERS = 1000;
const UPDATE_POLL_LUA = `
local entries = redis.call('LRANGE', KEYS[1], 0, -1)
for i, raw in ipairs(entries) do
  local ok, msg = pcall(cjson.decode, raw)
  if ok and type(msg) == 'table' and tonumber(msg.id) == tonumber(ARGV[1]) and msg.type == 'poll' and type(msg.poll) == 'table' then
    local poll = msg.poll
    if ARGV[2] == 'close' then
      if poll.closed == true then return {0, raw} end
      if msg.from ~= ARGV[3] then return {-2, raw} end
      poll.closed = true
      poll.closedAt = tonumber(ARGV[4])
    else
      if poll.closed == true then return {-3, raw} end
      local option = tonumber(ARGV[4])
      if not option or option < 1 or option > #poll.options then return {-4, raw} end
      local found = false
      for _, vote in ipairs(poll.votes) do
        if vote.user == ARGV[3] then vote.option = option; found = true; break end
      end
      if not found then
        if #poll.votes >= tonumber(ARGV[5]) then return {-5, raw} end
        table.insert(poll.votes, {user=ARGV[3], option=option})
      end
    end
    local updated = cjson.encode(msg)
    redis.call('LSET', KEYS[1], i - 1, updated)
    return {1, updated}
  end
end
return {-1, ''}
`;

// Atomic prune: concurrent RPUSH cannot be lost, and only entries older than
// the cutoff are removed. LREM count=1 is safe even when identical JSON repeats.
const PRUNE_ROOM_LUA = `
local entries = redis.call('LRANGE', KEYS[1], 0, -1)
local cutoff = tonumber(ARGV[1])
for _, raw in ipairs(entries) do
  local ok, msg = pcall(cjson.decode, raw)
  if not ok or type(msg) ~= 'table' or not tonumber(msg.ts) or tonumber(msg.ts) <= cutoff
     or (msg.moderationNotice == true and tonumber(msg.expiresAt) and tonumber(msg.expiresAt) <= tonumber(ARGV[4])) then
    redis.call('LREM', KEYS[1], 1, raw)
  end
end
redis.call('LTRIM', KEYS[1], -tonumber(ARGV[2]), -1)
if redis.call('EXISTS', KEYS[1]) == 1 then redis.call('EXPIRE', KEYS[1], ARGV[3]) end
return redis.call('LLEN', KEYS[1])
`;

// Older owner-panel releases also copied announcements into auditlog. Prune
// only these announcement copies; unrelated moderation/admin audit records stay.
const PRUNE_ANNOUNCEMENT_AUDIT_LUA = `
local entries = redis.call('LRANGE', KEYS[1], 0, -1)
local cutoff = tonumber(ARGV[1])
for _, raw in ipairs(entries) do
  local ok, entry = pcall(cjson.decode, raw)
  if ok and type(entry) == 'table' and type(entry.msg) == 'string'
     and string.sub(entry.msg, 1, 8) == 'notice: '
     and (not tonumber(entry.ts) or tonumber(entry.ts) <= cutoff) then
    redis.call('LREM', KEYS[1], 1, raw)
  end
end
return redis.call('LLEN', KEYS[1])
`;

async function maintainRoom(room) {
  const keep = room === 'global' ? MAX_KEEP.global : MAX_KEEP.dm;
  let retention = { globalMinutes: 1440, dmMinutes: 1440 };
  try { const rawSettings = await cmd('GET', 'chat:retention'); if (rawSettings) retention = JSON.parse(rawSettings); } catch { /* preserve safe default */ }
  const minutes = Math.max(1, Math.min(43200, Number(room === 'global' ? retention.globalMinutes : retention.dmMinutes) || 1440));
  const retentionSeconds = minutes * 60;
  const length = Number(await cmd('EVAL', PRUNE_ROOM_LUA, '1', `chat:${room}`, String(Date.now() - retentionSeconds * 1000), String(keep), String(retentionSeconds), String(Date.now())));
  if (room === 'global') {
    await cmd('EVAL', PRUNE_ANNOUNCEMENT_AUDIT_LUA, '1', 'auditlog', String(Date.now() - retentionSeconds * 1000));
  }
  return length;
}

function roomInfo(room, me) {
  if (room === 'global') return { type: 'global' };
  const listen = /^listen:([A-Z0-9]{4,16})$/.exec(room || '');
  if (listen) return { type: 'listen', code: listen[1] };
  const m = /^dm:([a-z0-9_.]{3,20}):([a-z0-9_.]{3,20})$/.exec(room || '');
  if (!m) return null;
  const [a, b] = [m[1], m[2]];
  if (a >= b || (me !== a && me !== b)) return null;
  return { type: 'dm', a, b, peer: me === a ? b : a };
}
async function canAccessListenRoom(info, username) {
  if (!info || info.type !== 'listen') return true;
  const room = await U.getJson(`lt:${info.code}`) || await U.getJson(`lt:ws:${info.code}`);
  return !!room && (room.host === username || (Array.isArray(room.members) && room.members.includes(username)));
}

const dmRoom = (x, y) => (x < y ? `dm:${x}:${y}` : `dm:${y}:${x}`);

async function pushMessage(room, msg, keep) {
  await maintainRoom(room);
  msg.id = Number(await cmd('INCR', `seq:${room}`));
  await cmd('RPUSH', `chat:${room}`, JSON.stringify(msg));
  await cmd('LTRIM', `chat:${room}`, String(-keep), '-1');
  await cmd('EXPIRE', `chat:${room}`, String(DAY_SECONDS));
  return msg;
}

async function getPinnedMessage(room) {
  if (room !== 'global') return null;
  const key = `chat:pinned:${room}`;
  const raw = await cmd('GET', key);
  if (!raw) return null;
  try {
    const pin = JSON.parse(raw);
    if (!pin.message || Date.now() - Number(pin.message.ts || 0) >= DAY_SECONDS * 1000) {
      await cmd('DEL', key);
      return null;
    }
    return pin;
  } catch {
    await cmd('DEL', key);
    return null;
  }
}

async function moderationNotice(room, username, text, noticeSeconds) {
  const now = Date.now();
  const admitted = await cmd('SET', `chat:moderation-notice:${room}:${username}`, String(now), 'NX', 'EX', String(noticeSeconds));
  if (!admitted) return null;
  const dm = /^dm:([a-z0-9_.]{3,20}):([a-z0-9_.]{3,20})$/.exec(room || '');
  if (dm) {
    await cmd('ZADD', `dms:${dm[1]}`, String(now), room);
    await cmd('ZADD', `dms:${dm[2]}`, String(now), room);
  }
  return pushMessage(room, {
    type: 'notice', text, from: 'sanzxcy-bot', displayName: 'Sanzxcy Bot',
    avatarUrl: '', avatarUrls: [], role: 'staff', title: null, ts: now,
    moderationNotice: true, expiresAt: now + noticeSeconds * 1000,
  }, dm ? MAX_KEEP.dm : MAX_KEEP.global);
}

function replySnapshot(original) {
  const reply = {
    id: Number(original.id),
    from: String(original.from || '').slice(0, 20),
    displayName: String(original.displayName || original.from || '').slice(0, 60),
    type: original.type === 'sticker' ? 'sticker' : (original.type === 'notice' ? 'notice' : 'text'),
  };
  if (typeof original.text === 'string') reply.text = original.text.slice(0, 500);
  if (typeof original.stickerUrl === 'string' && BLOB_HOST.test(original.stickerUrl)) reply.stickerUrl = original.stickerUrl.slice(0, 500);
  return reply;
}

async function findMessage(room, id) {
  const keep = room === 'global' ? MAX_KEEP.global : MAX_KEEP.dm;
  const raw = await cmd('LRANGE', `chat:${room}`, String(-keep), '-1');
  for (const item of raw) {
    try {
      const message = JSON.parse(item);
      if (Number(message.id) === id) return message;
    } catch { /* ignore malformed legacy records */ }
  }
  return null;
}

async function handleChat(req, res) {
  try {
    const a = await U.auth(req, res); if (!a) return;
    const me = a.user;
    const action = req.query.action || '';

    if (req.method === 'GET' && action === 'dms') {
      const rooms = await cmd('ZREVRANGE', `dms:${me.username}`, '0', '29');
      const out = [];
      for (const r of rooms) {
        const info = roomInfo(r, me.username); if (!info) continue;
        const length = await maintainRoom(r);
        if (!length) {
          await cmd('ZREM', `dms:${me.username}`, r);
          await cmd('ZREM', `dms:${info.peer}`, r);
          continue;
        }
        const last = await cmd('LRANGE', `chat:${r}`, '-1', '-1');
        const peer = await U.getUser(info.peer);
        out.push({ room: r, peer: U.publicUser(peer), last: last[0] ? JSON.parse(last[0]) : null });
      }
      return U.send(res, 200, { ok: true, dms: out });
    }

    if (req.method === 'GET' && action === 'room') {
      const peer = U.lower(req.query.peer);
      if (!(await U.getUser(peer)) || peer === me.username) return U.fail(res, 404, 'Pengguna tidak ditemukan');
      return U.send(res, 200, { ok: true, room: dmRoom(me.username, peer) });
    }

    if (req.method === 'GET') {
      const room = req.query.room || 'global';
      const info = roomInfo(room, me.username);
      if (!info || !(await canAccessListenRoom(info, me.username))) return U.fail(res, 403, 'Kamu bukan anggota ruang dengar ini.');
      await maintainRoom(room);
      const after = Number(req.query.after || 0);
      const raw = await cmd('LRANGE', `chat:${room}`, '-80', '-1');
      const messages = raw.map((x) => { try { return JSON.parse(x); } catch { return null; } }).filter((m) => m && m.id > after);
      const pinned = await getPinnedMessage(room);
      const muted = me.mutedUntil && me.mutedUntil > Date.now() ? me.mutedUntil : 0;
      const { getSettings } = require('../lib/bot-settings');
      const policy = await getSettings();
      const lastSentAt = Number(await cmd('GET', `chat:last:${me.username}`) || 0);
      const cooldownUntil = policy.spamEnabled && lastSentAt + policy.cooldownSeconds * 1000 > Date.now() ? lastSentAt + policy.cooldownSeconds * 1000 : 0;
      return U.send(res, 200, { ok: true, messages, pinned, mutedUntil: muted, warns: me.warns || 0, cooldownUntil });
    }

    if (req.method === 'DELETE') {
      const room = req.query.room || 'global'; const id = Number(req.query.id);
      const info = roomInfo(room, me.username);
      if (!info || !(await canAccessListenRoom(info, me.username))) return U.fail(res, 403, 'Kamu bukan anggota ruang dengar ini.');
      await maintainRoom(room);
      const raw = await cmd('LRANGE', `chat:${room}`, '-200', '-1');
      const hit = raw.find((x) => { try { return JSON.parse(x).id === id; } catch { return false; } });
      if (!hit) return U.fail(res, 404, 'Pesan tidak ditemukan');
      const m = JSON.parse(hit);
      const canDelete = m.from === me.username || (U.ROLE_RANK[me.role] || 0) >= 1;
      if (!canDelete) return U.fail(res, 403, 'Tidak punya izin menghapus pesan ini');
      await cmd('LREM', `chat:${room}`, '1', hit);
      const pinKey = `chat:pinned:${room}`;
      const pinRaw = await cmd('GET', pinKey);
      if (pinRaw) {
        try { if (JSON.parse(pinRaw).message?.id === id) await cmd('DEL', pinKey); } catch { await cmd('DEL', pinKey); }
      }
      return U.send(res, 200, { ok: true });
    }

    if (req.method !== 'POST') return U.fail(res, 405, 'Method tidak didukung');
    const b = U.body(req);
    const room = b.room || 'global';
    if (['createPoll', 'votePoll', 'closePoll'].includes(b.action) && room !== 'global') return U.fail(res, 400, 'Poll hanya tersedia di Global Chat');
    const info = roomInfo(room, me.username);
    if (!info || !(await canAccessListenRoom(info, me.username))) return U.fail(res, 403, 'Kamu bukan anggota ruang dengar ini.');

    if (['createPoll', 'votePoll', 'closePoll'].includes(b.action)) {
      if (room !== 'global') return U.fail(res, 400, 'Poll hanya tersedia di Global Chat');
      if (me.mutedUntil && me.mutedUntil > Date.now()) return U.fail(res, 403, 'Kamu sedang di-mute dari chat.', { code: 'muted', mutedUntil: me.mutedUntil });
      if (b.action === 'createPoll') {
        if (me.role !== 'owner') return U.fail(res, 403, 'Hanya owner yang dapat membuat poll.', { code: 'forbidden' });
        const question = typeof b.question === 'string' ? b.question.trim() : '';
        if (!question || question.length > 200) return U.fail(res, 400, 'Pertanyaan wajib diisi (maksimal 200 karakter)');
        if (!Array.isArray(b.options) || b.options.length < 2 || b.options.length > 10) return U.fail(res, 400, 'Poll harus memiliki 2 hingga 10 pilihan');
        const options = b.options.map((option) => typeof option === 'string' ? option.trim() : '');
        if (options.some((option) => !option || option.length > 100)) return U.fail(res, 400, 'Setiap pilihan wajib diisi (maksimal 100 karakter)');
        if (new Set(options.map((option) => option.toLocaleLowerCase())).size !== options.length) return U.fail(res, 400, 'Pilihan poll harus unik');
        let imageUrl = '';
        if (b.imageUrl !== undefined && b.imageUrl !== null && b.imageUrl !== '') {
          if (typeof b.imageUrl !== 'string' || b.imageUrl.length > 500 || !U.allowedMediaUrl(b.imageUrl)) return U.fail(res, 400, 'URL gambar tidak valid');
          imageUrl = b.imageUrl;
        }
        const msg = { type: 'poll', from: me.username, displayName: me.displayName || me.username, avatarUrl: me.avatarUrl || '', avatarUrls: U.mediaUrls(me, 'avatarUrl', 'avatarUrls'), role: me.role, title: me.title || null, ts: Date.now(), poll: { question, options, imageUrl, votes: [], closed: false } };
        const saved = await pushMessage('global', msg, MAX_KEEP.global);
        return U.send(res, 200, { ok: true, message: saved });
      }
      const id = Number(b.id);
      if (!Number.isSafeInteger(id) || id <= 0) return U.fail(res, 400, 'ID poll tidak valid');
      await maintainRoom('global');
      const isClose = b.action === 'closePoll';
      const option = Number(b.option);
      if (!isClose && (!Number.isSafeInteger(option) || option < 1 || option > 10)) return U.fail(res, 400, 'Pilihan suara tidak valid');
      const result = await cmd('EVAL', UPDATE_POLL_LUA, '1', 'chat:global', String(id), isClose ? 'close' : 'vote', me.username, isClose ? String(Date.now()) : String(option), String(MAX_POLL_VOTERS));
      const status = Number(result?.[0]);
      if (status === -1) return U.fail(res, 404, 'Poll tidak ditemukan');
      if (status === -2) return U.fail(res, 403, 'Hanya pembuat poll yang dapat menutupnya.');
      if (status === -3) return U.fail(res, 409, 'Poll sudah ditutup');
      if (status === -4) return U.fail(res, 400, 'Pilihan suara tidak valid');
      if (status === -5) return U.fail(res, 409, 'Poll telah mencapai batas suara');
      const message = JSON.parse(result[1]);
      return U.send(res, 200, { ok: true, message, changed: status === 1 });
    }

    if (b.action === 'pin' || b.action === 'unpin') {
      if (room !== 'global') return U.fail(res, 400, 'Pin hanya tersedia di Global Chat');
      if ((U.ROLE_RANK[me.role] || 0) < 1) return U.fail(res, 403, 'Hanya staff, admin, atau owner yang dapat mengelola pin');
      const pinKey = `chat:pinned:${room}`;
      if (b.action === 'unpin') {
        await maintainRoom(room);
        await cmd('DEL', pinKey);
        return U.send(res, 200, { ok: true, pinned: null });
      }
      const id = Number(b.id);
      if (!Number.isSafeInteger(id) || id <= 0) return U.fail(res, 400, 'ID pesan tidak valid');
      await maintainRoom(room);
      const entries = await cmd('LRANGE', `chat:${room}`, '-400', '-1');
      const hit = entries.find((entry) => { try { return JSON.parse(entry).id === id; } catch { return false; } });
      if (!hit) return U.fail(res, 404, 'Pesan tidak ditemukan');
      const message = JSON.parse(hit);
      const remainingSeconds = Math.max(1, Math.ceil((Number(message.ts || Date.now()) + DAY_SECONDS * 1000 - Date.now()) / 1000));
      const pinned = { message, pinnedBy: me.username, pinnedAt: Date.now() };
      await cmd('SET', pinKey, JSON.stringify(pinned), 'EX', String(remainingSeconds));
      return U.send(res, 200, { ok: true, pinned });
    }

    if (me.mutedUntil && me.mutedUntil > Date.now()) {
      return U.fail(res, 403, 'Kamu sedang di-mute dari chat.', { code: 'muted', mutedUntil: me.mutedUntil, cooldownUntil: me.mutedUntil });
    }

    const type = b.type === 'sticker' ? 'sticker' : 'text';
    const msg = {
      type, from: me.username, displayName: me.displayName || me.username, avatarUrl: me.avatarUrl || '',
      avatarUrls: U.mediaUrls(me, 'avatarUrl', 'avatarUrls'), role: me.role, title: me.title || null, ts: Date.now(),
    };
    if (type === 'sticker') {
      if (!BLOB_HOST.test(String(b.stickerUrl || ''))) return U.fail(res, 400, 'Stiker tidak valid');
      msg.stickerUrl = b.stickerUrl;
    } else {
      const text = String(b.text || '').trim();
      if (!text) return U.fail(res, 400, 'Pesan kosong');
      if (text.length > 500) return U.fail(res, 400, 'Maksimal 500 karakter');
      if (text.toLowerCase() === '/clearall') {
        if (me.role !== 'owner') return U.fail(res, 403, 'Perintah /clearall hanya untuk owner.', { code: 'forbidden' });
        await cmd('DEL', 'chat:global', 'chat:pinned:global');
        await cmd('LPUSH', 'auditlog', JSON.stringify({ ts: Date.now(), by: me.username, msg: 'semua pesan Global Chat dibersihkan dengan /clearall' }));
        await cmd('LTRIM', 'auditlog', '0', '299');
        return U.send(res, 200, { ok: true, cleared: true });
      }
      msg.text = text;
    }

    // Reply targets are always resolved from the authenticated room history;
    // no client-supplied snapshot is accepted or copied into storage.
    if (Object.prototype.hasOwnProperty.call(b, 'replyToId')) {
      const replyToId = Number(b.replyToId);
      if (!Number.isSafeInteger(replyToId) || replyToId <= 0) return U.fail(res, 400, 'ID balasan tidak valid');
      await maintainRoom(room);
      const original = await findMessage(room, replyToId);
      if (!original) return U.fail(res, 404, 'Pesan balasan tidak ditemukan atau sudah kedaluwarsa');
      msg.reply = replySnapshot(original);
    }

    const { getSettings } = require('../lib/bot-settings');
    const policy = await getSettings();
    const now = Date.now();
    const cooldownKey = `chat:last:${me.username}`;
    const lastSentAt = Number(await cmd('GET', cooldownKey) || 0);
    const cooldownUntil = policy.spamEnabled ? lastSentAt + policy.cooldownSeconds * 1000 : 0;
    if (policy.spamEnabled && lastSentAt > 0 && now < cooldownUntil) {
      me.mutedUntil = now + policy.spamMuteSeconds * 1000;
      await U.saveUser(me);
      await moderationNotice(room, me.username, `@${me.username} dibatasi ${policy.spamMuteSeconds} detik oleh Sanzxcy Bot karena spam. Jeda minimum antar pesan: ${policy.cooldownSeconds} detik.`, policy.noticeSeconds);
      return U.fail(res, 403, `Sanzxcy Bot membatasi chat selama ${policy.spamMuteSeconds} detik karena spam.`, { code: 'muted', mutedUntil: me.mutedUntil, cooldownUntil });
    }
    if (policy.spamEnabled) await cmd('SET', cooldownKey, String(now), 'EX', '86400');

    // Community rules apply regardless of rank; routine violations use the
    // normal warning/mute response. Only ordinary accounts receive the severe
    // SARA permanent-ban action.
    if (type === 'text') {
      const c = checkMessage(msg.text, { global: info.type === 'global', policy });
      if (!c.ok) {
        if (c.severe && (U.ROLE_RANK[me.role] || 0) === 0) {
          me.banned = true; await U.saveUser(me);
          await moderationNotice(room, me.username, `@${me.username} diblokir permanen dari fitur komunitas oleh Sanzxcy Bot. Alasan: ${c.reason}.`, policy.noticeSeconds);
          return U.fail(res, 403, 'Akun kamu diblokir permanen dari komunitas karena penghinaan SARA.', { code: 'banned', cooldownUntil: now + policy.cooldownSeconds * 1000 });
        }
        me.warns = (me.warns || 0) + 1;
        const warningCount = me.warns;
        let tail = `Peringatan ${warningCount}/${policy.warningLimit}.`; let muted = 0;
        if (warningCount >= policy.warningLimit) { muted = Date.now() + policy.warningMuteMinutes * 60 * 1000; me.mutedUntil = muted; me.warns = 0; tail = `Batas ${policy.warningLimit} peringatan tercapai: chat dibekukan ${policy.warningMuteMinutes} menit.`; }
        await U.saveUser(me);
        await moderationNotice(room, me.username, `Pesan @${me.username} dihapus oleh Sanzxcy Bot. Alasan: ${c.reason}. ${tail}`, policy.noticeSeconds);
        return U.fail(res, 422, `Pesan dihapus oleh Sanzxcy Bot: ${c.reason}. ${tail}`, { code: 'moderated', reason: c.reason, warns: me.warns, mutedUntil: muted, cooldownUntil: now + policy.cooldownSeconds * 1000 });
      }
    }

    const saved = await pushMessage(room, msg, info.type === 'global' ? MAX_KEEP.global : MAX_KEEP.dm);
    if (info.type === 'dm') {
      await cmd('ZADD', `dms:${info.a}`, String(Date.now()), room);
      await cmd('ZADD', `dms:${info.b}`, String(Date.now()), room);
    }
    return U.send(res, 200, { ok: true, message: saved, cooldownUntil: now + policy.cooldownSeconds * 1000 });
  } catch (e) { return U.fail(res, 500, 'Server error: ' + e.message); }
}

module.exports = handleChat;
module.exports.maintainChatRoom = maintainRoom;
module.exports.PRUNE_ROOM_LUA = PRUNE_ROOM_LUA;
