// Shapes of the Java API's JSON responses (records in com.hotel.engine.*). Dates are "yyyy-mm-dd";
// instants are ISO timestamps.

export type RoomType = "STANDARD" | "DELUXE" | "EXECUTIVE" | "SUITE";
export const ROOM_TYPES: RoomType[] = ["STANDARD", "DELUXE", "EXECUTIVE", "SUITE"];

export type Role = "ADMIN" | "MANAGER" | "FRONT_DESK";
export type ReservationStatus = "CONFIRMED" | "PENDING_REVIEW" | "WAITLISTED" | "CHECKED_IN" | "CHECKED_OUT" | "CANCELLED";
export const STATUSES: ReservationStatus[] = ["CONFIRMED", "PENDING_REVIEW", "WAITLISTED", "CHECKED_IN", "CHECKED_OUT", "CANCELLED"];
export type Screening = "CLEAR" | "CLEAR_AFTER_CHECK" | "MATCH" | "APPROVED";
export type Housekeeping = "CLEAN" | "DIRTY" | "INSPECTED" | "OCCUPIED" | "OUT_OF_SERVICE";
export type AssignmentMethod = "MAX_FLOW" | "MANUAL" | "REASSIGNED";
export type WatchKind = "EMAIL" | "PHONE";

export interface Me {
  username: string;
  displayName: string;
  role: Role;
}

export interface Page<T> {
  content: T[];
  total: number;
  page: number;
  size: number;
  pages: number;
}

// ---------- algorithm traces ----------

export type VisitKind = "FULL" | "PARTIAL" | "OUTSIDE";
export interface NodeVisit {
  node: number;
  lo: number;
  hi: number;
  kind: VisitKind;
}
export interface SegmentTreeTrace {
  operation: string;
  from: number;
  to: number;
  value: number;
  visits: NodeVisit[];
}
export interface TreeNode {
  node: number;
  lo: number;
  hi: number;
  min: number;
  lazy: number;
}
export interface BloomTrace {
  operation: string;
  item: string;
  positions: number[];
  bitWasSet: boolean[];
  possiblyPresent: boolean;
}
export interface AugmentingStep {
  iteration: number;
  path: number[];
  edges: number[];
  bottleneck: number;
  totalFlow: number;
}
export interface FlowTrace {
  nodes: { id: number; label: string; kind: "SOURCE" | "GUEST" | "ROOM" | "SINK" }[];
  edges: { id: number; from: number; to: number; capacity: number; flow: number }[];
  steps: AugmentingStep[];
  minCutEdges: number[];
  sourceSide: number[];
}
export interface ShortageGroup {
  guestIds: string[];
  guestNames: string[];
  roomNumbers: string[];
  shortage: number;
  summary: string;
}
export interface AssignmentResult {
  guestCount: number;
  roomCount: number;
  assignedCount: number;
  firstFitAssignedCount: number;
  assignments: { guestId: string; guestName: string; roomNumber: string; roomType: RoomType; upgraded: boolean }[];
  unassigned: { guestId: string; guestName: string; suitableRooms: number; reason: string; suggestion: string }[];
  bottleneckRooms: string[];
  shortages: ShortageGroup[];
  trace: FlowTrace | null;
}

// ---------- inventory ----------

export interface AvailabilityCheck {
  roomType: RoomType;
  checkIn: string;
  checkOut: string;
  nights: number;
  rooms: number;
  needsAccessible: boolean;
  fits: boolean;
  fewestFree: number;
  busiestNight: string;
  fewestAccessible: number | null;
  limitedBy: "ROOMS" | "ACCESSIBLE_ROOMS" | null;
  freeByNight: { night: string; free: number }[];
  trace: SegmentTreeTrace;
  nodesVisited: number;
  fullNodes: number;
}
export type HeatLevel = "HEALTHY" | "LOW" | "SOLD_OUT";
export interface HeatCell {
  night: string;
  total: number;
  free: number;
  booked: number;
  blocked: number;
  level: HeatLevel;
}
export interface HeatRow {
  roomType: RoomType;
  total: number;
  accessibleTotal: number;
  cells: HeatCell[];
}
export interface InventoryAlert {
  roomType: RoomType;
  night: string;
  free: number;
  total: number;
  level: HeatLevel;
  message: string;
}
export interface MaintenanceBlock {
  id: number;
  roomNumber: string;
  roomType: RoomType;
  floor: number;
  accessible: boolean;
  startDate: string;
  endDate: string;
  nights: number;
  reason: string;
  createdBy: string;
  createdAt: string;
}
export interface BlockResult {
  block: MaintenanceBlock;
  moved: string[];
  unplaced: string[];
}

