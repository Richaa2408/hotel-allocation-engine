// Availability screen: check and book stays, the per-night calendar, and the segment tree with the
// nodes the last operation visited. The calendar and the tree share one horizontal scale, so each
// leaf of the tree sits directly under its night's bar.

import { addDays, api, cap, el, fmt, shortDate, showError, svg, tip } from "./lib.js";

const $ = (id) => document.getElementById(id);

const PAD_LEFT = 34;
const PAD_RIGHT = 10;

let calendar = null;   // Calendar from the API for the selected room type
let shown = null;      // { trace, kind: "query" | "update", from, to, wanted }

export function initAvailability() {
  $("stay-form").addEventListener("submit", (e) => { e.preventDefault(); check(); });
  $("stay-book").addEventListener("click", book);
  $("stay-type").addEventListener("change", () => { shown = null; $("stay-outcome").hidden = true; refresh(); });
  refresh();
}

/** Reloads the calendar for the selected room type and redraws. Called after any change to the data. */
export async function refresh() {
  try {
    calendar = await api(`/api/availability/calendar?type=${$("stay-type").value}`);
    if (!$("stay-in").value || $("stay-in").value < calendar.start) {
      $("stay-in").value = addDays(calendar.start, 3);
      $("stay-out").value = addDays(calendar.start, 7);
    }
    const last = addDays(calendar.start, calendar.nights);
    for (const id of ["stay-in", "stay-out"]) {
      $(id).min = calendar.start;
      $(id).max = last;
    }
    drawCalendar();
    drawTree();
    await loadBookings();
  } catch (err) {
    showError($("stay-error"), err);
  }
}

function stayBody() {
  return {
    roomType: $("stay-type").value,
    checkIn: $("stay-in").value || null,
    checkOut: $("stay-out").value || null,
    rooms: Number($("stay-rooms").value),
  };
}

function nightIndex(iso) {
  return Math.round((new Date(iso + "T00:00:00") - new Date(calendar.start + "T00:00:00")) / 86400000);
}

async function check() {
  const body = stayBody();
  try {
    const r = await api("/api/availability/check", { method: "POST", body });
    showError($("stay-error"), null);
    outcome(r.fits,
      r.fits
        ? `Fits. Fewest ${r.roomType.toLowerCase()} rooms free on any of the ${r.nights} nights: ${r.fewestFree}.`
        : `Doesn't fit. Only ${r.fewestFree} ${r.roomType.toLowerCase()} room${r.fewestFree === 1 ? "" : "s"} free on the busiest night, ${r.roomsWanted} wanted.`);
    shown = { trace: r.trace, kind: "query", from: r.trace.from, to: r.trace.to, wanted: r.roomsWanted };
    await refresh();
  } catch (err) {
    showError($("stay-error"), err);
  }
}

async function book() {
  const body = { ...stayBody(), guestName: $("stay-guest").value };
  try {
    const r = await api("/api/availability/bookings", { method: "POST", body });
    showError($("stay-error"), null);
    outcome(r.booked, r.booked ? `${r.message} Reference ${r.bookingId}.` : r.message);
    const t = r.update || r.check.trace;
    shown = { trace: t, kind: r.update ? "update" : "query", from: t.from, to: t.to, wanted: r.check.roomsWanted };
    await refresh();
  } catch (err) {
    showError($("stay-error"), err);
  }
}

async function cancel(id) {
  try {
    const r = await api(`/api/availability/bookings/${encodeURIComponent(id)}`, { method: "DELETE" });
    showError($("stay-error"), null);
    outcome(true, r.message);
    if (r.check.roomType !== $("stay-type").value) $("stay-type").value = r.check.roomType;
    shown = { trace: r.update, kind: "update", from: r.update.from, to: r.update.to, wanted: null };
    await refresh();
  } catch (err) {
    showError($("stay-error"), err);
  }
}

function outcome(ok, text) {
  const box = $("stay-outcome");
  box.hidden = false;
  box.className = `outcome ${ok ? "ok" : "no"}`;
  box.textContent = text;
}

