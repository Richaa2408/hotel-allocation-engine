# StayFlow — Complete Project Technical Handbook

**Project:** StayFlow / `hotel-allocation-engine` — hotel inventory, room assignment and guest
screening (Spring Boot 4 · Java 21 · PostgreSQL 17 · React 19 · TypeScript · Docker).
**State documented:** working tree of branch `main` on **12 November 2026**, *including uncommitted
changes* (last commit `b834c9a`). See [Volume 2 §5.7](docs/handbook/02-architecture-and-repository.md#57-working-tree-state-vs-last-commit).
**Verification performed:** every source, config, test and doc file was read; the backend test
suite (59 tests) and frontend tests (9) plus TypeScript type-check were run and passed; the Maven
dependency tree was resolved. The running application against PostgreSQL was **not** started.

> Secrets policy: demo passwords and the local database password exist in
> `src/main/resources/application.properties`, `compose.yaml` and the README. This handbook explains
> their purpose but does not reproduce their values.

---

## 1. How this handbook teaches

Every major concept is explained in three levels:

| Level | Audience | What you get |
|---|---|---|
| **Level 1 — 5th standard** | Curious 10–11-year-old | Everyday analogies (restaurant kitchen, school register, light switches, water pipes); *why* before *how* |
| **Level 2 — Undergraduate** | Knows programming, DS&A, databases | Correct terminology, how it works, code from this repo, interactions, trade-offs |
| **Level 3 — Graduate / professional** | Interview / design-review depth | Internals, invariants, complexity, concurrency, failure modes, alternatives |

Suggested reading paths:

- **"Explain the project in 10 minutes"**: Vol 1 §2 → Vol 2 §4.1–4.6 → Vol 7 summaries (§10.0, §10.8).
- **Viva / interview preparation**: Vol 7 → Vol 5 §8.10 → Vol 11 → Vol 12 §20 → Vol 13.
- **"I need to change the code tomorrow"**: Vol 2 §5 → Vol 10 §14 → Vol 5 → Vol 6.
- **Revision**: Vol 12 §18 traces and §19 glossary.

---

## 2. Volumes and detailed table of contents

| Vol | File | Requirement sections |
|---|---|---|
| 1 | [Project Understanding and Feature Reference](docs/handbook/01-project-and-features.md) | Part I: §2 Project overview, §3 Feature-by-feature (43 features) |
| 2 | [System Architecture and Repository Structure](docs/handbook/02-architecture-and-repository.md) | Part II: §4 Architecture (6 Mermaid diagrams), §5 Repository |
| 3 | [Technology Stack](docs/handbook/03-technology-stack.md) | Part III: §6 Every framework/library/tool |
| 4 | [Frontend Engineering](docs/handbook/04-frontend.md) | Part IV: §7 |
| 5 | [Backend Engineering and API Reference](docs/handbook/05-backend-and-api.md) | Part V: §8 incl. every endpoint |
| 6 | [Database and Data Engineering](docs/handbook/06-database.md) | Part VI: §9 incl. ER diagram |
| 7 | [Algorithms and Data Structures](docs/handbook/07-algorithms.md) | Part VII: §10 |
| 8 | [DevOps, Infrastructure and Deployment](docs/handbook/08-devops.md) | Part VIII: §11 |
| 9 | [Security and Performance](docs/handbook/09-security-and-performance.md) | Part IX: §12, §13 |
| 10 | [Development Workflow and Testing](docs/handbook/10-development-and-testing.md) | Parts X–XI: §14, §15 |
| 11 | [Engineering Decisions, Limitations, Improvements](docs/handbook/11-decisions-and-limitations.md) | Part XII: §16, §17 |
| 12 | [Master Learning and Revision Guide](docs/handbook/12-learning-guide.md) | Part XIII: §18 traces, §19 glossary, §20 question bank (50 Q&A) |
| 13 | [Self-Assessment Exercises](docs/handbook/13-exercises.md) · [Solutions](docs/handbook/13b-exercise-solutions.md) | Part XIII: §21 (32 exercises) |

### Detailed contents

**Volume 1 — Project understanding**
- 2.1 What the application does (three levels) · 2.2 Problem · 2.3 Users and roles · 2.4 Use cases ·
  2.5 What's technically interesting · 2.6 Feature inventory · 2.7 Functional/non-functional
  requirements · 2.8 What happens on interaction
- 3. F1–F43: sign-in, guards, overview, reservations (search, create, alternatives, idempotency,
  detail, edit, cancel, approve, check-in/out, waitlist, CSV), inventory (heatmap, check, alerts,
  maintenance), assignment (board, run, manual, floor plan/log, reassignment), risk (screening,
  false positive, watchlist, stats), Lab (tree, flow, Bloom, scenarios), analytics/audit, settings
  (users, Bloom sizing, room import, reset), help, seeding, audit trail, housekeeping API, Swagger,
  SPA forwarding, cross-cutting UI

**Volume 2 — Architecture & repository**
- 4.1 Big picture · 4.2 High-level diagram · 4.3 Component interaction · 4.4 Frontend↔backend
  sequence · 4.5 Data flow · 4.6 Deployment · 4.7 Pattern & layering · 4.8–4.10 Frontend/backend/DB
  summaries · 4.11 Integrations · 4.12 AuthN/AuthZ · 4.13 Persistence & in-memory state ·
  4.14 Error handling · 4.15 Background work
- 5.1 Top level · 5.2 Backend source (every package/file) · 5.3 Resources · 5.4 Tests · 5.5 Frontend ·
  5.6 Docs/samples/tooling · 5.7 Working tree vs last commit

**Volume 3 — Technology stack** — 6.1 Inventory (with versions, direct/transitive/dev) · 6.2
Languages · 6.3 Spring Boot/Framework/Tomcat · 6.4 PostgreSQL, JPA/Hibernate, HikariCP, Flyway, H2,
JDBC, docker-compose module · 6.5 Spring Security, BCrypt · 6.6 Validation, Jackson 2/3, springdoc,
logging · 6.7 Maven, JUnit · 6.8 React, Router, TanStack Query, Radix, Recharts, React Flow, lucide,
clsx/twMerge/CVA, fonts · 6.9 Vite, Tailwind v4, Vitest, npm · 6.10 Infra tools · 6.11 Unused/absent ·
6.12 How they fit

**Volume 4 — Frontend** — 7.1–7.22: entry point, lifecycle, hierarchy diagram, routing, state,
props, hooks, contexts, forms, API client, loading/error/empty states, conditional rendering, UI kit,
styling, responsive, accessibility, visualisations, source walkthroughs, interaction traces,
decisions, limitations

**Volume 5 — Backend & API** — 8.1–8.13: startup sequence, request lifecycle, controllers, services
(incl. reservation state machine), filters, validation layers, auth, DB patterns, transactions &
locking, error mapping, logging, async · 8.14 API reference (Auth, Reservations, Inventory &
maintenance, Assignment & rooms, Screening & watchlist, Overview/analytics/audit, Lab, Admin,
Docs & SPA) · 8.15 curl usage

**Volume 6 — Database** — 9.1 basics · 9.2 config · 9.3 ER diagram · 9.4 every table and column ·
9.5 keys/constraints/cascading · 9.6 indexes · 9.7 normalisation · 9.8 migrations · 9.9 transactions
& integrity · 9.10 key queries · 9.11 performance · 9.12 in-memory derived tables

**Volume 7 — Algorithms** — 10.0 inventory · 10.1 segment tree (11-point treatment + Hall's theorem) ·
10.2 Edmonds-Karp matching · 10.3 min-cut explanations · 10.4 first-fit · 10.5 Bloom filter ·
10.6 supporting algorithms · 10.7 benchmarks · 10.8 complexity table

**Volume 8 — DevOps** — 11.1 Docker concepts · 11.2 Dockerfile line by line · 11.3 .dockerignore ·
11.4 compose.yaml · 11.5 Spring + Compose · 11.6 commands used · 11.7 troubleshooting commands ·
11.8 build pipeline · 11.9 CI/CD (absent; proposal) · 11.10 deployment · 11.11 prod vs dev ·
11.12 logging/monitoring/scaling

**Volume 9 — Security & performance** — 12.1 threat model · 12.2 control-by-control table ·
12.3 weaknesses with scenarios · 12.4 hardening · 13.1–13.6 cost model, bottlenecks, caching,
concurrency, resources, scalability

**Volume 10 — Workflow & testing** — 14.1 prerequisites · 14.2 three ways to run · 14.3 config ·
14.4 DB/migrations · 14.5 commands · 14.6 common changes · 14.7 troubleshooting · 15.1 strategy ·
15.2 every test · 15.3 fixtures/mocking · 15.4 quality gates · 15.5 untested areas

**Volume 11 — Decisions & limitations** — D1–D16 decision records, "what I'd change", L1–L34
limitations/bugs with confidence, prioritised roadmap

**Volume 12 — Learning guide** — J1–J6 end-to-end traces, ~75-term glossary, 50-question bank
(beginner → project-specific)

**Volume 13 — Exercises** — conceptual, code reading, algorithm tracing, debugging, SQL, Docker,
architecture changes, system design; separate solutions file

---

## 3. One-page summary

StayFlow is a **layered Spring Boot monolith** that serves a **React SPA** and a JSON API over a
**PostgreSQL** database. Three hotel decisions are delegated to from-scratch algorithms in
`com.hotel.engine.algorithm`:

1. **Availability** — 8 in-memory **segment trees with lazy propagation** (4 room types × {all,
   accessible}) over 400 nights: O(log n) range-minimum checks and range-add bookings.
2. **Room assignment** — **maximum bipartite matching via Edmonds-Karp**, with whole-stay
   availability, tie-break ordering, a first-fit baseline, and **min-cut-based shortage
   explanations**.
3. **Screening** — a **Bloom filter** (FNV-1a + SplitMix64 double hashing) as a pre-screen, always
   confirmed by an exact indexed DB lookup.

Correctness under concurrency comes from one **fair global lock around each write transaction**
plus **rebuild-on-failure** of the in-memory structures — simple and provably safe in one JVM, but
the main obstacle to horizontal scaling. Quality evidence: 59 backend tests (randomised comparisons
with brute force, theory checks, flows, an 8-thread race, security rules) and 9 frontend tests.
Weak spots: no CI, local-only deployment, committed demo credentials, a few logic bugs (see
Volume 11 L4, L7, L31), N+1 queries, and stale session privileges.

---

## 4. Progress checklist

| Section | Status |
|---|---|
| 1 Teaching methodology (applied in every volume) | ✅ |
| 2 Project overview | ✅ Vol 1 |
| 3 Feature-by-feature (all 15 questions per feature, compressed into tables) | ✅ Vol 1 |
| 4 Architecture with ≥5 Mermaid diagrams | ✅ Vol 2 (high-level, component, sequence, data flow, deployment, auth) |
| 5 Repository structure | ✅ Vol 2 |
| 6 Technology inventory | ✅ Vol 3 |
| 7 Frontend | ✅ Vol 4 |
| 8 Backend + every endpoint | ✅ Vol 5 |
| 9 Database + ER diagram | ✅ Vol 6 |
| 10 Algorithms (11 points each) | ✅ Vol 7 |
| 11 DevOps incl. Docker line by line, commands, CI/CD, deployment | ✅ Vol 8 (CI/CD and hosting documented as absent) |
| 12 Security | ✅ Vol 9 |
| 13 Performance | ✅ Vol 9 |
| 14 Developer guide + troubleshooting | ✅ Vol 10 |
| 15 Testing | ✅ Vol 10 |
| 16 Decisions | ✅ Vol 11 |
| 17 Limitations & roadmap | ✅ Vol 11 |
| 18 Execution traces | ✅ Vol 12 |
| 19 Glossary | ✅ Vol 12 |
| 20 Question bank | ✅ Vol 12 |
| 21 Exercises + separate solutions | ✅ Vol 13 / 13b |

---

## 5. Completeness audit

### What was inspected

- **All backend source** (`src/main/java/**`, 72 files): algorithms, domain, model, repositories,
  security, services, lab, seed, sample, controllers.
- **Resources:** `application.properties` (main and test), `V1__schema.sql`.
- **All tests** (12 classes) — structure and assertions; ran the suite.
- **All frontend source** (`frontend/src/**`, 37 files) — core files fully; page files fully for
  reservations, overview, login, inventory heatmap, assignment, Lab replay/diagram logic; remaining
  page markup inspected through their state/API/handler code.
- **Build & infra:** `pom.xml`, Maven wrapper config, `package.json`, `package-lock.json` (versions
  of key transitive packages), `vite.config.ts`, `tsconfig.json`, `index.html`, `Dockerfile`,
  `compose.yaml`, `.dockerignore`, `.gitignore`, `.gitattributes`.
- **Docs & samples:** README, `docs/report-notes.md`, `docs/demo-script.md`,
  `docs/benchmarks/*`, `samples/*.json`.
- **Tooling folders:** `.claude/`, `.vscode/`, `.github/modernize/`, `skills-lock.json`.
- **Git state:** status, diff stats, commit history.

### What was documented

All 21 requirement sections, as per the checklist above, in 13 volumes + index.

### What was verified by execution

| Check | Result |
|---|---|
| `mvnw test -Dskip.npm -Dskip.installnodenpm` | 59 tests, 0 failures, 0 errors |
| `vitest run` | 9 tests passed |
| `tsc -b` | no type errors |
| `mvnw dependency:tree` | resolved versions listed in Vol 3 |

### What could not be verified

- The application was **not run against PostgreSQL**, so seed-dependent figures quoted from the
  project's docs (≈2,800/2,835 reservations, "22 of 23 placed", `visitor71750@example.org` after
  71,751 tries) are **reported, not re-observed**.
- **Benchmarks were not re-run**; numbers come from `docs/benchmarks/results/*.csv`.
- Runtime behaviour of suspected bugs **L4** (status code for reversed dates on waitlisted edits),
  **L31** (FK violation when shrinking a booking) and **L3** (stale session privileges) was inferred
  from code and framework semantics, not reproduced.
- Docker image build and `docker compose up` were not executed; their documentation is from reading
  the files.
- Exact Spring Security filter order was described from framework knowledge, not printed.
- Whether `spring-boot-docker-compose` is excluded from the fat jar was not checked (no jar built).
- Visual appearance/accessibility was assessed from code, not with a browser or screen reader.

### What remains incomplete / out of scope

- Line-by-line walkthrough of every JSX element in the larger page files (Settings, Risk, Analytics,
  Help) — their behaviour, state and API calls are documented, markup details are not.
- `frontend/src/lib/types.ts` is summarised (it mirrors the Java records documented in Vol 5) rather
  than reproduced field by field.

### Areas needing further investigation

1. Reproduce L31 and L4 with integration tests (Vol 10 §15.5 lists the tests to write).
2. Run the stack in Docker and confirm the seeded demo figures and the shortage explanation.
3. Re-run benchmarks on a quiet machine for the report.
4. Decide on a multi-instance strategy only if high availability becomes a requirement (Vol 11 D2/D4).
5. Commit the current working tree — at the time of writing, most of the application (frontend,
   persistence, security, Docker files) exists only as uncommitted/untracked files.
