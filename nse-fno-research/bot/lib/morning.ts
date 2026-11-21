// Weekday 08:45 IST brief for the NSE F&O book.
// Prices are the previous session. Newsletter items are headlines with a link, not the article.
// Buy watch and Sell watch are research labels. They are not orders.

const USER_AGENT =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";
const IST_MS = (5 * 60 + 30) * 60 * 1000;
const DAY_MS = 24 * 60 * 60 * 1000;
const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

export const MORNING_RULE = 2;

const BOOKS = `
Financial Svc|360ONE|360 One
Capital Goods|ABB|ABB INDIA
Financial Svc|ABCAPITAL|Aditya Birla Capital
Power|ADANIENSOL|Adani Energy
Metals|ADANIENT|Adani Enterprises
Oil & Gas|RELIANCE|Reliance
Power|ADANIGREEN|Adani Green
Services|ADANIPORTS|Adani Ports
Power|ADANIPOWER|Adani Power
Healthcare|ALKEM|ALKEM LABORATORIES
Consumer|AMBER|AMBER ENTERPRISES (I)
Construction|AMBUJACEM|AMBUJA CEMENTS
Financial Svc|ANANDRATHI|Anand Rathi
Financial Svc|ANGELONE|Angel One
Metals|APLAPOLLO|APL APOLLO TUBES
Healthcare|APOLLOHOSP|Apollo Hospitals
Auto|ASHOKLEY|Ashok Leyland
Consumer|ASIANPAINT|Asian Paints
Capital Goods|ASTRAL|ASTRAL
Auto|ATHERENERG|Ather
Banking|AUBANK|AU Bank
Healthcare|AUROPHARMA|AUROBINDO PHARMA
Banking|AXISBANK|Axis Bank
Auto|BAJAJ-AUTO|Bajaj Auto
Financial Svc|BAJAJFINSV|Bajaj Finserv
Financial Svc|BAJFINANCE|Bajaj Finance
Banking|BANDHANBNK|Bandhan Bank
PSU Banks|BANKBARODA|Bank of Baroda
PSU Banks|BANKINDIA|Bank of India
Defence|BDL|BHARAT DYNAMICS
Defence|BEL|BHARAT ELECTRONICS
Auto|BHARATFORG|Bharat Forge
Telecom|BHARTIARTL|Bharti
Capital Goods|BHEL|BHEL
Healthcare|BIOCON|BIOCON
Consumer|BLUESTARCO|BLUE STAR
Auto|BOSCHLTD|Bosch
Oil & Gas|BPCL|BHARAT PETROLEUM CORP LT
FMCG|BRITANNIA|BRITANNIA INDUSTRIES
Financial Svc|BSE|BSE
Financial Svc|CAMS|CAMS
PSU Banks|CANBK|Canara Bank
Financial Svc|CDSL|CDSL
Capital Goods|CGPOWER|CG POWER AND IND SOL
Financial Svc|CHOLAFIN|Cholamandalam
Healthcare|CIPLA|CIPLA
Oil & Gas|COALINDIA|Coal India
Defence|COCHINSHIP|COCHIN SHIPYARD
IT|COFORGE|Coforge
FMCG|COLPAL|COLGATE PALMOLIVE
Services|CONCOR|CONTAINER CORP OF IND
Chemicals|COROMANDEL|COROMANDEL INTERNTL
Consumer|CROMPTON|CROMPT GREA CON ELEC
Banking|CUB|CITY UNION BANK
Capital Goods|CUMMINSIND|CUMMINS INDIA
FMCG|DABUR|DABUR INDIA
Defence|DATAPATTNS|DATA PATTERNS INDIA
Logistics|DELHIVERY|DELHIVERY
Healthcare|DIVISLAB|DIVI S LABORATORIES
Consumer|DIXON|DIXON TECHNO (INDIA)
Realty|DLF|DLF
Consumer|DMART|DMart
Healthcare|DRREDDY|Dr Reddy
Auto|EICHERMOT|Eicher
Capital Goods|ENRIN|SIEMENS ENERGY INDIA
Consumer|ETERNAL|Eternal
Banking|FEDERALBNK|Federal Bank
Auto|FORCEMOT|Force Motors
Healthcare|FORTIS|FORTIS HEALTHCARE
Oil & Gas|GAIL|GAIL (INDIA)
Healthcare|GLENMARK|GLENMARK PHARMACEUTICALS
Services|GMRAIRPORT|GMR AIRPORTS
FMCG|GODFRYPHLP|GODFREY PHILLIPS INDIA LT
FMCG|GODREJCP|GODREJ CONSUMER PRODUCTS
Realty|GODREJPROP|GODREJ PROPERTIES
Construction|GRASIM|GRASIM INDUSTRIES
Capital Goods|GVT&D|GE VERNOVA T&D INDIA
Defence|HAL|Hindustan Aeronautics
Consumer|HAVELLS|HAVELLS INDIA
IT|HCLTECH|HCLTech
Financial Svc|HDFCAMC|HDFC AMC
Banking|HDFCBANK|HDFC Bank
Insurance|HDFCLIFE|HDFC Life
Auto|HEROMOTOCO|Hero MotoCorp
Metals|HINDALCO|Hindalco
Oil & Gas|HINDPETRO|HPCL
FMCG|HINDUNILVR|Hindustan Unilever
Metals|HINDZINC|Hindustan Zinc
Auto|HYUNDAI|Hyundai
Banking|ICICIBANK|ICICI Bank
Financial Svc|ICICIGI|ICICI Lombard
Insurance|ICICILIFE|ICICI Prudential
Telecom|IDEA|Vodafone Idea
Banking|IDFCFIRSTB|IDFC First
Financial Svc|IEX|IEX
Consumer|INDHOTEL|Indian Hotels
PSU Banks|INDIANB|Indian Bank
Services|INDIGO|INTERGLOBE AVIATION
Banking|INDUSINDBK|IndusInd
Telecom|INDUSTOWER|INDUS TOWERS
IT|INFY|Infosys
Capital Goods|INOXWIND|Inox Wind
Oil & Gas|IOC|INDIAN OIL
Financial Svc|IREDA|IREDA
PSU Finance|IRFC|IRFC
FMCG|ITC|ITC
Metals|JINDALSTEL|JINDAL STEEL
Financial Svc|JIOFIN|Jio Financial
Power|JSWENERGY|JSW ENERGY
Metals|JSWSTEEL|JSW STEEL
Consumer|JUBLFOOD|JUBILANT FOODWORKS
Consumer|KALYANKJIL|Kalyan Jewellers
Banking|KARURVYSYA|KARUR VYSYA BANK
Capital Goods|KAYNES|KAYNES TECHNOLOGY IND
Capital Goods|KEI|KEI INDUSTRIES
Financial Svc|KFINTECH|KFin
Banking|KOTAKBANK|Kotak Bank
IT|KPITTECH|KPIT
Healthcare|LAURUSLABS|LAURUS LABS
Financial Svc|LICHSGFIN|LIC Housing
Insurance|LICI|LIC
Realty|LODHA|LODHA DEVELOPERS
Infrastructure|LT|Larsen
Financial Svc|LTF|L&T Finance
IT|LTM|LTIMindtree
Healthcare|LUPIN|LUPIN
Auto|M&M|Mahindra
PSU Banks|MAHABANK|Bank of Maharashtra
Financial Svc|MANAPPURAM|Manappuram
Healthcare|MANKIND|MANKIND PHARMA
FMCG|MARICO|MARICO
Auto|MARUTI|Maruti
Healthcare|MAXHEALTH|Max Healthcare
Defence|MAZDOCK|MAZAGON DOCK SHIPBUIL
Financial Svc|MCX|MCX
Financial Svc|MFSL|Max Financial
Auto|MOTHERSON|Motherson
Financial Svc|MOTILALOFS|Motilal Oswal
IT|MPHASIS|Mphasis
Financial Svc|MUTHOOTFIN|Muthoot
Financial Svc|NAM-INDIA|Nippon Life
Metals|NATIONALUM|NATIONAL ALUMINIUM CO
Consumer|NAUKRI|Info Edge
Chemicals|NAVINFLUOR|NAVIN FLUORINE INT
Infrastructure|NBCC|NBCC (INDIA)
FMCG|NESTLEIND|Nestle
Power|NHPC|NHPC
Metals|NMDC|NMDC
Power|NTPC|NTPC
Consumer|NYKAA|Nykaa
Realty|OBEROIRLTY|OBEROI REALTY
IT|OFSS|Oracle Financial
Oil & Gas|OIL|Oil India
Oil & Gas|ONGC|OIL AND NATURAL GAS
Textiles|PAGEIND|Page Industries
FMCG|PATANJALI|PATANJALI FOODS
Fintech|PAYTM|Paytm
IT|PERSISTENT|Persistent
Oil & Gas|PETRONET|PETRONET LNG
PSU Finance|PFC|PFC
Consumer|PGEL|PG ELECTROPLAST
Realty|PHOENIXLTD|PHOENIX MILLS
Chemicals|PIDILITIND|PIDILITE INDUSTRIES
Chemicals|PIIND|PI INDUSTRIES
PSU Banks|PNB|PNB
Financial Svc|PNBHOUSING|PNB Housing
Insurance|POLICYBZR|Policybazaar
Capital Goods|POLYCAB|POLYCAB INDIA
Power|POWERGRID|Power Grid
Capital Goods|POWERINDIA|HITACHI ENERGY INDIA
Capital Goods|PREMIERENE|PREMIER ENERGIES
Realty|PRESTIGE|PRESTIGE ESTATE
FMCG|RADICO|RADICO KHAITAN
Banking|RBLBANK|RBL Bank
PSU Finance|RECLTD|REC
Infrastructure|RVNL|RAIL VIKAS NIGAM
IT|SAGILITY|SAGILITY
Metals|SAIL|STEEL AUTHORITY OF INDIA
Financial Svc|SBICARD|SBI Cards
Insurance|SBILIFE|SBI Life
PSU Banks|SBIN|SBI
Construction|SHREECEM|SHREE CEMENT
Financial Svc|SHRIRAMFIN|Shriram Finance
Capital Goods|SIEMENS|SIEMENS
Chemicals|SOLARINDS|SOLAR INDUSTRIES (I)
Auto|SONACOMS|Sona Comstar
Chemicals|SRF|SRF
Healthcare|SUNPHARMA|Sun Pharma
Capital Goods|SUPREMEIND|SUPREME INDUSTRIES
Capital Goods|SUZLON|SUZLON ENERGY
Consumer|SWIGGY|SWIGGY
Capital Goods|SYRMA|SYRMA SGS TECHNOLOGY
FMCG|TATACONSUM|Tata Consumer
IT|TATAELXSI|Tata Elxsi
Power|TATAPOWER|Tata Power
Metals|TATASTEEL|Tata Steel
IT|TCS|TCS
IT|TECHM|Tech Mahindra
Auto|TIINDIA|Tube Investments
Consumer|TITAN|Titan
Auto|TMPV|Tata Motors
Healthcare|TORNTPHARM|TORRENT PHARMACEUTICALS L
Consumer|TRENT|Trent
Auto|TVSMOTOR|TVS Motor
Banking|UJJIVANSFB|Ujjivan
Construction|ULTRACEMCO|UltraTech
PSU Banks|UNIONBANK|Union Bank
FMCG|UNITDSPR|UNITED SPIRITS
Auto|UNOMINDA|Uno Minda
Chemicals|UPL|UPL
FMCG|VBL|VARUN BEVERAGES
Metals|VEDL|Vedanta
Consumer|VMM|VISHAL MEGA MART
Consumer|VOLTAS|VOLTAS
Capital Goods|WAAREEENER|WAAREE ENERGIES
IT|WIPRO|Wipro
Banking|YESBANK|Yes Bank
Healthcare|ZYDUSLIFE|ZYDUS LIFESCIENCES
`;

