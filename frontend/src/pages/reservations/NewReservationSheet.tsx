import { useEffect, useMemo, useState, type FormEvent } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Sheet } from "@/components/ui/overlay";
import { Button } from "@/components/ui/button";
import { Field, Input, Textarea } from "@/components/ui/form";
import { ErrorBox, Notice } from "@/components/ui/feedback";
import { Badge } from "@/components/ui/badge";
import { StayFields, floorOrNull, type StayValues } from "@/components/StayFields";
import { api } from "@/lib/api";
import { addDays, dayDate, label, plural, todayIso } from "@/lib/format";
import type { CreateResult, NewReservation, RoomType } from "@/lib/types";

const blankStay = (): StayValues => ({
  roomType: "DELUXE",
  checkIn: addDays(todayIso(), 3),
  checkOut: addDays(todayIso(), 5),
  rooms: 1,
  adults: 2,
  needsAccessible: false,
  maxFloor: "",
  preferredFloor: "",
  acceptsUpgrade: false,
});

export function NewReservationSheet({ open, onOpenChange, onOpenReservation }: {
  open: boolean;
  onOpenChange: (o: boolean) => void;
  onOpenReservation: (reference: string) => void;
}) {
  const queryClient = useQueryClient();
  const [guestName, setGuestName] = useState("");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [notes, setNotes] = useState("");
  const [stay, setStay] = useState<StayValues>(blankStay);
  const [result, setResult] = useState<CreateResult | null>(null);
  // One key per form: a double click or a retry after a timeout returns the first result instead of booking twice.
  const [idempotencyKey, setIdempotencyKey] = useState(() => crypto.randomUUID());

  useEffect(() => {
    if (open) {
      setGuestName("");
      setEmail("");
      setPhone("");
      setNotes("");
      setStay(blankStay());
      setResult(null);
      setIdempotencyKey(crypto.randomUUID());
    }
  }, [open]);

  const create = useMutation({
    mutationFn: (body: NewReservation) => api.post<CreateResult>("/api/reservations", body, { "Idempotency-Key": idempotencyKey }),
    onSuccess: (r) => {
      setResult(r);
      if (r.outcome !== "UNAVAILABLE") {
        queryClient.invalidateQueries();
      }
    },
  });

  const body = useMemo<NewReservation>(() => ({
    guestName,
    email,
    phone,
    roomType: stay.roomType,
    checkIn: stay.checkIn,
    checkOut: stay.checkOut,
    rooms: stay.rooms,
    adults: stay.adults,
    needsAccessible: stay.needsAccessible,
    maxFloor: floorOrNull(stay.maxFloor),
    preferredFloor: floorOrNull(stay.preferredFloor),
    acceptsUpgrade: stay.acceptsUpgrade,
    notes,
    joinWaitlist: false,
  }), [guestName, email, phone, notes, stay]);

  function submit(e?: FormEvent, overrides: Partial<NewReservation> = {}) {
    e?.preventDefault();
    if (overrides.roomType) setStay((s) => ({ ...s, roomType: overrides.roomType as RoomType }));
    // A changed request (another room type, joining the waitlist) is a new submission.
    if (Object.keys(overrides).length) setIdempotencyKey(crypto.randomUUID());
    create.mutate({ ...body, ...overrides });
  }

  const done = result && result.outcome !== "UNAVAILABLE";

  return (
    <Sheet
      open={open}
      onOpenChange={onOpenChange}
      title="New reservation"
      description="The guest is checked against the watchlist, every night of the stay is checked, and the rooms are held in one step."
      wide
      footer={
        done ? (
          <>
            <Button onClick={() => onOpenChange(false)}>Close</Button>
            {result.reservation && (
              <Button variant="primary" onClick={() => onOpenReservation(result.reservation!.reference)}>
                Open {result.reservation.reference}
              </Button>
            )}
          </>
        ) : (
          <>
            <Button onClick={() => onOpenChange(false)}>Cancel</Button>
            <Button variant="primary" type="submit" form="new-reservation" loading={create.isPending}>
              Check and book
            </Button>
          </>
        )
      }
    >
      {done ? (
        <Outcome result={result} />
      ) : (
        <form id="new-reservation" onSubmit={submit} className="space-y-6" noValidate>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Guest name" className="sm:col-span-2">
              {(id) => <Input id={id} value={guestName} onChange={(e) => setGuestName(e.target.value)} autoComplete="off" />}
            </Field>
            <Field label="Email" hint="Email or phone is needed for screening">
              {(id) => <Input id={id} type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="off" />}
            </Field>
            <Field label="Phone">
              {(id) => <Input id={id} type="tel" value={phone} onChange={(e) => setPhone(e.target.value)} autoComplete="off" />}
            </Field>
          </div>
          <StayFields value={stay} onChange={setStay} minDate={todayIso()} />
          <Field label="Notes">
            {(id) => <Textarea id={id} value={notes} onChange={(e) => setNotes(e.target.value)} maxLength={500} />}
          </Field>
          <ErrorBox error={create.error} />
          {result?.outcome === "UNAVAILABLE" && (
            <Unavailable result={result} busy={create.isPending} onTry={(roomType) => submit(undefined, { roomType })}
              onWaitlist={() => submit(undefined, { joinWaitlist: true })} />
          )}
        </form>
      )}
    </Sheet>
  );
}

