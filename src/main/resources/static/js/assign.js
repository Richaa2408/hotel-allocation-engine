// Room assignment screen: input, result, and a step-by-step replay of the Edmonds-Karp trace.

import { api, ApiError, cap, el, showError, svg, tip } from "./lib.js";

const $ = (id) => document.getElementById(id);

let request = null;   // { guests, rooms } as sent to the API
let result = null;    // AssignmentResult from the API
let step = 0;         // 0 = before any path, 1..n = after path n (highlighted), n + 1 = final flow

export function initAssign() {
  $("assign-load-sample").addEventListener("click", loadSample);
  $("assign-upload").addEventListener("change", uploadFile);
  $("assign-run").addEventListener("click", run);
  $("trace-first").addEventListener("click", () => goTo(0));
  $("trace-prev").addEventListener("click", () => goTo(step - 1));
  $("trace-next").addEventListener("click", () => goTo(step + 1));
  $("trace-last").addEventListener("click", () => goTo(Infinity));
  $("trace-cut").addEventListener("change", draw);
  loadSample();
}

export async function loadSample() {
  try {
    setRequest(await api("/api/sample/arrivals"));
    showError($("assign-error"), null);
  } catch (err) {
    showError($("assign-error"), err);
  }
}

function uploadFile(event) {
  const file = event.target.files[0];
  if (!file) return;
  file.text().then((text) => {
    try {
      const parsed = JSON.parse(text);
      if (!Array.isArray(parsed.guests) || !Array.isArray(parsed.rooms)) {
        throw new Error("The file needs a \"guests\" list and a \"rooms\" list.");
      }
      setRequest(parsed);
      showError($("assign-error"), null);
    } catch (err) {
      showError($("assign-error"), new ApiError({ message: `Couldn't use ${file.name}: ${err.message}` }));
    }
    event.target.value = "";
  });
}

function setRequest(req) {
  request = req;
  $("assign-json").value = JSON.stringify(req, null, 2);
  renderInputTables();
}

function renderInputTables() {
  const g = request.guests || [];
  const r = request.rooms || [];
  $("assign-input-summary").textContent = `${g.length} guests arriving, ${r.length} rooms free tonight.`;

  $("assign-guests").tBodies[0].replaceChildren(...g.map((guest) => {
    const needs = [];
    if (guest.needsAccessible) needs.push("accessible");
    if (guest.maxFloor !== null && guest.maxFloor !== undefined) needs.push(`floor ${guest.maxFloor} or below`);
    return el("tr", {},
      el("td", {}, guest.id), el("td", {}, guest.name), el("td", {}, cap(guest.roomType || "")),
      el("td", {}, needs.join(", ") || "none"), el("td", {}, guest.acceptsUpgrade ? "yes" : "no"));
  }));

  $("assign-rooms").tBodies[0].replaceChildren(...r.map((room) =>
    el("tr", {},
      el("td", {}, room.number), el("td", {}, cap(room.type || "")),
      el("td", { class: "num" }, String(room.floor)), el("td", {}, room.accessible ? "yes" : "no"))));
}

async function run() {
  const box = $("assign-error");
  let body;
  try {
    body = JSON.parse($("assign-json").value);
  } catch (err) {
    showError(box, new ApiError({ message: `The request JSON isn't valid: ${err.message}` }));
    return;
  }
  request = body;
  renderInputTables();
  $("assign-run").disabled = true;
  try {
    result = await api("/api/assignments", { method: "POST", body });
    showError(box, null);
    renderResult();
  } catch (err) {
    showError(box, err);
  } finally {
    $("assign-run").disabled = false;
  }
}

