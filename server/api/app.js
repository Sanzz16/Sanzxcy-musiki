// Status aplikasi publik: maintenance, update, changelog (dikendalikan lewat owner.html).
const U = require('../lib/util');

module.exports = async (req, res) => {
  try {
    const st = (await U.getJson('app:status')) || {};
    const cl = (await U.getJson('app:changelog')) || { md: '', updatedAt: 0 };
    U.send(res, 200, {
      ok: true,
      serverTime: Date.now(),
      maintenance: st.maintenance || { enabled: false, message: '', eta: '' },
      update: st.update || { enabled: false, versionName: '', versionCode: 0, message: '', downloadUrl: '' },
      changelog: cl,
    });
  } catch (e) { U.fail(res, 500, 'Server error: ' + e.message); }
};
