'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const Module = require('node:module');
const originalLoad = Module._load;
const state = { invites: new Map(), pending: new Set(), inbox: new Map(), current: null };
const fakeUtil = {
  auth: async () => ({ user: state.current }),
  body: (req) => req.body || {},
  lower: (value) => String(value || '').trim().toLowerCase(),
  getUser: async (name) => name === 'bob' ? { username: 'bob' } : null,
  getJson: async (key) => state.invites.get(key.slice('invite:'.length)) || null,
  cmd: async (...args) => {
    if (args[0] === 'EVAL' && args[2] === '4') {
      const [, , , pendingKey, inviteKey, inboxKey, sentKey, id, , json, , max] = args;
      if (state.pending.has(pendingKey)) return 0;
      state.pending.add(pendingKey);
      const invite = JSON.parse(json); state.invites.set(id, invite);
      for (const key of [inboxKey, sentKey]) {
        const list = state.inbox.get(key) || []; list.unshift(id); state.inbox.set(key, list.slice(0, Number(max)));
      }
      return 1;
    }
    if (args[0] === 'EVAL' && args[2] === '1') {
      const [, , , key, recipient, status, ,] = args;
      const invite = state.invites.get(key.slice('invite:'.length));
      if (!invite) return [0, ''];
      if (invite.to !== recipient) return [-1, JSON.stringify(invite)];
      if (invite.status === status) return [2, JSON.stringify(invite)];
      if (invite.status !== 'pending') return [-2, JSON.stringify(invite)];
      invite.status = status; invite.respondedAt = Date.now();
      return [1, JSON.stringify(invite)];
    }
    if (args[0] === 'LRANGE') return state.inbox.get(args[1]) || [];
    throw new Error(`Unexpected Redis command: ${args.join(' ')}`);
  },
  send: (res, status, body) => { res.statusCode = status; res.body = body; return body; },
  fail: (res, status, error, extra) => { res.statusCode = status; res.body = { ok: false, error, ...(extra || {}) }; return res.body; },
};
Module._load = function (request, parent, isMain) {
  if (request === '../lib/util' && parent?.filename.endsWith('/server/api/invites.js')) return fakeUtil;
  return originalLoad.call(this, request, parent, isMain);
};
const handler = require('../api/invites');
Module._load = originalLoad;
const request = (method, body = {}, query = {}) => ({ method, body, query, headers: {} });
const response = () => ({});

test.beforeEach(() => {
  state.invites.clear(); state.pending.clear(); state.inbox.clear(); state.current = { username: 'alice', displayName: 'Alice' };
});

test('creation derives sender from session and creates a recipient inbox item', async () => {
  const res = response();
  await handler(request('POST', { action: 'create', username: 'BOB', from: 'attacker' }), res);
  assert.equal(res.statusCode, 201);
  assert.equal(res.body.invite.from, 'alice');
  assert.equal(res.body.invite.to, 'bob');
  assert.deepEqual(state.inbox.get('invites:inbox:bob'), [res.body.invite.id]);
  assert.deepEqual(state.inbox.get('invites:sent:alice'), [res.body.invite.id]);
  assert.equal(res.body.invite.status, 'pending');
});

test('rejects self-invites and a duplicate pending invite', async () => {
  let res = response();
  await handler(request('POST', { username: 'alice' }), res);
  assert.equal(res.statusCode, 400);
  res = response();
  await handler(request('POST', { username: 'bob' }), res);
  assert.equal(res.statusCode, 201);
  res = response();
  await handler(request('POST', { username: 'bob' }), res);
  assert.equal(res.statusCode, 409);
});

test('inbox exposes only invites addressed to the authenticated recipient', async () => {
  state.current = { username: 'alice' };
  const id = '00000000-0000-4000-8000-000000000001';
  state.invites.set(id, { id, from: 'mallory', to: 'bob', status: 'pending', createdAt: Date.now(), expiresAt: Date.now() + 10000 });
  state.inbox.set('invites:inbox:bob', [id]);
  let res = response(); await handler(request('GET'), res);
  assert.deepEqual(res.body.invites, []);
  state.current = { username: 'bob' };
  res = response(); await handler(request('GET'), res);
  assert.equal(res.body.invites.length, 1);
  assert.equal(res.body.invites[0].from, 'mallory');
});

test('outgoing status feed only exposes invites created by the authenticated sender', async () => {
  const owned = '00000000-0000-4000-8000-000000000003';
  const foreign = '00000000-0000-4000-8000-000000000004';
  const expiresAt = Date.now() + 10000;
  state.invites.set(owned, { id: owned, from: 'alice', to: 'bob', status: 'accepted', createdAt: Date.now(), expiresAt });
  state.invites.set(foreign, { id: foreign, from: 'mallory', to: 'bob', status: 'declined', createdAt: Date.now(), expiresAt });
  state.inbox.set('invites:sent:alice', [owned, foreign]);
  const res = response();
  await handler(request('GET'), res);
  assert.deepEqual(res.body.sent.map((invite) => invite.id), [owned]);
  assert.equal(res.body.sent[0].status, 'accepted');
});

test('only recipient can accept; repeated accept is idempotent and opposite action conflicts', async () => {
  const id = '00000000-0000-4000-8000-000000000002';
  state.invites.set(id, { id, from: 'alice', to: 'bob', status: 'pending', createdAt: Date.now(), expiresAt: Date.now() + 10000 });
  let res = response(); await handler(request('POST', { action: 'accept', id }), res);
  assert.equal(res.statusCode, 404);
  state.current = { username: 'bob' };
  res = response(); await handler(request('POST', { action: 'accept', id }), res);
  assert.equal(res.statusCode, 200);
  res = response(); await handler(request('POST', { action: 'accept', id }), res);
  assert.equal(res.statusCode, 200);
  assert.equal(res.body.unchanged, true);
  res = response(); await handler(request('POST', { action: 'decline', id }), res);
  assert.equal(res.statusCode, 409);
});
