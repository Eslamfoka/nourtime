import { readFileSync } from "node:fs";
import { afterAll, beforeAll, beforeEach, describe, expect, it } from "vitest";
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
  writeBatch,
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
    .authenticatedContext(uid, { email: `${uid}@example.com`, email_verified: true, firebase: { sign_in_provider: "google.com" } })
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
const openPairing = { deviceId: DEVICE, childUid: CHILD, createdAt: Timestamp.now(), claimedBy: null, claimedEmail: null, claimedName: null };
/** What the child's phone writes: the server sets createdAt, and codes expire 10 minutes later. */
const newPairing = { ...openPairing, createdAt: serverTimestamp(), expireAt: minutesFromNow(24 * 60) };

/** Today (UTC) as a usage document id. */
const isoDay = (offsetDays = 0) => new Date(Date.now() + offsetDays * 24 * 60 * 60_000).toISOString().slice(0, 10);
const TODAY = isoDay();

/** A device with the account record naming it, in one write, as the child's phone makes it. */
function createDevice(db: Firestore, id = DEVICE, data: object = unpairedDevice, uid = CHILD) {
  const batch = writeBatch(db);
  batch.set(doc(db, "childAccounts", uid), { deviceId: id }, { merge: true });
  batch.set(doc(db, "devices", id), data);
  return batch.commit();
}

/** A pairing code with the account record naming it, in one write, as the child's phone makes it. */
function createPairing(db: Firestore, code = CODE, data: object = newPairing, uid = CHILD, deviceId = DEVICE) {
  const batch = writeBatch(db);
  batch.set(doc(db, "childAccounts", uid), { deviceId, openCode: code });
  batch.set(doc(db, "pairings", code), data);
  return batch.commit();
}

