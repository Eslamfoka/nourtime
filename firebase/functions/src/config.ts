import type { PushType } from "./events.js";

/** The Firestore database's location (firestore:databases:get, 2026-10-13): triggers run next to it. */
export const REGION = "me-central2";

const MINUTE = 60_000;

/** How long after a push of a type the same device gets another one of that type. */
export const MIN_GAP_MS: Record<PushType, number> = {
  ASK: 5 * MINUTE,
  MISSED_RESTART: 30 * MINUTE,
  OTHER_USER: 30 * MINUTE,
  USB_DEBUGGING: 30 * MINUTE,
  NOTIFICATIONS_OFF: 30 * MINUTE,
  PROTECTION_DEGRADED: 30 * MINUTE,
  // The parent should hear about a waiting PIN at once, every time.
  PIN_RESET_PENDING: 0,
  // Once per silence already (quiet.ts claimQuietSpell).
  QUIET: 0,
};
