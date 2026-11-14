import { createContext, useCallback, useContext, useState, type ReactNode } from "react";
import { AlertTriangle, CheckCircle2, Info, X, XCircle } from "lucide-react";
import { cn } from "@/lib/utils";
import { ApiError } from "@/lib/api";

/** An API error: the message, plus any fields that need fixing. */
export function ErrorBox({ error, className }: { error: unknown; className?: string }) {
  if (!error) return null;
  const err = error instanceof ApiError ? error : new ApiError(0, error instanceof Error ? error.message : String(error));
  return (
    <div role="alert" className={cn("rounded-md border border-coral-500/40 bg-coral-50 px-3.5 py-2.5 text-sm text-coral-700", className)}>
      <div className="flex gap-2">
        <XCircle className="mt-0.5 size-4 shrink-0" aria-hidden />
        <div>
          <p>{err.message}</p>
          {err.fields.length > 0 && (
            <ul className="mt-1 list-disc pl-4">
              {err.fields.map((f) => (
                <li key={f.field}>
                  {f.field}: {f.problem}
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </div>
  );
}

const noticeTones = {
  info: { box: "border-navy-300/50 bg-navy-900/[0.03] text-navy-800", icon: Info },
  ok: { box: "border-ok-600/30 bg-ok-50 text-ok-600", icon: CheckCircle2 },
  warn: { box: "border-amber-600/30 bg-amber-50 text-amber-700", icon: AlertTriangle },
  bad: { box: "border-coral-500/40 bg-coral-50 text-coral-700", icon: XCircle },
};

export function Notice({
  tone = "info",
  title,
  children,
  className,
}: {
  tone?: keyof typeof noticeTones;
  title?: ReactNode;
  children?: ReactNode;
  className?: string;
}) {
  const { box, icon: Icon } = noticeTones[tone];
  return (
    <div className={cn("rounded-md border px-3.5 py-3 text-sm", box, className)}>
      <div className="flex gap-2.5">
        <Icon className="mt-0.5 size-4 shrink-0" aria-hidden />
        <div className="min-w-0">
          {title && <p className="font-semibold">{title}</p>}
          {children && <div className={cn(title && "mt-0.5", "text-ink/80")}>{children}</div>}
        </div>
      </div>
    </div>
  );
}

export function EmptyState({ title, children, action }: { title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center px-6 py-12 text-center">
      <p className="text-sm font-semibold text-ink">{title}</p>
      {children && <p className="mt-1 max-w-sm text-sm text-muted">{children}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}

/** Placeholder blocks while data loads (for waits longer than a moment). */
export function Skeleton({ className }: { className?: string }) {
  return <div className={cn("animate-pulse rounded-md bg-sunken", className)} />;
}

export function LoadingRows({ rows = 6 }: { rows?: number }) {
  return (
    <div className="space-y-2 p-5" aria-busy="true" aria-label="Loading">
      {Array.from({ length: rows }, (_, i) => (
        <Skeleton key={i} className="h-8" />
      ))}
    </div>
  );
}

// ---------- toasts ----------

interface Toast {
  id: number;
  tone: "ok" | "bad" | "info";
  text: string;
}

const ToastContext = createContext<(tone: Toast["tone"], text: string) => void>(() => {});

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const push = useCallback((tone: Toast["tone"], text: string) => {
    const id = Date.now() + Math.random();
    setToasts((t) => [...t, { id, tone, text }]);
    setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), 5000);
  }, []);
  return (
    <ToastContext.Provider value={push}>
      {children}
      <div className="pointer-events-none fixed bottom-4 right-4 z-[60] flex w-[min(24rem,calc(100%-2rem))] flex-col gap-2" aria-live="polite">
        {toasts.map((t) => (
          <div
            key={t.id}
            className={cn(
              "pointer-events-auto flex items-start gap-2 rounded-md border bg-surface px-3.5 py-2.5 text-sm shadow-pop",
              t.tone === "bad" ? "border-coral-500/40" : t.tone === "ok" ? "border-ok-600/30" : "border-line",
            )}
          >
            {t.tone === "ok" ? (
              <CheckCircle2 className="mt-0.5 size-4 shrink-0 text-ok-600" />
            ) : t.tone === "bad" ? (
              <XCircle className="mt-0.5 size-4 shrink-0 text-coral-700" />
            ) : (
              <Info className="mt-0.5 size-4 shrink-0 text-navy-700" />
            )}
            <p className="flex-1 text-ink">{t.text}</p>
            <button className="text-muted hover:text-ink" aria-label="Dismiss" onClick={() => setToasts((x) => x.filter((y) => y.id !== t.id))}>
              <X className="size-4" />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  return useContext(ToastContext);
}
