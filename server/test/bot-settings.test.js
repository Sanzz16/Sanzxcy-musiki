'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { DEFAULTS, FIELDS, validateSettings } = require('../lib/bot-settings');

const valid = () => ({ ...DEFAULTS, extraBlockedWords: [] });

test('bot settings expose the required defaults and exact schema', () => {
  assert.deepEqual({ ...DEFAULTS, extraBlockedWords: [] }, {
    rudeEnabled: true, linksEnabled: true, promotionEnabled: true, spamEnabled: true,
    extraBlockedWords: [], cooldownSeconds: 40, spamMuteSeconds: 80, warningLimit: 3,
    warningMuteMinutes: 10080, noticeSeconds: 10,
  });
  assert.equal(FIELDS.length, 10);
});

test('settings validation accepts and de-duplicates custom words', () => {
  const input = { ...valid(), extraBlockedWords: ['  frasa khusus ', 'Kata', 'kata'] };
  const result = validateSettings(input);
  assert.equal(result.ok, true);
  assert.deepEqual(result.settings.extraBlockedWords, ['frasa khusus', 'Kata']);
});

test('settings validation rejects missing fields, wrong types, and out-of-range values', () => {
  assert.equal(validateSettings({ ...valid(), noticeSeconds: undefined }).ok, false);
  assert.equal(validateSettings({ ...valid(), extra: true }).ok, false);
  assert.equal(validateSettings({ ...valid(), spamEnabled: 1 }).ok, false);
  for (const [field, value] of [['cooldownSeconds', 4], ['spamMuteSeconds', 86401], ['warningLimit', 1.5], ['warningMuteMinutes', 0], ['noticeSeconds', 61]]) {
    assert.equal(validateSettings({ ...valid(), [field]: value }).ok, false, `${field} should reject ${value}`);
  }
  assert.equal(validateSettings({ ...valid(), extraBlockedWords: [''] }).ok, false);
  assert.equal(validateSettings({ ...valid(), extraBlockedWords: Array(101).fill('word') }).ok, false);
});
