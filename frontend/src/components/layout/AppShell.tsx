import { useEffect, useState, type ReactNode } from "react";
import { NavLink, Outlet, useLocation } from "react-router";
import {
  BarChart3,
  BedDouble,
  CalendarCheck2,
  CalendarRange,
  FlaskConical,
  LayoutDashboard,
  LifeBuoy,
  LogOut,
  Menu,
  Settings,
  ShieldCheck,
  X,
} from "lucide-react";
import { roleLabel, useAuth } from "@/auth/AuthContext";
import { cn } from "@/lib/utils";
import { longDate, todayIso } from "@/lib/format";

const main = [
  { to: "/", label: "Overview", icon: LayoutDashboard, end: true },
  { to: "/reservations", label: "Reservations", icon: CalendarCheck2 },
  { to: "/inventory", label: "Room Inventory", icon: CalendarRange },
  { to: "/assignment", label: "Room Assignment", icon: BedDouble },
  { to: "/risk", label: "Risk & Duplicates", icon: ShieldCheck },
  { to: "/lab", label: "Algorithm Lab", icon: FlaskConical },
  { to: "/analytics", label: "Analytics", icon: BarChart3 },
];

function Brand() {
  return (
    <div className="flex items-center gap-2.5 px-5 pb-5 pt-5">
      <img src="/favicon.svg" alt="" className="size-8" />
      <div>
        <p className="text-[15px] font-semibold tracking-tight text-white">StayFlow</p>
        <p className="text-xs text-navy-300">Hotel operations</p>
      </div>
    </div>
  );
}

function NavItem({ to, label, icon: Icon, end }: { to: string; label: string; icon: typeof Menu; end?: boolean }) {
  return (
    <NavLink
      to={to}
      end={end}
      className={({ isActive }) =>
        cn(
          "flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors",
          isActive ? "bg-white/10 text-white" : "text-navy-300 hover:bg-white/5 hover:text-white",
        )
      }
    >
      <Icon className="size-4" aria-hidden />
      {label}
    </NavLink>
  );
}

function Sidebar({ onNavigate }: { onNavigate?: () => void }) {
  const { me, logout, can } = useAuth();
  return (
    <div className="flex h-full flex-col bg-navy-900" onClick={(e) => (e.target as HTMLElement).closest("a") && onNavigate?.()}>
      <Brand />
      <nav aria-label="Main" className="flex-1 space-y-0.5 overflow-y-auto px-3">
        <p className="px-3 pb-1.5 text-[11px] font-semibold uppercase tracking-wider text-navy-300/70">Main menu</p>
        {main.map((item) => (
          <NavItem key={item.to} {...item} />
        ))}
        <p className="px-3 pb-1.5 pt-5 text-[11px] font-semibold uppercase tracking-wider text-navy-300/70">System</p>
        {can("ADMIN") && <NavItem to="/settings" label="Settings" icon={Settings} />}
        <NavItem to="/help" label="Help & docs" icon={LifeBuoy} />
      </nav>
      {me && (
        <div className="border-t border-white/10 p-3">
          <div className="flex items-center justify-between gap-2 rounded-md px-2 py-1.5">
            <div className="min-w-0">
              <p className="truncate text-sm font-medium text-white">{me.displayName}</p>
              <p className="text-xs text-navy-300">{roleLabel[me.role]}</p>
            </div>
            <button
              onClick={() => logout()}
              className="rounded-md p-2 text-navy-300 hover:bg-white/10 hover:text-white"
              aria-label="Sign out"
              title="Sign out"
            >
              <LogOut className="size-4" />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

export function AppShell() {
  const [open, setOpen] = useState(false);
  const location = useLocation();
  useEffect(() => setOpen(false), [location.pathname]);

  return (
    <div className="flex h-full">
      <aside className="hidden w-60 shrink-0 lg:block">
        <div className="fixed inset-y-0 w-60">
          <Sidebar />
        </div>
      </aside>

      {open && (
        <div className="fixed inset-0 z-40 lg:hidden" role="dialog" aria-modal="true" aria-label="Menu">
          <div className="absolute inset-0 bg-navy-950/40" onClick={() => setOpen(false)} />
          <div className="absolute inset-y-0 left-0 w-64">
            <Sidebar onNavigate={() => setOpen(false)} />
          </div>
          <button className="absolute left-[16.5rem] top-3 rounded-md bg-surface p-2" onClick={() => setOpen(false)} aria-label="Close menu">
            <X className="size-4" />
          </button>
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-30 flex items-center gap-3 border-b border-line bg-surface/95 px-4 py-2.5 backdrop-blur lg:hidden">
          <button className="rounded-md p-2 hover:bg-sunken" onClick={() => setOpen(true)} aria-label="Open menu">
            <Menu className="size-5" />
          </button>
          <img src="/favicon.svg" alt="" className="size-6" />
          <span className="font-semibold">StayFlow</span>
        </header>
        <main className="mx-auto w-full max-w-[1400px] flex-1 px-4 py-6 sm:px-6 lg:px-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

export function PageHeader({ title, description, actions }: { title: string; description?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div className="min-w-0">
        <p className="text-[13px] text-muted">{longDate(todayIso())}</p>
        <h1 className="mt-0.5 text-2xl font-semibold tracking-tight text-ink">{title}</h1>
        {description && <p className="mt-1 max-w-3xl text-sm text-muted">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  );
}
