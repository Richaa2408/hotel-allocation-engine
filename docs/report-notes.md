# Technical report notes

Raw material for the 5 to 8 page technical report (deliverable C). Each section maps to an item the
guidelines list. Rewrite in your own words; the numbers come from this repository's tests and benchmarks.

## 1. Problem statement and constraints

**Users:** front-desk and reservations staff at a mid-sized city hotel (40 rooms in the sample).

**Three decisions, made many times a day:**

1. **Tonight's room assignment.** 28 guests arrive for 26 free rooms. Each guest booked a room type, some
   need a wheelchair-accessible room, some can't go above a given floor, and some accept an upgrade. Goal:
   place as many guests as possible, and explain anyone who can't be placed.
2. **Availability for a stay.** A guest asks for k rooms of a type for nights a to b. Goal: answer
   correctly and book without ever overbooking any night, quickly enough for live enquiries.
3. **Fraud screening.** Each new booking's email and phone are checked against a blocklist of past
   chargebacks and fraud. Goal: never block an honest guest, and avoid an exact (database) lookup for the
   great majority of clean guests.

**Input constraints (enforced, returning HTTP 400 with a message):** non-empty guest and room lists, unique
booking references and room numbers, floors at least 0, at most 2,000 guests and rooms per request,
check-out after check-in, dates inside the 90-night calendar, rooms requested at least 1 and at most the
hotel's total of that type, valid email (one `@`, text on both sides), phone with 7 to 15 digits.

**Measurable success criteria:**

| Criterion | Target | Achieved |
|---|---|---|
| Assignment is optimal | Equals brute force on every test graph | 500 of 500 random graphs |
| Assignment beats the simple method | More guests than first-fit | +12 to +488 guests (+15 to +18%) at 100 to 5,000 guests |
| Availability is exact | Same answer as scanning every night | 0 mismatches in 100,000 random operations |
| Availability is fast | Faster than a scan on realistic calendars | 6x at 10,000 nights, 34x at 100,000 |
| Screening never blocks an honest guest | 0 false blocks | 0, including on a deliberately overloaded filter |
| Screening saves lookups | Most guests cleared without an exact check | 99% cleared by the filter |
| Screening error matches theory | Measured FPR close to (1 - e^(-kn/m))^k | 0.97 to 1.01% measured against 1.00% expected |

## 2. System design

```
Browser (HTML + plain JavaScript modules, SVG drawings)
   │  JSON over HTTP
   ▼
Controllers (api/)            validate input (@Valid), map errors to 400 via ApiExceptionHandler
   ▼
Services (service/)           build algorithm inputs from hotel data, explain results, keep state
   ▼
Algorithms (algorithm/)       EdmondsKarp, AvailabilitySegmentTree, BloomFilter, FirstFitAssigner
                              pure Java, no Spring, each returns a result plus a trace
```

Each algorithm returns a trace (augmenting paths, visited tree nodes, hashed bit positions). The frontend
only draws traces; it never computes a result. This satisfies the rule that Java executes the assessed
algorithms.

API table: see the README.

## 3. Algorithms and pseudocode

### 3.1 Room assignment as maximum flow (Edmonds-Karp)

Network: source S, one node per guest, one node per room, sink T. Edges S to each guest (capacity 1),
guest to each suitable room (capacity 1), each room to T (capacity 1). A suitable room has the booked type
(or better, if the guest accepts an upgrade), is accessible if needed, and is on or below the guest's
highest floor.

```
EDMONDS-KARP(G, s, t)
  for each edge e: flow[e] = 0                  // reverse edge e^1 has capacity 0
  total = 0
  while BFS finds a path P from s to t using edges with capacity[e] - flow[e] > 0:
      b = min over e in P of (capacity[e] - flow[e])
      for each e in P:
          flow[e]   += b
          flow[e^1] -= b                         // creates residual capacity to undo later
      total += b
      record step (P, b, total)
  S_side = nodes the last BFS reached
  cut = forward edges from S_side to the rest
  return total, steps, cut
```

Guest g is assigned room r exactly when the edge g to r carries flow 1.

**Correctness.** Ford-Fulkerson terminates with a maximum flow when no augmenting path exists (max-flow
min-cut theorem): the nodes reachable from s form a cut whose capacity equals the flow, and no flow can
exceed any cut. With unit capacities the maximum flow is an integer flow, so it is a valid matching
(each guest and each room used at most once), and its value is the maximum number of guests placed.
Reverse edges let later paths reassign earlier guests, which is why it beats first-fit.

**Evidence.** CLRS figure 26.1 network gives 23; 500 random graphs agree with brute force; every test checks
cut capacity = flow; 20 generated hotels give valid assignments that are never worse than first-fit.

### 3.2 Availability with a segment tree and lazy propagation

