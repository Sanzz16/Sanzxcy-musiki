'use strict';

const U = require('./util');

const KEY = 'community:bot-settings';
const DEFAULTS = Object.freeze({
  rudeEnabled: true,
  linksEnabled: true,
  promotionEnabled: true,
  spamEnabled: true,
  extraBlockedWords: Object.freeze([]),
  cooldownSeconds: 40,
  spamMuteSeconds: 80,
  warningLimit: 3,
  warningMuteMinutes: 10080,
  noticeSeconds: 10,
});
const BOOLS = ['rudeEnabled', 'linksEnabled', 'promotionEnabled', 'spamEnabled'];
const INTS = {
  cooldownSeconds: [5, 300],
  spamMuteSeconds: [10, 86400],
  warningLimit: [1, 10],
  warningMuteMinutes: [1, 43200],
  noticeSeconds: [1, 60],
};
const FIELDS = [...BOOLS, 'extraBlockedWords', ...Object.keys(INTS)];

function validateSettings(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return { ok: false, error: 'Pengaturan bot harus berupa objek.' };
  const keys = Object.keys(value);
  if (keys.length !== FIELDS.length || FIELDS.some((field) => !Object.prototype.hasOwnProperty.call(value, field))) {
    return { ok: false, error: 'Skema pengaturan bot tidak lengkap atau berisi field yang tidak dikenal.' };
  }
  for (const field of BOOLS) {
    if (typeof value[field] !== 'boolean') return { ok: false, error: `${field} harus berupa boolean.` };
  }
  if (!Array.isArray(value.extraBlockedWords) || value.extraBlockedWords.length > 100) {
    return { ok: false, error: 'extraBlockedWords harus berupa daftar maksimal 100 kata.' };
  }
  const words = [];
  for (const item of value.extraBlockedWords) {
    if (typeof item !== 'string' || item.trim().length < 1 || item.trim().length > 60) {
      return { ok: false, error: 'Setiap kata tambahan harus berisi 1–60 karakter.' };
    }
    const word = item.trim();
    if (!words.some((existing) => existing.toLowerCase() === word.toLowerCase())) words.push(word);
  }
  for (const [field, [min, max]] of Object.entries(INTS)) {
    if (!Number.isSafeInteger(value[field]) || value[field] < min || value[field] > max) {
      return { ok: false, error: `${field} harus bilangan bulat ${min}–${max}.` };
    }
  }
  return { ok: true, settings: { ...value, extraBlockedWords: words } };
}

async function getSettings() {
  const saved = await U.getJson(KEY);
  const checked = validateSettings(saved);
  return checked.ok ? checked.settings : { ...DEFAULTS, extraBlockedWords: [] };
}

async function saveSettings(value) {
  const checked = validateSettings(value);
  if (!checked.ok) return checked;
  await U.setJson(KEY, checked.settings);
  return checked;
}

module.exports = { KEY, DEFAULTS, FIELDS, validateSettings, getSettings, saveSettings };
