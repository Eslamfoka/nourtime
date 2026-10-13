# Parent Push Notifications (FCM on Blaze) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The parent's phone gets a push notification, even when Nour Time is closed, when the child asks for more time, when protection is weakened on the child's phone, and when a new PIN chosen through the security question waits for the parent's approval.

**Architecture:** The child's phone keeps writing to Firestore only (no new permission, no FCM on the child side). Two Cloud Functions (2nd gen, TypeScript, in `firebase/functions`) react to exactly two kinds of writes: a new `devices/{id}/requests/{rid}` document, and a change of the small `devices/{id}/meta/alerts` document (alerts move there from the device document, so the functions don't run on every status upload). They look up the device's `ownerUid`, throttle per device and type in a server-only `pushState/{deviceId}` document, and send a **data-only** high-priority FCM message to every token in `parents/{uid}/tokens/{token}`. The parent's phone registers its token after Google sign-in, deletes it on sign-out and account deletion, and builds the notification itself, in the app's language, opening that child's screen on tap.

**Tech Stack:** Kotlin, Hilt, Firebase BoM 33.7.0 + `firebase-messaging`; Cloud Functions for Firebase v2 (`firebase-functions` 6.x, `firebase-admin` 13.x, Node 22, TypeScript); vitest; Firebase Emulator Suite (functions + firestore + auth) and `@firebase/rules-unit-testing`.

**Spec:** owner's decision of 2026-10-13: move to Blaze and push "requests, alerts, and remote PIN reset" to the parent's phone; `docs/handoff.md` §20.6 step 2 and §23 (PIN reset approval command `APPROVE_PIN_RESET`); the 2026-10-13 audits (the parent never hears "ask for more time" unless the app is open; protection alerts reach the parent only when they open the app).

## Global Constraints

- The child's phone gets **no new permission and no FCM**; it keeps working with no network and without pairing (Phase 1 rule).
- **Never `READ_PHONE_STATE`** (owner, 2026-10-13).
- Push messages are **data-only**: no child name, no message text, no app names. Payload: `type`, `deviceId`, `deviceName` (the phone name the parent already sees), and for PIN resets `pendingUntil`.
- Every new string in `values/strings.xml` and `values-ar/strings.xml`; parent-facing Arabic uses the neutral wording already used on the parent's phone.
- Throttle: an "ask for more time" push at most once per 5 minutes per device; each alert type at most once per 30 minutes per device; a PIN reset waiting for approval is never throttled.
- Functions region = the Firestore database's region (read it before the first deploy: `npx firebase firestore:databases:get "(default)" --project nourtime-8d4ce`); one constant `REGION` in `firebase/functions/src/config.ts`.
- Debug builds keep using the emulators (`adb reverse` for 8080/9099; the functions emulator needs no reverse because only the server calls it).
- Existing child apps (no update yet) keep working: the parent's phone still reads the old `alerts` field when `meta/alerts` is missing (no push for those, by design).
- `firebase-messaging` must be on Google's Families self-certified SDK list before release (owner check, Task 7).

## Review Focus

1. **A phone shared by two parent accounts** (sign out, the other parent signs in): the old account must stop getting this phone's pushes at once. Token documents are deleted on sign-out and re-registered under the new uid. Test in Task 5 (`PushTokensTest`: sign-out deletes, sign-in under another uid moves the token).
2. **A child tapping "ask for more time" again and again:** one push per 5 minutes per device. Test in Task 3 (`throttle.test.ts`) and Task 4 (emulator test: three requests in a row → one send).
3. **A removed device** (`ownerUid` null) or a device whose parent has no tokens: nothing is sent and nothing fails. Test in Task 4.
4. **A token FCM no longer accepts** (app uninstalled, data cleared): it is deleted so it isn't retried forever. Test in Task 4 (fake sender reports `messaging/registration-token-not-registered`).
5. **The parent's phone with notifications switched off** (Android 13+ permission denied or channel off): the app works as before, and the parent's home screen says pushes are off with a button to fix it. Test in Task 6 (`ParentNotificationsTest` for the decision; manual check on the emulator).

---

## File Structure

| File | Responsibility |
|---|---|
| `firebase/functions/package.json`, `tsconfig.json`, `vitest.config.ts` | Functions project (Node 22, TypeScript). |
| `firebase/functions/src/config.ts` | `REGION`, throttle gaps. |
| `firebase/functions/src/events.ts` | Pure: which push types a change of `meta/alerts` produces. |
| `firebase/functions/src/throttle.ts` | Pure: may this type be sent now, given the last send. |
| `firebase/functions/src/send.ts` | Look up owner + tokens, throttle in a transaction, send, prune dead tokens. Takes a `Sender` so tests use a fake. |
| `firebase/functions/src/index.ts` | The two triggers. |
| `firebase/functions/test/*.test.ts` | Unit tests (events, throttle) and emulator tests (send). |
| `firebase/firebase.json`, `firebase/firestore.rules`, `firebase/test/rules.test.ts` | Functions + emulator config; rules for `parents/{uid}/tokens` and `pushState`. |
| `app/.../remote/child/RemoteSync.kt` | Alerts (plus `protectionDegraded`) written to `meta/alerts`. |
| `app/.../data/protection/ProtectionAlerts.kt` | `protectionDegraded` field. |
| `app/.../remote/parent/ParentDevices.kt` | Reads `meta/alerts`, falls back to the old field. |
| `app/.../remote/parent/PushTokens.kt` | Register/unregister the parent's FCM token (`TokenStore`, `PushTokenSource` seams). |
| `app/.../remote/parent/PushMessage.kt` | Parse the data payload into a typed message. |
| `app/.../remote/parent/ParentMessagingService.kt` | `FirebaseMessagingService`: new token → register; message → notification. |
| `app/.../feature/parent/ParentNotifications.kt` | Channel, text per type, tap → open that child's screen. |
| `app/.../feature/parent/OpenDeviceRequest.kt` | Hands the tapped device id to `ParentRoute` (like `ForgotPinRequest`). |
| `.github/workflows/android.yml` | Functions tests in the `firestore-rules` job. |
| `docs/privacy-policy.md`, `docs/play-compliance.md`, `docs/handoff.md`, `README.md` | FCM token disclosure, Blaze, deploy runbook. |

