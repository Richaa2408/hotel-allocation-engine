// Screening screen: screen a guest, add blocklist entries, and show which Bloom filter bits each value
// hashed to, followed by the exact check when the filter says "possibly listed".

import { api, el, fmt, showError, svg, tip } from "./lib.js";

const $ = (id) => document.getElementById(id);

let stats = null;

export function initScreening() {
  $("screen-form").addEventListener("submit", (e) => { e.preventDefault(); screen(); });
  $("block-form").addEventListener("submit", (e) => { e.preventDefault(); addEntry(); });
  refresh();
}

export async function refresh() {
  try {
    stats = await api("/api/screening/stats");
    renderStats();
  } catch (err) {
    showError($("screen-error"), err);
  }
}

async function screen() {
  const body = { email: $("screen-email").value || null, phone: $("screen-phone").value || null };
  try {
    const r = await api("/api/screening/check", { method: "POST", body });
    showError($("screen-error"), null);
    await refresh();
    renderResult(r);
  } catch (err) {
    showError($("screen-error"), err);
  }
}

async function addEntry() {
  const body = { kind: $("block-kind").value, value: $("block-value").value, reason: $("block-reason").value || null };
  try {
    const r = await api("/api/screening/blocklist", { method: "POST", body });
    showError($("block-error"), null);
    $("block-result").textContent = r.added ? "Added to the blocklist and the filter." : "Already on the blocklist. Nothing changed.";
    stats = r.stats;
    renderStats();
  } catch (err) {
    $("block-result").textContent = "";
    showError($("block-error"), err);
  }
}

function renderStats() {
  const s = stats;
  const pct = (x) => `${(x * 100).toFixed(2)}%`;
  const rows = [
    ["Blocklist entries", fmt(s.entries) + (s.overCapacity ? ` (over the planned ${fmt(s.capacity)})` : "")],
    ["Bits in the filter (m)", fmt(s.bitCount) + ` (${fmt(Math.ceil(s.bitCount / 8 / 1024))} KB)`],
    ["Hash functions (k)", String(s.hashCount)],
    ["Bits set to 1", `${fmt(s.bitsSet)} (${pct(s.bitsSet / s.bitCount)})`],
    ["Expected false-positive rate", rate(s.expectedFalsePositiveRate)],
    null,
    ["Values screened", fmt(s.fieldsScreened)],
    ["Cleared by the filter alone", fmt(s.clearedByBloom)],
    ["Sent to the exact blocklist", fmt(s.sentToExactCheck)],
    ["Confirmed on the blocklist", fmt(s.confirmedMatches)],
    ["False positives (not held)", fmt(s.falsePositives)],
  ];
  $("screen-stats").replaceChildren(...rows.flatMap((r) =>
    r === null ? [el("div", { class: "sep" })] : [el("dt", {}, r[0]), el("dd", {}, r[1])]));
}

/** Small probabilities need more than two decimals, and "1 in N" is easier to picture. */
function rate(p) {
  if (p <= 0) return "0% (no entries yet)";
  const percent = p * 100;
  const shown = percent >= 0.1 ? percent.toFixed(2) : percent.toPrecision(2);
  return `${shown}% (about 1 in ${fmt(Math.round(1 / p))})`;
}

function renderResult(r) {
  $("screen-result").hidden = false;
  const verdict = $("screen-verdict");
  verdict.className = `verdict ${r.verdict === "BLOCKED" ? "no" : "ok"}`;
  verdict.replaceChildren(el("b", {}, r.message));
  $("screen-checks").replaceChildren(...r.checks.map(renderCheck));
}

function renderCheck(c) {
  const b = c.bloom;
  const zeros = b.positions.filter((_, i) => !b.bitWasSet[i]);
  const field = c.field === "EMAIL" ? "Email" : "Phone";

  const stage1 = b.possiblyPresent
    ? `All ${b.positions.length} bits are 1, so this value is possibly on the blocklist. The filter can't be sure: other entries may have set these bits.`
    : `${zeros.length} of the ${b.positions.length} bits ${zeros.length === 1 ? "is" : "are"} 0 (bit ${fmt(zeros[0])} first). `
      + "Every listed value set all of its bits, so this one was never added. Cleared without looking up the blocklist.";

  let stage2;
  if (!c.exactChecked) stage2 = "Skipped. The filter's \"definitely not listed\" answer is always correct.";
  else if (c.listed) stage2 = `Found on the blocklist: ${c.reason}.`;
  else stage2 = "Not on the blocklist. The filter's match was a false positive, so the guest is not held.";

  return el("div", { class: "stage" },
    el("h3", {}, `${field}: `, el("span", { class: "mono" }, c.normalized)),
    el("p", { class: "small muted" }, "Normalized before hashing (lower case for emails, digits only for phones)."),
    el("p", {}, el("b", {}, "Stage 1, Bloom filter. "), stage1),
    el("div", { class: "bits" }, ...b.positions.map((p, i) =>
      el("div", { class: `bit ${b.bitWasSet[i] ? "one" : "zero"}`, title: `Hash ${i + 1} gives bit ${fmt(p)}` },
        el("b", {}, b.bitWasSet[i] ? "1" : "0"), `#${fmt(p)}`))),
    strip(b),
    el("p", {}, el("b", {}, "Stage 2, exact blocklist. "), stage2));
}

/** Where the k positions fall across the whole bit array. */
function strip(b) {
  const width = 900, height = 34, m = stats.bitCount;
  const x = (p) => 4 + (p / (m - 1)) * (width - 8);
  return el("div", { class: "svg-wrap" }, svg("svg", { width, height, role: "img", "aria-label": "Bit positions across the filter" },
    svg("rect", { x: 4, y: 12, width: width - 8, height: 8, fill: "#eceef1", stroke: "var(--border)" }),
    b.positions.map((p, i) => svg("line", {
      x1: x(p), x2: x(p), y1: 6, y2: 26, "stroke-width": 2,
      stroke: b.bitWasSet[i] ? "var(--accent)" : "var(--red)",
    }, tip(`bit ${fmt(p)}: ${b.bitWasSet[i] ? 1 : 0}`))),
    svg("text", { x: 4, y: 34, "font-size": 10, fill: "var(--muted)" }, "bit 0"),
    svg("text", { x: width - 4, y: 34, "font-size": 10, fill: "var(--muted)", "text-anchor": "end" }, `bit ${fmt(m - 1)}`)));
}
