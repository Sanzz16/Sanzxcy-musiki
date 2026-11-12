const test = require('node:test');
const assert = require('node:assert/strict');
const Module = require('node:module');
const originalLoad = Module._load;
const rooms = new Map();
const state = { authCalls: 0, user: { username: 'host', displayName: 'Host Name' } };
const fake = {
  cmd: async (...args) => {
    if (args[0] === 'SCAN') return ['0', [...rooms.keys()]];
    if (args[0] === 'GET') return null;
    if (args[0] === 'DEL') { rooms.delete(args[1]); return 1; }
    throw new Error(`unexpected command ${args.join(' ')}`);
  },
  getJson: async (key) => rooms.get(key) || null,
  setJson: async (key, value) => { rooms.set(key, structuredClone(value)); },
  auth: async (_req, res) => { state.authCalls++; if (state.authError) { res.status(401).setHeader('Content-Type', 'application/json').send(JSON.stringify({ ok: false })); return null; } return { user: state.user }; },
  body: (req) => req.body || {},
  send: (res, status, body) => { res.statusCode = status; res.body = body; return body; },
  fail: (res, status, error) => { res.statusCode = status; res.body = { ok: false, error }; return res.body; },
};
Module._load = function (request, parent, isMain) {
  if (request === '../lib/util' && parent?.filename.endsWith('/server/api/listen.js')) return fake;
  return originalLoad.call(this, request, parent, isMain);
};
const handler = require('../api/listen');
Module._load = originalLoad;
function request(method, query = {}, body = {}, user = 'host') {
  state.user = { username: user, displayName: `${user} display` };
  const req = { method, query, body, headers: {} };
  const res = { status(status) { this.statusCode = status; return this; }, setHeader() { return this; }, send(text) { this.body = JSON.parse(text); return this; } };
  return handler(req, res).then(() => res);
}
test('unauthenticated public index includes only public room summaries', async () => {
  rooms.clear(); state.authCalls = 0; state.authError = true;
  rooms.set('lt:ws:PUBLIC01', { transport: 'websocket', visibility: 'public', host: 'host', hostName: 'Host', members: ['host'], state: { title: 'secret track' }, roomName: 'WS room', bannerUrl: 'https://images.test/ws-banner.jpg', wallpaperUrl: 'https://images.test/ws-wallpaper.jpg', createdAt: 1, lastHeartbeatAt: Date.now() });
  rooms.set('lt:ws:PRIVATE1', { transport: 'websocket', visibility: 'private', host: 'host', hostName: 'Private host', members: ['host'], state: { title: 'private' }, createdAt: 2, lastHeartbeatAt: Date.now() });
  rooms.set('lt:REST001', { visibility: 'public', host: 'host', hostName: 'REST Host', members: ['host'], state: { title: 'private playback details' }, roomName: 'REST room', bannerUrl: 'https://images.test/rest-banner.jpg', wallpaperUrl: 'https://images.test/rest-wallpaper.jpg', createdAt: 3 });
  rooms.set('lt:REST002', { visibility: 'private', host: 'host', hostName: 'Hidden', members: ['host'], createdAt: 4 });
  const res = await request('GET', { action: 'list' });
  assert.equal(res.statusCode, 200);
  assert.equal(state.authCalls, 0);
  assert.deepEqual(res.body.rooms, [{ code: 'PUBLIC01', transport: 'websocket', hostName: 'Host', roomName: 'WS room', bannerUrl: 'https://images.test/ws-banner.jpg', wallpaperUrl: 'https://images.test/ws-wallpaper.jpg', members: 1, createdAt: 1 }]);
  assert.equal(JSON.stringify(res.body).includes('secret track'), false);
  const rest = await request('GET', { action: 'list', transport: 'rest' });
  assert.deepEqual(rest.body.rooms, [{ code: 'REST001', transport: 'rest', hostName: 'REST Host', roomName: 'REST room', bannerUrl: 'https://images.test/rest-banner.jpg', wallpaperUrl: 'https://images.test/rest-wallpaper.jpg', members: 1, createdAt: 3 }]);
  const all = await request('GET', { action: 'list', transport: 'all' });
  assert.equal(all.body.rooms.length, 2);
  assert.equal(JSON.stringify(all.body).includes('private playback details'), false);
});
test('metadata registration defaults to private and REST GET omits WS playback state', async () => {
  rooms.clear(); state.authError = false;
  const res = await request('POST', { action: 'metadata' }, { code: 'ROOM1234' });
  assert.equal(res.body.visibility, 'private');
  assert.equal(rooms.get('lt:ws:ROOM1234').visibility, 'private');
  assert.equal(rooms.get('lt:ws:ROOM1234').bannerUrl, '');
  assert.equal(rooms.get('lt:ws:ROOM1234').wallpaperUrl, '');
  assert.ok(Date.now() - rooms.get('lt:ws:ROOM1234').lastHeartbeatAt < 1_000);
  assert.equal(res.body.bannerUrl, '');
  assert.equal(res.body.wallpaperUrl, '');
  rooms.set('lt:ws:LEGACY01', { transport: 'websocket', host: 'host', members: ['host'], wallpaperUrl: 'https://images.test/old-wallpaper.jpg' });
  const legacy = await request('POST', { action: 'metadata' }, { code: 'LEGACY01' });
  assert.equal(legacy.body.bannerUrl, '');
  assert.equal(legacy.body.wallpaperUrl, 'https://images.test/old-wallpaper.jpg');
  assert.equal(rooms.get('lt:ws:LEGACY01').bannerUrl, '');
  const get = await request('GET', { code: 'ROOM1234' });
  assert.equal(get.body.transport, 'websocket');
  assert.equal(get.body.state, undefined);
});
test('only the WS-room host may change visibility', async () => {
  rooms.clear(); rooms.set('lt:ws:ROOM1234', { transport: 'websocket', visibility: 'private', host: 'host', members: ['host'], createdAt: 1 });
  const forbidden = await request('POST', { action: 'visibility' }, { code: 'ROOM1234', visibility: 'public' }, 'guest');
  assert.equal(forbidden.statusCode, 403);
  assert.equal(rooms.get('lt:ws:ROOM1234').visibility, 'private');
  const allowed = await request('POST', { action: 'visibility' }, { code: 'ROOM1234', visibility: 'public' }, 'host');
  assert.equal(allowed.body.visibility, 'public');
});
test('WS room heartbeat is host-only and stale metadata automatically closes', async () => {
  rooms.clear(); state.authError = false;
  const staleAt = Date.now() - 3 * 60 * 1000;
  rooms.set('lt:ws:LEASE01', { transport: 'websocket', visibility: 'public', host: 'host', hostName: 'Host', members: ['host'], createdAt: staleAt, lastHeartbeatAt: staleAt });

  const forbidden = await request('POST', { action: 'heartbeat' }, { code: 'LEASE01' }, 'guest');
  assert.equal(forbidden.statusCode, 403);
  assert.equal(rooms.get('lt:ws:LEASE01').lastHeartbeatAt, staleAt);

  const refreshed = await request('POST', { action: 'heartbeat' }, { code: 'LEASE01' }, 'host');
  assert.equal(refreshed.statusCode, 200);
  assert.ok(refreshed.body.lastHeartbeatAt > staleAt);

  rooms.set('lt:ws:LEASE01', { transport: 'websocket', visibility: 'private', host: 'host', members: ['host'], createdAt: staleAt, lastHeartbeatAt: staleAt });
  const expired = await request('GET', { code: 'LEASE01' }, {}, 'guest');
  assert.equal(expired.statusCode, 404);
  assert.equal(rooms.has('lt:ws:LEASE01'), false);

  rooms.set('lt:ws:STALE01', { transport: 'websocket', visibility: 'public', host: 'host', members: ['host'], createdAt: staleAt, lastHeartbeatAt: staleAt });
  const listing = await request('GET', { action: 'list' });
  assert.equal(listing.body.rooms.some((room) => room.code === 'STALE01'), false);
  assert.equal(rooms.has('lt:ws:STALE01'), false);
});
test('REST room creation defaults both URLs and public GET/join return separate banner and wallpaper metadata', async () => {
  rooms.clear(); state.authError = false;
  const created = await request('POST', { action: 'create' }, { visibility: 'public', state: { title: 'shared track' } });
  assert.equal(created.statusCode, 200);
  const code = created.body.code;
  const stored = rooms.get(`lt:${code}`);
  assert.equal(stored.visibility, 'public');
  assert.equal(stored.transport, undefined);
  assert.equal(stored.bannerUrl, '');
  assert.equal(stored.wallpaperUrl, '');
  const settings = await request('POST', { action: 'settings' }, { code, roomName: 'Ruang malam', wallpaperUrl: 'https://images.test/inside.jpg', bannerUrl: 'https://images.test/listing.jpg' });
  assert.equal(settings.statusCode, 200);
  const bannerOnly = await request('POST', { action: 'settings' }, { code, bannerUrl: 'https://images.test/updated-listing.jpg' });
  assert.equal(bannerOnly.body.bannerUrl, 'https://images.test/updated-listing.jpg');
  assert.equal(bannerOnly.body.wallpaperUrl, 'https://images.test/inside.jpg');
  const wallpaperOnly = await request('POST', { action: 'settings' }, { code, wallpaperUrl: 'https://images.test/updated-inside.jpg' });
  assert.equal(wallpaperOnly.body.bannerUrl, 'https://images.test/updated-listing.jpg');
  assert.equal(wallpaperOnly.body.wallpaperUrl, 'https://images.test/updated-inside.jpg');
  const joined = await request('POST', { action: 'join' }, { code }, 'guest');
  assert.equal(joined.statusCode, 200);
  assert.equal(joined.body.roomName, 'Ruang malam');
  assert.equal(joined.body.bannerUrl, 'https://images.test/updated-listing.jpg');
  assert.equal(joined.body.wallpaperUrl, 'https://images.test/updated-inside.jpg');
  assert.equal(joined.body.visibility, 'public');
  assert.equal(joined.body.state.title, 'shared track');
  const read = await request('GET', { code }, {}, 'guest');
  assert.equal(read.body.bannerUrl, 'https://images.test/updated-listing.jpg');
  assert.equal(read.body.wallpaperUrl, 'https://images.test/updated-inside.jpg');
});

