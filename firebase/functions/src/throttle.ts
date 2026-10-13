import { MIN_GAP_MS } from "./config.js";
import type { PushType } from "./events.js";

/** Whether a push of [type] may go out now, given when the last one of that type went out for the device. */
export function mayPush(type: PushType, lastSentMs: number | undefined, nowMs: number): boolean {
  return lastSentMs == null || nowMs - lastSentMs >= MIN_GAP_MS[type];
}
