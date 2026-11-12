'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const Module = require('node:module');
const originalLoad = Module._load;
const state = { role: 'owner', key: false, data: new Map(), calls: [], published: [] };
const clone = (value) => structuredClone(value);
const util = {
  ROLE_RANK: { user: 0, staff: 1, admin: 2, owner: 3 },
  ownerKeyOk: () => state.key,
  ipOf: () => '198.51.100.9',
  auth: async (req, res) => {
    if (!(req.headers.authorization || '').startsWith('Bearer ')) {
      res.statusCode = 401; res.body = { ok: false, error: 'Belum login.' }; return null;
    }
    return { user: { role: state.role, username: 'tester' } };
  },
  body: (req) => req.body || {},
  fail: (res, status, error) => (res.statusCode = status, res.body = { ok: false, error }),
  send: (res, status, body) => (res.statusCode = status, res.body = body),
  cmd: async (...args) => { state.calls.push(args); return args[0] === 'LRANGE' ? [] : 'OK'; },
  getJson: async (key) => state.data.has(key) ? clone(state.data.get(key)) : null,
  setJson: async (key, value) => { state.data.set(key, clone(value)); return 'OK'; },
};
const botSettings = {};
const chat = { publishAnnouncement: async (text) => { state.published.push(text); } };
Module._load = function (request, parent, isMain) {
  if (parent?.filename.endsWith('/server/api/owner.js')) {
    if (request === '../lib/util') return util;
    if (request === '../lib/bot-settings') return botSettings;
    if (request === './chat') return chat;
  }
  return originalLoad.call(this, request, parent, isMain);
};
const handler = require('../api/owner');
function reset() {
  state.role = 'owner'; state.key = false; state.data.clear(); state.calls = []; state.published = [];
}
async function request(method, action, body = {}, options = {}) {
  const headers = options.headers || (options.noAuth ? {} : { authorization: 'Bearer valid-session' });
  const req = { method, query: { action }, body: { action, ...body }, headers, socket: { remoteAddress: '198.51.100.9' } };
  const res = { headers: {}, setHeader(name, value) { this.headers[name.toLowerCase()] = value; } };
  await handler(req, res);
  return res;
}

test.beforeEach(reset);

test('histori maintenance menolak request tanpa autentikasi dan role staff/admin', async () => {
  const noAuth = await request('GET', 'maintenance.history.list', {}, { noAuth: true });
  assert.equal(noAuth.statusCode, 401);
  for (const role of ['staff', 'admin']) {
    state.role = role;
    for (const [method, action, body] of [
      ['GET', 'maintenance.history.list', {}],
      ['POST', 'maintenance.history.update', { id: '11111111-1111-4111-8111-111111111111', enabled: false, message: '', eta: '' }],
      ['POST', 'maintenance.history.delete', { id: '11111111-1111-4111-8111-111111111111' }],
      ['POST', 'maintenance.history.apply', { id: '11111111-1111-4111-8111-111111111111' }],
    ]) assert.equal((await request(method, action, body)).statusCode, 403, `${role} ${action}`);
  }
  assert.equal(state.data.size, 0);
});

test('owner mengelola dan menerapkan snapshot maintenance tanpa mengubah status update aplikasi', async () => {
  state.data.set('app:status', { update: { enabled: true, versionName: '9.1', versionCode: 9 }, maintenance: { enabled: false } });
  const set = await request('POST', 'maintenance.set', { enabled: true, message: 'Peningkatan server', eta: '21.00 WIB' });
  assert.equal(set.statusCode, 200);
  const snapshot = set.body.snapshot;
  assert.equal(snapshot.createdBy, 'tester');
  assert.equal((await request('GET', 'maintenance.history.list')).body.snapshots.length, 1);
  const edited = await request('POST', 'maintenance.history.update', { id: snapshot.id, enabled: false, message: 'Pemeliharaan selesai', eta: '' });
  assert.equal(edited.body.snapshot.message, 'Pemeliharaan selesai');
  const applied = await request('POST', 'maintenance.history.apply', { id: snapshot.id });
  assert.deepEqual(applied.body.maintenance, { enabled: false, message: 'Pemeliharaan selesai', eta: '' });
  const status = await request('GET', 'status.get');
  assert.deepEqual(status.body.status.update, { enabled: true, versionName: '9.1', versionCode: 9 });
  assert.deepEqual(status.body.status.maintenance, applied.body.maintenance);
  assert.equal((await request('POST', 'maintenance.history.delete', { id: snapshot.id })).statusCode, 200);
  assert.equal((await request('GET', 'maintenance.history.list')).body.snapshots.length, 0);
  assert.ok(state.data.get('owner:maintenance-history')[0].deletedAt > 0);
});

test('aksi histori menolak method yang keliru, ID tidak valid, dan field melebihi batas', async () => {
  assert.equal((await request('POST', 'maintenance.set', { enabled: true, message: 'x'.repeat(801), eta: '' })).statusCode, 400);
  assert.equal((await request('POST', 'maintenance.history.apply', { id: 'bad' })).statusCode, 400);
});
