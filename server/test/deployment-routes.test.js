'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const config = JSON.parse(fs.readFileSync(path.join(root, 'vercel.json'), 'utf8'));
const clientApi = fs.readFileSync(path.resolve(root, '../app/src/main/kotlin/com/metrolist/music/community/CommunityApi.kt'), 'utf8');

test('community client routes resolve to deployed Vercel functions', () => {
  assert.ok(config.functions['api/*.js'], 'Vercel must deploy first-level api/*.js handlers');
  const clientEndpoints = ['upload', 'owner', 'announcements', 'chat', 'listen', 'statuses', 'stickers'];
  for (const endpoint of clientEndpoints) {
    assert.ok(clientApi.includes(`"${endpoint}"`), `Android client should use /api/${endpoint}`);
    assert.ok(fs.existsSync(path.join(root, 'api', `${endpoint}.js`)), `/api/${endpoint} must exist in the deployed server root`);
  }
  const apiHeader = config.headers.find((header) => header.source === '/api/:path*');
  assert.ok(apiHeader, 'API security headers must match every /api/* route');
  assert.ok(!config.rewrites.some((rewrite) => rewrite.source.startsWith('/api/')), 'API routes must not be shadowed by static rewrites');
});
