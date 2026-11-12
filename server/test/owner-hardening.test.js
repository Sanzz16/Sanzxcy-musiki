'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const Module = require('node:module');
const originalLoad = Module._load;
const state = { keyOk: false, role: 'owner', authCalls: 0, calls: [], counters: new Map(), saves: 0, rooms: new Map() };
const fakeUtil = {
  ROLE_RANK: { user: 0, staff: 1, admin: 2, owner: 3 },
  ownerKeyOk: () => state.keyOk,
  ipOf: () => '198.51.100.9',
  auth: async (req, res) => {
    state.authCalls++;
    if (!(req.headers.authorization || '').startsWith('Bearer ')) {
      res.statusCode = 401;
      res.body = { ok: false, error: 'Belum login.' };
      return null;
    }
    return { user: { username: 'account', role: state.role } };
  },
  body: (req) => req.body || {},
  send: (res, status, body) => { res.statusCode = status; res.body = body; return body; },
  fail: (res, status, error) => { res.statusCode = status; res.body = { ok: false, error }; return res.body; },
  cmd: async (...args) => {
    state.calls.push(args);
    if (args[0] === 'SCAN') return ['0', [...state.rooms.keys()].filter((key) => args[2] === 'lt:ws:*' ? key.startsWith('lt:ws:') : key.startsWith('lt:'))];
    if (args[0] === 'GET') return null;
    if (args[0] === 'DEL') { state.rooms.delete(args[1]); return 1; }
    if (args[0] === 'EVAL') {
      const key = args[3];
      const n = (state.counters.get(key) || 0) + 1;
      state.counters.set(key, n);
      return n;
    }
    if (args[0] === 'LRANGE') return [];
    if (args[0] === 'SCARD') return 0;
    return 'OK';
  },
  getJson: async (key) => state.rooms.get(key) || null,
  setJson: async (key, value) => { state.rooms.set(key, structuredClone(value)); },
  saveUser: async () => { state.saves++; },
};
const fakeBotSettings = { getSettings: async () => ({}), saveSettings: async () => ({ ok: true, settings: {} }) };
Module._load = function (request, parent, isMain) {
  if (request === '../lib/util' && parent?.filename.endsWith('/server/api/owner.js')) return fakeUtil;
  if (request === '../lib/bot-settings' && parent?.filename.endsWith('/server/api/owner.js')) return fakeBotSettings;
  return originalLoad.call(this, request, parent, isMain);
};
const handler = require('../api/owner');
Module._load = originalLoad;

function reset() {
  state.keyOk = false;
  state.role = 'owner';
  state.authCalls = 0;
  state.calls = [];
  state.counters = new Map();
  state.saves = 0;
  state.rooms.clear();
}
function request(method, action, { body = {}, headers = {}, ip = '198.51.100.9' } = {}) {
  const req = { method, query: { action }, body: { action, ...body }, headers, socket: { remoteAddress: ip } };
  const res = {
    headers: {},
    setHeader(name, value) { this.headers[name.toLowerCase()] = value; return this; },
    status(s) { this.statusCode = s; return this; },
    send(text) { this.body = JSON.parse(text); return this; },
  };
  return handler(req, res).then(() => res);
}

 test('owner read-only actions reject POST before auth or writes', async () => {
  reset();
  const res = await request('POST', 'stats', { body: {} });
  assert.equal(res.statusCode, 405);
  assert.equal(res.headers.allow, 'GET');
  assert.equal(state.authCalls, 0);
  assert.equal(state.calls.length, 0);
  assert.equal(state.saves, 0);
});

test('owner mutations reject GET before auth or writes', async () => {
  reset();
  const res = await request('GET', 'maintenance.set', { headers: { 'x-owner-key': 'valid' } });
  assert.equal(res.statusCode, 405);
  assert.equal(res.headers.allow, 'POST');
  assert.equal(state.authCalls, 0);
  assert.equal(state.calls.length, 0);
  assert.equal(state.saves, 0);
});

test('owner room listing and updates keep banner and wallpaper independent', async () => {
  reset();
  state.rooms.set('lt:ROOM1234', { host: 'host', hostName: 'Host', visibility: 'public', roomName: 'Room', bannerUrl: 'https://images.test/listing.jpg', wallpaperUrl: 'https://images.test/inside.jpg', members: ['host'], createdAt: 1 });
  const listed = await request('GET', 'rooms.list', { headers: { authorization: 'Bearer valid-session' } });
  assert.equal(listed.statusCode, 200);
  assert.equal(listed.body.rooms[0].bannerUrl, 'https://images.test/listing.jpg');
  assert.equal(listed.body.rooms[0].wallpaperUrl, 'https://images.test/inside.jpg');

  const updated = await request('POST', 'rooms.update', { body: { code: 'ROOM1234', bannerUrl: 'https://images.test/new-listing.jpg' }, headers: { authorization: 'Bearer valid-session' } });
  assert.equal(updated.statusCode, 200);
  assert.equal(updated.body.room.bannerUrl, 'https://images.test/new-listing.jpg');
  assert.equal(updated.body.room.wallpaperUrl, 'https://images.test/inside.jpg');
  assert.equal(state.rooms.get('lt:ROOM1234').wallpaperUrl, 'https://images.test/inside.jpg');

  for (const [field, value] of [['bannerUrl', 'http://images.test/nope.jpg'], ['wallpaperUrl', 'http://images.test/nope.jpg'], ['bannerUrl', `https://${'a'.repeat(493)}`], ['wallpaperUrl', `https://${'a'.repeat(493)}`], ['bannerUrl', 12]]) {
    const invalid = await request('POST', 'rooms.update', { body: { code: 'ROOM1234', [field]: value }, headers: { authorization: 'Bearer valid-session' } });
    assert.equal(invalid.statusCode, 400, `${field} should reject invalid value`);
  }
  const mixedInvalid = await request('POST', 'rooms.update', { body: { code: 'ROOM1234', bannerUrl: 'https://images.test/partial.jpg', wallpaperUrl: 'http://images.test/invalid.jpg' }, headers: { authorization: 'Bearer valid-session' } });
  assert.equal(mixedInvalid.statusCode, 400);
  assert.equal(state.rooms.get('lt:ROOM1234').bannerUrl, 'https://images.test/new-listing.jpg');
  assert.equal(state.rooms.get('lt:ROOM1234').wallpaperUrl, 'https://images.test/inside.jpg');
});

