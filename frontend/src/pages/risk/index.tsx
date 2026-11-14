import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, Search, Trash2 } from "lucide-react";
import { PageHeader } from "@/components/layout/AppShell";
import { Button } from "@/components/ui/button";
import { Card, CardBody, CardHeader } from "@/components/ui/card";
import { Field, Input, Select } from "@/components/ui/form";
import { Badge } from "@/components/ui/badge";
import { EmptyState, ErrorBox, LoadingRows, Notice, useToast } from "@/components/ui/feedback";
import { Modal, Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/overlay";
import { Table, TableWrap, Td, Th, Tr } from "@/components/ui/table";
import { BloomBits } from "@/components/BloomBits";
import { useAuth } from "@/auth/AuthContext";
import { api, qs } from "@/lib/api";
import { useDebounced } from "@/lib/hooks";
import { bytes, num, pct, rate, stamp } from "@/lib/format";
import type { FieldCheck, Page, ScreeningResult, WatchEntry, WatchKind, WatchStats } from "@/lib/types";

export default function Risk() {
  const { can } = useAuth();
  const stats = useQuery({ queryKey: ["watch-stats"], queryFn: () => api.get<WatchStats>("/api/screening/stats") });
  return (
    <>
      <PageHeader
        title="Risk & Duplicates"
        description="New bookings are screened against a watchlist of past chargebacks, no-shows and fraud. A Bloom filter clears most guests instantly; any possible match is confirmed against the full list, so a filter false positive never holds an honest guest. Duplicate submissions are caught by the booking itself."
      />
      <div className="grid gap-6 xl:grid-cols-[1.5fr_1fr]">
        <div className="min-w-0">
          <Tabs defaultValue="screen">
            <TabsList>
              <TabsTrigger value="screen">Screen a guest</TabsTrigger>
              {can("MANAGER", "ADMIN") && <TabsTrigger value="watchlist">Watchlist</TabsTrigger>}
            </TabsList>
            <TabsContent value="screen" className="pt-4">
              <Screen bitCount={stats.data?.bitCount ?? 1} />
            </TabsContent>
            {can("MANAGER", "ADMIN") && (
              <TabsContent value="watchlist" className="pt-4">
                <Watchlist />
              </TabsContent>
            )}
          </Tabs>
        </div>
        <FilterStats stats={stats.data} error={stats.error} />
      </div>
    </>
  );
}

function Screen({ bitCount }: { bitCount: number }) {
  const queryClient = useQueryClient();
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const screen = useMutation({
    mutationFn: (body: { email: string; phone: string }) => api.post<ScreeningResult>("/api/screening/check", body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["watch-stats"] }),
  });
  const fp = useMutation({
    mutationFn: () => api.get<{ email: string; tries: number }>("/api/screening/false-positive"),
    onSuccess: (r) => {
      setEmail(r.email);
      setPhone("");
      screen.mutate({ email: r.email, phone: "" });
    },
  });
  function submit(e: FormEvent) {
    e.preventDefault();
    screen.mutate({ email, phone });
  }
  const r = screen.data;
  return (
    <Card>
      <CardBody className="space-y-5">
        <form onSubmit={submit} className="grid gap-4 sm:grid-cols-2" noValidate>
          <Field label="Email">{(id) => <Input id={id} type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="name@example.com" />}</Field>
          <Field label="Phone">{(id) => <Input id={id} type="tel" value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="+91 90000 11111" />}</Field>
          <div className="flex flex-wrap gap-2 sm:col-span-2">
            <Button type="submit" variant="primary" loading={screen.isPending}><Search /> Screen</Button>
            <Button onClick={() => fp.mutate()} loading={fp.isPending}>Find a real false positive</Button>
          </div>
        </form>
        {fp.data && (
          <p className="text-[13px] text-muted">
            Tried {num(fp.data.tries)} generated emails that are not on the watchlist before the live filter reported one as possibly listed.
          </p>
        )}
        <ErrorBox error={screen.error ?? fp.error} />
        {r && (
          <div className="space-y-4">
            <Notice tone={r.verdict === "MATCH" ? "bad" : "ok"} title={r.verdict === "MATCH" ? "On the watchlist" : "Clear"}>
              {r.message}
            </Notice>
            {r.checks.map((c) => <CheckView key={c.field} c={c} bitCount={bitCount} />)}
          </div>
        )}
      </CardBody>
    </Card>
  );
}

