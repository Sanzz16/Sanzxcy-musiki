const { test } = require('node:test');
const assert = require('node:assert/strict');
const U = require('../lib/util');

test('sticker favorites are separate, idempotent, and capped at 60', async () => {
  const lists = new Map([['stk:tester', ['https://custom.public.blob.vercel-storage.com/custom.png']]]);
  const savedCmd = U.cmd;
  const savedAuth = U.auth;
  U.auth = async () => ({ user: { username: 'tester' } });
  U.cmd = async (...args) => {
    const [command, , , key, value, limit] = args;
    if (command === 'LRANGE') return lists.get(args[1]) || [];
    if (command === 'EVAL') {
      const list = lists.get(key) || [];
      if (list.includes(value)) return 0;
      if (list.length >= Number(limit)) return -1;
      lists.set(key, [value, ...list]);
      return 1;
    }
    if (command === 'LLEN') return (lists.get(args[1]) || []).length;
    if (command === 'LPUSH') {
      lists.set(args[1], [args[2], ...(lists.get(args[1]) || [])]);
      return lists.get(args[1]).length;
    }
    if (command === 'LREM') {
      const name = args[1];
      const count = args[2];
      const item = args[3];
      const current = lists.get(name) || [];
      const remaining = count === '0' ? current.filter((entry) => entry !== item) : current.filter((entry, index) => entry !== item || index !== current.indexOf(item));
      lists.set(name, remaining);
      return current.length - remaining.length;
    }
    throw new Error(`Unexpected Redis command: ${command}`);
  };
  const endpointPath = require.resolve('../api/stickers');
  delete require.cache[endpointPath];
  const endpoint = require('../api/stickers');
  const invoke = async (method, { body = {}, query = {} } = {}) => {
    const res = {
      status(code) { this.code = code; return this; },
      setHeader() { return this; },
      send(value) { this.body = JSON.parse(value); return this; },
    };
    await endpoint({ method, body, query, headers: {} }, res);
    return res;
  };

  try {
    const favoriteUrl = (n) => `https://fav.public.blob.vercel-storage.com/${n}.png`;
    let response = await invoke('POST', { body: { action: 'favorite', url: favoriteUrl(0) } });
    assert.equal(response.code, 200);
    response = await invoke('POST', { body: { action: 'favorite', url: favoriteUrl(0) } });
    assert.equal(response.code, 200);
    assert.equal(lists.get('stkfav:tester').length, 1);

    for (let i = 1; i < 60; i++) {
      response = await invoke('POST', { body: { action: 'favorite', url: favoriteUrl(i) } });
      assert.equal(response.code, 200);
    }
    response = await invoke('POST', { body: { action: 'favorite', url: favoriteUrl(60) } });
    assert.equal(response.code, 400);
    response = await invoke('POST', { body: { action: 'favorite', url: favoriteUrl(0) } });
    assert.equal(response.code, 200, 're-saving an existing favorite remains idempotent at capacity');

    response = await invoke('GET');
    assert.deepEqual(response.body.stickers, ['https://custom.public.blob.vercel-storage.com/custom.png']);
    assert.equal(response.body.favorites.length, 60);
    response = await invoke('DELETE', { query: { action: 'favorite', url: favoriteUrl(0) } });
    assert.equal(response.code, 200);
    response = await invoke('DELETE', { query: { action: 'favorite', url: favoriteUrl(0) } });
    assert.equal(response.code, 200);
    assert.equal(lists.get('stkfav:tester').length, 59);
    assert.equal(lists.get('stk:tester').length, 1);
  } finally {
    U.cmd = savedCmd;
    U.auth = savedAuth;
    delete require.cache[endpointPath];
  }
});
