// Open interest from the NSE option-chain page.
// https://www.nseindia.in/option-chain
// The chain is fetched when the symbol is loaded. It is the latest payload that page returns.

const USER_AGENT =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";
const PAGE = "https://www.nseindia.in/option-chain";
const API = "https://www.nseindia.in/api";

const INDEX_CHAIN: Record<string, string> = {
  "^NSEI": "NIFTY",
  "^NSEBANK": "BANKNIFTY",
  "NIFTY_FIN_SERVICE.NS": "FINNIFTY",
  "NIFTY_MID_SELECT.NS": "MIDCPNIFTY",
};

export type ChainTarget = {
  input: string;
  yahoo: string;
  kind: "index" | "stock";
};

export type ChainStrike = {
  strike: number;
  atTheMoney: boolean;
  callOi: number | null;
  callChange: number | null;
  callIv: number | null;
  putOi: number | null;
  putChange: number | null;
  putIv: number | null;
};

export type OptionChain = {
  ok: true;
  applicable: true;
  symbol: string;
  nseSymbol: string;
  expiry: string;
  expiries: string[];
  asOf: string | null;
  underlying: number | null;
  callOi: number;
  putOi: number;
  callChange: number;
  putChange: number;
  pcr: number | null;
  bias: "buy" | "sell" | "neutral";
  biasDetail: string;
  maxCallStrike: number | null;
  maxCallOi: number | null;
  maxPutStrike: number | null;
  maxPutOi: number | null;
  strikes: ChainStrike[];
  source: "https://www.nseindia.in/option-chain";
  note: string;
};

type NotListed = { ok: true; applicable: false; symbol: string; reason: string };
type ChainFailure = { ok: false; symbol?: string; error: string };

type Session = { cookie: string; exp: number };
let session: Session | undefined;