test('unsupported methods reject before any auth or side effect', async () => {
  reset();
  const res = await request('DELETE', 'stats');
  assert.equal(res.statusCode, 405);
  assert.equal(res.headers.allow, 'GET, POST');
  assert.equal(state.authCalls, 0);
  assert.equal(state.calls.length, 0);
});

test('failed OWNER_KEY attempts are bounded while bearer login and a rotated valid key remain usable', async () => {
  reset();
  const previous = process.env.OWNER_KEY;
  process.env.OWNER_KEY = 'configured-owner-key';
  try {
    for (let i = 0; i < 10; i++) {
      const res = await request('GET', 'stats', { headers: { 'x-owner-key': 'wrong-key' } });
      assert.equal(res.statusCode, 401);
    }
    const blocked = await request('GET', 'stats', { headers: { 'x-owner-key': 'wrong-key' } });
    assert.equal(blocked.statusCode, 429);

    const bearer = await request('GET', 'stats', { headers: { 'x-owner-key': 'wrong-key', authorization: 'Bearer valid-session' } });
    assert.equal(bearer.statusCode, 200);

    state.keyOk = true; // Simulate rotation to the configured OWNER_KEY.
    const rotatedKey = await request('GET', 'stats', { headers: { 'x-owner-key': 'configured-owner-key' } });
    assert.equal(rotatedKey.statusCode, 200);
  } finally {
    if (previous === undefined) delete process.env.OWNER_KEY;
    else process.env.OWNER_KEY = previous;
  }
});

test('owner page uses external JavaScript and CSP forbids inline scripts', () => {
  const publicDir = path.join(__dirname, '..', 'public');
  const html = fs.readFileSync(path.join(publicDir, 'owner.html'), 'utf8');
  const config = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'vercel.json'), 'utf8'));
  assert.match(html, /<script\s+src="\/owner\.js"><\/script>/);
  assert.doesNotMatch(html, /<script(?![^>]*\bsrc=)[^>]*>[\s\S]*?<\/script>/i);
  const rules = config.headers;
  const ownerHeaders = rules.find((r) => r.source === '/owner.html').headers;
  const apiHeaders = rules.find((r) => r.source === '/api/:path*').headers;
  const ownerCsp = ownerHeaders.find((h) => h.key === 'Content-Security-Policy').value;
  const apiCsp = apiHeaders.find((h) => h.key === 'Content-Security-Policy').value;
  assert.match(ownerCsp, /script-src 'self'/);
  assert.match(ownerCsp, /media-src 'self' https:/);
  assert.doesNotMatch(ownerCsp, /script-src[^;]*unsafe-inline/);
  assert.match(apiCsp, /default-src 'none'/);
  for (const headers of [ownerHeaders, apiHeaders]) {
    assert.ok(headers.some((h) => h.key === 'X-Content-Type-Options'));
    assert.ok(headers.some((h) => h.key === 'X-Frame-Options'));
    assert.ok(headers.some((h) => h.key === 'Strict-Transport-Security'));
  }
});


test('APK uploader is owner-page only, uses local Blob SDK, and never embeds GitHub credentials in browser code', () => {
  const publicDir = path.join(__dirname, '..', 'public');
  const html = fs.readFileSync(path.join(publicDir, 'owner.html'), 'utf8');
  const js = fs.readFileSync(path.join(publicDir, 'owner.js'), 'utf8');
  const api = fs.readFileSync(path.join(__dirname, '..', 'api', 'apk-upload.js'), 'utf8');
  assert.match(html, /id="t-apk"/);
  assert.match(js, /api\/apk-upload/);
  assert.match(js, /import\('\/blob-client\.js'\)/);
  assert.match(js, /async function ensureApkOwnerSession/);
  assert.match(js, /result\.user\?\.role!=='owner'/);
  assert.match(html, /id="botApkShortcut"/);
  assert.match(js, /botApkShortcut.*showTab\('apk'\)/);
  assert.doesNotMatch(js, /Untuk upload APK, masuk dengan akun Owner username\/password/);
  assert.doesNotMatch(js, /GITHUB_TOKEN|gh[pousr]_[A-Za-z0-9_]{20,}/);
  assert.match(api, /process\.env\.GITHUB_TOKEN/);
  assert.match(api, /BLOB_READ_WRITE_TOKEN tidak dikonfigurasi/);
  assert.match(js, /action:'preflight'/);
  assert.match(html, /id="apkDist"/);
  assert.match(html, /id="uDist"/);
  assert.match(js, /Sanzxcy-Music-arm64-v8a\.apk/);
  assert.match(js, /Sanzxcy-Music-arm32-v7a\.apk/);
  assert.match(js, /\?i=1#beranda/);
  assert.doesNotMatch(js, /BLOB_READ_WRITE_TOKEN|GITHUB_TOKEN/);
  assert.ok(fs.existsSync(path.join(publicDir, 'blob-client.js')));
});