function Unavailable({ result, busy, onTry, onWaitlist }: {
  result: CreateResult;
  busy: boolean;
  onTry: (t: RoomType) => void;
  onWaitlist: () => void;
}) {
  const a = result.availability!;
  return (
    <Notice tone="warn" title="Not available">
      <p>{result.message.replace(a.busiestNight, dayDate(a.busiestNight))}</p>
      <div className="mt-3 flex flex-wrap gap-2">
        {result.alternatives.map((alt) => (
          <Button key={alt.roomType} size="sm" disabled={busy} onClick={() => onTry(alt.roomType)}>
            Book {label(alt.roomType).toLowerCase()} instead ({plural(alt.fewestFree, "room")} free)
          </Button>
        ))}
        <Button size="sm" disabled={busy} onClick={onWaitlist}>
          Add to waitlist
        </Button>
      </div>
      {result.alternatives.length === 0 && (
        <p className="mt-2 text-[13px]">No other room type is free for every night of this stay.</p>
      )}
    </Notice>
  );
}

function Outcome({ result }: { result: CreateResult }) {
  const r = result.reservation!;
  const tone = result.outcome === "BOOKED" ? "ok" : result.outcome === "HELD" ? "bad" : "warn";
  const title = { BOOKED: "Booked", WAITLISTED: "On the waitlist", HELD: "Held for review", UNAVAILABLE: "" }[result.outcome];
  const a = result.availability;
  const s = result.screening;
  return (
    <div className="space-y-5">
      <Notice tone={tone} title={title}>
        {result.message}
      </Notice>
      <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-2 text-sm">
        <dt className="text-muted">Reference</dt>
        <dd className="font-semibold tabular">{r.reference}</dd>
        <dt className="text-muted">Guest</dt>
        <dd>{r.guestName}</dd>
        <dt className="text-muted">Rooms</dt>
        <dd>{plural(r.rooms, `${label(r.roomType).toLowerCase()} room`)}</dd>
        <dt className="text-muted">Stay</dt>
        <dd>
          {dayDate(r.checkIn)} to {dayDate(r.checkOut)} ({plural(r.nights, "night")})
        </dd>
      </dl>
      <div className="rounded-md border border-line bg-canvas p-4 text-[13px] text-muted">
        <p className="font-semibold text-ink">How this was decided</p>
        {s && (
          <p className="mt-1.5">
            Watchlist:{" "}
            {s.checks.map((c) => (
              <span key={c.field} className="mr-2">
                {c.field.toLowerCase()}{" "}
                <Badge tone={c.outcome === "CONFIRMED" ? "coral" : c.outcome === "FALSE_POSITIVE" ? "amber" : "green"}>
                  {c.outcome === "CLEARED_BY_FILTER" ? "cleared by the Bloom filter" : c.outcome === "FALSE_POSITIVE" ? "filter flagged, full list cleared" : "on the list"}
                </Badge>
              </span>
            ))}
          </p>
        )}
        {a && (
          <p className="mt-1.5">
            Availability: fewest free {label(a.roomType).toLowerCase()} rooms over the {plural(a.nights, "night")} was{" "}
            {a.fewestFree}, from a segment-tree range-minimum query ({a.nodesVisited} tree nodes visited, {a.fullNodes} of them
            answering for a whole block of nights).
          </p>
        )}
      </div>
    </div>
  );
}
