import { prompt } from "@grok/agent-sdk";
import { defineTool } from "@grok/agent-sdk/tools";
import * as z from "zod/v4";
import { resolveSymbol } from "../lib/market.js";
import { stageScreenFor } from "../lib/stage2.js";

export default defineTool({
  description: prompt`
    Score one NSE stock on the Stage 2 trend template, earnings acceleration, and a tight-base breakout.
    One failed template rule takes the stock out. Skip indexes such as NIFTY.
    Quote only the rules this tool returns. Do not turn the verdict into a trade recommendation.
  `,
  inputSchema: z.object({
    symbol: z.string().min(1).max(32).describe("NSE stock ticker. Examples: RELIANCE, HDFCBANK, TCS."),
  }),
  effect: "read",
  async execute({ symbol }) {
    const resolved = resolveSymbol(symbol);
    if ("ok" in resolved) return resolved;
    try {
      return await stageScreenFor(resolved);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Stage 2 screen failed.";
      return { ok: false as const, error: message };
    }
  },
});
