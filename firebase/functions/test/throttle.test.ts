import { describe, expect, it } from "vitest";
import { mayPush } from "../src/throttle.js";

describe("mayPush", () => {
  it("first push of a type is allowed", () => expect(mayPush("ASK", undefined, 0)).toBe(true));

  it("asks are at most every 5 minutes", () => {
    expect(mayPush("ASK", 0, 5 * 60_000 - 1)).toBe(false);
    expect(mayPush("ASK", 0, 5 * 60_000)).toBe(true);
  });

  it("alerts are at most every 30 minutes per type", () => {
    expect(mayPush("USB_DEBUGGING", 0, 30 * 60_000 - 1)).toBe(false);
    expect(mayPush("USB_DEBUGGING", 0, 30 * 60_000)).toBe(true);
  });

  it("a PIN reset waiting for approval is never held back", () => expect(mayPush("PIN_RESET_PENDING", 0, 1)).toBe(true));
});
