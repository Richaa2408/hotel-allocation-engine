# Optimisation pass: before and after

## What changed

| Area | Before | After |
|---|---|---|
| Bloom filter lookup (`mightContain`) | Built a full trace (two arrays and a record) and copied the string into a byte array | Hashes the string's characters directly, allocates nothing, stops at the first 0 bit |
| Segment tree | Every query and update recorded a `NodeVisit` for each node touched | `minOver` and `add` skip recording; `queryMin` and `rangeAdd` still record for the visualization |
| Flow network | Edges in `List<Integer>` (boxed integers); a new visited array and queue for every BFS | Edges in `int[]`; outgoing edges packed into one array (CSR) before solving; BFS arrays allocated once and reused |

All 40 correctness tests pass before and after. Every benchmark checks that the optimised and traced
versions return the same answers.

## Method

The first benchmark run happened while the machine was heavily loaded: the unchanged baselines
(array scan, HashSet) were about 20 times slower than in later runs, so that run can't be compared.

For a fair comparison, the old algorithm code (commit `20032f4`) and the new code were compiled with the
same benchmark and run alternately three times each (old, new, old, new, old, new). Each figure below is
the median of those three runs, and each run is itself the median of 5 timed repetitions after 2 warm-ups.
Even so, timings on this laptop vary by up to about 1.5x between runs (first-fit, whose code did not
change, ranged from 0.3x to 1x), so only larger differences are meaningful.

## Results (median of 3 alternating runs)

### Segment tree, 20,000 operations

| Nights | Old (always traced) | New, no trace | New, with trace | Array scan |
|---|---|---|---|---|
| 1,000 | 17.6 ms | **4.8 ms** | 15.5 ms | 4.8 ms |
| 10,000 | 16.8 ms | **6.1 ms** | 16.5 ms | 43 ms |
| 100,000 | 22.8 ms | **10.8 ms** | 22.3 ms | 914 to 974 ms |

Recording the trace roughly doubles to triples the cost of each operation. Without it, the tree now ties
the array scan at 1,000 nights instead of losing, and is about 85 to 90 times faster at 100,000 nights.

### Edmonds-Karp, rooms = 90% of guests

| Guests | Old | New | Speedup |
|---|---|---|---|
| 100 | 2.9 ms | 2.4 ms | 1.2x (within noise) |
| 1,000 | 118 ms | 29 ms | **4.0x** |
| 5,000 | 1,781 ms | 1,432 ms | 1.2x |

Both versions place exactly the same guests (79, 743, 3,730). At 5,000 guests the gain is small because
the time is dominated by the algorithm's own work, not by boxing: 3,730 augmenting paths, each found by a
BFS over roughly 45,000 residual edges, is about 1.7 x 10^8 edge checks. That is the O(f * E) bound
showing through. Faster asymptotics would need a different algorithm, such as Hopcroft-Karp
(O(E * sqrt(V)) for bipartite matching), which is noted as future work.

### Bloom filter lookups

No reliable change: results moved in both directions between runs (from 2x faster to 0.6x). With
100,000+ entries the filter (117 KB to 1.2 MB) no longer fits in the fastest CPU cache, and each lookup
reads 7 random bits, so memory access, not allocation, dominates. An in-memory HashSet stays faster per
lookup (about 15 to 40 ns against 100 to 300 ns) while using 29 to 41 times more memory.

## Conclusions for the report

1. The visualization trace has a real cost (2 to 3x on the segment tree), so the services use the
   trace-free paths wherever nothing is drawn.
2. Removing boxing helped Edmonds-Karp most at medium sizes. At large sizes the O(f * E) work of the
   algorithm itself dominates.
3. The Bloom filter's advantage is memory (about 30 to 40x smaller than a HashSet) and avoiding the exact
   lookup for about 99% of guests, not raw speed against an in-memory set.
