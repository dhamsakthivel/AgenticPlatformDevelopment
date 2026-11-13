import { defineAgent } from "@grok/agent-sdk";

export default defineAgent({
  name: "NSE F&O research",
  description:
    "Research notes for NSE futures and options underlyings: published results, technicals, and trend.",
  model: {
    id: "grok-4.7",
    params: [
      { id: "reasoning_effort", value: "high" },
      { id: "fast", value: "true" },
    ],
  },
  tools: ["read", "grep"],
  local: {
    sandbox: true,
  },
  hosting: {
    egressDomains: ["query1.finance.yahoo.com", "fc.yahoo.com"],
  },
});
