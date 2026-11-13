// Public delayed quotes from Yahoo Finance.
// NSE's own site blocks this host, so the option chain is out of scope.

// Yahoo's quote-summary endpoint returns 429 for a short agent user agent.
const USER_AGENT =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";
const CHART_RANGE = "6mo";

export type Trend = "up" | "down" | "range";

export type Coverage = {
  source: "Yahoo Finance";
  priceDelay: string;
  notIncluded: string[];
};

const coverage: Coverage = {
  source: "Yahoo Finance",
  priceDelay: "Quotes can be delayed. They are not the NSE live feed.",
  notIncluded: [
    "option chain",
    "open interest",
    "put-call ratio",
    "futures basis",
    "lot size",
    "F&O ban list",
  ],
};

type IndexRef = { yahoo: string; label: string };

const INDEXES: Record<string, IndexRef> = {
  NIFTY: { yahoo: "^NSEI", label: "Nifty 50" },
  NIFTY50: { yahoo: "^NSEI", label: "Nifty 50" },
  "NIFTY 50": { yahoo: "^NSEI", label: "Nifty 50" },
  NSEI: { yahoo: "^NSEI", label: "Nifty 50" },
  BANKNIFTY: { yahoo: "^NSEBANK", label: "Nifty Bank" },
  "BANK NIFTY": { yahoo: "^NSEBANK", label: "Nifty Bank" },
  NSEBANK: { yahoo: "^NSEBANK", label: "Nifty Bank" },
  FINNIFTY: { yahoo: "NIFTY_FIN_SERVICE.NS", label: "Nifty Financial Services" },
  "FIN NIFTY": { yahoo: "NIFTY_FIN_SERVICE.NS", label: "Nifty Financial Services" },
  MIDCPNIFTY: { yahoo: "NIFTY_MID_SELECT.NS", label: "Nifty Midcap Select" },
  "MIDCAP NIFTY": { yahoo: "NIFTY_MID_SELECT.NS", label: "Nifty Midcap Select" },
  INDIAVIX: { yahoo: "^INDIAVIX", label: "India VIX" },
  "INDIA VIX": { yahoo: "^INDIAVIX", label: "India VIX" },
};

export type ResolvedSymbol = {
  input: string;
  yahoo: string;
  kind: "index" | "stock";
  label: string | null;
};

export type SymbolError = { ok: false; error: string };

export function resolveSymbol(input: string): ResolvedSymbol | SymbolError {
  const cleaned = input.trim().toUpperCase().replace(/\s+/g, " ");
  if (cleaned.length === 0 || cleaned.length > 32) {
    return { ok: false, error: "Pass an NSE ticker or index, such as RELIANCE or NIFTY." };
  }
  if (!/^[A-Z0-9.^& _-]+$/.test(cleaned)) {
    return { ok: false, error: "The symbol has characters this feed does not accept." };
  }
  if (cleaned.endsWith(".BO")) {
    return { ok: false, error: "This agent covers NSE. Use the NSE ticker, for example RELIANCE." };
  }
  const index = INDEXES[cleaned];
  if (index) {
    return { input: cleaned, yahoo: index.yahoo, kind: "index", label: index.label };
  }
  if (cleaned.startsWith("^")) {
    return { input: cleaned, yahoo: cleaned, kind: "index", label: null };
  }
  const yahoo = cleaned.includes(".") ? cleaned : `${cleaned}.NS`;
  return { input: cleaned, yahoo, kind: "stock", label: null };
}

type Session = { cookie: string; crumb: string; exp: number };

let session: Session | undefined;

function cookieHeader(response: Response): string {
  const headers = response.headers as Headers & { getSetCookie?: () => string[] };
  const lines =
    typeof headers.getSetCookie === "function" ? headers.getSetCookie() : [];
  const fallback = response.headers.get("set-cookie");
  const parts = lines.length > 0 ? lines : fallback ? [fallback] : [];
  return parts
    .map((line) => line.split(";")[0]?.trim() ?? "")
    .filter((part) => part.length > 0)
    .join("; ");
}

