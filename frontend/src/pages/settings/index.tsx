import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { PageHeader } from "@/components/layout/AppShell";
import { Button } from "@/components/ui/button";
import { Card, CardBody, CardHeader } from "@/components/ui/card";
import { Field, Input, Select } from "@/components/ui/form";
import { Badge } from "@/components/ui/badge";
import { ErrorBox, LoadingRows, Notice, useToast } from "@/components/ui/feedback";
import { Modal } from "@/components/ui/overlay";
import { Table, TableWrap, Td, Th, Tr } from "@/components/ui/table";
import { roleLabel, useAuth } from "@/auth/AuthContext";
import { api } from "@/lib/api";
import { bytes, num, rate } from "@/lib/format";
import type { Role, SeedSummary, UserView, WatchStats } from "@/lib/types";

const ROLES: Role[] = ["ADMIN", "MANAGER", "FRONT_DESK"];

export default function Settings() {
  return (
    <>
      <PageHeader title="Settings" description="Staff accounts, the watchlist filter's size, room imports and the demo data. Only administrators see this page." />
      <div className="space-y-6">
        <Users />
        <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
          <BloomSettings />
          <RoomImport />
        </div>
        <DemoReset />
      </div>
    </>
  );
}

function Users() {
  const queryClient = useQueryClient();
  const toast = useToast();
  const { me } = useAuth();
  const users = useQuery({ queryKey: ["users"], queryFn: () => api.get<UserView[]>("/api/admin/users") });
  const [form, setForm] = useState({ username: "", displayName: "", role: "FRONT_DESK" as Role, password: "" });
  const [resetFor, setResetFor] = useState<UserView | null>(null);
  const [newPassword, setNewPassword] = useState("");
  const refresh = () => queryClient.invalidateQueries({ queryKey: ["users"] });

  const create = useMutation({
    mutationFn: () => api.post<UserView>("/api/admin/users", form),
    onSuccess: (u) => {
      toast("ok", `Account ${u.username} created`);
      setForm({ username: "", displayName: "", role: "FRONT_DESK", password: "" });
      refresh();
    },
  });
  const change = useMutation({
    mutationFn: ({ id, ...body }: { id: number; role?: Role; enabled?: boolean; password?: string }) =>
      api.patch<UserView>(`/api/admin/users/${id}`, body),
    onSuccess: (u, vars) => {
      toast("ok", vars.password ? `Password reset for ${u.username}` : `${u.username} updated`);
      setResetFor(null);
      setNewPassword("");
      refresh();
    },
  });

  return (
    <Card>
      <CardHeader title="Staff accounts" description="Front desk: reservations, arrivals and screening. Manager: everything except this page. Administrator: everything." />
      <ErrorBox error={users.error ?? (resetFor ? null : change.error)} className="mx-4 mt-4" />
      {users.isLoading ? <LoadingRows rows={4} /> : (
        <TableWrap>
          <Table>
            <thead><tr><Th>Username</Th><Th>Name</Th><Th>Role</Th><Th>Status</Th><Th className="text-right"><span className="sr-only">Password</span></Th></tr></thead>
            <tbody>
              {users.data?.map((u) => {
                const self = u.username === me?.username;
                return (
                  <Tr key={u.id}>
                    <Td className="font-mono text-[13px]">{u.username}{self && <span className="ml-1.5 font-sans text-muted">(you)</span>}</Td>
                    <Td>{u.displayName}</Td>
                    <Td>
                      <Select aria-label={`Role of ${u.username}`} className="h-8 w-36" value={u.role} disabled={change.isPending}
                        onChange={(e) => change.mutate({ id: u.id, role: e.target.value as Role })}>
                        {ROLES.map((r) => <option key={r} value={r}>{roleLabel[r]}</option>)}
                      </Select>
                    </Td>
                    <Td>
                      <div className="flex items-center gap-2">
                        {u.enabled ? <Badge tone="green">Active</Badge> : <Badge>Disabled</Badge>}
                        {!self && (
                          <Button size="sm" variant="ghost" disabled={change.isPending} onClick={() => change.mutate({ id: u.id, enabled: !u.enabled })}>
                            {u.enabled ? "Disable" : "Enable"}
                          </Button>
                        )}
                      </div>
                    </Td>
                    <Td className="text-right"><Button size="sm" onClick={() => { change.reset(); setResetFor(u); }}>Reset password</Button></Td>
                  </Tr>
                );
              })}
            </tbody>
          </Table>
        </TableWrap>
      )}
      <CardBody className="border-t border-line">
        <form className="grid items-start gap-3 sm:grid-cols-2 xl:grid-cols-[1fr_1fr_1fr_1fr_auto]" onSubmit={(e) => { e.preventDefault(); create.mutate(); }}>
          <Field label="Username">{(id) => <Input id={id} required maxLength={30} autoComplete="off" value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} />}</Field>
          <Field label="Full name">{(id) => <Input id={id} maxLength={100} value={form.displayName} onChange={(e) => setForm({ ...form, displayName: e.target.value })} />}</Field>
          <Field label="Role">
            {(id) => (
              <Select id={id} value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value as Role })}>
                {ROLES.map((r) => <option key={r} value={r}>{roleLabel[r]}</option>)}
              </Select>
            )}
          </Field>
          <Field label="First password" hint="10 to 72 characters">{(id) => <Input id={id} type="password" required minLength={10} maxLength={72} autoComplete="new-password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} />}</Field>
          <div className="sm:col-span-2 xl:col-span-1 xl:pt-[26px]"><Button type="submit" variant="primary" loading={create.isPending}>Add account</Button></div>
        </form>
        <ErrorBox error={create.error} className="mt-3" />
      </CardBody>

      <Modal
        open={!!resetFor}
        onOpenChange={(o) => { if (!o) { setResetFor(null); setNewPassword(""); } }}
        title={`Reset password for ${resetFor?.username}`}
        description="They will need the new password the next time they sign in. Sessions already open stay signed in."
        footer={<Button variant="primary" disabled={newPassword.length < 10} loading={change.isPending} onClick={() => change.mutate({ id: resetFor!.id, password: newPassword })}>Set password</Button>}
      >
        <Field label="New password" hint="10 to 72 characters">{(id) => <Input id={id} type="password" autoComplete="new-password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} />}</Field>
        <ErrorBox error={change.error} className="mt-3" />
      </Modal>
    </Card>
  );
}