function CheckView({ c, bitCount }: { c: FieldCheck; bitCount: number }) {
  const zeros = c.bloom.positions.filter((_, i) => !c.bloom.bitWasSet[i]);
  return (
    <div className="rounded-md border border-line p-4">
      <p className="text-sm font-semibold">
        {c.field === "EMAIL" ? "Email" : "Phone"} <span className="font-mono text-[13px] font-normal text-muted">{c.normalized}</span>
      </p>
      <p className="mt-0.5 text-[12.5px] text-muted">Normalised first (lower case, digits only) so the same person matches however it was typed.</p>
      <p className="mt-3 text-sm">
        <span className="font-semibold">Stage 1, Bloom filter: </span>
        {c.bloom.possiblyPresent
          ? `all ${c.bloom.positions.length} bits are 1, so this value is possibly on the list. Other entries may have set these bits.`
          : `${zeros.length} of the ${c.bloom.positions.length} bits ${zeros.length === 1 ? "is" : "are"} 0, so it was never added. Cleared without a database lookup.`}
      </p>
      <div className="mt-3">
        <BloomBits trace={c.bloom} bitCount={bitCount} />
      </div>
      <p className="mt-3 text-sm">
        <span className="font-semibold">Stage 2, full watchlist: </span>
        {!c.exactChecked ? "skipped. A 0 bit is always right." : c.listed ? `found: ${c.reason}.` : "not found. The filter's match was a false positive, so the guest is not held."}
      </p>
    </div>
  );
}

function FilterStats({ stats, error }: { stats?: WatchStats; error: unknown }) {
  return (
    <Card className="h-fit">
      <CardHeader title="Bloom filter" description="Live figures for the filter in front of the watchlist." />
      <ErrorBox error={error} className="m-4" />
      {!stats ? <LoadingRows rows={5} /> : (
        <CardBody>
          {stats.overCapacity && (
            <Notice tone="warn" className="mb-4" title="Over capacity">The watchlist has more entries than the filter was sized for, so false positives are rising. An admin can resize it in Settings.</Notice>
          )}
          <dl className="grid grid-cols-[1fr_auto] gap-x-4 gap-y-2 text-sm">
            {([
              ["Watchlist entries", `${num(stats.entries)} (sized for ${num(stats.capacity)})`],
              ["Bits (m)", `${num(stats.bitCount)}, ${bytes(Math.ceil(stats.bitCount / 8))}`],
              ["Hash functions (k)", String(stats.hashCount)],
              ["Bits set to 1", `${num(stats.bitsSet)} (${pct((100 * stats.bitsSet) / stats.bitCount)})`],
              ["Expected false-positive rate", rate(stats.expectedFalsePositiveRate)],
            ] as const).map(([k, v]) => (
              <div key={k} className="contents"><dt className="text-muted">{k}</dt><dd className="text-right tabular">{v}</dd></div>
            ))}
            <div className="col-span-2 my-1 border-t border-line" />
            {([
              ["Values screened", stats.screened],
              ["Cleared by the filter alone", stats.clearedByFilter],
              ["Sent to the full watchlist", stats.exactLookups],
              ["Confirmed matches", stats.confirmed],
              ["False positives (not held)", stats.falsePositives],
            ] as const).map(([k, v]) => (
              <div key={k} className="contents"><dt className="text-muted">{k}</dt><dd className="text-right tabular">{num(v)}</dd></div>
            ))}
          </dl>
          {stats.builtAt && <p className="mt-4 text-[12.5px] text-muted">Filter last rebuilt {stamp(stats.builtAt)}. Counters cover screenings since the server started.</p>}
        </CardBody>
      )}
    </Card>
  );
}

