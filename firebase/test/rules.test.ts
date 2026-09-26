import { readFileSync } from "node:fs";
import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import {
  addDoc,
  collection,
  deleteDoc,
  doc,
  Firestore,
  getDoc,
  getDocs,
  query,
  serverTimestamp,
  setDoc,
  Timestamp,
  updateDoc,
  where,
} from "firebase/firestore";

let env: RulesTestEnvironment;

const CHILD = "child-uid";
const PARENT = "parent-uid";
const OTHER_PARENT = "other-parent-uid";
const DEVICE = "device-1";
const CODE = "123456";

const child = (): Firestore =>
  env.authenticatedContext(CHILD, { firebase: { sign_in_provider: "anonymous" } }).firestore() as unknown as Firestore;
const anonymous = (uid = "someone-else"): Firestore =>
  env.authenticatedContext(uid, { firebase: { sign_in_provider: "anonymous" } }).firestore() as unknown as Firestore;
const parent = (uid = PARENT): Firestore =>
  env
    .authenticatedContext(uid, { email: `${uid}@example.com`, firebase: { sign_in_provider: "google.com" } })
    .firestore() as unknown as Firestore;
const unauthenticated = (): Firestore => env.unauthenticatedContext().firestore() as unknown as Firestore;

const minutesFromNow = (m: number) => Timestamp.fromMillis(Date.now() + m * 60_000);

const settings = {
  budgetMinutes: 60,
  lockPeriodHours: 6,
  lockType: "SELECTED_APPS",
  limitedApps: ["com.google.android.youtube"],
  allowedDuringLock: [],
  bedtimeEnabled: false,
  bedtimeStart: 1260,
  bedtimeEnd: 420,
  dailyResetMinute: null,
  rev: 1,
  by: "child",
};

/** Seeds documents with the rules turned off. */
async function seed(write: (db: Firestore) => Promise<unknown>) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await write(ctx.firestore() as unknown as Firestore);
  });
}

const unpairedDevice = { childUid: CHILD, ownerUid: null, ownerEmail: null, name: "Pixel", createdAt: Timestamp.now() };
const pairedDevice = { ...unpairedDevice, ownerUid: PARENT, ownerEmail: "parent-uid@example.com", settings };
const openPairing = { deviceId: DEVICE, childUid: CHILD, expiresAt: minutesFromNow(10), claimedBy: null, claimedEmail: null, claimedName: null };

beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-nourtime",
    firestore: { rules: readFileSync("firestore.rules", "utf8"), host: "127.0.0.1", port: 8080 },
  });
});

afterAll(async () => {
  await env.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
});

describe("devices", () => {
  it("can't be read without signing in", async () => {
    await seed((db) => setDoc(doc(db, "devices", DEVICE), pairedDevice));
    await assertFails(getDoc(doc(unauthenticated(), "devices", DEVICE)));
  });

  it("the child phone creates its own unpaired device", async () => {
    await assertSucceeds(setDoc(doc(child(), "devices", DEVICE), unpairedDevice));
  });

  it("the child phone can't create a device that is already paired", async () => {
    await assertFails(setDoc(doc(child(), "devices", DEVICE), pairedDevice));
  });

  it("another phone can't take over an existing device", async () => {
    await seed((db) => setDoc(doc(db, "devices", DEVICE), pairedDevice));
    await assertFails(setDoc(doc(anonymous(), "devices", DEVICE), { ...unpairedDevice, childUid: "someone-else" }));
    await assertFails(updateDoc(doc(anonymous(), "devices", DEVICE), { childUid: "someone-else" }));
  });

  it("the parent can't read a device before the child confirms", async () => {
    await seed((db) => setDoc(doc(db, "devices", DEVICE), unpairedDevice));
    await assertFails(getDoc(doc(parent(), "devices", DEVICE)));
  });

  it("after the child confirms, the parent reads and lists it", async () => {
    await seed((db) => setDoc(doc(db, "devices", DEVICE), unpairedDevice));
    await assertSucceeds(updateDoc(doc(child(), "devices", DEVICE), { ownerUid: PARENT, ownerEmail: "p@example.com" }));
    await assertSucceeds(getDoc(doc(parent(), "devices", DEVICE)));
    await assertSucceeds(getDocs(query(collection(parent(), "devices"), where("ownerUid", "==", PARENT))));
  });

  it("nobody can list every device", async () => {
    await assertFails(getDocs(collection(parent(), "devices")));
  });

  it("another parent can't read it", async () => {
    await seed((db) => setDoc(doc(db, "devices", DEVICE), pairedDevice));
    await assertFails(getDoc(doc(parent(OTHER_PARENT), "devices", DEVICE)));
  });

  it("the parent writes settings but not the status", async () => {
    await seed((db) => setDoc(doc(db, "devices", DEVICE), pairedDevice));
    await assertSucceeds(updateDoc(doc(parent(), "devices", DEVICE), { settings: { ...settings, rev: 2, by: "parent" } }));
    await assertFails(updateDoc(doc(parent(), "devices", DEVICE), { status: { phase: "AVAILABLE" } }));
    await assertFails(updateDoc(doc(parent(), "devices", DEVICE), { ownerUid: OTHER_PARENT }));
  });

  it("the parent can remove itself", async () => {
    await seed((db) => setDoc(doc(db, "devices", DEVICE), pairedDevice));
    await assertSucceeds(updateDoc(doc(parent(), "devices", DEVICE), { ownerUid: null, ownerEmail: null }));
  });

  it("the child writes its status and can disconnect", async () => {
    await seed((db) => setDoc(doc(db, "devices", DEVICE), pairedDevice));
    await assertSucceeds(updateDoc(doc(child(), "devices", DEVICE), { status: { phase: "LOCKED", updatedAt: serverTimestamp() } }));
    await assertSucceeds(updateDoc(doc(child(), "devices", DEVICE), { ownerUid: null, ownerEmail: null }));
  });

  it("devices are never deleted", async () => {
    await seed((db) => setDoc(doc(db, "devices", DEVICE), pairedDevice));
    await assertFails(deleteDoc(doc(child(), "devices", DEVICE)));
    await assertFails(deleteDoc(doc(parent(), "devices", DEVICE)));
  });
});

