import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { beforeEach, describe, expect, it } from "vitest";
import { pushToOwner, type Sender } from "../src/send.js";

// Run through `npm run test:functions` in firebase/: the Firestore emulator sets FIRESTORE_EMULATOR_HOST.
initializeApp({ projectId: "demo-nourtime" });
const db = getFirestore();
const NOW = 1_800_000_000_000;

class FakeSender implements Sender {
  calls: Array<{ tokens: string[]; data: Record<string, string> }> = [];
  dead = new Set<string>();

  async send(tokens: string[], data: Record<string, string>) {
    this.calls.push({ tokens, data });
    return tokens.map((t) => (this.dead.has(t) ? { ok: false, code: "messaging/registration-token-not-registered" } : { ok: true }));
  }
}

beforeEach(async () => {
  await fetch(`http://${process.env.FIRESTORE_EMULATOR_HOST}/emulator/v1/projects/demo-nourtime/databases/(default)/documents`, { method: "DELETE" });
  await db.doc("devices/d1").set({ ownerUid: "p1", name: "Galaxy A10", childUid: "c1" });
  await db.doc("parents/p1/tokens/t1").set({ createdAt: new Date(), platform: "android" });
  await db.doc("parents/p1/tokens/t2").set({ createdAt: new Date(), platform: "android" });
});

describe("pushToOwner", () => {
  it("sends to every phone of the owner, with the device name", async () => {
    const s = new FakeSender();
    expect(await pushToOwner(db, s, "d1", "ASK", {}, NOW)).toBe("sent");
    expect(s.calls[0].tokens.sort()).toEqual(["t1", "t2"]);
    expect(s.calls[0].data).toEqual({ type: "ASK", deviceId: "d1", deviceName: "Galaxy A10" });
  });

  it("three asks in a row send one push", async () => {
    const s = new FakeSender();
    await pushToOwner(db, s, "d1", "ASK", {}, NOW);
    expect(await pushToOwner(db, s, "d1", "ASK", {}, NOW + 1000)).toBe("throttled");
    expect(await pushToOwner(db, s, "d1", "ASK", {}, NOW + 2000)).toBe("throttled");
    expect(s.calls.length).toBe(1);
  });

  it("a removed device or a parent without phones sends nothing", async () => {
    const s = new FakeSender();
    await db.doc("devices/d1").update({ ownerUid: null });
    expect(await pushToOwner(db, s, "d1", "ASK", {}, NOW)).toBe("no-owner");
    await db.doc("devices/d2").set({ ownerUid: "p2", name: "x", childUid: "c2" });
    expect(await pushToOwner(db, s, "d2", "ASK", {}, NOW)).toBe("no-tokens");
    expect(s.calls.length).toBe(0);
  });

  it("tokens FCM no longer accepts are deleted, the others kept", async () => {
    const s = new FakeSender();
    s.dead.add("t2");
    await pushToOwner(db, s, "d1", "OTHER_USER", {}, NOW);
    expect((await db.doc("parents/p1/tokens/t2").get()).exists).toBe(false);
    expect((await db.doc("parents/p1/tokens/t1").get()).exists).toBe(true);
  });

  it("a waiting PIN reset carries its time", async () => {
    const s = new FakeSender();
    await pushToOwner(db, s, "d1", "PIN_RESET_PENDING", { pendingUntil: String(NOW + 86_400_000) }, NOW);
    expect(s.calls[0].data.pendingUntil).toBe(String(NOW + 86_400_000));
  });
});
