import { useMutation } from "@tanstack/react-query";
import { useSearchParams } from "react-router";
import { PageHeader } from "@/components/layout/AppShell";
import { Button } from "@/components/ui/button";
import { Card, CardBody } from "@/components/ui/card";
import { ErrorBox } from "@/components/ui/feedback";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/overlay";
import { api } from "@/lib/api";
import type { Scenario } from "@/lib/types";
import { TreeLab } from "./TreeLab";
import { FlowLab } from "./FlowLab";
import { BloomLab } from "./BloomLab";

const SCENARIOS = [
  { id: "peak-season", title: "Peak season", question: "What happens when booking requests far exceed rooms?", algo: "Segment tree" },
  { id: "accessibility-shortage", title: "Accessibility shortage", question: "What if more guests need accessible rooms on low floors than exist?", algo: "Max flow" },
  { id: "unexpected-maintenance", title: "Unexpected maintenance", question: "What if five occupied rooms fail inspection just before check-in?", algo: "Max flow" },
  { id: "suspicious-activity", title: "Suspicious booking activity", question: "How much work does the filter save when screening a busy day?", algo: "Bloom filter" },
];

export default function Lab() {
  const [params, setParams] = useSearchParams();
  const tab = params.get("tab") ?? "tree";
  return (
    <>
      <PageHeader
        title="Algorithm Lab"
        description="The three algorithms behind StayFlow, run on generated data so you can watch them work and compare them with the simple approach. Nothing here changes the live hotel. Every number comes from running the Java code now."
      />
      <Tabs value={tab} onValueChange={(v) => setParams({ tab: v }, { replace: true })}>
        <TabsList>
          <TabsTrigger value="tree">Inventory: segment tree</TabsTrigger>
          <TabsTrigger value="flow">Allocation: max flow</TabsTrigger>
          <TabsTrigger value="bloom">Risk: Bloom filter</TabsTrigger>
          <TabsTrigger value="scenarios">What happens if</TabsTrigger>
        </TabsList>
        <TabsContent value="tree" className="pt-5"><TreeLab /></TabsContent>
        <TabsContent value="flow" className="pt-5"><FlowLab /></TabsContent>
        <TabsContent value="bloom" className="pt-5"><BloomLab /></TabsContent>
        <TabsContent value="scenarios" className="pt-5">
          <div className="grid gap-4 lg:grid-cols-2">
            {SCENARIOS.map((s) => <ScenarioCard key={s.id} {...s} />)}
          </div>
        </TabsContent>
      </Tabs>
    </>
  );
}

function ScenarioCard({ id, title, question, algo }: { id: string; title: string; question: string; algo: string }) {
  const run = useMutation({ mutationFn: () => api.get<Scenario>(`/api/lab/scenarios/${id}`) });
  const r = run.data;
  return (
    <Card>
      <CardBody className="space-y-3">
        <div className="flex items-start justify-between gap-3">
          <div>
            <p className="text-xs font-semibold uppercase tracking-wide text-teal-700">{algo}</p>
            <h3 className="mt-0.5 font-semibold">{title}</h3>
            <p className="text-sm text-muted">{question}</p>
          </div>
          <Button variant={r ? "secondary" : "primary"} size="sm" loading={run.isPending} onClick={() => run.mutate()}>{r ? "Run again" : "Run"}</Button>
        </div>
        <ErrorBox error={run.error} />
        {r && (
          <>
            <dl className="grid grid-cols-2 gap-2 sm:grid-cols-3">
              {r.metrics.map((m) => (
                <div key={m.label} className="rounded-md border border-line px-3 py-2">
                  <dt className="text-[12px] text-muted">{m.label}</dt>
                  <dd className="font-semibold tabular">{m.value}</dd>
                </div>
              ))}
            </dl>
            <ul className="list-disc space-y-1 pl-5 text-sm">{r.findings.map((f, i) => <li key={i}>{f}</li>)}</ul>
          </>
        )}
      </CardBody>
    </Card>
  );
}
