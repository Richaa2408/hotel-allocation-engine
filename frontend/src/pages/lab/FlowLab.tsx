import { useMemo, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Background, ReactFlow, type Edge, type Node } from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { ChevronLeft, ChevronRight, SkipBack, SkipForward } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardBody, CardHeader } from "@/components/ui/card";
import { Checkbox, Field, Input, Select } from "@/components/ui/form";
import { Badge } from "@/components/ui/badge";
import { ErrorBox, LoadingRows, Notice } from "@/components/ui/feedback";
import { Table, TableWrap, Td, Th, Tr } from "@/components/ui/table";
import { api, qs } from "@/lib/api";
import { label, num } from "@/lib/format";
import type { FlowBenchmark, FlowDemo } from "@/lib/types";

const COL = { SOURCE: 0, GUEST: 230, ROOM: 560, SINK: 790 };

export function FlowLab() {
  const [scenario, setScenario] = useState("greedy-trap");
  const [size, setSize] = useState({ guests: 10, rooms: 10, seed: 42 });
  const demo = useQuery({
    queryKey: ["lab-flow", scenario, size],
    queryFn: () => api.get<FlowDemo>(`/api/lab/flow/demo${qs({ scenario, ...size })}`),
    placeholderData: (p) => p,
  });
  const bench = useMutation({ mutationFn: () => api.get<FlowBenchmark>("/api/lab/flow/benchmark") });
  const d = demo.data;

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader
          title="Room assignment as maximum flow (Edmonds-Karp)"
          description="Source to each guest (capacity 1), guest to each room that meets their must-haves (1), room to sink (1). The maximum flow is the most guests placed at once. Each step is a shortest augmenting path found by breadth-first search."
        />
        <CardBody className="space-y-4">
          <div className="grid gap-3 sm:grid-cols-4">
            <Field label="Example" className="sm:col-span-2">
              {(id) => (
                <Select id={id} value={scenario} onChange={(e) => setScenario(e.target.value)}>
                  <option value="greedy-trap">First-fit trap (3 guests)</option>
                  <option value="accessibility-shortage">Accessibility shortage (6 guests)</option>
                  <option value="random">Random hotel</option>
                </Select>
              )}
            </Field>
            {scenario === "random" && (["guests", "rooms", "seed"] as const).map((k) => (
              <Field key={k} label={label(k)}>{(id) => <Input id={id} type="number" min={1} max={k === "seed" ? undefined : 40} value={size[k]} onChange={(e) => setSize({ ...size, [k]: Number(e.target.value) })} />}</Field>
            ))}
          </div>
          <ErrorBox error={demo.error} />
          {!d ? <LoadingRows /> : <FlowReplay key={demo.dataUpdatedAt} demo={d} />}
        </CardBody>
      </Card>

      <Card>
        <CardHeader title="Compare with the baseline" description="Edmonds-Karp against first-fit in booking order on the same generated hotels, rooms = 90% of guests. Measured now."
          actions={<Button variant="primary" loading={bench.isPending} onClick={() => bench.mutate()}>Run benchmark</Button>} />
        <ErrorBox error={bench.error} className="m-4" />
        {bench.data && (
          <CardBody className="space-y-3">
            <TableWrap className="rounded-md border border-line">
              <Table>
                <thead><tr><Th>Guests</Th><Th className="text-right">Rooms</Th><Th className="text-right">Max flow placed</Th><Th className="text-right">First-fit placed</Th><Th className="text-right">Extra guests</Th><Th className="text-right">Max flow time</Th><Th className="text-right">First-fit time</Th></tr></thead>
                <tbody>
                  {bench.data.rows.map((r) => (
                    <Tr key={r.guests}>
                      <Td className="tabular">{num(r.guests)}</Td>
                      <Td className="text-right tabular">{num(r.rooms)}</Td>
                      <Td className="text-right font-semibold tabular">{num(r.maxFlowPlaced)}</Td>
                      <Td className="text-right tabular">{num(r.firstFitPlaced)}</Td>
                      <Td className="text-right tabular text-ok-600">+{num(r.maxFlowPlaced - r.firstFitPlaced)} ({((100 * (r.maxFlowPlaced - r.firstFitPlaced)) / Math.max(1, r.firstFitPlaced)).toFixed(1)}%)</Td>
                      <Td className="text-right tabular">{r.maxFlowMs} ms</Td>
                      <Td className="text-right tabular">{r.firstFitMs} ms</Td>
                    </Tr>
                  ))}
                </tbody>
              </Table>
            </TableWrap>
            <p className="text-[13px] text-muted">{bench.data.method} Java {bench.data.environment.java}. Max flow is O(V·E²) in general and costs more time; it places more guests because it can move earlier guests through reverse edges.</p>
          </CardBody>
        )}
      </Card>
    </div>
  );
}

