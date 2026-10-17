import { describe, expect, it } from "vitest";
import { isNewQuietSpell, QUIET_AFTER_MS, QUIET_GIVE_UP_MS } from "../src/quiet.js";

const NOW = 1_800_000_000_000;

describe("isNewQuietSpell", () => {
  it("a phone that checked in recently isn't quiet", () => {
    expect(isNewQuietSpell(NOW - QUIET_AFTER_MS + 1, undefined, NOW)).toBe(false);
  });

  it("twelve hours without a check-in is quiet", () => {
    expect(isNewQuietSpell(NOW - QUIET_AFTER_MS, undefined, NOW)).toBe(true);
  });

  it("the same silence is pushed about once", () => {
    const lastSeen = NOW - QUIET_AFTER_MS - 1;
    expect(isNewQuietSpell(lastSeen, lastSeen, NOW)).toBe(false);
    expect(isNewQuietSpell(lastSeen, lastSeen, NOW + 24 * 60 * 60_000)).toBe(false);
  });

  it("a new silence after the phone came back is pushed about again", () => {
    const earlier = NOW - 3 * QUIET_AFTER_MS;
    const lastSeen = NOW - QUIET_AFTER_MS;
    expect(isNewQuietSpell(lastSeen, earlier, NOW)).toBe(true);
  });

  it("a phone gone for over a week is given up on", () => {
    expect(isNewQuietSpell(NOW - QUIET_GIVE_UP_MS - 1, undefined, NOW)).toBe(false);
  });

  it("a phone that never uploaded a status isn't quiet", () => {
    expect(isNewQuietSpell(undefined, undefined, NOW)).toBe(false);
  });
});
