const U = require('../lib/util');
const { cmd } = U;
const crypto = require('crypto');
const ALPHA = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
const TTL = 6 * 3600;
const WS_CODE = /^[A-Z0-9]{4,16}$/;
const MEMBER_LEASE = 2 * 60 * 1000;
const roomKey = (code) => `lt:${code}`;
const wsRoomKey = (code) => `lt:ws:${code}`;
const newCode = () => Array.from(crypto.randomBytes(6)).map((b) => ALPHA[b % ALPHA.length]).join('');
function cleanState(s) {
  s = s || {};
  return {
    videoId: String(s.videoId || '').slice(0, 20), title: String(s.title || '').slice(0, 140), artist: String(s.artist || '').slice(0, 140),
    thumb: String(s.thumb || '').slice(0, 300), positionMs: Math.max(0, Number(s.positionMs) || 0), playing: !!s.playing, durationMs: Math.max(0, Number(s.durationMs) || 0),
  };
}
function cleanMembers(room) {
  const now = Date.now();
  room.members = Array.isArray(room.members) ? room.members : [room.host].filter(Boolean);
  room.memberInfo = room.memberInfo || {}; room.memberHeartbeat = room.memberHeartbeat || {};
  const hostHeartbeat = Number(room.memberHeartbeat[room.host] || 0);
  const hostIsLive = !hostHeartbeat || now - hostHeartbeat < MEMBER_LEASE;
  room.members = [...new Set(room.members.filter((name) => (name === room.host ? hostIsLive : !room.memberHeartbeat[name] || now - Number(room.memberHeartbeat[name]) < MEMBER_LEASE)))];
  if (room.host && hostIsLive && !room.members.includes(room.host)) room.members.unshift(room.host);
  for (const name of Object.keys(room.memberInfo)) if (!room.members.includes(name)) delete room.memberInfo[name];
  for (const name of Object.keys(room.memberHeartbeat)) if (!room.members.includes(name)) delete room.memberHeartbeat[name];
}
function webSocketRoomIsStale(room, now = Date.now()) {
  if (!room || room.transport !== 'websocket') return false;
  const heartbeat = Number(room.lastHeartbeatAt || room.memberHeartbeat?.[room.host] || room.createdAt || 0);
  return heartbeat > 0 && now - heartbeat >= MEMBER_LEASE;
}
function memberList(room) {
  cleanMembers(room);
  return room.members.map((username) => { const info = room.memberInfo[username] || {}; return { username, displayName: String(info.displayName || username).slice(0, 80), avatarUrl: String(info.avatarUrl || '').slice(0, 500) }; });
}
function joinMember(room, user) {
  room.members = Array.isArray(room.members) ? room.members : [room.host].filter(Boolean);
  room.memberInfo = room.memberInfo || {}; room.memberHeartbeat = room.memberHeartbeat || {};
  if (!room.members.includes(user.username)) room.members.push(user.username);
  room.memberInfo[user.username] = { displayName: String(user.displayName || user.username).slice(0, 80), avatarUrl: String(user.avatarUrl || '').slice(0, 500) };
  room.memberHeartbeat[user.username] = Date.now();
}
async function publicRooms(requestedTransport = 'websocket') {
  const found = [];
  let cursor = '0';
  do {
    const result = await cmd('SCAN', cursor, 'MATCH', 'lt:*', 'COUNT', '200');
    cursor = String(result[0]);
    const keys = Array.isArray(result[1]) ? result[1] : [];
    for (const key of keys) {
      const redisKey = String(key);
      const isWebSocketRoom = redisKey.startsWith('lt:ws:');
      const transport = isWebSocketRoom ? 'websocket' : 'rest';
      if (requestedTransport !== 'all' && requestedTransport !== transport) continue;
      if (!isWebSocketRoom && !redisKey.startsWith('lt:')) continue;
      const code = redisKey.slice(isWebSocketRoom ? 'lt:ws:'.length : 'lt:'.length).toUpperCase();
      if (!WS_CODE.test(code)) continue;
      const room = await U.getJson(isWebSocketRoom ? wsRoomKey(code) : roomKey(code));
      if (!room) continue;
      if (isWebSocketRoom && room.transport !== 'websocket') continue;
      if (isWebSocketRoom && webSocketRoomIsStale(room)) { await cmd('DEL', wsRoomKey(code)); continue; }
      if (!isWebSocketRoom && room.transport === 'websocket') continue;
      if (room.visibility !== 'public') continue;
      if (!isWebSocketRoom) {
        cleanMembers(room);
        if (room.host && !room.members.includes(room.host)) { await cmd('DEL', roomKey(code)); continue; }
        await U.setJson(roomKey(code), room, TTL);
      }
      found.push({
        code,
        transport,
        hostName: String(room.hostName || 'Host').slice(0, 80),
        roomName: String(room.roomName || '').slice(0, 60),
        bannerUrl: String(room.bannerUrl || '').slice(0, 500),
        wallpaperUrl: String(room.wallpaperUrl || '').slice(0, 500),
        members: Array.isArray(room.members) ? room.members.length : 1,
        createdAt: Math.max(0, Number(room.createdAt) || 0),
      });
    }
  } while (cursor !== '0');
  found.sort((a, b) => b.createdAt - a.createdAt);
  return found.slice(0, 100);
}
module.exports = async (req, res) => {
  try {
    const origin = String(req.headers.origin || '');
    const allowedOrigin = 'https://sanzxcy-music.xo.je';
    if (origin === allowedOrigin) {
      res.setHeader('Access-Control-Allow-Origin', allowedOrigin);
      res.setHeader('Vary', 'Origin');
    }
    if (req.method === 'OPTIONS') {
      if (origin !== allowedOrigin) return U.fail(res, 403, 'Origin tidak diizinkan.');
      res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
      res.setHeader('Access-Control-Max-Age', '600');
      res.statusCode = 204;
      return res.end();
    }
    const action = req.query.action || U.body(req).action;
    // Public discovery deliberately exposes only the explicitly public room summary.
    if (req.method === 'GET' && action === 'list') {
      if (await cmd('GET', 'listen:rooms:closed') === '1') return U.send(res, 200, { ok: true, rooms: [], closedAll: true });
      const transport = String(req.query.transport || 'websocket').toLowerCase();
      if (!['websocket', 'rest', 'all'].includes(transport)) return U.fail(res, 400, 'Filter transport tidak valid.');
      return U.send(res, 200, { ok: true, rooms: await publicRooms(transport) });
    }
    // An invite code is a bearer token; expose only minimal room metadata, never playback state.
    if (req.method === 'GET' && action === 'invite') {
      const code = String(req.query.code || '').toUpperCase();
      if (!WS_CODE.test(code)) return U.fail(res, 400, 'Kode undangan tidak valid.');
      const room = await U.getJson(wsRoomKey(code)) || await U.getJson(roomKey(code));
      if (!room) return U.fail(res, 404, 'Ruangan tidak ditemukan atau sudah ditutup.');
      if (webSocketRoomIsStale(room)) { await cmd('DEL', wsRoomKey(code)); return U.fail(res, 404, 'Ruangan tidak ditemukan atau sudah ditutup.'); }
      return U.send(res, 200, { ok: true, code, hostName: String(room.hostName || 'Host').slice(0, 80), members: Array.isArray(room.members) ? room.members.length : 1 });
    }
    const a = await U.auth(req, res); if (!a) return;
    const me = a.user.username;
    if (req.method === 'GET') {
      const code = String(req.query.code || '').toUpperCase();
      if (!code) return U.fail(res, 400, 'Kode ruangan wajib diisi.');
      const room = await U.getJson(wsRoomKey(code)) || await U.getJson(roomKey(code));
      if (!room) return U.fail(res, 404, 'Ruangan tidak ditemukan atau sudah ditutup.');
      if (webSocketRoomIsStale(room)) { await cmd('DEL', wsRoomKey(code)); return U.fail(res, 404, 'Ruangan tidak ditemukan atau sudah ditutup.'); }
      if (room.transport === 'websocket') {
        if (room.visibility !== 'public' && room.host !== me && a.user.role !== 'owner' && !(Array.isArray(room.members) && room.members.includes(me))) return U.fail(res, 404, 'Ruangan tidak ditemukan atau sudah ditutup.');
        // WS rooms are discovered/joined via WebSocket; never expose playback state here.
        return U.send(res, 200, {
          ok: true, transport: 'websocket', visibility: room.visibility || 'private', hostName: room.hostName,
          members: Array.isArray(room.members) ? room.members.length : 1, createdAt: room.createdAt, roomName: String(room.roomName || ''), bannerUrl: String(room.bannerUrl || ''), wallpaperUrl: String(room.wallpaperUrl || ''),
        });
      }
      const hostHeartbeat = Number(room.memberHeartbeat?.[room.host] || 0);
      if (room.host && hostHeartbeat && Date.now() - hostHeartbeat >= MEMBER_LEASE) {
        await cmd('DEL', roomKey(code));
        return U.fail(res, 404, 'Ruangan ditutup karena host sudah tidak aktif.');
      }
      if ((room.visibility || 'private') === 'private' && room.host !== me && a.user.role !== 'owner' && !(Array.isArray(room.members) && room.members.includes(me))) return U.fail(res, 404, 'Ruangan tidak ditemukan atau sudah ditutup.');
      cleanMembers(room);
      if (room.members.includes(me)) {
        room.memberHeartbeat = room.memberHeartbeat || {};
        room.memberHeartbeat[me] = Date.now();
      }
      await U.setJson(roomKey(code), room, TTL);
      return U.send(res, 200, { ok: true, transport: 'rest', visibility: room.visibility || 'private', host: room.host, hostName: room.hostName, roomName: String(room.roomName || ''), bannerUrl: String(room.bannerUrl || ''), wallpaperUrl: String(room.wallpaperUrl || ''), members: room.members.length, memberList: memberList(room), state: room.state, serverTs: Date.now() });
    }
    if (req.method !== 'POST') return U.fail(res, 405, 'Method tidak didukung');
    const b = U.body(req);
    if (action === 'create') {
      if (await cmd('GET', 'listen:rooms:closed') === '1') return U.fail(res, 403, 'Pembuatan ruang dengar bersama sedang ditutup oleh Owner.');
      const code = newCode();
      const room = { host: me, hostName: a.user.displayName || me, roomName: String(b.roomName || '').trim().slice(0, 60), bannerUrl: '', wallpaperUrl: '', visibility: b.visibility === 'public' ? 'public' : 'private', members: [], memberInfo: {}, memberHeartbeat: {}, entryRequests: {}, songRequests: {}, state: { ...cleanState(b.state), serverTs: Date.now() }, createdAt: Date.now() };
      joinMember(room, a.user);
      await U.setJson(roomKey(code), room, TTL);
      return U.send(res, 200, { ok: true, code, visibility: room.visibility });
    }
    const code = String(b.code || '').toUpperCase();
    if (!code) return U.fail(res, 400, 'Kode ruangan wajib diisi.');
    if (action === 'metadata') {
      if (!WS_CODE.test(code)) return U.fail(res, 400, 'Kode ruangan tidak valid.');
      const existing = await U.getJson(wsRoomKey(code));
      if (await cmd('GET', 'listen:rooms:closed') === '1') return U.fail(res, 403, 'Pembuatan ruang dengar bersama sedang ditutup oleh Owner.');
      if (existing && existing.host !== me) return U.fail(res, 403, 'Hanya host yang bisa mengatur visibilitas ruangan.');
      const room = existing && existing.transport === 'websocket'
        ? existing
        : { host: me, hostName: a.user.displayName || me, roomName: '', bannerUrl: '', wallpaperUrl: '', members: [me], createdAt: Date.now(), transport: 'websocket' };
      room.transport = 'websocket';
      room.host = me;
      room.hostName = a.user.displayName || room.hostName || me;
      room.bannerUrl = String(room.bannerUrl || '');
      room.wallpaperUrl = String(room.wallpaperUrl || '');
      room.members = Array.isArray(room.members) ? room.members : [me];
      if (!room.members.includes(me)) room.members.unshift(me);
      room.memberHeartbeat = room.memberHeartbeat || {};
      room.memberHeartbeat[me] = Date.now();
      room.lastHeartbeatAt = Date.now();
      room.visibility = b.visibility === 'public' ? 'public' : 'private';
      // Metadata only: this separate REST service does not drive WS playback.
      room.state = cleanState({});
      await U.setJson(wsRoomKey(code), room, TTL);
      return U.send(res, 200, { ok: true, code, visibility: room.visibility, bannerUrl: room.bannerUrl, wallpaperUrl: room.wallpaperUrl });
    }
    const metadataKey = ['close', 'heartbeat'].includes(action) ? wsRoomKey(code) : roomKey(code);
    const room = ['close', 'heartbeat', 'join', 'push', 'settings', 'visibility', 'members', 'entryRequests', 'entryDecision', 'songRequest', 'songRequestStatus', 'songRequests', 'songDecision', 'transfer'].includes(action)
      ? await U.getJson(wsRoomKey(code)) || await U.getJson(roomKey(code))
      : await U.getJson(metadataKey);
    if (!room) return U.fail(res, 404, 'Ruangan tidak ditemukan atau sudah ditutup.');
    if (room.transport !== 'websocket' && room.host) {
      const hostHeartbeat = Number(room.memberHeartbeat?.[room.host] || 0);
      if (hostHeartbeat && Date.now() - hostHeartbeat >= MEMBER_LEASE) {
        await cmd('DEL', roomKey(code));
        return U.fail(res, 404, 'Ruangan ditutup karena host sudah tidak aktif.');
      }
    }
    if (action === 'settings') {
      if (room.transport === 'websocket' && room.host !== me) return U.fail(res, 403, 'Hanya host yang dapat mengubah tampilan room.');
      if (room.transport !== 'websocket' && room.host !== me) return U.fail(res, 403, 'Hanya host yang dapat mengubah tampilan room.');
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
      if (Object.prototype.hasOwnProperty.call(b, 'roomName')) room.roomName = String(b.roomName || '').trim().slice(0,60);
      room.bannerUrl = String(room.bannerUrl || '');
      room.wallpaperUrl = String(room.wallpaperUrl || '');
      const updatedRoomName = String(room.roomName || '');
      await U.setJson(room.transport === 'websocket' ? wsRoomKey(code) : roomKey(code), room, TTL);
      return U.send(res, 200, { ok: true, roomName: updatedRoomName, bannerUrl: room.bannerUrl, wallpaperUrl: room.wallpaperUrl });
    }
    if (action === 'visibility') {
      if (room.host !== me) return U.fail(res, 403, 'Hanya host yang bisa mengatur visibilitas ruangan.');
      room.visibility = b.visibility === 'public' ? 'public' : 'private';
      await U.setJson(room.transport === 'websocket' ? wsRoomKey(code) : roomKey(code), room, TTL);
      return U.send(res, 200, { ok: true, visibility: room.visibility });
    }
    if (action === 'close') {
      if (room.host !== me) return U.fail(res, 403, 'Hanya host yang bisa menutup ruangan.');
      await cmd('DEL', room.transport === 'websocket' ? wsRoomKey(code) : roomKey(code));
      return U.send(res, 200, { ok: true, closed: true });
    }
    if (action === 'heartbeat') {
      if (room.transport !== 'websocket') return U.fail(res, 409, 'Heartbeat ini hanya untuk ruangan WebSocket.');
      if (room.host !== me) return U.fail(res, 403, 'Hanya host yang dapat memperbarui heartbeat ruangan.');
      const now = Date.now();
      room.lastHeartbeatAt = now;
      room.memberHeartbeat = room.memberHeartbeat || {};
      room.memberHeartbeat[me] = now;
      await U.setJson(wsRoomKey(code), room, TTL);
      return U.send(res, 200, { ok: true, lastHeartbeatAt: now });
    }
    if (room.transport === 'websocket' && ['join', 'push', 'members', 'entryRequests', 'entryDecision', 'songRequest', 'songRequestStatus', 'songRequests', 'songDecision'].includes(action)) {
      return U.fail(res, 409, 'Ruangan ini dikelola melalui WebSocket; gunakan alur WebSocket untuk bergabung dan menyinkronkan pemutaran.');
    }
    if (action === 'join') {
      cleanMembers(room); room.entryRequests = room.entryRequests || {};
      if ((room.visibility || 'private') === 'private' && room.host !== me && a.user.role !== 'owner' && !room.members.includes(me)) {
        const old = room.entryRequests[me];
        if (old && old.status === 'declined') return U.send(res, 200, { ok: true, status: 'rejected', rejection: { status: 'declined', message: 'Permintaan masuk ditolak oleh host.' } });
        room.entryRequests[me] = old && old.status === 'pending' ? old : { username: me, displayName: a.user.displayName || me, requestedAt: Date.now(), status: 'pending' };
        await U.setJson(roomKey(code), room, TTL); return U.send(res, 200, { ok: true, status: 'pending', request: room.entryRequests[me] });
      }
      joinMember(room, a.user);
      await U.setJson(roomKey(code), room, TTL);
      return U.send(res, 200, { ok: true, status: 'joined', visibility: room.visibility || 'private', host: room.host, hostName: room.hostName, roomName: String(room.roomName || ''), bannerUrl: String(room.bannerUrl || ''), wallpaperUrl: String(room.wallpaperUrl || ''), members: room.members.length, memberList: memberList(room), state: room.state, serverTs: Date.now() });
    }
    if (action === 'members') { cleanMembers(room); await U.setJson(roomKey(code), room, TTL); return U.send(res, 200, { ok: true, members: memberList(room), count: room.members.length }); }
    if (action === 'entryRequests') { if (room.host !== me) return U.fail(res, 403, 'Hanya host yang dapat melihat permintaan masuk.'); return U.send(res, 200, { ok: true, requests: Object.values(room.entryRequests || {}).filter((r) => r.status === 'pending') }); }
    if (action === 'entryDecision') {
      if (room.host !== me) return U.fail(res, 403, 'Hanya host yang dapat memutuskan permintaan masuk.');
      const user = String(b.username || ''), r = (room.entryRequests || {})[user]; if (!r || r.status !== 'pending') return U.fail(res, 404, 'Permintaan masuk tidak ditemukan.');
      r.status = b.accept === true ? 'accepted' : 'declined'; r.decidedAt = Date.now(); if (r.status === 'accepted') joinMember(room, { username: user, displayName: r.displayName });
      await U.setJson(roomKey(code), room, TTL); return U.send(res, 200, { ok: true, username: user, status: r.status, members: room.members.length, memberList: memberList(room) });
    }
    if (action === 'songRequest') {
      cleanMembers(room); if (!room.members.includes(me)) return U.fail(res, 403, 'Hanya anggota room yang dapat meminta lagu.');
      const videoId = String(b.videoId || '').trim(); if (!/^[A-Za-z0-9_-]{11}$/.test(videoId)) return U.fail(res, 400, 'Video ID YouTube tidak valid.');
      const id = crypto.randomBytes(8).toString('hex'); room.songRequests = room.songRequests || {};
      const requests = Object.values(room.songRequests);
      if (requests.length >= 100) return U.fail(res, 429, 'Batas permintaan lagu room telah tercapai.');
      if (requests.filter((request) => request.username === me && request.status === 'pending').length >= 3) return U.fail(res, 429, 'Kamu masih memiliki 3 permintaan lagu yang menunggu.');
      room.songRequests[id] = { id, username: me, displayName: a.user.displayName || me, videoId, title: String(b.title || '').slice(0, 140), artist: String(b.artist || '').slice(0, 140), requestedAt: Date.now(), status: 'pending' };
      await U.setJson(roomKey(code), room, TTL); return U.send(res, 200, { ok: true, request: room.songRequests[id] });
    }
    if (action === 'songRequests') {
      if (room.host !== me) return U.fail(res, 403, 'Hanya host yang dapat melihat permintaan lagu.');
      return U.send(res, 200, { ok: true, requests: Object.values(room.songRequests || {}).filter((r) => r.status === 'pending') });
    }
    if (action === 'songRequestStatus') {
      const request = (room.songRequests || {})[String(b.requestId || '')];
      if (!request || request.username !== me) return U.fail(res, 404, 'Permintaan lagu tidak ditemukan.');
      return U.send(res, 200, { ok: true, request: { id: request.id, status: request.status, decidedAt: request.decidedAt || 0 } });
    }
    if (action === 'songDecision') {
      if (room.host !== me) return U.fail(res, 403, 'Hanya host yang dapat memutuskan permintaan lagu.');
      const r = (room.songRequests || {})[String(b.requestId || '')]; if (!r || r.status !== 'pending') return U.fail(res, 404, 'Permintaan lagu tidak ditemukan.');
      r.status = b.accept === true ? 'accepted' : 'declined'; r.decidedAt = Date.now();
      if (r.status === 'accepted') room.state = { ...cleanState({ videoId: r.videoId, title: r.title, artist: r.artist }), serverTs: Date.now() };
      await U.setJson(roomKey(code), room, TTL); return U.send(res, 200, { ok: true, request: r, ...(r.status === 'accepted' ? { state: room.state } : {}) });
    }
    if (action === 'transfer') {
      if (room.host !== me) return U.fail(res, 403, 'Hanya host yang dapat memindahkan kepemilikan.');
      cleanMembers(room); const next = String(b.username || ''); if (next === me || !room.members.includes(next)) return U.fail(res, 400, 'Pemilik baru harus anggota room lain.');
      const previousHost = room.host;
      room.host = next;
      room.hostName = (room.memberInfo[next] || {}).displayName || next;
      room.memberHeartbeat = room.memberHeartbeat || {};
      room.memberHeartbeat[previousHost] = Date.now();
      room.memberHeartbeat[next] = Date.now();
      await U.setJson(room.transport === 'websocket' ? wsRoomKey(code) : roomKey(code), room, TTL);
      return U.send(res, 200, { ok: true, host: next, hostName: room.hostName, previousHost, memberList: memberList(room) });
    }
    if (action === 'push') {
      if (room.host !== me) return U.fail(res, 403, 'Hanya host yang bisa mengatur lagu.');
      room.memberHeartbeat = room.memberHeartbeat || {};
      room.memberHeartbeat[me] = Date.now();
      cleanMembers(room);
      room.state = { ...cleanState(b.state), serverTs: Date.now() };
      await U.setJson(roomKey(code), room, TTL);
      return U.send(res, 200, { ok: true, members: room.members.length });
    }
    if (action === 'leave') {
      if (room.host === me) await cmd('DEL', roomKey(code));
      else { room.members = (room.members || []).filter((m) => m !== me); if (room.memberInfo) delete room.memberInfo[me]; if (room.memberHeartbeat) delete room.memberHeartbeat[me]; await U.setJson(roomKey(code), room, TTL); }
      return U.send(res, 200, { ok: true, members: room.members.length, memberList: room.host === me ? [] : memberList(room) });
    }
    return U.fail(res, 400, 'Aksi tidak dikenal');
  } catch (e) { return U.fail(res, 500, 'Server error: ' + e.message); }
};
