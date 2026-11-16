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
  maxPutStrike: number | null;
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

async function nseSession(force = false): Promise<string> {
  if (!force && session && session.exp > Date.now()) return session.cookie;
  const response = await fetch(PAGE, {
    headers: { "user-agent": USER_AGENT, accept: "text/html" },
    signal: AbortSignal.timeout(20_000),
  });
  const cookie = cookieHeader(response);
  if (!response.ok || !cookie) {
    throw new Error(`NSE option-chain page returned HTTP ${response.status}.`);
  }
  session = { cookie, exp: Date.now() + 5 * 60 * 1000 };
  return cookie;
}

async function nseGet(path: string, cookie: string): Promise<unknown> {
  const response = await fetch(`${API}${path}`, {
    headers: {
      "user-agent": USER_AGENT,
      accept: "application/json, text/plain, */*",
      referer: PAGE,
      "x-requested-with": "XMLHttpRequest",
      cookie,
    },
    signal: AbortSignal.timeout(20_000),
  });
  if (response.status === 401 || response.status === 403) {
    throw new Error(`NSE option chain returned HTTP ${response.status}.`);
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
  maxPutStrike: number | null;
  strikes: ChainStrike[];
} {
  let callOi = 0;
  let putOi = 0;
  let callChange = 0;
  let putChange = 0;
  let maxCall = -1;
  let maxPut = -1;
  let maxCallStrike: number | null = null;
  let maxPutStrike: number | null = null;
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
      }
    }
    if (put.oi !== null) {
      putOi += put.oi;
      if (put.oi > maxPut) {
        maxPut = put.oi;
        maxPutStrike = strike;
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
    maxPutStrike,
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
      maxPutStrike: totals.maxPutStrike,
      strikes: totals.strikes,
      source: PAGE,
      note: "Buy means put open interest moved at least 15% further in the supportive direction than call open interest, with a put-call ratio of at least 0.70. Sell is the mirror, and the ratio must be 1.20 or below. Anything closer, or a ratio that disagrees, is Neutral. This is an open-interest read, not a trade recommendation.",
    };
  } catch (error) {
    const message = error instanceof Error ? error.message : "NSE option chain request failed.";
    return { ok: false, symbol, error: message };
  }
}