test('room settings validates each URL independently as HTTPS and at most 500 characters', async () => {
  rooms.clear(); state.authError = false;
  const created = await request('POST', { action: 'create' }, { visibility: 'public' });
  const code = created.body.code;
  const valid = { bannerUrl: 'https://images.test/banner.jpg', wallpaperUrl: 'https://images.test/wallpaper.jpg' };
  await request('POST', { action: 'settings' }, { code, ...valid });
  for (const [field, value] of [['bannerUrl', 'http://images.test/banner.jpg'], ['wallpaperUrl', 'http://images.test/wallpaper.jpg'], ['bannerUrl', `https://${'a'.repeat(493)}`], ['wallpaperUrl', `https://${'a'.repeat(493)}`], ['bannerUrl', 12]]) {
    const invalid = await request('POST', { action: 'settings' }, { code, [field]: value });
    assert.equal(invalid.statusCode, 400, `${field} should reject invalid value`);
  }
  const mixedInvalid = await request('POST', { action: 'settings' }, { code, bannerUrl: 'https://images.test/changed.jpg', wallpaperUrl: 'http://images.test/invalid.jpg' });
  assert.equal(mixedInvalid.statusCode, 400);
  assert.deepEqual([rooms.get(`lt:${code}`).bannerUrl, rooms.get(`lt:${code}`).wallpaperUrl], [valid.bannerUrl, valid.wallpaperUrl]);
});
test('private REST rooms are not discoverable or joinable by other users', async () => {
  rooms.clear(); state.authError = false;
  rooms.set('lt:PRIVATE1', { host: 'host', hostName: 'Host', visibility: 'private', members: ['host'], state: {}, createdAt: 1 });
  const list = await request('GET', { action: 'list', transport: 'all' });
  assert.deepEqual(list.body.rooms, []);
  const joined = await request('POST', { action: 'join' }, { code: 'PRIVATE1' }, 'guest');
  assert.equal(joined.statusCode, 200);
  assert.equal(joined.body.status, 'pending');
  assert.equal(joined.body.request.displayName, 'guest display');
  const read = await request('GET', { code: 'PRIVATE1' }, {}, 'guest');
  assert.equal(read.statusCode, 404);
});
test('REST host can change visibility and only its room key is updated', async () => {
  rooms.clear(); state.authError = false;
  rooms.set('lt:REST1234', { host: 'host', hostName: 'Host', visibility: 'private', members: ['host'], createdAt: 1 });
  const changed = await request('POST', { action: 'visibility' }, { code: 'REST1234', visibility: 'public' });
  assert.equal(changed.statusCode, 200);
  assert.equal(rooms.get('lt:REST1234').visibility, 'public');
  assert.equal(rooms.has('lt:ws:REST1234'), false);
});