function record(value: unknown): Record<string, unknown> | null {
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function num(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function str(value: unknown): string | null {
  return typeof value === "string" && value.trim().length > 0 ? value.trim() : null;
}

function strings(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return value.filter((item): item is string => typeof item === "string" && item.length > 0);
}

const PAGE_HEADERS = {
  "user-agent": USER_AGENT,
  accept: "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
  "accept-language": "en-IN,en;q=0.9",
  "upgrade-insecure-requests": "1",
  "sec-fetch-dest": "document",
  "sec-fetch-mode": "navigate",
  "sec-fetch-site": "none",
  "sec-fetch-user": "?1",
};

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

function rememberCookies(jar: Map<string, string>, response: Response): void {
  for (const part of cookieHeader(response).split(";")) {
    const trimmed = part.trim();
    const eq = trimmed.indexOf("=");
    if (eq > 0) jar.set(trimmed.slice(0, eq), trimmed.slice(eq + 1));
  }
}

function cookieValue(jar: Map<string, string>): string {
  return [...jar.entries()].map(([key, value]) => `${key}=${value}`).join("; ");
}

async function readPage(jar: Map<string, string>, url: string): Promise<number> {
  let current = url;
  let status = 0;
  for (let hop = 0; hop < 4; hop += 1) {
    const response = await fetch(current, {
      headers: {
        ...PAGE_HEADERS,
        ...(jar.size > 0 ? { cookie: cookieValue(jar) } : {}),
      },
      redirect: "manual",
      signal: AbortSignal.timeout(12_000),
    });
    status = response.status;
    rememberCookies(jar, response);
    const location = response.headers.get("location");
    if (!location || status < 300 || status >= 400) return status;
    current = new URL(location, current).href;
  }
  return status;
}

function usable(jar: Map<string, string>): boolean {
  return jar.has("nsit") || jar.has("AKA_A2");
}

async function nseSession(force = false): Promise<string> {
  if (!force && session && session.exp > Date.now()) return session.cookie;
  const jar = new Map<string, string>();
  let status = await readPage(jar, PAGE);
  if (!usable(jar)) status = await readPage(jar, "https://www.nseindia.in/");
  if (!usable(jar)) status = await readPage(jar, "https://www.nseindia.com/option-chain");
  if (usable(jar)) {
    const cookie = cookieValue(jar);
    session = { cookie, exp: Date.now() + 5 * 60 * 1000 };
    return cookie;
  }
  throw new Error(`NSE option-chain page returned HTTP ${status || 403}.`);
}

async function nseGet(path: string, cookie?: string, attempt = 0): Promise<unknown> {
  const jar = cookie ?? (await nseSession());
  let response: Response;
  try {
    response = await fetch(`${API}${path}`, {
      headers: {
        "user-agent": USER_AGENT,
        accept: "application/json, text/plain, */*",
        "accept-language": "en-IN,en;q=0.9",
        referer: PAGE,
        "x-requested-with": "XMLHttpRequest",
        cookie: jar,
      },
      signal: AbortSignal.timeout(12_000),
    });
  } catch (error) {
    if (attempt < 1) {
      session = undefined;
      return nseGet(path, await nseSession(true), attempt + 1);
    }
    throw error;
  }
  if ((response.status === 401 || response.status === 403) && attempt < 1) {
    session = undefined;
    return nseGet(path, await nseSession(true), attempt + 1);
  }
  if (!response.ok) {
    throw new Error(`NSE option chain returned HTTP ${response.status}.`);
  }
  return response.json() as Promise<unknown>;
}

function nseSymbol(target: ChainTarget): string | null {
  if (target.kind === "index") return INDEX_CHAIN[target.yahoo] ?? null;
  const ticker = target.yahoo.replace(/\.NS$/i, "").trim();
  return ticker.length > 0 ? ticker : null;
}

function side(node: unknown): { oi: number | null; change: number | null; iv: number | null } {
  const item = record(node);
  if (!item) return { oi: null, change: null, iv: null };
  return {
    oi: num(item.openInterest),
    change: num(item.changeinOpenInterest),
    iv: num(item.impliedVolatility),
  };
}

function round(value: number, digits: number): number {
  const scale = 10 ** digits;
  return Math.round(value * scale) / scale;
}

function movePhrase(callChange: number, putChange: number): string {
  if (putChange > callChange) {
    if (putChange > 0 && callChange < 0) return "Puts were added and calls were unwound";
    if (callChange < 0 && putChange < 0) return "Calls were unwound more than puts";
    return "Puts were added ahead of calls";
  }
  if (callChange > 0 && putChange < 0) return "Calls were added and puts were unwound";
  if (callChange < 0 && putChange < 0) return "Puts were unwound more than calls";
  return "Calls were added ahead of puts";
}

function oiBias(callChange: number, putChange: number, pcr: number | null): { bias: OptionChain["bias"]; biasDetail: string } {
  const scale = Math.max(Math.abs(callChange), Math.abs(putChange), 1);
  const tilt = (putChange - callChange) / scale;
  const pcrText = pcr === null ? "The put-call ratio is unavailable." : `The put-call ratio is ${pcr.toFixed(2)}.`;
  const move = movePhrase(callChange, putChange);
  if (tilt >= 0.15 && (pcr === null || pcr >= 0.7)) {
    return { bias: "buy", biasDetail: `Buy. ${move}. ${pcrText}` };
  }
  if (tilt <= -0.15 && (pcr === null || pcr <= 1.2)) {
    return { bias: "sell", biasDetail: `Sell. ${move}. ${pcrText}` };
  }
  if (tilt >= 0.15 || tilt <= -0.15) {
    return {
      bias: "neutral",
      biasDetail: `Neutral. ${move}, and ${pcrText.charAt(0).toLowerCase()}${pcrText.slice(1)} That ratio does not confirm it.`,
    };
  }
  return {
    bias: "neutral",
    biasDetail: `Neutral. Call and put open-interest changes are within 15% of each other. ${pcrText}`,
  };
}

function summarize(rows: unknown[], underlying: number | null): {
  callOi: number;
  putOi: number;
  callChange: number;
  putChange: number;
  maxCallStrike: number | null;
  maxCallOi: number | null;
  maxPutStrike: number | null;
  maxPutOi: number | null;
  strikes: ChainStrike[];
} {
  let callOi = 0;
  let putOi = 0;
  let callChange = 0;
  let putChange = 0;
  let maxCall = -1;
  let maxPut = -1;
  let maxCallStrike: number | null = null;
  let maxCallOi: number | null = null;
  let maxPutStrike: number | null = null;
  let maxPutOi: number | null = null;
  const parsed: { strike: number; callOi: number | null; callChange: number | null; callIv: number | null; putOi: number | null; putChange: number | null; putIv: number | null }[] = [];
  for (const row of rows) {
    const item = record(row);
    const strike = num(item?.strikePrice);
    if (strike === null) continue;
    const call = side(item?.CE);
    const put = side(item?.PE);
    if (call.oi !== null) {
      callOi += call.oi;
      if (call.oi > maxCall) {
        maxCall = call.oi;
        maxCallStrike = strike;
        maxCallOi = call.oi;
      }
    }
    if (put.oi !== null) {
      putOi += put.oi;
      if (put.oi > maxPut) {
        maxPut = put.oi;
        maxPutStrike = strike;
        maxPutOi = put.oi;
      }
    }
    callChange += call.change ?? 0;
    putChange += put.change ?? 0;
    parsed.push({
      strike,
      callOi: call.oi,
      callChange: call.change,
      callIv: call.iv,
      putOi: put.oi,
      putChange: put.change,
      putIv: put.iv,
    });
  }
  parsed.sort((a, b) => a.strike - b.strike);
  let atm = 0;
  if (parsed.length > 0) {
    const spot = underlying !== null && underlying > 0 ? underlying : parsed[Math.floor(parsed.length / 2)].strike;
    let best = Infinity;
    parsed.forEach((row, index) => {
      const gap = Math.abs(row.strike - spot);
      if (gap < best) {
        best = gap;
        atm = index;
      }
    });
  }
  const from = Math.max(0, atm - 4);
  const slice = parsed.slice(from, from + 9);
  return {
    callOi,
    putOi,
    callChange,
    putChange,
    maxCallStrike,
    maxCallOi,
    maxPutStrike,
    maxPutOi,
    strikes: slice.map((row) => ({ ...row, atTheMoney: row.strike === parsed[atm]?.strike })),
  };
}

export async function optionChainFor(target: ChainTarget): Promise<OptionChain | NotListed | ChainFailure> {
  const symbol = nseSymbol(target);
  if (!symbol) {
    return {
      ok: true,
      applicable: false,
      symbol: target.input,
      reason: "This symbol has no equity or index option chain on the NSE option-chain page.",
    };
  }
  const type = target.kind === "index" ? "Indices" : "Equity";
  try {
    let cookie = await nseSession();
    const loadInfo = async () => nseGet(`/option-chain-contract-info?symbol=${encodeURIComponent(symbol)}`, cookie);
    let info: unknown;
    try {
      info = await loadInfo();
    } catch {
      session = undefined;
      cookie = await nseSession(true);
      info = await loadInfo();
    }
    const expiries = strings(record(info)?.expiryDates);
    if (expiries.length === 0) {
      return { ok: false, symbol, error: `NSE lists no option expiries for ${symbol}.` };
    }
    const expiry = expiries[0];
    const chain = await nseGet(
      `/option-chain-v3?type=${type}&symbol=${encodeURIComponent(symbol)}&expiry=${encodeURIComponent(expiry)}`,
      cookie,
    );
    const records = record(record(chain)?.records);
    const rows = Array.isArray(records?.data) ? records.data : [];
    if (rows.length === 0) {
      return { ok: false, symbol, error: `NSE returned no open interest for ${symbol} ${expiry}.` };
    }
    const underlying = num(records?.underlyingValue);
    const totals = summarize(rows, underlying);
    const pcr = totals.callOi > 0 ? round(totals.putOi / totals.callOi, 2) : null;
    const bias = oiBias(totals.callChange, totals.putChange, pcr);
    return {
      ok: true,
      applicable: true,
      symbol: target.input,
      nseSymbol: symbol,
      expiry,
      expiries: expiries.slice(0, 6),
      asOf: str(records?.timestamp),
      underlying: underlying === null ? null : round(underlying, 2),
      callOi: totals.callOi,
      putOi: totals.putOi,
      callChange: totals.callChange,
      putChange: totals.putChange,
      pcr,
      bias: bias.bias,
      biasDetail: bias.biasDetail,
      maxCallStrike: totals.maxCallStrike,
      maxCallOi: totals.maxCallOi,
      maxPutStrike: totals.maxPutStrike,
      maxPutOi: totals.maxPutOi,
      strikes: totals.strikes,
      source: PAGE,
      note: "Buy means put open interest moved at least 15% further in the supportive direction than call open interest, with a put-call ratio of at least 0.70. Sell is the mirror, and the ratio must be 1.20 or below. Anything closer, or a ratio that disagrees, is Neutral. This is an open-interest read, not a trade recommendation.",
    };
  } catch (error) {
    const message = error instanceof Error ? error.message : "NSE option chain request failed.";
    return { ok: false, symbol, error: message };
  }
}

const SCAN_INDEXES = new Set(["NIFTY", "BANKNIFTY", "FINNIFTY", "MIDCPNIFTY", "NIFTYNXT50"]);

export type OiPattern = "call-loaded" | "put-loaded";

export type ScanRow = {
  symbol: string;
  pattern: OiPattern;
  patternLabel: string;
  callChange: number;
  putChange: number;
  underlying: number | null;
  changePercent: number | null;
};

export type OiScan = {
  ok: true;
  asOf: string | null;
  expiry: string;
  scanned: number;
  missed: number;
  callLoaded: ScanRow[];
  putLoaded: ScanRow[];
  note: string;
};

let scanCache: { exp: number; value: OiScan } | undefined;
let scanFlight: Promise<OiScan | ChainFailure> | undefined;

function patternOf(callChange: number, putChange: number): { pattern: OiPattern; patternLabel: string } | null {
  if (callChange > 0 && putChange < 0) {
    return { pattern: "call-loaded", patternLabel: "Call loaded, put unwound" };
  }
  if (putChange > 0 && callChange < 0) {
    return { pattern: "put-loaded", patternLabel: "Put loaded, call unwound" };
  }
  return null;
}

async function mapPool<T, R>(items: T[], limit: number, fn: (item: T) => Promise<R>): Promise<R[]> {
  const out: R[] = new Array(items.length);
  let next = 0;
  async function worker(): Promise<void> {
    while (next < items.length) {
      const index = next;
      next += 1;
      out[index] = await fn(items[index]);
    }
  }
  const workers = Math.min(limit, items.length);
  await Promise.all(Array.from({ length: workers }, () => worker()));
  return out;
}

async function priceChanges(symbols: string[]): Promise<Map<string, number>> {
  const out = new Map<string, number>();
  for (let start = 0; start < symbols.length; start += 20) {
    const query = symbols
      .slice(start, start + 20)
      .map((symbol) => encodeURIComponent(`${symbol}.NS`))
      .join(",");
    try {
      const response = await fetch(
        `https://query1.finance.yahoo.com/v7/finance/spark?symbols=${query}&range=1d&interval=1d`,
        {
          headers: { "user-agent": USER_AGENT, accept: "application/json" },
          signal: AbortSignal.timeout(20_000),
        },
      );
      if (!response.ok) continue;
      const body: unknown = await response.json();
      const list = record(record(body)?.spark)?.result;
      if (!Array.isArray(list)) continue;
      for (const item of list) {
        const node = record(item);
        const yahoo = str(node?.symbol);
        const first = Array.isArray(node?.response) ? record(node.response[0]) : null;
        const change = num(record(first?.meta)?.regularMarketChangePercent);
        if (!yahoo || change === null) continue;
        out.set(yahoo.replace(/\.NS$/i, ""), round(change, 2));
      }
    } catch {
      continue;
    }
  }
  return out;
}

async function equityTotals(symbol: string, expiry: string): Promise<{ callChange: number; putChange: number; underlying: number | null } | null> {
  for (let attempt = 0; attempt < 2; attempt += 1) {
    try {
      const cookie = await nseSession(attempt > 0);
      const chain = await nseGet(
        `/option-chain-v3?type=Equity&symbol=${encodeURIComponent(symbol)}&expiry=${encodeURIComponent(expiry)}`,
        cookie,
      );
      const records = record(record(chain)?.records);
      const rows = Array.isArray(records?.data) ? records.data : [];
      if (rows.length === 0) return null;
      let callChange = 0;
      let putChange = 0;
      for (const row of rows) {
        const item = record(row);
        callChange += side(item?.CE).change ?? 0;
        putChange += side(item?.PE).change ?? 0;
      }
      return { callChange, putChange, underlying: num(records?.underlyingValue) };
    } catch {
      session = undefined;
    }
  }
  return null;
}

async function loadOiScan(): Promise<OiScan | ChainFailure> {
  let cookie = await nseSession();
  let underlyings: unknown;
  try {
    underlyings = await nseGet("/live-analysis-oi-spurts-underlyings", cookie);
  } catch {
    session = undefined;
    cookie = await nseSession(true);
    underlyings = await nseGet("/live-analysis-oi-spurts-underlyings", cookie);
  }
  const listed = Array.isArray(record(underlyings)?.data) ? (record(underlyings)?.data as unknown[]) : [];
  const symbols = listed
    .map((row) => str(record(row)?.symbol))
    .filter((symbol): symbol is string => symbol !== null && !SCAN_INDEXES.has(symbol));
  if (symbols.length === 0) {
    return { ok: false, error: "NSE returned no F&O underlyings." };
  }
  const info = await nseGet("/option-chain-contract-info?symbol=RELIANCE", cookie);
  const expiry = strings(record(info)?.expiryDates)[0];
  if (!expiry) return { ok: false, error: "NSE returned no equity option expiry." };
  const totals = await mapPool(symbols, 8, async (symbol) => ({ symbol, book: await equityTotals(symbol, expiry) }));
  const rows: ScanRow[] = [];
  let missed = 0;
  for (const item of totals) {
    if (!item.book) {
      missed += 1;
      continue;
    }
    const pattern = patternOf(item.book.callChange, item.book.putChange);
    if (!pattern) continue;
    rows.push({
      symbol: item.symbol,
      pattern: pattern.pattern,
      patternLabel: pattern.patternLabel,
      callChange: item.book.callChange,
      putChange: item.book.putChange,
      underlying: item.book.underlying === null ? null : round(item.book.underlying, 2),
      changePercent: null,
    });
  }
  const changes = await priceChanges(rows.map((row) => row.symbol));
  for (const row of rows) row.changePercent = changes.get(row.symbol) ?? null;
  const bySize = (a: ScanRow, b: ScanRow) =>
    Math.abs(b.callChange) + Math.abs(b.putChange) - (Math.abs(a.callChange) + Math.abs(a.putChange));
  const scan: OiScan = {
    ok: true,
    asOf: str(record(underlyings)?.timestamp),
    expiry,
    scanned: symbols.length,
    missed,
    callLoaded: rows.filter((row) => row.pattern === "call-loaded").sort(bySize),
    putLoaded: rows.filter((row) => row.pattern === "put-loaded").sort(bySize),
    note: "Equity F&O only, nearest shared expiry, summed across strikes. A name is listed when call open interest rose and put open interest fell, or the reverse. Names where both sides rose or both fell are left off. Change is the cash session move in percent. This is an open-interest scan, not a trade recommendation.",
  };
  scanCache = { exp: Date.now() + 3 * 60 * 1000, value: scan };
  return scan;
}

export function oiPatternScan(fresh = false): Promise<OiScan | ChainFailure> {
  if (!fresh && scanCache && scanCache.exp > Date.now()) return Promise.resolve(scanCache.value);
  if (!fresh && scanFlight) return scanFlight;
  const flight = loadOiScan().finally(() => {
    if (scanFlight === flight) scanFlight = undefined;
  });
  if (!fresh) scanFlight = flight;
  return flight;
}
