import { useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Button } from "@/components/ui/button";
import { Card, CardBody, CardHeader } from "@/components/ui/card";
import { Field, Input } from "@/components/ui/form";
import { ErrorBox, LoadingRows } from "@/components/ui/feedback";
import { Table, TableWrap, Td, Th, Tr } from "@/components/ui/table";
import { ChartTooltip, axisProps, gridProps } from "@/components/charts";
import { api, qs } from "@/lib/api";
import { num } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { TreeBenchmark, TreeDemo, TreeNode, VisitKind } from "@/lib/types";

const kindFill: Record<VisitKind, string> = { FULL: "#0f766e", PARTIAL: "#ffffff", OUTSIDE: "#eceae4" };

/** A segment tree drawn level by level, each node spanning the nights it covers. */
function TreeDiagram({ nodes, nights, visits, title }: {
  nodes: TreeNode[];
  nights: number;
  visits: Map<number, VisitKind>;
  title: string;
}) {
  const width = Math.max(640, nights * 34);
  const levelH = 40;
  const depthOf = (node: number) => Math.floor(Math.log2(node));
  const depth = Math.max(...nodes.map((n) => depthOf(n.node)));
  const unit = (width - 8) / nights;
  return (
    <div className="overflow-x-auto">
      <svg width={width} height={(depth + 1) * levelH + 24} role="img" aria-label={title}>
        {nodes.map((n) => {
          const kind = visits.get(n.node);
          const x = 4 + n.lo * unit + 1;
          const w = (n.hi - n.lo + 1) * unit - 2;
          const y = depthOf(n.node) * levelH + 2;
          const leaf = n.lo === n.hi;
          return (
            <g key={n.node}>
              <rect x={x} y={y} width={w} height={28} rx={4} fill={kind ? kindFill[kind] : "#ffffff"}
                stroke={kind === "FULL" ? "#0f766e" : kind === "PARTIAL" ? "#0f766e" : "#d8d3c8"} strokeWidth={kind === "PARTIAL" ? 2 : 1}>
                <title>{`Node ${n.node}: nights ${n.lo} to ${n.hi}, min ${n.min}${n.lazy ? `, owes children ${n.lazy}` : ""}${kind ? `. ${kind}` : ""}`}</title>
              </rect>
              {w >= 18 && (
                <text x={x + w / 2} y={y + 18} textAnchor="middle" fontSize={12} fontWeight={600} fill={kind === "FULL" ? "#ffffff" : "#16202c"} pointerEvents="none">
                  {n.min}
                </text>
              )}
              {n.lazy !== 0 && (
                <g pointerEvents="none">
                  <rect x={x + w - 26} y={y - 7} width={24} height={14} rx={3} fill="#b45309" />
                  <text x={x + w - 14} y={y + 3} textAnchor="middle" fontSize={10} fill="#fff" fontWeight={600}>{n.lazy}</text>
                </g>
              )}
              {leaf && (
                <text x={x + w / 2} y={(depth + 1) * levelH + 14} textAnchor="middle" fontSize={10} fill="#8a93a0">{n.lo}</text>
              )}
            </g>
          );
        })}
      </svg>
    </div>
  );
}

