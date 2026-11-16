import { prompt } from "@grok/agent-sdk";
import { defineTool } from "@grok/agent-sdk/tools";
import * as z from "zod/v4";
import { oiPatternScan } from "../lib/optionchain.js";

export default defineTool({
  description: prompt`
    List NSE equity F&O names where call open interest was added and put open interest was unwound, or the reverse.
    Use when the user wants a high-level scan of that open-interest pattern.
    Quote only the symbols this tool returns. Do not add names, and do not turn the list into a recommendation.
  `,
  inputSchema: z.object({}),
  effect: "read",
  async execute() {
    try {
      return await oiPatternScan();
    } catch (error) {
      const message = error instanceof Error ? error.message : "NSE open-interest scan failed.";
      return { ok: false as const, error: message };
    }
  },
});