// ---------- reservations ----------

export interface Reservation {
  id: number;
  reference: string;
  guestName: string;
  email: string | null;
  phone: string | null;
  roomType: RoomType;
  checkIn: string;
  checkOut: string;
  nights: number;
  rooms: number;
  adults: number;
  needsAccessible: boolean;
  maxFloor: number | null;
  preferredFloor: number | null;
  acceptsUpgrade: boolean;
  status: ReservationStatus;
  screening: Screening;
  blockReason: string | null;
  notes: string | null;
  roomsAssigned: number;
  roomNumbers: string[];
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}
export interface FieldCheck {
  field: WatchKind;
  normalized: string;
  bloom: BloomTrace;
  exactChecked: boolean;
  listed: boolean;
  outcome: "CLEARED_BY_FILTER" | "FALSE_POSITIVE" | "CONFIRMED";
  reason: string | null;
}
export interface ScreeningResult {
  verdict: "CLEAR" | "MATCH";
  message: string;
  checks: FieldCheck[];
}
export interface CreateResult {
  outcome: "BOOKED" | "WAITLISTED" | "HELD" | "UNAVAILABLE";
  message: string;
  reservation: Reservation | null;
  availability: AvailabilityCheck | null;
  screening: ScreeningResult | null;
  alternatives: { roomType: RoomType; fewestFree: number }[];
}
export interface RoomHistoryItem {
  seq: number;
  roomNumber: string;
  floor: number;
  method: AssignmentMethod;
  reason: string | null;
  by: string;
  assignedAt: string;
  releasedAt: string | null;
  active: boolean;
}
export interface ReservationDetail {
  reservation: Reservation;
  rooms: { requestId: number; seq: number; roomNumber: string | null; floor: number | null }[];
  roomHistory: RoomHistoryItem[];
  history: { at: string; actor: string; action: string; detail: string }[];
}
export interface NewReservation {
  guestName: string;
  email: string;
  phone: string;
  roomType: RoomType;
  checkIn: string;
  checkOut: string;
  rooms: number;
  adults: number;
  needsAccessible: boolean;
  maxFloor: number | null;
  preferredFloor: number | null;
  acceptsUpgrade: boolean;
  notes: string;
  joinWaitlist: boolean;
}
export interface ImportResult {
  rows: number;
  booked: number;
  waitlisted: number;
  held: number;
  unavailable: number;
  failed: number;
  results: { line: number; guestName: string; outcome: string; reference: string | null; message: string }[];
}

// ---------- assignment ----------

export interface RequestCard {
  requestId: number;
  reference: string;
  seq: number;
  roomsInBooking: number;
  guestName: string;
  roomType: RoomType;
  checkIn: string;
  checkOut: string;
  nights: number;
  adults: number;
  hardRules: string;
  preference: string | null;
  needsAccessible: boolean;
  maxFloor: number | null;
  preferredFloor: number | null;
  acceptsUpgrade: boolean;
  status: ReservationStatus;
  roomNumber: string | null;
  method: AssignmentMethod | null;
}
export type RoomState = "FREE" | "ASSIGNED" | "OCCUPIED" | "OUT_OF_SERVICE";
export interface RoomTile {
  number: string;
  type: RoomType;
  floor: number;
  accessible: boolean;
  capacity: number;
  housekeeping: Housekeeping;
  state: RoomState;
  reference: string | null;
  guestName: string | null;
  until: string | null;
  blockReason: string | null;
}
export interface Board {
  date: string;
  requests: RequestCard[];
  rooms: RoomTile[];
  pending: number;
  assigned: number;
  freeRooms: number;
}
export interface RunResult {
  date: string;
  requests: number;
  placed: number;
  firstFitPlaced: number;
  candidateRooms: number;
  augmentingPaths: number;
  unplaced: { requestId: number; reference: string; guestName: string; hardRules: string; suitableRooms: number; reason: string; suggestion: string }[];
  shortages: { references: string[]; guestNames: string[]; roomNumbers: string[]; shortage: number; summary: string }[];
  board: Board;
  message: string;
}
export interface AssignmentLogItem {
  at: string;
  reference: string;
  guestName: string;
  roomNumber: string;
  method: AssignmentMethod;
  reason: string | null;
  by: string;
  active: boolean;
}

// ---------- risk ----------

export interface WatchStats {
  capacity: number;
  targetFalsePositiveRate: number;
  bitCount: number;
  hashCount: number;
  entries: number;
  bitsSet: number;
  expectedFalsePositiveRate: number;
  overCapacity: boolean;
  builtAt: string | null;
  screened: number;
  clearedByFilter: number;
  exactLookups: number;
  confirmed: number;
  falsePositives: number;
}
export interface WatchEntry {
  id: number;
  kind: WatchKind;
  value: string;
  reason: string;
  createdAt: string;
}

