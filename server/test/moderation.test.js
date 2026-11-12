'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { checkMessage } = require('../lib/moderation');

const disabled = { rudeEnabled: false, linksEnabled: false, promotionEnabled: false, spamEnabled: false, extraBlockedWords: [] };

test('profanity is blocked in both global and DM rooms by default', () => {
  for (const global of [true, false]) {
    const result = checkMessage('Kamu goblok', { global });
    assert.equal(result.ok, false);
    assert.match(result.reason, /kasar|hinaan/i);
  }
});

test('TikTok is blocked in global chat by default, including ordinary mention', () => {
  const result = checkMessage('follow TikTok aku', { global: true });
  assert.equal(result.ok, false);
  assert.match(result.reason, /TikTok/i);
});

test('links are blocked in global chat by default', () => {
  for (const text of ['kunjungi https://example.com', 'link ada di example.co', 't.me/contoh']) {
    const result = checkMessage(text, { global: true });
    assert.equal(result.ok, false, text);
    assert.match(result.reason, /Link/i);
  }
});

test('promotional messages are blocked in global chat by default', () => {
  for (const text of ['Jualan akun murah', 'open order sekarang', 'harga promo']) {
    const result = checkMessage(text, { global: true });
    assert.equal(result.ok, false, text);
    assert.match(result.reason, /Promo|jualan/i);
  }
});

test('link and promo restrictions remain global-only by default', () => {
  assert.equal(checkMessage('kunjungi https://example.com', { global: false }).ok, true);
  assert.equal(checkMessage('open order sekarang', { global: false }).ok, true);
});

test('policy toggles disable their category without disabling severe SARA detection', () => {
  assert.equal(checkMessage('Kamu goblok', { global: true, policy: disabled }).ok, true);
  assert.equal(checkMessage('https://example.com', { global: true, policy: disabled }).ok, true);
  assert.equal(checkMessage('open order sekarang', { global: true, policy: disabled }).ok, true);
  assert.equal(checkMessage('hahahahahahahaha', { global: true, policy: disabled }).ok, true);
  const severe = checkMessage('Islam sampah', { global: true, policy: disabled });
  assert.equal(severe.ok, false);
  assert.equal(severe.severe, true);
});

test('custom blocked words apply in global and DM rooms independently of category toggles', () => {
  for (const global of [true, false]) {
    const result = checkMessage('hindari kata rahasia ini', { global, policy: { ...disabled, extraBlockedWords: ['rahasia'] } });
    assert.equal(result.ok, false);
    assert.match(result.reason, /Kata yang diblokir/);
  }
});