function renderResult() {
  $("assign-result").hidden = false;
  const r = result;
  $("assign-figures").replaceChildren(
    figure(`${r.assignedCount} of ${r.guestCount}`, "guests placed by max flow"),
    figure(`${r.firstFitAssignedCount}`, "placed by first-fit, in arrival order"),
    figure(`${r.unassigned.length}`, "guests not placed"),
  );

  $("assign-table").tBodies[0].replaceChildren(...r.assignments.map((a) =>
    el("tr", {},
      el("td", {}, a.guestName), el("td", {}, a.roomNumber), el("td", {}, cap(a.roomType)),
      el("td", {}, a.upgraded ? el("span", { class: "tag up" }, "upgrade") : ""))));

  $("assign-unplaced").replaceChildren(...(r.unassigned.length
    ? r.unassigned.map((u) => el("li", {}, el("b", {}, u.guestName), ` (${u.guestId}): ${u.reason}`))
    : [el("li", {}, "Everyone has a room.")]));

  // When every room is on the cut, the limit is the total number of rooms, not a particular kind of room.
  $("assign-bottleneck").textContent = r.bottleneckRooms.length === 0
    ? "None. Every guest who could be placed was placed."
    : r.bottleneckRooms.length === r.roomCount
      ? `All ${r.roomCount} rooms are on the cut: the hotel is simply full tonight, not short of one kind of room.`
      : r.bottleneckRooms.map((n) => `Room ${n}`).join(", ");

  const hasTrace = r.trace !== null;
  $("assign-trace").hidden = !hasTrace;
  $("assign-no-trace").hidden = hasTrace;
  if (!hasTrace) {
    $("assign-no-trace").textContent =
      "The step-by-step drawing is only produced for up to 200 guests and rooms combined. The result above is complete.";
    return;
  }
  $("trace-cut").checked = false;
  goTo(0);
}

function figure(value, label) {
  return el("div", { class: "figure" }, el("b", {}, value), el("span", {}, label));
}

function goTo(target) {
  const final = result.trace.steps.length + 1;
  step = Math.max(0, Math.min(final, target));
  $("trace-first").disabled = $("trace-prev").disabled = step === 0;
  $("trace-next").disabled = $("trace-last").disabled = step === final;
  $("trace-cut").disabled = step !== final;
  if (step !== final) $("trace-cut").checked = false;
  draw();
}

// ---------------------------------------------------------------------------
// Drawing
// ---------------------------------------------------------------------------

