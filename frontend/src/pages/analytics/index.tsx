import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Bar, BarChart, CartesianGrid, Legend, Line, LineChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { PageHeader } from "@/components/layout/AppShell";
import { Card, CardBody, CardHeader, Stat } from "@/components/ui/card";
import { Select } from "@/components/ui/form";
import { StatusBadge } from "@/components/ui/badge";
import { ErrorBox, LoadingRows } from "@/components/ui/feedback";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/overlay";
import { Table, TableWrap, Td, Th, Tr } from "@/components/ui/table";
import { ChartTooltip, TYPE_COLORS, axisProps, gridProps } from "@/components/charts";
import { useAuth } from "@/auth/AuthContext";
import { api, qs } from "@/lib/api";
import { label, num, pct, plural, shortDate, stamp, todayIso } from "@/lib/format";
import { ROOM_TYPES, type Activity, type Analytics as Data } from "@/lib/types";

export default function Analytics() {
  const { can } = useAuth();
  const [range, setRange] = useState("30");
  const data = useQuery({
    queryKey: ["analytics", range],
    queryFn: () => api.get<Data>(`/api/analytics${qs({ past: range, future: range })}`),
  });
  const d = data.data;
  const today = todayIso();

  return (
    <>
      <PageHeader
        title="Analytics"
        description="Occupancy, guest flow, demand and how well rooms and screening are working. Computed from the bookings in the database."
        actions={
          <Select aria-label="Range" className="w-48" value={range} onChange={(e) => setRange(e.target.value)}>
            <option value="14">14 nights each side</option>
            <option value="30">30 nights each side</option>
            <option value="60">60 nights each side</option>
          </Select>
        }
      />
      <ErrorBox error={data.error} className="mb-4" />
      <Tabs defaultValue="operations">
        <TabsList>
          <TabsTrigger value="operations">Operations</TabsTrigger>
          {can("MANAGER", "ADMIN") && <TabsTrigger value="audit">Audit log</TabsTrigger>}
        </TabsList>
        <TabsContent value="operations" className="pt-4">
          {!d ? <LoadingRows /> : (
            <div className="space-y-6">
              <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
                <Stat label="Rooms assigned for guests in house and arriving today" value={`${d.assignment.assigned} of ${d.assignment.requests}`} detail={pct(d.assignment.successRate)} />
                <Stat label="Assigned by max flow" value={num(d.assignment.byMethod.MAX_FLOW ?? 0)} detail={`${d.assignment.byMethod.MANUAL ?? 0} by hand, ${d.assignment.byMethod.REASSIGNED ?? 0} moved after maintenance`} />
                <Stat label="Guests cleared by the Bloom filter alone" value={num(d.screening.clearedByFilter)} detail={`of ${num(d.screening.screened)} values screened since start-up`} />
                <Stat label="Watchlist matches confirmed" value={num(d.screening.confirmed)} detail={`${plural(d.screening.falsePositives, "false positive")}, none held`} />
              </div>

              <Card>
                <CardHeader title="Occupancy by room type" description="Share of each type's rooms booked per night. Future nights show bookings taken so far." />
                <div className="h-80 px-2 py-4">
                  <ResponsiveContainer width="100%" height="100%">
                    <LineChart data={d.occupancy.map((p) => ({ night: p.night, ...p.occupancy }))} margin={{ left: 0, right: 16, top: 8 }}>
                      <CartesianGrid {...gridProps} />
                      <XAxis dataKey="night" {...axisProps} tickFormatter={shortDate} minTickGap={28} />
                      <YAxis {...axisProps} domain={[0, 100]} tickFormatter={(v) => `${v}%`} width={44} />
                      <Tooltip content={<ChartTooltip format={(v) => pct(v)} />} labelFormatter={(l) => shortDate(String(l))} />
                      <Legend wrapperStyle={{ fontSize: 12 }} />
                      <ReferenceLine x={today} stroke="#8a93a0" strokeDasharray="3 3" />
                      {ROOM_TYPES.map((t) => (
                        <Line key={t} type="monotone" dataKey={t} name={label(t)} stroke={TYPE_COLORS[t]} strokeWidth={2} dot={false} isAnimationActive={false} />
                      ))}
                    </LineChart>
                  </ResponsiveContainer>
                </div>
              </Card>

              <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
                <Card>
                  <CardHeader title="Arrivals and departures" description="Bookings starting and ending each day, two weeks each side of today." />
                  <div className="h-72 px-2 py-4">
                    <ResponsiveContainer width="100%" height="100%">
                      <BarChart data={d.flow} margin={{ left: 0, right: 16, top: 8 }}>
                        <CartesianGrid {...gridProps} />
                        <XAxis dataKey="date" {...axisProps} tickFormatter={shortDate} minTickGap={20} />
                        <YAxis {...axisProps} allowDecimals={false} width={36} />
                        <Tooltip content={<ChartTooltip />} labelFormatter={(l) => shortDate(String(l))} cursor={{ fill: "#f1eee8" }} />
                        <Legend wrapperStyle={{ fontSize: 12 }} />
                        <Bar dataKey="arrivals" name="Arrivals" fill="#0f766e" radius={[2, 2, 0, 0]} isAnimationActive={false} />
                        <Bar dataKey="departures" name="Departures" fill="#9fb2cc" radius={[2, 2, 0, 0]} isAnimationActive={false} />
                      </BarChart>
                    </ResponsiveContainer>
                  </div>
                </Card>
                <Card>
                  <CardHeader title="Demand by room type, next 30 nights" description="Room-nights booked against room-nights available." />
                  <TableWrap>
                    <Table>
                      <thead><tr><Th>Room type</Th><Th className="text-right">Bookings</Th><Th className="text-right">Room-nights</Th><Th className="text-right">Capacity</Th><Th>Occupancy</Th></tr></thead>
                      <tbody>
                        {d.demand.map((t) => (
                          <Tr key={t.roomType}>
                            <Td className="font-medium">{label(t.roomType)}</Td>
                            <Td className="text-right tabular">{num(t.bookings)}</Td>
                            <Td className="text-right tabular">{num(t.roomNights)}</Td>
                            <Td className="text-right tabular">{num(t.capacityNights)}</Td>
                            <Td>
                              <div className="flex items-center gap-2">
                                <div className="h-2 w-28 rounded-full bg-sunken">
                                  <div className="h-2 rounded-full" style={{ width: `${Math.min(100, t.occupancy)}%`, background: TYPE_COLORS[t.roomType] }} />
                                </div>
                                <span className="tabular">{pct(t.occupancy)}</span>
                              </div>
                            </Td>
                          </Tr>
                        ))}
                      </tbody>
                    </Table>
                  </TableWrap>
                </Card>
              </div>

              <Card>
                <CardHeader title="Reservations by status" description="All bookings in the system." />
                <CardBody className="flex flex-wrap gap-3">
                  {d.statuses.map((s) => (
                    <div key={s.status} className="flex items-center gap-2 rounded-md border border-line px-3 py-2">
                      <StatusBadge status={s.status} />
                      <span className="font-semibold tabular">{num(s.count)}</span>
                    </div>
                  ))}
                </CardBody>
              </Card>
            </div>
          )}
        </TabsContent>
        {can("MANAGER", "ADMIN") && (
          <TabsContent value="audit" className="pt-4">
            <AuditLog />
          </TabsContent>
        )}
      </Tabs>
    </>
  );
}