---

### Task 1: Alerts in their own document (`meta/alerts`), with `protectionDegraded`

**Files:**
- Modify: `app/src/main/java/com/nourtime/app/data/protection/ProtectionAlerts.kt`
- Modify: `app/src/main/java/com/nourtime/app/remote/child/RemoteSync.kt` (`uploadAlerts`)
- Modify: `app/src/main/java/com/nourtime/app/remote/parent/ParentDevices.kt` (`device()`, `devices()`, `toDevice`)
- Modify: `app/src/main/java/com/nourtime/app/remote/RemotePaths.kt`
- Test: `app/src/test/java/com/nourtime/app/data/protection/ProtectionAlertsTest.kt`

**Interfaces:**
- Produces: `RemotePaths.ALERTS_DOC = "alerts"`; `ProtectionAlerts.protectionDegraded: Boolean` (map key `protectionDegraded`); `ProtectionAlerts.visible()` adds `ProtectionAlert.PROTECTION_DEGRADED`. The `meta/alerts` document has exactly the keys of `ProtectionAlerts.toMap()` plus `updatedAt` (server timestamp).

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `degraded protection reaches the parent's phone and is shown`() {
    val a = ProtectionAlerts(protectionDegraded = true)
    assertEquals(a, ProtectionAlerts.fromMap(a.toMap()))
    assertEquals(listOf(ProtectionAlert.PROTECTION_DEGRADED), a.visible(nowMs = 0))
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew --offline -q testDebugUnitTest --tests "com.nourtime.app.data.protection.ProtectionAlertsTest"`
Expected: compile failure, `protectionDegraded` unknown.

- [ ] **Step 3: Implement**

In `ProtectionAlerts.kt` add `PROTECTION_DEGRADED` to `ProtectionAlert`, the field `val protectionDegraded: Boolean = false`, `"protectionDegraded" to protectionDegraded` in `toMap()`, `protectionDegraded = m["protectionDegraded"] as? Boolean ?: false` in `fromMap()`, and in `visible()` first line `if (protectionDegraded) add(ProtectionAlert.PROTECTION_DEGRADED)`. Give the new alert a message in the existing `ProtectionAlert.message` mapping (`R.string.parent_protection_warning` already says protection is weakened).

In `RemotePaths.kt`: `const val ALERTS_DOC = "alerts"`.

In `RemoteSync.uploadAlerts` replace the device-document update with:

```kotlin
private suspend fun uploadAlerts(device: DocumentReference) {
    // Its own small document: the push functions run only when an alert changes, not on every status upload.
    combine(
        protectionAlerts.alerts,
        security.pendingPinActiveAt,
        engine.status.filterNotNull().map { it.protectionDegraded }.distinctUntilChanged(),
    ) { alerts, pending, degraded -> alerts.copy(pinResetPendingUntilMs = pending, protectionDegraded = degraded) }
        .distinctUntilChanged()
        .collect {
            device.collection(RemotePaths.META).document(RemotePaths.ALERTS_DOC)
                .set(it.toMap() + ("updatedAt" to FieldValue.serverTimestamp()))
                .logIfFailed(TAG, "alerts")
        }
}
```

In `ParentDevices`: `device(id)` combines the device listener with a listener on `devices/{id}/meta/alerts` (same `MetadataChanges.INCLUDE` + `cacheOnlyMissing()` rule); when that document exists its `ProtectionAlerts.fromMap(...)` replaces `ChildDevice.alerts`, otherwise the old `alerts` field is kept (child phones not yet updated). `devices(uid)` keeps reading the old field only (the list shows a warning icon from `status.protectionDegraded`, which is unchanged).

- [ ] **Step 4: Run the tests**

Run: `./gradlew --offline -q testDebugUnitTest --tests "com.nourtime.app.data.protection.*" --tests "com.nourtime.app.remote.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src && git commit -m "Alerts in their own meta/alerts document, with degraded protection"
```

### Task 2: Firestore rules for parent tokens and the server-only push state

**Files:**
- Modify: `firebase/firestore.rules`
- Test: `firebase/test/rules.test.ts`

**Interfaces:**
- Produces: `parents/{uid}/tokens/{token}` documents `{ createdAt: timestamp, platform: "android" }`, readable and writable only by `uid`, with the document id equal to the FCM token. `pushState/{deviceId}`: no client access (Admin SDK only).

- [ ] **Step 1: Write the failing rules tests**

```ts
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
```

(`parent()`, `child()`, `PARENT`, `OTHER_PARENT`, `DEVICE` are the helpers already defined at the top of `rules.test.ts`.)

- [ ] **Step 2: Run them to see them fail**

Run (in `firebase/`, Java 21 on PATH): `npm test`
Expected: the new "registers" test fails (default deny).

- [ ] **Step 3: Add the rules**

Inside `match /databases/{database}/documents`, after `match /devices/{id} { ... }`:

```
    // FCM tokens of a parent's phones (one document per token, id = token).
    match /parents/{uid}/tokens/{token} {
      allow read, delete: if signedIn() && request.auth.uid == uid;
      allow create, update: if signedIn() && request.auth.uid == uid
        && request.resource.data.keys().hasOnly(['createdAt', 'platform'])
        && request.resource.data.createdAt == request.time
        && request.resource.data.platform == 'android';
    }

    // When each push type was last sent for a device: Cloud Functions only.
    match /pushState/{deviceId} {
      allow read, write: if false;
    }
```

- [ ] **Step 4: Run the rules tests**

Run: `npm test` → Expected: all pass (50 + 3).

- [ ] **Step 5: Commit**

```bash
git add firebase/firestore.rules firebase/test/rules.test.ts && git commit -m "Rules: parent push tokens, server-only push state"
```

### Task 3: Functions project and the pure logic (which pushes, how often)

**Files:**
- Create: `firebase/functions/package.json`, `firebase/functions/tsconfig.json`, `firebase/functions/vitest.config.ts`, `firebase/functions/.gitignore` (`lib/`, `node_modules/`)
- Create: `firebase/functions/src/config.ts`, `firebase/functions/src/events.ts`, `firebase/functions/src/throttle.ts`
- Test: `firebase/functions/test/events.test.ts`, `firebase/functions/test/throttle.test.ts`
- Modify: `firebase/firebase.json` (functions source + emulator port 5001)

**Interfaces:**
- Produces:
  - `type PushType = "ASK" | "MISSED_RESTART" | "OTHER_USER" | "USB_DEBUGGING" | "NOTIFICATIONS_OFF" | "PROTECTION_DEGRADED" | "PIN_RESET_PENDING"`
  - `newAlertEvents(before: AlertsDoc | undefined, after: AlertsDoc | undefined, nowMs: number): PushType[]`
  - `mayPush(type: PushType, lastSentMs: number | undefined, nowMs: number): boolean`
  - `REGION: string`, `MIN_GAP_MS: Record<PushType, number>`

- [ ] **Step 1: Scaffold**

`firebase/functions/package.json`:

```json
{
  "name": "nourtime-functions",
  "private": true,
  "type": "module",
  "main": "lib/index.js",
  "engines": { "node": "22" },
  "scripts": {
    "build": "tsc",
    "test": "vitest run test/events.test.ts test/throttle.test.ts",
    "test:emulator": "firebase emulators:exec --project demo-nourtime --only functions,firestore \"vitest run test/send.test.ts\""
  },
  "dependencies": { "firebase-admin": "^13.0.0", "firebase-functions": "^6.1.0" },
  "devDependencies": { "typescript": "^5.6.0", "vitest": "^2.1.0" }
}
```

`tsconfig.json`: `{"compilerOptions":{"module":"NodeNext","moduleResolution":"NodeNext","target":"ES2022","outDir":"lib","strict":true,"skipLibCheck":true},"include":["src"]}`.

In `firebase/firebase.json` add `"functions": [{ "source": "functions", "codebase": "default", "predeploy": ["npm --prefix \"$RESOURCE_DIR\" run build"] }]` and under `emulators`: `"functions": { "port": 5001, "host": "127.0.0.1" }`. Run `npm --prefix firebase/functions install`.

`src/config.ts`:

```ts
import type { PushType } from "./events.js";

/** Must be the Firestore database's location (see the plan's Global Constraints). */
export const REGION = "europe-west1";

const MINUTE = 60_000;
export const MIN_GAP_MS: Record<PushType, number> = {
  ASK: 5 * MINUTE,
  MISSED_RESTART: 30 * MINUTE,
  OTHER_USER: 30 * MINUTE,
  USB_DEBUGGING: 30 * MINUTE,
  NOTIFICATIONS_OFF: 30 * MINUTE,
  PROTECTION_DEGRADED: 30 * MINUTE,
  PIN_RESET_PENDING: 0,
};
```

- [ ] **Step 2: Write the failing tests**

`test/events.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import { newAlertEvents } from "../src/events.js";

const NOW = 1_800_000_000_000;

describe("newAlertEvents", () => {
  it("nothing new, nothing sent", () => {
    const a = { usbDebugging: true, missedRestartAt: NOW - 1000 };
    expect(newAlertEvents(a, a, NOW)).toEqual([]);
  });
  it("a newer event time is a new event", () => {
    expect(newAlertEvents({ otherUserAt: 1 }, { otherUserAt: 2 }, NOW)).toEqual(["OTHER_USER"]);
    expect(newAlertEvents(undefined, { missedRestartAt: 5 }, NOW)).toEqual(["MISSED_RESTART"]);
  });
  it("a switch turning on is an event, turning off is not", () => {
    expect(newAlertEvents({ usbDebugging: false }, { usbDebugging: true }, NOW)).toEqual(["USB_DEBUGGING"]);
    expect(newAlertEvents({ notificationsOff: true }, { notificationsOff: false }, NOW)).toEqual([]);
    expect(newAlertEvents({}, { protectionDegraded: true }, NOW)).toEqual(["PROTECTION_DEGRADED"]);
  });
  it("a PIN reset waiting in the future is an event; a past or cleared one is not", () => {
    expect(newAlertEvents({}, { pinResetPendingUntil: NOW + 1000 }, NOW)).toEqual(["PIN_RESET_PENDING"]);
    expect(newAlertEvents({ pinResetPendingUntil: NOW + 1000 }, { pinResetPendingUntil: null }, NOW)).toEqual([]);
    expect(newAlertEvents({}, { pinResetPendingUntil: NOW - 1 }, NOW)).toEqual([]);
  });
  it("a deleted alerts document sends nothing", () => {
    expect(newAlertEvents({ usbDebugging: true }, undefined, NOW)).toEqual([]);
  });
});
```

`test/throttle.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import { mayPush } from "../src/throttle.js";

describe("mayPush", () => {
  it("first push of a type is allowed", () => expect(mayPush("ASK", undefined, 0)).toBe(true));
  it("asks are at most every 5 minutes", () => {
    expect(mayPush("ASK", 0, 5 * 60_000 - 1)).toBe(false);
    expect(mayPush("ASK", 0, 5 * 60_000)).toBe(true);
  });
  it("a PIN reset waiting for approval is never held back", () => expect(mayPush("PIN_RESET_PENDING", 0, 1)).toBe(true));
});
```

- [ ] **Step 3: Run them to see them fail**

Run: `npm --prefix firebase/functions test` → Expected: FAIL, modules not found.

- [ ] **Step 4: Implement**

`src/events.ts`:

```ts
/** The meta/alerts document the child's phone writes (ProtectionAlerts.toMap on Android). */
export interface AlertsDoc {
  missedRestartAt?: number | null;
  otherUserAt?: number | null;
  usbDebugging?: boolean;
  notificationsOff?: boolean;
  pinResetPendingUntil?: number | null;
  protectionDegraded?: boolean;
}

export type PushType =
  | "ASK" | "MISSED_RESTART" | "OTHER_USER" | "USB_DEBUGGING"
  | "NOTIFICATIONS_OFF" | "PROTECTION_DEGRADED" | "PIN_RESET_PENDING";

const newer = (b?: number | null, a?: number | null) => a != null && (b == null || a > b);
const turnedOn = (b?: boolean, a?: boolean) => a === true && b !== true;

/** What changed for the worse between two versions of meta/alerts, as push types. */
export function newAlertEvents(before: AlertsDoc | undefined, after: AlertsDoc | undefined, nowMs: number): PushType[] {
  if (!after) return [];
  const b = before ?? {};
  const out: PushType[] = [];
  if (newer(b.missedRestartAt, after.missedRestartAt)) out.push("MISSED_RESTART");
  if (newer(b.otherUserAt, after.otherUserAt)) out.push("OTHER_USER");
  if (turnedOn(b.usbDebugging, after.usbDebugging)) out.push("USB_DEBUGGING");
  if (turnedOn(b.notificationsOff, after.notificationsOff)) out.push("NOTIFICATIONS_OFF");
  if (turnedOn(b.protectionDegraded, after.protectionDegraded)) out.push("PROTECTION_DEGRADED");
  const pending = after.pinResetPendingUntil;
  if (pending != null && pending > nowMs && pending !== b.pinResetPendingUntil) out.push("PIN_RESET_PENDING");
  return out;
}
```

`src/throttle.ts`:

```ts
import { MIN_GAP_MS } from "./config.js";
import type { PushType } from "./events.js";

export function mayPush(type: PushType, lastSentMs: number | undefined, nowMs: number): boolean {
  return lastSentMs == null || nowMs - lastSentMs >= MIN_GAP_MS[type];
}
```

- [ ] **Step 5: Run the tests** → `npm --prefix firebase/functions test` → PASS.

- [ ] **Step 6: Commit**

```bash
git add firebase/functions firebase/firebase.json && git commit -m "Functions project: which alert changes push, and how often"
```

### Task 4: The two triggers and sending (owner lookup, throttle, dead tokens)

**Files:**
- Create: `firebase/functions/src/send.ts`, `firebase/functions/src/index.ts`
- Test: `firebase/functions/test/send.test.ts` (Firestore emulator)

**Interfaces:**
- Consumes: `newAlertEvents`, `mayPush`, `REGION` (Task 3); token documents (Task 2).
- Produces:
  - `interface Sender { send(tokens: string[], data: Record<string, string>): Promise<Array<{ ok: boolean; code?: string }>> }`
  - `pushToOwner(db: Firestore, sender: Sender, deviceId: string, type: PushType, extra: Record<string, string>, nowMs: number): Promise<"sent" | "throttled" | "no-owner" | "no-tokens">`
  - Exports `onTimeRequest` and `onAlertsChanged` (2nd gen triggers).
  - Data payload keys: `type`, `deviceId`, `deviceName`, and `pendingUntil` (ms as a string) for `PIN_RESET_PENDING`.

- [ ] **Step 1: Write the failing emulator test**

```ts
import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { beforeEach, describe, expect, it } from "vitest";
import { pushToOwner, type Sender } from "../src/send.js";

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
  await fetch("http://127.0.0.1:8080/emulator/v1/projects/demo-nourtime/databases/(default)/documents", { method: "DELETE" });
  await db.doc("devices/d1").set({ ownerUid: "p1", name: "Galaxy A10", childUid: "c1" });
  await db.doc("parents/p1/tokens/t1").set({ createdAt: new Date(), platform: "android" });
  await db.doc("parents/p1/tokens/t2").set({ createdAt: new Date(), platform: "android" });
});

