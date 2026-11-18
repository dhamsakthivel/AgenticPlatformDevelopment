// Cash pre-open ranking for F&O names.
// https://www.nseindia.in/market-data/pre-open-market-cm-and-emerge-market
// The desk runs this at 09:10 IST on weekdays, after order collection has closed.

const USER_AGENT =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";
const PAGE = "https://www.nseindia.in/market-data/pre-open-market-cm-and-emerge-market";
const DATA =
  "https://www.nseindia.in/api/NextApi/apiClient/cmPreOpenApi?functionName=getPreOpenData&category=FO&symbol=";
const IST_MS = (5 * 60 + 30) * 60 * 1000;
const DAY_MS = 24 * 60 * 60 * 1000;

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

export type PremarketRow = {
  symbol: string;
  previousClose: number;
  openPrice: number;
  changePercent: number;
  bidPrice: number;
  bidQty: number;
  askPrice: number;
  askQty: number;
  matchedQty: number;
  matchedValue: number;
};

export type PremarketScan = {
  ok: true;
  ready: true;
  sessionDate: string;
  sessionLabel: string;
  asOf: string | null;
  status: string | null;
  advances: number;
  declines: number;
  unchanged: number;
  scanned: number;
  quantityFloor: number;
  buys: PremarketRow[];
  sells: PremarketRow[];
  source: typeof PAGE;
  note: string;
};

type PremarketFailure = { ok: false; error: string };

type BookRow = {
  price: number;
  qty: number;
  prev: number;
  pct: number;
  bid: number;
  bidQty: number;
  ask: number;
  askQty: number;
  value: number;
  symbol: string;
};

