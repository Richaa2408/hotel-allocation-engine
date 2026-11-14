import { Checkbox, Field, Input, Select } from "@/components/ui/form";
import { label, nightsBetween } from "@/lib/format";
import { ROOM_TYPES, type RoomType } from "@/lib/types";

export interface StayValues {
  roomType: RoomType;
  checkIn: string;
  checkOut: string;
  rooms: number;
  adults: number;
  needsAccessible: boolean;
  maxFloor: string;
  preferredFloor: string;
  acceptsUpgrade: boolean;
}

/**
 * Stay details shared by the new-booking and edit forms. Hard rules (accessibility, highest floor) are
 * shown apart from the preference (preferred floor), which only breaks ties during room assignment.
 */
export function StayFields({ value, onChange, minDate }: { value: StayValues; onChange: (v: StayValues) => void; minDate?: string }) {
  const set = <K extends keyof StayValues>(k: K, v: StayValues[K]) => onChange({ ...value, [k]: v });
  const nights = value.checkIn && value.checkOut ? nightsBetween(value.checkIn, value.checkOut) : 0;

  return (
    <div className="space-y-5">
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Room type">
          {(id) => (
            <Select id={id} value={value.roomType} onChange={(e) => set("roomType", e.target.value as RoomType)}>
              {ROOM_TYPES.map((t) => (
                <option key={t} value={t}>
                  {label(t)}
                </option>
              ))}
            </Select>
          )}
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Rooms">
            {(id) => (
              <Input id={id} type="number" min={1} max={20} value={value.rooms} onChange={(e) => set("rooms", Number(e.target.value))} />
            )}
          </Field>
          <Field label="Adults">
            {(id) => (
              <Input id={id} type="number" min={1} max={80} value={value.adults} onChange={(e) => set("adults", Number(e.target.value))} />
            )}
          </Field>
        </div>
        <Field label="Check-in">
          {(id) => <Input id={id} type="date" min={minDate} value={value.checkIn} onChange={(e) => set("checkIn", e.target.value)} />}
        </Field>
        <Field label="Check-out" hint={nights > 0 ? `${nights} night${nights === 1 ? "" : "s"}` : undefined}>
          {(id) => <Input id={id} type="date" min={value.checkIn || minDate} value={value.checkOut} onChange={(e) => set("checkOut", e.target.value)} />}
        </Field>
      </div>

      <fieldset className="rounded-md border border-line p-4">
        <legend className="px-1 text-[13px] font-semibold text-ink">Must have</legend>
        <p className="mb-3 text-[12.5px] text-muted">Rooms that break these are never offered.</p>
        <div className="grid gap-4 sm:grid-cols-2">
          <Checkbox label="Wheelchair-accessible room" checked={value.needsAccessible} onChange={(v) => set("needsAccessible", v)} />
          <Field label="Highest floor" hint="Leave empty for any floor">
            {(id) => (
              <Input id={id} type="number" min={0} max={50} value={value.maxFloor} onChange={(e) => set("maxFloor", e.target.value)} placeholder="Any" />
            )}
          </Field>
        </div>
      </fieldset>

      <fieldset className="rounded-md border border-line p-4">
        <legend className="px-1 text-[13px] font-semibold text-ink">Nice to have</legend>
        <p className="mb-3 text-[12.5px] text-muted">Used to choose between equally good rooms. Never costs another guest a room.</p>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Preferred floor">
            {(id) => (
              <Input id={id} type="number" min={0} max={50} value={value.preferredFloor} onChange={(e) => set("preferredFloor", e.target.value)} placeholder="None" />
            )}
          </Field>
          <Checkbox label="Accepts an upgrade" hint="A better room type if theirs is full" checked={value.acceptsUpgrade} onChange={(v) => set("acceptsUpgrade", v)} />
        </div>
      </fieldset>
    </div>
  );
}

export function floorOrNull(s: string): number | null {
  return s.trim() === "" ? null : Number(s);
}
