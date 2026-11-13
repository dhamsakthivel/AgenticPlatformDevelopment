import { prompt } from "@grok/agent-sdk";
import { defineTool } from "@grok/agent-sdk/tools";
import * as z from "zod/v4";
import { marketTechnicals } from "../lib/market.js";

export default defineTool({
  description: prompt`
    Load delayed price, technicals, and a trend label for one NSE underlying.
    Use for NIFTY, BANKNIFTY, FINNIFTY, MIDCPNIFTY, INDIAVIX, or a stock ticker such as RELIANCE.
    The trend field is the bias. Quote its numbers. Do not invent option-chain data.
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
