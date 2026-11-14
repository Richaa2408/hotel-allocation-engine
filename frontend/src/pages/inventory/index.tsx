import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ChevronLeft, ChevronRight, Plus, Trash2, Wrench } from "lucide-react";
import { PageHeader } from "@/components/layout/AppShell";
import { Button } from "@/components/ui/button";
import { Card, CardBody, CardHeader } from "@/components/ui/card";
import { Checkbox, Field, Input, Select } from "@/components/ui/form";
import { Badge } from "@/components/ui/badge";
import { EmptyState, ErrorBox, LoadingRows, Notice, useToast } from "@/components/ui/feedback";
import { Modal, Sheet } from "@/components/ui/overlay";
import { Table, TableWrap, Td, Th, Tr } from "@/components/ui/table";
import { useAuth } from "@/auth/AuthContext";
import { api, qs } from "@/lib/api";
import { addDays, dayDate, label, nightsBetween, parseDay, plural, shortDate, todayIso } from "@/lib/format";
import { cn } from "@/lib/utils";
import {
  ROOM_TYPES,
  type AvailabilityCheck,
  type BlockResult,
  type HeatCell,
  type HeatRow,
  type InventoryAlert,
  type MaintenanceBlock,
  type RoomType,
} from "@/lib/types";

const levelStyle = {
  HEALTHY: "bg-teal-50 text-teal-800 hover:bg-teal-100",
  LOW: "bg-amber-50 text-amber-700 hover:bg-amber-600/15",
  SOLD_OUT: "bg-coral-50 text-coral-700 hover:bg-coral-500/15",
};

interface Selection {
  type: RoomType;
  from: number;
  to: number;
}

