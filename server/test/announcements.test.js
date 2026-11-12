'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const Module = require('node:module');
const originalLoad = Module._load;
const state = { role: 'owner', ownerKey: false, data: new Map(), audit: [] };
const clone = (value) => value == null ? value : structuredClone(value);
const util = {
  ROLE_RANK: { user: 0, staff: 1, admin: 2, owner: 3 },
  ownerKeyOk: () => state.ownerKey,
  ipOf: () => '198.51.100.9',
  auth: async (req, res) => {
    if (!(req.headers.authorization || '').startsWith('Bearer ')) {
      res.statusCode = 401; res.body = { ok: false, error: 'Belum login.' }; return null;
    }
    return { user: { role: state.role, username: 'tester' } };
  },
  body: (req) => req.body || {},
  fail: (res, status, error) => { res.statusCode = status; res.body = { ok: false, error }; return res.body; },
  send: (res, status, body) => { res.statusCode = status; res.body = body; return body; },
  cmd: async (...args) => { state.audit.push(args); return 'OK'; },
  getJson: async (key) => clone(state.data.get(key)),
  setJson: async (key, value) => { state.data.set(key, clone(value)); return 'OK'; },
};
Module._load = function (request, parent, isMain) {
  if (parent?.filename.endsWith('/server/api/owner.js')) {
    if (request === '../lib/util') return util;
    if (request === '../lib/bot-settings') return {};
    if (request === './chat') return {};
  }
  if (parent?.filename.endsWith('/server/api/announcements.js') && request === '../lib/util') return util;
  return originalLoad.call(this, request, parent, isMain);
};
const ownerHandler = require('../api/owner');
const feedHandler = require('../api/announcements');
Module._load = originalLoad;

const request = (method, action, body = {}, extraQuery = {}, auth = true) => ({
  method,
  query: { ...(action ? { action } : {}), ...extraQuery },
  body: { ...(action ? { action } : {}), ...body },
  headers: auth ? { authorization: 'Bearer valid-session' } : {},
  socket: { remoteAddress: '198.51.100.9' },
});
const response = () => ({ setHeader(name, value) { this.headers ||= {}; this.headers[name.toLowerCase()] = value; } });
const ownerRequest = async (method, action, body = {}, query = {}) => { const res = response(); await ownerHandler(request(method, action, body, query), res); return res; };

test.beforeEach(() => { state.role = 'owner'; state.ownerKey = false; state.data.clear(); state.audit = []; });

test('hanya owner tepat atau owner key yang dapat membaca dan mengelola pengumuman', async () => {
  for (const role of ['staff', 'admin', 'user']) {
    state.role = role;
    assert.equal((await ownerRequest('GET', 'announcements.list')).statusCode, 403, role);
    assert.equal((await ownerRequest('POST', 'announcements.create', { title: 'Rilis', description: 'Info' })).statusCode, 403, role);
  }
  state.ownerKey = true;
  assert.equal((await ownerRequest('POST', 'announcements.create', { title: 'Rilis', description: 'Info' })).statusCode, 201);
});

test('create, edit, revision history, soft-delete, dan public feed berjalan konsisten', async () => {
  const created = await ownerRequest('POST', 'announcements.create', { title: 'Info baru', description: 'Server akan diperbarui.' });
  assert.equal(created.statusCode, 201);
  const id = created.body.announcement.id;
  let feed = response();
  await feedHandler(request('GET', null, {}, {}, true), feed);
  assert.equal(feed.body.announcements.length, 1);
  assert.equal(feed.body.announcements[0].title, 'Info baru');

  const edited = await ownerRequest('POST', 'announcements.update', { id, title: 'Jadwal baru', description: 'Pembaruan selesai.' });
  assert.equal(edited.statusCode, 200);
  assert.ok(edited.body.announcement.updatedAt >= created.body.announcement.updatedAt);
  const history = await ownerRequest('GET', 'announcements.history', {}, { id });
  assert.deepEqual(history.body.history.map((revision) => revision.action), ['edit', 'create']);
  assert.equal(history.body.history[1].title, 'Info baru');

  const deleted = await ownerRequest('POST', 'announcements.delete', { id });
  assert.equal(deleted.statusCode, 200);
  assert.ok(deleted.body.announcement.deletedAt > 0);
  feed = response();
  await feedHandler(request('GET', null, {}, {}, true), feed);
  assert.deepEqual(feed.body.announcements, []);
  const ownerList = await ownerRequest('GET', 'announcements.list');
  assert.ok(ownerList.body.announcements[0].deletedAt > 0);
  const finalHistory = await ownerRequest('GET', 'announcements.history', {}, { id });
  assert.equal(finalHistory.body.history[0].action, 'delete');
});

test('alias action announcement singular diteruskan ke endpoint owner canonical', async () => {
  const created = await ownerRequest('POST', 'announcement.create', { title: 'Pengumuman kompatibel', description: 'Aksi lama tetap dikenali.' });
  assert.equal(created.statusCode, 201);
  const id = created.body.announcement.id;
  assert.equal((await ownerRequest('GET', 'announcement.list')).body.announcements[0].id, id);

  const updated = await ownerRequest('POST', 'announcement.update', { id, title: 'Judul baru', description: 'Konten baru.' });
  assert.equal(updated.statusCode, 200);
  assert.equal(updated.body.announcement.title, 'Judul baru');
  assert.equal((await ownerRequest('GET', 'announcement.history', {}, { id })).statusCode, 200);
  assert.equal((await ownerRequest('POST', 'announcement.delete', { id })).statusCode, 200);
});

test('menolak judul/deskripsi kosong atau melewati batas dan method feed selain GET', async () => {
  assert.equal((await ownerRequest('POST', 'announcements.create', { title: '', description: 'Isi' })).statusCode, 400);
  assert.equal((await ownerRequest('POST', 'announcements.create', { title: 'a'.repeat(101), description: 'Isi' })).statusCode, 400);
  assert.equal((await ownerRequest('POST', 'announcements.create', { title: 'Judul', description: 'a'.repeat(2001) })).statusCode, 400);
  const res = response();
  await feedHandler(request('POST', null), res);
  assert.equal(res.statusCode, 405);
});
