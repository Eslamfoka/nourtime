import { FieldValue, type Firestore } from "firebase-admin/firestore";

const HOUR = 60 * 60_000;

/**
 * A child's phone uploads its status at least every 10 minutes while Nour Time runs and the phone is
 * online (StatusThrottle.HEARTBEAT_MS on Android). Twelve hours without one means the phone is off or
 * offline, or Nour Time was stopped, perhaps reset from safe mode, which leaves nothing else behind.
 * Long enough that a phone switched off for the night doesn't alert every morning.
 */
export const QUIET_AFTER_MS = 12 * HOUR;

/** A phone silent for longer than this is gone for good (sold, wiped): no more pushes about it. */
export const QUIET_GIVE_UP_MS = 7 * 24 * HOUR;

/**
 * Whether a device whose last status upload was at [lastSeenMs] is newly quiet: past QUIET_AFTER_MS,
 * not given up on, and not already pushed about for this same silence ([alertedForMs] is the
 * lastSeenMs of the silence last pushed about).
 */
export function isNewQuietSpell(lastSeenMs: number | undefined, alertedForMs: number | undefined, nowMs: number): boolean {
  if (lastSeenMs == null) return false;
  const quietFor = nowMs - lastSeenMs;
  return quietFor >= QUIET_AFTER_MS && quietFor <= QUIET_GIVE_UP_MS && alertedForMs !== lastSeenMs;
}

/**
 * Records that the silence since [lastSeenMs] is being pushed about, once: overlapping runs of the
 * schedule can't push twice. False when it isn't new (pushState/{deviceId}.quietFor).
 */
export async function claimQuietSpell(db: Firestore, deviceId: string, lastSeenMs: number, nowMs: number): Promise<boolean> {
  const ref = db.doc(`pushState/${deviceId}`);
  return db.runTransaction(async (tx) => {
    const alertedFor = (await tx.get(ref)).data()?.quietFor as number | undefined;
    if (!isNewQuietSpell(lastSeenMs, alertedFor, nowMs)) return false;
    tx.set(ref, { quietFor: lastSeenMs }, { merge: true });
    return true;
  });
}

/** Undoes [claimQuietSpell] when the push failed, so the next run tries again. */
export async function releaseQuietSpell(db: Firestore, deviceId: string, lastSeenMs: number): Promise<void> {
  const ref = db.doc(`pushState/${deviceId}`);
  await db.runTransaction(async (tx) => {
    if ((await tx.get(ref)).data()?.quietFor === lastSeenMs) tx.update(ref, { quietFor: FieldValue.delete() });
  });
}

/** Paired devices whose last status upload falls in the quiet window, with when it was (ms). */
export async function quietDevices(db: Firestore, nowMs: number): Promise<Array<{ id: string; lastSeenMs: number }>> {
  const snap = await db
    .collection("devices")
    .where("status.updatedAt", "<=", new Date(nowMs - QUIET_AFTER_MS))
    .where("status.updatedAt", ">=", new Date(nowMs - QUIET_GIVE_UP_MS))
    .get();
  return snap.docs
    .filter((d) => d.get("ownerUid") != null)
    .map((d) => ({ id: d.id, lastSeenMs: d.get("status.updatedAt").toMillis() as number }));
}
