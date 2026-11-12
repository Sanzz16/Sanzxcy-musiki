const U = require('../lib/util');
const { cmd } = U;
const BLOB_HOST = /^https:\/\/[a-z0-9-]+\.public\.blob\.vercel-storage\.com\//i;
const ADD_FAVORITE_LUA = `
  local items = redis.call('LRANGE', KEYS[1], 0, -1)
  for _, item in ipairs(items) do
    if item == ARGV[1] then return 0 end
  end
  if #items >= tonumber(ARGV[2]) then return -1 end
  redis.call('LPUSH', KEYS[1], ARGV[1])
  return 1
`;

module.exports = async (req, res) => {
  try {
    const a = await U.auth(req, res); if (!a) return;
    const key = `stk:${a.user.username}`;
    const favoritesKey = `stkfav:${a.user.username}`;
    if (req.method === 'GET') {
      const [list, favorites] = await Promise.all([
        cmd('LRANGE', key, '0', '-1'),
        cmd('LRANGE', favoritesKey, '0', '-1'),
      ]);
      return U.send(res, 200, { ok: true, stickers: list, favorites });
    }
    if (req.method === 'POST') {
      const body = U.body(req);
      const url = String(body.url || '');
      if (!BLOB_HOST.test(url)) return U.fail(res, 400, 'URL stiker tidak valid');
      if (body.action === 'favorite') {
        const result = Number(await cmd('EVAL', ADD_FAVORITE_LUA, '1', favoritesKey, url, '60'));
        if (result < 0) return U.fail(res, 400, 'Maksimal 60 stiker favorit.');
        return U.send(res, 200, { ok: true, favorites: true });
      }
      if ((await cmd('LLEN', key)) >= 60) return U.fail(res, 400, 'Maksimal 60 stiker.');
      await cmd('LPUSH', key, url);
      return U.send(res, 200, { ok: true });
    }
    if (req.method === 'DELETE') {
      const url = String(req.query.url || '');
      if (req.query.action === 'favorite') {
        await cmd('LREM', favoritesKey, '0', url);
      } else {
        await cmd('LREM', key, '1', url);
      }
      return U.send(res, 200, { ok: true });
    }
    return U.fail(res, 405, 'Method tidak didukung');
  } catch (e) { return U.fail(res, 500, 'Server error: ' + e.message); }
};
