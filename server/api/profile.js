const U = require('../lib/util');
const { cmd } = U;

const ICONS = ['none', 'star', 'crown', 'music', 'heart', 'fire', 'bolt', 'diamond', 'shield', 'leaf'];
const SHAPES = ['pill', 'round', 'tag', 'outline'];
const COLOR = /^#[0-9a-fA-F]{6}$/;

module.exports = async (req, res) => {
  try {
    const a = await U.auth(req, res); if (!a) return;
    if (req.method === 'GET') {
      if (req.query.action === 'members') {
        const names = await cmd('SMEMBERS', 'users');
        const users = [];
        for (const n of names) { const user = await U.getUser(n); if (user && !user.banned && !user.archivedAt && !user.deletedAt) users.push(U.publicUser(user)); }
        users.sort((x, y) => String(x.displayName).localeCompare(String(y.displayName)));
        return U.send(res, 200, { ok: true, users });
      }
      if (req.query.search) {
        const q = U.lower(req.query.search).slice(0, 20);
        if (q.length < 2) return U.send(res, 200, { ok: true, users: [] });
        const all = await cmd('SMEMBERS', 'users');
        const names = all.filter((n) => n.includes(q) && n !== a.user.username).slice(0, 10);
        const users = [];
        for (const n of names) users.push(U.publicUser(await U.getUser(n)));
        return U.send(res, 200, { ok: true, users });
      }
      const u = await U.getUser(req.query.u || a.user.username);
      if (!u || u.banned) return U.fail(res, 404, 'Pengguna tidak ditemukan');
      return U.send(res, 200, { ok: true, user: U.publicUser(u, { full: u.username === a.user.username }) });
    }
    if (req.method !== 'POST') return U.fail(res, 405, 'Method tidak didukung');
    const b = U.body(req); const u = a.user;
    const isStaffUp = (U.ROLE_RANK[u.role] || 0) >= 1;

    if (b.displayName !== undefined) {
      const n = String(b.displayName).trim();
      if (n.length < 1 || n.length > 24) return U.fail(res, 400, 'Nama tampilan 1-24 karakter.');
      if (!isStaffUp && U.hasReservedWord(n)) return U.fail(res, 400, 'Nama tidak boleh meniru Owner/Admin/Staff (dengan font atau huruf apa pun).', { code: 'reserved_name' });
      u.displayName = n;
    }
    if (b.bio !== undefined) u.bio = String(b.bio).slice(0, 160);
    for (const [field, listField, kind] of [['avatarUrl', 'avatarUrls', 'avatar'], ['bannerUrl', 'bannerUrls', 'banner']]) {
      if (b[listField] !== undefined || b[field] !== undefined) {
        const candidate = b[listField] !== undefined ? b[listField] : [b[field]];
        if (!Array.isArray(candidate) || candidate.length > 2 || candidate.some((v) => typeof v !== 'string' || (v !== '' && !U.allowedMediaUrl(v)))) return U.fail(res, 400, `URL ${kind} tidak valid`);
        const urls = [...new Set(candidate.filter(Boolean))];
        u[listField] = urls;
        u[field] = urls[0] || '';
      }
    }
    if (b.bannerType !== undefined) u.bannerType = b.bannerType === 'video' ? 'video' : 'image';
    if (b.title !== undefined) {
      if (b.title === null || b.title.text === '') u.title = null;
      else {
        const t = b.title; const text = String(t.text || '').trim();
        if (text.length < 1 || text.length > 16) return U.fail(res, 400, 'Title 1-16 karakter.');
        if (!isStaffUp && U.hasReservedWord(text)) return U.fail(res, 400, 'Title Owner/Admin/Staff dilarang untuk pengguna biasa, dengan font apa pun.', { code: 'reserved_title' });
        if (!COLOR.test(t.color || '')) return U.fail(res, 400, 'Warna title tidak valid');
        u.title = { text, color: t.color, shape: SHAPES.includes(t.shape) ? t.shape : 'pill', icon: ICONS.includes(t.icon) ? t.icon : 'none' };
      }
    }
    await U.saveUser(u);
    return U.send(res, 200, { ok: true, user: U.publicUser(u, { full: true }) });
  } catch (e) { return U.fail(res, 500, 'Server error: ' + e.message); }
};