test('private admission acceptance joins the member and exposes live avatar metadata', async () => {
  rooms.clear(); state.authError = false;
  const created = await request('POST', { action: 'create' }, { visibility: 'private', state: {} });
  const code = created.body.code;
  const pending = await request('POST', { action: 'join' }, { code }, 'guest');
  assert.equal(pending.body.status, 'pending');
  const inbox = await request('POST', { action: 'entryRequests' }, { code }, 'host');
  assert.equal(inbox.body.requests.length, 1);
  const accepted = await request('POST', { action: 'entryDecision' }, { code, username: 'guest', accept: true }, 'host');
  assert.equal(accepted.body.status, 'accepted');
  const joined = await request('POST', { action: 'join' }, { code }, 'guest');
  assert.equal(joined.body.status, 'joined');
  assert.equal(joined.body.members, 2);
  assert.deepEqual(joined.body.memberList.map((member) => member.username), ['host', 'guest']);
});

test('private admission preview exposes the requester name and host rejection reaches requester', async () => {
  rooms.clear(); state.authError = false;
  const created = await request('POST', { action: 'create' }, { visibility: 'private', state: {} });
  const code = created.body.code;
  const pending = await request('POST', { action: 'join' }, { code }, 'guest');
  assert.equal(pending.body.status, 'pending');

  const unauthorizedInbox = await request('POST', { action: 'entryRequests' }, { code }, 'guest');
  assert.equal(unauthorizedInbox.statusCode, 403);
  const unauthorizedDecision = await request('POST', { action: 'entryDecision' }, { code, username: 'guest', accept: false }, 'guest');
  assert.equal(unauthorizedDecision.statusCode, 403);

  const inbox = await request('POST', { action: 'entryRequests' }, { code }, 'host');
  assert.equal(inbox.body.requests[0].displayName, 'guest display');
  const declined = await request('POST', { action: 'entryDecision' }, { code, username: 'guest', accept: false }, 'host');
  assert.equal(declined.body.status, 'declined');
  const result = await request('POST', { action: 'join' }, { code }, 'guest');
  assert.equal(result.body.status, 'rejected');
  assert.match(result.body.rejection.message, /ditolak/i);
});

