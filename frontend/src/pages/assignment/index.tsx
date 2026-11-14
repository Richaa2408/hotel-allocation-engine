import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useSearchParams } from "react-router";
import { Accessibility, ChevronLeft, ChevronRight, Play } from "lucide-react";
import { PageHeader } from "@/components/layout/AppShell";
import { Button } from "@/components/ui/button";
import { Card, CardBody, CardHeader, Stat } from "@/components/ui/card";
import { Field, Input, Select } from "@/components/ui/form";
import { Badge } from "@/components/ui/badge";
import { EmptyState, ErrorBox, LoadingRows, Notice, useToast } from "@/components/ui/feedback";
import { Sheet, Tabs, TabsContent, TabsList, TabsTrigger, Tip } from "@/components/ui/overlay";
import { Table, TableWrap, Td, Th, Tr } from "@/components/ui/table";
import { api, qs } from "@/lib/api";
import { addDays, dayDate, label, longDate, plural, shortDate, stamp, todayIso } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { AssignmentLogItem, Board, RequestCard, RoomTile, RunResult } from "@/lib/types";

const stateStyle: Record<RoomTile["state"], string> = {
  FREE: "border-line-strong bg-surface text-ink",
  ASSIGNED: "border-teal-600/40 bg-teal-50 text-teal-800",
  OCCUPIED: "border-navy-300/50 bg-navy-900/[0.06] text-navy-800",
  OUT_OF_SERVICE: "border-dashed border-line-strong bg-sunken text-muted",
};

const stateText: Record<RoomTile["state"], string> = {
  FREE: "Free tonight",
  ASSIGNED: "Arriving guest",
  OCCUPIED: "Occupied",
  OUT_OF_SERVICE: "Out of service",
};

