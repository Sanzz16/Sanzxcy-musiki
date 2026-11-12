'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const Module = require('node:module');
const originalLoad = Module._load;
const originalFetch = global.fetch;
const state = { auth: true, user: 'alice', writes: [], blob: null, blobFail: false, stored: new Map(), zsets: new Map(), supabase: null, fetchCalls: [] };
const util = {
  auth: async (_req, res) => state.auth ? { user: { username: state.user, displayName: state.user === 'alice' ? 'Alice' : 'Bob' } } : (res.statusCode = 401, null),
  body: (req) => req.body || {},
  fail: (res, status, error, extra) => (res.statusCode = status, res.value = { ok: false, error, ...(extra || {}) }),
  send: (res, status, value) => (res.statusCode = status, res.value = value),
  setJson: async (key, value) => state.stored.set(key, value),
  getJson: async (key) => state.stored.get(key) || null,
  cmd: async (...args) => {
    state.writes.push(args);
    if (args[0] === 'ZREVRANGE') return state.zsets.get(args[1]) || [];
    if (args[0] === 'ZADD') {
      const list = state.zsets.get(args[1]) || [];
      if (!list.includes(args[3])) list.unshift(args[3]);
      state.zsets.set(args[1], list);
    }
    return 'OK';
  },
  supabaseStorageConfig: () => state.supabase,
};
Module._load = function (request, parent, isMain) {
  if (request === '../lib/util' && parent.filename.endsWith('/server/api/feedback.js')) return util;
  if (request === '@vercel/blob' && parent.filename.endsWith('/server/api/feedback.js')) return { put: async (...args) => { state.blob = args; if (state.blobFail) throw new Error('simulated blob failure'); return { url: 'https://files.public.blob.vercel-storage.com/f.mp4' }; } };
  return originalLoad.call(this, request, parent, isMain);
};
const handler = require('../api/feedback');
Module._load = originalLoad;
function request(method, body = {}, query = {}) {
  const res = { headers: {}, setHeader(k, v) { this.headers[k.toLowerCase()] = v; } };
  return Promise.resolve(handler({ method, query, body, headers: {} }, res)).then(() => res);
}
const png = Buffer.from([137,80,78,71,13,10,26,10,1,2,3]);
const base = { type: 'bug', title: 'Crash', description: 'Unexpected exit' };
test.beforeEach(() => {
  state.auth = true; state.user = 'alice'; state.writes = []; state.blob = null; state.blobFail = false; state.stored.clear(); state.zsets.clear();
  state.supabase = null; state.fetchCalls = [];
  delete process.env.BLOB_READ_WRITE_TOKEN;
  delete process.env.FEEDBACK_STORAGE_BUCKET;
  global.fetch = originalFetch;
});
test.after(() => { global.fetch = originalFetch; });