function FlowReplay({ demo }: { demo: FlowDemo }) {
  const trace = demo.result.trace!;
  const total = trace.steps.length;
  const [step, setStep] = useState(0); // 0 = no flow, 1..total = after path k, total + 1 = final
  const [showCut, setShowCut] = useState(false);
  const isFinal = step === total + 1;
  const current = step >= 1 && step <= total ? trace.steps[step - 1] : null;

  const { nodes, edges } = useMemo(() => {
    // Replay the recorded pushes to draw the flow after each step; odd edge ids are reverse edges.
    const flow = new Map<number, number>();
    for (let i = 0; i < Math.min(step, total); i++) {
      const s = trace.steps[i];
      for (const e of s.edges) {
        const fwd = e % 2 === 0 ? e : e ^ 1;
        flow.set(fwd, (flow.get(fwd) ?? 0) + (e % 2 === 0 ? s.bottleneck : -s.bottleneck));
      }
    }
    const onPath = new Set(current?.edges.filter((e) => e % 2 === 0));
    const reversed = new Set(current?.edges.filter((e) => e % 2 === 1).map((e) => e ^ 1));
    const cut = new Set(isFinal && showCut ? trace.minCutEdges : []);
    const sSide = new Set(isFinal && showCut ? trace.sourceSide : []);
    const inPath = new Set(current?.path ?? []);

    const guests = trace.nodes.filter((n) => n.kind === "GUEST");
    const rooms = trace.nodes.filter((n) => n.kind === "ROOM");
    const rowH = 54;
    const height = Math.max(guests.length, rooms.length) * rowH;
    const y = new Map<number, number>();
    guests.forEach((n, i) => y.set(n.id, (height - guests.length * rowH) / 2 + i * rowH));
    rooms.forEach((n, i) => y.set(n.id, (height - rooms.length * rowH) / 2 + i * rowH));

    const nodes: Node[] = trace.nodes.map((n) => ({
      id: String(n.id),
      position: { x: COL[n.kind], y: n.kind === "SOURCE" || n.kind === "SINK" ? height / 2 - rowH / 2 : y.get(n.id)! },
      data: { label: n.label },
      draggable: false,
      sourcePosition: "right" as never,
      targetPosition: "left" as never,
      style: {
        width: n.kind === "SOURCE" || n.kind === "SINK" ? 90 : 170,
        fontSize: 12,
        padding: "6px 8px",
        borderRadius: 6,
        border: `${inPath.has(n.id) || sSide.has(n.id) ? 2 : 1}px solid ${inPath.has(n.id) || sSide.has(n.id) ? "#0f766e" : "#cfcac0"}`,
        background: n.kind === "SOURCE" || n.kind === "SINK" ? "#10213a" : sSide.has(n.id) ? "#d5ece8" : n.kind === "GUEST" ? "#f4f6fa" : "#f7f5f0",
        color: n.kind === "SOURCE" || n.kind === "SINK" ? "#fff" : "#16202c",
      },
    }));
    const edges: Edge[] = trace.edges.map((e) => {
      const f = flow.get(e.id) ?? 0;
      let stroke = "#d8d3c8", width = 1, dash: string | undefined;
      if (f > 0) { stroke = "#16202c"; width = 2; }
      if (onPath.has(e.id)) { stroke = "#0f766e"; width = 3.5; }
      if (reversed.has(e.id)) { stroke = "#b45309"; width = 3.5; dash = "7 5"; }
      if (cut.has(e.id)) { stroke = "#e0533d"; width = 3.5; }
      return {
        id: String(e.id),
        source: String(e.from),
        target: String(e.to),
        type: "straight",
        style: { stroke, strokeWidth: width, strokeDasharray: dash },
        zIndex: onPath.has(e.id) || reversed.has(e.id) || cut.has(e.id) ? 10 : f > 0 ? 5 : 0,
      };
    });
    return { nodes, edges };
  }, [trace, step, total, current, isFinal, showCut]);

  const labelOf = (id: number) => trace.nodes[id].label;
  const caption = step === 0
    ? `No flow yet. Edmonds-Karp found ${total} augmenting path${total === 1 ? "" : "s"}. Step through them.`
    : isFinal
      ? showCut
        ? `Minimum cut: ${trace.minCutEdges.length} saturated edges with total capacity ${demo.result.assignedCount}, equal to the flow. Nodes outlined in teal are still reachable from the source: these guests compete for fewer suitable rooms than there are guests. No arrangement places more.`
        : `Final flow ${demo.result.assignedCount}: no path from source to sink has spare capacity left.`
      : `Step ${step} of ${total}: push ${current!.bottleneck} along ${current!.path.map(labelOf).join(" > ")}. Flow is now ${current!.totalFlow}.`
        + (current!.edges.some((e) => e % 2 === 1) ? " This path uses a reverse edge: a guest placed earlier is moved to another room to free one up." : "");

  const ffRoom = new Map(demo.firstFit.map((p) => [p.guestId, p.roomNumber]));
  const mfRoom = new Map(demo.result.assignments.map((a) => [a.guestId, a.roomNumber]));

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <Button size="icon" aria-label="First" onClick={() => setStep(0)} disabled={step === 0}><SkipBack /></Button>
        <Button size="icon" aria-label="Previous step" onClick={() => setStep(step - 1)} disabled={step === 0}><ChevronLeft /></Button>
        <Button size="icon" aria-label="Next step" onClick={() => setStep(step + 1)} disabled={isFinal}><ChevronRight /></Button>
        <Button size="icon" aria-label="Final flow" onClick={() => setStep(total + 1)} disabled={isFinal}><SkipForward /></Button>
        <span className="text-sm text-muted tabular">{isFinal ? "Final" : `Step ${step} of ${total}`}</span>
        <div className="ml-2"><Checkbox label="Show minimum cut" checked={showCut} onChange={(v) => { setShowCut(v); if (v) setStep(total + 1); }} /></div>
      </div>
      <p className="min-h-10 text-sm" aria-live="polite">{caption}</p>
      <div className="flex flex-wrap gap-x-5 gap-y-1 text-xs text-muted">
        <span className="flex items-center gap-1.5"><span className="h-0.5 w-5 bg-ink" /> Carries flow</span>
        <span className="flex items-center gap-1.5"><span className="h-1 w-5 bg-teal-600" /> Current augmenting path</span>
        <span className="flex items-center gap-1.5"><span className="w-5 border-t-[3px] border-dashed border-amber-600" /> Reverse edge: flow taken back</span>
        <span className="flex items-center gap-1.5"><span className="h-1 w-5 bg-coral-500" /> Minimum cut</span>
      </div>
      <div className="rounded-md border border-line bg-canvas" style={{ height: Math.min(520, 110 + 44 * Math.max(demo.guests.length, trace.nodes.length - demo.guests.length - 2)) }}>
        <ReactFlow nodes={nodes} edges={edges} fitView fitViewOptions={{ padding: 0.08 }} nodesDraggable={false} nodesConnectable={false} elementsSelectable={false} proOptions={{ hideAttribution: true }} minZoom={0.2}>
          <Background color="#e3e0d8" gap={24} />
        </ReactFlow>
      </div>
      <div className="grid gap-4 lg:grid-cols-2">
        <TableWrap className="rounded-md border border-line">
          <Table>
            <thead><tr><Th>Guest</Th><Th>Must have</Th><Th>First-fit</Th><Th>Max flow</Th></tr></thead>
            <tbody>
              {demo.guests.map((g) => (
                <Tr key={g.id}>
                  <Td>{g.name}</Td>
                  <Td className="text-muted">{label(g.roomType).toLowerCase()}{g.acceptsUpgrade ? " or better" : ""}{g.needsAccessible ? ", accessible" : ""}{g.maxFloor !== null ? `, floor ${g.maxFloor} or below` : ""}</Td>
                  <Td>{ffRoom.get(g.id) ?? <Badge tone="coral">none</Badge>}</Td>
                  <Td className="font-semibold">{mfRoom.get(g.id) ?? <Badge tone="coral">none</Badge>}</Td>
                </Tr>
              ))}
            </tbody>
          </Table>
        </TableWrap>
        <div className="space-y-3">
          <Notice tone={demo.result.assignedCount > demo.result.firstFitAssignedCount ? "ok" : "info"} title={`Max flow placed ${demo.result.assignedCount}, first-fit ${demo.result.firstFitAssignedCount}`}>
            First-fit gives each guest, in booking order, the first free room that suits them and never changes its mind.
          </Notice>
          {demo.result.shortages.map((s, i) => <Notice key={i} tone="warn" title="Shortage found from the minimum cut">{s.summary}</Notice>)}
          <div className="rounded-md bg-navy-900 p-3 font-mono text-[12px] leading-relaxed text-teal-50">
            {demo.log.map((l, i) => <p key={i}>{l}</p>)}
          </div>
        </div>
      </div>
    </div>
  );
}
