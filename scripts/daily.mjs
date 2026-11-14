// Her gün GitHub Actions tarafından çalıştırılır.
// Şehirler için 16 günlük tahmini kaydeder, geçmiş tahminleri gerçekleşen değerlerle karşılaştırır.
import fs from 'node:fs/promises';
const DIR = new URL('../public/data/', import.meta.url);
const read = async (f, d) => { try { return JSON.parse(await fs.readFile(new URL(f, DIR), 'utf8')); } catch { return d; } };
const write = (f, v) => fs.writeFile(new URL(f, DIR), JSON.stringify(v));
const slug = s => s.toLocaleLowerCase('tr').normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/ı/g, 'i').replace(/[^a-z0-9]+/g, '-');
const dayDiff = (a, b) => Math.round((Date.parse(b) - Date.parse(a)) / 864e5);

const cities = await read('cities.json', []);
const out = { updated: new Date().toISOString(), source: 'Open-Meteo en iyi eşleşme (best_match) tahmini', cities: {} };

for (const c of cities) {
  const s = slug(c.name);
  const url = `https://api.open-meteo.com/v1/forecast?latitude=${c.lat}&longitude=${c.lon}&timezone=auto` +
    `&daily=temperature_2m_max,temperature_2m_min,precipitation_sum&past_days=10&forecast_days=16`;
  let j;
  try { const r = await fetch(url); j = await r.json(); if (!r.ok || j.error) throw new Error(j.reason || r.status); }
  catch (e) { console.error(c.name, e.message); continue; }
  const d = j.daily;
  const now = new Date(Date.now() + j.utc_offset_seconds * 1000).toISOString().slice(0, 10);
  const h = await read(`history/${s}.json`, { issued: {}, obs: {} });
  // gerçekleşen (bugünden önceki günler)
  d.time.forEach((t, i) => { if (t < now && d.temperature_2m_max[i] != null) h.obs[t] = { x: d.temperature_2m_max[i], n: d.temperature_2m_min[i], p: d.precipitation_sum[i] }; });
  // bugünün tahmini
  h.issued[now] = d.time.map((t, i) => ({ d: t, x: d.temperature_2m_max[i], n: d.temperature_2m_min[i], p: d.precipitation_sum[i] })).filter(r => r.d >= now && r.x != null);
  // eski kayıtları buda (150 gün)
  for (const k of Object.keys(h.issued)) if (dayDiff(k, now) > 150) delete h.issued[k];
  for (const k of Object.keys(h.obs)) if (dayDiff(k, now) > 170) delete h.obs[k];
  await write(`history/${s}.json`, h);
  // doğrulama
  const leads = [];
  for (let L = 1; L <= 15; L++) {
    let n = 0, ae = 0, hit = 0, pn = 0, pc = 0, bias = 0;
    for (const [iss, rows] of Object.entries(h.issued)) for (const r of rows) {
      if (dayDiff(iss, r.d) !== L) continue; const o = h.obs[r.d]; if (!o || o.x == null) continue;
      const e = r.x - o.x; n++; ae += Math.abs(e); bias += e; if (Math.abs(e) <= 2) hit++;
      if (o.p != null && r.p != null) { pn++; if ((r.p >= 0.5) === (o.p >= 0.5)) pc++; }
    }
    leads.push({ L, n, mae: n ? +(ae / n).toFixed(2) : null, bias: n ? +(bias / n).toFixed(2) : null, hit: n ? +(hit / n).toFixed(3) : null, wetAcc: pn ? +(pc / pn).toFixed(3) : null });
  }
  out.cities[s] = { name: c.name, lat: c.lat, lon: c.lon, days: Object.keys(h.issued).length, leads };
  console.log(c.name, 'kayıt:', Object.keys(h.issued).length, 'gün');
}
await write('verification.json', out);
