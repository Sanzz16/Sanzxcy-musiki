const crypto = require('crypto');
const U = require('../lib/util');
const { put } = require('@vercel/blob');
const MAX_MEDIA_BYTES = 2 * 1024 * 1024;
const TYPES = {
  'image/jpeg': { ext: 'jpg', matches: (b) => b.length >= 3 && b[0] === 0xff && b[1] === 0xd8 && b[2] === 0xff },
  'image/png': { ext: 'png', matches: (b) => b.length >= 8 && b.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])) },
  'image/webp': { ext: 'webp', matches: (b) => b.length >= 12 && b.toString('ascii', 0, 4) === 'RIFF' && b.toString('ascii', 8, 12) === 'WEBP' },
  'video/mp4': { ext: 'mp4', matches: (b) => b.length >= 12 && b.toString('ascii', 4, 8) === 'ftyp' },
};
const USER_HISTORY_LIMIT = 200;

function publicFeedback(record) {
  return {
    id: String(record.id || ''),
    type: String(record.type || ''),
    title: String(record.title || ''),
    description: String(record.description || ''),
    username: String(record.username || ''),
    displayName: String(record.displayName || record.username || ''),
    createdAt: Number(record.createdAt) || 0,
    updatedAt: Number(record.updatedAt) || Number(record.createdAt) || 0,
    status: ['new', 'processing', 'done', 'archived'].includes(record.status) ? record.status : 'new',
    attachment: record.attachment && typeof record.attachment === 'object'
      ? { url: String(record.attachment.url || ''), contentType: String(record.attachment.contentType || ''), size: Number(record.attachment.size) || 0 }
      : null,
  };
}

async function userFeedbackList(username) {
  const own = String(username || '').trim().toLowerCase();
  const indexed = await U.cmd('ZREVRANGE', `feedback:user:${own}`, '0', String(USER_HISTORY_LIMIT - 1));
  const legacy = await U.cmd('ZREVRANGE', 'feedback:all', '0', String(USER_HISTORY_LIMIT - 1));
  const ids = [...new Set([...(Array.isArray(indexed) ? indexed : []), ...(Array.isArray(legacy) ? legacy : [])].map(String))];
  const records = await Promise.all(ids.map((id) => U.getJson(`feedback:${id}`)));
  return records
    .filter((r) => r && String(r.username || '').trim().toLowerCase() === own)
    .map(publicFeedback)
    .sort((a, b) => b.createdAt - a.createdAt);
}

async function uploadToSupabase(filename, bytes, mime) {
  const cfg = U.supabaseStorageConfig();
  if (!cfg) return { configured: false, ok: false };
  // Feedback memakai bucket tersendiri; jangan pernah mengubah community-media
  // (yang bisa berisi profil/stiker) dari privat menjadi publik.
  const bucketName = String(process.env.FEEDBACK_STORAGE_BUCKET || 'feedback-media').trim();
  if (!/^[a-z0-9][a-z0-9._-]{1,62}$/i.test(bucketName)) return { configured: true, ok: false };
  const authHeaders = { apikey: cfg.key };
  // Supabase sb_secret_* keys are not JWTs; do not send them as Bearer tokens.
  if (!cfg.key.startsWith('sb_secret_')) authHeaders.Authorization = `Bearer ${cfg.key}`;
  const bucketUrl = `${cfg.url}/storage/v1/bucket`;
  const bucket = await fetch(bucketUrl, {
    method: 'POST',
    headers: { ...authHeaders, 'Content-Type': 'application/json' },
    body: JSON.stringify({ id: bucketName, name: bucketName, public: true, file_size_limit: MAX_MEDIA_BYTES, allowed_mime_types: Object.keys(TYPES) }),
  });
  if (!bucket.ok) {
    const existing = await fetch(`${bucketUrl}/${encodeURIComponent(bucketName)}`, { headers: authHeaders });
    if (!existing.ok) return { configured: true, ok: false };
    let existingBucket = null;
    try { existingBucket = await existing.json(); } catch (_) {}
    // A pre-existing private bucket is not silently made public.
    if (!existingBucket || existingBucket.public !== true) return { configured: true, ok: false };
  }
  const path = `feedback/${filename}`;
  const uploaded = await fetch(`${cfg.url}/storage/v1/object/${encodeURIComponent(bucketName)}/${path.split('/').map(encodeURIComponent).join('/')}`, {
    method: 'POST', headers: { ...authHeaders, 'Content-Type': mime, 'x-upsert': 'true' }, body: bytes,
  });
  if (!uploaded.ok) return { configured: true, ok: false };
  return {
    configured: true,
    ok: true,
    url: `${cfg.url}/storage/v1/object/public/${encodeURIComponent(bucketName)}/${path.split('/').map(encodeURIComponent).join('/')}`,
  };
}

