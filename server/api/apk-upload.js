'use strict';

const crypto = require('crypto');
const U = require('../lib/util');
const { handleUpload } = require('@vercel/blob/client');
const { del } = require('@vercel/blob');

const MAX_APK_BYTES = 120 * 1024 * 1024;
const API_VERSION = '2022-11-28';
const PENDING_TTL_SECONDS = 60 * 60;
const MIME_APK = 'application/vnd.android.package-archive';

function validVersion(value) {
  return typeof value === 'string' && /^(?:0|[1-9]\d{0,3})(?:\.(?:0|[1-9]\d{0,3})){1,3}(?:[-+][A-Za-z0-9][A-Za-z0-9.-]{0,14})?$/.test(value);
}

function safeRepo() {
  const repo = String(process.env.GITHUB_REPO || 'Sanzz16/Sanzxcy-Music').trim();
  return /^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(repo) ? repo : '';
}

function githubHeaders(token, json = true) {
  return {
    Authorization: `Bearer ${token}`,
    Accept: 'application/vnd.github+json',
    'X-GitHub-Api-Version': API_VERSION,
    'User-Agent': 'sanzxcy-apk-publisher',
    ...(json ? { 'Content-Type': 'application/json' } : {}),
  };
}

function pendingKey(pathname) {
  return `apk:pending:${crypto.createHash('sha256').update(pathname).digest('hex')}`;
}

async function authorizeOwner(req, res) {
  const auth = await U.auth(req, res);
  if (!auth) return null;
  if (auth.user.role !== 'owner') {
    U.fail(res, 403, 'Upload APK hanya dapat dilakukan oleh akun owner.');
    return null;
  }
  return auth.user;
}

function parseClientPayload(value) {
  let data;
  try { data = JSON.parse(String(value || '')); } catch { return null; }
  const versionName = String(data.versionName || '').trim();
  const versionCode = Number(data.versionCode);
  const message = String(data.message || '').trim().slice(0, 1500);
  const pathname = String(data.pathname || '');
  const actor = String(data.actor || '').trim().toLowerCase();
  const authToken = String(data.authToken || '').trim();
  if (!validVersion(versionName) || !Number.isSafeInteger(versionCode) || versionCode < 1 || versionCode > 2_000_000_000) return null;
  if (!/^[a-z0-9_.]{3,20}$/.test(actor)) return null;
  if (!/^apk\/staging\/[0-9a-f-]{36}\/[A-Za-z0-9._-]{1,80}\.apk$/i.test(pathname)) return null;
  return { versionName, versionCode, message, pathname, actor, authToken };
}

async function githubRequest(url, token, options = {}) {
  const response = await fetch(url, {
    ...options,
    headers: { ...githubHeaders(token, options.json !== false), ...(options.headers || {}) },
    ...(Object.prototype.hasOwnProperty.call(options, 'json') ? { body: JSON.stringify(options.json), json: undefined } : {}),
    signal: AbortSignal.timeout(25_000),
  });
  const text = await response.text().catch(() => '');
  const data = text ? (() => { try { return JSON.parse(text); } catch { return {}; } })() : {};
  return { response, data };
}