function BloomSettings() {
  const queryClient = useQueryClient();
  const toast = useToast();
  const stats = useQuery({ queryKey: ["screening-stats"], queryFn: () => api.get<WatchStats>("/api/screening/stats") });
  const [capacity, setCapacity] = useState(0);
  const [fpr, setFpr] = useState(0);
  useEffect(() => {
    if (stats.data && capacity === 0) {
      setCapacity(stats.data.capacity);
      setFpr(stats.data.targetFalsePositiveRate * 100);
    }
  }, [stats.data, capacity]);
  const save = useMutation({
    mutationFn: () => api.put<WatchStats>("/api/admin/bloom", { capacity, falsePositiveRate: fpr / 100 }),
    onSuccess: (s) => {
      queryClient.setQueryData(["screening-stats"], s);
      toast("ok", `Filter rebuilt: ${num(s.bitCount)} bits, ${s.hashCount} hash functions`);
    },
  });
  const s = stats.data;
  // Preview of the size the backend will pick, using the standard formulas.
  const m = capacity > 0 && fpr > 0 && fpr < 100 ? Math.ceil((-capacity * Math.log(fpr / 100)) / Math.LN2 ** 2) : 0;
  const k = m ? Math.max(1, Math.round((m / capacity) * Math.LN2)) : 0;

  return (
    <Card>
      <CardHeader title="Watchlist filter" description="The Bloom filter is sized for an expected number of entries and a target false-positive rate. Saving rebuilds it from the watchlist table." />
      <CardBody className="space-y-4">
        <ErrorBox error={stats.error} />
        {s && (
          <dl className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-3">
            <div><dt className="text-muted">Entries</dt><dd className="font-semibold tabular">{num(s.entries)}</dd></div>
            <div><dt className="text-muted">Size</dt><dd className="font-semibold tabular">{num(s.bitCount)} bits ({bytes(Math.ceil(s.bitCount / 8))})</dd></div>
            <div><dt className="text-muted">Hash functions</dt><dd className="font-semibold tabular">{s.hashCount}</dd></div>
            <div className="col-span-2 sm:col-span-3"><dt className="text-muted">Expected false-positive rate now</dt><dd className="font-semibold tabular">{rate(s.expectedFalsePositiveRate)}</dd></div>
          </dl>
        )}
        {s?.overCapacity && <Notice tone="warn" title="Over capacity">The watchlist has more entries than the filter was sized for, so false positives are more frequent. Raise the capacity.</Notice>}
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Expected entries (n)">{(id) => <Input id={id} type="number" min={1} value={capacity || ""} onChange={(e) => setCapacity(Number(e.target.value))} />}</Field>
          <Field label="Target false-positive rate (%)">{(id) => <Input id={id} type="number" step="0.001" min={0.0001} max={50} value={fpr || ""} onChange={(e) => setFpr(Number(e.target.value))} />}</Field>
        </div>
        {m > 0 && <p className="text-[13px] text-muted">Will use about {num(m)} bits ({bytes(Math.ceil(m / 8))}) and {k} hash functions: m = -n ln p / (ln 2)², k = (m / n) ln 2.</p>}
        <Button variant="primary" loading={save.isPending} disabled={!m} onClick={() => save.mutate()}>Save and rebuild</Button>
        <ErrorBox error={save.error} />
      </CardBody>
    </Card>
  );
}

