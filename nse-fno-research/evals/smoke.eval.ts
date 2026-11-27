import { defineEval, includes } from "@grok/agent-sdk/evals";

export default defineEval({
  tags: ["smoke"],
  description: "A stock research note loads technicals and published results.",
  async test(t) {
    await t.send(
      "Write an NSE F&O research note for RELIANCE. Cover published results, technical analysis, and the overall trend.",
    );
    t.succeeded();
    t.calledTool("market_technicals");
    t.calledTool("published_results");
    t.check(t.reply, includes(/RELIANCE/));
  },
});