describe("pairings", () => {
  it("the child creates a pairing for its own device", async () => {
    await seed((db) => setDoc(doc(db, "devices", DEVICE), unpairedDevice));
    await assertSucceeds(setDoc(doc(child(), "pairings", CODE), openPairing));
  });

  it("nobody can create a pairing for someone else's device", async () => {
    await seed((db) => setDoc(doc(db, "devices", DEVICE), unpairedDevice));
    await assertFails(setDoc(doc(anonymous(), "pairings", CODE), { ...openPairing, childUid: "someone-else" }));
  });

  it("a pairing can't be valid for more than 11 minutes", async () => {
    await seed((db) => setDoc(doc(db, "devices", DEVICE), unpairedDevice));
    await assertFails(setDoc(doc(child(), "pairings", CODE), { ...openPairing, expiresAt: minutesFromNow(60) }));
  });

  it("a Google-signed-in parent claims an open code", async () => {
    await seed((db) => setDoc(doc(db, "pairings", CODE), openPairing));
    await assertSucceeds(getDoc(doc(parent(), "pairings", CODE)));
    await assertSucceeds(
      updateDoc(doc(parent(), "pairings", CODE), { claimedBy: PARENT, claimedEmail: "p@example.com", claimedName: "Mom" }),
    );
  });

  it("anonymous users can't claim", async () => {
    await seed((db) => setDoc(doc(db, "pairings", CODE), openPairing));
    await assertFails(updateDoc(doc(anonymous(), "pairings", CODE), { claimedBy: "someone-else" }));
  });

  it("a parent can't claim in someone else's name", async () => {
    await seed((db) => setDoc(doc(db, "pairings", CODE), openPairing));
    await assertFails(updateDoc(doc(parent(), "pairings", CODE), { claimedBy: OTHER_PARENT }));
  });

  it("an expired code can't be claimed", async () => {
    await seed((db) => setDoc(doc(db, "pairings", CODE), { ...openPairing, expiresAt: minutesFromNow(-1) }));
    await assertFails(updateDoc(doc(parent(), "pairings", CODE), { claimedBy: PARENT }));
  });

  it("a claimed code can't be claimed again", async () => {
    await seed((db) => setDoc(doc(db, "pairings", CODE), { ...openPairing, claimedBy: OTHER_PARENT }));
    await assertFails(updateDoc(doc(parent(), "pairings", CODE), { claimedBy: PARENT }));
  });

  it("a claim can't change the device", async () => {
    await seed((db) => setDoc(doc(db, "pairings", CODE), openPairing));
    await assertFails(updateDoc(doc(parent(), "pairings", CODE), { claimedBy: PARENT, deviceId: "device-2" }));
  });

  it("pairings can't be listed", async () => {
    await seed((db) => setDoc(doc(db, "pairings", CODE), openPairing));
    await assertFails(getDocs(collection(parent(), "pairings")));
  });

  it("only the child deletes its pairing", async () => {
    await seed((db) => setDoc(doc(db, "pairings", CODE), openPairing));
    await assertFails(deleteDoc(doc(parent(), "pairings", CODE)));
    await assertSucceeds(deleteDoc(doc(child(), "pairings", CODE)));
  });
});

