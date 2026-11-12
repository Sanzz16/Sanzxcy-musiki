'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const Module = require('node:module');
const originalLoad = Module._load;
const state = { role: 'owner', authCalls: 0, handleCalls: 0, captured: null };
const fakeUtil = {
  auth: async (req, res) => {
    state.authCalls++;
    if (!(req.headers.authorization || '').startsWith('Bearer ')) {
      res.statusCode = 401;
      res.body = { ok: false, error: 'Belum login.' };
      return null;
    }
    return { user: { username: 'testowner', role: state.role } };
  },
  body: (req) => req.body || {},
  send: (res, status, body) => { res.statusCode = status; res.body = body; return body; },
  fail: (res, status, error) => { res.statusCode = status; res.body = { ok: false, error }; return res.body; },
  setJson: async () => 'OK',
  getJson: async () => null,
  cmd: async () => 'OK',
};
Module._load = function (request, parent, isMain) {
  if (request === '../lib/util' && parent?.filename.endsWith('/server/api/apk-upload.js')) return fakeUtil;
  if (request === '@vercel/blob/client') return {
    handleUpload: async (config) => {
      state.handleCalls++;
      state.captured = config;
      const p = config.body.payload;
      await config.onBeforeGenerateToken(p.pathname, p.clientPayload, false);
      return { type: p.type, clientToken: 'client-token' };
    },
  };
  if (request === '@vercel/blob') return { del: async () => {} };
  return originalLoad.call(this, request, parent, isMain);
};
const handler = require('../api/apk-upload');
Module._load = originalLoad;

function request(method, body = {}, headers = {}) {
  const req = { method, body, headers, query: {}, socket: { remoteAddress: '127.0.0.1' } };
  const res = { headers: {}, setHeader(k, v) { this.headers[k.toLowerCase()] = v; }, status(s) { this.statusCode = s; return this; }, send(x) { this.body = JSON.parse(x); return this; } };
  return handler(req, res).then(() => res);
}
function clientPayload() {
  return {
    type: 'blob.generate-client-token',
    payload: {
      pathname: 'apk/staging/123e4567-e89b-12d3-a456-426614174000/Sanzxcy.apk',
      clientPayload: JSON.stringify({ pathname: 'apk/staging/123e4567-e89b-12d3-a456-426614174000/Sanzxcy.apk', versionName: '1.0.8', versionCode: 13, message: 'Release', actor: 'testowner' }),
    },
  };
}

test('APK route rejects unsupported methods before authentication', async () => {
  state.authCalls = 0;
  const res = await request('GET');
  assert.equal(res.statusCode, 405);
  assert.equal(res.headers.allow, 'POST');
  assert.equal(state.authCalls, 0);
});

test('only owner can request a Blob upload capability', async () => {
  state.role = 'admin';
  state.authCalls = 0;
  const calls = state.handleCalls;
  const res = await request('POST', clientPayload(), { authorization: 'Bearer session' });
  assert.equal(res.statusCode, 403);
  assert.equal(state.handleCalls, calls);
});

test('preflight reports missing Blob configuration to an authenticated owner without returning secrets', async () => {
  state.role = 'owner';
  const previous = process.env.BLOB_READ_WRITE_TOKEN;
  delete process.env.BLOB_READ_WRITE_TOKEN;
  try {
    const missing = await request('POST', { action: 'preflight' }, { authorization: 'Bearer session' });
    assert.equal(missing.statusCode, 503);
    assert.match(missing.body.error, /BLOB_READ_WRITE_TOKEN tidak dikonfigurasi/);

    process.env.BLOB_READ_WRITE_TOKEN = 'test-only-blob-secret';
    const ready = await request('POST', { action: 'preflight' }, { authorization: 'Bearer session' });
    assert.equal(ready.statusCode, 200);
    assert.deepEqual(ready.body, { ok: true });
    assert.doesNotMatch(JSON.stringify(ready.body), /test-only-blob-secret/);
  } finally {
    if (previous === undefined) delete process.env.BLOB_READ_WRITE_TOKEN;
    else process.env.BLOB_READ_WRITE_TOKEN = previous;
  }
});

test('preflight tetap hanya dapat diakses oleh owner yang terautentikasi', async () => {
  state.role = 'admin';
  const denied = await request('POST', { action: 'preflight' }, { authorization: 'Bearer session' });
  assert.equal(denied.statusCode, 403);
  const anonymous = await request('POST', { action: 'preflight' });
  assert.equal(anonymous.statusCode, 401);
});

test('owner upload capability limits content types, file size, path, and version metadata', async () => {
  state.role = 'owner';
  state.authCalls = 0;
  const previous = process.env.BLOB_READ_WRITE_TOKEN;
  process.env.BLOB_READ_WRITE_TOKEN = 'test-only-blob-secret';
  try {
    const res = await request('POST', clientPayload(), { authorization: 'Bearer session' });
    assert.equal(res.statusCode, 200);
    const token = await state.captured.onBeforeGenerateToken(clientPayload().payload.pathname, clientPayload().payload.clientPayload, true);
    assert.deepEqual(token.allowedContentTypes, ['application/vnd.android.package-archive', 'application/octet-stream']);
    assert.equal(token.maximumSizeInBytes, 120 * 1024 * 1024);
    assert.equal(token.addRandomSuffix, false);
    assert.equal(state.authCalls >= 2, true);
  } finally {
    if (previous === undefined) delete process.env.BLOB_READ_WRITE_TOKEN;
    else process.env.BLOB_READ_WRITE_TOKEN = previous;
  }
});