describe("pushToOwner", () => {
  it("sends to every token of the owner, with the device name", async () => {
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
  it("a removed device or a parent without tokens sends nothing", async () => {
    const s = new FakeSender();
    await db.doc("devices/d1").update({ ownerUid: null });
    expect(await pushToOwner(db, s, "d1", "ASK", {}, NOW)).toBe("no-owner");
    await db.doc("devices/d2").set({ ownerUid: "p2", name: "x", childUid: "c2" });
    expect(await pushToOwner(db, s, "d2", "ASK", {}, NOW)).toBe("no-tokens");
    expect(s.calls.length).toBe(0);
  });
  it("tokens FCM no longer accepts are deleted", async () => {
    const s = new FakeSender();
    s.dead.add("t2");
    await pushToOwner(db, s, "d1", "OTHER_USER", {}, NOW);
    expect((await db.doc("parents/p1/tokens/t2").get()).exists).toBe(false);
    expect((await db.doc("parents/p1/tokens/t1").get()).exists).toBe(true);
  });
});
```

- [ ] **Step 2: Run it to see it fail**

Run (in `firebase/functions`, Java 21 on PATH): `npm run test:emulator` → Expected: FAIL, `../src/send.js` not found.

- [ ] **Step 3: Implement `send.ts`**

```ts
import type { Firestore } from "firebase-admin/firestore";
import { mayPush } from "./throttle.js";
import type { PushType } from "./events.js";

export interface Sender {
  send(tokens: string[], data: Record<string, string>): Promise<Array<{ ok: boolean; code?: string }>>;
}

const DEAD = new Set(["messaging/registration-token-not-registered", "messaging/invalid-registration-token"]);

/** One push to every phone of the device's parent, at most as often as MIN_GAP_MS allows. */
export async function pushToOwner(
  db: Firestore, sender: Sender, deviceId: string, type: PushType, extra: Record<string, string>, nowMs: number,
): Promise<"sent" | "throttled" | "no-owner" | "no-tokens"> {
  const device = (await db.doc(`devices/${deviceId}`).get()).data();
  const ownerUid = device?.ownerUid as string | null | undefined;
  if (!ownerUid) return "no-owner";

  const stateRef = db.doc(`pushState/${deviceId}`);
  const allowed = await db.runTransaction(async (tx) => {
    const last = (await tx.get(stateRef)).data()?.[type] as number | undefined;
    if (!mayPush(type, last, nowMs)) return false;
    tx.set(stateRef, { [type]: nowMs }, { merge: true });
    return true;
  });
  if (!allowed) return "throttled";

  const tokens = (await db.collection(`parents/${ownerUid}/tokens`).get()).docs.map((d) => d.id);
  if (tokens.length === 0) return "no-tokens";

  const data = { type, deviceId, deviceName: String(device?.name ?? ""), ...extra };
  const results = await sender.send(tokens, data);
  await Promise.all(results.map((r, i) =>
    !r.ok && r.code && DEAD.has(r.code) ? db.doc(`parents/${ownerUid}/tokens/${tokens[i]}`).delete() : null));
  return "sent";
}
```

- [ ] **Step 4: Implement `index.ts`**

```ts
import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { onDocumentCreated, onDocumentWritten } from "firebase-functions/v2/firestore";
import { logger } from "firebase-functions/v2";
import { REGION } from "./config.js";
import { newAlertEvents, type AlertsDoc } from "./events.js";
import { pushToOwner, type Sender } from "./send.js";

initializeApp();
const db = getFirestore();

const fcm: Sender = {
  async send(tokens, data) {
    const r = await getMessaging().sendEachForMulticast({ tokens, data, android: { priority: "high", ttl: 3600_000 } });
    return r.responses.map((x) => ({ ok: x.success, code: x.error?.code }));
  },
};

/** The child asked for more time. */
export const onTimeRequest = onDocumentCreated({ document: "devices/{id}/requests/{rid}", region: REGION }, async (event) => {
  const result = await pushToOwner(db, fcm, event.params.id, "ASK", {}, Date.now());
  logger.info("ask push", { device: event.params.id, result });
});

/** An alert appeared, or a new PIN waits for approval. */
export const onAlertsChanged = onDocumentWritten({ document: "devices/{id}/meta/alerts", region: REGION }, async (event) => {
  const before = event.data?.before.data() as AlertsDoc | undefined;
  const after = event.data?.after.data() as AlertsDoc | undefined;
  for (const type of newAlertEvents(before, after, Date.now())) {
    const extra = type === "PIN_RESET_PENDING" ? { pendingUntil: String(after?.pinResetPendingUntil) } : {};
    const result = await pushToOwner(db, fcm, event.params.id, type, extra, Date.now());
    logger.info("alert push", { device: event.params.id, type, result });
  }
});
```

- [ ] **Step 5: Run the tests**

Run: `npm --prefix firebase/functions run build && npm --prefix firebase/functions run test:emulator` → Expected: PASS (4 tests). Then `npm --prefix firebase/functions test` → PASS.

- [ ] **Step 6: Commit**

```bash
git add firebase/functions && git commit -m "Functions: push asks and alerts to the parent's phones, throttled, dead tokens pruned"
```

### Task 5: The parent's phone registers its token (and removes it on sign-out / deletion)

**Files:**
- Modify: `gradle/libs.versions.toml` (`firebase-messaging = { group = "com.google.firebase", name = "firebase-messaging" }`), `app/build.gradle.kts` (`implementation(libs.firebase.messaging)`)
- Create: `app/src/main/java/com/nourtime/app/remote/parent/PushTokens.kt`
- Modify: `app/src/main/java/com/nourtime/app/feature/parent/ParentRoute.kt` (`ParentViewModel`: register on sign-in, unregister on sign-out/deletion)
- Modify: `app/src/main/java/com/nourtime/app/remote/parent/ParentDevices.kt` (`forgetParent`: delete `parents/{uid}/tokens/*`)
- Modify: `app/src/main/java/com/nourtime/app/di/BindingsModule.kt`
- Test: `app/src/test/java/com/nourtime/app/remote/parent/PushTokensTest.kt`

**Interfaces:**
- Produces:
  - `interface PushTokenSource { suspend fun token(): String; suspend fun delete() }` (Firebase impl `FirebasePushTokenSource` using `FirebaseMessaging.getInstance().token.await()` / `deleteToken().await()`)
  - `interface TokenStore { suspend fun put(uid: String, token: String); suspend fun remove(uid: String, token: String) }` (Firestore impl writes `parents/{uid}/tokens/{token}` with `createdAt = serverTimestamp()`, `platform = "android"`)
  - `class PushTokens(source: PushTokenSource, store: TokenStore)` with `suspend fun register(uid: String)`, `suspend fun unregister(uid: String)`, `suspend fun onNewToken(uid: String?, token: String)`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.nourtime.app.remote.parent

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PushTokensTest {
    private class FakeSource(var current: String = "tokA") : PushTokenSource {
        override suspend fun token() = current
        override suspend fun delete() { current = "tokB" }
    }
    private class FakeStore : TokenStore {
        val docs = mutableSetOf<Pair<String, String>>()
        override suspend fun put(uid: String, token: String) { docs += uid to token }
        override suspend fun remove(uid: String, token: String) { docs -= uid to token }
    }

    private val source = FakeSource()
    private val store = FakeStore()
    private val tokens = PushTokens(source, store)

    @Test
    fun `signing in registers this phone's token for that parent`() = runTest {
        tokens.register("p1")
        assertEquals(setOf("p1" to "tokA"), store.docs)
    }

    @Test
    fun `signing out removes it and gets a fresh token, so the next parent doesn't share it`() = runTest {
        tokens.register("p1")
        tokens.unregister("p1")
        tokens.register("p2")
        assertEquals(setOf("p2" to "tokB"), store.docs)
    }

    @Test
    fun `a new token from FCM is stored only while a parent is signed in`() = runTest {
        tokens.onNewToken(null, "tokC")
        assertEquals(emptySet<Pair<String, String>>(), store.docs)
        tokens.onNewToken("p1", "tokC")
        assertEquals(setOf("p1" to "tokC"), store.docs)
    }
}
```

- [ ] **Step 2: Run it to see it fail** → `./gradlew --offline -q testDebugUnitTest --tests "com.nourtime.app.remote.parent.PushTokensTest"` → compile failure.

- [ ] **Step 3: Implement `PushTokens.kt`**

```kotlin
package com.nourtime.app.remote.parent

import android.util.Log

/** FCM token of this phone (Firebase in the app, a fake in tests). */
interface PushTokenSource {
    suspend fun token(): String
    /** Forgets the token, so the next one is new (after sign-out). */
    suspend fun delete()
}

/** Where the parent's tokens are kept: `parents/{uid}/tokens/{token}`. */
interface TokenStore {
    suspend fun put(uid: String, token: String)
    suspend fun remove(uid: String, token: String)
}

/**
 * The parent's phone receives pushes only while a parent is signed in, and only that parent's: the token
 * is stored under the signed-in uid, removed and replaced on sign-out (a phone may change hands).
 */
class PushTokens(private val source: PushTokenSource, private val store: TokenStore) {

    suspend fun register(uid: String) {
        runCatching { store.put(uid, source.token()) }.onFailure { runCatching { Log.w(TAG, "couldn't register", it) } }
    }

    suspend fun unregister(uid: String) {
        runCatching {
            store.remove(uid, source.token())
            source.delete()
        }.onFailure { runCatching { Log.w(TAG, "couldn't unregister", it) } }
    }

    suspend fun onNewToken(uid: String?, token: String) {
        if (uid != null) runCatching { store.put(uid, token) }
    }

    private companion object { const val TAG = "PushTokens" }
}
```

Firebase implementations in the same file (`FirebasePushTokenSource`, `FirestoreTokenStore(firestore)`), and a Hilt `@Provides @Singleton fun pushTokens(...)` in `AppModule`. In `ParentViewModel.init`, `viewModelScope.launch { auth.user.filterNotNull().distinctUntilChanged { a, b -> a.uid == b.uid }.collect { pushTokens.register(it.uid) } }`; in `signOut()` call `pushTokens.unregister(uid)` **before** `auth.signOut()` (the delete needs the signed-in uid); in `ParentDevices.forgetParent(uid)` delete every document under `parents/{uid}/tokens`.

- [ ] **Step 4: Run the tests** → PASS.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app && git commit -m "Parent's phone registers its push token while signed in"
```

### Task 6: Receiving pushes on the parent's phone

**Files:**
- Create: `app/src/main/java/com/nourtime/app/remote/parent/PushMessage.kt`
- Create: `app/src/main/java/com/nourtime/app/remote/parent/ParentMessagingService.kt`
- Create: `app/src/main/java/com/nourtime/app/feature/parent/ParentNotifications.kt`
- Create: `app/src/main/java/com/nourtime/app/feature/parent/OpenDeviceRequest.kt`
- Modify: `app/src/main/AndroidManifest.xml` (service with `com.google.firebase.MESSAGING_EVENT`)
- Modify: `app/src/main/java/com/nourtime/app/MainActivity.kt` (`onCreate`/`onNewIntent`: hand `EXTRA_DEVICE_ID` to `OpenDeviceRequest`)
- Modify: `app/src/main/java/com/nourtime/app/feature/parent/ParentRoute.kt` (open the requested device; "Notifications are off" card with a button that asks for `POST_NOTIFICATIONS` or opens the channel settings)
- Modify: `values/strings.xml`, `values-ar/strings.xml`
- Test: `app/src/test/java/com/nourtime/app/remote/parent/PushMessageTest.kt`

**Interfaces:**
- Consumes: payload from Task 4 (`type`, `deviceId`, `deviceName`, `pendingUntil`).
- Produces:
  - `sealed interface PushMessage { val deviceId: String; val deviceName: String }` with `Ask`, `Alert(alert: ProtectionAlert)`, `PinResetPending(untilMs: Long)`; `fun pushMessageOf(data: Map<String, String>): PushMessage?` (null for unknown types or a missing `deviceId`).
  - `ParentNotifications.show(context, message: PushMessage)`; notification id = `deviceId.hashCode() * 31 + type ordinal` (one per device and type, replaced not stacked); channel `parent_updates` (IMPORTANCE_HIGH).
  - `object OpenDeviceRequest { val deviceId: StateFlow<String?>; fun request(id: String); fun consume(): String? }`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.nourtime.app.remote.parent

import com.nourtime.app.data.protection.ProtectionAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PushMessageTest {
    private val base = mapOf("deviceId" to "d1", "deviceName" to "Galaxy A10")

    @Test
    fun `reads an ask`() {
        assertEquals(PushMessage.Ask("d1", "Galaxy A10"), pushMessageOf(base + ("type" to "ASK")))
    }

    @Test
    fun `reads alerts`() {
        assertEquals(PushMessage.Alert("d1", "Galaxy A10", ProtectionAlert.USB_DEBUGGING), pushMessageOf(base + ("type" to "USB_DEBUGGING")))
        assertEquals(PushMessage.Alert("d1", "Galaxy A10", ProtectionAlert.PROTECTION_DEGRADED), pushMessageOf(base + ("type" to "PROTECTION_DEGRADED")))
    }

    @Test
    fun `reads a waiting PIN reset with its time`() {
        assertEquals(
            PushMessage.PinResetPending("d1", "Galaxy A10", 1_800_000_000_000),
            pushMessageOf(base + ("type" to "PIN_RESET_PENDING") + ("pendingUntil" to "1800000000000")),
        )
    }

    @Test
    fun `unknown types, missing device or a bad time are ignored`() {
        assertNull(pushMessageOf(base + ("type" to "WIPE")))
        assertNull(pushMessageOf(mapOf("type" to "ASK")))
        assertNull(pushMessageOf(base + ("type" to "PIN_RESET_PENDING") + ("pendingUntil" to "soon")))
    }
}
```

- [ ] **Step 2: Run it to see it fail** → compile failure.

- [ ] **Step 3: Implement `PushMessage.kt`**

```kotlin
package com.nourtime.app.remote.parent

import com.nourtime.app.data.protection.ProtectionAlert

/** A push from the server to the parent's phone (data only; the text is built on the phone). */
sealed interface PushMessage {
    val deviceId: String
    val deviceName: String

    data class Ask(override val deviceId: String, override val deviceName: String) : PushMessage
    data class Alert(override val deviceId: String, override val deviceName: String, val alert: ProtectionAlert) : PushMessage
    data class PinResetPending(override val deviceId: String, override val deviceName: String, val untilMs: Long) : PushMessage
}

fun pushMessageOf(data: Map<String, String>): PushMessage? {
    val id = data["deviceId"]?.takeIf { it.isNotBlank() } ?: return null
    val name = data["deviceName"].orEmpty()
    return when (val type = data["type"]) {
        "ASK" -> PushMessage.Ask(id, name)
        "PIN_RESET_PENDING" -> data["pendingUntil"]?.toLongOrNull()?.let { PushMessage.PinResetPending(id, name, it) }
        else -> ProtectionAlert.entries.firstOrNull { it.name == type }?.let { PushMessage.Alert(id, name, it) }
    }
}
```

`ParentMessagingService` (`@AndroidEntryPoint class ParentMessagingService : FirebaseMessagingService()`): `onNewToken(token)` → `scope.launch { pushTokens.onNewToken(auth.user.value?.uid, token) }`; `onMessageReceived(m)` → if `modes.mode.first() == AppMode.PARENT`, `pushMessageOf(m.data)?.let { ParentNotifications.show(this, it) }`.

`ParentNotifications.show`: title/body per type —
- `Ask`: `parent_push_ask_title` "Asking for more time", body `parent_push_ask_body` "%1$s is asking for more time."
- `Alert`: title `parent_push_alert_title` "Protection on %1$s", body = the existing `ProtectionAlert.message` string.
- `PinResetPending`: title `device_pin_reset_title` (exists), body `parent_push_pin_body` "A new PIN was chosen on %1$s. Tap to approve it, or unlock that phone with the current PIN to cancel it."
Tap: `PendingIntent.getActivity(context, id, Intent(context, MainActivity::class.java).putExtra(EXTRA_DEVICE_ID, deviceId).addFlags(FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TOP), FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT)`.

`ParentRoute`: `LaunchedEffect(Unit) { OpenDeviceRequest.deviceId.filterNotNull().collect { OpenDeviceRequest.consume()?.let(viewModel::open) } }`. On the parent home, when `!NotificationManagerCompat.from(context).areNotificationsEnabled()`, a card "Notifications are off: you won't hear when your child asks for more time" with a button: Android 13+ → request `POST_NOTIFICATIONS` (rememberLauncherForActivityResult); otherwise open `Settings.ACTION_APP_NOTIFICATION_SETTINGS`.

- [ ] **Step 4: Run the tests** → `./gradlew --offline -q testDebugUnitTest lintDebug assembleDebug` → PASS, lint clean.

- [ ] **Step 5: Emulator check** (debug build, functions + firestore + auth emulators): parent user 11 signed in with the test account; child user 0 paired; tap "Ask for more time" on the child's lock screen with the parent app closed → a notification on user 11 within a few seconds; tap → that child's screen. Note: FCM delivery from the functions emulator doesn't reach devices; for this check the functions run against the real project after deploy (Task 7), or the emulator check only verifies that the function logs `ask push … sent` and `ParentNotifications` is exercised with `adb shell am broadcast` of a test intent. Record which was done.

- [ ] **Step 6: Commit**

```bash
git add app && git commit -m "Parent's phone shows pushes: asks, alerts, a PIN waiting for approval"
```

### Task 7: CI, docs, and the deploy runbook

**Files:**
- Modify: `.github/workflows/android.yml` (the `firestore-rules` job: `npm --prefix functions ci && npm --prefix functions test && npm --prefix functions run build && npm --prefix functions run test:emulator`)
- Modify: `docs/privacy-policy.md` (EN/AR): the parent's phone stores an FCM registration token per signed-in phone to receive notifications; deleted on sign-out and account deletion; notifications carry only the event type and the phone's name.
- Modify: `docs/play-compliance.md`: Data safety → "Device or other IDs: FCM token (parent's phone only, app functionality, not shared)"; `firebase-messaging` on the Families SDK list (owner to verify).
- Modify: `docs/account-deletion.md` (EN/AR): tokens deleted.
- Modify: `README.md` (Blaze needed; how to deploy), `docs/handoff.md` (new section + runbook).

- [ ] **Step 1: CI change and docs** (text above).
- [ ] **Step 2: Runbook in `docs/handoff.md`** (owner):
  1. Firebase console → upgrade `nourtime-8d4ce` to Blaze; set a budget alert (e.g. $5/month).
  2. `npx firebase login` (owner), then `npx firebase firestore:databases:get "(default)" --project nourtime-8d4ce` → put its location in `firebase/functions/src/config.ts` `REGION`.
  3. `cd firebase && npx firebase deploy --only firestore:rules,functions --project nourtime-8d4ce`.
  4. Install the new release APK on both phones; on the parent's phone allow notifications when asked.
- [ ] **Step 3: Verify** → push to GitHub; both CI jobs green.
- [ ] **Step 4: Commit**

```bash
git add .github docs README.md && git commit -m "Push notifications: CI runs the functions tests; privacy, Data safety and deploy runbook"
```
