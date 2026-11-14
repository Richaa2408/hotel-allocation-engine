import { describe, expect, it } from "vitest";
import { addDays, bytes, label, nightsBetween, plural, rate, toIso } from "./format";
import { qs } from "./api";

describe("dates", () => {
  it("adds days across a month and a year end", () => {
    expect(addDays("2026-01-30", 3)).toBe("2026-02-02");
    expect(addDays("2026-12-31", 1)).toBe("2027-01-01");
    expect(addDays("2026-03-01", -1)).toBe("2026-02-28");
  });

  it("counts nights between dates, including across a daylight-saving change", () => {
    expect(nightsBetween("2026-11-11", "2026-11-14")).toBe(3);
    expect(nightsBetween("2026-03-27", "2026-03-31")).toBe(4);
    expect(nightsBetween("2026-11-11", "2026-11-11")).toBe(0);
  });

  it("formats a local date as yyyy-mm-dd", () => {
    expect(toIso(new Date(2026, 0, 5))).toBe("2026-01-05");
  });
});

describe("numbers and words", () => {
  it("uses the singular only for exactly one", () => {
    expect(plural(1, "night")).toBe("1 night");
    expect(plural(0, "night")).toBe("0 nights");
    expect(plural(2, "guest")).toBe("2 guests");
    expect(plural(3, "entry", "entries")).toBe("3 entries");
  });

  it("shows small probabilities as a percentage and a 1-in-N figure", () => {
    expect(rate(0)).toBe("0%");
    expect(rate(0.01)).toBe("1.00% (about 1 in 100)");
    expect(rate(0.000012)).toMatch(/^0\.0012% \(about 1 in 83,333\)$/);
  });

  it("labels enum values for display", () => {
    expect(label("DELUXE")).toBe("Deluxe");
    expect(label("OUT_OF_SERVICE")).toBe("Out of service");
  });

  it("formats byte sizes", () => {
    expect(bytes(512)).toBe("512 B");
    expect(bytes(11_981)).toBe("11.7 KB");
    expect(bytes(5 * 1024 * 1024)).toBe("5.0 MB");
  });
});

describe("query strings", () => {
  it("drops empty values and encodes the rest", () => {
    expect(qs({ q: "a&b", status: "", type: null, page: 0, open: false, x: undefined })).toBe("?q=a%26b&page=0&open=false");
  });

  it("returns nothing when every value is empty", () => {
    expect(qs({ q: "", type: undefined })).toBe("");
  });
});
