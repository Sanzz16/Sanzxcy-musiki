// Klien Upstash Redis via REST (tanpa dependensi tambahan).
const URL_ = process.env.KV_REST_API_URL || process.env.UPSTASH_REDIS_REST_URL;
const TOKEN = process.env.KV_REST_API_TOKEN || process.env.UPSTASH_REDIS_REST_TOKEN;

async function cmd(...args) {
  if (!URL_ || !TOKEN) throw new Error('Redis belum dikonfigurasi (KV_REST_API_URL / KV_REST_API_TOKEN)');
  const r = await fetch(URL_, {
    method: 'POST',
    headers: { Authorization: `Bearer ${TOKEN}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(args.map((a) => (typeof a === 'object' && a !== null ? JSON.stringify(a) : a))),
  });
  const j = await r.json();
  if (j.error) throw new Error(j.error);
  return j.result;
}

async function getJson(key) {
  const v = await cmd('GET', key);
  if (v == null) return null;
  try { return JSON.parse(v); } catch { return null; }
}
async function setJson(key, obj, exSeconds) {
  if (exSeconds) return cmd('SET', key, JSON.stringify(obj), 'EX', String(exSeconds));
  return cmd('SET', key, JSON.stringify(obj));
}

module.exports = { cmd, getJson, setJson };
