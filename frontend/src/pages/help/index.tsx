import type { ReactNode } from "react";
import { Link } from "react-router";
import { PageHeader } from "@/components/layout/AppShell";
import { Card, CardBody } from "@/components/ui/card";

function Section({ id, title, children }: { id: string; title: string; children: ReactNode }) {
  return (
    <Card id={id} className="scroll-mt-6">
      <CardBody className="space-y-3 text-[14.5px] leading-relaxed">
        <h2 className="text-base font-semibold">{title}</h2>
        {children}
      </CardBody>
    </Card>
  );
}

const TOC = [
  ["day", "A day at the front desk"],
  ["reservations", "Reservations"],
  ["inventory", "Room inventory"],
  ["assignment", "Room assignment"],
  ["risk", "Risk and duplicates"],
  ["lab", "Algorithm Lab"],
  ["roles", "Roles"],
  ["faq", "Questions"],
] as const;

export default function Help() {
  return (
    <>
      <PageHeader title="Help" description="How StayFlow works, page by page, and what each algorithm decides." />
      <div className="grid gap-6 lg:grid-cols-[13rem_1fr]">
        <nav aria-label="On this page" className="lg:sticky lg:top-6 lg:self-start">
          <ul className="flex flex-wrap gap-x-4 gap-y-1.5 text-sm lg:flex-col">
            {TOC.map(([id, t]) => <li key={id}><a href={`#${id}`} className="text-teal-700 hover:underline">{t}</a></li>)}
          </ul>
        </nav>
        <div className="min-w-0 space-y-4">
          <Section id="day" title="A day at the front desk">
            <ol className="list-decimal space-y-1.5 pl-5">
              <li>Start on <Link className="text-teal-700 hover:underline" to="/">Overview</Link>: today's arrivals and departures, occupancy, and alerts for nights that are nearly full.</li>
              <li>Take bookings on <Link className="text-teal-700 hover:underline" to="/reservations">Reservations</Link>. Each one is screened, checked against inventory and saved in one step.</li>
              <li>Before guests arrive, open <Link className="text-teal-700 hover:underline" to="/assignment">Room assignment</Link> and run it for today. Every arriving guest gets a room that meets their must-haves, or the page tells you exactly which rule can't be met.</li>
              <li>Check guests in and out from the reservation panel. Cancelling frees the nights, and the first waitlisted booking that now fits is confirmed automatically.</li>
            </ol>
          </Section>

          <Section id="reservations" title="Reservations">
            <p>A new booking runs through these checks in order:</p>
            <ol className="list-decimal space-y-1 pl-5">
              <li><b>Input:</b> check-out after check-in, check-in not in the past, check-out within the next 400 nights, 1 to 20 rooms.</li>
              <li><b>Duplicate:</b> the same guest (by email or phone) already holding the same room type for the same dates is rejected. Submitting the same form twice is caught by an idempotency key, so a double click never books twice.</li>
              <li><b>Watchlist:</b> the guest's email and phone are screened. A match puts the booking on hold for a manager to approve.</li>
              <li><b>Availability:</b> the stay must fit every night. If it doesn't, you see other room types or nearby dates that do, and you can join the waitlist.</li>
            </ol>
            <p>Managers can import bookings from CSV (Reservations, Import). Anyone can export the filtered list.</p>
          </Section>

          <Section id="inventory" title="Room inventory">
            <p>The heatmap shows free rooms per room type for each night: green is plenty, amber is getting full, red is full, grey is blocked for maintenance. Select a range to see the detail, check a stay, or block rooms for maintenance.</p>
            <p><b>How it is fast:</b> each room type keeps a segment tree over the next 400 nights. Asking "is there a room every night from the 12th to the 19th?" is a range-minimum query, and booking subtracts one from a whole range of nights using lazy propagation. Both take time proportional to log 400 (about 9 levels), not to the length of the stay. Accessible rooms have their own tree per type, so an accessible booking must fit both.</p>
          </Section>

          <Section id="assignment" title="Room assignment">
            <p>Assigning rooms is a matching problem. Each guest can only use rooms that meet their must-haves (type, or better if they accept an upgrade; accessible if needed; at or below their highest floor) and that are free for their whole stay.</p>
            <p>StayFlow builds a flow network (source, guests, eligible rooms, sink, every capacity 1) and runs <b>Edmonds-Karp</b>. The maximum flow is the largest number of guests that can be placed at the same time. Unlike first-fit, it can move a guest placed earlier to free a room for someone with fewer options.</p>
            <p>If not everyone fits, the <b>minimum cut</b> explains why: the guests still reachable from the source after the last search, and the rooms they could use, form the group that is short. The page says, for example, that 3 guests need an accessible deluxe room on floor 3 or below but only 2 exist. Preferred floor is used only to break ties.</p>
            <p>You can override any room by hand. If a room goes out of service, its guests are reassigned automatically and the history keeps the reason.</p>
          </Section>

          <Section id="risk" title="Risk and duplicates">
            <p>The watchlist holds emails and phone numbers that need a manager's review. Every new booking is checked against it in two stages:</p>
            <ol className="list-decimal space-y-1 pl-5">
              <li>A <b>Bloom filter</b> in memory answers "definitely not on the list" or "possibly on the list". Almost every guest is cleared here without touching the database.</li>
              <li>Only a "possibly" goes to an exact database lookup. A Bloom filter can say "possibly" for a value that was never added (a false positive), so no booking is ever held on the filter's word alone.</li>
            </ol>
            <p>Duplicate bookings are not detected with the filter: they use exact database rules, because a false positive there would turn away a real guest.</p>
          </Section>

          <Section id="lab" title="Algorithm Lab">
            <p>The <Link className="text-teal-700 hover:underline" to="/lab">Lab</Link> runs the same Java algorithms on generated data that you control, without touching the live hotel. You can step through a segment tree query, replay each augmenting path of the max flow and see the minimum cut, and watch bits being set in a Bloom filter. Each algorithm has a benchmark against the simple approach at three input sizes, timed when you press Run.</p>
          </Section>

          <Section id="roles" title="Roles">
            <ul className="list-disc space-y-1 pl-5">
              <li><b>Front desk:</b> reservations, check-in and check-out, screening, inventory checks, and running or changing room assignment.</li>
              <li><b>Manager:</b> all of the above, plus approving held bookings, importing bookings, maintenance blocks, editing the watchlist and the activity log.</li>
              <li><b>Administrator:</b> everything, plus staff accounts, the filter's size and resetting the demo data.</li>
            </ul>
          </Section>

          <Section id="faq" title="Questions">
            <dl className="space-y-3">
              <div><dt className="font-medium">Why was a booking held when the guest isn't on the list?</dt><dd className="text-muted">It wasn't held by the filter. Only an exact match in the database holds a booking. Check the screening line in the reservation panel to see which value matched.</dd></div>
              <div><dt className="font-medium">Why can't a guest get a room when the hotel isn't full?</dt><dd className="text-muted">Their must-haves may rule out every free room. Room assignment names the group that is short and by how many.</dd></div>
              <div><dt className="font-medium">Are the numbers on the Overview real?</dt><dd className="text-muted">They are computed from the reservations in the database. The demo data is generated from a seed, so the same seed always gives the same hotel.</dd></div>
              <div><dt className="font-medium">Where is the API documentation?</dt><dd className="text-muted">At <a className="text-teal-700 hover:underline" href="/swagger-ui/index.html" target="_blank" rel="noreferrer">/swagger-ui</a> while signed in.</dd></div>
            </dl>
          </Section>
        </div>
      </div>
    </>
  );
}