export default function Assignment() {
  const [params, setParams] = useSearchParams();
  const date = params.get("date") ?? todayIso();
  const setDate = (d: string) => setParams({ date: d }, { replace: true });
  const queryClient = useQueryClient();
  const toast = useToast();
  const [result, setResult] = useState<RunResult | null>(null);
  const [moving, setMoving] = useState<RequestCard | null>(null);
  const [tile, setTile] = useState<RoomTile | null>(null);

  const board = useQuery({
    queryKey: ["board", date],
    queryFn: () => api.get<Board>(`/api/assignments/board${qs({ date })}`),
  });
  const run = useMutation({
    mutationFn: () => api.post<RunResult>(`/api/assignments/run${qs({ date })}`),
    onSuccess: (r) => {
      setResult(r);
      queryClient.setQueryData(["board", date], r.board);
      queryClient.invalidateQueries({ predicate: (q) => q.queryKey[0] !== "board" });
    },
  });
  const unassign = useMutation({
    mutationFn: (id: number) => api.del<Board>(`/api/assignments/requests/${id}/room`),
    onSuccess: (b) => {
      queryClient.setQueryData(["board", date], b);
      toast("ok", "Room taken off the booking.");
    },
  });

  const b = board.data;
  const cards = useMemo(() => (b ? [...b.requests].sort((x, y) => Number(!!x.roomNumber) - Number(!!y.roomNumber)) : []), [b]);

  return (
    <>
      <PageHeader
        title="Room Assignment"
        description="Give each arriving guest a physical room. Maximum bipartite matching (Edmonds-Karp) places as many guests as any arrangement can, using only rooms that meet their must-haves and are free for their whole stay."
        actions={
          <>
            <Button size="icon" aria-label="Previous day" onClick={() => { setResult(null); setDate(addDays(date, -1)); }}>
              <ChevronLeft />
            </Button>
            <Input type="date" aria-label="Arrival date" className="w-40" value={date} onChange={(e) => { setResult(null); if (e.target.value) setDate(e.target.value); }} />
            <Button size="icon" aria-label="Next day" onClick={() => { setResult(null); setDate(addDays(date, 1)); }}>
              <ChevronRight />
            </Button>
            <Button variant="primary" loading={run.isPending} disabled={!b || b.pending === 0} onClick={() => run.mutate()}>
              <Play /> Assign rooms
            </Button>
          </>
        }
      />

      <ErrorBox error={board.error ?? run.error ?? unassign.error} className="mb-4" />
      {board.isLoading || !b ? (
        <LoadingRows />
      ) : (
        <>
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <Stat label={`Room requests arriving ${date === todayIso() ? "today" : shortDate(date)}`} value={b.requests.length} detail={plural(new Set(b.requests.map((r) => r.reference)).size, "booking")} />
            <Stat label="With a room" value={b.assigned} tone={b.assigned === b.requests.length && b.requests.length > 0 ? "good" : "default"} />
            <Stat label="Waiting for a room" value={b.pending} tone={b.pending > 0 ? "warn" : "default"} />
            <Stat label="Rooms free that night" value={b.freeRooms} detail={`of ${b.rooms.length}`} />
          </div>

          {result && <RunSummary r={result} />}

          <Tabs defaultValue="requests" className="mt-6">
            <TabsList>
              <TabsTrigger value="requests">Arrivals</TabsTrigger>
              <TabsTrigger value="floors">Floor plan</TabsTrigger>
              <TabsTrigger value="log">Assignment log</TabsTrigger>
            </TabsList>
            <TabsContent value="requests" className="pt-4">
              {cards.length === 0 ? (
                <Card><EmptyState title={`No arrivals on ${longDate(date)}`}>Pick another date with the arrows above.</EmptyState></Card>
              ) : (
                <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
                  {cards.map((c) => (
                    <RequestCardView key={c.requestId} c={c} onMove={() => setMoving(c)} onUnassign={() => unassign.mutate(c.requestId)} />
                  ))}
                </div>
              )}
            </TabsContent>
            <TabsContent value="floors" className="pt-4">
              <FloorPlan rooms={b.rooms} onPick={setTile} />
            </TabsContent>
            <TabsContent value="log" className="pt-4">
              <Log />
            </TabsContent>
          </Tabs>
        </>
      )}

      <MoveSheet card={moving} board={b} date={date} onClose={() => setMoving(null)} />
      <Sheet open={!!tile} onOpenChange={(o) => !o && setTile(null)} title={tile ? `Room ${tile.number}` : ""}
        description={tile ? `${label(tile.type)}, floor ${tile.floor}, sleeps ${tile.capacity}${tile.accessible ? ", wheelchair accessible" : ""}` : undefined}>
        {tile && (
          <dl className="grid grid-cols-[9rem_1fr] gap-y-2.5 text-sm">
            <dt className="text-muted">That night</dt><dd>{stateText[tile.state]}</dd>
            <dt className="text-muted">Housekeeping</dt><dd>{label(tile.housekeeping)}</dd>
            {tile.guestName && <><dt className="text-muted">Guest</dt><dd>{tile.guestName} ({tile.reference})</dd></>}
            {tile.until && <><dt className="text-muted">Until</dt><dd>{dayDate(tile.until)}</dd></>}
            {tile.blockReason && <><dt className="text-muted">Reason</dt><dd>{tile.blockReason}</dd></>}
          </dl>
        )}
      </Sheet>
    </>
  );
}

function RunSummary({ r }: { r: RunResult }) {
  return (
    <Card className="mt-6">
      <CardHeader title="Result" description={r.message} />
      <CardBody className="space-y-4">
        {r.requests > 0 && (
          <div className="grid gap-4 sm:grid-cols-3">
            <div>
              <p className="text-[13px] text-muted">Placed by max flow</p>
              <p className="text-xl font-semibold tabular">{r.placed} of {r.requests}</p>
            </div>
            <div>
              <p className="text-[13px] text-muted">First-fit, in booking order, would place</p>
              <p className="text-xl font-semibold tabular">{r.firstFitPlaced}</p>
            </div>
            <div>
              <p className="text-[13px] text-muted">Rooms that fitted at least one guest</p>
              <p className="text-xl font-semibold tabular">{r.candidateRooms}</p>
            </div>
          </div>
        )}
        {r.shortages.map((s, i) => (
          <Notice key={i} tone="warn" title={`Shortage of ${plural(s.shortage, "room")}`}>
            <p>{s.summary}</p>
            {s.roomNumbers.length > 0 && <p className="mt-1">Rooms that fit them: {s.roomNumbers.join(", ")}. Guests: {s.guestNames.join(", ")}.</p>}
            <p className="mt-1 text-[12.5px]">Proven by the minimum cut: no rearrangement of rooms can place more of these guests.</p>
          </Notice>
        ))}
        {r.unplaced.length > 0 && (
          <div>
            <h3 className="mb-2 text-sm font-semibold">Needs attention</h3>
            <ul className="space-y-2">
              {r.unplaced.map((u) => (
                <li key={u.requestId} className="rounded-md border border-line border-l-4 border-l-amber-600 px-4 py-3 text-sm">
                  <p><span className="font-semibold">{u.guestName}</span> <span className="text-muted">{u.reference}, needs {u.hardRules}</span></p>
                  <p className="mt-1">{u.reason}</p>
                  <p className="mt-1 font-medium">{u.suggestion}</p>
                </li>
              ))}
            </ul>
          </div>
        )}
      </CardBody>
    </Card>
  );
}