function draw() {
  const trace = result.trace;
  const nodes = trace.nodes;
  const label = (id) => nodes[id].label;
  const showCut = $("trace-cut").checked;

  // Replay the recorded pushes up to the current step. Odd edge ids are reverse edges:
  // pushing on one takes flow back off its forward edge (id ^ 1).
  const total = trace.steps.length;
  const flow = new Map();
  for (let i = 0; i < Math.min(step, total); i++) {
    const s = trace.steps[i];
    for (const e of s.edges) {
      const forward = e % 2 === 0 ? e : e ^ 1;
      flow.set(forward, (flow.get(forward) || 0) + (e % 2 === 0 ? s.bottleneck : -s.bottleneck));
    }
  }
  const current = step >= 1 && step <= total ? trace.steps[step - 1] : null;
  const onPath = new Set();
  const reversed = new Set();
  if (current) {
    for (const e of current.edges) {
      if (e % 2 === 0) onPath.add(e);
      else reversed.add(e ^ 1);
    }
  }
  const cut = new Set(showCut ? trace.minCutEdges : []);
  const sourceSide = new Set(showCut ? trace.sourceSide : []);

  // Layout: four columns. Guests and rooms are spaced evenly; source and sink are centred.
  const guests = nodes.filter((n) => n.kind === "GUEST");
  const rooms = nodes.filter((n) => n.kind === "ROOM");
  const rowH = 22;
  const rows = Math.max(guests.length, rooms.length, 1);
  const height = rows * rowH + 30;
  const width = 940;
  const colX = { SOURCE: 40, GUEST: 250, ROOM: 660, SINK: 900 };
  const pos = new Map();
  const place = (list) => {
    const top = (height - list.length * rowH) / 2 + rowH / 2;
    list.forEach((n, i) => pos.set(n.id, { x: colX[n.kind], y: top + i * rowH }));
  };
  place(guests);
  place(rooms);
  for (const n of nodes) {
    if (n.kind === "SOURCE" || n.kind === "SINK") pos.set(n.id, { x: colX[n.kind], y: height / 2 });
  }

  const base = [], used = [], highlighted = [];
  for (const e of trace.edges) {
    const a = pos.get(e.from), b = pos.get(e.to);
    const f = flow.get(e.id) || 0;
    let cls = "edge", stroke = "var(--edge)", w = 1, dash = null, layer = base, opacity = 0.45;
    if (f > 0) { stroke = "var(--text)"; w = 1.6; layer = used; opacity = 1; }
    if (onPath.has(e.id)) { stroke = "var(--accent)"; w = 3; layer = highlighted; opacity = 1; }
    if (reversed.has(e.id)) { stroke = "var(--amber)"; w = 3; dash = "6 4"; layer = highlighted; opacity = 1; }
    if (cut.has(e.id)) { stroke = "var(--red)"; w = 3; layer = highlighted; opacity = 1; }
    layer.push(svg("line", {
      class: cls, x1: a.x, y1: a.y, x2: b.x, y2: b.y, stroke, "stroke-width": w, "stroke-dasharray": dash,
      "stroke-opacity": opacity,
    }, tip(`${label(e.from)} to ${label(e.to)}: flow ${f} of ${e.capacity}`)));
  }

  const nodeEls = nodes.map((n) => {
    const p = pos.get(n.id);
    const inPath = current && current.path.includes(n.id);
    const fill = sourceSide.has(n.id) ? "var(--accent-weak)" : "var(--surface)";
    const stroke = inPath ? "var(--accent)" : "var(--muted)";
    const r = n.kind === "SOURCE" || n.kind === "SINK" ? 9 : 5;
    const textAttrs = n.kind === "GUEST"
      ? { x: p.x - 10, "text-anchor": "end" }
      : n.kind === "ROOM" ? { x: p.x + 10, "text-anchor": "start" }
        : { x: p.x, "text-anchor": "middle", dy: -16 };
    return svg("g", {},
      svg("circle", { cx: p.x, cy: p.y, r, fill, stroke, "stroke-width": inPath ? 2.5 : 1.2 }),
      svg("text", {
        ...textAttrs, y: p.y, dy: textAttrs.dy ?? 4, "font-size": 12, fill: "var(--text)",
        stroke: "var(--surface)", "stroke-width": 3, "paint-order": "stroke",
        "font-weight": inPath ? 600 : 400,
      }, n.label));
  });

  const headers = [["Source", colX.SOURCE], ["Guests", colX.GUEST], ["Rooms", colX.ROOM], ["Sink", colX.SINK]]
    .map(([t, x]) => svg("text", { x, y: 12, "text-anchor": "middle", "font-size": 11, fill: "var(--muted)" }, t));

  $("trace-svg").replaceChildren(svg("svg", {
    width, height: height + 10, viewBox: `0 -6 ${width} ${height + 10}`, role: "img",
    "aria-label": `Flow network with ${guests.length} guests and ${rooms.length} rooms`,
  }, headers, base, used, highlighted, nodeEls));

  $("trace-caption").textContent = caption(current, label, showCut);
}

function caption(current, label, showCut) {
  const trace = result.trace;
  const total = trace.steps.length;
  if (step === 0) {
    return `Before the first step: no flow yet. Edmonds-Karp found ${total} augmenting path${total === 1 ? "" : "s"}. Press Next to see them in order.`;
  }
  if (step === total + 1) {
    if (showCut) {
      return `Minimum cut: ${trace.minCutEdges.length} full edges with total capacity ${result.assignedCount}, `
        + `equal to the max flow. Shaded nodes are still reachable from Source. No arrangement can place more guests.`;
    }
    return `Final flow: ${result.assignedCount} guests placed. No path from Source to Sink has spare capacity left.`;
  }
  const route = current.path.map(label).join(" → ");
  let text = `Step ${step} of ${total}: send ${current.bottleneck} along ${route}. Total flow is now ${current.totalFlow}.`;
  const rev = current.edges.filter((e) => e % 2 === 1);
  if (rev.length) {
    // A reverse edge runs room -> guest; its forward edge was guest -> room.
    const moves = rev.map((e) => {
      const fwd = trace.edges.find((x) => x.id === (e ^ 1));
      return `takes ${label(fwd.to)} back from ${label(fwd.from)}`;
    });
    text += ` This path ${moves.join(" and ")}, who moves to another room.`;
  }
  return text;
}