beforeAll(async () => {
  env = await initializeTestEnvironment({
    // Separate from the app's demo-nourtime data: these tests clear the database.
    projectId: "demo-nourtime-test",
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

  it("a signed-in phone can see that a device doesn't exist yet", async () => {
    await assertSucceeds(getDoc(doc(child(), "devices", "not-created-yet")));
  });

  it("the child phone creates its own unpaired device", async () => {
    await assertSucceeds(createDevice(child()));
  });

  it("the child phone can't create a device that is already paired", async () => {
    await assertFails(createDevice(child(), DEVICE, pairedDevice));
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

  it("the child can only make the parent who claimed its code the owner", async () => {
    await seed((db) => setDoc(doc(db, "devices", DEVICE), unpairedDevice));
    await assertFails(updateDoc(doc(child(), "devices", DEVICE), { ownerUid: PARENT, ownerEmail: "p@example.com" }));
    await seed((db) => setDoc(doc(db, "pairings", CODE), { ...openPairing, claimedBy: OTHER_PARENT }));
    await assertFails(updateDoc(doc(child(), "devices", DEVICE), { ownerUid: PARENT, pairingCode: CODE }));
  });

  it("a stranger's device can't be pushed into a parent's list", async () => {
    const strangerDevice = { ...unpairedDevice, childUid: "someone-else" };
    await seed((db) => setDoc(doc(db, "devices", "fake"), strangerDevice));
    await assertFails(updateDoc(doc(anonymous(), "devices", "fake"), { ownerUid: PARENT }));
  });

  it("after the child confirms, the parent reads and lists it", async () => {
    await seed((db) => setDoc(doc(db, "devices", DEVICE), unpairedDevice));
    await seed((db) => setDoc(doc(db, "pairings", CODE), { ...openPairing, claimedBy: PARENT }));
    await assertSucceeds(updateDoc(doc(child(), "devices", DEVICE), { ownerUid: PARENT, ownerEmail: "p@example.com", pairingCode: CODE }));
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
    await assertSucceeds(updateDoc(doc(parent(), "devices", DEVICE), { ownerUid: null, ownerEmail: null, ownerName: null }));
  });

  it("the child writes its status and can disconnect", async () => {
    await seed((db) => setDoc(doc(db, "devices", DEVICE), pairedDevice));
    await assertSucceeds(updateDoc(doc(child(), "devices", DEVICE), { status: { phase: "LOCKED", updatedAt: serverTimestamp() } }));
    await assertSucceeds(updateDoc(doc(child(), "devices", DEVICE), { ownerUid: null, ownerEmail: null }));
  });

  it("the child writes its Learning Hub minutes; the parent only reads them", async () => {
    await seed((db) => setDoc(doc(db, "devices", DEVICE), pairedDevice));
    await assertSucceeds(updateDoc(doc(child(), "devices", DEVICE), { learning: { bankMinutes: 10, earnedDay: "2026-10-05", earnedMinutes: 15 } }));
    await assertFails(updateDoc(doc(parent(), "devices", DEVICE), { learning: { bankMinutes: 999 } }));
    await assertSucceeds(getDoc(doc(parent(), "devices", DEVICE)));
  });

  it("only the child phone deletes its device (account deletion)", async () => {
    await seed((db) => setDoc(doc(db, "devices", DEVICE), pairedDevice));
    await assertFails(deleteDoc(doc(parent(), "devices", DEVICE)));
    await assertFails(deleteDoc(doc(anonymous(), "devices", DEVICE)));
    await assertSucceeds(deleteDoc(doc(child(), "devices", DEVICE)));
  });
});

describe("pairings", () => {
  it("the child creates a pairing for its own device", async () => {
    await seed((db) => setDoc(doc(db, "devices", DEVICE), unpairedDevice));
    await assertSucceeds(createPairing(child(), CODE, newPairing));
  });

  it("nobody can create a pairing for someone else's device", async () => {
    await seed((db) => setDoc(doc(db, "devices", DEVICE), unpairedDevice));
    await assertFails(createPairing(anonymous(), CODE, { ...newPairing, childUid: "someone-else" }, "someone-else"));
  });

  it("pairing codes are exactly 6 digits", async () => {
    await seed((db) => setDoc(doc(db, "devices", DEVICE), unpairedDevice));
    await assertFails(createPairing(child(), "12345", newPairing));
    await assertFails(createPairing(child(), "abcdef", newPairing));
    await assertFails(createPairing(child(), "1234567", newPairing));
  });

  it("a pairing carries a clean-up time between 10 minutes and 2 days away (Firestore TTL)", async () => {
    await seed((db) => setDoc(doc(db, "devices", DEVICE), unpairedDevice));
    const { expireAt: _, ...withoutExpiry } = newPairing;
    await assertFails(createPairing(child(), CODE, withoutExpiry));
    await assertFails(createPairing(child(), CODE, { ...newPairing, expireAt: minutesFromNow(5) }));
    await assertFails(createPairing(child(), CODE, { ...newPairing, expireAt: minutesFromNow(3 * 24 * 60) }));
  });

  it("a pairing's start time is the server's, not the phone's clock", async () => {
    await seed((db) => setDoc(doc(db, "devices", DEVICE), unpairedDevice));
    await assertFails(createPairing(child(), CODE, { ...openPairing, createdAt: minutesFromNow(60) }));
  });

  it("an existing code can't be overwritten", async () => {
    await seed((db) => setDoc(doc(db, "devices", DEVICE), unpairedDevice));
    await seed((db) => setDoc(doc(db, "pairings", CODE), { ...openPairing, childUid: "someone-else" }));
    await assertFails(createPairing(child(), CODE, newPairing));
  });

  it("a Google-signed-in parent claims an open code blind, then reads it", async () => {
    await seed((db) => setDoc(doc(db, "pairings", CODE), openPairing));
    await assertFails(getDoc(doc(parent(), "pairings", CODE)));
    await assertSucceeds(
      updateDoc(doc(parent(), "pairings", CODE), { claimedBy: PARENT, claimedEmail: "parent-uid@example.com", claimedName: "Mom" }),
    );
    await assertSucceeds(getDoc(doc(parent(), "pairings", CODE)));
  });

  it("once the child deletes a claimed code, the parent can't read it either (the app takes that as gone)", async () => {
    await seed((db) => setDoc(doc(db, "pairings", CODE), { ...openPairing, claimedBy: PARENT }));
    await assertSucceeds(getDoc(doc(parent(), "pairings", CODE)));
    await seed((db) => deleteDoc(doc(db, "pairings", CODE)));
    await assertFails(getDoc(doc(parent(), "pairings", CODE)));
  });

  it("parents can't look codes up: not open ones, not someone else's claim, not even whether one exists", async () => {
    await seed((db) => setDoc(doc(db, "pairings", CODE), openPairing));
    await assertFails(getDoc(doc(parent(), "pairings", CODE)));
    await assertFails(getDoc(doc(parent(), "pairings", "999999")));
    await seed((db) => setDoc(doc(db, "pairings", CODE), { ...openPairing, claimedBy: OTHER_PARENT }));
    await assertFails(getDoc(doc(parent(), "pairings", CODE)));
  });

  it("a claim can't show someone else's email", async () => {
    await seed((db) => setDoc(doc(db, "pairings", CODE), openPairing));
    await assertFails(updateDoc(doc(parent(), "pairings", CODE), { claimedBy: PARENT, claimedEmail: "mom@gmail.com", claimedName: "Mom" }));
  });

  it("a claim needs a verified email", async () => {
    await seed((db) => setDoc(doc(db, "pairings", CODE), openPairing));
    const unverified = env
      .authenticatedContext(PARENT, { email: "parent-uid@example.com", email_verified: false, firebase: { sign_in_provider: "google.com" } })
      .firestore() as unknown as Firestore;
    await assertFails(updateDoc(doc(unverified, "pairings", CODE), { claimedBy: PARENT, claimedEmail: "parent-uid@example.com" }));
  });

  it("anonymous phones can't read other phones' codes", async () => {
    await seed((db) => setDoc(doc(db, "pairings", CODE), openPairing));
    await assertFails(getDoc(doc(anonymous(), "pairings", CODE)));
    await assertSucceeds(getDoc(doc(child(), "pairings", CODE)));
    await assertSucceeds(getDoc(doc(anonymous(), "pairings", "999999")));
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
    await seed((db) => setDoc(doc(db, "pairings", CODE), { ...openPairing, createdAt: minutesFromNow(-11) }));
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
    await assertSucceeds(setDoc(doc(child(), "devices", DEVICE, "usage", TODAY), { ms: { a: 1 } }));
    await assertSucceeds(setDoc(doc(child(), "devices", DEVICE, "meta", "apps"), { apps: [] }));
    await assertSucceeds(getDoc(doc(parent(), "devices", DEVICE, "usage", TODAY)));
    await assertSucceeds(getDoc(doc(parent(), "devices", DEVICE, "meta", "apps")));
  });

  it("the child deletes them", async () => {
    await seed(async (db) => {
      await setDoc(doc(db, "devices", DEVICE), pairedDevice);
      await setDoc(doc(db, "devices", DEVICE, "usage", "2026-09-27"), { ms: { a: 1 } });
      await setDoc(doc(db, "devices", DEVICE, "meta", "apps"), { apps: [] });
    });
    await assertFails(deleteDoc(doc(parent(), "devices", DEVICE, "usage", "2026-09-27")));
    await assertSucceeds(deleteDoc(doc(child(), "devices", DEVICE, "usage", "2026-09-27")));
    await assertSucceeds(deleteDoc(doc(child(), "devices", DEVICE, "meta", "apps")));
  });

  it("the parent can't write them and strangers can't read them", async () => {
    await seed((db) => setDoc(doc(db, "devices", DEVICE), pairedDevice));
    await assertFails(setDoc(doc(parent(), "devices", DEVICE, "usage", TODAY), { ms: {} }));
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
    await assertSucceeds(addDoc(commands(parent()), { type: "APPROVE_PIN_RESET", createdAt: serverTimestamp(), by: PARENT, appliedAt: null }));
    await assertFails(addDoc(commands(parent()), { type: "APPROVE_PIN_RESET", pin: "1234", createdAt: serverTimestamp(), by: PARENT, appliedAt: null }));
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

  it("the child deletes any command; the owner deletes only its own", async () => {
    await seed(async (db) => {
      await setDoc(doc(db, "devices", DEVICE, "commands", "mine"), { type: "LOCK_NOW", createdAt: Timestamp.now(), by: PARENT, appliedAt: null });
      await setDoc(doc(db, "devices", DEVICE, "commands", "old"), { type: "LOCK_NOW", createdAt: Timestamp.now(), by: OTHER_PARENT, appliedAt: null });
    });
    await assertFails(deleteDoc(doc(parent(), "devices", DEVICE, "commands", "old")));
    await assertFails(deleteDoc(doc(parent(OTHER_PARENT), "devices", DEVICE, "commands", "old")));
    await assertSucceeds(deleteDoc(doc(parent(), "devices", DEVICE, "commands", "mine")));
    await assertSucceeds(deleteDoc(doc(child(), "devices", DEVICE, "commands", "old")));
  });

  it("the owner finds its own commands to delete them", async () => {
    await assertSucceeds(getDocs(query(commands(parent()), where("by", "==", PARENT))));
  });
});

describe("requests for more time", () => {
  const requests = (db: Firestore) => collection(db, "devices", DEVICE, "requests");

  beforeEach(async () => {
    await seed((db) => setDoc(doc(db, "devices", DEVICE), pairedDevice));
  });

  it("the child asks; others can't ask for it", async () => {
    await assertSucceeds(addDoc(requests(child()), { status: "pending", createdAt: serverTimestamp() }));
    await assertFails(addDoc(requests(parent()), { status: "pending", createdAt: serverTimestamp() }));
    await assertFails(addDoc(requests(anonymous()), { status: "pending", createdAt: serverTimestamp() }));
  });

  it("a request starts pending, at server time, with nothing else", async () => {
    await assertFails(addDoc(requests(child()), { status: "approved", createdAt: serverTimestamp() }));
    await assertFails(addDoc(requests(child()), { status: "pending", createdAt: Timestamp.now() }));
    await assertFails(addDoc(requests(child()), { status: "pending", createdAt: serverTimestamp(), minutes: 60 }));
  });

  it("the owner approves with minutes, or declines, once", async () => {
    await seed((db) => setDoc(doc(db, "devices", DEVICE, "requests", "r1"), { status: "pending", createdAt: Timestamp.now() }));
    await assertFails(updateDoc(doc(child(), "devices", DEVICE, "requests", "r1"), { status: "approved", minutes: 60, answeredAt: serverTimestamp(), by: CHILD }));
    await assertFails(updateDoc(doc(parent(), "devices", DEVICE, "requests", "r1"), { status: "approved", minutes: 999, answeredAt: serverTimestamp(), by: PARENT }));
    await assertFails(updateDoc(doc(parent(OTHER_PARENT), "devices", DEVICE, "requests", "r1"), { status: "declined", answeredAt: serverTimestamp(), by: OTHER_PARENT }));
    await assertSucceeds(updateDoc(doc(parent(), "devices", DEVICE, "requests", "r1"), { status: "approved", minutes: 15, answeredAt: serverTimestamp(), by: PARENT }));
    await assertFails(updateDoc(doc(parent(), "devices", DEVICE, "requests", "r1"), { status: "declined", answeredAt: serverTimestamp(), by: PARENT }));
  });

  it("a decline carries no minutes", async () => {
    await seed((db) => setDoc(doc(db, "devices", DEVICE, "requests", "r2"), { status: "pending", createdAt: Timestamp.now() }));
    await assertFails(updateDoc(doc(parent(), "devices", DEVICE, "requests", "r2"), { status: "declined", minutes: 15, answeredAt: serverTimestamp(), by: PARENT }));
    await assertSucceeds(updateDoc(doc(parent(), "devices", DEVICE, "requests", "r2"), { status: "declined", answeredAt: serverTimestamp(), by: PARENT }));
  });

  it("both read them; only the child deletes them", async () => {
    await seed((db) => setDoc(doc(db, "devices", DEVICE, "requests", "r3"), { status: "pending", createdAt: Timestamp.now() }));
    await assertSucceeds(getDoc(doc(parent(), "devices", DEVICE, "requests", "r3")));
    await assertFails(getDoc(doc(parent(OTHER_PARENT), "devices", DEVICE, "requests", "r3")));
    await assertFails(deleteDoc(doc(parent(), "devices", DEVICE, "requests", "r3")));
    await assertSucceeds(deleteDoc(doc(child(), "devices", DEVICE, "requests", "r3")));
  });
});

describe("parent push tokens", () => {
  const tokenDoc = (db: Firestore, uid: string, token: string) => doc(db, "parents", uid, "tokens", token);

  it("a parent registers and removes their own token", async () => {
    await assertSucceeds(setDoc(tokenDoc(parent(), PARENT, "tok1"), { createdAt: serverTimestamp(), platform: "android" }));
    await assertSucceeds(getDoc(tokenDoc(parent(), PARENT, "tok1")));
    await assertSucceeds(deleteDoc(tokenDoc(parent(), PARENT, "tok1")));
  });

  it("nobody else can read or write them, and only the two fields are allowed", async () => {
    await assertFails(setDoc(tokenDoc(parent(OTHER_PARENT), PARENT, "tok1"), { createdAt: serverTimestamp(), platform: "android" }));
    await assertFails(getDoc(tokenDoc(child(), PARENT, "tok1")));
    await assertFails(setDoc(tokenDoc(parent(), PARENT, "tok2"), { createdAt: serverTimestamp(), platform: "android", extra: 1 }));
  });

  it("the push state is server-only", async () => {
    await assertFails(getDoc(doc(parent(), "pushState", DEVICE)));
    await assertFails(setDoc(doc(child(), "pushState", DEVICE), { ASK: 1 }));
  });
});

// 2026-10-28 audit: any signed-in account, also an anonymous one, could make devices and pairing codes
// without limit (the codes are only a million) and write documents of any size or day.
describe("limits per account", () => {
  it("a device needs the account record naming it, in the same write", async () => {
    await assertFails(setDoc(doc(child(), "devices", DEVICE), unpairedDevice));
    await seed((db) => setDoc(doc(db, "childAccounts", CHILD), { deviceId: "device-2" }));
    await assertFails(setDoc(doc(child(), "devices", DEVICE), unpairedDevice));
  });

  it("one device per account: a second only once the first is gone", async () => {
    await assertSucceeds(createDevice(child()));
    await assertFails(createDevice(child(), "device-2"));
    await assertSucceeds(deleteDoc(doc(child(), "devices", DEVICE)));
    await assertSucceeds(createDevice(child(), "device-2"));
  });

  it("the same device can be made again by its account (after a failed disconnect)", async () => {
    await seed((db) => setDoc(doc(db, "childAccounts", CHILD), { deviceId: DEVICE }));
    await assertSucceeds(createDevice(child()));
  });

  it("one open code per account: a new one only with the old one deleted", async () => {
    await seed((db) => setDoc(doc(db, "devices", DEVICE), unpairedDevice));
    await assertSucceeds(createPairing(child(), CODE));
    await assertFails(createPairing(child(), "654321"));
    // Without naming the new code, the code isn't accepted either.
    await assertFails(setDoc(doc(child(), "pairings", "654321"), newPairing));
    const db = child();
    const batch = writeBatch(db);
    batch.delete(doc(db, "pairings", CODE));
    batch.set(doc(db, "childAccounts", CHILD), { deviceId: DEVICE, openCode: "654321" });
    batch.set(doc(db, "pairings", "654321"), newPairing);
    await assertSucceeds(batch.commit());
  });

  it("an old code already deleted, or now another phone's, doesn't block a new one", async () => {
    await seed(async (db) => {
      await setDoc(doc(db, "devices", DEVICE), unpairedDevice);
      await setDoc(doc(db, "childAccounts", CHILD), { deviceId: DEVICE, openCode: CODE });
    });
    await assertSucceeds(createPairing(child(), "654321"));
    await seed(async (db) => {
      await setDoc(doc(db, "childAccounts", CHILD), { deviceId: DEVICE, openCode: CODE });
      await setDoc(doc(db, "pairings", CODE), { ...openPairing, childUid: "someone-else" });
    });
    await assertSucceeds(createPairing(child(), "111111"));
  });

  it("a record naming an old device of its own: the phone clears that device and its code, then moves on", async () => {
    // Two device documents left by an older build, or a restored device id: the record names device-1.
    await seed(async (db) => {
      await setDoc(doc(db, "devices", DEVICE), pairedDevice);
      await setDoc(doc(db, "devices", DEVICE, "usage", TODAY), { ms: { a: 1 }, updatedAt: Timestamp.now() });
      await setDoc(doc(db, "pairings", CODE), openPairing);
      await setDoc(doc(db, "childAccounts", CHILD), { deviceId: DEVICE, openCode: CODE });
    });
    // Refused while the old device is there, as the phone first sees it.
    await assertFails(createDevice(child(), "device-2"));
    // What the phone may clear: the old code, the old device's data, then the old device.
    await assertSucceeds(deleteDoc(doc(child(), "pairings", CODE)));
    await assertSucceeds(deleteDoc(doc(child(), "devices", DEVICE, "usage", TODAY)));
    await assertSucceeds(deleteDoc(doc(child(), "devices", DEVICE)));
    await assertSucceeds(createDevice(child(), "device-2"));
    await assertSucceeds(createPairing(child(), "654321", { ...newPairing, deviceId: "device-2" }, CHILD, "device-2"));
    // Still one device and one code: the first can't come back beside the second.
    await assertFails(createDevice(child(), DEVICE));
    await assertFails(createPairing(child(), "111111", { ...newPairing, deviceId: "device-2" }, CHILD, "device-2"));
  });

  it("a record naming an existing device of its own, already made by an older build, moves once the old one is gone", async () => {
    await seed(async (db) => {
      await setDoc(doc(db, "devices", DEVICE), unpairedDevice);
      await setDoc(doc(db, "devices", "device-2"), unpairedDevice);
      await setDoc(doc(db, "childAccounts", CHILD), { deviceId: DEVICE });
    });
    const move = () => setDoc(doc(child(), "childAccounts", CHILD), { deviceId: "device-2" }, { merge: true });
    await assertFails(move());
    await assertSucceeds(deleteDoc(doc(child(), "devices", DEVICE)));
    await assertSucceeds(move());
  });

  it("a record naming a device that isn't its own doesn't block the phone, and frees no extra device", async () => {
    await seed(async (db) => {
      await setDoc(doc(db, "devices", DEVICE), { ...unpairedDevice, childUid: "someone-else" });
      await setDoc(doc(db, "childAccounts", CHILD), { deviceId: DEVICE });
    });
    // Not its device: it can't delete it, but the record may move on without it.
    await assertFails(deleteDoc(doc(child(), "devices", DEVICE)));
    await assertSucceeds(createDevice(child(), "device-2"));
    // Its own device still counts: no third device while device-2 exists.
    await assertFails(createDevice(child(), "device-3"));
    // The other account's device is untouched.
    let other: unknown;
    await seed(async (db) => {
      other = (await getDoc(doc(db, "devices", DEVICE))).data()?.childUid;
    });
    expect(other).toBe("someone-else");
  });

  it("a record naming a device that isn't its own can be deleted (account deletion)", async () => {
    await seed(async (db) => {
      await setDoc(doc(db, "devices", DEVICE), { ...unpairedDevice, childUid: "someone-else" });
      await setDoc(doc(db, "childAccounts", CHILD), { deviceId: DEVICE });
    });
    await assertSucceeds(deleteDoc(doc(child(), "childAccounts", CHILD)));
  });

  it("the account record is its own phone's, with a 6-digit code and nothing else", async () => {
    await assertFails(setDoc(doc(anonymous(), "childAccounts", CHILD), { deviceId: DEVICE }));
    await assertFails(getDoc(doc(anonymous(), "childAccounts", CHILD)));
    await assertFails(setDoc(doc(child(), "childAccounts", CHILD), { deviceId: DEVICE, openCode: "12" }));
    await assertFails(setDoc(doc(child(), "childAccounts", CHILD), { deviceId: DEVICE, extra: true }));
    await assertFails(setDoc(doc(child(), "childAccounts", CHILD), { deviceId: "x".repeat(65) }));
    await assertSucceeds(setDoc(doc(child(), "childAccounts", CHILD), { deviceId: DEVICE, openCode: null }));
    await assertSucceeds(getDoc(doc(child(), "childAccounts", CHILD)));
    await assertFails(getDocs(collection(child(), "childAccounts")));
  });

  it("the account record is deleted only after its device and code", async () => {
    await seed(async (db) => {
      await setDoc(doc(db, "devices", DEVICE), unpairedDevice);
      await setDoc(doc(db, "pairings", CODE), openPairing);
      await setDoc(doc(db, "childAccounts", CHILD), { deviceId: DEVICE, openCode: CODE });
    });
    await assertFails(deleteDoc(doc(child(), "childAccounts", CHILD)));
    await assertSucceeds(deleteDoc(doc(child(), "devices", DEVICE)));
    await assertFails(deleteDoc(doc(child(), "childAccounts", CHILD)));
    await assertSucceeds(deleteDoc(doc(child(), "pairings", CODE)));
    await assertSucceeds(deleteDoc(doc(child(), "childAccounts", CHILD)));
  });

  it("a device has a short name and a bounded number of fields", async () => {
    await assertFails(createDevice(child(), DEVICE, { ...unpairedDevice, name: "x".repeat(101) }));
    await assertFails(createDevice(child(), DEVICE, { ...unpairedDevice, extra: 1 }));
    await seed((db) => setDoc(doc(db, "devices", DEVICE), pairedDevice));
    await assertFails(updateDoc(doc(child(), "devices", DEVICE), { name: "x".repeat(101) }));
    const many = Object.fromEntries(Array.from({ length: 20 }, (_, i) => [`f${i}`, i]));
    await assertFails(updateDoc(doc(child(), "devices", DEVICE), many));
    await assertSucceeds(updateDoc(doc(child(), "devices", DEVICE), { name: "Samsung SM-A155F" }));
  });

  it("usage only for days close to now, with its two fields and at most 500 apps", async () => {
    await seed((db) => setDoc(doc(db, "devices", DEVICE), pairedDevice));
    const usage = (day: string) => doc(child(), "devices", DEVICE, "usage", day);
    await assertSucceeds(setDoc(usage(isoDay(-1)), { ms: { a: 1 }, updatedAt: serverTimestamp() }));
    await assertSucceeds(setDoc(usage(isoDay(-6)), { ms: { a: 1 } }));
    await assertSucceeds(setDoc(usage(isoDay(1)), { ms: { a: 1 } }));
    await assertFails(setDoc(usage(isoDay(-9)), { ms: { a: 1 } }));
    await assertFails(setDoc(usage(isoDay(3)), { ms: { a: 1 } }));
    await assertFails(setDoc(usage("not-a-day"), { ms: { a: 1 } }));
    await assertFails(setDoc(usage(TODAY), { ms: { a: 1 }, extra: "x" }));
    await assertFails(setDoc(usage(TODAY), { ms: "lots" }));
    const tooMany = Object.fromEntries(Array.from({ length: 501 }, (_, i) => [`app${i}`, 1]));
    await assertFails(setDoc(usage(TODAY), { ms: tooMany }));
    // An old day is still pruned.
    await seed((db) => setDoc(doc(db, "devices", DEVICE, "usage", "2026-01-01"), { ms: { a: 1 } }));
    await assertSucceeds(deleteDoc(usage("2026-01-01")));
  });

  it("only the app list and the alerts under meta, the list bounded", async () => {
    await seed((db) => setDoc(doc(db, "devices", DEVICE), pairedDevice));
    const meta = (id: string) => doc(child(), "devices", DEVICE, "meta", id);
    await assertSucceeds(setDoc(meta("alerts"), { protectionDegraded: false, updatedAt: serverTimestamp() }));
    await assertFails(setDoc(meta("other"), { a: 1 }));
    await assertFails(setDoc(meta("apps"), { apps: Array.from({ length: 2001 }, (_, i) => ({ p: `p${i}`, l: "x" })) }));
    await assertFails(setDoc(meta("apps"), { apps: "all" }));
  });
});