function RequestCardView({ c, onMove, onUnassign }: { c: RequestCard; onMove: () => void; onUnassign: () => void }) {
  return (
    <Card className={cn("p-4", !c.roomNumber && "border-l-4 border-l-amber-600")}>
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="truncate font-semibold">{c.guestName}{c.roomsInBooking > 1 && <span className="font-normal text-muted"> (room {c.seq} of {c.roomsInBooking})</span>}</p>
          <p className="text-[13px] text-muted">{c.reference}, {shortDate(c.checkIn)} to {shortDate(c.checkOut)}, {plural(c.adults, "adult")}</p>
        </div>
        {c.roomNumber ? (
          <div className="text-right">
            <p className="text-lg font-semibold tabular">{c.roomNumber}</p>
            <Badge tone={c.method === "MANUAL" ? "navy" : c.method === "REASSIGNED" ? "amber" : "teal"}>{c.method === "MAX_FLOW" ? "Max flow" : label(c.method ?? "")}</Badge>
          </div>
        ) : (
          <Badge tone="amber">No room yet</Badge>
        )}
      </div>
      <div className="mt-3 space-y-1 text-[13px]">
        <p className="flex items-center gap-1.5">
          <span className="font-medium">Must have:</span> {c.hardRules}
          {c.needsAccessible && <Accessibility className="size-3.5 text-navy-700" aria-label="accessible" />}
        </p>
        <p className="text-muted"><span className="font-medium text-ink">Nice to have:</span> {c.preference ?? "nothing"}</p>
      </div>
      <div className="mt-3 flex gap-2">
        <Button size="sm" onClick={onMove}>{c.roomNumber ? "Move to another room" : "Choose a room"}</Button>
        {c.roomNumber && c.status === "CONFIRMED" && <Button size="sm" variant="ghost" onClick={onUnassign}>Take room off</Button>}
      </div>
    </Card>
  );
}

function FloorPlan({ rooms, onPick }: { rooms: RoomTile[]; onPick: (t: RoomTile) => void }) {
  const floors = [...new Set(rooms.map((r) => r.floor))].sort((a, b) => b - a);
  return (
    <Card>
      <CardBody className="space-y-5">
        <div className="flex flex-wrap gap-x-5 gap-y-1 text-xs text-muted">
          {(Object.keys(stateText) as RoomTile["state"][]).map((s) => (
            <span key={s} className="flex items-center gap-1.5"><span className={cn("size-3 rounded-[3px] border", stateStyle[s])} /> {stateText[s]}</span>
          ))}
          <span className="flex items-center gap-1.5"><Accessibility className="size-3.5" /> Accessible</span>
        </div>
        {floors.map((f) => (
          <div key={f} className="grid gap-2 sm:grid-cols-[5rem_1fr]">
            <p className="pt-2 text-sm font-semibold text-muted">Floor {f}</p>
            <div className="flex flex-wrap gap-1.5">
              {rooms.filter((r) => r.floor === f).map((r) => (
                <Tip key={r.number} content={`${label(r.type)}. ${stateText[r.state]}${r.guestName ? `: ${r.guestName}` : ""}${r.blockReason ? `: ${r.blockReason}` : ""}. Housekeeping: ${label(r.housekeeping).toLowerCase()}.`}>
                  <button onClick={() => onPick(r)} className={cn("w-[4.5rem] rounded-md border px-2 py-1.5 text-left", stateStyle[r.state])}>
                    <span className="flex items-center justify-between text-sm font-semibold tabular">
                      {r.number}
                      {r.accessible && <Accessibility className="size-3.5" aria-label="accessible" />}
                    </span>
                    <span className="block truncate text-[11px]">{r.state === "FREE" ? label(r.type) : r.guestName ?? (r.state === "OUT_OF_SERVICE" ? "Out" : "")}</span>
                  </button>
                </Tip>
              ))}
            </div>
          </div>
        ))}
      </CardBody>
    </Card>
  );
}

