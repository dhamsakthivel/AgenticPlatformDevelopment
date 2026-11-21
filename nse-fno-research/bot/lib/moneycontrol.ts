// Moneycontrol public JSON used by their stock pages.
// News: /mcapi/v1/stock/news
// Bulk deals: /mcapi/v1/extdata/mc-bulk-data
// Large deals: /mcapi/v1/stock/large-deals

const USER_AGENT =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";
const API = "https://api.moneycontrol.com";

const MONTHS: Record<string, string> = {
  jan: "01",
  feb: "02",
  mar: "03",
  apr: "04",
  may: "05",
  jun: "06",
  jul: "07",
  aug: "08",
  sep: "09",
  oct: "10",
  nov: "11",
  dec: "12",
};

export type NewsItem = {
  headline: string;
  url: string;
  date: string | null;
};

export type BulkDeal = {
  date: string;
  client: string;
  side: string;
  quantity: number | null;
  price: number | null;
  percentTraded: number | null;
};

export type LargeDeal = {
  date: string;
  time: string;
  exchange: string;
  quantity: number | null;
  price: number | null;
  valueCrore: number | null;
};

export type CompanyBrief = {
  ok: true;
  applicable: true;
  symbol: string;
  scId: string;
  name: string;
  news: NewsItem[];
  bulkDeals: BulkDeal[];
  bulkWindow: string;
  bulkNote: string;
  largeDeals: LargeDeal[];
  largeError: string | null;
  source: "Moneycontrol";
  dealsPage: string;
  largeDealsPage: string;
};

type BriefFailure = { ok: false; symbol?: string; error: string };
type NotStock = { ok: true; applicable: false; symbol: string; reason: string };

function istToday(): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Kolkata",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
}

