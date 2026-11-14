import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router";
import { CartesianGrid, Line, LineChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { AlertTriangle, ArrowRight, Info, XCircle } from "lucide-react";
import { PageHeader } from "@/components/layout/AppShell";
import { Card, CardHeader, Stat } from "@/components/ui/card";
import { Select } from "@/components/ui/form";
import { EmptyState, ErrorBox, LoadingRows } from "@/components/ui/feedback";
import { ChartTooltip, TYPE_COLORS, axisProps, gridProps } from "@/components/charts";
import { api } from "@/lib/api";
import { clock, label, pct, plural, shortDate, todayIso } from "@/lib/format";
import { cn } from "@/lib/utils";
import { ROOM_TYPES, type Overview as OverviewData } from "@/lib/types";

const actionText: Record<string, string> = {
  RESERVATION_CREATED: "New booking",
  RESERVATION_HELD: "Held for review",
  RESERVATION_WAITLISTED: "Waitlisted",
  RESERVATION_CHANGED: "Booking changed",
  RESERVATION_CANCELLED: "Cancelled",
  RESERVATION_APPROVED: "Approved",
  WAITLIST_PROMOTED: "Confirmed from waitlist",
  CHECKED_IN: "Checked in",
  CHECKED_OUT: "Checked out",
  ASSIGNMENT_RUN: "Rooms assigned",
  ROOM_ASSIGNED: "Room assigned by hand",
  ROOM_UNASSIGNED: "Room taken off",
  ROOM_REASSIGNED: "Guest moved",
  ROOM_REASSIGN_FAILED: "Couldn't move guest",
  MAINTENANCE_ADDED: "Room out of service",
  MAINTENANCE_REMOVED: "Room back in service",
  HOUSEKEEPING: "Housekeeping",
  WATCHLIST_ADDED: "Watchlist entry added",
  WATCHLIST_REMOVED: "Watchlist entry removed",
};

export default function Overview() {
  const [type, setType] = useState("ALL");
  const overview = useQuery({ queryKey: ["overview"], queryFn: () => api.get<OverviewData>("/api/overview"), refetchInterval: 60_000 });
  const d = overview.data;
  const today = todayIso();

  return (
    <>
      <PageHeader title="Overview" description="What's happening at the hotel today. Every figure is computed from the live bookings and inventory." />
      <ErrorBox error={overview.error} className="mb-4" />
      {!d ? (
        <LoadingRows />
      ) : (
        <>
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <Stat label="Occupancy tonight" value={pct(d.kpis.occupancy)} detail={`${d.kpis.occupiedTonight} of ${d.kpis.sellableTonight} sellable rooms; ${d.kpis.availableTonight} free`} />
            <Stat label="Arrivals today" value={d.kpis.arrivalsToday} detail={d.kpis.awaitingAllocation ? `${plural(d.kpis.awaitingAllocation, "room request")} without a room` : "Every arrival has a room"} tone={d.kpis.awaitingAllocation ? "warn" : "default"} />
            <Stat label="In house" value={d.kpis.inHouse} detail={`${plural(d.kpis.departuresToday, "departure")} today`} />
            <Stat label="Held for review" value={d.kpis.pendingReviews} detail={`${plural(d.kpis.waitlisted, "booking")} on the waitlist`} tone={d.kpis.pendingReviews ? "bad" : "default"} />
          </div>

          <div className="mt-6 grid gap-6 xl:grid-cols-[1.6fr_1fr]">
            <Card>
              <CardHeader
                title="Occupancy, last 30 and next 30 nights"
                description="Booked rooms as a share of all rooms. Future nights show bookings taken so far."
                actions={
                  <Select aria-label="Room type" className="w-40" value={type} onChange={(e) => setType(e.target.value)}>
                    <option value="ALL">All rooms</option>
                    {ROOM_TYPES.map((t) => <option key={t} value={t}>{label(t)}</option>)}
                  </Select>
                }
              />
              <div className="h-72 px-2 py-4">
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={d.trend.map((p) => ({ night: p.night, value: p.occupancy[type] }))} margin={{ left: 0, right: 16, top: 8, bottom: 0 }}>
                    <CartesianGrid {...gridProps} />
                    <XAxis dataKey="night" {...axisProps} tickFormatter={shortDate} minTickGap={28} />
                    <YAxis {...axisProps} domain={[0, 100]} tickFormatter={(v) => `${v}%`} width={44} />
                    <Tooltip content={<ChartTooltip format={(v) => pct(v)} />} labelFormatter={(l) => shortDate(String(l))} />
                    <ReferenceLine x={today} stroke="#8a93a0" strokeDasharray="3 3" label={{ value: "Today", position: "insideTopRight", fontSize: 11, fill: "#5b6573" }} />
                    <Line type="monotone" dataKey="value" name={type === "ALL" ? "All rooms" : label(type)} stroke={TYPE_COLORS[type]} strokeWidth={2} dot={false} isAnimationActive={false} />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            </Card>

            <Card>
              <CardHeader title="Today's activity" description="Changes made today, newest first." />
              {d.activity.length === 0 ? (
                <EmptyState title="Nothing changed yet today">Bookings, check-ins and room moves appear here as they happen.</EmptyState>
              ) : (
                <ol className="max-h-[19rem] space-y-0 overflow-y-auto px-5 py-3">
                  {d.activity.map((a, i) => (
                    <li key={i} className="relative border-l border-line pb-3 pl-4">
                      <span className="absolute -left-[4.5px] top-1.5 size-2 rounded-full bg-teal-600" />
                      <p className="text-[12.5px] text-muted">
                        <span className="tabular">{clock(a.at)}</span> · {actionText[a.action] ?? label(a.action)}
                        {a.reference && <> · {a.reference}</>} · {a.actor}
                      </p>
                      <p className="text-sm">{a.detail}</p>
                    </li>
                  ))}
                </ol>
              )}
            </Card>
          </div>

          <Card className="mt-6">
            <CardHeader title="Needs attention" description="From inventory, room assignment and watchlist screening." />
            {d.alerts.length === 0 ? (
              <EmptyState title="Nothing needs attention" />
            ) : (
              <ul className="divide-y divide-line">
                {d.alerts.map((a, i) => {
                  const Icon = a.severity === "CRITICAL" ? XCircle : a.severity === "WARNING" ? AlertTriangle : Info;
                  return (
                    <li key={i}>
                      <Link to={a.link} className="flex items-center gap-3 px-5 py-3 text-sm hover:bg-canvas">
                        <Icon className={cn("size-4 shrink-0", a.severity === "CRITICAL" ? "text-coral-700" : a.severity === "WARNING" ? "text-amber-600" : "text-navy-700")} />
                        <span className="flex-1">{a.message}</span>
                        <ArrowRight className="size-4 text-subtle" />
                      </Link>
                    </li>
                  );
                })}
              </ul>
            )}
          </Card>
        </>
      )}
    </>
  );
}
