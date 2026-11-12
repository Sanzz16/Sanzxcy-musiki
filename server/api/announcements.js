'use strict';

const U = require('../lib/util');

module.exports = async (req, res) => {
  try {
    if (req.method !== 'GET') {
      res.setHeader('Allow', 'GET');
      return U.fail(res, 405, 'Method tidak didukung');
    }
    const auth = await U.auth(req, res);
    if (!auth) return;
    const rawSince = Number(req.query.since || 0);
    const since = Number.isFinite(rawSince) && rawSince > 0 ? rawSince : 0;
    const stored = await U.getJson('app:announcements');
    const announcements = (Array.isArray(stored) ? stored : [])
      .filter((item) => item && !item.deletedAt && Number(item.updatedAt || item.createdAt) > since)
      .sort((a, b) => Number(b.updatedAt || b.createdAt) - Number(a.updatedAt || a.createdAt))
      .slice(0, 100);
    return U.send(res, 200, { ok: true, announcements, serverTime: Date.now() });
  } catch (error) {
    return U.fail(res, 500, `Server error: ${error.message}`);
  }
};