function record(value: unknown): Record<string, unknown> | null {
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function num(value: unknown): number {
  return typeof value === "number" && Number.isFinite(value) ? value : 0;
}

function str(value: unknown): string | null {
  return typeof value === "string" && value.trim().length > 0 ? value.trim() : null;
}

function round(value: number, digits: number): number {
  const scale = 10 ** digits;
  return Math.round(value * scale) / scale;
}

function istClock(now: Date): { year: number; month: number; day: number; weekday: number } {
  const ist = new Date(now.getTime() + IST_MS);
  return {
    year: ist.getUTCFullYear(),
    month: ist.getUTCMonth(),
    day: ist.getUTCDate(),
    weekday: ist.getUTCDay(),
  };
}

export function istDateKey(now = new Date()): string {
  const clock = istClock(now);
  const month = String(clock.month + 1).padStart(2, "0");
  const day = String(clock.day).padStart(2, "0");
  return `${clock.year}-${month}-${day}`;
}

function atIst(year: number, month: number, day: number): number {
  return Date.UTC(year, month, day, 9, 10, 0) - IST_MS;
}

export function nextPremarketAt(now = new Date()): Date {
  const clock = istClock(now);
  let cursor = atIst(clock.year, clock.month, clock.day);
  if (now.getTime() >= cursor) cursor += DAY_MS;
  for (let step = 0; step < 7; step += 1) {
    const weekday = new Date(cursor + IST_MS).getUTCDay();
    if (weekday !== 0 && weekday !== 6) return new Date(cursor);
    cursor += DAY_MS;
  }
  return new Date(cursor);
}

export function premarketDue(now = new Date()): boolean {
  const clock = istClock(now);
  if (clock.weekday === 0 || clock.weekday === 6) return false;
  return now.getTime() >= atIst(clock.year, clock.month, clock.day);
}

export function premarketLabel(when: Date): string {
  const clock = istClock(when);
  return `${clock.day} ${MONTHS[clock.month]} ${clock.year}, 09:10 IST`;
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
        "user-agent": USER_AGENT,
        accept: "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "accept-language": "en-IN,en;q=0.9",
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

async function nseCookie(): Promise<string> {
  const jar = new Map<string, string>();
  let status = await readPage(jar, PAGE);
  if (!jar.has("nsit") && !jar.has("AKA_A2")) status = await readPage(jar, "https://www.nseindia.in/");
  if (!jar.has("nsit") && !jar.has("AKA_A2")) {
    throw new Error(`NSE pre-open page returned HTTP ${status || 403}.`);
  }
  return cookieValue(jar);
}

async function fetchBook(attempt = 0): Promise<Record<string, unknown>> {
  const cookie = await nseCookie();
  let response: Response;
  try {
    response = await fetch(DATA, {
      headers: {
        "user-agent": USER_AGENT,
        accept: "application/json, text/plain, */*",
        "accept-language": "en-IN,en;q=0.9",
        referer: PAGE,
        cookie,
      },
      signal: AbortSignal.timeout(15_000),
    });
  } catch (error) {
    if (attempt < 1) return fetchBook(attempt + 1);
    throw error;
  }
  if ((response.status === 401 || response.status === 403) && attempt < 1) return fetchBook(attempt + 1);
  if (!response.ok) throw new Error(`NSE pre-open book returned HTTP ${response.status}.`);
  const body = record(await response.json());
  if (!body || !Array.isArray(body.data)) throw new Error("NSE pre-open book did not include the F&O list.");
  return body;
}

function sessionFrom(timestamp: string | null, now: Date): { key: string; label: string } {
  const match = timestamp?.match(/^(\d{2})-([A-Za-z]{3})-(\d{4})/);
  const month = match ? MONTHS.indexOf(match[2]) : -1;
  if (match && month >= 0) {
    return {
      key: `${match[3]}-${String(month + 1).padStart(2, "0")}-${match[1]}`,
      label: `${Number(match[1])} ${match[2]} ${match[3]}`,
    };
  }
  const clock = istClock(now);
  return {
    key: istDateKey(now),
    label: `${clock.day} ${MONTHS[clock.month]} ${clock.year}`,
  };
}

function toRow(row: BookRow): PremarketRow {
  return {
    symbol: row.symbol,
    previousClose: row.prev,
    openPrice: row.price,
    changePercent: round(row.pct, 2),
    bidPrice: row.bid,
    bidQty: row.bidQty,
    askPrice: row.ask,
    askQty: row.askQty,
    matchedQty: row.qty,
    matchedValue: row.value,
  };
}

function rankBook(body: Record<string, unknown>, now: Date): PremarketScan {
  const rawRows = Array.isArray(body.data) ? body.data : [];
  const parsed: BookRow[] = [];
  for (const item of rawRows) {
    const row = record(item);
    const symbol = str(row?.symbol);
    if (!row || !symbol) continue;
    const finalPrice = num(row.finalPrice);
    const iep = num(row.IEP);
    const price = finalPrice > 0 ? finalPrice : iep;
    const finalQty = num(row.finalQuantity);
    const traded = num(row.totTradedQty);
    const qty = finalQty > 0 ? finalQty : traded;
    if (price <= 0 || qty <= 0) continue;
    const finalValue = num(row.finalValue);
    const indicative = num(row.indicativeValue);
    parsed.push({
      symbol,
      price,
      qty,
      prev: num(row.prevClose),
      pct: num(row.perChange),
      bid: num(row.bestBidPrice),
      bidQty: num(row.bestBidQty),
      ask: num(row.bestAskPrice),
      askQty: num(row.bestAskQty),
      value: finalValue > 0 ? finalValue : indicative,
    });
  }
  const quantities = parsed.map((row) => row.qty).sort((a, b) => a - b);
  const quantityFloor = quantities.length > 0 ? quantities[Math.floor(quantities.length * 0.4)] : 0;
  const usable = parsed.filter((row) => row.qty >= quantityFloor && !(row.bid > 0 && row.ask > 0 && row.bid > row.ask + 0.001));
  const held = (row: BookRow) => {
    const bidDist = Math.abs(row.bid - row.price);
    const askDist = Math.abs(row.ask - row.price);
    return {
      onBid: row.bid > 0 && bidDist <= askDist && bidDist < 0.051,
      onAsk: row.ask > 0 && askDist < bidDist && askDist < 0.051,
    };
  };
  const buys = usable
    .filter((row) => row.pct > 0 && held(row).onBid && row.bidQty > row.askQty)
    .sort((a, b) => b.pct - a.pct || b.qty - a.qty)
    .map(toRow);
  const sells = usable
    .filter((row) => row.pct < 0 && held(row).onAsk && row.askQty > row.bidQty)
    .sort((a, b) => a.pct - b.pct || b.askQty - a.askQty)
    .map(toRow);
  const session = sessionFrom(str(body.timestamp), now);
  const floorText = quantityFloor.toLocaleString("en-IN");
  return {
    ok: true,
    ready: true,
    sessionDate: session.key,
    sessionLabel: session.label,
    asOf: str(body.timestamp),
    status: str(body.status),
    advances: num(body.advances),
    declines: num(body.declines),
    unchanged: num(body.unchanged),
    scanned: rawRows.length,
    quantityFloor,
    buys,
    sells,
    source: PAGE,
    note: `A buy opened higher and the print is on the bid, with more shares bid there than offered. A sell opened lower and the print is on the ask, with more shares offered than bid. Matched quantity below ${floorText} shares is left out, as is a book whose best bid sits above its best ask.`,
  };
}

export async function premarketScan(): Promise<PremarketScan | PremarketFailure> {
  try {
    const body = await fetchBook();
    return rankBook(body, new Date());
  } catch (error) {
    return { ok: false, error: error instanceof Error ? error.message : "NSE pre-open book did not load." };
  }
}