test('song requests are host-approved, requester can see the outcome, and ownership can transfer', async () => {
  rooms.clear(); state.authError = false;
  const created = await request('POST', { action: 'create' }, { visibility: 'public', state: {} });
  const code = created.body.code;
  await request('POST', { action: 'join' }, { code }, 'guest');
  const invalid = await request('POST', { action: 'songRequest' }, { code, videoId: 'bad' }, 'guest');
  assert.equal(invalid.statusCode, 400);
  const sent = await request('POST', { action: 'songRequest' }, { code, videoId: 'abcdefghijk', title: 'Lagu pilihan' }, 'guest');
  assert.equal(sent.statusCode, 200);
  const requestId = sent.body.request.id;
  const pending = await request('POST', { action: 'songRequests' }, { code }, 'host');
  assert.equal(pending.body.requests.length, 1);
  const accepted = await request('POST', { action: 'songDecision' }, { code, requestId, accept: true }, 'host');
  assert.equal(accepted.body.request.status, 'accepted');
  assert.equal(accepted.body.state.videoId, 'abcdefghijk');
  const status = await request('POST', { action: 'songRequestStatus' }, { code, requestId }, 'guest');
  assert.equal(status.body.request.status, 'accepted');
  const transferred = await request('POST', { action: 'transfer' }, { code, username: 'guest' }, 'host');
  assert.equal(transferred.body.host, 'guest');
  const forbidden = await request('POST', { action: 'push' }, { code, state: {} }, 'host');
  assert.equal(forbidden.statusCode, 403);
  const closed = await request('POST', { action: 'close' }, { code }, 'guest');
  assert.equal(closed.body.closed, true);
  assert.equal(rooms.has(`lt:${code}`), false);
});

