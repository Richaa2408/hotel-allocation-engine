import type { ReactNode } from "react";

/** Colours for room types in charts: one hue family, distinct steps, readable on white. */
export const TYPE_COLORS: Record<string, string> = {
  ALL: "#10213a",
  STANDARD: "#0f766e",
  DELUXE: "#2f6fb0",
  EXECUTIVE: "#b45309",
  SUITE: "#8a5a9e",
};

export const axisProps = {
  stroke: "#8a93a0",
  fontSize: 12,
  tickLine: false,
  axisLine: { stroke: "#e3e0d8" },
};

export const gridProps = { stroke: "#efece5", vertical: false };

export function ChartTooltip({ active, payload, label, format }: {
  active?: boolean;
  payload?: { name: string; value: number; color: string }[];
  label?: ReactNode;
  format?: (v: number) => string;
}) {
  if (!active || !payload?.length) return null;
  return (
    <div className="rounded-md border border-line bg-surface px-3 py-2 text-xs shadow-pop">
      <p className="mb-1 font-semibold text-ink">{label}</p>
      {payload.map((p) => (
        <p key={p.name} className="flex items-center gap-2 text-muted">
          <span className="size-2 rounded-full" style={{ background: p.color }} />
          {p.name}: <span className="font-medium text-ink tabular">{format ? format(p.value) : p.value}</span>
        </p>
      ))}
    </div>
  );
}
