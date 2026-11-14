import { num } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { BloomTrace } from "@/lib/types";

/**
 * The k bit positions one value hashes to: a box per hash (1 or 0), and where each falls across the
 * whole bit array. Any 0 means "definitely not listed".
 */
export function BloomBits({ trace, bitCount }: { trace: BloomTrace; bitCount: number }) {
  return (
    <div className="space-y-3">
      <div className="flex flex-wrap gap-1.5">
        {trace.positions.map((p, i) => (
          <div
            key={i}
            className={cn(
              "min-w-[4.5rem] rounded-[4px] border px-2 py-1 text-center font-mono text-xs",
              trace.bitWasSet[i] ? "border-teal-600/40 bg-teal-50 text-teal-800" : "border-2 border-coral-500 bg-surface text-coral-700",
            )}
            title={`Hash ${i + 1} gives bit ${num(p)}`}
          >
            <span className="block text-sm font-semibold">{trace.bitWasSet[i] ? 1 : 0}</span>#{num(p)}
          </div>
        ))}
      </div>
      <div className="relative h-7" aria-hidden>
        <div className="absolute inset-x-0 top-2.5 h-2 rounded-full bg-sunken ring-1 ring-line" />
        {trace.positions.map((p, i) => (
          <span
            key={i}
            className={cn("absolute top-0.5 h-6 w-0.5", trace.bitWasSet[i] ? "bg-teal-600" : "bg-coral-500")}
            style={{ left: `${(p / Math.max(1, bitCount - 1)) * 100}%` }}
          />
        ))}
      </div>
      <div className="flex justify-between text-[11px] text-muted">
        <span>bit 0</span>
        <span>bit {num(bitCount - 1)}</span>
      </div>
    </div>
  );
}