const FEEDS: { source: string; url: string }[] = [
  { source: "Economic Times", url: "https://economictimes.indiatimes.com/markets/rssfeeds/1977021501.cms" },
  { source: "Moneycontrol", url: "https://www.moneycontrol.com/rss/marketreports.xml" },
  { source: "Business Standard", url: "https://www.business-standard.com/rss/markets-106.rss" },
  { source: "Mint", url: "https://www.livemint.com/rss/markets" },
];

const GLOBALS: { symbol: string; label: string }[] = [
  { symbol: "^NSEI", label: "Nifty 50" },
  { symbol: "^IXIC", label: "Nasdaq" },
  { symbol: "^GSPC", label: "S&P 500" },
  { symbol: "^N225", label: "Nikkei" },
  { symbol: "GC=F", label: "Gold" },
  { symbol: "SI=F", label: "Silver" },
  { symbol: "HG=F", label: "Copper" },
  { symbol: "BZ=F", label: "Brent" },
];

const NEGATIVE = ["downgrade", "plunge", "plunges", "tumble", "tumbles", "fraud", "default", "penalty", "disappoint", "cuts target", "cut target", "probe", "raid", "loss"];
const POSITIVE = ["upgrade", "upgrades", "beats", "record profit", "order win", "wins order", "dividend"];
const SKIP_SYMBOL = new Set(["OIL", "IDEA", "HAL", "PAGE"]);

