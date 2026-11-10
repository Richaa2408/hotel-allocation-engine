# Demo script (about 8 minutes) and viva preparation

## Before the demo

1. Close other programs and plug the laptop in.
2. From the project folder: `.\mvnw.cmd clean spring-boot:run`. Wait for `Started HotelAllocationEngineApplication`.
3. Open http://localhost:8080 and press **Reload sample data** with seed 42, so the hotel is in a known state.
4. Have a second terminal ready in the project folder for `.\mvnw.cmd test` in case the examiner asks.
5. Keep the browser zoom at 100%.

## Running order

| Time | Screen | What to do | What to say |
|---|---|---|---|
| 0:00 | (none) | Introduce the problem | Three front-desk decisions, each driven by one algorithm; Java computes everything, the page only draws the trace |
| 0:45 | Room assignment | Show 28 arrivals, 26 rooms; press **Assign rooms** | Max flow places 26, first-fit 24. Read one "not placed" reason |
| 1:30 | Room assignment | **Next** once, then **Next** until step 25 | Each step is a BFS shortest path. Step 25 uses a reverse edge: Room 301 is taken back from M. Mehta, who moves to Room 408. First-fit can't do this |
| 2:45 | Room assignment | **Final flow**, tick **Show minimum cut** | Cut capacity 26 equals the flow, which proves no arrangement places more. Here every room is on the cut, so the hotel is simply full |
| 3:30 | Availability | Deluxe, 2 rooms, default dates; **Check availability**, then type a name and **Book** | Range minimum over the stay decides it. After booking, only a few FULL nodes were updated and given lazy tags (amber dots) |
| 4:30 | Availability | Raise rooms until it refuses (or pick a busy weekend) | Refused booking changes nothing: check first, then update, so no night can go negative |
| 5:00 | Availability | Set check-out before check-in; press Check | Clear 400 message, not a crash |
| 5:20 | Screening | Screen `fraud.ring@example.com` | All 7 bits set, confirmed by the exact list: hold |
| 5:50 | Screening | Screen `guest53329@example.org` | All 7 bits set, but the exact list says no: a false positive, and the guest is cleared. This is why a Bloom hit is only "possibly listed" |
| 6:30 | Screening | Screen `asha@example.org` | A 0 bit: definitely not listed, no lookup needed. Point at the stats panel: false-positive rate about 1 in 86,000 |
| 7:00 | Terminal or slides | Show the benchmark tables (README or `docs/benchmarks`) | Tree 34x faster than scanning at 100,000 nights; max flow +15 to +18% guests; filter 32 to 47 times smaller than a HashSet with measured FPR matching theory |
| 7:45 | (none) | Close | One limitation each: Edmonds-Karp's O(VE^2), no deletion from a Bloom filter, in-memory storage |

## Edge cases the examiner may ask for

| Request | How to show it |
|---|---|
| Empty input | Room assignment > Edit request JSON, set `"guests": []`, Assign: "guests: must not be empty" |
| Duplicate room | Copy one room twice in the JSON: "Room 101 appears more than once." |
| Guest with no possible room | Already in the sample: "No available room is suite, ..." |
| Infeasible stay | More rooms than exist: "The hotel has only 16 deluxe rooms in total." |
| Dates outside the calendar | Check-out 4 months ahead: the message gives the allowed range |
| Invalid email / phone | `not-an-email`, or a 5-digit phone |
| Same email in different case | `FRAUD.Ring@Example.COM` is still blocked (normalisation) |
| Different data | Change the seed (for example 7) and Reload sample data |

## Viva questions to prepare (each member should be able to answer for their part)

**Edmonds-Karp**
- Why BFS? Shortest augmenting paths bound the number of iterations by O(VE), giving O(VE^2) overall,
  independent of capacities.
- What is a reverse edge for? It lets a later path cancel earlier flow, which is how a guest gets moved.
- Why is the answer optimal? Max-flow min-cut theorem: the reachable set at the end defines a cut with
  capacity equal to the flow, and no flow can exceed any cut.
- Why integer assignments? With integer capacities every augmentation is an integer, so flow on each
  guest-room edge is 0 or 1.
- Walk through `EdmondsKarp.maxFlow` and the `Search.bfs` round-number trick.

**Segment tree**
- Why at most 4 nodes per level? At each level only the two boundary nodes can be PARTIAL; the rest are FULL
  or OUTSIDE.
- What does lazy propagation save? A range update touches O(log n) nodes instead of every night; children
  are updated only when something later looks inside.
- Why can't it overbook? `tryBook` queries the minimum first and only updates if every night has enough.
- When is a scan better? Small calendars: at 1,000 nights they tie (benchmark).

**Bloom filter**
- Derive m and k from n and p. For n = 10,000 and p = 1%: m = 95,851 bits, k = 7.
- Why no false negatives? Bits are only ever set, and an added value set all its bits.
- Why does a false positive never block a guest? Only the exact blocklist can block.
- Why double hashing? Two good hashes simulate k hash functions with the same false-positive behaviour
  (Kirsch and Mitzenmacher).
- Why is a HashSet faster per lookup here, and why use the filter anyway? Memory (32 to 47 times smaller)
  and avoiding a remote lookup for 99% of guests.

**General**
- Separation of concerns: controllers validate, services explain, algorithms are pure Java with no Spring.
- How do you know the code is correct? Brute-force and naive comparisons on random inputs, theory checks
  (cut = flow, measured FPR), and edge-case tests: 40 in total.
- What would you change at scale? Hopcroft-Karp, a counting Bloom filter, a database with proper locking.
