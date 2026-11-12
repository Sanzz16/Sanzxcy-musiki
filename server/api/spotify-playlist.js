// Read metadata and tracks from a public Spotify playlist using server-only client credentials.
const U = require('../lib/util');

function playlistId(input) {
  let url;
  try { url = new URL(String(input || '')); } catch { return null; }
  if (!['open.spotify.com', 'www.open.spotify.com'].includes(url.hostname.toLowerCase())) return null;
  const match = url.pathname.match(/^\/playlist\/([A-Za-z0-9]+)\/?$/);
  return match ? match[1] : null;
}

async function spotifyFetch(url, token) {
  const response = await fetch(url, { headers: { Authorization: `Bearer ${token}` } });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error(response.status === 404 ? 'Playlist tidak ditemukan atau tidak publik' : 'Spotify API gagal mengambil data playlist');
    error.status = response.status === 404 ? 404 : 502;
    throw error;
  }
  return body;
}

module.exports = async (req, res) => {
  try {
    if (req.method !== 'GET') return U.fail(res, 405, 'Method tidak didukung');
    const id = playlistId(req.query.url);
    if (!id) return U.fail(res, 400, 'Masukkan URL playlist Spotify publik yang valid.');
    const clientId = process.env.SPOTIFY_CLIENT_ID;
    const clientSecret = process.env.SPOTIFY_CLIENT_SECRET;
    if (!clientId || !clientSecret) return U.fail(res, 503, 'Impor Spotify belum dikonfigurasi di server.');
    const basic = Buffer.from(`${clientId}:${clientSecret}`).toString('base64');
    const tokenResponse = await fetch('https://accounts.spotify.com/api/token', {
      method: 'POST', headers: { Authorization: `Basic ${basic}`, 'Content-Type': 'application/x-www-form-urlencoded' },
      body: 'grant_type=client_credentials',
    });
    const tokenData = await tokenResponse.json().catch(() => ({}));
    if (!tokenResponse.ok || !tokenData.access_token) return U.fail(res, 502, 'Gagal terhubung ke Spotify.');
    const token = tokenData.access_token;
    const playlist = await spotifyFetch(`https://api.spotify.com/v1/playlists/${id}?fields=id,name,description,images,owner(display_name),tracks(total,items(track(id,name,artists(name),album(name,images(url)),duration_ms,external_urls(spotify))))`, token);
    const items = [...(playlist.tracks?.items || [])];
    const total = playlist.tracks?.total || items.length;
    for (let offset = items.length; offset < Math.min(total, 1000); offset += 100) {
      const page = await spotifyFetch(`https://api.spotify.com/v1/playlists/${id}/tracks?limit=100&offset=${offset}&fields=items(track(id,name,artists(name),album(name,images(url)),duration_ms,external_urls(spotify)))`, token);
      items.push(...(page.items || []));
    }
    const tracks = items.filter((item) => item.track).map(({ track }) => ({
      id: track.id, title: track.name,
      artists: (track.artists || []).map((artist) => artist.name),
      album: track.album?.name || '', image: track.album?.images?.[0]?.url || '',
      durationMs: track.duration_ms, url: track.external_urls?.spotify || '',
    }));
    return U.send(res, 200, { ok: true, playlist: { id: playlist.id, name: playlist.name, description: playlist.description || '', image: playlist.images?.[0]?.url || '', owner: playlist.owner?.display_name || '', total, tracks } });
  } catch (e) { return U.fail(res, e.status || 502, e.message || 'Gagal membaca playlist Spotify.'); }
};