type NameRef = { symbol: string; sector: string; match: string };

export type MorningAction = "Buy watch" | "Sell watch" | "Do not chase" | "Stand aside";

export type MorningDeal = {
  symbol: string;
  kind: "Bulk" | "Block";
  side: "Buy" | "Sell";
  client: string;
  quantity: number | null;
  price: number | null;
};

export type MorningRow = {
  symbol: string;
  name: string;
  sector: string;
  close: number;
  changePercent: number;
  sessions: number[];
  action: MorningAction;
  reason: string;
  deal: string | null;
  headline: string | null;
};

export type MorningSector = {
  sector: string;
  count: number;
  median: number;
  rows: MorningRow[];
};

export type MorningHeadline = { source: string; title: string; url: string };

export type MorningGlobal = { label: string; changePercent: number };

export type MorningReport = {
  ok: true;
  ready: true;
  ruleVersion: number;
  briefingKey: string;
  briefingLabel: string;
  marketDate: string;
  marketLabel: string;
  builtAt: string;
  advances: number;
  declines: number;
  unchanged: number;
  scanned: number;
  missed: number;
  globals: MorningGlobal[];
  themes: string[];
  headlines: MorningHeadline[];
  deals: MorningDeal[];
  dealAsOf: string | null;
  lead: MorningRow[];
  sectors: MorningSector[];
  note: string;
};

