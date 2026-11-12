// Endpoint panel owner/admin/staff. Akses: header x-owner-key (OWNER_KEY) = owner penuh,
// atau token login dengan role staff/admin/owner (izin dibatasi per role).
const crypto = require('crypto');
const U = require('../lib/util');
const BotSettings = require('../lib/bot-settings');
const { cmd } = U;

const RANK = U.ROLE_RANK;
const MIN = {
  'stats': 1, 'users.search': 1, 'user.get': 1, 'user.mute': 1, 'user.unmute': 1, 'user.warns.reset': 1, 'chat.delete': 1,
  'ip.lookup': 2, 'ip.list': 2, 'ip.ban': 2, 'ip.unban': 2, 'user.ban': 2, 'user.unban': 2, 'user.resetPassword': 1,
  'user.create': 3,
  'user.archive': 3, 'user.unarchive': 3, 'user.delete': 3,
  'user.setRole': 3, 'user.setTitle': 2, 'maintenance.set': 3, 'update.set': 3, 'changelog.set': 3, 'status.get': 1,
  'maintenance.history.list': 3, 'maintenance.history.update': 3, 'maintenance.history.delete': 3, 'maintenance.history.apply': 3,
  'bot.settings.get': 3, 'bot.settings.set': 3,
  'chat.retention.get': 3, 'chat.retention.set': 3, 'chat.clearall': 3,
  'feedback.list': 3, 'feedback.update': 3, 'feedback.archive': 3,
  'rooms.list': 3, 'rooms.update': 3, 'rooms.close': 3, 'rooms.closeAll': 3, 'rooms.openAll': 3,
  'announcements.list': 3, 'announcements.history': 3, 'announcements.create': 3, 'announcements.update': 3, 'announcements.delete': 3,
};
const READ_ONLY = new Set(['stats', 'users.search', 'user.get', 'ip.lookup', 'ip.list', 'status.get', 'bot.settings.get', 'chat.retention.get', 'feedback.list', 'maintenance.history.list', 'rooms.list', 'announcements.list', 'announcements.history']);
const HISTORY_ACTIONS = new Set(['maintenance.history.list', 'maintenance.history.update', 'maintenance.history.delete', 'maintenance.history.apply', 'announcements.history']);
const MAINTENANCE_HISTORY_KEY = 'owner:maintenance-history';
const HISTORY_LIMIT = 100;
const ANNOUNCEMENTS_KEY = 'app:announcements';
const ANNOUNCEMENT_HISTORY_LIMIT = 50;
const OWNER_KEY_ATTEMPT_LIMIT = 10;
const OWNER_KEY_WINDOW_SECONDS = 600;
const ACTION_ALIASES = {
  'announcement.list': 'announcements.list',
  'announcement.history': 'announcements.history',
  'announcement.create': 'announcements.create',
  'announcement.update': 'announcements.update',
  'announcement.delete': 'announcements.delete',
};

function tempPassword() {
  const a = 'abcdefghjkmnpqrstuvwxyz23456789';
  return Array.from(crypto.randomBytes(10)).map((x) => a[x % a.length]).join('');
}

function adminView(u, includeIps) {
  const o = {
    username: u.username, displayName: u.displayName, role: u.role, title: u.title, joinedAt: u.joinedAt, lastSeen: u.lastSeen,
    warns: u.warns || 0, mutedUntil: u.mutedUntil || 0, banned: !!u.banned, avatarUrl: u.avatarUrl, bio: u.bio, mustChange: !!u.mustChange,
    archivedAt: u.archivedAt || 0, deletedAt: u.deletedAt || 0,
  };
  if (includeIps) { o.lastIp = u.lastIp; o.ips = u.ips || []; }
  return o;
}

async function readHistory(key) {
  const history = await U.getJson(key);
  return Array.isArray(history) ? history : [];
}
function validHistoryId(value) { return typeof value === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value); }
function findHistoryItem(history, id) { return history.find((item) => item && item.id === id && !item.deletedAt); }
async function readAnnouncements() { const rows = await U.getJson(ANNOUNCEMENTS_KEY); return Array.isArray(rows) ? rows : []; }
async function addAnnouncementRevision(item, action, actorName) {
  const key = `app:announcement:${item.id}:history`;
  const rows = await U.getJson(key);
  const history = Array.isArray(rows) ? rows : [];
  history.unshift({ id: crypto.randomUUID(), action, title: item.title, description: item.description, by: actorName, at: Date.now() });
  await U.setJson(key, history.slice(0, ANNOUNCEMENT_HISTORY_LIMIT));
}