function shiftIso(iso: string, days: number): string {
  const [year, month, day] = iso.split("-").map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

function monthNumber(name: string): string | null {
  return MONTHS[name.slice(0, 3).toLowerCase()] ?? null;
}

function parseDisplayDate(value: string): string | null {
  const dayFirst = value.trim().match(/^(\d{1,2})\s+([A-Za-z]+),?\s+(\d{4})$/);
  if (dayFirst) {
    const month = monthNumber(dayFirst[2]);
    if (!month) return null;
    return `${dayFirst[3]}-${month}-${dayFirst[1].padStart(2, "0")}`;
  }
  const monthFirst = value.trim().match(/^([A-Za-z]+)\s+(\d{1,2}),?\s+(\d{4})$/);
  if (monthFirst) {
    const month = monthNumber(monthFirst[1]);
    if (!month) return null;
    return `${monthFirst[3]}-${month}-${monthFirst[2].padStart(2, "0")}`;
  }
  return null;
}

function record(value: unknown): Record<string, unknown> | null {
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function num(value: unknown): number | null {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string") {
    const parsed = Number(value.replace(/,/g, ""));
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
}

function str(value: unknown): string | null {
  return typeof value === "string" && value.trim().length > 0 ? value.trim() : null;
}

async function getJson(url: string): Promise<unknown> {
  const response = await fetch(url, {
    headers: {
      accept: "application/json",
      "user-agent": USER_AGENT,
      origin: "https://www.moneycontrol.com",
      referer: "https://www.moneycontrol.com/",
    },
    signal: AbortSignal.timeout(20_000),
  });
  if (!response.ok) {
    throw new Error(`Moneycontrol returned HTTP ${response.status}.`);
  }
  return response.json() as Promise<unknown>;
}

type Match = { id: string; name: string };

function pickStock(query: string, rows: unknown[]): Match | null {
  const wanted = query.toUpperCase().replace(/[^A-Z0-9]/g, "");
  const parsed = rows.map((row) => {
    const item = record(row);
    const id = str(item?.id);
    const shortName = str(item?.shortName) ?? "";
    const name = str(item?.name) ?? shortName;
    return id ? { id, shortName, name } : null;
  }).filter((row): row is { id: string; shortName: string; name: string } => row !== null);
  const exact = parsed.find((row) => row.shortName.toUpperCase().replace(/[^A-Z0-9]/g, "") === wanted);
  const chosen = exact ?? parsed[0];
  return chosen ? { id: chosen.id, name: chosen.name || chosen.shortName } : null;
}

function sideLabel(value: string): string {
  const lower = value.toLowerCase();
  if (lower === "purchase" || lower === "buy") return "Buy";
  if (lower === "sell") return "Sell";
  return value;
}

async function loadNews(scId: string): Promise<NewsItem[]> {
  const body = await getJson(`${API}/mcapi/v1/stock/news?scId=${encodeURIComponent(scId)}&start=0&limit=12`);
  const data = record(record(body)?.data);
  const news = record(data?.news);
  const rows = Array.isArray(news?.data) ? news.data : [];
  return rows.slice(0, 12).map((row) => {
    const item = record(row);
    return {
      headline: str(item?.heading) ?? "Untitled",
      url: str(item?.posturl) ?? "",
      date: str(item?.display_date),
    };
  }).filter((item) => item.url.startsWith("https://"));
}

async function loadBulkDeals(scId: string, fromIso: string): Promise<{ deals: BulkDeal[]; note: string }> {
  const deals: BulkDeal[] = [];
  let note = "A bulk deal is a trade of more than 0.5% of the company's listed equity shares.";
  for (let page = 1; page <= 3; page += 1) {
    const body = await getJson(
      `${API}/mcapi/v1/extdata/mc-bulk-data?scId=${encodeURIComponent(scId)}&page=${page}&deviceType=W`,
    );
    const data = record(record(body)?.data);
    if (str(data?.note)) note = str(data?.note) ?? note;
    const rows = Array.isArray(data?.stock_detail) ? data.stock_detail : [];
    if (rows.length === 0) break;
    let oldest: string | null = null;
    for (const row of rows) {
      const item = record(row);
      const display = str(item?.datetime);
      const iso = display ? parseDisplayDate(display) : null;
      if (iso && (oldest === null || iso < oldest)) oldest = iso;
      if (!iso || iso < fromIso) continue;
      deals.push({
        date: display ?? iso,
        client: str(item?.title) ?? "Unknown client",
        side: sideLabel(str(item?.type) ?? ""),
        quantity: num(item?.quantity),
        price: num(item?.price),
        percentTraded: num(item?.perTraded),
      });
    }
    if (oldest !== null && oldest < fromIso) break;
    if (rows.length < 8) break;
  }
  return { deals, note };
}

async function loadLargeDeals(scId: string, fromIso: string): Promise<LargeDeal[]> {
  const body = await getJson(`${API}/mcapi/v1/stock/large-deals?scId=${encodeURIComponent(scId)}`);
  const data = record(body)?.data;
  const rows = Array.isArray(data) ? data : [];
  const deals: LargeDeal[] = [];
  for (const row of rows) {
    const item = record(row);
    const display = str(item?.date);
    const iso = display ? parseDisplayDate(display) : null;
    if (!iso || iso < fromIso) continue;
    deals.push({
      date: display ?? iso,
      time: str(item?.time) ?? "",
      exchange: str(item?.exchange) ?? "",
      quantity: num(item?.qty),
      price: num(item?.price),
      valueCrore: num(item?.value),
    });
  }
  return deals;
}

export async function companyBrief(symbol: string): Promise<CompanyBrief | BriefFailure | NotStock> {
  const ticker = symbol.trim().toUpperCase();
  try {
    const found = await getJson(`${API}/mcapi/v1/stock/search?query=${encodeURIComponent(ticker)}`);
    const rows = record(found)?.data;
    const match = pickStock(ticker, Array.isArray(rows) ? rows : []);
    if (!match) {
      return { ok: false, symbol: ticker, error: `Moneycontrol has no stock match for ${ticker}.` };
    }
    const fromIso = shiftIso(istToday(), -4);
    const [news, bulk, large] = await Promise.all([
      loadNews(match.id),
      loadBulkDeals(match.id, fromIso),
      loadLargeDeals(match.id, fromIso).then(
        (deals) => ({ deals, error: null as string | null }),
        (error: unknown) => ({
          deals: [] as LargeDeal[],
          error: error instanceof Error ? error.message : "Moneycontrol large deals did not load.",
        }),
      ),
    ]);
    return {
      ok: true,
      applicable: true,
      symbol: ticker,
      scId: match.id,
      name: match.name,
      news,
      bulkDeals: bulk.deals,
      bulkWindow: `${fromIso} to ${istToday()} (Asia/Kolkata)`,
      bulkNote: bulk.note,
      largeDeals: large.deals,
      largeError: large.error,
      source: "Moneycontrol",
      dealsPage: "https://www.moneycontrol.com/markets/stock-deals/bulk-deals/",
      largeDealsPage: "https://www.moneycontrol.com/markets/stock-deals/large-deals/",
    };
  } catch (error) {
    const message = error instanceof Error ? error.message : "Moneycontrol request failed.";
    return { ok: false, symbol: ticker, error: message };
  }
}