export type MorningFailure = { ok: false; error: string };

type Clock = { year: number; month: number; day: number; weekday: number; minutes: number };

function istClock(now: Date): Clock {
  const ist = new Date(now.getTime() + IST_MS);
  return {
    year: ist.getUTCFullYear(),
    month: ist.getUTCMonth(),
    day: ist.getUTCDate(),
    weekday: ist.getUTCDay(),
    minutes: ist.getUTCHours() * 60 + ist.getUTCMinutes(),
  };
}

function dateKey(year: number, month: number, day: number): string {
  return `${year}-${String(month + 1).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
}

function labelOf(year: number, month: number, day: number): string {
  return `${day} ${MONTHS[month]} ${year}`;
}

function shift(year: number, month: number, day: number, days: number): { year: number; month: number; day: number; weekday: number } {
  const date = new Date(Date.UTC(year, month, day) + days * DAY_MS);
  return { year: date.getUTCFullYear(), month: date.getUTCMonth(), day: date.getUTCDate(), weekday: date.getUTCDay() };
}

function previousWeekday(year: number, month: number, day: number): { year: number; month: number; day: number } {
  let cursor = { year, month, day, weekday: new Date(Date.UTC(year, month, day)).getUTCDay() };
  for (let step = 0; step < 7; step += 1) {
    cursor = shift(cursor.year, cursor.month, cursor.day, -1);
    if (cursor.weekday !== 0 && cursor.weekday !== 6) return cursor;
  }
  return cursor;
}

function atIst(year: number, month: number, day: number): number {
  return Date.UTC(year, month, day, 8, 45, 0) - IST_MS;
}

export function briefingKey(now = new Date()): string {
  const clock = istClock(now);
  let cursor = { year: clock.year, month: clock.month, day: clock.day, weekday: clock.weekday };
  while (cursor.weekday === 0 || cursor.weekday === 6) cursor = shift(cursor.year, cursor.month, cursor.day, 1);
  return dateKey(cursor.year, cursor.month, cursor.day);
}

export function briefingSlot(now = new Date()): Date {
  const key = briefingKey(now);
  const [year, month, day] = key.split("-").map(Number);
  return new Date(atIst(year, month - 1, day));
}

export function nextMorningAt(now = new Date()): Date {
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

export function morningLabel(when: Date): string {
  const clock = istClock(when);
  return `${clock.day} ${MONTHS[clock.month]} ${clock.year}, 08:45 IST`;
}

function round(value: number, digits = 2): number {
  const scale = 10 ** digits;
  return Math.round(value * scale) / scale;
}

export function fnoBook(): NameRef[] {
  return names();
}

function names(): NameRef[] {
  const out: NameRef[] = [];
  for (const line of BOOKS.split("\n")) {
    const trimmed = line.trim();
    if (!trimmed) continue;
    const [sector, symbol, match] = trimmed.split("|");
    if (!sector || !symbol || !match) continue;
    out.push({ sector, symbol, match });
  }
  return out;
}

function wordHit(text: string, phrase: string): boolean {
  const escaped = phrase.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  return new RegExp(`(?:^|[^A-Za-z0-9])${escaped}(?:[^A-Za-z0-9]|$)`, "i").test(text);
}

async function fetchText(url: string, accept: string): Promise<string> {
  const response = await fetch(url, {
    headers: { "user-agent": USER_AGENT, accept, "accept-language": "en-IN,en;q=0.9" },
    signal: AbortSignal.timeout(15000),
  });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return response.text();
}

function decode(value: string): string {
  return value
    .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, "$1")
    .replace(/&amp;/g, "&")
    .replace(/&quot;/g, "\"")
    .replace(/&#39;|&apos;/g, "'")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/\s+/g, " ")
    .trim();
}

function rssItems(xml: string): { title: string; url: string }[] {
  const items: { title: string; url: string }[] = [];
  for (const block of xml.match(/<item[\s\S]*?<\/item>/gi) ?? []) {
    const title = block.match(/<title>([\s\S]*?)<\/title>/i)?.[1];
    const link = block.match(/<link>([\s\S]*?)<\/link>/i)?.[1];
    if (!title || !link) continue;
    const url = decode(link);
    const headline = decode(title);
    if (!url.startsWith("http") || headline.length < 8) continue;
    items.push({ title: headline.slice(0, 180), url });
  }
  return items.slice(0, 12);
}

async function headlines(): Promise<MorningHeadline[]> {
  const collected: MorningHeadline[] = [];
  await Promise.all(FEEDS.map(async (feed) => {
    try {
      const xml = await fetchText(feed.url, "application/rss+xml, application/xml, text/xml, */*");
      for (const item of rssItems(xml)) collected.push({ source: feed.source, title: item.title, url: item.url });
    } catch {
      // A missing feed does not stop the brief.
    }
  }));
  const seen = new Set<string>();
  return collected.filter((item) => {
    const key = item.title.toLowerCase();
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

async function spark(symbols: string[]): Promise<Map<string, number[]>> {
  const params = new URLSearchParams({ symbols: symbols.join(","), range: "10d", interval: "1d" });
  const response = await fetch(`https://query1.finance.yahoo.com/v7/finance/spark?${params}`, {
    headers: { "user-agent": USER_AGENT, accept: "application/json" },
    signal: AbortSignal.timeout(20000),
  });
  if (!response.ok) throw new Error(`Yahoo returned HTTP ${response.status}.`);
  const body = await response.json() as { spark?: { result?: unknown[] } };
  const map = new Map<string, number[]>();
  const result = body.spark?.result;
  if (!Array.isArray(result)) return map;
  for (const item of result) {
    const row = item as { symbol?: string; response?: { indicators?: { quote?: { close?: unknown[] }[] } }[] };
    const closes = row.response?.[0]?.indicators?.quote?.[0]?.close;
    if (!row.symbol || !Array.isArray(closes)) continue;
    const values = closes.filter((value): value is number => typeof value === "number" && Number.isFinite(value));
    if (values.length >= 2) map.set(row.symbol, values);
  }
  return map;
}