describe("usage and app list", () => {
  it("the child writes them and the parent reads them", async () => {
    await seed((db) => setDoc(doc(db, "devices", DEVICE), pairedDevice));
    await assertSucceeds(setDoc(doc(child(), "devices", DEVICE, "usage", "2026-09-27"), { ms: { a: 1 } }));
    await assertSucceeds(setDoc(doc(child(), "devices", DEVICE, "meta", "apps"), { apps: [] }));
    await assertSucceeds(getDoc(doc(parent(), "devices", DEVICE, "usage", "2026-09-27")));
    await assertSucceeds(getDoc(doc(parent(), "devices", DEVICE, "meta", "apps")));
  });

  it("the parent can't write them and strangers can't read them", async () => {
    await seed((db) => setDoc(doc(db, "devices", DEVICE), pairedDevice));
    await assertFails(setDoc(doc(parent(), "devices", DEVICE, "usage", "2026-09-27"), { ms: {} }));
    await assertFails(getDoc(doc(parent(OTHER_PARENT), "devices", DEVICE, "usage", "2026-09-27")));
  });
});

describe("commands", () => {
  const commands = (db: Firestore) => collection(db, "devices", DEVICE, "commands");

  beforeEach(async () => {
    await seed((db) => setDoc(doc(db, "devices", DEVICE), pairedDevice));
  });

  it("the owner sends a bonus", async () => {
    await assertSucceeds(addDoc(commands(parent()), { type: "BONUS", minutes: 30, createdAt: serverTimestamp(), by: PARENT, appliedAt: null }));
  });

  it("bonuses outside 1 to 240 minutes are rejected", async () => {
    await assertFails(addDoc(commands(parent()), { type: "BONUS", minutes: 0, createdAt: serverTimestamp(), by: PARENT, appliedAt: null }));
    await assertFails(addDoc(commands(parent()), { type: "BONUS", minutes: 500, createdAt: serverTimestamp(), by: PARENT, appliedAt: null }));
  });

  it("lock now and end lock carry no minutes", async () => {
    await assertSucceeds(addDoc(commands(parent()), { type: "LOCK_NOW", createdAt: serverTimestamp(), by: PARENT, appliedAt: null }));
    await assertFails(addDoc(commands(parent()), { type: "END_LOCK", minutes: 5, createdAt: serverTimestamp(), by: PARENT, appliedAt: null }));
  });

  it("unknown types, other senders and pre-applied commands are rejected", async () => {
    await assertFails(addDoc(commands(parent()), { type: "WIPE", createdAt: serverTimestamp(), by: PARENT, appliedAt: null }));
    await assertFails(addDoc(commands(parent(OTHER_PARENT)), { type: "LOCK_NOW", createdAt: serverTimestamp(), by: OTHER_PARENT, appliedAt: null }));
    await assertFails(addDoc(commands(child()), { type: "LOCK_NOW", createdAt: serverTimestamp(), by: CHILD, appliedAt: null }));
    await assertFails(
      addDoc(commands(parent()), { type: "LOCK_NOW", createdAt: serverTimestamp(), by: PARENT, appliedAt: serverTimestamp() }),
    );
  });

  it("commands must be created unapplied, with appliedAt null", async () => {
    await assertFails(addDoc(commands(parent()), { type: "LOCK_NOW", createdAt: serverTimestamp(), by: PARENT }));
  });

  it("the child reads the unapplied commands", async () => {
    await assertSucceeds(getDocs(query(commands(child()), where("appliedAt", "==", null))));
  });

  it("the child marks a command applied and nothing else", async () => {
    await seed((db) => setDoc(doc(db, "devices", DEVICE, "commands", "c1"), { type: "LOCK_NOW", createdAt: Timestamp.now(), by: PARENT, appliedAt: null }));
    await assertFails(updateDoc(doc(child(), "devices", DEVICE, "commands", "c1"), { type: "END_LOCK" }));
    await assertFails(updateDoc(doc(parent(), "devices", DEVICE, "commands", "c1"), { appliedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(doc(child(), "devices", DEVICE, "commands", "c1"), { appliedAt: serverTimestamp() }));
    await assertSucceeds(getDoc(doc(parent(), "devices", DEVICE, "commands", "c1")));
    await assertFails(updateDoc(doc(child(), "devices", DEVICE, "commands", "c1"), { appliedAt: serverTimestamp() }));
  });
});
