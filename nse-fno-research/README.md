# NSE F&O research

This agent writes a short research note for an NSE futures and options underlying.

It does three things:

- It reads delayed prices for the underlying and computes the trend.
- It computes SMA20, SMA50, RSI14, recent returns, realized volatility, and ranges.
- It loads published quarterly EPS and the next earnings date for a stock.

Prices come from Yahoo Finance. Open interest for the nearest expiry comes from the NSE option-chain page when you load a symbol. Futures basis, lot size, and the F&O ban list are outside this feed.

Supported index names: `NIFTY`, `BANKNIFTY`, `FINNIFTY`, `MIDCPNIFTY`, `INDIAVIX`. Stocks use the NSE ticker, such as `RELIANCE`.

## Get started

1. Open a terminal in this folder.
2. Run `npm run desk`.
3. Open http://127.0.0.1:4173.
4. Load a symbol. The board shows the quote, a Stage 2 screen, NSE open interest, results, Moneycontrol headlines, bulk deals, and large deals from the last 5 days.
5. The desk opens on **Research gate**: the top 3 new shorts and the top 3 new longs from the last three sessions. A name needs that reading on at least two sessions. Each day shows the close against the previous session. **Direction** is the full list. A new short is a lower close with open interest up. A new long is a higher close with open interest up. Choose **Morning** for the 08:45 IST weekday brief. It ranks up to three F&O names in each sector from the previous session, with the last three daily moves, NSE bulk and block deals, and headlines from Economic Times, Moneycontrol, Business Standard, and Mint. **Premarket** is the 09:10 IST auction. A buy there has today's bid and ask above the previous close, with more shares on the bid. A sell has both below that close, with more shares on the ask. **F&O scan** is the open-interest list. **52-week** lists the 52-week high and the 52-week low for every F&O name, with the last price against each level.
6. Choose **Write the note** for the agent's written research. That step needs `npx --yes "@grok/agent-sdk@0.6.0" login`.

Stop an old desk with Ctrl+C before you start it again. `npm run desk` must run inside this folder, not in your home directory.

The desk listens on `127.0.0.1:4173` and starts the agent on `127.0.0.1:3921` when it is not already running. Set `DESK_PORT`, `AGENT_PORT`, or `AGENT_URL` to use other addresses.

The generic playground is still available:

1. Run `npx --yes "@grok/agent-sdk@0.6.0" login` if you are not signed in to Cursor.
2. Run `npx --yes "@grok/agent-sdk@0.6.0" dev`.
3. Open the playground URL the command prints.

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
