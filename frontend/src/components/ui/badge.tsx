import type { ReactNode } from "react";
import { cn } from "@/lib/utils";
import type { ReservationStatus } from "@/lib/types";

const tones = {
  neutral: "border-line-strong bg-sunken text-muted",
  teal: "border-teal-100 bg-teal-50 text-teal-700",
  amber: "border-amber-600/25 bg-amber-50 text-amber-700",
  coral: "border-coral-500/25 bg-coral-50 text-coral-700",
  green: "border-ok-600/20 bg-ok-50 text-ok-600",
  navy: "border-navy-300/40 bg-navy-900/5 text-navy-800",
} as const;

export type Tone = keyof typeof tones;

/** A small squared label. Not a pill. */
export function Badge({ tone = "neutral", children, className }: { tone?: Tone; children: ReactNode; className?: string }) {
  return (
    <span className={cn("inline-flex items-center gap-1 rounded-[4px] border px-1.5 py-px text-xs font-medium", tones[tone], className)}>
      {children}
    </span>
  );
}

const statusTone: Record<ReservationStatus, Tone> = {
  CONFIRMED: "teal",
  PENDING_REVIEW: "coral",
  WAITLISTED: "amber",
  CHECKED_IN: "navy",
  CHECKED_OUT: "neutral",
  CANCELLED: "neutral",
};

const statusText: Record<ReservationStatus, string> = {
  CONFIRMED: "Confirmed",
  PENDING_REVIEW: "Held for review",
  WAITLISTED: "Waitlisted",
  CHECKED_IN: "In house",
  CHECKED_OUT: "Checked out",
  CANCELLED: "Cancelled",
};

export function StatusBadge({ status }: { status: ReservationStatus }) {
  return <Badge tone={statusTone[status]}>{statusText[status]}</Badge>;
}

export function statusLabel(status: ReservationStatus) {
  return statusText[status];
}
