// Entry point: tab switching, the sample-data reload, and starting each screen.

import { api } from "./lib.js";
import { initAssign, loadSample } from "./assign.js";
import { initAvailability, refresh as refreshAvailability } from "./availability.js";
import { initScreening, refresh as refreshScreening } from "./screening.js";

const TABS = ["assign", "availability", "screening"];

function selectTab(name, updateHash = true) {
  if (!TABS.includes(name)) name = "assign";
  for (const t of TABS) {
    const selected = t === name;
    document.getElementById(`tab-${t}`).setAttribute("aria-selected", String(selected));
    document.getElementById(`panel-${t}`).hidden = !selected;
  }
  if (updateHash) history.replaceState(null, "", `#${name}`);
}

for (const t of TABS) {
  document.getElementById(`tab-${t}`).addEventListener("click", () => selectTab(t));
}
selectTab(location.hash.slice(1), false);
window.addEventListener("hashchange", () => selectTab(location.hash.slice(1), false));

document.getElementById("sample-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const status = document.getElementById("sample-status");
  const seed = document.getElementById("sample-seed").value;
  status.textContent = "Reloading sample data...";
  try {
    const s = await api(`/api/sample/reset?seed=${encodeURIComponent(seed)}`, { method: "POST" });
    status.textContent = `Sample hotel ${s.seed} loaded: ${s.bookingsMade} future bookings on the calendar, `
      + `${s.blocklistEntries} blocklist entries, ${s.arrivals} arrivals for ${s.roomsFreeTonight} free rooms tonight.`;
    await Promise.all([loadSample(), refreshAvailability(), refreshScreening()]);
  } catch (err) {
    status.textContent = `Couldn't reload: ${err.message}`;
  }
});

initAssign();
initAvailability();
initScreening();
