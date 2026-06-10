---
title: Compatibility DB
nav_order: 6
description: "Community-reported mod compatibility, ProtonDB-style: badges, versions, and details for mods run through Retromod."
---

# Compatibility DB

Community reports of real mods run through Retromod — which build, which host, and how well it actually went. Inspired by [ProtonDB](https://www.protondb.com). Every entry is a real run, not a guess.

**Add your report:** open a [compatibility report](https://github.com/Bownlux/Retromod/issues/new?template=compat_report.yml) (a short form — no free-form issue writing), or PR [`docs/_data/compatdb.yml`](https://github.com/Bownlux/Retromod/blob/main/docs/_data/compatdb.yml) directly. Reports are reviewed and merged by maintainers.

## Badges

| Badge | Meaning |
|---|---|
| <span class="cdb-badge cdb-diamond">⛏ Diamond</span> | Indistinguishable from running natively — everything works |
| <span class="cdb-badge cdb-gold">🥇 Gold</span> | Fully playable; minor or cosmetic quirks only |
| <span class="cdb-badge cdb-iron">⚙ Iron</span> | Core features work; something noticeable is broken or inert |
| <span class="cdb-badge cdb-copper">🟠 Copper</span> | Loads and runs, but major features are missing or inert |
| <span class="cdb-badge cdb-borked">💥 Borked</span> | Crashes or unusable |

**Bundles** <span class="cdb-badge cdb-bundle">📦 Bundle</span> — mods often behave differently together than alone. A bundle report means *"I ran all of these at once"* and the badge applies to the whole combination. If you can tell which mod caused a failure, file a single-mod report for it too — the bundle stays as evidence of the combination.

<div class="cdb-controls">
  <input type="text" id="cdb-search" placeholder="Search mods…" aria-label="Search mods">
  <div class="cdb-filters" id="cdb-badge-filters">
    <button class="cdb-chip" data-filter="all">All</button>
    <button class="cdb-chip" data-filter="diamond">Diamond</button>
    <button class="cdb-chip" data-filter="gold">Gold</button>
    <button class="cdb-chip" data-filter="iron">Iron</button>
    <button class="cdb-chip" data-filter="copper">Copper</button>
    <button class="cdb-chip" data-filter="borked">Borked</button>
  </div>
  <div class="cdb-filters" id="cdb-loader-filters">
    <button class="cdb-chip" data-loader="all">All loaders</button>
    <button class="cdb-chip" data-loader="fabric">Fabric</button>
    <button class="cdb-chip" data-loader="neoforge">NeoForge</button>
    <button class="cdb-chip" data-loader="forge">Forge</button>
  </div>
  <span id="cdb-count"></span>
</div>

<div id="cdb-list">
{% assign entries = site.data.compatdb | sort: "name" %}
{% for e in entries %}
{% if e.bundle %}{% capture searchname %}{{ e.name }} {% for m in e.mods %}{{ m.name }} {% endfor %}{% endcapture %}{% else %}{% capture searchname %}{{ e.name }}{% endcapture %}{% endif %}
<div class="cdb-card" data-name="{{ searchname | downcase }}" data-badge="{{ e.badge }}" data-loader="{{ e.loader }}">
  <div class="cdb-card-head">
    <span class="cdb-badge cdb-{{ e.badge }}">{% case e.badge %}{% when 'diamond' %}⛏ Diamond{% when 'gold' %}🥇 Gold{% when 'iron' %}⚙ Iron{% when 'copper' %}🟠 Copper{% else %}💥 Borked{% endcase %}</span>
    {% if e.bundle %}<span class="cdb-badge cdb-bundle">📦 Bundle</span>{% endif %}
    <strong class="cdb-name">{{ e.name }}</strong>
    <span class="cdb-links">
      {% unless e.bundle %}
      {% if e.links.modrinth %}<a href="{{ e.links.modrinth }}">Modrinth</a>{% endif %}
      {% if e.links.curseforge %}<a href="{{ e.links.curseforge }}">CurseForge</a>{% endif %}
      {% if e.links.github %}<a href="{{ e.links.github }}">GitHub</a>{% endif %}
      {% endunless %}
      {% if e.issue %}<a href="https://github.com/Bownlux/Retromod/issues/{{ e.issue }}">#{{ e.issue }}</a>{% endif %}
    </span>
  </div>
  {% if e.bundle %}
  <div class="cdb-bundle-mods">
    {% for m in e.mods %}<span class="cdb-mod-chip">{% if m.link %}<a href="{{ m.link }}">{{ m.name }}</a>{% else %}{{ m.name }}{% endif %} <code>{{ m.version }}</code>{% if m.source_mc %} <small>(MC {{ m.source_mc }})</small>{% endif %}</span>{% endfor %}
  </div>
  <div class="cdb-meta">all together → host MC <code>{{ e.host_mc }}</code> · {{ e.loader }} · Retromod <code>{{ e.retromod }}</code></div>
  {% else %}
  <div class="cdb-meta">
    mod <code>{{ e.mod_version }}</code> (built for MC {{ e.source_mc }}) → host MC <code>{{ e.host_mc }}</code> · {{ e.loader }} · Retromod <code>{{ e.retromod }}</code>
  </div>
  {% endif %}
  <div class="cdb-summary">{{ e.summary }}</div>
  {% if e.details %}<details class="cdb-details"><summary>Details</summary><p>{{ e.details }}</p></details>{% endif %}
  <div class="cdb-foot">reported by {{ e.reporter }} · {{ e.date }}</div>
</div>
{% endfor %}
</div>

<p id="cdb-empty" style="display:none">No entries match. <a href="https://github.com/Bownlux/Retromod/issues/new?template=compat_report.yml">Be the first to report this combination.</a></p>

> Entries reflect the Retromod version they were tested with — a Borked entry on an old build may already be fixed. When you retest on a newer build, file a fresh report; both stay in the history.

<style>
.cdb-badge { display:inline-block; padding:1px 9px; border-radius:10px; font-size:0.78em; font-weight:600; white-space:nowrap; }
.cdb-diamond { background:#b9f2ff; color:#055160; }
.cdb-gold    { background:#ffe9a8; color:#664d03; }
.cdb-iron    { background:#e2e3e5; color:#41464b; }
.cdb-copper  { background:#ffd8b5; color:#7a3e06; }
.cdb-borked  { background:#f8d7da; color:#842029; }
.cdb-bundle  { background:#e7d6f7; color:#4a2070; }
.cdb-bundle-mods { display:flex; flex-wrap:wrap; gap:.4em; margin:.3em 0; }
.cdb-mod-chip { border:1px solid var(--border-color, #ccc); border-radius:10px; padding:.05em .55em; font-size:.82em; }
.cdb-controls { margin:1em 0; display:flex; flex-wrap:wrap; gap:.5em; align-items:center; }
#cdb-search { padding:.35em .6em; min-width:220px; border:1px solid #bbb; border-radius:6px; }
.cdb-chip { padding:.2em .7em; border:1px solid #bbb; border-radius:12px; background:transparent; cursor:pointer; font-size:.82em; }
.cdb-chip.active { background:#0d6efd; color:#fff; border-color:#0d6efd; }
.cdb-card { border:1px solid var(--border-color, #ddd); border-radius:8px; padding:.7em .9em; margin:.6em 0; }
.cdb-card-head { display:flex; flex-wrap:wrap; gap:.6em; align-items:center; }
.cdb-name { font-size:1.05em; }
.cdb-links { margin-left:auto; display:flex; gap:.6em; font-size:.85em; }
.cdb-meta { font-size:.82em; opacity:.85; margin:.25em 0; }
.cdb-summary { margin:.2em 0; }
.cdb-details summary { cursor:pointer; font-size:.85em; }
.cdb-foot { font-size:.75em; opacity:.65; margin-top:.3em; }
</style>

<script>
(function () {
  var badge = 'all', loader = 'all', q = '';
  var cards = Array.prototype.slice.call(document.querySelectorAll('.cdb-card'));
  function apply() {
    var shown = 0;
    cards.forEach(function (c) {
      var ok = (badge === 'all' || c.dataset.badge === badge)
            && (loader === 'all' || c.dataset.loader === loader)
            && (q === '' || c.dataset.name.indexOf(q) !== -1);
      c.style.display = ok ? '' : 'none';
      if (ok) shown++;
    });
    document.getElementById('cdb-count').textContent = shown + ' / ' + cards.length + ' reports';
    document.getElementById('cdb-empty').style.display = shown ? 'none' : '';
  }
  function wire(boxId, attr, set) {
    var box = document.getElementById(boxId);
    box.addEventListener('click', function (ev) {
      var b = ev.target.closest('.cdb-chip'); if (!b) return;
      box.querySelectorAll('.cdb-chip').forEach(function (x) { x.classList.remove('active'); });
      b.classList.add('active'); set(b.dataset[attr]); apply();
    });
    box.querySelector('.cdb-chip').classList.add('active');
  }
  wire('cdb-badge-filters', 'filter', function (v) { badge = v; });
  wire('cdb-loader-filters', 'loader', function (v) { loader = v; });
  document.getElementById('cdb-search').addEventListener('input', function (e) {
    q = e.target.value.trim().toLowerCase(); apply();
  });
  apply();
})();
</script>