```
QUERY-MIN(node, lo, hi, a, b)
  if [lo, hi] and [a, b] don't overlap: return +infinity          // OUTSIDE
  if a <= lo and hi <= b: return min[node]                       // FULL
  PUSH-DOWN(node)                                                // PARTIAL
  mid = (lo + hi) / 2
  return min(QUERY-MIN(2node, lo, mid, a, b), QUERY-MIN(2node+1, mid+1, hi, a, b))

RANGE-ADD(node, lo, hi, a, b, d)
  if no overlap: return
  if a <= lo and hi <= b: min[node] += d; lazy[node] += d; return
  PUSH-DOWN(node)
  recurse into both children
  min[node] = min(min[2node], min[2node+1])

PUSH-DOWN(node)
  if lazy[node] != 0:
      apply lazy[node] to both children (min and lazy); lazy[node] = 0

TRY-BOOK(a, b, k)
  if QUERY-MIN(1, 0, n-1, a, b) < k: refuse, change nothing
  else RANGE-ADD(1, 0, n-1, a, b, -k)
```

**Correctness.** Invariant: min[node] is the minimum of its range once all lazy amounts owed by its
ancestors are added. RANGE-ADD keeps it because adding d to every element of a range adds d to its minimum,
and parents recompute from children. PUSH-DOWN moves an owed amount one level down before anything reads
inside a node. A stay fits exactly when the minimum over its nights is at least k, so TRY-BOOK can never make
a night negative (no overbooking).

**Evidence.** Matches a plain array on 100,000 random queries and updates; refused bookings leave every
night unchanged; book then cancel restores every night.

### 3.3 Fraud screening with a Bloom filter and exact check

```
SIZE(n, p):  m = ceil(-n ln p / (ln 2)^2);  k = round((m / n) ln 2)
POSITIONS(x): h = FNV-1a-64(x); h1 = mix(h); h2 = mix(h xor C) or 1
              return (h1 + i * h2) mod m for i = 0 .. k-1          // double hashing
ADD(x):       set every bit in POSITIONS(x)
MIGHT-CONTAIN(x): return true only if every bit in POSITIONS(x) is set

SCREEN(value)
  key = normalise(value)                     // lower case email / digits-only phone, kind prefix
  if not MIGHT-CONTAIN(key): CLEAR           // definitely not listed
  else if key in exact blocklist: BLOCK      // confirmed
  else: CLEAR (false positive recorded)
```

**Correctness.** No false negatives: ADD sets every bit a value maps to, and bits are never cleared, so
an added value always finds all its bits set. False positives happen with probability about
(1 - e^(-kn/m))^k, and the exact check makes them harmless: only a confirmed match blocks. Duplicates are
caught by the exact list before ADD, so n stays accurate.

**Evidence.** 0 false negatives over 5,000 items; measured FPR within 25% of theory over 200,000 trials
(0.98% against 1.00%); a deliberately overloaded filter produced false positives and none blocked a guest.

## 4. Complexity

| Operation | Time | Space |
|---|---|---|
| Edmonds-Karp | O(V E^2) in general; O(f E) here, f = guests placed, since each path adds 1 | O(V + E) |
| First-fit (baseline) | O(G + E) | O(R) |
| Segment tree build | O(n) | O(n), arrays of 4n |
| Segment tree query / range add / book | O(log n), at most 4 nodes per level | O(log n) recursion |
| Night-by-night scan (baseline) | O(n) per query or update | O(n) |
| Bloom filter add / check | O(k) | O(m) bits |
| HashSet (baseline) | O(1) expected | O(n) objects |

## 5. Benchmarks

Method: fixed seeds; 2 warm-up runs; median of 5 timed runs; checksums printed so the JIT can't remove
the work; the tree and the scan (and traced and untraced paths) are checked to return the same answers.
Use `docs/benchmarks/results/*.csv` for charts. Suggested charts:

1. Segment tree against scan, time against nights on a log scale: the scan grows about 10x per step,
   the tree barely moves. They tie at 1,000 nights, where the scan's simplicity wins back the tree's
   overhead: that is the condition under which the tree helps.
2. Guests placed: max flow against first-fit at 100, 1,000 and 5,000 guests (+15 to +18%), with time on a
   second axis to show the cost of optimality.
3. Bloom filter against HashSet memory (32 to 47 times smaller), plus measured against expected FPR.

Discussion points:
- The trace costs 2 to 3 times the plain segment tree operation, so the services use trace-free paths
  where nothing is drawn (see `docs/benchmarks/optimisation-comparison.md`).
- Removing boxed integers from the flow network made it 4x faster at 1,000 guests but only 1.2x at 5,000,
  where the O(f E) work itself dominates.
- An in-memory HashSet is faster per lookup than the filter (cache misses on 7 random bits). The filter's
  value is memory and avoided remote lookups, not raw speed against an in-memory set.
- The first benchmark run was taken while the laptop was overloaded and was discarded; numbers vary up to
  about 1.5x between runs on the same machine.

## 6. Limitations and future work

See the README's Limitations section: preferences (min-cost flow), Hopcroft-Karp for scale, non-unique
minimum cut, no deletion from the Bloom filter (counting filter), in-memory storage, simple locking.

## 7. Individual contributions

Each member should write their own part, matching their Git commits.
