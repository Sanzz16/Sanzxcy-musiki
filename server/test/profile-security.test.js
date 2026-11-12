'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const Module = require('node:module');
const originalLoad = Module._load;
const user = { username: 'victim', role: 'user', salt: 'server-salt', passHash: 'server-hash', ver: 4, displayName: 'Victim', bio: '' };
let saved = null;
const fakeUtil = {
  ROLE_RANK: { user: 0, staff: 1, admin: 2, owner: 3 },
  auth: async () => ({ user }),
  body: (req) => req.body || {},
  saveUser: async (value) => { saved = structuredClone(value); },
  publicUser: (value) => ({ username: value.username, displayName: value.displayName, bio: value.bio }),
  hasReservedWord: () => false,
  allowedMediaUrl: () => true,
  send: (res, status, body) => { res.statusCode = status; res.body = body; return body; },
  fail: (res, status, error) => { res.statusCode = status; res.body = { ok: false, error }; return res.body; },
};
Module._load = function (request, parent, isMain) {
  if (request === '../lib/util' && parent?.filename.endsWith('/server/api/profile.js')) return fakeUtil;
  return originalLoad.call(this, request, parent, isMain);
};
const handler = require('../api/profile');
Module._load = originalLoad;

test('profile HTTP update cannot overwrite server-owned password hash, salt, or session version', async () => {
  saved = null;
  const req = {
    method: 'POST',
    body: { displayName: 'New Name', salt: 'attacker-salt', passHash: 'attacker-hash', ver: 0, password: 'attacker-password' },
    headers: {}, query: {}, socket: { remoteAddress: '127.0.0.1' },
  };
  const res = {};
  await handler(req, res);
  assert.equal(res.statusCode, 200);
  assert.equal(saved.salt, 'server-salt');
  assert.equal(saved.passHash, 'server-hash');
  assert.equal(saved.ver, 4);
  assert.equal(saved.displayName, 'New Name');
  assert.doesNotMatch(JSON.stringify(res.body), /server-salt|server-hash|passHash|"salt"/);
});
