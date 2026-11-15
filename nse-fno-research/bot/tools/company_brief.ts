import { prompt } from "@grok/agent-sdk";
import { defineTool } from "@grok/agent-sdk/tools";
import * as z from "zod/v4";
import { resolveSymbol } from "../lib/market.js";
import { companyBrief } from "../lib/moneycontrol.js";

export default defineTool({
  description: prompt`
    Load Moneycontrol headlines and bulk deals from the last 5 calendar days for one NSE stock.
    Skip indexes such as NIFTY. Use only the headlines and deals this tool returns.
  `,
  inputSchema: z.object({
    symbol: z.string().min(1).max(32).describe("NSE stock ticker. Examples: RELIANCE, HDFCBANK, TCS."),
  }),
  effect: "read",
  async execute({ symbol }) {
    const resolved = resolveSymbol(symbol);
    if ("ok" in resolved) return resolved;
    if (resolved.kind === "index") {
      return {
        ok: true as const,
        applicable: false as const,
        symbol: resolved.input,
        reason: "An index does not have company news or bulk deals. Ask for an NSE stock symbol.",
      };
    }
    try {
      return await companyBrief(resolved.input);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Moneycontrol request failed.";
      return { ok: false as const, error: message };
    }
  },
});
