// Shared helpers: calling the Java API, showing its errors, and building SVG.
// The frontend never computes an algorithm result; it only draws what the API returns.

export class ApiError extends Error {
  constructor(body) {
    super(body.message || "Request failed.");
    this.fields = body.fields || [];
  }
}

export async function api(path, { method = "GET", body } = {}) {
  let response;
  try {
    response = await fetch(path, {
      method,
      headers: body === undefined ? {} : { "Content-Type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError({ message: "Can't reach the server. Check that it's running on this address." });
  }
  const text = await response.text();
  const data = text ? JSON.parse(text) : null;
  if (!response.ok) {
    throw new ApiError(data || { message: `Request failed with status ${response.status}.` });
  }
  return data;
}

/** Shows an API error (with any field problems) in an error box, or hides the box when err is null. */
export function showError(box, err) {
  if (!err) {
    box.hidden = true;
    box.textContent = "";
    return;
  }
  box.hidden = false;
  box.textContent = err.message;
  if (err.fields && err.fields.length) {
    const ul = document.createElement("ul");
    for (const f of err.fields) {
      const li = document.createElement("li");
      li.textContent = `${f.field}: ${f.problem}`;
      ul.append(li);
    }
    box.append(ul);
  }
}

export function el(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (v === undefined || v === null || v === false) continue;
    if (k === "class") node.className = v;
    else if (k.startsWith("on")) node.addEventListener(k.slice(2), v);
    else node.setAttribute(k, v === true ? "" : v);
  }
  node.append(...children.flat().filter((c) => c !== null && c !== undefined));
  return node;
}

const SVG_NS = "http://www.w3.org/2000/svg";

export function svg(tag, attrs = {}, ...children) {
  const node = document.createElementNS(SVG_NS, tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (v !== undefined && v !== null) node.setAttribute(k, v);
  }
  node.append(...children.flat().filter((c) => c !== null && c !== undefined));
  return node;
}

/** An SVG <title>, which browsers show as a tooltip. */
export function tip(text) {
  return svg("title", {}, text);
}

export function css(name) {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
}

export function cap(word) {
  return word.charAt(0) + word.slice(1).toLowerCase();
}

const DAY_FMT = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short" });

export function shortDate(iso) {
  return DAY_FMT.format(new Date(iso + "T00:00:00"));
}

/** Adds days to a yyyy-mm-dd date, using local time (toISOString would shift it to UTC and can change the day). */
export function addDays(iso, days) {
  const d = new Date(iso + "T00:00:00");
  d.setDate(d.getDate() + days);
  const pad = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export function fmt(n) {
  return Number(n).toLocaleString("en-IN");
}