function AuditLog() {
  const [page, setPage] = useState(0);
  const log = useQuery({ queryKey: ["audit", page], queryFn: () => api.get<Activity[]>(`/api/audit${qs({ page, size: 50 })}`), placeholderData: (p) => p });
  return (
    <Card>
      <CardHeader title="Audit log" description="Every change, who made it and when. Newest first." />
      <ErrorBox error={log.error} className="m-4" />
      {log.isLoading ? <LoadingRows /> : (
        <>
          <TableWrap>
            <Table>
              <thead><tr><Th>When</Th><Th>Who</Th><Th>What</Th><Th>Record</Th><Th>Details</Th></tr></thead>
              <tbody>
                {(log.data ?? []).map((a, i) => (
                  <Tr key={i}>
                    <Td className="tabular text-muted">{stamp(a.at)}</Td>
                    <Td>{a.actor}</Td>
                    <Td>{label(a.action)}</Td>
                    <Td className="tabular">{a.reference ?? ""}</Td>
                    <Td className="whitespace-normal">{a.detail}</Td>
                  </Tr>
                ))}
              </tbody>
            </Table>
          </TableWrap>
          <div className="flex justify-end gap-2 px-4 py-3">
            <button className="text-sm text-teal-700 disabled:text-subtle" disabled={page === 0} onClick={() => setPage(page - 1)}>Newer</button>
            <button className="text-sm text-teal-700 disabled:text-subtle" disabled={(log.data?.length ?? 0) < 50} onClick={() => setPage(page + 1)}>Older</button>
          </div>
        </>
      )}
    </Card>
  );
}
