/** The devices/{id}/meta/alerts document the child's phone writes (ProtectionAlerts.toMap on Android). */
export interface AlertsDoc {
  missedRestartAt?: number | null;
  otherUserAt?: number | null;
  usbDebugging?: boolean;
  notificationsOff?: boolean;
  pinResetPendingUntil?: number | null;
  protectionDegraded?: boolean;
}

/** What a push is about; the alert names match ProtectionAlert on Android. */
export type PushType =
  | "ASK"
  | "MISSED_RESTART"
  | "OTHER_USER"
  | "USB_DEBUGGING"
  | "NOTIFICATIONS_OFF"
  | "PROTECTION_DEGRADED"
  | "PIN_RESET_PENDING";

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
