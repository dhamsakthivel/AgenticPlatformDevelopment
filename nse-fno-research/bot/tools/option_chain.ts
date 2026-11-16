import { prompt } from "@grok/agent-sdk";
import { defineTool } from "@grok/agent-sdk/tools";
import * as z from "zod/v4";
import { resolveSymbol } from "../lib/market.js";
import { optionChainFor } from "../lib/optionchain.js";

export default defineTool({
  description: prompt`
    Load the latest NSE option-chain open interest for one underlying.
    Use for NIFTY, BANKNIFTY, FINNIFTY, MIDCPNIFTY, or a stock ticker such as RELIANCE.
    Quote the bias, the totals, the PCR, and the strikes this tool returns. Do not invent a chain.
  `,
  inputSchema: z.object({
    symbol: z.string().min(1).max(32).describe("NSE ticker or index. Examples: RELIANCE, NIFTY, BANKNIFTY."),
  }),
  effect: "read",
  async execute({ symbol }) {
    const resolved = resolveSymbol(symbol);
    if ("ok" in resolved) return resolved;
    try {
      return await optionChainFor(resolved);
    } catch (error) {
      const message = error instanceof Error ? error.message : "NSE option chain request failed.";
      return { ok: false as const, error: message };
    }
  },
});