async function loadBookings() {
  const list = (await api("/api/availability/bookings")).reverse();
  $("bookings-sub").textContent = `${fmt(list.length)} bookings, newest first, including the sample bookings loaded at startup.`;
  const rows = list.map((b) => el("tr", {},
    el("td", {}, b.id), el("td", {}, b.guestName), el("td", {}, cap(b.roomType)),
    el("td", {}, `${shortDate(b.checkIn)} to ${shortDate(b.checkOut)}`),
    el("td", { class: "num" }, String(b.rooms)),
    el("td", {}, el("button", { type: "button", class: "secondary", onclick: () => cancel(b.id) }, `Cancel ${b.id}`))));
  $("bookings").tBodies[0].replaceChildren(...(rows.length
    ? rows : [el("tr", {}, el("td", { colspan: 6, class: "muted" }, "No bookings yet."))]));
}

// ---------------------------------------------------------------------------
// Calendar bar chart
// ---------------------------------------------------------------------------

function scaleX() {
  const n = calendar.nights;
  const width = Math.max(900, n * 10) + PAD_LEFT + PAD_RIGHT;
  const unit = (width - PAD_LEFT - PAD_RIGHT) / n;
  return { width, unit, x: (night) => PAD_LEFT + night * unit };
}

function drawCalendar() {
  const { width, unit, x } = scaleX();
  const height = 170;
  const plotTop = 10, plotBottom = 140;
  const total = Math.max(calendar.totalRooms, 1);
  const y = (v) => plotBottom - (v / total) * (plotBottom - plotTop);
  const inRange = (i) => shown && i >= shown.from && i <= shown.to;
  const minInRange = shown
    ? Math.min(...calendar.freeByNight.slice(shown.from, shown.to + 1).map((n) => n.free)) : null;

  const parts = [];
  parts.push(svg("line", { x1: PAD_LEFT, x2: width - PAD_RIGHT, y1: plotBottom, y2: plotBottom, stroke: "var(--border-strong)" }));
  for (const v of [0, total]) {
    parts.push(svg("text", { x: PAD_LEFT - 6, y: y(v) + 4, "text-anchor": "end", "font-size": 11, fill: "var(--muted)" }, String(v)));
  }
  if (shown) {
    parts.push(svg("rect", {
      x: x(shown.from), y: plotTop - 6, width: (shown.to - shown.from + 1) * unit, height: plotBottom - plotTop + 6,
      fill: "var(--accent-weak)",
    }));
  }
  calendar.freeByNight.forEach((n, i) => {
    let fill = "var(--border-strong)";
    if (inRange(i)) fill = n.free === minInRange ? (shown.wanted && n.free < shown.wanted ? "var(--red)" : "var(--accent)") : "var(--muted)";
    parts.push(svg("rect", {
      x: x(i) + 1, y: y(n.free), width: Math.max(unit - 2, 1), height: plotBottom - y(n.free), fill,
    }, tip(`${shortDate(n.night)}: ${n.free} of ${calendar.totalRooms} free`)));
    if (i % 7 === 0) {
      parts.push(svg("text", { x: x(i) + unit / 2, y: plotBottom + 16, "text-anchor": "middle", "font-size": 11, fill: "var(--muted)" },
        shortDate(n.night)));
    }
  });
  if (shown && shown.wanted) {
    parts.push(svg("line", {
      x1: PAD_LEFT, x2: width - PAD_RIGHT, y1: y(shown.wanted), y2: y(shown.wanted),
      stroke: "var(--amber)", "stroke-dasharray": "5 4", "stroke-width": 1.5,
    }));
    parts.push(svg("text", { x: width - PAD_RIGHT, y: y(shown.wanted) - 4, "text-anchor": "end", "font-size": 11, fill: "var(--amber)" },
      `${shown.wanted} wanted`));
  }

  $("cal-title").textContent = `${cap(calendar.roomType)} rooms free per night`;
  $("cal-sub").textContent = `${calendar.totalRooms} rooms in total, ${calendar.nights} nights from ${shortDate(calendar.start)}. `
    + (shown ? "The shaded nights are the stay; the darkest bar is its busiest night." : "Check a stay to highlight its nights.");
  $("cal-svg").replaceChildren(svg("svg", { width, height, role: "img", "aria-label": "Free rooms per night" }, parts));
}

