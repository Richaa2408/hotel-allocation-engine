import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router";
import { Modal, Sheet, Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/overlay";
import { Button } from "@/components/ui/button";
import { Field, Input, Textarea } from "@/components/ui/form";
import { Badge, StatusBadge } from "@/components/ui/badge";
import { ErrorBox, LoadingRows, Notice, useToast } from "@/components/ui/feedback";
import { StayFields, floorOrNull, type StayValues } from "@/components/StayFields";
import { useAuth } from "@/auth/AuthContext";
import { api } from "@/lib/api";
import { dayDate, label, plural, stamp, todayIso } from "@/lib/format";
import type { Reservation, ReservationDetail } from "@/lib/types";

export function ReservationDetailSheet({ reference, onClose }: { reference: string | null; onClose: () => void }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const { can } = useAuth();
  const [editing, setEditing] = useState(false);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [reason, setReason] = useState("");

  useEffect(() => {
    setEditing(false);
  }, [reference]);

  const detail = useQuery({
    queryKey: ["reservation", reference],
    queryFn: () => api.get<ReservationDetail>(`/api/reservations/${reference}`),
    enabled: !!reference,
  });

  const action = useMutation({
    mutationFn: ({ path, body }: { path: string; body?: unknown }) => api.post<Reservation>(`/api/reservations/${reference}/${path}`, body),
    onSuccess: (r, v) => {
      const done = { "check-in": "Checked in", "check-out": "Checked out", cancel: "Cancelled", approve: "Approved" }[v.path] ?? "Done";
      toast("ok", `${done}: ${r.reference}.`);
      queryClient.invalidateQueries();
      setCancelOpen(false);
    },
  });

  const r = detail.data?.reservation;
  const today = todayIso();
  const canCheckIn = r?.status === "CONFIRMED" && r.checkIn <= today && today < r.checkOut;
  const editable = r && !["CANCELLED", "CHECKED_OUT"].includes(r.status);

  return (
    <Sheet
      open={!!reference}
      onOpenChange={(o) => !o && onClose()}
      wide
      title={r ? `${r.reference}: ${r.guestName}` : reference ?? ""}
      description={r ? `${plural(r.rooms, `${label(r.roomType).toLowerCase()} room`)}, ${dayDate(r.checkIn)} to ${dayDate(r.checkOut)}` : undefined}
      footer={
        r && !editing ? (
          <>
            {r.status === "PENDING_REVIEW" && can("MANAGER", "ADMIN") && (
              <Button variant="primary" loading={action.isPending} onClick={() => action.mutate({ path: "approve" })}>
                Approve after review
              </Button>
            )}
            {canCheckIn && (
              <Button variant="primary" loading={action.isPending} onClick={() => action.mutate({ path: "check-in" })}>
                Check in
              </Button>
            )}
            {r.status === "CHECKED_IN" && (
              <Button variant="primary" loading={action.isPending} onClick={() => action.mutate({ path: "check-out" })}>
                Check out
              </Button>
            )}
            {editable && <Button onClick={() => setEditing(true)}>Edit stay</Button>}
            {["CONFIRMED", "PENDING_REVIEW", "WAITLISTED"].includes(r.status) && (
              <Button variant="danger" onClick={() => { setReason(""); setCancelOpen(true); }}>
                Cancel reservation
              </Button>
            )}
          </>
        ) : undefined
      }
    >
      {detail.isLoading && <LoadingRows rows={5} />}
      <ErrorBox error={detail.error} />
      <ErrorBox error={action.error} className="mb-4" />
      {r && detail.data && (editing ? (
        <EditStay reservation={r} onDone={() => setEditing(false)} />
      ) : (
        <Tabs defaultValue="summary">
          <TabsList>
            <TabsTrigger value="summary">Summary</TabsTrigger>
            <TabsTrigger value="rooms">Rooms</TabsTrigger>
            <TabsTrigger value="history">History</TabsTrigger>
          </TabsList>
          <TabsContent value="summary" className="pt-5">
            <Summary r={r} />
          </TabsContent>
          <TabsContent value="rooms" className="pt-5">
            <Rooms detail={detail.data} />
          </TabsContent>
          <TabsContent value="history" className="pt-5">
            <ol className="space-y-3">
              {detail.data.history.length === 0 && <p className="text-sm text-muted">No changes recorded yet.</p>}
              {detail.data.history.map((h, i) => (
                <li key={i} className="border-l-2 border-line pl-3 text-sm">
                  <p className="text-[12.5px] text-muted">
                    {stamp(h.at)} by {h.actor}
                  </p>
                  <p>{h.detail}</p>
                </li>
              ))}
            </ol>
          </TabsContent>
        </Tabs>
      ))}

      <Modal
        open={cancelOpen}
        onOpenChange={setCancelOpen}
        title={`Cancel ${r?.reference}?`}
        description="The rooms go back on sale, and the waitlist is checked for bookings that now fit."
        footer={
          <>
            <Button onClick={() => setCancelOpen(false)}>Keep it</Button>
            <Button variant="danger" loading={action.isPending} onClick={() => action.mutate({ path: "cancel", body: { reason } })}>
              Cancel {r?.reference}
            </Button>
          </>
        }
      >
        <Field label="Reason (optional)">
          {(id) => <Input id={id} value={reason} onChange={(e) => setReason(e.target.value)} maxLength={200} />}
        </Field>
      </Modal>
    </Sheet>
  );
}

function Summary({ r }: { r: Reservation }) {
  const rows: [string, React.ReactNode][] = [
    ["Status", <StatusBadge status={r.status} />],
    ["Guest", r.guestName],
    ["Email", r.email ?? "None"],
    ["Phone", r.phone ?? "None"],
    ["Stay", `${dayDate(r.checkIn)} to ${dayDate(r.checkOut)}, ${plural(r.nights, "night")}`],
    ["Rooms", `${plural(r.rooms, `${label(r.roomType).toLowerCase()} room`)} for ${plural(r.adults, "adult")}`],
    ["Must have", [r.needsAccessible && "accessible room", r.maxFloor !== null && `floor ${r.maxFloor} or below`].filter(Boolean).join(", ") || "Nothing special"],
    ["Nice to have", [r.preferredFloor !== null && `floor ${r.preferredFloor}`, r.acceptsUpgrade && "accepts an upgrade"].filter(Boolean).join(", ") || "Nothing"],
    ["Assigned rooms", r.roomNumbers.length ? r.roomNumbers.join(", ") : "Not yet"],
    ["Notes", r.notes ?? "None"],
    ["Booked by", `${r.createdBy}, ${stamp(r.createdAt)}`],
  ];
  return (
    <div className="space-y-4">
      {r.status === "PENDING_REVIEW" && (
        <Notice tone="bad" title="Held after a watchlist match">
          {r.blockReason}. No rooms are held until a manager approves it.
        </Notice>
      )}
      {r.screening === "CLEAR_AFTER_CHECK" && (
        <Notice tone="info" title="Screening note">
          The Bloom filter flagged a possible match, and the full watchlist confirmed there was none.
        </Notice>
      )}
      <dl className="grid grid-cols-[9rem_1fr] gap-x-4 gap-y-2.5 text-sm">
        {rows.map(([k, v]) => (
          <div key={k} className="contents">
            <dt className="text-muted">{k}</dt>
            <dd className="min-w-0 break-words">{v}</dd>
          </div>
        ))}
      </dl>
    </div>
  );
}

function Rooms({ detail }: { detail: ReservationDetail }) {
  const r = detail.reservation;
  const missing = detail.rooms.filter((x) => !x.roomNumber).length;
  return (
    <div className="space-y-5">
      {missing > 0 && ["CONFIRMED", "CHECKED_IN"].includes(r.status) && (
        <Notice tone="warn" title={`${plural(missing, "room")} not assigned yet`}>
          <Link to={`/assignment?date=${r.checkIn}`} className="font-medium text-teal-700 underline">
            Open Room Assignment for {dayDate(r.checkIn)}
          </Link>
        </Notice>
      )}
      <ul className="grid gap-2 sm:grid-cols-2">
        {detail.rooms.map((q) => (
          <li key={q.requestId} className="rounded-md border border-line px-3.5 py-2.5 text-sm">
            <span className="text-muted">Room {q.seq}: </span>
            {q.roomNumber ? <span className="font-semibold">{q.roomNumber}</span> : <span className="text-muted">not assigned</span>}
            {q.floor !== null && <span className="text-muted">, floor {q.floor}</span>}
          </li>
        ))}
      </ul>
      <div>
        <h3 className="mb-2 text-sm font-semibold">Room history</h3>
        {detail.roomHistory.length === 0 ? (
          <p className="text-sm text-muted">No rooms assigned yet.</p>
        ) : (
          <ol className="space-y-2">
            {detail.roomHistory.map((h, i) => (
              <li key={i} className="flex flex-wrap items-baseline gap-x-2 text-sm">
                <span className="font-semibold">Room {h.roomNumber}</span>
                <Badge tone={h.method === "MAX_FLOW" ? "teal" : h.method === "REASSIGNED" ? "amber" : "navy"}>
                  {h.method === "MAX_FLOW" ? "Max flow" : label(h.method)}
                </Badge>
                {!h.active && <Badge>released</Badge>}
                <span className="text-muted">
                  {stamp(h.assignedAt)} by {h.by}
                  {h.reason ? `: ${h.reason}` : ""}
                </span>
              </li>
            ))}
          </ol>
        )}
      </div>
    </div>
  );
}

function EditStay({ reservation: r, onDone }: { reservation: Reservation; onDone: () => void }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [guestName, setGuestName] = useState(r.guestName);
  const [notes, setNotes] = useState(r.notes ?? "");
  const [stay, setStay] = useState<StayValues>({
    roomType: r.roomType,
    checkIn: r.checkIn,
    checkOut: r.checkOut,
    rooms: r.rooms,
    adults: r.adults,
    needsAccessible: r.needsAccessible,
    maxFloor: r.maxFloor?.toString() ?? "",
    preferredFloor: r.preferredFloor?.toString() ?? "",
    acceptsUpgrade: r.acceptsUpgrade,
  });
  const save = useMutation({
    mutationFn: () => {
      const maxFloor = floorOrNull(stay.maxFloor);
      const preferredFloor = floorOrNull(stay.preferredFloor);
      return api.patch<Reservation>(`/api/reservations/${r.reference}`, {
        guestName,
        notes,
        roomType: stay.roomType,
        checkIn: stay.checkIn,
        checkOut: stay.checkOut,
        rooms: stay.rooms,
        adults: stay.adults,
        needsAccessible: stay.needsAccessible,
        maxFloor,
        clearMaxFloor: maxFloor === null,
        preferredFloor,
        clearPreferredFloor: preferredFloor === null,
        acceptsUpgrade: stay.acceptsUpgrade,
      });
    },
    onSuccess: (updated) => {
      toast("ok", `Saved ${updated.reference}.`);
      queryClient.invalidateQueries();
      onDone();
    },
  });
  return (
    <div className="space-y-5">
      {r.status === "CHECKED_IN" && (
        <Notice tone="info">The guest has checked in, so only the check-out date can change.</Notice>
      )}
      <Field label="Guest name">{(id) => <Input id={id} value={guestName} onChange={(e) => setGuestName(e.target.value)} />}</Field>
      <StayFields value={stay} onChange={setStay} />
      <Field label="Notes">{(id) => <Textarea id={id} value={notes} onChange={(e) => setNotes(e.target.value)} maxLength={500} />}</Field>
      <p className="text-[13px] text-muted">
        Changing the dates or rooms checks availability again. If the new stay doesn't fit, nothing changes.
      </p>
      <ErrorBox error={save.error} />
      <div className="flex justify-end gap-2">
        <Button onClick={onDone}>Discard changes</Button>
        <Button variant="primary" loading={save.isPending} onClick={() => save.mutate()}>
          Save changes
        </Button>
      </div>
    </div>
  );
}
