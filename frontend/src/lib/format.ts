// Display formatting only. Dates are yyyy-mm-dd strings from the API, handled in local time.

const dayMonth = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short" });
const weekdayDay = new Intl.DateTimeFormat("en-GB", { weekday: "short", day: "numeric", month: "short" });
const fullDate = new Intl.DateTimeFormat("en-GB", { weekday: "long", day: "numeric", month: "long", year: "numeric" });
const time = new Intl.DateTimeFormat("en-GB", { hour: "2-digit", minute: "2-digit" });
const dateTime = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });

export function parseDay(iso: string): Date {
  return new Date(`${iso}T00:00:00`);
}

/** "14 Nov" */
export const shortDate = (iso: string) => dayMonth.format(parseDay(iso));
/** "Sat 14 Nov" */
export const dayDate = (iso: string) => weekdayDay.format(parseDay(iso));
/** "Saturday 14 November 2026" */
export const longDate = (iso: string) => fullDate.format(parseDay(iso));
/** "09:42" from an ISO instant */
export const clock = (instant: string) => time.format(new Date(instant));
/** "14 Nov, 09:42" from an ISO instant */
export const stamp = (instant: string) => dateTime.format(new Date(instant));

export function toIso(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export function todayIso(): string {
  return toIso(new Date());
}

export function addDays(iso: string, days: number): string {
  const d = parseDay(iso);
  d.setDate(d.getDate() + days);
  return toIso(d);
}

export function nightsBetween(from: string, to: string): number {
  return Math.round((parseDay(to).getTime() - parseDay(from).getTime()) / 86_400_000);
}

export const num = (n: number) => n.toLocaleString("en-IN");

export function pct(p: number, places = 1): string {
  return `${p.toFixed(places)}%`;
}

/** Small probabilities: "0.0012% (about 1 in 86,348)". */
export function rate(p: number): string {
  if (p <= 0) return "0%";
  const percent = p * 100;
  const shown = percent >= 0.1 ? percent.toFixed(2) : percent.toPrecision(2);
  return `${shown}% (about 1 in ${num(Math.round(1 / p))})`;
}

export function plural(n: number, word: string, many = `${word}s`): string {
  return `${num(n)} ${n === 1 ? word : many}`;
}

/** "DELUXE" -> "Deluxe" */
export function label(value: string): string {
  return value
    .toLowerCase()
    .split("_")
    .map((w, i) => (i === 0 ? w.charAt(0).toUpperCase() + w.slice(1) : w))
    .join(" ");
}

export function bytes(n: number): string {
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / 1024 / 1024).toFixed(1)} MB`;
}
