import type { ReactNode } from "react";
import { Dialog as D, Tabs as T, Tooltip as TT } from "radix-ui";
import { X } from "lucide-react";
import { cn } from "@/lib/utils";

/** A panel that slides in from the right; focus is trapped inside and restored on close. */
export function Sheet({
  open,
  onOpenChange,
  title,
  description,
  children,
  footer,
  wide,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: ReactNode;
  description?: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
  wide?: boolean;
}) {
  return (
    <D.Root open={open} onOpenChange={onOpenChange}>
      <D.Portal>
        <D.Overlay className="fixed inset-0 z-40 bg-navy-950/30" />
        <D.Content
          className={cn(
            "fixed inset-y-0 right-0 z-50 flex w-full flex-col border-l border-line bg-surface shadow-pop",
            wide ? "max-w-3xl" : "max-w-xl",
          )}
        >
          <div className="flex items-start justify-between gap-4 border-b border-line px-6 py-4">
            <div className="min-w-0">
              <D.Title className="text-base font-semibold text-ink">{title}</D.Title>
              {description ? (
                <D.Description className="mt-0.5 text-sm text-muted">{description}</D.Description>
              ) : (
                <D.Description className="sr-only">Details</D.Description>
              )}
            </div>
            <D.Close className="rounded-md p-1.5 text-muted hover:bg-sunken hover:text-ink" aria-label="Close">
              <X className="size-4" />
            </D.Close>
          </div>
          <div className="flex-1 overflow-y-auto px-6 py-5">{children}</div>
          {footer && <div className="flex flex-wrap justify-end gap-2 border-t border-line px-6 py-3">{footer}</div>}
        </D.Content>
      </D.Portal>
    </D.Root>
  );
}

/** A centred dialog for confirmations. */
export function Modal({
  open,
  onOpenChange,
  title,
  description,
  children,
  footer,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: ReactNode;
  description?: ReactNode;
  children?: ReactNode;
  footer?: ReactNode;
}) {
  return (
    <D.Root open={open} onOpenChange={onOpenChange}>
      <D.Portal>
        <D.Overlay className="fixed inset-0 z-40 bg-navy-950/30" />
        <D.Content className="fixed left-1/2 top-1/2 z-50 w-[calc(100%-2rem)] max-w-md -translate-x-1/2 -translate-y-1/2 rounded-lg border border-line bg-surface p-6 shadow-pop">
          <D.Title className="text-base font-semibold text-ink">{title}</D.Title>
          {description ? (
            <D.Description className="mt-1.5 text-sm text-muted">{description}</D.Description>
          ) : (
            <D.Description className="sr-only">Confirm</D.Description>
          )}
          {children && <div className="mt-4">{children}</div>}
          {footer && <div className="mt-6 flex justify-end gap-2">{footer}</div>}
        </D.Content>
      </D.Portal>
    </D.Root>
  );
}

export const Tabs = T.Root;

export function TabsList({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <T.List className={cn("flex gap-1 overflow-x-auto border-b border-line", className)}>{children}</T.List>
  );
}

export function TabsTrigger({ value, children }: { value: string; children: ReactNode }) {
  return (
    <T.Trigger
      value={value}
      className="-mb-px whitespace-nowrap border-b-2 border-transparent px-3 py-2 text-sm font-medium text-muted hover:text-ink data-[state=active]:border-teal-600 data-[state=active]:text-ink"
    >
      {children}
    </T.Trigger>
  );
}

export const TabsContent = T.Content;

export function Tip({ content, children }: { content: ReactNode; children: ReactNode }) {
  return (
    <TT.Root delayDuration={200}>
      <TT.Trigger asChild>{children}</TT.Trigger>
      <TT.Portal>
        <TT.Content
          sideOffset={6}
          className="z-50 max-w-xs rounded-md bg-navy-900 px-2.5 py-1.5 text-xs leading-relaxed text-white shadow-pop"
        >
          {content}
        </TT.Content>
      </TT.Portal>
    </TT.Root>
  );
}

export const TipProvider = TT.Provider;
