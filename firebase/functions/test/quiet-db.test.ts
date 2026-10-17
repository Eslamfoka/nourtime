import { initializeApp } from "firebase-admin/app";
import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { beforeEach, describe, expect, it } from "vitest";
import { claimQuietSpell, QUIET_AFTER_MS, QUIET_GIVE_UP_MS, quietDevices, releaseQuietSpell } from "../src/quiet.js";

// Run through `npm run test:functions` in firebase/: the Firestore emulator sets FIRESTORE_EMULATOR_HOST.
initializeApp({ projectId: "demo-nourtime" });
const db = getFirestore();
const NOW = 1_800_000_000_000;
const HOUR = 60 * 60_000;

const status = (atMs: number) => ({ phase: "COUNTING", updatedAt: Timestamp.fromMillis(atMs) });

beforeEach(async () => {
  await fetch(`http://${process.env.FIRESTORE_EMULATOR_HOST}/emulator/v1/projects/demo-nourtime/databases/(default)/documents`, { method: "DELETE" });
});

describe("quietDevices", () => {
  it("finds paired phones silent for twelve hours to a week, and nothing else", async () => {
    await db.doc("devices/quiet").set({ ownerUid: "p1", childUid: "c1", status: status(NOW - 13 * HOUR) });
    await db.doc("devices/fresh").set({ ownerUid: "p1", childUid: "c2", status: status(NOW - HOUR) });
    await db.doc("devices/gone").set({ ownerUid: "p1", childUid: "c3", status: status(NOW - QUIET_GIVE_UP_MS - HOUR) });
    await db.doc("devices/unpaired").set({ ownerUid: null, childUid: "c4", status: status(NOW - 13 * HOUR) });
    await db.doc("devices/new").set({ ownerUid: "p1", childUid: "c5" });
    expect(await quietDevices(db, NOW)).toEqual([{ id: "quiet", lastSeenMs: NOW - 13 * HOUR }]);
  });
});

describe("claimQuietSpell", () => {
  it("claims each silence once, and a later one again", async () => {
    const lastSeen = NOW - QUIET_AFTER_MS - HOUR;
    expect(await claimQuietSpell(db, "d1", lastSeen, NOW)).toBe(true);
    expect(await claimQuietSpell(db, "d1", lastSeen, NOW + HOUR)).toBe(false);
    const later = NOW + 2 * HOUR;
    expect(await claimQuietSpell(db, "d1", later, later + QUIET_AFTER_MS)).toBe(true);
  });

  it("a failed push gives the silence back, so the next run tries again", async () => {
    const lastSeen = NOW - QUIET_AFTER_MS;
    await claimQuietSpell(db, "d1", lastSeen, NOW);
    await releaseQuietSpell(db, "d1", lastSeen);
    expect(await claimQuietSpell(db, "d1", lastSeen, NOW + HOUR)).toBe(true);
  });

  it("keeps the push throttle times next to it", async () => {
    await db.doc("pushState/d1").set({ ASK: NOW });
    await claimQuietSpell(db, "d1", NOW - QUIET_AFTER_MS, NOW);
    expect((await db.doc("pushState/d1").get()).data()).toEqual({ ASK: NOW, quietFor: NOW - QUIET_AFTER_MS });
  });
});
