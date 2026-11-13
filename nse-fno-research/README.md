# NSE F&O research

This agent writes a short research note for an NSE futures and options underlying.

It does three things:

- It reads delayed prices for the underlying and computes the trend.
- It computes SMA20, SMA50, RSI14, recent returns, realized volatility, and ranges.
- It loads published quarterly EPS and the next earnings date for a stock.

The feed is Yahoo Finance. NSE's own site blocks this host, so the note does not include the option chain, open interest, put-call ratio, futures basis, lot size, or the F&O ban list.

Supported index names: `NIFTY`, `BANKNIFTY`, `FINNIFTY`, `MIDCPNIFTY`, `INDIAVIX`. Stocks use the NSE ticker, such as `RELIANCE`.

## Get started

1. Open a terminal in this folder.
2. Run `npx --yes "@grok/agent-sdk@0.6.0" login` if you are not signed in to Cursor.
3. Run `npx --yes "@grok/agent-sdk@0.6.0" dev`.
4. Open the playground URL the command prints.
5. Ask: `Write an NSE F&O research note for RELIANCE. Cover published results, technicals, and the trend.`

Other prompts:

- `What is the trend in NIFTY and BANKNIFTY?`
- `Write a research note for HDFCBANK.`
- `How do the last four TCS results sit against the price trend?`

## Check the project

```bash
npx --yes "@grok/agent-sdk@0.6.0" validate --dir .
npx tsc --noEmit -p .
npx --yes "@grok/agent-sdk@0.6.0" tool call market_technicals --input '{"symbol":"NIFTY"}'
npx --yes "@grok/agent-sdk@0.6.0" eval --dir .
```

`eval` and `dev` need a Cursor credential. `validate` and `tool call` do not.

## Deploy

Deploy from your own terminal so the alias token is not printed into a shared log. See the Agent SDK deploy guide. Set egress to `query1.finance.yahoo.com` and `fc.yahoo.com` (already declared on the agent).
