import { prompt } from "@grok/agent-sdk";
import { defineTool } from "@grok/agent-sdk/tools";
import * as z from "zod/v4";
import { marketTechnicals } from "../lib/market.js";

export default defineTool({
  description: prompt`
    Load delayed price, technicals, and a trend label for one NSE underlying.
    Use for NIFTY, BANKNIFTY, FINNIFTY, MIDCPNIFTY, INDIAVIX, or a stock ticker such as RELIANCE.
    The trend field is the bias. dayLevels holds the previous session's high, low, close, pivot, and the nearest support and resistance. Quote those numbers. Do not invent levels.
  `,
  inputSchema: z.object({
    symbol: z
      .string()
      .min(1)
      .max(32)
      .describe("NSE ticker or index name. Examples: RELIANCE, NIFTY, BANKNIFTY."),
  }),
  effect: "read",
  async execute({ symbol }) {
    try {
      return await marketTechnicals(symbol);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Market data request failed.";
      return { ok: false as const, error: message };
    }
  },
});