async function prices(symbols: string[]): Promise<Map<string, number[]>> {
  const map = new Map<string, number[]>();
  for (let index = 0; index < symbols.length; index += 20) {
    const batch = await spark(symbols.slice(index, index + 20));
    for (const [symbol, closes] of batch) map.set(symbol, closes);
  }
  return map;
}

function num(value: unknown): number | null {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string") {
    const parsed = Number(value.replace(/,/g, ""));
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
}

async function dealBook(): Promise<{ asOf: string | null; deals: MorningDeal[] }> {
  const response = await fetch("https://www.nseindia.com/api/snapshot-capital-market-largedeal", {
    headers: {
      "user-agent": USER_AGENT,
      accept: "application/json",
      referer: "https://www.nseindia.com/",
    },
    signal: AbortSignal.timeout(15000),
  });
  if (!response.ok) throw new Error(`NSE deals returned HTTP ${response.status}.`);
  const body = await response.json() as Record<string, unknown>;
  const asOf = typeof body.as_on_date === "string" ? body.as_on_date : null;
  const deals: MorningDeal[] = [];
  const groups: { key: string; kind: "Bulk" | "Block" }[] = [
    { key: "BLOCK_DEALS_DATA", kind: "Block" },
    { key: "BULK_DEALS_DATA", kind: "Bulk" },
  ];
  for (const group of groups) {
    const rows = body[group.key];
    if (!Array.isArray(rows)) continue;
    for (const row of rows) {
      if (!row || typeof row !== "object") continue;
      const item = row as Record<string, unknown>;
      const symbol = typeof item.symbol === "string" ? item.symbol.trim().toUpperCase() : "";
      const sideRaw = typeof item.buySell === "string" ? item.buySell.toUpperCase() : "";
      if (!symbol || (sideRaw !== "BUY" && sideRaw !== "SELL")) continue;
      deals.push({
        symbol,
        kind: group.kind,
        side: sideRaw === "BUY" ? "Buy" : "Sell",
        client: typeof item.clientName === "string" ? item.clientName.trim() : "Unknown client",
        quantity: num(item.qty),
        price: num(item.watp),
      });
    }
  }
  return { asOf, deals };
}

