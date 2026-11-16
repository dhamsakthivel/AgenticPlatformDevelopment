---
description: Use when the user wants an NSE F&O research note, a trend view, or a comparison of underlyings.
---

# F&O research note

1. If the user names no symbol, ask for one. Indexes: NIFTY, BANKNIFTY, FINNIFTY, MIDCPNIFTY. Stocks use the NSE ticker, such as RELIANCE.
2. Call `market_technicals` once per symbol.
3. Call `published_results` when the symbol is a stock, or when the user asks about results or earnings. Do not call it for an index.
4. Call `company_brief` for a stock. Do not call it for an index.
5. Call `stage_screen` for a stock. Do not call it for an index.
6. Call `option_chain` for a stock and for NIFTY, BANKNIFTY, FINNIFTY, and MIDCPNIFTY. Skip it for INDIAVIX.
7. Write the note in this shape. Use only tool figures.

## <name> (<symbol>)

Bias: <trend>
As of: <asOf>. <priceDelay>

Last: <price> <currency> (<dayChangePercent>% on the day)
Range: day <dayLow>–<dayHigh>, 20 sessions <range20.low>–<range20.high>, 52-week <fiftyTwoWeekLow>–<fiftyTwoWeekHigh>
Day levels: from `dayLevels`, name the session date, nearest support, nearest resistance, pivot, Support 1, and Resistance 1. If `dayLevels` is null, say the prior session is missing.
Technicals: SMA20 <sma20>, SMA50 <sma50>, RSI14 <rsi14>, 5-session return <return5d>%, 21-session return <return21d>%, 63-session return <return63d>%, 20-session realized vol <realizedVol20d>%
Results: for a stock, list each quarter with EPS actual, EPS estimate, and surprisePercent, then the next earnings date and whether that date is an estimate. For an index, say company results do not apply.
News: list each Moneycontrol headline with its date. Do not add headlines the tool did not return.
Stage 2: state `verdict` and `verdictDetail`. List each failed template rule. If the template passes, list each failed earnings rule. If both pass, state whether the entry is a fresh breakout. Do not add a rule the tool did not return.
Open interest: state `bias` and `biasDetail` first, then the expiry, as-of time, call OI, put OI, PCR, and the at-the-money strike. List only the strikes the tool returned. If the tool says the chain does not apply, say so.
Bulk deals: list each Moneycontrol bulk deal inside `bulkWindow` with client, side, quantity, and price. If `bulkDeals` is empty, say Moneycontrol shows none in that window.
Large deals: list each Moneycontrol large deal inside `bulkWindow` with time, exchange, quantity, price, and value in rupee crore. If there are more than 8, state the count and list the 8 largest by `valueCrore`. If `largeDeals` is empty, say Moneycontrol shows none in that window. If `largeError` is set, quote it.
Outside this feed: futures basis, lot size, and the F&O ban list.
What would change the view: one sentence tied to SMA20 or the 20-session range.

This is research context, not investment advice or a trade recommendation.

8. If `corporateAction` is set, quote it before the levels.
9. Compare symbols only after `market_technicals` has returned for each one.
10. Call `market_technicals` for INDIAVIX only when the user asks about volatility.
