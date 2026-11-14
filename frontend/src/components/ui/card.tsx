import type { HTMLAttributes, ReactNode } from "react";
import { cn } from "@/lib/utils";

export function Card({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("rounded-lg border border-line bg-surface shadow-card", className)} {...props} />;
}

export function CardHeader({
  title,
  description,
  actions,
  className,
}: {
  title: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("flex flex-wrap items-start justify-between gap-3 border-b border-line px-5 py-4", className)}>
      <div className="min-w-0">
        <h2 className="text-[15px] font-semibold text-ink">{title}</h2>
        {description && <p className="mt-0.5 text-sm text-muted">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  );
}

export function CardBody({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("px-5 py-4", className)} {...props} />;
}

/** A computed figure with a label. Only ever filled with numbers from the API. */
export function Stat({
  label,
  value,
  detail,
  tone = "default",
}: {
  label: string;
  value: ReactNode;
  detail?: ReactNode;
  tone?: "default" | "warn" | "bad" | "good";
}) {
  const toneClass = {
    default: "text-ink",
    warn: "text-amber-700",
    bad: "text-coral-700",
    good: "text-ok-600",
  }[tone];
  return (
    <Card className="px-5 py-4">
      <p className="text-[13px] font-medium text-muted">{label}</p>
      <p className={cn("mt-1 text-2xl font-semibold tracking-tight tabular", toneClass)}>{value}</p>
      {detail && <p className="mt-1 text-[13px] text-muted">{detail}</p>}
    </Card>
  );
}