async function publishPending(req, res, body) {
  const user = await authorizeOwner(req, res);
  if (!user) return;
  const pathname = String(body.pathname || '');
  const key = pendingKey(pathname);
  const pending = await U.getJson(key);
  if (!pending || pending.actor !== user.username || pending.pathname !== pathname) {
    return U.fail(res, 404, 'Berkas APK tidak ditemukan atau sesi unggah sudah kedaluwarsa. Unggah ulang APK.');
  }
  if (!process.env.GITHUB_TOKEN) return U.fail(res, 503, 'GITHUB_TOKEN belum dikonfigurasi pada environment server Vercel.');
  const repo = safeRepo();
  if (!repo) return U.fail(res, 503, 'GITHUB_REPO harus berformat pemilik/repo.');

  const token = process.env.GITHUB_TOKEN;
  const [owner, name] = repo.split('/');
  const tag = `sanzxcy-v${pending.versionName}`;
  const assetName = `sanzxcy-${pending.versionName}.apk`;
  const apiBase = `https://api.github.com/repos/${encodeURIComponent(owner)}/${encodeURIComponent(name)}`;
  const releaseUrl = `${apiBase}/releases/tags/${encodeURIComponent(tag)}`;
  let releaseResult = await githubRequest(releaseUrl, token);
  let release = releaseResult.data;
  let publishAfterUpload = false;

  if (releaseResult.response.status === 404) {
    releaseResult = await githubRequest(`${apiBase}/releases`, token, {
      method: 'POST',
      json: {
        tag_name: tag,
        target_commitish: process.env.GITHUB_RELEASE_TARGET || 'main',
        name: `Sanzxcy Music ${pending.versionName}`,
        body: pending.message || `Rilis aplikasi Sanzxcy Music ${pending.versionName}.`,
        draft: true,
        prerelease: false,
      },
    });
    release = releaseResult.data;
    publishAfterUpload = releaseResult.response.ok;
  } else if (releaseResult.response.ok) {
    const updated = await githubRequest(`${apiBase}/releases/${release.id}`, token, {
      method: 'PATCH',
      json: {
        name: `Sanzxcy Music ${pending.versionName}`,
        body: pending.message || `Rilis aplikasi Sanzxcy Music ${pending.versionName}.`,
        draft: !!release.draft,
        prerelease: false,
      },
    });
    if (!updated.response.ok) return U.fail(res, 502, `Metadata GitHub Release gagal diperbarui (${updated.response.status}).`);
    release = updated.data;
    publishAfterUpload = !!release.draft;
  }

  if (!releaseResult.response.ok && releaseResult.response.status !== 404) {
    return U.fail(res, 502, `GitHub gagal menyiapkan release (${releaseResult.response.status}). Periksa izin Contents/Release pada GITHUB_TOKEN.`);
  }
  if (!release || !Number.isSafeInteger(Number(release.id)) || !release.upload_url) {
    return U.fail(res, 502, 'GitHub tidak mengembalikan informasi release yang valid.');
  }

  const blobResponse = await fetch(pending.blobUrl, { signal: AbortSignal.timeout(25_000) });
  if (!blobResponse.ok) return U.fail(res, 502, 'Berkas sementara tidak dapat dibaca. Unggah ulang APK.');
  const bytes = Buffer.from(await blobResponse.arrayBuffer());
  if (!bytes.length || bytes.length > MAX_APK_BYTES) return U.fail(res, 413, 'APK kosong atau melebihi batas 120 MiB.');
  // APK adalah arsip ZIP; tolak berkas yang jelas bukan ZIP sebelum menyentuh release.
  if (bytes.length < 4 || bytes[0] !== 0x50 || bytes[1] !== 0x4b) return U.fail(res, 400, 'Berkas bukan APK/ZIP yang valid.');

  const existing = Array.isArray(release.assets) ? release.assets.find((asset) => asset.name === assetName) : null;
  if (existing) {
    const removed = await githubRequest(`${apiBase}/releases/assets/${existing.id}`, token, { method: 'DELETE', json: undefined });
    if (!removed.response.ok && removed.response.status !== 204) {
      return U.fail(res, 502, `Asset APK versi ini tidak dapat diganti (${removed.response.status}).`);
    }
  }

  const uploadUrl = String(release.upload_url).replace(/\{\?name,label\}$/, '');
  const assetUrl = new URL(uploadUrl);
  if (assetUrl.protocol !== 'https:' || !['uploads.github.com', 'api.github.com'].includes(assetUrl.hostname)) {
    return U.fail(res, 502, 'Alamat upload release GitHub tidak valid.');
  }
  assetUrl.searchParams.set('name', assetName);
  const uploaded = await fetch(assetUrl, {
    method: 'POST',
    headers: { ...githubHeaders(token, false), 'Content-Type': MIME_APK },
    body: bytes,
    signal: AbortSignal.timeout(25_000),
  });
  const uploadedData = await uploaded.json().catch(() => ({}));
  if (!uploaded.ok || !uploadedData.browser_download_url) {
    return U.fail(res, 502, `GitHub gagal menyimpan APK (${uploaded.status}).`);
  }
  if (publishAfterUpload) {
    const published = await githubRequest(`${apiBase}/releases/${release.id}`, token, { method: 'PATCH', json: { draft: false } });
    if (!published.response.ok) return U.fail(res, 502, `APK sudah terunggah, tetapi release draft tidak dapat dipublikasikan (${published.response.status}).`);
    release = published.data;
  }

  await U.cmd('DEL', key);
  await del(pending.blobUrl, { token: process.env.BLOB_READ_WRITE_TOKEN }).catch(() => {});
  const audit = { ts: Date.now(), by: user.username, msg: `APK ${pending.versionName} diunggah ke GitHub Release` };
  await U.cmd('LPUSH', 'auditlog', JSON.stringify(audit));
  await U.cmd('LTRIM', 'auditlog', '0', '299');
  return U.send(res, 200, {
    ok: true,
    versionName: pending.versionName,
    versionCode: pending.versionCode,
    downloadUrl: uploadedData.browser_download_url,
    releaseUrl: release.html_url,
  });
}