export function TreeLab() {
  const [form, setForm] = useState({ nights: 16, rooms: 12, from: 3, to: 9, wanted: 2, seed: 42 });
  const [view, setView] = useState<"query" | "update">("query");
  const set = (k: keyof typeof form, v: string) => setForm({ ...form, [k]: Number(v) });
  const demo = useQuery({
    queryKey: ["lab-tree", form],
    queryFn: () => api.get<TreeDemo>(`/api/lab/segment-tree/demo${qs(form)}`),
    placeholderData: (p) => p,
  });
  const bench = useMutation({ mutationFn: () => api.get<TreeBenchmark>("/api/lab/segment-tree/benchmark?operations=5000") });
  const d = demo.data;
  const showUpdate = view === "update" && d?.update;
  const trace = showUpdate ? d!.update! : d?.query;
  const visits = new Map((trace?.visits ?? []).map((v) => [v.node, v.kind] as const));

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader
          title="Segment tree with lazy propagation"
          description="Each leaf is one night's free rooms; each parent holds the minimum of its children. A stay fits if the minimum over its nights covers the rooms wanted. Booking subtracts from a whole range with lazy tags instead of touching every night."
        />
        <CardBody className="space-y-5">
          <div className="grid gap-3 sm:grid-cols-3 lg:grid-cols-6">
            {([["nights", "Nights (2 to 64)"], ["rooms", "Rooms of the type"], ["from", "Stay: first night"], ["to", "Stay: last night"], ["wanted", "Rooms wanted"], ["seed", "Random seed"]] as const).map(([k, l]) => (
              <Field key={k} label={l}>{(id) => <Input id={id} type="number" value={form[k]} onChange={(e) => set(k, e.target.value)} />}</Field>
            ))}
          </div>
          <ErrorBox error={demo.error} />
          {!d ? <LoadingRows rows={3} /> : (
            <>
              <div className="flex flex-wrap items-center gap-2">
                <Button size="sm" variant={view === "query" ? "primary" : "secondary"} onClick={() => setView("query")}>1. Range-minimum query</Button>
                <Button size="sm" variant={view === "update" ? "primary" : "secondary"} disabled={!d.update} onClick={() => setView("update")}>2. Booking: range update</Button>
                <span className="text-[13px] text-muted">{showUpdate ? "Tree after the booking. Amber tags are amounts a node still owes its children." : "Tree before the booking, coloured by what the query did at each node."}</span>
              </div>
              <TreeDiagram nodes={showUpdate ? d.nodesAfter : d.nodesBefore} nights={d.nights} visits={visits} title="Segment tree" />
              <div className="flex flex-wrap gap-x-5 gap-y-1 text-xs text-muted">
                <span className="flex items-center gap-1.5"><span className="size-3 rounded-[3px] bg-teal-600" /> FULL: inside the stay, answered without going deeper</span>
                <span className="flex items-center gap-1.5"><span className="size-3 rounded-[3px] border-2 border-teal-600 bg-white" /> PARTIAL: split into its two children</span>
                <span className="flex items-center gap-1.5"><span className="size-3 rounded-[3px] border border-line-strong bg-sunken" /> OUTSIDE: skipped</span>
                <span className="flex items-center gap-1.5"><span className="h-3 w-5 rounded-[3px] bg-amber-600" /> Lazy tag</span>
              </div>
              <div>
                <p className="mb-2 text-[13px] font-medium text-muted">Free rooms per night {showUpdate ? "after" : "before"} the booking (the stay is outlined)</p>
                <div className="flex items-end gap-1 overflow-x-auto">
                  {(showUpdate ? d.freeAfter : d.freeBefore).map((v, i) => (
                    <div key={i} className="flex min-w-7 flex-1 flex-col items-center gap-0.5">
                      <span className="text-[11px] tabular text-muted">{v}</span>
                      <div className={cn("w-full rounded-t-[3px]", i >= form.from && i <= form.to ? "bg-teal-600" : "bg-teal-100", i >= form.from && i <= form.to && "ring-1 ring-navy-700")}
                        style={{ height: `${4 + (v / Math.max(1, d.roomsOfType)) * 60}px` }} />
                      <span className="text-[10px] text-muted">{i}</span>
                    </div>
                  ))}
                </div>
              </div>
              <div className="rounded-md bg-navy-900 p-4 font-mono text-[12.5px] leading-relaxed text-teal-50">
                {d.log.map((l, i) => <p key={i}>{l}</p>)}
              </div>
            </>
          )}
        </CardBody>
      </Card>

      <Card>
        <CardHeader title="Compare with the baseline" description="The same random queries and bookings on calendars of three sizes: segment tree against reading every night of every stay. Measured now, on this machine."
          actions={<Button variant="primary" loading={bench.isPending} onClick={() => bench.mutate()}>Run benchmark</Button>} />
        <ErrorBox error={bench.error} className="m-4" />
        {bench.data && (
          <CardBody className="space-y-4">
            <TableWrap className="rounded-md border border-line">
              <Table>
                <thead><tr><Th>Nights</Th><Th className="text-right">Operations</Th><Th className="text-right">Segment tree</Th><Th className="text-right">Scanning every night</Th><Th className="text-right">Speed-up</Th></tr></thead>
                <tbody>
                  {bench.data.rows.map((r) => (
                    <Tr key={r.nights}>
                      <Td className="tabular">{num(r.nights)}</Td>
                      <Td className="text-right tabular">{num(r.operations)}</Td>
                      <Td className="text-right tabular">{r.treeMs} ms <span className="text-muted">({r.treeUsPerOp} µs/op)</span></Td>
                      <Td className="text-right tabular">{r.scanMs} ms <span className="text-muted">({r.scanUsPerOp} µs/op)</span></Td>
                      <Td className="text-right font-semibold tabular">{r.speedup}x</Td>
                    </Tr>
                  ))}
                </tbody>
              </Table>
            </TableWrap>
            <div className="h-64">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={bench.data.rows.map((r) => ({ nights: num(r.nights), "Segment tree": r.treeMs, "Scan": r.scanMs }))}>
                  <CartesianGrid {...gridProps} />
                  <XAxis dataKey="nights" {...axisProps} />
                  <YAxis {...axisProps} scale="log" domain={["auto", "auto"]} tickFormatter={(v) => `${v} ms`} width={64} allowDataOverflow />
                  <Tooltip content={<ChartTooltip format={(v) => `${v} ms`} />} cursor={{ fill: "#f1eee8" }} />
                  <Legend wrapperStyle={{ fontSize: 12 }} />
                  <Bar dataKey="Segment tree" fill="#0f766e" isAnimationActive={false} />
                  <Bar dataKey="Scan" fill="#9fb2cc" isAnimationActive={false} />
                </BarChart>
              </ResponsiveContainer>
            </div>
            <p className="text-[13px] text-muted">
              {bench.data.method} Java {bench.data.environment.java}, {bench.data.environment.processors} cores. Log scale.
              The tree's time barely grows with the calendar (O(log n) per operation) while the scan grows with it (O(n)); on small calendars the scan can win because it is simpler.
            </p>
          </CardBody>
        )}
      </Card>
    </div>
  );
}
