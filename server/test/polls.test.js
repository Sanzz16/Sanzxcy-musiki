'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const Module = require('node:module');
const originalLoad = Module._load;
const state = { role: 'owner', messages: [], seq: 0, user: 'pollowner' };
const util = {
  ROLE_RANK: { user: 0, staff: 1, admin: 2, owner: 3 },
  auth: async () => ({ user: { username: state.user, displayName: state.user, role: state.role } }),
  body: (req) => req.body || {},
  send: (res, status, value) => { res.statusCode = status; res.body = value; return value; },
  fail: (res, status, error, extra) => util.send(res, status, { ok: false, error, ...(extra || {}) }),
  mediaUrls: () => [], allowedMediaUrl: (url) => /^https:\/\/media\.example\.com\//.test(url),
  getJson: async () => null, lower: (v) => String(v || '').toLowerCase(),
};
function prune() { return state.messages.length; }
function evalUpdate(args) {
  const [id, action, user, value, cap] = args.slice(3);
  const index = state.messages.findIndex((raw) => JSON.parse(raw).id === Number(id));
  if (index < 0) return [-1, ''];
  const msg = JSON.parse(state.messages[index]);
  if (msg.type !== 'poll') return [-1, ''];
  const poll = msg.poll;
  if (action === 'close') {
    if (poll.closed) return [0, state.messages[index]];
    if (msg.from !== user) return [-2, state.messages[index]];
    poll.closed = true; poll.closedAt = Number(value);
  } else {
    if (poll.closed) return [-3, state.messages[index]];
    const option = Number(value);
    if (option < 1 || option > poll.options.length) return [-4, state.messages[index]];
    const prior = poll.votes.find((vote) => vote.user === user);
    if (prior) prior.option = option;
    else { if (poll.votes.length >= Number(cap)) return [-5, state.messages[index]]; poll.votes.push({ user, option }); }
  }
  state.messages[index] = JSON.stringify(msg);
  return [1, state.messages[index]];
}
util.cmd = async (...args) => {
  const [command, ...rest] = args;
  if (command === 'GET') return null;
  if (command === 'EVAL') return rest[2] === 'chat:global' ? (String(rest[0]).includes("msg.type == 'poll'") ? evalUpdate(rest) : prune()) : prune();
  if (command === 'LRANGE') return state.messages;
  if (command === 'INCR') return ++state.seq;
  if (command === 'RPUSH') { state.messages.push(rest[1]); return state.messages.length; }
  if (command === 'LTRIM') { state.messages = state.messages.slice(-400); return 'OK'; }
  if (command === 'EXPIRE') return 1;
  throw new Error(`Unexpected Redis command ${args.join(' ')}`);
};
Module._load = function (request, parent, isMain) {
  if (request === '../lib/util' && parent?.filename.endsWith('/server/api/chat.js')) return util;
  if (request === '../lib/moderation' && parent?.filename.endsWith('/server/api/chat.js')) return { checkMessage: () => ({ ok: true }) };
  return originalLoad.call(this, request, parent, isMain);
};
const chat = require('../api/chat');
Module._load = originalLoad;
function request(action, body, user = state.user) {
  state.user = user;
  const req = { method: 'POST', query: {}, body: { action, ...body }, headers: {} };
  const res = { status(code) { this.statusCode = code; return this; }, setHeader() {}, send(value) { this.body = JSON.parse(value); return this; } };
  return chat(req, res).then(() => res);
}
test('only owner can create a bounded poll with policy-approved optional HTTPS media', async () => {
  state.messages = []; state.seq = 0; state.role = 'user';
  let res = await request('createPoll', { question: 'Lunch?', options: ['Soup', 'Rice'] });
  assert.equal(res.statusCode, 403);
  state.role = 'owner';
  res = await request('createPoll', { question: 'Lunch?', options: ['Soup', 'Rice'], imageUrl: 'https://media.example.com/poll.jpg' });
  assert.equal(res.statusCode, 200);
  assert.equal(res.body.message.type, 'poll');
  assert.deepEqual(res.body.message.poll.votes, []);
  assert.equal(res.body.message.poll.imageUrl, 'https://media.example.com/poll.jpg');
  assert.equal((await request('createPoll', { question: 'Bad', options: ['only'] })).statusCode, 400);
  assert.equal((await request('createPoll', { question: 'Bad', options: ['a', 'b'], room: 'dm:alice:bob' })).statusCode, 400);
  assert.equal((await request('createPoll', { question: 'Bad', options: ['a', 'b'], imageUrl: 'http://media.example.com/a' })).statusCode, 400);
});

test('poll image URL remains present in the global chat feed for regular community viewers', async () => {
  state.messages = []; state.seq = 0; state.role = 'owner'; state.user = 'pollowner';
  const created = await request('createPoll', { question: 'Pilihan?', options: ['A', 'B'], imageUrl: 'https://media.example.com/poll.jpg' });
  assert.equal(created.statusCode, 200);

  state.role = 'user'; state.user = 'viewer';
  const originalLoad = Module._load;
  Module._load = function (request, parent, isMain) {
    if (request === '../lib/bot-settings' && parent?.filename.endsWith('/server/api/chat.js')) return { getSettings: async () => ({ spamEnabled: false, cooldownSeconds: 40 }) };
    return originalLoad.call(this, request, parent, isMain);
  };
  try {
    const res = { setHeader() {} };
    await chat({ method: 'GET', query: { room: 'global', after: '0' }, headers: {} }, res);
    assert.equal(res.statusCode, 200);
    assert.equal(res.body.messages[0].poll.imageUrl, 'https://media.example.com/poll.jpg');
  } finally {
    Module._load = originalLoad;
  }
});

test('votes are per-user and changeable, and closure is creator-only and final', async () => {
  state.messages = []; state.seq = 0; state.role = 'owner'; state.user = 'pollowner';
  const created = await request('createPoll', { question: 'Pick?', options: ['A', 'B'] });
  const id = created.body.message.id;
  let res = await request('votePoll', { id, option: 1 }, 'alice');
  assert.equal(res.statusCode, 200, JSON.stringify(res.body));
  res = await request('votePoll', { id, option: 2 }, 'alice');
  assert.equal(res.statusCode, 200);
  assert.deepEqual(res.body.message.poll.votes, [{ user: 'alice', option: 2 }]);
  assert.equal((await request('closePoll', { id }, 'alice')).statusCode, 403);
  assert.equal((await request('closePoll', { id }, 'pollowner')).statusCode, 200);
  assert.equal((await request('votePoll', { id, option: 1 }, 'bob')).statusCode, 409);
});