function returnsOf(closes: number[]): number[] {
  const out: number[] = [];
  for (let index = 1; index < closes.length; index += 1) {
    const prior = closes[index - 1];
    if (prior > 0) out.push((closes[index] / prior - 1) * 100);
  }
  return out;
}

function median(values: number[]): number {
  if (values.length === 0) return 0;
  const sorted = [...values].sort((a, b) => a - b);
  const mid = Math.floor(sorted.length / 2);
  return sorted.length % 2 === 0 ? (sorted[mid - 1] + sorted[mid]) / 2 : sorted[mid];
}

function phraseHit(text: string, phrase: string): boolean {
  const escaped = phrase.replace(/[.*+?^${}()|[\]\\]/g, "\\$&").replace(/ /g, "\\s+");
  return new RegExp(`(?:^|[^a-z0-9])${escaped}(?:[^a-z0-9]|$)`, "i").test(text);
}

function toneOf(title: string): "bad" | "good" | null {
  const text = title.toLowerCase().replace(/stop-?loss/g, " ");
  if (NEGATIVE.some((word) => phraseHit(text, word))) return "bad";
  if (POSITIVE.some((word) => phraseHit(text, word))) return "good";
  return null;
}

function dealLine(deals: MorningDeal[]): string | null {
  if (deals.length === 0) return null;
  const first = deals[0];
  const extra = deals.length > 1 ? ` +${deals.length - 1} more` : "";
  const price = first.price === null ? "" : ` @ ${round(first.price, 2)}`;
  return `${first.kind} ${first.side.toLowerCase()} · ${first.client}${price}${extra}`;
}

