const crypto = require('crypto');
const U = require('../lib/util');
const { cmd } = U;

const USER_RE = /^[a-z0-9_.]{3,20}$/;
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const CODE_TTL = 600;
const CODE_SECRET = () => process.env.KV_REST_API_TOKEN || process.env.UPSTASH_REDIS_REST_TOKEN || process.env.OWNER_KEY || '';

function validEmail(value) {
  const email = U.lower(value);
  return email.length <= 254 && EMAIL_RE.test(email) ? email : '';
}
function codeDigest(purpose, binding, code) {
  return crypto.createHmac('sha256', CODE_SECRET()).update(`${purpose}\0${binding}\0${String(code).trim()}`).digest('hex');
}
function safeEqualHex(a, b) {
  if (!/^[a-f0-9]{64}$/.test(String(a)) || !/^[a-f0-9]{64}$/.test(String(b))) return false;
  return crypto.timingSafeEqual(Buffer.from(a, 'hex'), Buffer.from(b, 'hex'));
}
function newCode() { return crypto.randomInt(0, 1_000_000).toString().padStart(6, '0'); }
async function rateAllowed(key, max, seconds) {
  const n = Number(await cmd('INCR', key));
  if (n === 1) await cmd('EXPIRE', key, String(seconds));
  return n <= max;
}
function mailConfigured() { return !!(process.env.RESEND_API_KEY && process.env.EMAIL_FROM); }
async function sendCodeEmail(to, code, purpose) {
  if (!mailConfigured()) return false;
  const subject = purpose === 'verify' ? 'Verifikasi email komunitas Sanzxcy' : 'Kode reset password Sanzxcy';
  const heading = purpose === 'verify' ? 'Verifikasi alamat email' : 'Reset password';
  const response = await fetch('https://api.resend.com/emails', {
    method: 'POST',
    signal: AbortSignal.timeout(8000),
    headers: { Authorization: `Bearer ${process.env.RESEND_API_KEY}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      from: process.env.EMAIL_FROM,
      to: [to],
      subject,
      html: `<div style="font-family:Arial,sans-serif;max-width:480px;margin:auto"><h2>${heading}</h2><p>Kode sekali pakai kamu:</p><p style="font-size:30px;font-weight:bold;letter-spacing:8px">${code}</p><p>Kode berlaku selama 10 menit. Jangan bagikan kode ini kepada siapa pun.</p><p>Jika kamu tidak meminta kode ini, abaikan email ini.</p></div>`,
    }),
  });
  return response.ok;
}
function genericResetMessage() { return 'Jika email tersebut terdaftar dan terverifikasi, kode akan dikirim.'; }

module.exports = async (req, res) => {
  try {
    const ip = U.ipOf(req);
    if (await U.isIpBanned(ip)) return U.fail(res, 403, 'IP kamu diblokir dari komunitas.', { code: 'ip_banned' });
    const action = req.query.action || '';

    if (req.method === 'GET' && action === 'me') {
      const a = await U.auth(req, res); if (!a) return;
      return U.send(res, 200, { ok: true, user: U.publicUser(a.user, { full: true }), mustChange: !!a.user.mustChange });
    }
    if (req.method !== 'POST') return U.fail(res, 405, 'Method tidak didukung');
    const b = U.body(req);

    if (action === 'register') {
      const username = U.lower(b.username); const password = String(b.password ?? '');
      if (!USER_RE.test(username)) return U.fail(res, 400, 'Username 3-20 karakter: huruf kecil, angka, titik, atau garis bawah.');
      if (password.length < 6 || password.length > 64) return U.fail(res, 400, 'Password minimal 6 karakter.');
      const isOwnerName = process.env.OWNER_USERNAME && U.lower(process.env.OWNER_USERNAME) === username;
      if (isOwnerName) return U.fail(res, 403, 'Username owner hanya dapat disediakan melalui panel owner atau OWNER_KEY.', { code: 'owner_registration_disabled' });
      if (U.hasReservedWord(username)) return U.fail(res, 400, 'Username tidak boleh memakai kata Owner/Admin/Staff.', { code: 'reserved_name' });
      const rl = Number(await cmd('INCR', `rl:reg:${ip}`)); if (rl === 1) await cmd('EXPIRE', `rl:reg:${ip}`, '86400');
      if (rl > 5) return U.fail(res, 429, 'Terlalu banyak pendaftaran dari jaringan ini. Coba lagi besok atau minta bantuan admin.');
      if (await U.getUser(username)) return U.fail(res, 409, 'Username sudah dipakai.');
      const { salt, hash } = U.hashPassword(password);
      const user = {
        username, nameLower: username, salt, passHash: hash, displayName: username, bio: '', avatarUrl: '', bannerUrl: '', bannerType: 'image',
        email: '', emailVerified: false, role: 'user', title: null, joinedAt: Date.now(), ips: [{ ip, first: Date.now(), last: Date.now() }], lastIp: ip,
        lastSeen: Date.now(), warns: 0, mutedUntil: 0, banned: false, ver: 0,
      };
      await U.saveUser(user);
      await cmd('SADD', 'users', username);
      await cmd('SADD', `ip:${ip}`, username);
      const token = await U.createSession(user);
      return U.send(res, 200, { ok: true, token, user: U.publicUser(user, { full: true }) });
    }

    if (action === 'login') {
      const rl = Number(await cmd('INCR', `rl:login:${ip}`)); if (rl === 1) await cmd('EXPIRE', `rl:login:${ip}`, '600');
      if (rl > 12) return U.fail(res, 429, 'Terlalu banyak percobaan login. Coba lagi dalam 10 menit.');
      const identifier = U.lower(b.identifier || b.username);
      let username = identifier;
      if (identifier.includes('@')) username = String(await cmd('GET', `email:${identifier}`) || '');
      const user = username ? await U.getUser(username) : null;
      if (!user || (identifier.includes('@') && (!user.emailVerified || U.lower(user.email) !== identifier)) || !U.verifyPassword(String(b.password ?? ''), user.salt, user.passHash)) {
        return U.fail(res, 401, 'Email/username atau password salah.');
      }
      if (user.archivedAt || user.deletedAt) return U.fail(res, 401, 'Email/username atau password salah.');
      if (user.banned) return U.fail(res, 403, 'Akun kamu diblokir permanen dari fitur komunitas.', { code: 'banned' });
      user.lastIp = ip; user.lastSeen = Date.now(); user.ips = user.ips || [];
      const known = user.ips.find((x) => x.ip === ip);
      if (known) known.last = Date.now(); else user.ips.push({ ip, first: Date.now(), last: Date.now() });
      if (user.ips.length > 30) user.ips = user.ips.slice(-30);
      await U.saveUser(user);
      await cmd('SADD', `ip:${ip}`, user.username);
      const token = await U.createSession(user);
      return U.send(res, 200, { ok: true, token, user: U.publicUser(user, { full: true }), mustChange: !!user.mustChange });
    }

    if (action === 'password.request') {
      if (!mailConfigured()) return U.fail(res, 503, 'Pemulihan email belum diaktifkan. Minta bantuan staf/admin untuk reset password.', { code: 'email_not_configured' });
      if (!(await rateAllowed(`rl:pwreset:ip:${ip}`, 5, 3600))) return U.fail(res, 429, 'Terlalu banyak permintaan kode. Coba lagi nanti.');
      const email = validEmail(b.email);
      if (email) {
        const mailKey = crypto.createHmac('sha256', CODE_SECRET()).update(email).digest('hex');
        if (await rateAllowed(`rl:pwreset:email:${mailKey}`, 3, 3600)) {
          const username = await cmd('GET', `email:${email}`);
          const user = username ? await U.getUser(username) : null;
          if (user && user.emailVerified && U.lower(user.email) === email) {
            const code = newCode();
            const saved = await sendCodeEmail(email, code, 'reset');
            if (saved) await cmd('SET', `pwreset:${username}`, JSON.stringify({ digest: codeDigest('reset', username, code), tries: 0 }), 'EX', String(CODE_TTL));
          }
        }
      }
      return U.send(res, 200, { ok: true, message: genericResetMessage() });
    }

    if (action === 'password.reset') {
      if (!mailConfigured()) return U.fail(res, 503, 'Pemulihan email belum diaktifkan. Minta bantuan staf/admin untuk reset password.', { code: 'email_not_configured' });
      const email = validEmail(b.email); const code = String(b.code || '').trim(); const newPassword = String(b.newPassword ?? '');
      if (!email || !/^\d{6}$/.test(code) || newPassword.length < 6 || newPassword.length > 64) return U.fail(res, 400, 'Email, kode, atau password baru tidak valid.');
      if (!(await rateAllowed(`rl:pwreset:verify:${ip}`, 10, 3600))) return U.fail(res, 429, 'Terlalu banyak percobaan kode. Coba lagi nanti.');
      const username = await cmd('GET', `email:${email}`);
      const user = username ? await U.getUser(username) : null;
      const key = username ? `pwreset:${username}` : '';
      const stored = key ? await U.getJson(key) : null;
      const candidate = username ? codeDigest('reset', username, code) : '';
      if (!user || !user.emailVerified || U.lower(user.email) !== email || !stored || !safeEqualHex(stored.digest, candidate)) {
        if (key && stored) {
          stored.tries = (stored.tries || 0) + 1;
          if (stored.tries >= 5) await cmd('DEL', key);
          else await cmd('SET', key, JSON.stringify(stored), 'EX', String(CODE_TTL));
        }
        return U.fail(res, 400, 'Kode salah atau sudah kedaluwarsa. Minta kode baru atau hubungi staf/admin.', { code: 'invalid_reset_code' });
      }
      const { salt, hash } = U.hashPassword(newPassword);
      user.salt = salt; user.passHash = hash; user.mustChange = false; user.ver = (user.ver || 0) + 1;
      await U.saveUser(user); await cmd('DEL', key);
      const token = await U.createSession(user);
      return U.send(res, 200, { ok: true, token, user: U.publicUser(user, { full: true }), mustChange: false });
    }

    if (action === 'email.request') {
      const a = await U.auth(req, res); if (!a) return;
      if (!mailConfigured()) return U.fail(res, 503, 'Verifikasi email belum diaktifkan di server.', { code: 'email_not_configured' });
      if (!(await rateAllowed(`rl:emailverify:ip:${ip}`, 8, 3600)) || !(await rateAllowed(`rl:emailverify:user:${a.user.username}`, 4, 3600))) {
        return U.fail(res, 429, 'Terlalu banyak permintaan kode. Coba lagi nanti.');
      }
      const email = validEmail(b.email);
      if (!email) return U.fail(res, 400, 'Alamat email tidak valid.');
      const owner = await cmd('GET', `email:${email}`);
      if (owner && owner !== a.user.username) return U.fail(res, 409, 'Email sudah dipakai akun lain.');
      if (a.user.emailVerified && U.lower(a.user.email) === email) return U.send(res, 200, { ok: true, verified: true, user: U.publicUser(a.user, { full: true }) });
      const code = newCode();
      if (!(await sendCodeEmail(email, code, 'verify'))) return U.fail(res, 502, 'Email belum berhasil dikirim. Coba lagi nanti.');
      const binding = a.user.username;
      await cmd('SET', `emailverify:${binding}`, JSON.stringify({ email, digest: codeDigest('verify', `${binding}:${email}`, code), tries: 0 }), 'EX', String(CODE_TTL));
      return U.send(res, 200, { ok: true, message: 'Kode verifikasi dikirim jika layanan email tersedia.' });
    }

    if (action === 'email.verify') {
      const a = await U.auth(req, res); if (!a) return;
      if (!(await rateAllowed(`rl:emailverify:attempt:${ip}`, 10, 3600))) return U.fail(res, 429, 'Terlalu banyak percobaan kode. Coba lagi nanti.');
      const email = validEmail(b.email); const code = String(b.code || '').trim();
      if (!email || !/^\d{6}$/.test(code)) return U.fail(res, 400, 'Email atau kode tidak valid.');
      const binding = a.user.username;
      const challengeKey = `emailverify:${binding}`;
      const stored = await U.getJson(challengeKey);
      const candidate = codeDigest('verify', `${binding}:${email}`, code);
      if (!stored || stored.email !== email || !safeEqualHex(stored.digest, candidate)) {
        if (stored && stored.email === email) {
          stored.tries = (stored.tries || 0) + 1;
          if (stored.tries >= 5) await cmd('DEL', challengeKey);
          else await cmd('SET', challengeKey, JSON.stringify(stored), 'EX', String(CODE_TTL));
        }
        return U.fail(res, 400, 'Kode salah atau sudah kedaluwarsa. Minta kode baru.');
      }
      const owner = await cmd('GET', `email:${email}`);
      if (owner && owner !== binding) return U.fail(res, 409, 'Email sudah dipakai akun lain.');
      const old = U.lower(a.user.email);
      if (old && old !== email && await cmd('GET', `email:${old}`) === binding) await cmd('DEL', `email:${old}`);
      a.user.email = email; a.user.emailVerified = true;
      await U.saveUser(a.user); await cmd('SET', `email:${email}`, binding); await cmd('DEL', `emailverify:${binding}`);
      return U.send(res, 200, { ok: true, user: U.publicUser(a.user, { full: true }) });
    }

    if (action === 'logout') {
      const h = req.headers.authorization || ''; if (h.startsWith('Bearer ')) await cmd('DEL', `sess:${h.slice(7)}`);
      return U.send(res, 200, { ok: true });
    }

    if (action === 'password') {
      const a = await U.auth(req, res); if (!a) return;
      const np = String(b.newPassword ?? '');
      if (np.length < 6 || np.length > 64) return U.fail(res, 400, 'Password baru minimal 6 karakter.');
      if (!a.user.mustChange && !U.verifyPassword(String(b.oldPassword ?? ''), a.user.salt, a.user.passHash)) return U.fail(res, 401, 'Password lama salah.');
      const { salt, hash } = U.hashPassword(np);
      a.user.salt = salt; a.user.passHash = hash; a.user.mustChange = false; a.user.ver = (a.user.ver || 0) + 1;
      await U.saveUser(a.user);
      const token = await U.createSession(a.user);
      return U.send(res, 200, { ok: true, token });
    }
    return U.fail(res, 400, 'Aksi tidak dikenal');
  } catch (e) { return U.fail(res, 500, 'Server error: ' + e.message); }
};
