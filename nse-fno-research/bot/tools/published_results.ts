import { prompt } from "@grok/agent-sdk";
import { defineTool } from "@grok/agent-sdk/tools";
import * as z from "zod/v4";
import { publishedResults } from "../lib/market.js";

export default defineTool({
  description: prompt`
    Load published quarterly results and the next earnings date for one NSE stock.
    Skip indexes such as NIFTY and BANKNIFTY: they do not publish company results.
    Figures are third-party estimates and reported EPS, not an NSE filing download.
  `,
  inputSchema: z.object({
    symbol: z
      .string()
      .min(1)
      .max(32)
      .describe("NSE stock ticker. Examples: RELIANCE, HDFCBANK, TCS."),
  }),
  effect: "read",
  async execute({ symbol }) {
    try {
      return await publishedResults(symbol);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Results request failed.";
      return { ok: false as const, error: message };
    }
  },
});
