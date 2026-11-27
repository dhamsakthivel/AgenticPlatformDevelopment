// 52-week high and low for every name in the NSE F&O book.
// The levels are the Yahoo cash quote. They are a research reference, not an order.

import { fnoBook } from "./morning";

const USER_AGENT =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
const BATCH = 40;

export const RANGE_RULE = 1;

export type RangeRow = {
  symbol: string;
  name: string;
  sector: string;
  price: number | null;
  high: number | null;
  low: number | null;
  fromHigh: number | null;
  fromLow: number | null;
};

export type RangeReport = {
  ok: true;
  ready: true;
  ruleVersion: number;
  builtAt: string;
  asOf: string | null;
  scanned: number;
  missed: number;
  rows: RangeRow[];
  note: string;
};

export type RangeFailure = { ok: false; error: string };

type Session = { cookie: string; crumb: string; exp: number };
type Quote = { price: number | null; high: number | null; low: number | null; time: number | null };

let session: Session | null = null;

function cookieHeader(response: Response): string {
  const headers = response.headers as Headers & { getSetCookie?: () => string[] };
  const lines = typeof headers.getSetCookie === "function" ? headers.getSetCookie() : [];
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
  if (!cookie) throw new Error("Yahoo did not return a session cookie.");
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

function round(value: number): number {
  return Math.round(value * 100) / 100;
}

function asNumber(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function istStamp(unixSeconds: number): string {
  const date = new Date(unixSeconds * 1000);
  const day = new Intl.DateTimeFormat("en-GB", {
    timeZone: "Asia/Kolkata",
    day: "numeric",
    month: "short",
    year: "numeric",
  }).format(date);
  const time = new Intl.DateTimeFormat("en-GB", {
    timeZone: "Asia/Kolkata",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).format(date);
  return `${day}, ${time} IST`;
}

async function loadQuotes(symbols: string[], attempt = 0): Promise<Map<string, Quote>> {
  const auth = await yahooSession(attempt > 0);
  const url = new URL("https://query1.finance.yahoo.com/v7/finance/quote");
  url.searchParams.set("symbols", symbols.join(","));
  url.searchParams.set("crumb", auth.crumb);
  const response = await fetch(url, {
    headers: { "user-agent": USER_AGENT, accept: "application/json", cookie: auth.cookie },
    signal: AbortSignal.timeout(20_000),
  });
  if (response.status === 401 && attempt < 1) return loadQuotes(symbols, attempt + 1);
  if (!response.ok) throw new Error(`Yahoo quote returned HTTP ${response.status}.`);
  const body = await response.json() as { quoteResponse?: { result?: unknown[] } };
  const result = body.quoteResponse?.result;
  if (!Array.isArray(result)) throw new Error("Yahoo quote returned no book.");
  const out = new Map<string, Quote>();
  for (const item of result) {
    if (!item || typeof item !== "object") continue;
    const row = item as Record<string, unknown>;
    const symbol = typeof row.symbol === "string" ? row.symbol.replace(/\.NS$/i, "") : "";
    if (!symbol) continue;
    out.set(symbol, {
      price: asNumber(row.regularMarketPrice),
      high: asNumber(row.fiftyTwoWeekHigh),
      low: asNumber(row.fiftyTwoWeekLow),
      time: asNumber(row.regularMarketTime),
    });
  }
  return out;
}

export async function yearRangeReport(): Promise<RangeReport | RangeFailure> {
  try {
    const book = fnoBook();
    const quotes = new Map<string, Quote>();
    for (let index = 0; index < book.length; index += BATCH) {
      const batch = await loadQuotes(book.slice(index, index + BATCH).map((name) => `${name.symbol}.NS`));
      for (const [symbol, quote] of batch) quotes.set(symbol, quote);
    }
    const rows: RangeRow[] = [];
    let missed = 0;
    let latest = 0;
    for (const name of book) {
      const quote = quotes.get(name.symbol);
      if (!quote || quote.high === null || quote.low === null) {
        missed += 1;
        continue;
      }
      if (quote.time !== null && quote.time > latest) latest = quote.time;
      const price = quote.price === null ? null : round(quote.price);
      rows.push({
        symbol: name.symbol,
        name: name.match,
        sector: name.sector,
        price,
        high: round(quote.high),
        low: round(quote.low),
        fromHigh: price !== null && quote.high > 0 ? round((price / quote.high - 1) * 100) : null,
        fromLow: price !== null && quote.low > 0 ? round((price / quote.low - 1) * 100) : null,
      });
    }
    rows.sort((a, b) => a.symbol.localeCompare(b.symbol));
    if (rows.length === 0) return { ok: false, error: "The 52-week book did not return a high and low." };
    return {
      ok: true,
      ready: true,
      ruleVersion: RANGE_RULE,
      builtAt: new Date().toISOString(),
      asOf: latest > 0 ? istStamp(latest) : null,
      scanned: book.length,
      missed,
      rows,
      note: "52-week high and 52-week low are the cash quote for every NSE F&O name. From high is the last price versus that high. From low is the last price versus that low. These levels are a research reference, not an order.",
    };
  } catch (error) {
    const message = error instanceof Error ? error.message : "The 52-week book did not load.";
    return { ok: false, error: message };
  }
}
