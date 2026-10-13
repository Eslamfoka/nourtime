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
