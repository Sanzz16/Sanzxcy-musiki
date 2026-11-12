'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const Module = require('node:module');
const originalLoad = Module._load;
const state = { role: 'owner', keyOk: false, authCalls: 0, writes: 0 };
const settings = {
  rudeEnabled: true, linksEnabled: true, promotionEnabled: true, spamEnabled: true,
  extraBlockedWords: [], cooldownSeconds: 40, spamMuteSeconds: 80, warningLimit: 3,
  warningMuteMinutes: 10080, noticeSeconds: 10,
};
const fakeUtil = {
  ROLE_RANK: { user: 0, staff: 1, admin: 2, owner: 3 },
  ownerKeyOk: () => state.keyOk,
  auth: async (_req, res) => { state.authCalls++; return { user: { username: 'account', role: state.role } }; },
  body: (req) => req.body || {},
  send: (res, status, body) => { res.statusCode = status; res.body = body; return body; },
  fail: (res, status, error) => { res.statusCode = status; res.body = { ok: false, error }; return res.body; },
  cmd: async () => 'OK',
};
const fakeSettings = {
  getSettings: async () => structuredClone(settings),
  saveSettings: async (value) => {
    const keys = Object.keys(settings);
    if (!value || Object.keys(value).length !== keys.length || keys.some((key) => !(key in value))) return { ok: false, error: 'invalid settings schema' };
    if (!Number.isSafeInteger(value.noticeSeconds) || value.noticeSeconds < 1 || value.noticeSeconds > 60) return { ok: false, error: 'noticeSeconds out of range' };
    state.writes++;
    Object.assign(settings, structuredClone(value));
    return { ok: true, settings: structuredClone(settings) };
  },
};
Module._load = function (request, parent, isMain) {
  if (request === '../lib/util' && parent?.filename.endsWith('/server/api/owner.js')) return fakeUtil;
  if (request === '../lib/bot-settings' && parent?.filename.endsWith('/server/api/owner.js')) return fakeSettings;
  return originalLoad.call(this, request, parent, isMain);
};
const handler = require('../api/owner');
Module._load = originalLoad;

function request(method, action, body = {}) {
  const req = { method, query: { action }, body: { action, ...body }, headers: {} };
  const res = { status(s) { this.statusCode = s; return this; }, setHeader() { return this; }, send(text) { this.body = JSON.parse(text); return this; } };
  return handler(req, res).then(() => res);
}

test('staff and admin cannot read or write bot settings', async () => {
  for (const role of ['staff', 'admin']) {
    state.role = role;
    const read = await request('GET', 'bot.settings.get');
    assert.equal(read.statusCode, 403, role);
    const write = await request('POST', 'bot.settings.set', { settings: structuredClone(settings) });
    assert.equal(write.statusCode, 403, role);
  }
  assert.equal(state.writes, 0);
});

test('only exact owner role can access settings with a session', async () => {
  state.role = 'owner'; state.keyOk = false;
  const read = await request('GET', 'bot.settings.get');
  assert.equal(read.statusCode, 200, JSON.stringify(read.body));
  assert.deepEqual(read.body.settings, settings);
  const write = await request('POST', 'bot.settings.set', { settings: { ...settings, cooldownSeconds: 55 } });
  assert.equal(write.statusCode, 200);
  assert.equal(write.body.settings.cooldownSeconds, 55);
});

test('valid OWNER_KEY can access settings without a session and schema errors are rejected', async () => {
  state.keyOk = true;
  const before = state.authCalls;
  const read = await request('GET', 'bot.settings.get');
  assert.equal(read.statusCode, 200);
  assert.equal(state.authCalls, before);
  const invalid = await request('POST', 'bot.settings.set', { settings: { ...settings, noticeSeconds: 0 } });
  assert.equal(invalid.statusCode, 400);
  state.keyOk = false;
});