function MoveSheet({ card, board, date, onClose }: { card: RequestCard | null; board?: Board; date: string; onClose: () => void }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [room, setRoom] = useState("");
  const free = (board?.rooms ?? []).filter((r) => r.state === "FREE");
  // A shortlist for convenience; the server checks every rule and the whole stay before accepting.
  const fitting = card ? free.filter((r) =>
    (card.acceptsUpgrade ? ["STANDARD", "DELUXE", "EXECUTIVE", "SUITE"].indexOf(r.type) >= ["STANDARD", "DELUXE", "EXECUTIVE", "SUITE"].indexOf(card.roomType) : r.type === card.roomType)
    && (!card.needsAccessible || r.accessible) && (card.maxFloor === null || r.floor <= card.maxFloor)) : [];
  const move = useMutation({
    mutationFn: () => api.put<Board>(`/api/assignments/requests/${card!.requestId}/room`, { roomNumber: room }),
    onSuccess: (b) => {
      queryClient.setQueryData(["board", date], b);
      toast("ok", `${card!.guestName} is in room ${room}.`);
      setRoom("");
      onClose();
    },
  });
  return (
    <Sheet open={!!card} onOpenChange={(o) => { if (!o) { setRoom(""); move.reset(); onClose(); } }} title={card ? `Room for ${card.guestName}` : ""}
      description={card ? `Must have: ${card.hardRules}. Rooms that break a must-have are refused.` : undefined}
      footer={<><Button onClick={onClose}>Cancel</Button><Button variant="primary" disabled={!room} loading={move.isPending} onClick={() => move.mutate()}>Put guest in room {room}</Button></>}>
      <div className="space-y-4">
        <Field label="Room" hint={`${plural(fitting.length, "free room")} meet${fitting.length === 1 ? "s" : ""} the must-haves that night; the stay is checked night by night when you save.`}>
          {(id) => (
            <Select id={id} value={room} onChange={(e) => setRoom(e.target.value)}>
              <option value="">Choose a room</option>
              {fitting.map((r) => (
                <option key={r.number} value={r.number}>
                  {r.number}: {label(r.type)}, floor {r.floor}{r.accessible ? ", accessible" : ""}
                </option>
              ))}
            </Select>
          )}
        </Field>
        <ErrorBox error={move.error} />
      </div>
    </Sheet>
  );
}

function Log() {
  const log = useQuery({ queryKey: ["assignment-log"], queryFn: () => api.get<AssignmentLogItem[]>("/api/assignments/log") });
  if (log.isLoading) return <LoadingRows />;
  return (
    <Card>
      <ErrorBox error={log.error} className="m-4" />
      <TableWrap>
        <Table>
          <thead>
            <tr><Th>When</Th><Th>Booking</Th><Th>Room</Th><Th>How</Th><Th>Note</Th><Th>By</Th></tr>
          </thead>
          <tbody>
            {(log.data ?? []).map((l, i) => (
              <Tr key={i}>
                <Td className="tabular text-muted">{stamp(l.at)}</Td>
                <Td>{l.reference} <span className="text-muted">{l.guestName}</span></Td>
                <Td className="font-semibold tabular">{l.roomNumber}{!l.active && <Badge className="ml-2">released</Badge>}</Td>
                <Td><Badge tone={l.method === "MAX_FLOW" ? "teal" : l.method === "REASSIGNED" ? "amber" : "navy"}>{l.method === "MAX_FLOW" ? "Max flow" : label(l.method)}</Badge></Td>
                <Td className="whitespace-normal text-muted">{l.reason ?? ""}</Td>
                <Td className="text-muted">{l.by}</Td>
              </Tr>
            ))}
          </tbody>
        </Table>
      </TableWrap>
    </Card>
  );
}