async function commitChangelogToGithub(md) {
  const token = process.env.GITHUB_TOKEN; if (!token) return { skipped: true };
  const repo = process.env.GITHUB_REPO || 'Sanzz16/Sanzxcy-Music'; const path = 'changelog.md';
  const api = `https://api.github.com/repos/${repo.split('/').map(encodeURIComponent).join('/')}/contents/${encodeURIComponent(path)}`;
  const h = { Authorization: `Bearer ${token}`, Accept: 'application/vnd.github+json', 'Content-Type': 'application/json', 'X-GitHub-Api-Version': '2022-11-28', 'User-Agent': 'sanzxcy-owner' };
  const cur = await fetch(api, { headers: h }); let sha;
  if (cur.ok) sha = (await cur.json()).sha;
  const put = await fetch(api, { method: 'PUT', headers: h, body: JSON.stringify({ message: 'Update changelog via owner panel', content: Buffer.from(md).toString('base64'), sha }) });
  return { ok: put.ok, status: put.status };
}

module.exports = async (req, res) => {
  try {
    if (req.method !== 'GET' && req.method !== 'POST') {
      res.setHeader('Allow', 'GET, POST');
      return U.fail(res, 405, 'Method tidak didukung');
    }
    const b = req.method === 'POST' ? U.body(req) : {};
    const requestedAction = req.query.action || b.action;
    const action = ACTION_ALIASES[requestedAction] || requestedAction;
    if (!MIN[action]) return U.fail(res, 400, 'Aksi tidak dikenal');
    const readOnly = READ_ONLY.has(action);
    if ((readOnly && req.method !== 'GET') || (!readOnly && req.method !== 'POST')) {
      res.setHeader('Allow', readOnly ? 'GET' : 'POST');
      return U.fail(res, 405, 'Method tidak didukung');
    }

    let level = 0; let actorName = 'owner-key'; let exactOwner = false;
    const ownerKey = U.ownerKeyOk(req);
    const suppliedOwnerKey = req.headers && req.headers['x-owner-key'];
    const hasBearer = /^Bearer\s+\S+/i.test(String(req.headers && req.headers.authorization || ''));
    if (!ownerKey && process.env.OWNER_KEY && typeof suppliedOwnerKey === 'string' && suppliedOwnerKey.length > 0) {
      try {
        const ipHash = crypto.createHash('sha256').update(U.ipOf(req)).digest('hex');
        const rateKey = `rl:owner-key:${ipHash}`;
        const attempts = Number(await cmd(
          'EVAL',
          "local n = redis.call('INCR', KEYS[1]); if n == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]); end; return n",
          '1', rateKey, String(OWNER_KEY_WINDOW_SECONDS),
        ));
        if ((!Number.isFinite(attempts) || attempts > OWNER_KEY_ATTEMPT_LIMIT) && !hasBearer) {
          return U.fail(res, 429, 'Terlalu banyak percobaan owner key. Coba lagi dalam 10 menit.');
        }
      } catch (e) {
        if (!hasBearer) throw e;
      }
    }
    if (ownerKey) level = 3;
    else {
      const a = await U.auth(req, res); if (!a) return;
      level = RANK[a.user.role] || 0; actorName = a.user.username;
      exactOwner = a.user.role === 'owner';
    }
    if (level < 1) return U.fail(res, 403, 'Tidak punya izin.');
    if (level < MIN[action]) return U.fail(res, 403, 'Role kamu belum punya izin untuk aksi ini.');
    if (action.startsWith('feedback.') && !ownerKey && !exactOwner) return U.fail(res, 403, 'Masukan komunitas hanya dapat diakses owner.');
    if (action.startsWith('rooms.') && !ownerKey && !exactOwner) return U.fail(res, 403, 'Kontrol room hanya dapat diakses owner.');
    if (action.startsWith('announcements.') && !ownerKey && !exactOwner) return U.fail(res, 403, 'Pengumuman global hanya dapat dikelola owner.');
    if (HISTORY_ACTIONS.has(action) && !ownerKey && !exactOwner) return U.fail(res, 403, 'Histori hanya dapat diakses owner.');
    const log = (msg) => cmd('LPUSH', 'auditlog', JSON.stringify({ ts: Date.now(), by: actorName, msg })).then(() => cmd('LTRIM', 'auditlog', '0', '299'));

    if (action.startsWith('announcements.')) {
      const rows = await readAnnouncements();
      if (action === 'announcements.list') {
        rows.sort((a, b) => Number(b.updatedAt || b.createdAt) - Number(a.updatedAt || a.createdAt));
        return U.send(res, 200, { ok: true, announcements: rows.slice(0, 500) });
      }
      const id = String(b.id || req.query.id || '');
      if (action === 'announcements.history') {
        if (!validHistoryId(id)) return U.fail(res, 400, 'ID pengumuman tidak valid.');
        const history = await U.getJson(`app:announcement:${id}:history`);
        return U.send(res, 200, { ok: true, history: Array.isArray(history) ? history : [] });
      }
      if (action === 'announcements.create' || action === 'announcements.update') {
        const title = typeof b.title === 'string' ? b.title.trim() : '';
        const description = typeof b.description === 'string' ? b.description.trim() : '';
        if (!title || title.length > 100 || !description || description.length > 2000) {
          return U.fail(res, 400, 'Judul wajib 1–100 karakter dan deskripsi wajib 1–2000 karakter.');
        }
        if (action === 'announcements.create') {
          const now = Date.now();
          const item = { id: crypto.randomUUID(), title, description, createdAt: now, createdBy: actorName, updatedAt: now, updatedBy: actorName, deletedAt: 0, deletedBy: '' };
          rows.unshift(item);
          await U.setJson(ANNOUNCEMENTS_KEY, rows.slice(0, 500));
          await addAnnouncementRevision(item, 'create', actorName);
          await log(`pengumuman global dibuat: ${item.id}`);
          return U.send(res, 201, { ok: true, announcement: item });
        }
        if (!validHistoryId(id)) return U.fail(res, 400, 'ID pengumuman tidak valid.');
        const item = rows.find((entry) => entry && entry.id === id && !entry.deletedAt);
        if (!item) return U.fail(res, 404, 'Pengumuman tidak ditemukan.');
        await addAnnouncementRevision(item, 'edit', actorName);
        item.title = title; item.description = description;
        item.updatedAt = Math.max(Date.now(), Number(item.updatedAt || 0) + 1); item.updatedBy = actorName;
        await U.setJson(ANNOUNCEMENTS_KEY, rows);
        await log(`pengumuman global diperbarui: ${item.id}`);
        return U.send(res, 200, { ok: true, announcement: item });
      }
      if (action === 'announcements.delete') {
        if (!validHistoryId(id)) return U.fail(res, 400, 'ID pengumuman tidak valid.');
        const item = rows.find((entry) => entry && entry.id === id && !entry.deletedAt);
        if (!item) return U.fail(res, 404, 'Pengumuman tidak ditemukan.');
        await addAnnouncementRevision(item, 'delete', actorName);
        item.deletedAt = Date.now(); item.deletedBy = actorName; item.updatedAt = Math.max(item.deletedAt, Number(item.updatedAt || 0) + 1);
        await U.setJson(ANNOUNCEMENTS_KEY, rows);
        await log(`pengumuman global dihapus: ${item.id}`);
        return U.send(res, 200, { ok: true, announcement: item });
      }
    }

    if ((action.startsWith('bot.settings.') || action.startsWith('chat.retention.') || action === 'chat.clearall') && !ownerKey && !exactOwner) {
      return U.fail(res, 403, 'Pengaturan bot hanya dapat diakses owner.');
    }
    if (action.startsWith('rooms.')) {
      const scanRooms = async (pattern) => {
        const keys = []; let cursor = '0';
        do { const result = await cmd('SCAN', cursor, 'MATCH', pattern, 'COUNT', '200'); cursor = String(result[0]); if (Array.isArray(result[1])) keys.push(...result[1]); } while (cursor !== '0');
        return keys;
      };
      const roomKeys = async () => [...new Set([...(await scanRooms('lt:ws:*')), ...(await scanRooms('lt:*'))])].filter((key) => /^lt:(?:ws:)?[A-Z0-9]{4,16}$/.test(key));
      if (action === 'rooms.list') {
        const rows = [];
        for (const key of await roomKeys()) {
          const room = await U.getJson(key); if (!room) continue;
          rows.push({ code: key.replace(/^lt:(?:ws:)?/, ''), transport: room.transport || 'rest', visibility: room.visibility === 'public' ? 'public' : 'private', host: String(room.host || ''), hostName: String(room.hostName || room.host || 'Host'), roomName: String(room.roomName || ''), bannerUrl: String(room.bannerUrl || ''), wallpaperUrl: String(room.wallpaperUrl || ''), members: Array.isArray(room.members) ? room.members.length : 1, createdAt: Number(room.createdAt) || 0 });
        }
        rows.sort((a,b)=>b.createdAt-a.createdAt);
        return U.send(res, 200, { ok: true, rooms: rows, closedAll: (await cmd('GET','listen:rooms:closed')) === '1' });
      }
      if (action === 'rooms.closeAll') {
        let deleted = 0; for (const key of await roomKeys()) deleted += Number(await cmd('DEL', key)) || 0;
        await cmd('SET','listen:rooms:closed','1'); await log(`menutup semua ruang dengar bersama (${deleted} room)`);
        return U.send(res, 200, { ok: true, deleted, closedAll: true });
      }
      if (action === 'rooms.openAll') {
        await cmd('DEL','listen:rooms:closed'); await log('membuka kembali pembuatan ruang dengar bersama');
        return U.send(res, 200, { ok: true, closedAll: false });
      }
      const code = String(b.code || '').toUpperCase();
      if (!/^[A-Z0-9]{4,16}$/.test(code)) return U.fail(res, 400, 'Kode room tidak valid.');
      const candidates = [`lt:ws:${code}`, `lt:${code}`]; let key = null; let room = null;
      for (const candidate of candidates) { const item = await U.getJson(candidate); if (item) { key = candidate; room = item; break; } }
      if (!room) return U.fail(res, 404, 'Room tidak ditemukan.');
      if (action === 'rooms.close') {
        await cmd('DEL', key); await log(`owner menutup room ${code}`);
        return U.send(res, 200, { ok: true, code });
      }
      const roomName = typeof b.roomName === 'string' ? b.roomName.trim().slice(0,60) : String(room.roomName || '');
      const hostName = typeof b.hostName === 'string' ? b.hostName.trim().slice(0,80) : String(room.hostName || 'Host');
      const visibility = b.visibility === 'public' ? 'public' : b.visibility === 'private' ? 'private' : room.visibility;
      const imageUrls = {};
      for (const field of ['bannerUrl', 'wallpaperUrl']) {
        if (!Object.prototype.hasOwnProperty.call(b, field)) continue;
        if (typeof b[field] !== 'string') return U.fail(res, 400, `${field} harus berupa URL HTTPS maksimal 500 karakter.`);
        const value = b[field].trim();
        const label = field === 'bannerUrl' ? 'Banner' : 'Wallpaper';
        if (value && (!/^https:\/\//i.test(value) || value.length > 500)) return U.fail(res, 400, `${label} harus URL HTTPS maksimal 500 karakter.`);
        imageUrls[field] = value;
      }
      Object.assign(room, imageUrls);
      room.bannerUrl = String(room.bannerUrl || ''); room.wallpaperUrl = String(room.wallpaperUrl || '');
      room.roomName = roomName; room.hostName = hostName;
      if (visibility) room.visibility = visibility;
      await U.setJson(key, room, 6 * 3600); await log(`owner mengubah pengaturan room ${code}`);
      return U.send(res, 200, { ok: true, room: { code, roomName, hostName, visibility: room.visibility || 'private', bannerUrl: room.bannerUrl, wallpaperUrl: room.wallpaperUrl } });
    }

    if (action === 'chat.retention.get') {
      const settings = (await U.getJson('chat:retention')) || { globalMinutes: 1440, dmMinutes: 1440 };
      return U.send(res, 200, { ok: true, settings });
    }
    if (action === 'chat.retention.set') {
      const globalMinutes = Math.max(1, Math.min(43200, Math.floor(Number(b.globalMinutes) || 1440)));
      const dmMinutes = Math.max(1, Math.min(43200, Math.floor(Number(b.dmMinutes) || 1440)));
      const settings = { globalMinutes, dmMinutes };
      await U.setJson('chat:retention', settings);
      await log(`retensi chat diatur: global ${globalMinutes} menit, DM ${dmMinutes} menit`);
      return U.send(res, 200, { ok: true, settings });
    }
    if (action === 'chat.clearall') {
      await cmd('DEL', 'chat:global', 'chat:pinned:global');
      await log('semua pesan Global Chat dibersihkan oleh owner');
      return U.send(res, 200, { ok: true });
    }
    if (action === 'bot.settings.get') {
      return U.send(res, 200, { ok: true, settings: await BotSettings.getSettings() });
    }
    if (action === 'bot.settings.set') {
      const result = await BotSettings.saveSettings(b.settings);
      if (!result.ok) return U.fail(res, 400, result.error);
      await log('pengaturan moderasi bot diperbarui');
      return U.send(res, 200, { ok: true, settings: result.settings });
    }

    if (action === 'user.create') {
      const username = U.lower(b.username);
      const role = String(b.role || 'user');
      if (!/^[a-z0-9_.]{3,20}$/.test(username)) return U.fail(res, 400, 'Username 3-20 karakter: huruf kecil, angka, titik, atau garis bawah.');
      if (!['user', 'staff', 'admin', 'owner'].includes(role)) return U.fail(res, 400, 'Role tidak valid.');
      if (role === 'user' && U.hasReservedWord(username)) return U.fail(res, 400, 'Username user biasa tidak boleh memakai kata Owner/Admin/Staff.');
      if (await U.getUser(username)) return U.fail(res, 409, 'Username sudah dipakai.');
      const password = tempPassword(); const { salt, hash } = U.hashPassword(password);
      const user = {
        username, nameLower: username, salt, passHash: hash, displayName: username, bio: '', avatarUrl: '', bannerUrl: '', bannerType: 'image',
        email: '', emailVerified: false, role, title: null, joinedAt: Date.now(), ips: [], lastIp: '', lastSeen: 0,
        warns: 0, mutedUntil: 0, banned: false, mustChange: true, ver: 0,
      };
      await U.saveUser(user); await cmd('SADD', 'users', username); await log(`buat akun role ${role} @${username}`);
      return U.send(res, 200, { ok: true, username, role, tempPassword: password, mustChange: true });
    }

    switch (action) {
      case 'feedback.list': {
        const ids = await cmd('ZREVRANGE', 'feedback:all', '0', '99');
        const feedback = [];
        for (const id of ids) { const item = await U.getJson(`feedback:${id}`); if (item) feedback.push(item); }
        return U.send(res, 200, { ok: true, feedback });
      }
      case 'feedback.update': {
        const id = typeof b.id === 'string' ? b.id : '';
        const status = String(b.status || '');
        if (!/^[0-9a-f-]{36}$/i.test(id) || !['new', 'processing', 'done'].includes(status)) return U.fail(res, 400, 'Laporan atau status tidak valid.');
        const item = await U.getJson(`feedback:${id}`);
        if (!item) return U.fail(res, 404, 'Laporan tidak ditemukan.');
        item.status = status; item.updatedAt = Date.now(); item.updatedBy = actorName;
        await U.setJson(`feedback:${id}`, item); await log(`laporan komunitas ${id} diubah ke ${status}`);
        return U.send(res, 200, { ok: true, feedback: item });
      }
      case 'feedback.archive': {
        const id = typeof b.id === 'string' ? b.id : '';
        if (!/^[0-9a-f-]{36}$/i.test(id)) return U.fail(res, 400, 'Laporan tidak valid.');
        const item = await U.getJson(`feedback:${id}`);
        if (!item) return U.fail(res, 404, 'Laporan tidak ditemukan.');
        item.status = 'archived'; item.updatedAt = Date.now(); item.updatedBy = actorName;
        await U.setJson(`feedback:${id}`, item); await log(`arsipkan laporan komunitas ${id}`);
        return U.send(res, 200, { ok: true, feedback: item });
      }
      case 'stats': {
        const users = await cmd('SCARD', 'users'); const bannedIps = await cmd('SCARD', 'bannedips');
        const archivedUsers = await cmd('SCARD', 'users:archived');
        const audit = (await cmd('LRANGE', 'auditlog', '0', '19')).map((x) => JSON.parse(x));
        return U.send(res, 200, { ok: true, users, archivedUsers, bannedIps, audit, level });
      }
      case 'users.search': {
        const q = U.lower(req.query.q || b.q || '');
        const status = String(req.query.status || b.status || 'active');
        if (!['active', 'archived'].includes(status)) return U.fail(res, 400, 'Filter pengguna tidak valid.');
        if (status === 'archived' && level < 3) return U.fail(res, 403, 'Hanya owner yang dapat melihat pengguna arsip.');
        const indexKey = status === 'archived' ? 'users:archived' : 'users';
        const all = await cmd('SMEMBERS', indexKey);
        const allowed = new Set(all);
        let names = all.filter((n) => !q || n.includes(q));
        if (q && /^[0-9a-f:.]+$/.test(q) && level >= 2) { // cari lewat IP, tetap hormati filter aktif/arsip
          const viaIp = await cmd('SMEMBERS', `ip:${req.query.q || b.q}`);
          names = Array.from(new Set([...names, ...viaIp.filter((n) => allowed.has(n))]));
        }
        names = names.sort().slice(0, 80);
        const out = [];
        for (const n of names) { const u = await U.getUser(n); if (u) out.push(adminView(u, level >= 2)); }
        return U.send(res, 200, { ok: true, users: out });
      }
      case 'user.get': {
        const u = await U.getUser(req.query.username || b.username);
        if (!u) return U.fail(res, 404, 'Pengguna tidak ditemukan');
        return U.send(res, 200, { ok: true, user: adminView(u, level >= 2) });
      }
      case 'ip.lookup': return U.send(res, 200, { ok: true, usernames: await cmd('SMEMBERS', `ip:${req.query.ip || b.ip}`), banned: (await cmd('SISMEMBER', 'bannedips', req.query.ip || b.ip)) === 1 });
      case 'ip.list': return U.send(res, 200, { ok: true, ips: await cmd('SMEMBERS', 'bannedips') });
      case 'ip.ban': { const ip = String(b.ip || '').trim(); if (!ip) return U.fail(res, 400, 'IP kosong'); await cmd('SADD', 'bannedips', ip); await log(`ban IP ${ip} ${b.reason || ''}`); return U.send(res, 200, { ok: true }); }
      case 'ip.unban': { const ip = String(b.ip || '').trim(); await cmd('SREM', 'bannedips', ip); await log(`unban IP ${ip}`); return U.send(res, 200, { ok: true }); }
      case 'chat.delete': {
        await require('./chat').maintainChatRoom('global');
        const raw = await cmd('LRANGE', 'chat:global', '-300', '-1');
        const hit = raw.find((x) => { try { return JSON.parse(x).id === Number(b.id); } catch { return false; } });
        if (hit) await cmd('LREM', 'chat:global', '1', hit);
        await log(`hapus pesan #${b.id}`); return U.send(res, 200, { ok: true });
      }
      case 'status.get': {
        return U.send(res, 200, { ok: true, status: (await U.getJson('app:status')) || {}, changelog: (await U.getJson('app:changelog')) || { md: '' } });
      }
      case 'maintenance.set': {
        const message = String(b.message || ''); const eta = String(b.eta || '');
        if (message.length > 800 || eta.length > 120) return U.fail(res, 400, 'Deskripsi maintenance maksimal 800 karakter dan perkiraan selesai maksimal 120 karakter.');
        const st = (await U.getJson('app:status')) || {};
        st.maintenance = { enabled: !!b.enabled, message, eta };
        await U.setJson('app:status', st);
        const item = { id: crypto.randomUUID(), ...st.maintenance, createdAt: Date.now(), createdBy: actorName, updatedAt: 0, updatedBy: '', deletedAt: 0, deletedBy: '' };
        const history = await readHistory(MAINTENANCE_HISTORY_KEY);
        history.unshift(item);
        await U.setJson(MAINTENANCE_HISTORY_KEY, history.slice(0, HISTORY_LIMIT));
        await log(`maintenance ${st.maintenance.enabled ? 'ON' : 'OFF'}`); return U.send(res, 200, { ok: true, snapshot: item });
      }
      case 'maintenance.history.list': {
        const history = await readHistory(MAINTENANCE_HISTORY_KEY);
        return U.send(res, 200, { ok: true, snapshots: history.filter((item) => item && !item.deletedAt) });
      }
      case 'maintenance.history.update': {
        if (!validHistoryId(b.id)) return U.fail(res, 400, 'ID snapshot maintenance tidak valid.');
        const message = typeof b.message === 'string' ? b.message : '';
        const eta = typeof b.eta === 'string' ? b.eta : '';
        if (message.length > 800 || eta.length > 120) return U.fail(res, 400, 'Deskripsi maksimal 800 karakter dan perkiraan selesai maksimal 120 karakter.');
        const history = await readHistory(MAINTENANCE_HISTORY_KEY);
        const item = findHistoryItem(history, b.id);
        if (!item) return U.fail(res, 404, 'Snapshot maintenance tidak ditemukan.');
        item.enabled = !!b.enabled; item.message = message; item.eta = eta; item.updatedAt = Date.now(); item.updatedBy = actorName;
        await U.setJson(MAINTENANCE_HISTORY_KEY, history);
        await log(`snapshot maintenance ${item.id} diperbarui`);
        return U.send(res, 200, { ok: true, snapshot: item });
      }
      case 'maintenance.history.delete': {
        if (!validHistoryId(b.id)) return U.fail(res, 400, 'ID snapshot maintenance tidak valid.');
        const history = await readHistory(MAINTENANCE_HISTORY_KEY);
        const item = findHistoryItem(history, b.id);
        if (!item) return U.fail(res, 404, 'Snapshot maintenance tidak ditemukan.');
        item.deletedAt = Date.now(); item.deletedBy = actorName;
        await U.setJson(MAINTENANCE_HISTORY_KEY, history);
        await log(`snapshot maintenance ${item.id} dihapus`);
        return U.send(res, 200, { ok: true });
      }
      case 'maintenance.history.apply': {
        if (!validHistoryId(b.id)) return U.fail(res, 400, 'ID snapshot maintenance tidak valid.');
        const history = await readHistory(MAINTENANCE_HISTORY_KEY);
        const item = findHistoryItem(history, b.id);
        if (!item) return U.fail(res, 404, 'Snapshot maintenance tidak ditemukan.');
        const st = (await U.getJson('app:status')) || {};
        st.maintenance = { enabled: !!item.enabled, message: String(item.message || ''), eta: String(item.eta || '') };
        await U.setJson('app:status', st);
        await log(`snapshot maintenance ${item.id} diterapkan`);
        return U.send(res, 200, { ok: true, maintenance: st.maintenance });
      }
      case 'update.set': {
        const st = (await U.getJson('app:status')) || {};
        const enabled = !!b.enabled;
        const versionName = String(b.versionName || '').trim().slice(0, 30);
        const versionCode = Number(b.versionCode) || 0;
        const downloadUrl = String(b.downloadUrl || '').trim().slice(0, 500);
        if (enabled && (!versionName || !Number.isSafeInteger(versionCode) || versionCode < 1 || !/^https:\/\//i.test(downloadUrl))) {
          return U.fail(res, 400, 'Update aktif memerlukan nama versi, version code positif, dan URL APK HTTPS.');
        }
        st.update = { enabled, versionName, versionCode, message: String(b.message || '').slice(0, 1500), downloadUrl };
        await U.setJson('app:status', st); await log(`update ${st.update.versionName} ${st.update.enabled ? 'ON' : 'OFF'}`); return U.send(res, 200, { ok: true });
      }
      case 'changelog.set': {
        const md = String(b.md || '').slice(0, 60000);
        await U.setJson('app:changelog', { md, updatedAt: Date.now() });
        const gh = await commitChangelogToGithub(md); await log('changelog diperbarui'); return U.send(res, 200, { ok: true, github: gh });
      }
    }

    // ---- aksi terhadap user ----
    const u = await U.getUser(b.username);
    if (!u) return U.fail(res, 404, 'Pengguna tidak ditemukan');
    const ownerRoleOverride = level >= 3 && action === 'user.setRole';
    if ((RANK[u.role] || 0) >= level && u.username !== actorName && !ownerRoleOverride) {
      return U.fail(res, 403, 'Tidak bisa mengubah akun dengan role setara atau lebih tinggi.');
    }
    switch (action) {
      case 'user.mute': u.mutedUntil = Date.now() + Math.max(1, Number(b.minutes) || 60) * 60000; break;
      case 'user.unmute': u.mutedUntil = 0; break;
      case 'user.warns.reset': u.warns = 0; break;
      case 'user.ban': u.banned = true; break;
      case 'user.unban': u.banned = false; break;
      case 'user.setRole': if (!['user', 'staff', 'admin', 'owner'].includes(b.role)) return U.fail(res, 400, 'Role tidak valid'); u.role = b.role; break;
      case 'user.setTitle': u.title = b.title && b.title.text ? { text: String(b.title.text).slice(0, 16), color: /^#[0-9a-fA-F]{6}$/.test(b.title.color) ? b.title.color : '#9ED7CB', shape: b.title.shape || 'pill', icon: b.title.icon || 'none' } : null; break;
      case 'user.resetPassword': {
        if (u.deletedAt) return U.fail(res, 404, 'Pengguna sudah dihapus.');
        const tmp = tempPassword(); const { salt, hash } = U.hashPassword(tmp);
        u.salt = salt; u.passHash = hash; u.mustChange = true; u.ver = (u.ver || 0) + 1;
        await U.saveUser(u); await log(`reset password @${u.username}`);
        return U.send(res, 200, { ok: true, tempPassword: tmp, note: 'Berikan password sementara ini ke pemilik akun. Mereka wajib menggantinya setelah login.' });
      }
      case 'user.archive': {
        if (u.deletedAt) return U.fail(res, 409, 'Akun sudah dihapus.');
        if (u.username === actorName) return U.fail(res, 400, 'Akun yang sedang dipakai tidak dapat mengarsipkan dirinya sendiri.');
        if (!u.archivedAt) {
          u.archivedAt = Date.now(); u.archivedBy = actorName; u.ver = (u.ver || 0) + 1;
          await U.saveUser(u); await cmd('SREM', 'users', u.username); await cmd('SADD', 'users:archived', u.username);
          await log(`arsipkan akun @${u.username}`);
        }
        return U.send(res, 200, { ok: true, archived: true });
      }
      case 'user.unarchive': {
        if (u.deletedAt) return U.fail(res, 409, 'Akun sudah dihapus dan tidak dapat dipulihkan.');
        if (u.archivedAt) {
          delete u.archivedAt; delete u.archivedBy; u.ver = (u.ver || 0) + 1;
          await U.saveUser(u); await cmd('SREM', 'users:archived', u.username); await cmd('SADD', 'users', u.username);
          await log(`pulihkan akun @${u.username} dari arsip`);
        }
        return U.send(res, 200, { ok: true, archived: false });
      }
      case 'user.delete': {
        if (u.deletedAt) return U.fail(res, 409, 'Akun sudah dihapus.');
        if (u.username === actorName) return U.fail(res, 400, 'Akun yang sedang dipakai tidak dapat menghapus dirinya sendiri.');
        const now = Date.now();
        const email = U.lower(u.email);
        if (email && await cmd('GET', `email:${email}`) === u.username) await cmd('DEL', `email:${email}`);
        for (const entry of (u.ips || [])) if (entry && entry.ip) await cmd('SREM', `ip:${entry.ip}`, u.username);
        if (u.lastIp) await cmd('SREM', `ip:${u.lastIp}`, u.username);
        const { salt, hash } = U.hashPassword(crypto.randomBytes(32).toString('hex'));
        Object.assign(u, {
          displayName: 'Akun dihapus', bio: '', avatarUrl: '', avatarUrls: [], bannerUrl: '', bannerUrls: [], bannerType: 'image',
          email: '', emailVerified: false, role: 'user', title: null, ips: [], lastIp: '', lastSeen: 0,
          warns: 0, mutedUntil: 0, banned: false, mustChange: true, salt, passHash: hash,
          archivedAt: now, deletedAt: now, deletedBy: actorName, ver: (u.ver || 0) + 1,
        });
        await U.saveUser(u); await cmd('SREM', 'users', u.username); await cmd('SREM', 'users:archived', u.username); await cmd('SADD', 'users:deleted', u.username);
        await log(`hapus akses dan data profil @${u.username}`);
        return U.send(res, 200, { ok: true, deleted: true, note: 'Akun tidak dapat login lagi; data profil dan email telah dihapus.' });
      }
    }
    await U.saveUser(u); await log(`${action} @${u.username}`);
    return U.send(res, 200, { ok: true, user: adminView(u, level >= 2) });
  } catch (e) {
    console.error('[owner-api] request failed:', e instanceof Error ? e.name : 'UnknownError');
    return U.fail(res, 500, 'Terjadi kesalahan server. Silakan coba lagi.');
  }
};
