import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { logger } from "firebase-functions/v2";
import { onDocumentCreated, onDocumentWritten } from "firebase-functions/v2/firestore";
import { REGION } from "./config.js";
import { type AlertsDoc, newAlertEvents } from "./events.js";
import { pushToOwner, type Sender } from "./send.js";

initializeApp();
const db = getFirestore();

/** Data-only, high priority: the parent's phone builds the notification itself, in its own language. */
const fcm: Sender = {
  async send(tokens, data) {
    const r = await getMessaging().sendEachForMulticast({ tokens, data, android: { priority: "high", ttl: 3_600_000 } });
    return r.responses.map((x) => ({ ok: x.success, code: x.error?.code }));
  },
};

/** The child asked for more time. */
export const onTimeRequest = onDocumentCreated({ document: "devices/{id}/requests/{rid}", region: REGION }, async (event) => {
  const result = await pushToOwner(db, fcm, event.params.id, "ASK", {}, Date.now());
  logger.info("ask push", { device: event.params.id, result });
});

/** An alert appeared on the child's phone, or a new PIN waits for the parent's approval. */
export const onAlertsChanged = onDocumentWritten({ document: "devices/{id}/meta/alerts", region: REGION }, async (event) => {
  const before = event.data?.before.data() as AlertsDoc | undefined;
  const after = event.data?.after.data() as AlertsDoc | undefined;
  for (const type of newAlertEvents(before, after, Date.now())) {
    const extra: Record<string, string> = type === "PIN_RESET_PENDING" ? { pendingUntil: String(after?.pinResetPendingUntil) } : {};
    const result = await pushToOwner(db, fcm, event.params.id, type, extra, Date.now());
    logger.info("alert push", { device: event.params.id, type, result });
  }
});
