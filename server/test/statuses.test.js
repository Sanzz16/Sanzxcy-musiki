const { test } = require('node:test');
const assert = require('node:assert/strict');
const U = require('../lib/util');

test('statuses enforce owned uploaded photos, three-hour expiry, bounded feed, unique views and owner-only deletion', async () => {
  const saved = { cmd: U.cmd, auth: U.auth, getUser: U.getUser, allowedMediaUrl: U.allowedMediaUrl };
  const strings = new Map();
  const sets = new Map();
  const sorted = new Map();
  let user = { username: 'alice', displayName: 'Alice', role: 'user' };
  U.auth = async () => ({ user });
  U.getUser = async (name) => ({ username: name, displayName: name === 'alice' ? 'Alice' : 'Bob', role: 'user' });
  U.allowedMediaUrl = (value) => typeof value === 'string' && /^https:\/\/[a-z0-9-]+\.public\.blob\.vercel-storage\.com\//i.test(value);
  U.cmd = async (...args) => {
    const [command, ...rest] = args;
    if (command === 'GET') return strings.get(rest[0]) || null;
    if (command === 'SET') { strings.set(rest[0], rest[1]); return 'OK'; }
    if (command === 'DEL') { for (const key of rest) { strings.delete(key); sets.delete(key); } return 1; }
    if (command === 'ZREM') { const z = sorted.get(rest[0]); z?.delete(rest[1]); return 1; }
    if (command === 'ZREMRANGEBYSCORE') {
      const z = sorted.get(rest[0]) || new Map();
      for (const [id, score] of z) if (score <= Number(rest[2])) z.delete(id);
      return z.size;
    }
    if (command === 'ZREVRANGEBYSCORE') {
      const z = sorted.get(rest[0]) || new Map();
      const maxScore = rest[1] === '+inf' ? Number.POSITIVE_INFINITY : Number(rest[1]);
      const minScore = rest[2] === '-inf' ? Number.NEGATIVE_INFINITY : Number(rest[2]);
      return [...z.entries()].filter(([, score]) => score <= maxScore && score >= minScore)
        .sort((a, b) => b[1] - a[1]).slice(Number(rest[4]), Number(rest[4]) + Number(rest[5])).map(([id]) => id);
    }
    if (command === 'SCARD') return (sets.get(rest[0]) || new Set()).size;
    if (command === 'EVAL') {
      const script = rest[0];
      const numKeys = Number(rest[1]);
      const remaining = rest.slice(2);
      const keys = remaining.slice(0, numKeys);
      const argv = remaining.slice(numKeys);
      if (script.includes('ZREMRANGEBYSCORE') && script.includes('ZCARD')) {
        const z = sorted.get(keys[0]) || new Map();
        for (const [id, score] of z) if (score <= Number(argv[0])) z.delete(id);
        return z.size;
      }
      if (script.includes('local count = tonumber(redis.call(\'INCR\'')) {
        const rateKey = keys[0];
        const count = (Number(strings.get(rateKey)) || 0) + 1;
        if (count > Number(argv[1])) return 0;
        strings.set(rateKey, String(count));
        strings.set(keys[1], argv[2]);
        const z = sorted.get(keys[2]) || new Map();
        z.set(argv[5], Number(argv[4]));
        sorted.set(keys[2], z);
        return 1;
      }
      if (script.includes('redis.call(\'SADD\'')) {
        if (!strings.has(keys[0])) return [-1, 0];
        const set = sets.get(keys[1]) || new Set();
        const before = set.size;
        set.add(argv[0]);
        sets.set(keys[1], set);
        return [set.size > before ? 1 : 0, set.size];
      }
      if (script.includes("item.from ~= ARGV[1]")) {
        const raw = strings.get(keys[0]);
        if (!raw) return -1;
        const item = JSON.parse(raw);
        if (item.from !== argv[0]) return -2;
        strings.delete(keys[0]);
        sets.delete(keys[1]);
        sorted.get(keys[2])?.delete(argv[1]);
        return 1;
      }
      throw new Error(`Unexpected Redis script ${script}`);
    }
    throw new Error(`Unexpected Redis command: ${command}`);
  };

  const endpointPath = require.resolve('../api/statuses');
  delete require.cache[endpointPath];
  const endpoint = require('../api/statuses');
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
    const { uploadKey, TTL_SECONDS } = endpoint.constants;
    assert.equal(TTL_SECONDS, 10_800);
    const ownedUrl = 'https://upload.public.blob.vercel-storage.com/statusPhoto/alice-photo.jpg';
    strings.set(uploadKey(ownedUrl), 'alice');

    let response = await invoke('POST', { body: { action: 'create', photoUrl: 'https://other.public.blob.vercel-storage.com/foreign.jpg', caption: 'Nope' } });
    assert.equal(response.code, 403, 'arbitrary public blob URLs are rejected');
    response = await invoke('POST', { body: { action: 'create', photoUrl: ownedUrl, caption: 'Momen sore' } });
    assert.equal(response.code, 201);
    const status = response.body.status;
    assert.equal(status.from, 'alice');
    assert.equal(status.expiresAt - status.createdAt, 3 * 60 * 60 * 1000);
    assert.equal(status.author.username, 'alice');

    response = await invoke('GET');
    assert.equal(response.code, 200);
    assert.equal(response.body.statuses.length, 1);
    assert.equal(response.body.statuses[0].id, status.id);
    assert.equal(response.body.statuses[0].views, 0);
    response = await invoke('POST', { body: { action: 'view', id: status.id } });
    assert.deepEqual({ views: response.body.views, added: response.body.added }, { views: 0, added: false }, 'owners do not count as viewers of their own status');

    user = { username: 'bob', displayName: 'Bob', role: 'user' };
    response = await invoke('POST', { body: { action: 'view', id: status.id } });
    assert.deepEqual({ views: response.body.views, added: response.body.added }, { views: 1, added: true });
    response = await invoke('POST', { body: { action: 'view', id: status.id } });
    assert.deepEqual({ views: response.body.views, added: response.body.added }, { views: 1, added: false }, 'repeat views by the same account are idempotent');
    response = await invoke('GET');
    assert.equal(response.body.statuses[0].views, 0, 'view totals are visible only to the status owner');

    response = await invoke('DELETE', { query: { id: status.id } });
    assert.equal(response.code, 403, 'another user cannot delete a status');
    user = { username: 'alice', displayName: 'Alice', role: 'user' };
    response = await invoke('DELETE', { query: { id: status.id } });
    assert.equal(response.code, 200);
    assert.equal(strings.has(endpoint.constants.statusKey(status.id)), false);

    const expiredId = '00000000-0000-4000-8000-000000000001';
    strings.set(endpoint.constants.statusKey(expiredId), JSON.stringify({ id: expiredId, from: 'alice', expiresAt: Date.now() - 1 }));
    sorted.set('statuses:index', new Map([[expiredId, Date.now() - 1]]));
    response = await invoke('GET');
    assert.equal(response.body.statuses.length, 0, 'expired items are pruned and never returned');
    assert.equal(sorted.get('statuses:index').has(expiredId), false);
  } finally {
    U.cmd = saved.cmd;
    U.auth = saved.auth;
    U.getUser = saved.getUser;
    U.allowedMediaUrl = saved.allowedMediaUrl;
    delete require.cache[endpointPath];
  }
});
