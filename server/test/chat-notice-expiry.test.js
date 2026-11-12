'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const Module = require('node:module');
const originalLoad = Module._load;
const state = { rooms: [], audited: [] };
const fakeUtil = {
  cmd: async (...args) => {
    if (args[0] !== 'EVAL') throw new Error(`unexpected command: ${args.join(' ')}`);
    const key = args[3];
    if (key === 'auditlog') return 0;
    if (key !== 'chat:global') throw new Error(`unexpected prune key ${key}`);
    const cutoff = Number(args[4]); const keep = Number(args[5]); const now = Number(args[7]);
    state.rooms = state.rooms.filter((message) => {
      const m = JSON.parse(message);
      return Number(m.ts) > cutoff && !(m.moderationNotice === true && Number(m.expiresAt) <= now);
    }).slice(-keep);
    return state.rooms.length;
  },
};
Module._load = function (request, parent, isMain) {
  if (request === '../lib/util' && parent?.filename.endsWith('/server/api/chat.js')) return fakeUtil;
  return originalLoad.call(this, request, parent, isMain);
};
const chat = require('../api/chat');
Module._load = originalLoad;

test('room pruning expires moderation notices while preserving normal announcements', async () => {
  const now = Date.now();
  state.rooms = [
    JSON.stringify({ type: 'notice', moderationNotice: true, expiresAt: now - 1, ts: now, text: 'expired moderation' }),
    JSON.stringify({ type: 'notice', expiresAt: now - 1, ts: now, text: 'ordinary announcement' }),
    JSON.stringify({ type: 'notice', moderationNotice: true, expiresAt: now + 5000, ts: now, text: 'active moderation' }),
  ];
  assert.match(chat.PRUNE_ROOM_LUA, /msg\.moderationNotice == true/);
  assert.match(chat.PRUNE_ROOM_LUA, /msg\.expiresAt/);
  await chat.maintainChatRoom('global');
  const kept = state.rooms.map((raw) => JSON.parse(raw).text);
  assert.deepEqual(kept.sort(), ['active moderation', 'ordinary announcement']);
});
