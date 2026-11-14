import { useEffect, useRef, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { CartesianGrid, Legend, Line, LineChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Button } from "@/components/ui/button";
import { Card, CardBody, CardHeader } from "@/components/ui/card";
import { Field, Input, Textarea } from "@/components/ui/form";
import { Badge } from "@/components/ui/badge";
import { ErrorBox, LoadingRows } from "@/components/ui/feedback";
import { Table, TableWrap, Td, Th, Tr } from "@/components/ui/table";
import { ChartTooltip, axisProps, gridProps } from "@/components/charts";
import { api, qs } from "@/lib/api";
import { bytes, num } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { BloomBenchmark, BloomDemo, BloomSweep, ItemTrace } from "@/lib/types";

const pctFmt = (v: number) => `${(v * 100).toFixed(2)}%`;

/** Every bit of a small filter as a grid; the selected value's hash positions are outlined. */
function BitGrid({ bits, set, highlight }: { bits: number; set: number[]; highlight?: ItemTrace }) {
  const ref = useRef<HTMLCanvasElement>(null);
  const cols = Math.min(128, Math.ceil(Math.sqrt(bits * 3)));
  const cell = bits <= 512 ? 12 : bits <= 2048 ? 8 : 6;
  const rows = Math.ceil(bits / cols);
  useEffect(() => {
    const c = ref.current;
    if (!c) return;
    const ctx = c.getContext("2d")!;
    const on = new Set(set);
    ctx.clearRect(0, 0, c.width, c.height);
    for (let i = 0; i < bits; i++) {
      const x = (i % cols) * cell, y = Math.floor(i / cols) * cell;
      ctx.fillStyle = on.has(i) ? "#0f766e" : "#ece9e2";
      ctx.fillRect(x + 1, y + 1, cell - 2, cell - 2);
    }
    highlight?.positions.forEach((p, i) => {
      const x = (p % cols) * cell, y = Math.floor(p / cols) * cell;
      ctx.strokeStyle = highlight.bitWasSet[i] ? "#b45309" : "#e0533d";
      ctx.lineWidth = 2.5;
      ctx.strokeRect(x - 1, y - 1, cell + 2, cell + 2);
    });
  }, [bits, set, highlight, cols, cell]);
  return <canvas ref={ref} width={cols * cell} height={rows * cell} className="max-w-full" role="img" aria-label={`Bit array of ${bits} bits, ${set.length} set`} />;
}

export function BloomLab() {
  const [form, setForm] = useState({ bitCount: 256, hashCount: 3, generated: 20, seed: 42 });
  const [insert, setInsert] = useState("fraud.ring@example.com\ncard.tester@example.org");
  const [probe, setProbe] = useState("fraud.ring@example.com\nasha@example.org\nnew.guest@example.net");
  const [selected, setSelected] = useState<ItemTrace | undefined>();
  const lines = (s: string) => s.split("\n").map((x) => x.trim()).filter(Boolean);
  const demo = useMutation({
    mutationFn: () => api.post<BloomDemo>("/api/lab/bloom/demo", { ...form, insert: lines(insert), probe: lines(probe) }),
    onSuccess: (d) => setSelected(d.probes[0]),
  });
  const [items, setItems] = useState(2000);
  const sweep = useQuery({ queryKey: ["lab-sweep", items], queryFn: () => api.get<BloomSweep>(`/api/lab/bloom/sweep${qs({ items })}`) });
  const bench = useMutation({ mutationFn: () => api.get<BloomBenchmark>("/api/lab/bloom/benchmark") });
  useEffect(() => { demo.mutate(); /* first run on open */ // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  const d = demo.data;
  // Inserted values were traced while being added (bitWasSet = "already 1 before this add").
  // After adding, all their bits are 1, so a check now always says "possibly": no false negatives.
  const rows: { trace: ItemTrace; added: boolean }[] = d
    ? [
        ...d.probes.map((t) => ({ trace: t, added: false })),
        ...d.insertedItems.filter((t) => !d.probes.some((p) => p.value === t.value)).slice(0, 4).map((t) => ({ trace: { ...t, possiblyPresent: true, actuallyPresent: true, bitWasSet: t.positions.map(() => true) }, added: true })),
      ]
    : [];
  const sharedBefore = (t: ItemTrace) => d?.insertedItems.find((x) => x.value === t.value)?.bitWasSet.filter(Boolean).length;

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader title="Bloom filter" description="Adding a value sets k bits chosen by hashing it. Checking it looks at the same k bits: any 0 means definitely not added; all 1 means possibly added, because other values may have set those bits." />
        <CardBody className="space-y-5">
          <div className="grid gap-3 sm:grid-cols-4">
            {([["bitCount", "Bits (m), 16 to 8,192"], ["hashCount", "Hash functions (k)"], ["generated", "Generated IDs to add"], ["seed", "Random seed"]] as const).map(([k, l]) => (
              <Field key={k} label={l}>{(id) => <Input id={id} type="number" value={form[k]} onChange={(e) => setForm({ ...form, [k]: Number(e.target.value) })} />}</Field>
            ))}
            <Field label="Also add these (one per line)" className="sm:col-span-2">{(id) => <Textarea id={id} value={insert} onChange={(e) => setInsert(e.target.value)} />}</Field>
            <Field label="Then check these (one per line)" className="sm:col-span-2">{(id) => <Textarea id={id} value={probe} onChange={(e) => setProbe(e.target.value)} />}</Field>
          </div>
          <Button variant="primary" loading={demo.isPending} onClick={() => demo.mutate()}>Build filter and check</Button>
          <ErrorBox error={demo.error} />
          {d && (
            <div className="grid gap-6 lg:grid-cols-[auto_1fr]">
              <div>
                <BitGrid bits={d.bitCount} set={d.setBits} highlight={selected} />
                <p className="mt-2 text-[12.5px] text-muted">{num(d.setBits.length)} of {num(d.bitCount)} bits set after {num(d.inserted)} values. Outlined: the bits of the selected value.</p>
              </div>
              <div className="min-w-0 space-y-4">
                <dl className="grid grid-cols-2 gap-3 text-sm">
                  <div className="rounded-md border border-line p-3"><dt className="text-muted">Expected false-positive rate</dt><dd className="text-lg font-semibold tabular">{pctFmt(d.expectedFpr)}</dd></div>
                  <div className="rounded-md border border-line p-3"><dt className="text-muted">Measured on {num(d.probesTested)} outsiders</dt><dd className="text-lg font-semibold tabular">{pctFmt(d.measuredFpr)} <span className="text-sm font-normal text-muted">({d.falsePositives})</span></dd></div>
                </dl>
                <TableWrap className="rounded-md border border-line">
                  <Table>
                    <thead><tr><Th>Value</Th><Th>Filter says</Th><Th>Really added?</Th></tr></thead>
                    <tbody>
                      {rows.map(({ trace: t, added }, i) => (
                        <Tr key={i} className={cn("cursor-pointer", selected?.value === t.value && "bg-teal-50")} onClick={() => setSelected(t)}>
                          <Td className="font-mono text-[12.5px]">{t.value}{added && <span className="ml-1.5 font-sans text-muted">(added)</span>}</Td>
                          <Td>{t.possiblyPresent ? <Badge tone="amber">possibly</Badge> : <Badge tone="green">definitely not</Badge>}</Td>
                          <Td>{t.actuallyPresent ? "yes" : t.possiblyPresent ? <Badge tone="coral">no: false positive</Badge> : "no"}</Td>
                        </Tr>
                      ))}
                    </tbody>
                  </Table>
                </TableWrap>
                {selected && (
                  <p className="text-[13px] text-muted">
                    <span className="font-mono text-ink">{selected.value}</span> hashes to bits {selected.positions.map((p, i) => `${p} (${selected.bitWasSet[i] ? 1 : 0})`).join(", ")}.
                    {sharedBefore(selected) ? ` When it was added, ${sharedBefore(selected)} of these were already 1 from other values.` : ""}
                    {!selected.possiblyPresent && " At least one bit is 0, so it was definitely never added."}
                    {selected.possiblyPresent && !selected.actuallyPresent && " Every bit is 1 although it was never added: other values set them. This is a false positive."}
                  </p>
                )}
              </div>
            </div>
          )}
        </CardBody>
      </Card>

      <Card>
        <CardHeader title="Size and hash count against error rate" description="Measured false positives on 20,000 outsiders against the formula (1 - e^(-kn/m))^k."
          actions={<Field label="Items (n)" className="w-36">{(id) => <Input id={id} type="number" value={items} onChange={(e) => setItems(Number(e.target.value))} />}</Field>} />
        <ErrorBox error={sweep.error} className="m-4" />
        {sweep.isLoading ? <LoadingRows rows={3} /> : sweep.data && (
          <CardBody className="grid grid-cols-1 gap-6 lg:grid-cols-2">
            <SweepChart title="More bits per item, k = 4" data={sweep.data.bySize.map((p) => ({ x: p.bitCount / p.items, Expected: p.expectedFpr, Measured: p.measuredFpr }))} xLabel="bits per item" />
            <SweepChart title={`More hash functions, 10 bits per item (best k is ${sweep.data.bestK})`} data={sweep.data.byHashes.map((p) => ({ x: p.hashCount, Expected: p.expectedFpr, Measured: p.measuredFpr }))} xLabel="k" best={sweep.data.bestK} />
          </CardBody>
        )}
      </Card>

      <Card>
        <CardHeader title="Compare with the baseline" description="Bloom filter at a 1% target against a HashSet of the same strings: memory, measured error and lookup time. Measured now."
          actions={<Button variant="primary" loading={bench.isPending} onClick={() => bench.mutate()}>Run benchmark</Button>} />
        <ErrorBox error={bench.error} className="m-4" />
        {bench.data && (
          <CardBody className="space-y-3">
            <TableWrap className="rounded-md border border-line">
              <Table>
                <thead><tr><Th>Entries</Th><Th className="text-right">Bloom filter</Th><Th className="text-right">HashSet</Th><Th className="text-right">Smaller by</Th><Th className="text-right">False positives</Th><Th className="text-right">Lookup: filter / set</Th></tr></thead>
                <tbody>
                  {bench.data.rows.map((r) => (
                    <Tr key={r.entries}>
                      <Td className="tabular">{num(r.entries)}</Td>
                      <Td className="text-right tabular">{bytes(r.bloomBytes)}</Td>
                      <Td className="text-right tabular">{bytes(r.hashSetBytes)}</Td>
                      <Td className="text-right font-semibold tabular">{r.memoryRatio}x</Td>
                      <Td className="text-right tabular">{pctFmt(r.measuredFpr)} <span className="text-muted">(expected {pctFmt(r.expectedFpr)})</span></Td>
                      <Td className="text-right tabular">{r.bloomLookupNs} / {r.hashSetLookupNs} ns</Td>
                    </Tr>
                  ))}
                </tbody>
              </Table>
            </TableWrap>
            <p className="text-[13px] text-muted">
              {bench.data.method} An in-memory HashSet answers faster per lookup; the filter's value is memory, and skipping the slower exact lookup (a database query in the live hotel) for almost every guest.
            </p>
          </CardBody>
        )}
      </Card>
    </div>
  );
}

function SweepChart({ title, data, xLabel, best }: { title: string; data: { x: number; Expected: number; Measured: number }[]; xLabel: string; best?: number }) {
  return (
    <div>
      <p className="mb-2 text-sm font-medium">{title}</p>
      <div className="h-60">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={data} margin={{ left: 0, right: 12, top: 4 }}>
            <CartesianGrid {...gridProps} />
            <XAxis dataKey="x" {...axisProps} label={{ value: xLabel, position: "insideBottomRight", offset: -2, fontSize: 11, fill: "#8a93a0" }} />
            <YAxis {...axisProps} scale="log" domain={["auto", "auto"]} tickFormatter={(v) => pctFmt(v)} width={60} allowDataOverflow />
            <Tooltip content={<ChartTooltip format={pctFmt} />} />
            <Legend wrapperStyle={{ fontSize: 12 }} />
            {best && <ReferenceLine x={best} stroke="#8a93a0" strokeDasharray="3 3" />}
            <Line type="monotone" dataKey="Expected" stroke="#9fb2cc" strokeWidth={2} dot={false} isAnimationActive={false} />
            <Line type="monotone" dataKey="Measured" stroke="#0f766e" strokeWidth={2} dot={{ r: 3 }} isAnimationActive={false} />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