test('REST room heartbeat refreshes active members and public discovery closes stale hosts', async () => {
  rooms.clear(); state.authError = false;
  rooms.set('lt:RESTSTAL', {
    host: 'host', hostName: 'Host', visibility: 'public', members: ['host', 'guest'],
    memberInfo: {}, memberHeartbeat: { host: Date.now() - 180_000, guest: Date.now() }, state: {}, createdAt: 1,
  });
  const list = await request('GET', { action: 'list', transport: 'rest' });
  assert.deepEqual(list.body.rooms, []);
  assert.equal(rooms.has('lt:RESTSTAL'), false);
  rooms.set('lt:ACTIVE01', { host: 'host', hostName: 'Host', visibility: 'public', members: ['host'], memberInfo: {}, memberHeartbeat: { host: Date.now() }, state: {}, createdAt: 1 });
  const read = await request('GET', { code: 'ACTIVE01' }, {}, 'host');
  assert.equal(read.statusCode, 200);
  assert.ok(Date.now() - rooms.get('lt:ACTIVE01').memberHeartbeat.host < 1_000);
});

test('WebSocket host transfer stays on the WS metadata key and grants the new host banner and wallpaper controls', async () => {
  rooms.clear(); state.authError = false;
  rooms.set('lt:ws:ROOM1234', {
    transport: 'websocket', host: 'host', hostName: 'Host', members: ['host', 'guest'],
    memberInfo: { guest: { displayName: 'Guest display' } }, memberHeartbeat: {}, createdAt: 1,
  });

  const transferred = await request('POST', { action: 'transfer' }, { code: 'ROOM1234', username: 'guest' }, 'host');
  assert.equal(transferred.statusCode, 200);
  assert.equal(rooms.get('lt:ws:ROOM1234').host, 'guest');
  assert.equal(rooms.has('lt:ROOM1234'), false);

  const saved = await request('POST', { action: 'settings' }, {
    code: 'ROOM1234', roomName: 'Ruang baru',
    bannerUrl: 'https://images.test/banner.jpg', wallpaperUrl: 'https://images.test/wallpaper.jpg',
  }, 'guest');
  assert.equal(saved.statusCode, 200);
  assert.equal(saved.body.bannerUrl, 'https://images.test/banner.jpg');
  assert.equal(saved.body.wallpaperUrl, 'https://images.test/wallpaper.jpg');

  const oldHost = await request('POST', { action: 'settings' }, { code: 'ROOM1234', bannerUrl: '' }, 'host');
  assert.equal(oldHost.statusCode, 403);
});

test('WS metadata cannot be mutated as a REST playback room', async () => {
  rooms.clear(); rooms.set('lt:ws:ROOM1234', { transport: 'websocket', visibility: 'public', host: 'host', members: ['host'], createdAt: 1 });
  const res = await request('POST', { action: 'push' }, { code: 'ROOM1234', state: { title: 'nope' } });
  assert.equal(res.statusCode, 409);
  assert.equal(rooms.get('lt:ws:ROOM1234').state, undefined);
});