interface RoomImportResult { added: number; skipped: number; problems: string[] }

function RoomImport() {
  const queryClient = useQueryClient();
  const [file, setFile] = useState<File | null>(null);
  const upload = useMutation({
    mutationFn: () => {
      const form = new FormData();
      form.append("file", file!);
      return api.post<RoomImportResult>("/api/rooms/import", form);
    },
    onSuccess: () => queryClient.invalidateQueries(),
  });
  const r = upload.data;
  return (
    <Card>
      <CardHeader title="Import rooms" description="Add rooms from a CSV file. Room numbers that already exist are skipped. Availability is recalculated after the import." />
      <CardBody className="space-y-4">
        <Field label="CSV file" hint="Columns: number, type, floor, accessible, capacity. Example: 701,DELUXE,7,yes,2">
          {(id) => <Input id={id} type="file" accept=".csv,text/csv" className="py-1.5" onChange={(e) => { setFile(e.target.files?.[0] ?? null); upload.reset(); }} />}
        </Field>
        <Button variant="primary" disabled={!file} loading={upload.isPending} onClick={() => upload.mutate()}>Import rooms</Button>
        <ErrorBox error={upload.error} />
        {r && (
          <Notice tone={r.problems.length ? "warn" : "ok"} title={`${r.added} added, ${r.skipped} skipped`}>
            {r.problems.length > 0 && <ul className="mt-1 list-disc pl-5">{r.problems.slice(0, 10).map((p, i) => <li key={i}>{p}</li>)}</ul>}
          </Notice>
        )}
      </CardBody>
    </Card>
  );
}

function DemoReset() {
  const queryClient = useQueryClient();
  const [seed, setSeed] = useState(42);
  const [confirming, setConfirming] = useState(false);
  const reset = useMutation({
    mutationFn: () => api.post<SeedSummary>(`/api/admin/reset?seed=${seed}`),
    onSuccess: () => { setConfirming(false); queryClient.invalidateQueries(); },
  });
  const r = reset.data;
  return (
    <Card>
      <CardHeader title="Demo data" description="Replace every room, guest, reservation, assignment, maintenance block and watchlist entry with freshly generated data, and clear the activity log. The same seed always gives the same hotel. Staff accounts are kept."
        actions={
          <div className="flex items-end gap-2">
            <Field label="Seed" className="w-28">{(id) => <Input id={id} type="number" value={seed} onChange={(e) => setSeed(Number(e.target.value))} />}</Field>
            <Button variant="danger" onClick={() => setConfirming(true)}>Reset demo data</Button>
          </div>
        } />
      {r && (
        <CardBody>
          <Notice tone="ok" title={`Hotel regenerated with seed ${r.seed}`}>
            {num(r.rooms)} rooms, {num(r.reservations)} reservations ({num(r.inHouse)} in house, {num(r.arrivalsToday)} arriving today, {num(r.waitlisted)} waitlisted, {num(r.held)} held for review), {num(r.watchlist)} watchlist entries, {num(r.maintenance)} maintenance blocks.
          </Notice>
        </CardBody>
      )}
      <Modal
        open={confirming}
        onOpenChange={setConfirming}
        title="Reset all hotel data?"
        description="Everything except staff accounts is deleted and generated again, including the activity log. This can't be undone."
        footer={<Button variant="danger" loading={reset.isPending} onClick={() => reset.mutate()}>Delete and regenerate</Button>}
      >
        <ErrorBox error={reset.error} />
        {reset.isPending && <p className="text-sm text-muted">Generating the hotel. This takes about 20 seconds.</p>}
      </Modal>
    </Card>
  );
}