async function yahooSession(force = false): Promise<Session> {
  if (!force && session && session.exp > Date.now()) return session;
  const home = await fetch("https://fc.yahoo.com", {
    headers: { "user-agent": USER_AGENT },
    redirect: "manual",
    signal: AbortSignal.timeout(20_000),
  });
  const cookie = cookieHeader(home);
  if (!cookie) {
    throw new Error("Yahoo did not return a session cookie.");
  }
  const crumbRes = await fetch("https://query1.finance.yahoo.com/v1/test/getcrumb", {
    headers: { "user-agent": USER_AGENT, cookie, accept: "text/plain" },
    signal: AbortSignal.timeout(20_000),
  });
  const crumb = (await crumbRes.text()).trim();
  if (crumbRes.status !== 200 || crumb.length === 0 || crumb.startsWith("{") || crumb.includes("<")) {
    throw new Error(`Yahoo crumb request failed (${crumbRes.status}).`);
  }
  session = { cookie, crumb, exp: Date.now() + 10 * 60 * 1000 };
  return session;
}

async function fetchJson(url: string, headers?: Record<string, string>): Promise<{ status: number; body: unknown }> {
  const response = await fetch(url, {
    headers: { "user-agent": USER_AGENT, accept: "application/json", ...headers },
    signal: AbortSignal.timeout(20_000),
  });
  const text = await response.text();
  let body: unknown = null;
  if (text) {
    try {
      body = JSON.parse(text) as unknown;
    } catch {
      body = null;
    }
  }
  return { status: response.status, body };
}