// ---------------------------------------------------------------------------
// Segment tree
// ---------------------------------------------------------------------------

function drawTree() {
  const { width, unit, x } = scaleX();
  const levelH = 30;
  const boxH = 22;
  const depthOf = (node) => Math.floor(Math.log2(node));
  const depth = Math.max(...calendar.treeNodes.map((n) => depthOf(n.node)));
  const height = (depth + 1) * levelH + 4;

  const kindOf = new Map();
  if (shown) for (const v of shown.trace.visits) kindOf.set(v.node, v.kind);

  const parts = calendar.treeNodes.map((n) => {
    const kind = kindOf.get(n.node);
    const bx = x(n.lo) + 1;
    const bw = Math.max((n.hi - n.lo + 1) * unit - 2, 1);
    const by = depthOf(n.node) * levelH + 2;
    let fill = "var(--surface)", stroke = "var(--border)", sw = 1, text = "var(--text)";
    if (kind === "FULL") { fill = "var(--accent)"; stroke = "var(--accent)"; text = "#fff"; }
    else if (kind === "PARTIAL") { stroke = "var(--accent)"; sw = 2; }
    else if (kind === "OUTSIDE") { fill = "#eceef1"; text = "var(--muted)"; }
    const range = n.lo === n.hi
      ? shortDate(calendar.freeByNight[n.lo].night)
      : `${shortDate(calendar.freeByNight[n.lo].night)} to ${shortDate(calendar.freeByNight[n.hi].night)}`;
    const note = `Node ${n.node}, nights ${n.lo} to ${n.hi} (${range}): min ${n.min}`
      + (n.lazy !== 0 ? `, owes its children ${n.lazy > 0 ? "+" : ""}${n.lazy}` : "")
      + (kind ? `. Last operation: ${kind}` : "");
    return svg("g", {},
      svg("rect", { x: bx, y: by, width: bw, height: boxH, rx: 2, fill, stroke, "stroke-width": sw }, tip(note)),
      bw >= 16
        ? svg("text", { x: bx + bw / 2, y: by + 15, "text-anchor": "middle", "font-size": 11, fill: text, "pointer-events": "none" }, String(n.min))
        : null,
      // A pending lazy tag: this node still owes its children an update.
      n.lazy !== 0
        ? svg("circle", { cx: bx + bw - 4, cy: by + 4, r: 3, fill: "var(--amber)", stroke: "var(--surface)", "pointer-events": "none" })
        : null);
  });

  $("tree-svg").replaceChildren(svg("svg", { width, height, role: "img", "aria-label": "Segment tree nodes" }, parts));

  const count = calendar.treeNodes.length;
  if (!shown) {
    $("tree-caption").textContent = `${count} nodes, each holding the fewest free rooms in its range of nights. `
      + "Hover a node for its range.";
    return;
  }
  const visits = shown.trace.visits;
  const full = visits.filter((v) => v.kind === "FULL").length;
  const nights = shown.to - shown.from + 1;
  const dates = `${shortDate(calendar.freeByNight[shown.from].night)} to ${shortDate(calendar.freeByNight[shown.to].night)}`;
  $("tree-caption").textContent = shown.kind === "query"
    ? `Availability query for ${dates} (${nights} nights): visited ${visits.length} of ${count} nodes, and ${full} FULL nodes `
      + `answered for their whole range. Answer: ${fmt(shown.trace.value)} free.`
    : `Range update for ${dates} (${shown.trace.value > 0 ? "+" : ""}${shown.trace.value} on ${nights} nights): `
      + `visited ${visits.length} of ${count} nodes. The ${full} FULL nodes were updated and given a lazy tag `
      + `instead of updating every night below them.`;
}