export async function morningReport(now = new Date()): Promise<MorningReport | MorningFailure> {
  try {
    const book = names();
    const bySymbol = new Map(book.map((name) => [name.symbol, name]));
    const [bars, wires, tape] = await Promise.all([
      prices([...book.map((name) => `${name.symbol}.NS`), ...GLOBALS.map((item) => item.symbol)]),
      headlines(),
      dealBook().catch(() => ({ asOf: null, deals: [] as MorningDeal[] })),
    ]);
    const fnoDeals = tape.deals.filter((deal) => bySymbol.has(deal.symbol));
    const dealsFor = new Map<string, MorningDeal[]>();
    for (const deal of fnoDeals) {
      const list = dealsFor.get(deal.symbol) ?? [];
      list.push(deal);
      dealsFor.set(deal.symbol, list);
    }
    const mentions = new Map<string, MorningHeadline[]>();
    for (const name of book) {
      const hits = wires.filter((item) => {
        const symbolHit = name.symbol.length >= 5 && !SKIP_SYMBOL.has(name.symbol) && wordHit(item.title, name.symbol);
        const nameHit = wordHit(item.title, name.match);
        if (!symbolHit && !nameHit) return false;
        const covered = book.some((other) => other.symbol !== name.symbol
          && other.match.length > name.match.length
          && other.match.toLowerCase().includes(name.match.toLowerCase())
          && wordHit(item.title, other.match));
        return !covered;
      });
      if (hits.length > 0) mentions.set(name.symbol, hits);
    }

    const quoted: { ref: NameRef; close: number; change: number; sessions: number[] }[] = [];
    let missed = 0;
    for (const name of book) {
      const closes = bars.get(`${name.symbol}.NS`);
      if (!closes) {
        missed += 1;
        continue;
      }
      const sessions = returnsOf(closes).slice(-3).map((value) => round(value, 2));
      if (sessions.length === 0) {
        missed += 1;
        continue;
      }
      quoted.push({ ref: name, close: closes[closes.length - 1], change: sessions[sessions.length - 1], sessions });
    }
    if (quoted.length < 40) {
      return { ok: false, error: "The morning brief did not get enough F&O prices to rank sectors." };
    }

    const globals: MorningGlobal[] = [];
    for (const item of GLOBALS) {
      const closes = bars.get(item.symbol);
      if (!closes) continue;
      const session = returnsOf(closes).slice(-1)[0];
      if (session === undefined) continue;
      globals.push({ label: item.label, changePercent: round(session, 2) });
    }

    const sectorMedian = new Map<string, number>();
    const grouped = new Map<string, typeof quoted>();
    for (const row of quoted) {
      const list = grouped.get(row.ref.sector) ?? [];
      list.push(row);
      grouped.set(row.ref.sector, list);
    }
    for (const [sector, rows] of grouped) sectorMedian.set(sector, median(rows.map((row) => row.change)));

    function build(row: (typeof quoted)[number]): MorningRow {
      const deals = dealsFor.get(row.ref.symbol) ?? [];
      const news = mentions.get(row.ref.symbol) ?? [];
      const headline = news[0]?.title ?? null;
      const newsTone = news.map((item) => toneOf(item.title)).find((tone) => tone) ?? null;
      const sellDeal = deals.some((deal) => deal.side === "Sell");
      const buyDeal = deals.some((deal) => deal.side === "Buy") && !sellDeal;
      const sectorMove = sectorMedian.get(row.ref.sector) ?? 0;
      const prior = row.sessions.length >= 2 ? row.sessions[row.sessions.length - 2] : 0;
      const extended = row.change >= 3 && prior >= 1.5;
      const sliding = row.sessions.length >= 3 && row.sessions.every((value) => value < 0) && row.change <= -2;
      let action: MorningAction = "Stand aside";
      const because: string[] = [];
      if (row.change >= 4 || extended) {
        action = "Do not chase";
        because.push(row.change >= 4
          ? `Last session +${row.change.toFixed(2)}% is already a large move`
          : `Last two sessions were +${prior.toFixed(2)}% then +${row.change.toFixed(2)}%. The move is already extended`);
      } else if (row.change >= 1 && !sellDeal && newsTone !== "bad") {
        action = "Buy watch";
        because.push(`Last session +${row.change.toFixed(2)}%, inside the 1% to 4% band`);
      } else if (row.change <= -2 && (sellDeal || newsTone === "bad" || sectorMove <= -0.5 || sliding)) {
        action = "Sell watch";
        because.push(`Last session ${row.change.toFixed(2)}%`);
        if (sectorMove <= -0.5) because.push(`${row.ref.sector} median ${sectorMove.toFixed(2)}%`);
        if (sliding) because.push("The last three sessions were all down");
      } else {
        because.push(`Last session ${row.change >= 0 ? "+" : ""}${row.change.toFixed(2)}% does not clear a watch by itself`);
      }
      if (sellDeal) because.push("a disclosed deal was a sell");
      if (buyDeal) because.push("a disclosed deal was a buy");
      if (newsTone === "bad") because.push("a headline is negative");
      if (newsTone === "good" && action === "Buy watch") because.push("a headline is positive");
      return {
        symbol: row.ref.symbol,
        name: row.ref.match,
        sector: row.ref.sector,
        close: round(row.close, 2),
        changePercent: row.change,
        sessions: row.sessions,
        action,
        reason: because.join(". ") + ".",
        deal: dealLine(deals),
        headline,
      };
    }

    const rows = quoted.map(build);
    const sectors: MorningSector[] = [...grouped.keys()].map((sector) => {
      const members = rows.filter((row) => row.sector === sector);
      const chosen: MorningRow[] = [];
      const push = (row: MorningRow | undefined) => {
        if (!row || chosen.some((item) => item.symbol === row.symbol)) return;
        chosen.push(row);
      };
      const dealt = members.filter((row) => row.deal).sort((a, b) => Math.abs(b.changePercent) - Math.abs(a.changePercent));
      const gainers = [...members].sort((a, b) => b.changePercent - a.changePercent);
      const losers = [...members].sort((a, b) => a.changePercent - b.changePercent);
      for (const row of dealt.slice(0, 1)) push(row);
      push(gainers[0]);
      push(losers[0]);
      push(gainers[1]);
      push(losers[1]);
      return {
        sector,
        count: members.length,
        median: round(sectorMedian.get(sector) ?? 0, 2),
        rows: chosen.slice(0, 3),
      };
    }).sort((a, b) => a.median - b.median);

    const sellLead = rows.filter((row) => row.action === "Sell watch").sort((a, b) => a.changePercent - b.changePercent).slice(0, 3);
    const buyLead = rows.filter((row) => row.action === "Buy watch").sort((a, b) => b.changePercent - a.changePercent).slice(0, 3);
    const chase = rows.filter((row) => row.action === "Do not chase").sort((a, b) => b.changePercent - a.changePercent).slice(0, 2);
    const lead = [...sellLead, ...buyLead, ...chase];

    const advances = quoted.filter((row) => row.change > 0.15).length;
    const declines = quoted.filter((row) => row.change < -0.15).length;
    const key = briefingKey(now);
    const [by, bm, bd] = key.split("-").map(Number);
    const market = previousWeekday(by, bm - 1, bd);
    const weakest = sectors[0];
    const strongest = sectors[sectors.length - 1];
    const themes: string[] = [];
    const gold = globals.find((item) => item.label === "Gold");
    const nasdaq = globals.find((item) => item.label === "Nasdaq");
    const nifty = globals.find((item) => item.label === "Nifty 50");
    if (nifty) themes.push(`Nifty 50 finished the last session ${nifty.changePercent >= 0 ? "up" : "down"} ${Math.abs(nifty.changePercent).toFixed(2)}%.`);
    if (nasdaq && nasdaq.changePercent <= -1.5) themes.push(`Nasdaq's last close was ${nasdaq.changePercent.toFixed(2)}%, after the Indian cash session. That is the overseas lead for IT and the higher-beta names.`);
    if (gold && Math.abs(gold.changePercent) >= 1.5) themes.push(`Gold's last close was ${gold.changePercent.toFixed(2)}%, after the Indian cash session. Muthoot and Manappuram carry gold collateral. Hindustan Zinc carries silver.`);
    if (strongest && weakest && strongest.sector !== weakest.sector) {
      themes.push(`${strongest.sector} was the strongest F&O group, median ${strongest.median >= 0 ? "+" : ""}${strongest.median.toFixed(2)}%. ${weakest.sector} was the weakest, median ${weakest.median >= 0 ? "+" : ""}${weakest.median.toFixed(2)}%.`);
    }
    const cited = wires.filter((item) => [...mentions.values()].some((hits) => hits.includes(item))).slice(0, 8);
    const shown = cited.length >= 4 ? cited : wires.slice(0, 8);

    return {
      ok: true,
      ready: true,
      ruleVersion: MORNING_RULE,
      briefingKey: key,
      briefingLabel: labelOf(by, bm - 1, bd),
      marketDate: dateKey(market.year, market.month, market.day),
      marketLabel: labelOf(market.year, market.month, market.day),
      builtAt: now.toISOString(),
      advances,
      declines,
      unchanged: quoted.length - advances - declines,
      scanned: quoted.length,
      missed,
      globals,
      themes,
      headlines: shown,
      deals: fnoDeals.slice(0, 12),
      dealAsOf: tape.asOf,
      lead,
      sectors,
      note: "Built at 08:45 IST on weekdays, before the 09:00 pre-open. Prices are the previous NSE session. Last 3 sessions are daily percent changes, oldest first. Bulk and block rows are the NSE disclosure file for that session. Prints below that disclosure are not in this brief. Headlines are the public market wires from Economic Times, Moneycontrol, Business Standard, and Mint. A Buy watch is a last session of +1% to +4% without a sell-side deal or a negative headline. A last session of +4% or more, or two strong up sessions in a row, is Do not chase. A Sell watch is a last session of −2% or worse when the sector, a sell-side deal, a negative headline, or three down sessions agree. Anything else is Stand aside. These labels are a research brief, not an order.",
    };
  } catch (error) {
    const message = error instanceof Error ? error.message : "The morning brief failed.";
    return { ok: false, error: message };
  }
}
