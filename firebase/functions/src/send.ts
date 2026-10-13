import type { Firestore } from "firebase-admin/firestore";
import type { PushType } from "./events.js";
import { mayPush } from "./throttle.js";

/** Sends one data message to each token; one result per token, in order (FCM in production, a fake in tests). */
export interface Sender {
  send(tokens: string[], data: Record<string, string>): Promise<Array<{ ok: boolean; code?: string }>>;
}

/** FCM's answers for a token that will never work again (app uninstalled, data cleared). */
const DEAD = new Set(["messaging/registration-token-not-registered", "messaging/invalid-registration-token"]);

/**
 * One push to every phone of the device's parent, at most as often as MIN_GAP_MS allows (the last send
 * per type is kept in pushState/{deviceId}, which only these functions can read or write).
 */
export async function pushToOwner(
  db: Firestore,
  sender: Sender,
  deviceId: string,
  type: PushType,
  extra: Record<string, string>,
  nowMs: number,
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
  await Promise.all(
    results.map((r, i) =>
      !r.ok && r.code && DEAD.has(r.code) ? db.doc(`parents/${ownerUid}/tokens/${tokens[i]}`).delete() : null,
    ),
  );
  return "sent";
}