test('requires POST or authenticated own-history action before accepting reports', async () => {
  const method = await request('PUT');
  assert.equal(method.statusCode, 405);
  assert.equal(method.headers.allow, 'GET, POST');
  state.auth = false;
  const deniedPost = await request('POST', base);
  const deniedGet = await request('GET', {}, { action: 'mine' });
  assert.equal(deniedPost.statusCode, 401);
  assert.equal(deniedGet.statusCode, 401);
  assert.equal(state.stored.size, 0);
});
test('validates type and bounded text metadata', async () => {
  assert.equal((await request('POST', { ...base, type: 'other' })).statusCode, 400);
  assert.equal((await request('POST', { ...base, description: 'x'.repeat(2001) })).statusCode, 400);
  assert.equal(state.stored.size, 0);
});
test('media uses configured blob storage and indexes submissions without persisting base64', async () => {
  const body = { ...base, contentType: 'image/png', data: png.toString('base64') };
  assert.equal((await request('POST', body)).statusCode, 503);
  assert.equal(state.writes.length, 0);
  process.env.BLOB_READ_WRITE_TOKEN = 'test-token';
  const res = await request('POST', body);
  assert.equal(res.statusCode, 201);
  assert.equal(state.blob[0].startsWith('feedback/'), true);
  const record = [...state.stored.values()][0];
  assert.equal(record.attachment.url, 'https://files.public.blob.vercel-storage.com/f.mp4');
  assert.equal(record.attachment.size, png.length);
  assert.equal(JSON.stringify(record).includes(body.data), false);
  assert.equal(JSON.stringify(state.writes).includes(body.data), false);
  assert.ok(state.writes.some((args) => args[0] === 'ZADD' && args[1] === 'feedback:user:alice'));
});
test('rejects declared MIME that does not match file signature and files over 2 MiB', async () => {
  process.env.BLOB_READ_WRITE_TOKEN = 'test-token';
  const mismatch = await request('POST', { ...base, contentType: 'image/jpeg', data: png.toString('base64') });
  assert.equal(mismatch.statusCode, 400);
  const tooLarge = Buffer.alloc(2 * 1024 * 1024 + 1, 1);
  const oversized = await request('POST', { ...base, contentType: 'image/png', data: tooLarge.toString('base64') });
  assert.equal(oversized.statusCode, 413);
  assert.equal(state.blob, null);
});
test('GET mine returns only caller-owned feedback across every status and legacy data', async () => {
  const records = [
    { id: 'a', username: 'alice', title: 'New', status: 'new', createdAt: 100 },
    { id: 'b', username: 'alice', title: 'Working', status: 'processing', createdAt: 200 },
    { id: 'c', username: 'alice', title: 'Done', status: 'done', createdAt: 300 },
    { id: 'd', username: 'alice', title: 'Archived', status: 'archived', createdAt: 400 },
    { id: 'x', username: 'bob', title: 'Private', status: 'new', createdAt: 500 },
  ];
  records.forEach((r) => state.stored.set(`feedback:${r.id}`, r));
  state.zsets.set('feedback:user:alice', ['a', 'b']);
  state.zsets.set('feedback:all', ['x', 'd', 'c', 'b', 'a']);
  const res = await request('GET', {}, { action: 'mine' });
  assert.equal(res.statusCode, 200);
  assert.deepEqual(res.value.feedback.map((f) => f.id), ['d', 'c', 'b', 'a']);
  assert.deepEqual(new Set(res.value.feedback.map((f) => f.status)), new Set(['new', 'processing', 'done', 'archived']));
  assert.equal(res.value.feedback.some((f) => f.username === 'bob'), false);
});
test('Supabase fallback uses a dedicated feedback bucket and does not expose service credentials', async () => {
  state.supabase = { url: 'https://example.supabase.co', key: 'sb_secret_test-only', bucket: 'community-media' };
  state.blobFail = true;
  process.env.BLOB_READ_WRITE_TOKEN = 'configured-but-failing-blob';
  global.fetch = async (url, options = {}) => {
    state.fetchCalls.push({ url: String(url), options });
    if (String(url).endsWith('/storage/v1/bucket') && options.method === 'POST') return { ok: false, status: 409, json: async () => ({}) };
    if (String(url).endsWith('/storage/v1/bucket/feedback-media') && options.method === undefined) return { ok: true, status: 200, json: async () => ({ public: true }) };
    return { ok: true, status: 200, json: async () => ({}) };
  };
  const res = await request('POST', { ...base, contentType: 'image/png', data: png.toString('base64') });
  assert.equal(res.statusCode, 201);
  const record = [...state.stored.values()][0];
  assert.match(record.attachment.url, /^https:\/\/example\.supabase\.co\/storage\/v1\/object\/public\/feedback-media\/feedback\//);
  assert.ok(state.fetchCalls.some((c) => c.options.method === 'POST' && String(c.url).endsWith('/storage/v1/bucket') && JSON.parse(c.options.body).id === 'feedback-media'));
  assert.ok(state.fetchCalls.some((c) => c.options.method === 'POST' && c.options.headers.apikey === 'sb_secret_test-only'));
  assert.equal(state.fetchCalls.some((c) => c.options.method === 'PATCH'), false);
  assert.ok(state.fetchCalls.every((c) => !JSON.stringify(c.options).includes('Bearer sb_secret_test-only')));
  assert.equal(JSON.stringify(record).includes('sb_secret_test-only'), false);
});
test('does not expose a pre-existing private feedback bucket', async () => {
  state.supabase = { url: 'https://example.supabase.co', key: 'service-key', bucket: 'community-media' };
  global.fetch = async (url, options = {}) => {
    state.fetchCalls.push({ url: String(url), options });
    if (String(url).endsWith('/storage/v1/bucket') && options.method === 'POST') return { ok: false, status: 409, json: async () => ({}) };
    if (String(url).endsWith('/storage/v1/bucket/feedback-media')) return { ok: true, status: 200, json: async () => ({ public: false }) };
    return { ok: true, status: 200, json: async () => ({}) };
  };
  const res = await request('POST', { ...base, contentType: 'image/png', data: png.toString('base64') });
  assert.equal(res.statusCode, 502);
  assert.equal(state.stored.size, 0);
  assert.equal(state.fetchCalls.some((c) => c.options.method === 'PATCH'), false);
});
test('missing blob and Supabase storage fails explicitly without claiming success', async () => {
  const res = await request('POST', { ...base, contentType: 'image/png', data: png.toString('base64') });
  assert.equal(res.statusCode, 503);
  assert.equal(res.value.code, 'attachment_storage_unconfigured');
  assert.match(res.value.error, /Hapus lampiran/);
  assert.equal(state.stored.size, 0);
});
