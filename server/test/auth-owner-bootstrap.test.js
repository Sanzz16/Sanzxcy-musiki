'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const Module = require('node:module');
const originalLoad = Module._load;
const state = { user: null, saved: 0 };
const fakeUtil = {
  ipOf: () => '127.0.0.1', isIpBanned: async () => false, lower: (x) => String(x || '').trim().toLowerCase(),
  body: (req) => req.body || {}, hasReservedWord: () => false,
  cmd: async (...args) => args[0] === 'INCR' ? 1 : args[0] === 'GET' ? null : 'OK',
  getUser: async () => state.user,
  saveUser: async (user) => { state.user = user; state.saved++; },
  hashPassword: () => ({ salt: 'salt', hash: 'hash' }), verifyPassword: () => true,
  createSession: async () => 'session-token', publicUser: (user) => ({ username: user.username, role: user.role }),
  send: (res, status, body) => { res.statusCode = status; res.body = body; return body; },
  fail: (res, status, error, extra) => { res.statusCode = status; res.body = { ok: false, error, ...(extra || {}) }; return res.body; },
};
Module._load = function (request, parent, isMain) {
  if (request === '../lib/util' && parent?.filename.endsWith('/server/api/auth.js')) return fakeUtil;
  return originalLoad.call(this, request, parent, isMain);
};
const handler = require('../api/auth');
Module._load = originalLoad;
function request(action, body) {
  const req = { method: 'POST', query: { action }, body, headers: {}, socket: {} };
  const res = { status(s) { this.statusCode = s; return this; }, setHeader() { return this; }, send(text) { this.body = JSON.parse(text); return this; } };
  return handler(req, res).then(() => res);
}

test('public registration cannot self-assign the configured owner username', async () => {
  const previous = process.env.OWNER_USERNAME;
  process.env.OWNER_USERNAME = 'MainOwner'; state.user = null; state.saved = 0;
  try {
    const res = await request('register', { username: 'mainowner', password: 'secret123' });
    assert.equal(res.statusCode, 403);
    assert.equal(res.body.code, 'owner_registration_disabled');
    assert.match(res.body.error, /panel owner|OWNER_KEY/);
    assert.equal(state.saved, 0);
  } finally {
    if (previous === undefined) delete process.env.OWNER_USERNAME; else process.env.OWNER_USERNAME = previous;
  }
});

test('existing owner record can still log in', async () => {
  const previous = process.env.OWNER_USERNAME;
  process.env.OWNER_USERNAME = 'mainowner';
  state.user = { username: 'mainowner', role: 'owner', salt: 'salt', passHash: 'hash', ver: 0, ips: [] };
  try {
    const res = await request('login', { identifier: 'mainowner', password: 'secret123' });
    assert.equal(res.statusCode, 200);
    assert.equal(res.body.user.role, 'owner');
    assert.equal(res.body.token, 'session-token');
  } finally {
    if (previous === undefined) delete process.env.OWNER_USERNAME; else process.env.OWNER_USERNAME = previous;
  }
});