// ---------- overview and analytics ----------

export interface Kpis {
  date: string;
  totalRooms: number;
  roomTypes: number;
  occupiedTonight: number;
  sellableTonight: number;
  occupancy: number;
  availableTonight: number;
  outOfServiceTonight: number;
  arrivalsToday: number;
  awaitingAllocation: number;
  departuresToday: number;
  inHouse: number;
  pendingReviews: number;
  waitlisted: number;
}
export interface TrendPoint {
  night: string;
  occupancy: Record<string, number>;
  booked: Record<string, number>;
}
export interface Activity {
  at: string;
  actor: string;
  action: string;
  reference: string | null;
  detail: string;
}
export interface AlertItem {
  kind: string;
  severity: "CRITICAL" | "WARNING" | "INFO";
  message: string;
  link: string;
}
export interface Overview {
  kpis: Kpis;
  trend: TrendPoint[];
  activity: Activity[];
  alerts: AlertItem[];
}
export interface Analytics {
  occupancy: TrendPoint[];
  flow: { date: string; arrivals: number; departures: number }[];
  demand: { roomType: RoomType; roomNights: number; capacityNights: number; occupancy: number; bookings: number }[];
  assignment: { requests: number; assigned: number; successRate: number; byMethod: Record<string, number> };
  statuses: { status: ReservationStatus; count: number }[];
  screening: WatchStats;
}

// ---------- lab ----------

export interface Environment {
  java: string;
  processors: number;
  maxHeapMb: number;
}
export interface TreeDemo {
  nights: number;
  roomsOfType: number;
  freeBefore: number[];
  freeAfter: number[];
  nodesBefore: TreeNode[];
  nodesAfter: TreeNode[];
  query: SegmentTreeTrace;
  update: SegmentTreeTrace | null;
  booked: boolean;
  roomsWanted: number;
  scanCellsRead: number;
  log: string[];
}
export interface LabGuest {
  id: string;
  name: string;
  roomType: RoomType;
  needsAccessible: boolean;
  maxFloor: number | null;
  acceptsUpgrade: boolean;
  preferredFloor: number | null;
}
export interface LabRoom {
  number: string;
  type: RoomType;
  floor: number;
  accessible: boolean;
}
export interface FlowDemo {
  scenario: string;
  guests: LabGuest[];
  rooms: LabRoom[];
  result: AssignmentResult;
  firstFit: { guestId: string; roomNumber: string }[];
  log: string[];
}
export interface ItemTrace {
  value: string;
  positions: number[];
  bitWasSet: boolean[];
  possiblyPresent: boolean;
  actuallyPresent: boolean;
}
export interface BloomDemo {
  bitCount: number;
  hashCount: number;
  inserted: number;
  setBits: number[];
  insertedItems: ItemTrace[];
  probes: ItemTrace[];
  probesTested: number;
  falsePositives: number;
  measuredFpr: number;
  expectedFpr: number;
}
export interface SweepPoint {
  bitCount: number;
  hashCount: number;
  items: number;
  expectedFpr: number;
  measuredFpr: number;
}
export interface BloomSweep {
  bySize: SweepPoint[];
  byHashes: SweepPoint[];
  bestK: number;
}
export interface TreeBenchmark {
  rows: { nights: number; operations: number; treeMs: number; scanMs: number; speedup: number; treeUsPerOp: number; scanUsPerOp: number }[];
  environment: Environment;
  method: string;
}
export interface FlowBenchmark {
  rows: { guests: number; rooms: number; suitablePairs: number; maxFlowPlaced: number; firstFitPlaced: number; augmentingPaths: number; maxFlowMs: number; firstFitMs: number }[];
  environment: Environment;
  method: string;
}
export interface BloomBenchmark {
  rows: { entries: number; bloomBytes: number; hashSetBytes: number; memoryRatio: number; expectedFpr: number; measuredFpr: number; bloomLookupNs: number; hashSetLookupNs: number }[];
  environment: Environment;
  method: string;
}
export interface Scenario {
  id: string;
  title: string;
  question: string;
  metrics: { label: string; value: string }[];
  findings: string[];
}

// ---------- admin ----------

export interface UserView {
  id: number;
  username: string;
  displayName: string;
  role: Role;
  enabled: boolean;
}
export interface SeedSummary {
  seed: number;
  rooms: number;
  reservations: number;
  inHouse: number;
  arrivalsToday: number;
  waitlisted: number;
  held: number;
  watchlist: number;
  maintenance: number;
}