export default function Inventory() {
  const { can } = useAuth();
  const [start, setStart] = useState(todayIso());
  const [days, setDays] = useState(28);
  const [cell, setCell] = useState<{ row: HeatRow; cell: HeatCell } | null>(null);
  const [drag, setDrag] = useState<Selection | null>(null);
  const [stay, setStay] = useState({ roomType: "DELUXE" as RoomType, checkIn: addDays(todayIso(), 3), checkOut: addDays(todayIso(), 6), rooms: 1, needsAccessible: false });
  const [blockOpen, setBlockOpen] = useState(false);

  const heat = useQuery({
    queryKey: ["heatmap", start, days],
    queryFn: () => api.get<HeatRow[]>(`/api/inventory/heatmap${qs({ from: start, days })}`),
    placeholderData: (p) => p,
  });

  useEffect(() => {
    const up = () => setDrag((d) => {
      if (d && heat.data) {
        const nights = heat.data[0].cells;
        const a = Math.min(d.from, d.to);
        const b = Math.max(d.from, d.to);
        setStay((s) => ({ ...s, roomType: d.type, checkIn: nights[a].night, checkOut: addDays(nights[b].night, 1) }));
        document.getElementById("check-stay")?.scrollIntoView({ behavior: "smooth", block: "start" });
      }
      return null;
    });
    window.addEventListener("mouseup", up);
    return () => window.removeEventListener("mouseup", up);
  }, [heat.data]);

  const nights = heat.data?.[0]?.cells ?? [];

  return (
    <>
      <PageHeader
        title="Room Inventory"
        description="How many rooms of each type can still be sold on each night. Click a night for details, or drag across nights to check a stay."
        actions={can("MANAGER", "ADMIN") && (
          <Button variant="primary" onClick={() => setBlockOpen(true)}>
            <Wrench /> Take a room out of service
          </Button>
        )}
      />

      <Card>
        <CardHeader
          title="Availability calendar"
          description="Free rooms per night. Accessibility is a feature of a room, so accessible rooms are counted inside their room type."
          actions={
            <>
              <Button size="icon" aria-label="Earlier" disabled={start <= todayIso()} onClick={() => setStart(addDays(start, -Math.min(days, nightsBetween(todayIso(), start))))}>
                <ChevronLeft />
              </Button>
              <Input type="date" aria-label="First night" className="w-40" min={todayIso()} value={start} onChange={(e) => e.target.value && setStart(e.target.value)} />
              <Button size="icon" aria-label="Later" onClick={() => setStart(addDays(start, days))}>
                <ChevronRight />
              </Button>
              <Select aria-label="Nights shown" className="w-32" value={days} onChange={(e) => setDays(Number(e.target.value))}>
                <option value={14}>14 nights</option>
                <option value={28}>28 nights</option>
                <option value={42}>42 nights</option>
              </Select>
            </>
          }
        />
        <ErrorBox error={heat.error} className="m-4" />
        {heat.isLoading ? (
          <LoadingRows rows={4} />
        ) : (
          <div className="overflow-x-auto px-5 py-4 select-none">
            <table className="border-separate border-spacing-[3px] text-xs">
              <thead>
                <tr>
                  <th className="sticky left-0 z-10 w-36 bg-surface pr-3 text-left text-[13px] font-semibold text-muted">Room type</th>
                  {nights.map((c) => {
                    const d = parseDay(c.night);
                    const weekend = d.getDay() === 5 || d.getDay() === 6;
                    return (
                      <th key={c.night} className={cn("min-w-9 text-center font-medium", weekend ? "text-ink" : "text-muted")}>
                        <div className="text-[10px] uppercase">{d.toLocaleDateString("en-GB", { weekday: "short" }).slice(0, 2)}</div>
                        <div className="tabular">{d.getDate()}</div>
                      </th>
                    );
                  })}
                </tr>
              </thead>
              <tbody>
                {heat.data?.map((row) => (
                  <tr key={row.roomType}>
                    <th className="sticky left-0 z-10 bg-surface pr-3 text-left">
                      <p className="text-[13px] font-semibold text-ink">{label(row.roomType)}</p>
                      <p className="font-normal text-muted">{row.total} rooms, {row.accessibleTotal} accessible</p>
                    </th>
                    {row.cells.map((c, i) => {
                      const inDrag = drag && drag.type === row.roomType && i >= Math.min(drag.from, drag.to) && i <= Math.max(drag.from, drag.to);
                      return (
                        <td key={c.night} className="p-0">
                          <button
                            className={cn(
                              "relative h-10 w-full min-w-9 rounded-[4px] text-[13px] font-semibold tabular transition-colors",
                              levelStyle[c.level],
                              inDrag && "ring-2 ring-navy-700",
                            )}
                            aria-label={`${label(row.roomType)}, ${dayDate(c.night)}: ${c.free} of ${c.total} free`}
                            title={`${dayDate(c.night)}: ${c.free} free, ${c.booked} booked${c.blocked ? `, ${c.blocked} out of service` : ""}`}
                            onMouseDown={(e) => { e.preventDefault(); setDrag({ type: row.roomType, from: i, to: i }); }}
                            onMouseEnter={() => setDrag((d) => (d && d.type === row.roomType ? { ...d, to: i } : d))}
                            onClick={() => setCell({ row, cell: c })}
                          >
                            {c.free}
                            {c.blocked > 0 && <span className="absolute right-0.5 top-0.5 size-1.5 rounded-full bg-navy-700" aria-hidden />}
                          </button>
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
            <div className="mt-3 flex flex-wrap gap-x-5 gap-y-1 text-xs text-muted">
              <span className="flex items-center gap-1.5"><span className="size-3 rounded-[3px] bg-teal-50 ring-1 ring-teal-100" /> Healthy</span>
              <span className="flex items-center gap-1.5"><span className="size-3 rounded-[3px] bg-amber-50 ring-1 ring-amber-600/30" /> Low: 10% or fewer left</span>
              <span className="flex items-center gap-1.5"><span className="size-3 rounded-[3px] bg-coral-50 ring-1 ring-coral-500/30" /> Sold out</span>
              <span className="flex items-center gap-1.5"><span className="size-1.5 rounded-full bg-navy-700" /> A room is out of service</span>
            </div>
          </div>
        )}
      </Card>

      <div className="mt-6 grid gap-6 xl:grid-cols-[1.2fr_1fr]">
        <CheckStay stay={stay} setStay={setStay} />
        <Alerts />
      </div>

      <Maintenance />

      <Sheet open={!!cell} onOpenChange={(o) => !o && setCell(null)} title={cell ? `${label(cell.row.roomType)}, ${dayDate(cell.cell.night)}` : ""}
        description="Where this night's rooms stand.">
        {cell && (
          <div className="space-y-5">
            <dl className="grid grid-cols-2 gap-3">
              {[
                ["Free to sell", cell.cell.free],
                ["Booked", cell.cell.booked],
                ["Out of service", cell.cell.blocked],
                ["Rooms of this type", cell.cell.total],
              ].map(([k, v]) => (
                <div key={k} className="rounded-md border border-line p-3">
                  <dt className="text-[13px] text-muted">{k}</dt>
                  <dd className="mt-0.5 text-xl font-semibold tabular">{v}</dd>
                </div>
              ))}
            </dl>
            <p className="text-sm text-muted">
              Free = rooms of the type minus rooms out of service minus rooms already booked for this night. Bookings hold
              rooms by type; specific room numbers are given out on the arrival day.
            </p>
            <Button
              variant="primary"
              onClick={() => {
                setStay((s) => ({ ...s, roomType: cell.row.roomType, checkIn: cell.cell.night, checkOut: addDays(cell.cell.night, 1) }));
                setCell(null);
                setTimeout(() => document.getElementById("check-stay")?.scrollIntoView({ behavior: "smooth" }), 100);
              }}
            >
              Check a stay from this night
            </Button>
          </div>
        )}
      </Sheet>
      <AddBlock open={blockOpen} onOpenChange={setBlockOpen} />
    </>
  );
}

type StayInput = { roomType: RoomType; checkIn: string; checkOut: string; rooms: number; needsAccessible: boolean };

function CheckStay({ stay, setStay }: { stay: StayInput; setStay: (s: StayInput) => void }) {
  const check = useMutation({ mutationFn: (s: StayInput) => api.post<AvailabilityCheck>("/api/inventory/check", s) });
  const r = check.data;
  const max = r ? Math.max(1, ...r.freeByNight.map((n) => n.free)) : 1;
  return (
    <Card id="check-stay" className="scroll-mt-6">
      <CardHeader title="Check a stay" description="The fewest free rooms on any night of the stay decides it. Nothing is booked here." />
      <CardBody className="space-y-4">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <Field label="Room type">
            {(id) => (
              <Select id={id} value={stay.roomType} onChange={(e) => setStay({ ...stay, roomType: e.target.value as RoomType })}>
                {ROOM_TYPES.map((t) => <option key={t} value={t}>{label(t)}</option>)}
              </Select>
            )}
          </Field>
          <Field label="Check-in">{(id) => <Input id={id} type="date" min={todayIso()} value={stay.checkIn} onChange={(e) => setStay({ ...stay, checkIn: e.target.value })} />}</Field>
          <Field label="Check-out">{(id) => <Input id={id} type="date" min={stay.checkIn} value={stay.checkOut} onChange={(e) => setStay({ ...stay, checkOut: e.target.value })} />}</Field>
          <Field label="Rooms">{(id) => <Input id={id} type="number" min={1} max={20} value={stay.rooms} onChange={(e) => setStay({ ...stay, rooms: Number(e.target.value) })} />}</Field>
        </div>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <Checkbox label="Needs a wheelchair-accessible room" checked={stay.needsAccessible} onChange={(v) => setStay({ ...stay, needsAccessible: v })} />
          <Button variant="primary" loading={check.isPending} onClick={() => check.mutate(stay)}>Check availability</Button>
        </div>
        <ErrorBox error={check.error} />
        {r && (
          <div className="space-y-4">
            <Notice tone={r.fits ? "ok" : "warn"} title={r.fits ? "Available" : "Not available"}>
              {r.fits
                ? `At least ${r.fewestFree} ${label(r.roomType).toLowerCase()} rooms are free on every night; ${r.rooms} needed.`
                : r.limitedBy === "ACCESSIBLE_ROOMS"
                  ? `Rooms are free, but only ${r.fewestAccessible} accessible ${label(r.roomType).toLowerCase()} room${r.fewestAccessible === 1 ? "" : "s"} on ${dayDate(r.busiestNight)}; ${r.rooms} needed.`
                  : `Only ${r.fewestFree} free on ${dayDate(r.busiestNight)}, the busiest night; ${r.rooms} needed.`}
            </Notice>
            <div>
              <p className="mb-2 text-[13px] font-medium text-muted">Free rooms each night</p>
              <div className="flex h-28 items-end gap-1">
                {r.freeByNight.map((n) => (
                  <div key={n.night} className="flex min-w-6 flex-1 flex-col items-center gap-1" title={`${dayDate(n.night)}: ${n.free} free`}>
                    <span className="text-[11px] tabular text-muted">{n.free}</span>
                    <div
                      className={cn("w-full rounded-t-[3px]", n.night === r.busiestNight ? (r.fits ? "bg-teal-700" : "bg-coral-500") : "bg-teal-100")}
                      style={{ height: `${Math.max(4, (n.free / max) * 72)}px` }}
                    />
                    <span className="text-[10px] text-muted">{shortDate(n.night)}</span>
                  </div>
                ))}
              </div>
            </div>
            <details className="rounded-md border border-line bg-canvas px-4 py-3 text-[13px] text-muted">
              <summary className="cursor-pointer font-medium text-ink">How this was decided</summary>
              <p className="mt-2">
                One range-minimum query on the {label(r.roomType).toLowerCase()} segment tree covered {plural(r.nights, "night")}. It
                visited {r.nodesVisited} tree nodes; {r.fullNodes} lay wholly inside the stay and answered for their whole block of
                nights without looking further down. Booking it would be one range update of -{r.rooms} over the same nights.
                {r.needsAccessible && " Because accessibility is needed, the accessible-room tree was checked as well."}
              </p>
              <p className="mt-2">The Algorithm Lab draws the tree and shows how this scales against reading every night.</p>
            </details>
          </div>
        )}
      </CardBody>
    </Card>
  );
}

function Alerts() {
  const alerts = useQuery({ queryKey: ["inventory-alerts"], queryFn: () => api.get<InventoryAlert[]>("/api/inventory/alerts?days=30") });
  const list = alerts.data ?? [];
  return (
    <Card>
      <CardHeader title="Low-availability alerts" description="Next 30 nights, by room type." />
      <ErrorBox error={alerts.error} className="m-4" />
      {alerts.isLoading ? <LoadingRows rows={3} /> : list.length === 0 ? (
        <EmptyState title="No tight nights">Every room type has more than 10% of its rooms free for the next 30 nights.</EmptyState>
      ) : (
        <ul className="max-h-[22rem] divide-y divide-line overflow-y-auto">
          {list.map((a) => (
            <li key={a.roomType + a.night} className="flex items-center justify-between gap-3 px-5 py-2.5 text-sm">
              <span>
                <span className="font-medium">{label(a.roomType)}</span> <span className="text-muted">{dayDate(a.night)}</span>
              </span>
              <Badge tone={a.level === "SOLD_OUT" ? "coral" : "amber"}>
                {a.level === "SOLD_OUT" ? "Sold out" : `${a.free} of ${a.total} left`}
              </Badge>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

function Maintenance() {
  const { can } = useAuth();
  const queryClient = useQueryClient();
  const toast = useToast();
  const [removing, setRemoving] = useState<MaintenanceBlock | null>(null);
  const blocks = useQuery({ queryKey: ["maintenance"], queryFn: () => api.get<MaintenanceBlock[]>("/api/maintenance") });
  const remove = useMutation({
    mutationFn: (id: number) => api.del(`/api/maintenance/${id}`),
    onSuccess: () => {
      toast("ok", "Room back in service. The waitlist was checked for bookings that now fit.");
      queryClient.invalidateQueries();
      setRemoving(null);
    },
  });
  const list = blocks.data ?? [];
  return (
    <Card className="mt-6">
      <CardHeader title="Rooms out of service" description="Maintenance blocks take a room out of the inventory for a range of nights." />
      <ErrorBox error={blocks.error ?? remove.error} className="m-4" />
      {blocks.isLoading ? <LoadingRows rows={3} /> : list.length === 0 ? (
        <EmptyState title="Every room is in service" />
      ) : (
        <TableWrap>
          <Table>
            <thead>
              <tr>
                <Th>Room</Th>
                <Th>Type</Th>
                <Th>Out of service</Th>
                <Th>Reason</Th>
                <Th>Added by</Th>
                {can("MANAGER", "ADMIN") && <Th />}
              </tr>
            </thead>
            <tbody>
              {list.map((b) => (
                <Tr key={b.id}>
                  <Td className="font-semibold">{b.roomNumber}{b.accessible && <Badge className="ml-2">accessible</Badge>}</Td>
                  <Td>{label(b.roomType)}, floor {b.floor}</Td>
                  <Td className="tabular">{shortDate(b.startDate)} to {shortDate(b.endDate)} <span className="text-muted">({plural(b.nights, "night")})</span></Td>
                  <Td className="whitespace-normal">{b.reason}</Td>
                  <Td className="text-muted">{b.createdBy}</Td>
                  {can("MANAGER", "ADMIN") && (
                    <Td className="text-right">
                      <Button size="sm" variant="ghost" onClick={() => setRemoving(b)}>
                        <Trash2 /> Back in service
                      </Button>
                    </Td>
                  )}
                </Tr>
              ))}
            </tbody>
          </Table>
        </TableWrap>
      )}
      <Modal open={!!removing} onOpenChange={(o) => !o && setRemoving(null)} title={`Put room ${removing?.roomNumber} back in service?`}
        description="The room goes back into the inventory for those nights, and waitlisted bookings that now fit are confirmed."
        footer={<>
          <Button onClick={() => setRemoving(null)}>Keep it out</Button>
          <Button variant="primary" loading={remove.isPending} onClick={() => removing && remove.mutate(removing.id)}>Put room {removing?.roomNumber} back</Button>
        </>} />
    </Card>
  );
}

function AddBlock({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const queryClient = useQueryClient();
  const [roomNumber, setRoomNumber] = useState("");
  const [startDate, setStartDate] = useState(todayIso());
  const [endDate, setEndDate] = useState(addDays(todayIso(), 2));
  const [reason, setReason] = useState("");
  const add = useMutation({
    mutationFn: () => api.post<BlockResult>("/api/maintenance", { roomNumber, startDate, endDate, reason }),
    onSuccess: () => queryClient.invalidateQueries(),
  });
  useEffect(() => {
    if (open) add.reset();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);
  const r = add.data;
  return (
    <Sheet open={open} onOpenChange={onOpenChange} title="Take a room out of service"
      description="Refused if every room of the type is already booked on one of those nights. Guests assigned to the room are moved to another room automatically."
      footer={r ? <Button onClick={() => onOpenChange(false)}>Close</Button> : <>
        <Button onClick={() => onOpenChange(false)}>Cancel</Button>
        <Button variant="primary" loading={add.isPending} onClick={() => add.mutate()}><Plus /> Add block</Button>
      </>}>
      {r ? (
        <div className="space-y-4">
          <Notice tone="ok" title={`Room ${r.block.roomNumber} is out of service`}>
            {dayDate(r.block.startDate)} to {dayDate(r.block.endDate)}: {r.block.reason}.
          </Notice>
          {r.moved.length > 0 && (
            <Notice tone="info" title={`${plural(r.moved.length, "guest")} moved`}>
              <ul className="list-disc pl-4">{r.moved.map((m) => <li key={m}>{m}</li>)}</ul>
            </Notice>
          )}
          {r.unplaced.length > 0 && (
            <Notice tone="bad" title="Call these guests: no other room fits">
              <ul className="list-disc pl-4">{r.unplaced.map((m) => <li key={m}>{m}</li>)}</ul>
            </Notice>
          )}
          {r.moved.length === 0 && r.unplaced.length === 0 && <p className="text-sm text-muted">No guest was assigned to this room on those nights.</p>}
        </div>
      ) : (
        <div className="space-y-4">
          <Field label="Room number">{(id) => <Input id={id} value={roomNumber} onChange={(e) => setRoomNumber(e.target.value)} placeholder="e.g. 305" />}</Field>
          <div className="grid grid-cols-2 gap-3">
            <Field label="First night">{(id) => <Input id={id} type="date" min={todayIso()} value={startDate} onChange={(e) => setStartDate(e.target.value)} />}</Field>
            <Field label="Back in service on">{(id) => <Input id={id} type="date" min={startDate} value={endDate} onChange={(e) => setEndDate(e.target.value)} />}</Field>
          </div>
          <Field label="Reason">{(id) => <Input id={id} value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Bathroom leak repair" maxLength={200} />}</Field>
          <ErrorBox error={add.error} />
        </div>
      )}
    </Sheet>
  );
}
