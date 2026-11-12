'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const U = require('../lib/util');

test('password hash menggunakan salt acak dan hanya hash yang memverifikasi password tepat', () => {
  const first = U.hashPassword(' kata sandi yang benar ');
  const second = U.hashPassword(' kata sandi yang benar ');
  assert.notEqual(first.salt, second.salt);
  assert.notEqual(first.hash, second.hash);
  assert.equal(U.verifyPassword(' kata sandi yang benar ', first.salt, first.hash), true);
  assert.equal(U.verifyPassword('kata sandi yang benar', first.salt, first.hash), false);
});

test('serialisasi profil tidak mengungkapkan salt, passHash, atau versi sesi', () => {
  const user = {
    username: 'testuser', displayName: 'Test User', role: 'user', joinedAt: 1,
    salt: 'private-salt', passHash: 'private-hash', ver: 7,
  };
  const serialized = JSON.stringify(U.publicUser(user, { full: true }));
  assert.doesNotMatch(serialized, /private-salt|private-hash|"salt"|"passHash"|"ver"/);
});
