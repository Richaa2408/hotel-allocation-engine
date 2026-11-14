import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useSearchParams } from "react-router";
import { ChevronLeft, ChevronRight, Download, Plus, Search, Upload } from "lucide-react";
import { PageHeader } from "@/components/layout/AppShell";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input, Select } from "@/components/ui/form";
import { StatusBadge, statusLabel } from "@/components/ui/badge";
import { EmptyState, ErrorBox, LoadingRows, useToast } from "@/components/ui/feedback";
import { Table, TableWrap, Td, Th, Tr } from "@/components/ui/table";
import { useAuth } from "@/auth/AuthContext";
import { api, download, qs } from "@/lib/api";
import { useDebounced } from "@/lib/hooks";
import { label, num, plural, shortDate } from "@/lib/format";
import { ROOM_TYPES, STATUSES, type Page, type Reservation } from "@/lib/types";
import { NewReservationSheet } from "./NewReservationSheet";
import { ReservationDetailSheet } from "./ReservationDetailSheet";
import { ImportSheet } from "./ImportSheet";

const PAGE_SIZE = 25;

export default function Reservations() {
  const [params, setParams] = useSearchParams();
  const { can } = useAuth();
  const toast = useToast();
  const [text, setText] = useState(params.get("q") ?? "");
  const q = useDebounced(text);
  const status = params.get("status") ?? "";
  const type = params.get("type") ?? "";
  const from = params.get("from") ?? "";
  const to = params.get("to") ?? "";
  const sort = params.get("sort") ?? "";
  const page = Number(params.get("page") ?? 0);
  const [creating, setCreating] = useState(false);
  const [importing, setImporting] = useState(false);
  const open = params.get("open");

  function update(changes: Record<string, string>) {
    const next = new URLSearchParams(params);
    for (const [k, v] of Object.entries(changes)) {
      if (v) next.set(k, v);
      else next.delete(k);
    }
    if (!("page" in changes)) next.delete("page");
    setParams(next, { replace: true });
  }

  const filters = { q, status, type, from, to };
  const list = useQuery({
    queryKey: ["reservations", filters, sort, page],
    queryFn: () => api.get<Page<Reservation>>(`/api/reservations${qs({ ...filters, sort, page, size: PAGE_SIZE })}`),
    placeholderData: (prev) => prev,
  });

  const data = list.data;
  return (
    <>
      <PageHeader
        title="Reservations"
        description="Every booking, from enquiry to check-out. New bookings are screened against the watchlist and checked night by night before rooms are held."
        actions={
          <>
            <Button onClick={() => download(`/api/reservations/export${qs(filters)}`, "reservations.csv").catch(() => toast("bad", "Export failed."))}>
              <Download /> Export CSV
            </Button>
            {can("MANAGER", "ADMIN") && (
              <Button onClick={() => setImporting(true)}>
                <Upload /> Import CSV
              </Button>
            )}
            <Button variant="primary" onClick={() => setCreating(true)}>
              <Plus /> New reservation
            </Button>
          </>
        }
      />

      <Card>
        <div className="grid gap-3 border-b border-line p-4 sm:grid-cols-2 lg:grid-cols-[1.5fr_1fr_1fr_1fr_1fr_1fr]">
          <div className="relative sm:col-span-2 lg:col-span-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-subtle" aria-hidden />
            <Input
              aria-label="Search reservations"
              className="pl-9"
              placeholder="Reference, guest name or email"
              value={text}
              onChange={(e) => {
                setText(e.target.value);
                update({ q: e.target.value });
              }}
            />
          </div>
          <Select aria-label="Status" value={status} onChange={(e) => update({ status: e.target.value })}>
            <option value="">All statuses</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {statusLabel(s)}
              </option>
            ))}
          </Select>
          <Select aria-label="Room type" value={type} onChange={(e) => update({ type: e.target.value })}>
            <option value="">All room types</option>
            {ROOM_TYPES.map((t) => (
              <option key={t} value={t}>
                {label(t)}
              </option>
            ))}
          </Select>
          <Input type="date" aria-label="Staying on or after" title="Staying on or after" value={from} onChange={(e) => update({ from: e.target.value })} />
          <Input type="date" aria-label="Staying before" title="Staying before" value={to} onChange={(e) => update({ to: e.target.value })} />
          <Select aria-label="Sort" value={sort} onChange={(e) => update({ sort: e.target.value })}>
            <option value="">Newest bookings first</option>
            <option value="checkIn">Check-in, earliest first</option>
            <option value="-checkIn">Check-in, latest first</option>
            <option value="guest">Guest name</option>
          </Select>
        </div>

        <ErrorBox error={list.error} className="m-4" />
        {list.isLoading ? (
          <LoadingRows rows={8} />
        ) : data && data.content.length === 0 ? (
          <EmptyState title="No reservations match these filters">Clear a filter or search for another name.</EmptyState>
        ) : (
          data && (
            <>
              <TableWrap>
                <Table>
                  <thead>
                    <tr>
                      <Th>Reference</Th>
                      <Th>Guest</Th>
                      <Th>Room type</Th>
                      <Th>Stay</Th>
                      <Th className="text-right">Rooms</Th>
                      <Th>Assigned</Th>
                      <Th>Status</Th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.content.map((r) => (
                      <Tr key={r.id} className="cursor-pointer" onClick={() => update({ open: r.reference, page: String(page) })}>
                        <Td>
                          <button className="font-medium text-teal-700 hover:underline" onClick={(e) => { e.stopPropagation(); update({ open: r.reference, page: String(page) }); }}>
                            {r.reference}
                          </button>
                        </Td>
                        <Td>
                          <p>{r.guestName}</p>
                          {r.needsAccessible && <p className="text-xs text-muted">Needs accessible room</p>}
                        </Td>
                        <Td>{label(r.roomType)}</Td>
                        <Td className="tabular">
                          {shortDate(r.checkIn)} to {shortDate(r.checkOut)}
                          <span className="text-muted">, {plural(r.nights, "night")}</span>
                        </Td>
                        <Td className="text-right tabular">{r.rooms}</Td>
                        <Td className="tabular">{r.roomNumbers.length ? r.roomNumbers.join(", ") : <span className="text-muted">None</span>}</Td>
                        <Td>
                          <StatusBadge status={r.status} />
                        </Td>
                      </Tr>
                    ))}
                  </tbody>
                </Table>
              </TableWrap>
              <div className="flex flex-wrap items-center justify-between gap-3 px-4 py-3 text-sm text-muted">
                <p>
                  {num(data.page * data.size + 1)} to {num(data.page * data.size + data.content.length)} of {num(data.total)}
                </p>
                <div className="flex gap-2">
                  <Button size="sm" disabled={page === 0} onClick={() => update({ page: String(page - 1) })}>
                    <ChevronLeft /> Previous
                  </Button>
                  <Button size="sm" disabled={page + 1 >= data.pages} onClick={() => update({ page: String(page + 1) })}>
                    Next <ChevronRight />
                  </Button>
                </div>
              </div>
            </>
          )
        )}
      </Card>

      <NewReservationSheet
        open={creating}
        onOpenChange={setCreating}
        onOpenReservation={(ref) => {
          setCreating(false);
          update({ open: ref, page: String(page) });
        }}
      />
      <ReservationDetailSheet reference={open} onClose={() => update({ open: "", page: String(page) })} />
      <ImportSheet open={importing} onOpenChange={setImporting} />
    </>
  );
}