function Watchlist() {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [text, setText] = useState("");
  const q = useDebounced(text);
  const [page, setPage] = useState(0);
  const [kind, setKind] = useState<WatchKind>("EMAIL");
  const [value, setValue] = useState("");
  const [reason, setReason] = useState("");
  const [removing, setRemoving] = useState<WatchEntry | null>(null);
  const list = useQuery({
    queryKey: ["watchlist", q, page],
    queryFn: () => api.get<Page<WatchEntry>>(`/api/watchlist${qs({ q, page, size: 15 })}`),
    placeholderData: (p) => p,
  });
  const add = useMutation({
    mutationFn: () => api.post<WatchEntry>("/api/watchlist", { kind, value, reason }),
    onSuccess: (e) => {
      toast("ok", `${e.value} added to the watchlist and the filter.`);
      setValue("");
      setReason("");
      queryClient.invalidateQueries({ queryKey: ["watchlist"] });
      queryClient.invalidateQueries({ queryKey: ["watch-stats"] });
    },
  });
  const remove = useMutation({
    mutationFn: (id: number) => api.del(`/api/watchlist/${id}`),
    onSuccess: () => {
      toast("ok", "Entry removed. The filter was rebuilt, because bits can't be cleared one entry at a time.");
      setRemoving(null);
      queryClient.invalidateQueries({ queryKey: ["watchlist"] });
      queryClient.invalidateQueries({ queryKey: ["watch-stats"] });
    },
  });
  return (
    <Card>
      <CardBody className="space-y-4">
        <form className="grid gap-3 sm:grid-cols-[8rem_1fr_1fr_auto] sm:items-end" onSubmit={(e) => { e.preventDefault(); add.mutate(); }} noValidate>
          <Field label="Kind">{(id) => <Select id={id} value={kind} onChange={(e) => setKind(e.target.value as WatchKind)}><option value="EMAIL">Email</option><option value="PHONE">Phone</option></Select>}</Field>
          <Field label="Value">{(id) => <Input id={id} value={value} onChange={(e) => setValue(e.target.value)} />}</Field>
          <Field label="Reason">{(id) => <Input id={id} value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Chargeback, Oct 2026" />}</Field>
          <Button type="submit" variant="primary" loading={add.isPending} disabled={!value}><Plus /> Add</Button>
        </form>
        <ErrorBox error={add.error ?? remove.error ?? list.error} />
        <Input aria-label="Search the watchlist" placeholder="Search value or reason" value={text} onChange={(e) => { setText(e.target.value); setPage(0); }} />
      </CardBody>
      {list.isLoading ? <LoadingRows /> : list.data && list.data.content.length === 0 ? <EmptyState title="No entries match" /> : list.data && (
        <>
          <TableWrap>
            <Table>
              <thead><tr><Th>Value</Th><Th>Kind</Th><Th>Reason</Th><Th>Added</Th><Th /></tr></thead>
              <tbody>
                {list.data.content.map((e) => (
                  <Tr key={e.id}>
                    <Td className="font-mono text-[13px]">{e.value}</Td>
                    <Td><Badge>{e.kind === "EMAIL" ? "email" : "phone"}</Badge></Td>
                    <Td className="whitespace-normal">{e.reason}</Td>
                    <Td className="text-muted">{stamp(e.createdAt)}</Td>
                    <Td className="text-right"><Button size="sm" variant="ghost" aria-label={`Remove ${e.value}`} onClick={() => setRemoving(e)}><Trash2 /></Button></Td>
                  </Tr>
                ))}
              </tbody>
            </Table>
          </TableWrap>
          <div className="flex items-center justify-between px-4 py-3 text-sm text-muted">
            <span>{num(list.data.total)} entries</span>
            <div className="flex gap-2">
              <Button size="sm" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</Button>
              <Button size="sm" disabled={page + 1 >= list.data.pages} onClick={() => setPage(page + 1)}>Next</Button>
            </div>
          </div>
        </>
      )}
      <Modal open={!!removing} onOpenChange={(o) => !o && setRemoving(null)} title={`Remove ${removing?.value}?`}
        description="Future bookings with this value won't be held. The filter is rebuilt from the remaining entries."
        footer={<><Button onClick={() => setRemoving(null)}>Keep it</Button><Button variant="danger" loading={remove.isPending} onClick={() => removing && remove.mutate(removing.id)}>Remove {removing?.value}</Button></>} />
    </Card>
  );
}