module.exports = async (req, res) => {
  try {
    if (req.method !== 'POST') {
      res.setHeader('Allow', 'POST');
      return U.fail(res, 405, 'Method tidak didukung.');
    }
    const body = U.body(req);
    if (body.action === 'publish') return await publishPending(req, res, body);
    if (body.action === 'preflight') {
      const user = await authorizeOwner(req, res);
      if (!user) return;
      if (!process.env.BLOB_READ_WRITE_TOKEN) {
        return U.fail(res, 503, 'Upload Server 1 belum siap: BLOB_READ_WRITE_TOKEN tidak dikonfigurasi pada environment server. Gunakan Server 2 atau konfigurasi Blob di deployment.');
      }
      return U.send(res, 200, { ok: true });
    }
    const authBody = body.type === 'blob.generate-client-token';
    const callbackMeta = parseClientPayload(body.payload?.clientPayload);
    // Blob token callbacks omit Authorization; use the owner's existing
    // session token from the callback payload, never a Blob/GitHub secret.
    if (authBody) {
      if (!callbackMeta) return U.fail(res, 400, 'Metadata unggah tidak valid.');
      const previousAuthorization = req.headers.authorization;
      if (!previousAuthorization && callbackMeta.authToken) req.headers.authorization = `Bearer ${callbackMeta.authToken}`;
      const user = await authorizeOwner(req, res);
      if (previousAuthorization) req.headers.authorization = previousAuthorization;
      else delete req.headers.authorization;
      if (!user) return;
      if (user.username !== callbackMeta.actor) return U.fail(res, 403, 'Akun owner unggah tidak cocok.');
      if (!process.env.BLOB_READ_WRITE_TOKEN) {
        return U.fail(res, 503, 'Upload Server 1 belum siap: BLOB_READ_WRITE_TOKEN tidak dikonfigurasi pada environment server. Gunakan Server 2 atau konfigurasi Blob di deployment.');
      }
    }

    const result = await handleUpload({
      body,
      request: req,
      onBeforeGenerateToken: async (pathname, clientPayload) => {
        const meta = parseClientPayload(clientPayload);
        if (!meta || meta.pathname !== pathname) throw new Error('Metadata unggah tidak valid.');
        const previousAuthorization = req.headers.authorization;
        if (!previousAuthorization && meta.authToken) req.headers.authorization = `Bearer ${meta.authToken}`;
        const actor = await authorizeOwner(req, res);
        if (previousAuthorization) req.headers.authorization = previousAuthorization;
        else delete req.headers.authorization;
        if (!actor) throw new Error('Akses upload ditolak.');
        if (actor.username !== meta.actor) throw new Error('Akun owner unggah tidak cocok.');
        return {
          allowedContentTypes: [MIME_APK, 'application/octet-stream'],
          maximumSizeInBytes: MAX_APK_BYTES,
          addRandomSuffix: false,
          cacheControlMaxAge: 60,
          tokenPayload: JSON.stringify({ ...meta, authToken: '' }),
        };
      },
      onUploadCompleted: async ({ blob, tokenPayload }) => {
        const meta = parseClientPayload(tokenPayload);
        if (!meta || blob.pathname !== meta.pathname || !/^https:\/\/[a-z0-9-]+\.public\.blob\.vercel-storage\.com\//i.test(blob.url)) {
          throw new Error('Callback upload tidak valid.');
        }
        await U.setJson(pendingKey(meta.pathname), { ...meta, blobUrl: blob.url, uploadedAt: Date.now() }, PENDING_TTL_SECONDS);
      },
    });
    return U.send(res, 200, result);
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Upload gagal.';
    const status = /ditolak|tidak valid|owner/i.test(message) ? 403 : 400;
    return U.fail(res, status, message);
  }
};