function record(value: unknown): Record<string, unknown> | null {
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function num(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function str(value: unknown): string | null {
  return typeof value === "string" && value.length > 0 ? value : null;
}

function round(value: number, digits: number): number {
  const scale = 10 ** digits;
  return Math.round(value * scale) / scale;
}

function rawNumber(node: unknown): number | null {
  return num(record(node)?.raw);
}

function displayOf(node: unknown): string | null {
  return str(record(node)?.fmt);
}

function istStamp(unixSeconds: number): string {
  const date = new Date(unixSeconds * 1000);
  const day = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Kolkata",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(date);
  const time = new Intl.DateTimeFormat("en-GB", {
    timeZone: "Asia/Kolkata",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).format(date);
  return `${day} ${time} IST`;
}

function sma(values: number[], period: number): number | null {
  if (values.length < period) return null;
  const slice = values.slice(-period);
  const total = slice.reduce((sum, value) => sum + value, 0);
  return total / period;
}

function rsi(values: number[], period = 14): number | null {
  if (values.length < period + 1) return null;
  let gain = 0;
  let loss = 0;
  for (let i = 1; i <= period; i++) {
    const change = values[i] - values[i - 1];
    if (change >= 0) gain += change;
    else loss -= change;
  }
  let avgGain = gain / period;
  let avgLoss = loss / period;
  for (let i = period + 1; i < values.length; i++) {
    const change = values[i] - values[i - 1];
    const up = change > 0 ? change : 0;
    const down = change < 0 ? -change : 0;
    avgGain = (avgGain * (period - 1) + up) / period;
    avgLoss = (avgLoss * (period - 1) + down) / period;
  }
  if (avgLoss === 0) return 100;
  const relative = avgGain / avgLoss;
  return 100 - 100 / (1 + relative);
}

function percentChange(values: number[], sessions: number): number | null {
  if (values.length <= sessions) return null;
  const then = values[values.length - 1 - sessions];
  const now = values[values.length - 1];
  if (then === 0) return null;
  return ((now - then) / then) * 100;
}

function realizedVol(values: number[], window = 20): number | null {
  if (values.length < window + 1) return null;
  const slice = values.slice(-(window + 1));
  const returns: number[] = [];
  for (let i = 1; i < slice.length; i++) {
    const previous = slice[i - 1];
    const current = slice[i];
    if (previous <= 0 || current <= 0) return null;
    returns.push(Math.log(current / previous));
  }
  const mean = returns.reduce((sum, value) => sum + value, 0) / returns.length;
  const variance =
    returns.reduce((sum, value) => sum + (value - mean) ** 2, 0) / (returns.length - 1);
  return Math.sqrt(variance) * Math.sqrt(252) * 100;
}

function classifyTrend(
  close: number,
  sma20: number | null,
  sma50: number | null,
  return21d: number | null,
): { trend: Trend; trendDetail: string } {
  if (sma20 === null || sma50 === null || return21d === null) {
    return {
      trend: "range",
      trendDetail: "range: fewer than 50 daily sessions, so the trend rules do not apply",
    };
  }
  if (close > sma20 && sma20 > sma50 && return21d > 0) {
    return {
      trend: "up",
      trendDetail: "up: close above SMA20 above SMA50, and the 21-session return is positive",
    };
  }
  if (close < sma20 && sma20 < sma50 && return21d < 0) {
    return {
      trend: "down",
      trendDetail: "down: close below SMA20 below SMA50, and the 21-session return is negative",
    };
  }
  return {
    trend: "range",
    trendDetail: "range: price is not stacked with both moving averages in one direction",
  };
}

export type MarketTechnicals = {
  ok: true;
  symbol: string;
  yahooSymbol: string;
  name: string | null;
  kind: "index" | "stock";
  instrumentType: string | null;
  currency: string | null;
  asOf: string | null;
  sessions: number;
  price: number | null;
  dayChangePercent: number | null;
  dayHigh: number | null;
  dayLow: number | null;
  fiftyTwoWeekHigh: number | null;
  fiftyTwoWeekLow: number | null;
  sma20: number | null;
  sma50: number | null;
  rsi14: number | null;
  return5d: number | null;
  return21d: number | null;
  return63d: number | null;
  realizedVol20d: number | null;
  range20: { high: number | null; low: number | null };
  trend: Trend;
  trendDetail: string;
  corporateAction: string | null;
  coverage: Coverage;
};

export type ToolFailure = { ok: false; symbol?: string; error: string };

type Bar = { close: number; high: number | null; low: number | null; adj: number | null };

function parseBars(result: Record<string, unknown>): Bar[] {
  const indicators = record(result.indicators);
  const quoteList = indicators?.quote;
  const quote = Array.isArray(quoteList) ? record(quoteList[0]) : null;
  const closes = Array.isArray(quote?.close) ? quote.close : [];
  const highs = Array.isArray(quote?.high) ? quote.high : [];
  const lows = Array.isArray(quote?.low) ? quote.low : [];
  const adjList = indicators?.adjclose;
  const adjNode = Array.isArray(adjList) ? record(adjList[0]) : null;
  const adj = Array.isArray(adjNode?.adjclose) ? adjNode.adjclose : [];
  const bars: Bar[] = [];
  for (let i = 0; i < closes.length; i++) {
    const close = num(closes[i]);
    if (close === null) continue;
    bars.push({ close, high: num(highs[i]), low: num(lows[i]), adj: num(adj[i]) });
  }
  return bars;
}

export async function marketTechnicals(input: string): Promise<MarketTechnicals | ToolFailure> {
  const resolved = resolveSymbol(input);
  if ("ok" in resolved) return resolved;

  const url =
    "https://query1.finance.yahoo.com/v8/finance/chart/" +
    encodeURIComponent(resolved.yahoo) +
    `?interval=1d&range=${CHART_RANGE}&includePrePost=false`;
  const { status, body } = await fetchJson(url);
  const chart = record(record(body)?.chart);
  const error = record(chart?.error);
  if (status !== 200 || error) {
    const code = str(error?.code) ?? `HTTP ${status}`;
    return {
      ok: false,
      symbol: resolved.yahoo,
      error: `No daily chart for ${resolved.yahoo} (${code}). Use an NSE ticker such as RELIANCE, or NIFTY, BANKNIFTY, FINNIFTY, MIDCPNIFTY.`,
    };
  }
  const result = record(Array.isArray(chart?.result) ? chart.result[0] : null);
  if (!result) {
    return { ok: false, symbol: resolved.yahoo, error: `Yahoo returned an empty chart for ${resolved.yahoo}.` };
  }
  const meta = record(result.meta) ?? {};
  const bars = parseBars(result);
  const closes = bars.map((bar) => bar.close);
  const last = closes[closes.length - 1] ?? null;
  const lastAdj = bars[bars.length - 1]?.adj ?? null;
  const sma20 = last === null ? null : sma(closes, 20);
  const sma50 = last === null ? null : sma(closes, 50);
  const return21d = percentChange(closes, 21);
  const trend = last === null
    ? { trend: "range" as const, trendDetail: "range: the chart has no closes" }
    : classifyTrend(last, sma20, sma50, return21d);
  const recent = bars.slice(-20);
  const highs = recent.map((bar) => bar.high).filter((value): value is number => value !== null);
  const lows = recent.map((bar) => bar.low).filter((value): value is number => value !== null);
  const corporateAction =
    last !== null && lastAdj !== null && last !== 0 && Math.abs(lastAdj / last - 1) > 0.02
      ? "Adjusted and raw closes differ. A split or similar action may sit in this window. Treat the levels with care."
      : null;
  const asOfUnix = num(meta.regularMarketTime);

  return {
    ok: true,
    symbol: resolved.input,
    yahooSymbol: str(meta.symbol) ?? resolved.yahoo,
    name: str(meta.shortName) ?? str(meta.longName) ?? resolved.label,
    kind: resolved.kind,
    instrumentType: str(meta.instrumentType),
    currency: str(meta.currency),
    asOf: asOfUnix === null ? null : istStamp(asOfUnix),
    sessions: closes.length,
    price: num(meta.regularMarketPrice) ?? (last === null ? null : round(last, 2)),
    dayChangePercent: roundOrNull(num(meta.regularMarketChangePercent), 2),
    dayHigh: num(meta.regularMarketDayHigh),
    dayLow: num(meta.regularMarketDayLow),
    fiftyTwoWeekHigh: num(meta.fiftyTwoWeekHigh),
    fiftyTwoWeekLow: num(meta.fiftyTwoWeekLow),
    sma20: roundOrNull(sma20, 2),
    sma50: roundOrNull(sma50, 2),
    rsi14: roundOrNull(rsi(closes, 14), 1),
    return5d: roundOrNull(percentChange(closes, 5), 2),
    return21d: roundOrNull(return21d, 2),
    return63d: roundOrNull(percentChange(closes, 63), 2),
    realizedVol20d: roundOrNull(realizedVol(closes, 20), 1),
    range20: {
      high: highs.length === 0 ? null : round(Math.max(...highs), 2),
      low: lows.length === 0 ? null : round(Math.min(...lows), 2),
    },
    trend: trend.trend,
    trendDetail: trend.trendDetail,
    corporateAction,
    coverage,
  };
}

function roundOrNull(value: number | null, digits: number): number | null {
  return value === null ? null : round(value, digits);
}

export type EarningsQuarter = {
  quarter: string | null;
  epsActual: number | null;
  epsEstimate: number | null;
  surprisePercent: number | null;
};

export type PublishedResults = {
  ok: true;
  applicable: boolean;
  symbol: string;
  yahooSymbol: string;
  name: string | null;
  currency: string | null;
  mostRecentQuarter: string | null;
  quarterlyEarningsGrowthPercent: number | null;
  trailingEps: number | null;
  forwardPe: number | null;
  history: EarningsQuarter[];
  currentQuarter: {
    endDate: string | null;
    epsEstimate: number | null;
    earningsGrowthPercent: number | null;
    revenueGrowthPercent: number | null;
  } | null;
  nextEarnings: {
    date: string | null;
    isEstimate: boolean | null;
    epsEstimate: number | null;
    revenueEstimate: string | null;
  } | null;
  reason: string | null;
  note: string;
  coverage: Coverage;
};

function percentFromFraction(node: unknown): number | null {
  const value = rawNumber(node);
  return value === null ? null : round(value * 100, 2);
}

export async function publishedResults(input: string): Promise<PublishedResults | ToolFailure> {
  const resolved = resolveSymbol(input);
  if ("ok" in resolved) return resolved;
  if (resolved.kind === "index") {
    return {
      ok: true,
      applicable: false,
      symbol: resolved.input,
      yahooSymbol: resolved.yahoo,
      name: resolved.label,
      currency: null,
      mostRecentQuarter: null,
      quarterlyEarningsGrowthPercent: null,
      trailingEps: null,
      forwardPe: null,
      history: [],
      currentQuarter: null,
      nextEarnings: null,
      reason: "An index does not publish company results. Ask for an NSE stock symbol.",
      note: "Analyst figures are third-party estimates, not NSE filings.",
      coverage,
    };
  }

  let auth: Session;
  try {
    auth = await yahooSession();
  } catch (error) {
    const message = error instanceof Error ? error.message : "Yahoo session failed.";
    return { ok: false, symbol: resolved.yahoo, error: message };
  }

  const load = async (active: Session) => {
    const url = new URL("https://query1.finance.yahoo.com/v10/finance/quoteSummary/" + encodeURIComponent(resolved.yahoo));
    url.searchParams.set(
      "modules",
      "earningsHistory,earningsTrend,calendarEvents,defaultKeyStatistics,price",
    );
    url.searchParams.set("crumb", active.crumb);
    return fetchJson(url.toString(), { cookie: active.cookie });
  };

  let loaded = await load(auth);
  if (loaded.status === 401) {
    session = undefined;
    try {
      auth = await yahooSession(true);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Yahoo session failed.";
      return { ok: false, symbol: resolved.yahoo, error: message };
    }
    loaded = await load(auth);
  }

  const summary = record(record(loaded.body)?.quoteSummary);
  const error = record(summary?.error);
  const result = record(Array.isArray(summary?.result) ? summary.result[0] : null);
  if (loaded.status !== 200 || !result) {
    const code = str(error?.code) ?? `HTTP ${loaded.status}`;
    return {
      ok: false,
      symbol: resolved.yahoo,
      error: `No published-results feed for ${resolved.yahoo} (${code}).`,
    };
  }

  const price = record(result.price);
  const stats = record(result.defaultKeyStatistics);
  const historyNode = record(result.earningsHistory);
  const historyRows = Array.isArray(historyNode?.history) ? historyNode.history : [];
  const history: EarningsQuarter[] = historyRows.map((row) => {
    const item = record(row);
    return {
      quarter: displayOf(item?.quarter),
      epsActual: roundOrNull(rawNumber(item?.epsActual), 2),
      epsEstimate: roundOrNull(rawNumber(item?.epsEstimate), 2),
      surprisePercent: percentFromFraction(item?.surprisePercent),
    };
  });

  const trendRows = Array.isArray(record(result.earningsTrend)?.trend)
    ? (record(result.earningsTrend)?.trend as unknown[])
    : [];
  const current = trendRows
    .map((row) => record(row))
    .find((row) => row?.period === "0q");
  const earningsEstimate = record(current?.earningsEstimate);
  const revenueEstimate = record(current?.revenueEstimate);
  const calendar = record(record(result.calendarEvents)?.earnings);
  const nextDate = Array.isArray(calendar?.earningsDate) ? record(calendar.earningsDate[0]) : null;

  return {
    ok: true,
    applicable: true,
    symbol: resolved.input,
    yahooSymbol: str(price?.symbol) ?? resolved.yahoo,
    name: str(price?.shortName) ?? str(price?.longName),
    currency: str(price?.currency),
    mostRecentQuarter: displayOf(stats?.mostRecentQuarter),
    quarterlyEarningsGrowthPercent: percentFromFraction(stats?.earningsQuarterlyGrowth),
    trailingEps: roundOrNull(rawNumber(stats?.trailingEps), 2),
    forwardPe: roundOrNull(rawNumber(stats?.forwardPE), 2),
    history,
    currentQuarter: current
      ? {
          endDate: str(current.endDate),
          epsEstimate: roundOrNull(rawNumber(earningsEstimate?.avg), 2),
          earningsGrowthPercent: percentFromFraction(earningsEstimate?.growth),
          revenueGrowthPercent: percentFromFraction(revenueEstimate?.growth),
        }
      : null,
    nextEarnings: calendar
      ? {
          date: str(nextDate?.fmt),
          isEstimate: typeof calendar.isEarningsDateEstimate === "boolean" ? calendar.isEarningsDateEstimate : null,
          epsEstimate: roundOrNull(rawNumber(calendar.earningsAverage), 2),
          revenueEstimate: displayOf(calendar.revenueAverage),
        }
      : null,
    reason: null,
    note: "Analyst figures are third-party estimates, not NSE filings. Surprise is the percent gap versus the Yahoo estimate.",
    coverage,
  };
}