module.exports = async (req, res) => {
  try {
    const action = String((req.query && req.query.action) || '');
    if (req.method === 'GET' && action === 'mine') {
      const a = await U.auth(req, res);
      if (!a) return;
      const feedback = await userFeedbackList(a.user.username);
      return U.send(res, 200, { ok: true, feedback });
    }
    if (req.method !== 'POST') {
      res.setHeader('Allow', 'GET, POST');
      return U.fail(res, 405, 'Method tidak didukung');
    }
    const a = await U.auth(req, res);
    if (!a) return;
    const b = U.body(req);
    const type = String(b.type || '');
    const title = typeof b.title === 'string' ? b.title.trim() : '';
    const description = typeof b.description === 'string' ? b.description.trim() : '';
    if (!['bug', 'feature'].includes(type)) return U.fail(res, 400, 'Jenis laporan tidak valid.');
    if (!title || title.length > 120) return U.fail(res, 400, 'Judul wajib diisi (maksimal 120 karakter).');
    if (!description || description.length > 2000) return U.fail(res, 400, 'Deskripsi wajib diisi (maksimal 2000 karakter).');

    let attachment = null;
    if (b.data !== undefined && b.data !== null && b.data !== '') {
      const mime = String(b.contentType || '').toLowerCase();
      const spec = TYPES[mime];
      if (!spec || typeof b.data !== 'string' || b.data.length > Math.ceil(MAX_MEDIA_BYTES / 3) * 4 + 8 || !/^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/.test(b.data)) {
        return U.fail(res, 400, 'Lampiran harus berupa JPEG, PNG, WebP, atau MP4 yang valid.');
      }
      const bytes = Buffer.from(b.data, 'base64');
      if (!bytes.length || bytes.length > MAX_MEDIA_BYTES) return U.fail(res, 413, 'Ukuran lampiran final maksimal 2 MiB.');
      if (!spec.matches(bytes)) return U.fail(res, 400, 'Isi file tidak cocok dengan tipe media yang dipilih.');
      const id = crypto.randomUUID();
      let stored = null;
      let blobConfigured = !!process.env.BLOB_READ_WRITE_TOKEN;
      if (blobConfigured) {
        try {
          const blob = await put(`feedback/${id}.${spec.ext}`, bytes, {
            access: 'public', contentType: mime, addRandomSuffix: true, token: process.env.BLOB_READ_WRITE_TOKEN,
          });
          stored = { url: blob.url, contentType: mime, size: bytes.length };
        } catch (_) { /* Continue with the configured server-side Supabase fallback. */ }
      }
      if (!stored) {
        let supabase;
        try { supabase = await uploadToSupabase(`${id}.${spec.ext}`, bytes, mime); }
        catch (_) { supabase = { configured: !!U.supabaseStorageConfig(), ok: false }; }
        if (supabase && supabase.ok) {
          stored = { url: supabase.url, contentType: mime, size: bytes.length };
        } else if (!blobConfigured && !(supabase && supabase.configured)) {
          return U.fail(res, 503, 'Penyimpanan lampiran belum dikonfigurasi. Hapus lampiran untuk mengirim laporan teks saja.', { code: 'attachment_storage_unconfigured' });
        } else {
          return U.fail(res, 502, 'Lampiran gagal diunggah ke penyimpanan yang tersedia. Hapus lampiran untuk mengirim laporan teks saja.', { code: 'attachment_upload_failed' });
        }
      }
      attachment = stored;
    }

    const now = Date.now();
    const id = crypto.randomUUID();
    const record = {
      id, type, title, description,
      username: String(a.user.username || '').slice(0, 20),
      displayName: String(a.user.displayName || a.user.username || '').slice(0, 24),
      createdAt: now, updatedAt: now, status: 'new', attachment,
    };
    await U.setJson(`feedback:${id}`, record);
    await U.cmd('ZADD', 'feedback:all', String(now), id);
    await U.cmd('ZADD', `feedback:user:${String(a.user.username || '').trim().toLowerCase()}`, String(now), id);
    return U.send(res, 201, { ok: true, id, status: record.status });
  } catch (e) {
    console.error('[feedback-api] request failed:', e instanceof Error ? e.name : 'UnknownError');
    return U.fail(res, 500, 'Terjadi kesalahan server. Silakan coba lagi.');
  }
};
