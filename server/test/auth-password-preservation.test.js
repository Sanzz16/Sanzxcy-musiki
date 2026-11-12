'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const Module = require('node:module');
const originalLoad = Module._load;
const state = { user: null, hashed: [], verified: [], reset: null };
const fakeUtil = {
  ipOf: () => '127.0.0.1',
  isIpBanned: async () => false,
  lower: (value) => String(value ?? '').trim().toLowerCase(),
  body: (req) => req.body || {},
  hasReservedWord: () => false,
  cmd: async (command, key) => {
    if (command === 'INCR') return 1;
    if (command === 'GET' && String(key).startsWith('email:')) return state.user?.username || null;
    return 'OK';
  },
  getUser: async (username) => state.user?.username === username ? state.user : null,
  saveUser: async (user) => { state.user = user; },
  getJson: async () => state.reset,
  hashPassword: (password) => { state.hashed.push(password); return { salt: 'salt', hash: 'hash' }; },
  verifyPassword: (password) => { state.verified.push(password); return true; },
  auth: async () => ({ user: state.user }),
  createSession: async () => 'session-token',
  publicUser: (user) => ({ username: user.username, role: user.role }),
  send: (res, status, body) => { res.statusCode = status; res.body = body; return body; },
  fail: (res, status, error, extra) => {
    res.statusCode = status;
    res.body = { ok: false, error, ...(extra || {}) };
    return res.body;
  },
};
Module._load = function (request, parent, isMain) {
  if (request === '../lib/util' && parent?.filename.endsWith('/server/api/auth.js')) return fakeUtil;
  return originalLoad.call(this, request, parent, isMain);
};
const handler = require('../api/auth');
Module._load = originalLoad;

function request(action, body) {
  const req = { method: 'POST', query: { action }, body, headers: {}, socket: {} };
  const res = {
    status(s) { this.statusCode = s; return this; },
    setHeader() { return this; },
    send(text) { this.body = JSON.parse(text); return this; },
  };
  return handler(req, res).then(() => res);
}

function resetState(user = null) {
  state.user = user;
  state.hashed = [];
  state.verified = [];
  state.reset = null;
}

test('registration hashes the exact password, including outer spaces and mixed case', async () => {
  const previousOwner = process.env.OWNER_USERNAME;
  delete process.env.OWNER_USERNAME;
  resetState();
  try {
    const password = ' AbC 123 ';
    const res = await request('register', { username: 'caseuser', password });
    assert.equal(res.statusCode, 200);
    assert.deepEqual(state.hashed, [password]);
  } finally {
    if (previousOwner === undefined) delete process.env.OWNER_USERNAME;
    else process.env.OWNER_USERNAME = previousOwner;
  }
});

test('login verifies the exact password without trimming or case folding', async () => {
  const password = ' AbC 123 ';
  resetState({ username: 'caseuser', salt: 'salt', passHash: 'hash', ver: 0, ips: [] });
  const res = await request('login', { identifier: 'caseuser', password });
  assert.equal(res.statusCode, 200);
  assert.deepEqual(state.verified, [password]);
});

test('password change verifies old input and hashes the exact new password', async () => {
  const oldPassword = ' Old 456 ';
  const newPassword = ' New 789 ';
  resetState({ username: 'caseuser', salt: 'salt', passHash: 'hash', ver: 0, ips: [], mustChange: false });
  const res = await request('password', { oldPassword, newPassword });
  assert.equal(res.statusCode, 200);
  assert.deepEqual(state.verified, [oldPassword]);
  assert.deepEqual(state.hashed, [newPassword]);
});

test('password reset hashes the exact new password', async () => {
  const envKeys = ['KV_REST_API_TOKEN', 'RESEND_API_KEY', 'EMAIL_FROM'];
  const previous = Object.fromEntries(envKeys.map((key) => [key, process.env[key]]));
  process.env.KV_REST_API_TOKEN = 'auth-test-secret';
  process.env.RESEND_API_KEY = 'test-key';
  process.env.EMAIL_FROM = 'test@example.com';
  const password = ' Reset 123 ';
  const code = '012345';
  const username = 'caseuser';
  const email = 'case@example.com';
  resetState({ username, email, emailVerified: true, salt: 'salt', passHash: 'hash', ver: 0 });
  state.reset = {
    digest: crypto.createHmac('sha256', process.env.KV_REST_API_TOKEN)
      .update(`reset\0${username}\0${code}`).digest('hex'),
    tries: 0,
  };
  try {
    const res = await request('password.reset', { email, code, newPassword: password });
    assert.equal(res.statusCode, 200);
    assert.deepEqual(state.hashed, [password]);
  } finally {
    for (const key of envKeys) {
      if (previous[key] === undefined) delete process.env[key];
      else process.env[key] = previous[key];
    }
  }
});
