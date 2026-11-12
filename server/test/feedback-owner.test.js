'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const Module = require('node:module');
const originalLoad = Module._load;
const data = new Map();
const state = { role: 'staff', key: false, calls: [] };
const util = {
  ROLE_RANK: { user: 0, staff: 1, admin: 3, owner: 3 },
  ownerKeyOk: () => state.key,
  auth: async () => ({ user: { role: state.role, username: 'tester' } }),
  body: (req) => req.body || {},
  fail: (res, status, error) => (res.statusCode = status, res.value = { ok: false, error }),
  send: (res, status, value) => (res.statusCode = status, res.value = value),
  cmd: async (...args) => { state.calls.push(args); return args[0] === 'ZREVRANGE' ? [] : 'OK'; },
  getJson: async (key) => data.get(key) || null,
  setJson: async (key, value) => data.set(key, value),
};
Module._load = function (request, parent, isMain) {
  if (request === '../lib/util' && parent.filename.endsWith('/server/api/owner.js')) return util;
  if (request === '../lib/bot-settings' && parent.filename.endsWith('/server/api/owner.js')) return {};
  return originalLoad.call(this, request, parent, isMain);
};
const handler = require('../api/owner');
Module._load = originalLoad;
async function request(method, action, body = {}) {
  const res = { headers: {}, setHeader(k, v) { this.headers[k.toLowerCase()] = v; } };
  await handler({ method, query: { action }, headers: {}, body: { action, ...body } }, res);
  return res;
}
test.beforeEach(() => { state.role = 'staff'; state.key = false; state.calls = []; data.clear(); });
test('feedback list and mutations reject staff and admin regardless of rank', async () => {
  for (const role of ['staff', 'admin']) {
    state.role = role;
    assert.equal((await request('GET', 'feedback.list')).statusCode, 403);
    assert.equal((await request('POST', 'feedback.update', { id: '11111111-1111-4111-8111-111111111111', status: 'done' })).statusCode, 403);
    assert.equal((await request('POST', 'feedback.archive', { id: '11111111-1111-4111-8111-111111111111' })).statusCode, 403);
  }
  assert.equal(state.calls.length, 0);
});
test('exact owner can list/update/archive; owner key can list', async () => {
  const id = '11111111-1111-4111-8111-111111111111';
  data.set(`feedback:${id}`, { id, status: 'new', title: 'Crash' });
  state.role = 'owner';
  assert.equal((await request('GET', 'feedback.list')).statusCode, 200);
  assert.equal((await request('POST', 'feedback.update', { id, status: 'processing' })).value.feedback.status, 'processing');
  assert.equal((await request('POST', 'feedback.archive', { id })).value.feedback.status, 'archived');
  state.role = 'staff'; state.key = true;
  assert.equal((await request('GET', 'feedback.list')).statusCode, 200);
});
test('feedback action method gate runs before authorization or storage access', async () => {
  const res = await request('POST', 'feedback.list');
  assert.equal(res.statusCode, 405);
  assert.equal(res.headers.allow, 'GET');
  assert.equal(state.calls.length, 0);
});
