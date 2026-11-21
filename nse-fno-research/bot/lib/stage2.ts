// Stage 2 screen for one NSE stock.
// Trend template rules are the eight checks named for this desk.
// A single miss takes the stock out. Earnings and the base are scored after that.
// Prices are split-adjusted Yahoo daily bars, not the NSE live feed.

const USER_AGENT =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";
const CHART = "https://query1.finance.yahoo.com/v8/finance/chart/";

export type Rule = {
  id: string;
  label: string;
  pass: boolean;
  detail: string;
};

export type StageScreen = {
  ok: true;
  applicable: true;
  symbol: string;
  yahooSymbol: string;
  name: string | null;
  asOf: string | null;
  price: number | null;
  verdict: "out" | "watch" | "breakout";
  verdictDetail: string;
  template: { pass: boolean; rules: Rule[] };
  fundamentals: { pass: boolean; rules: Rule[] };
  entry: {
    pass: boolean;
    state: "breakout" | "base" | "light_volume" | "extended" | "none";
    pivot: number | null;
    contractions: number[];
    rules: Rule[];
  };
  note: string;
};

type NotStock = { ok: true; applicable: false; symbol: string; reason: string };
type ScreenFailure = { ok: false; symbol?: string; error: string };

export type ScreenTarget = {
  input: string;
  yahoo: string;
  kind: "index" | "stock";
};

type Bar = { close: number; high: number; low: number; volume: number };

type Session = { cookie: string; crumb: string; exp: number };
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
  return typeof value === "string" && value.length > 0 ? value : null;
}

function rawNumber(node: unknown): number | null {
  return num(record(node)?.raw);
}

function round(value: number, digits: number): number {
  const scale = 10 ** digits;
  return Math.round(value * scale) / scale;
}

function money(value: number): string {
  return value.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function pct(value: number): string {
  const scaled = round(value * 100, 1);
  return `${scaled > 0 ? "+" : ""}${scaled.toFixed(1)}%`;
}

function mean(values: number[]): number | null {
  if (values.length === 0) return null;
  return values.reduce((sum, value) => sum + value, 0) / values.length;
}

function smaEnding(values: number[], end: number, period: number): number | null {
  if (end + 1 < period || end >= values.length) return null;
  return mean(values.slice(end + 1 - period, end + 1));
}

function trailingReturn(values: number[], sessions: number): number | null {
  if (values.length <= sessions) return null;
  const then = values[values.length - 1 - sessions];
  const now = values[values.length - 1];
  if (then === 0) return null;
  return (now - then) / then;
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

async function loadChart(yahoo: string): Promise<{ bars: Bar[]; name: string | null; asOf: string | null } | { error: string }> {
  const url = `${CHART}${encodeURIComponent(yahoo)}?interval=1d&range=2y&includePrePost=false`;
  const { status, body } = await fetchJson(url);
  const chart = record(record(body)?.chart);
  const error = record(chart?.error);
  if (status !== 200 || error) {
    return { error: `No daily chart for ${yahoo} (${str(error?.code) ?? `HTTP ${status}`}).` };
  }
  const result = record(Array.isArray(chart?.result) ? chart.result[0] : null);
  if (!result) return { error: `Yahoo returned an empty chart for ${yahoo}.` };
  const meta = record(result.meta) ?? {};
  const indicators = record(result.indicators);
  const quote = Array.isArray(indicators?.quote) ? record(indicators.quote[0]) : null;
  const closes = Array.isArray(quote?.close) ? quote.close : [];
  const highs = Array.isArray(quote?.high) ? quote.high : [];
  const lows = Array.isArray(quote?.low) ? quote.low : [];
  const volumes = Array.isArray(quote?.volume) ? quote.volume : [];
  const adjNode = Array.isArray(indicators?.adjclose) ? record(indicators.adjclose[0]) : null;
  const adj = Array.isArray(adjNode?.adjclose) ? adjNode.adjclose : [];
  const bars: Bar[] = [];
  for (let i = 0; i < closes.length; i++) {
    const close = num(closes[i]);
    const high = num(highs[i]);
    const low = num(lows[i]);
    const volume = num(volumes[i]);
    if (close === null || high === null || low === null || volume === null || close <= 0) continue;
    const adjusted = num(adj[i]);
    const factor = adjusted !== null && adjusted > 0 ? adjusted / close : 1;
    bars.push({ close: close * factor, high: high * factor, low: low * factor, volume });
  }
  const asOfUnix = num(meta.regularMarketTime);
  return {
    bars,
    name: str(meta.shortName) ?? str(meta.longName),
    asOf: asOfUnix === null ? null : istStamp(asOfUnix),
  };
}

function templateRules(bars: Bar[]): Rule[] {
  const closes = bars.map((bar) => bar.close);
  const last = closes.length - 1;
  const price = closes[last] ?? null;
  const sma50 = price === null ? null : smaEnding(closes, last, 50);
  const sma150 = price === null ? null : smaEnding(closes, last, 150);
  const sma200 = price === null ? null : smaEnding(closes, last, 200);
  const sma200Prior = price === null ? null : smaEnding(closes, last - 21, 200);
  const year = bars.slice(-252);
  const yearHigh = year.length >= 252 ? Math.max(...year.map((bar) => bar.high)) : null;
  const yearLow = year.length >= 252 ? Math.min(...year.map((bar) => bar.low)) : null;
  const aboveLow = price !== null && yearLow !== null && yearLow > 0 ? (price - yearLow) / yearLow : null;
  const belowHigh = price !== null && yearHigh !== null && yearHigh > 0 ? (yearHigh - price) / yearHigh : null;

  const missing = "Not enough daily history for this rule.";
  return [
    {
      id: "price-150",
      label: "Price above the 150-day average",
      pass: price !== null && sma150 !== null && price > sma150,
      detail: price === null || sma150 === null ? missing : `Price ${money(price)} versus 150-day average ${money(sma150)}.`,
    },
    {
      id: "price-200",
      label: "Price above the 200-day average",
      pass: price !== null && sma200 !== null && price > sma200,
      detail: price === null || sma200 === null ? missing : `Price ${money(price)} versus 200-day average ${money(sma200)}.`,
    },
    {
      id: "ma-150-200",
      label: "150-day average above the 200-day average",
      pass: sma150 !== null && sma200 !== null && sma150 > sma200,
      detail: sma150 === null || sma200 === null ? missing : `150-day ${money(sma150)} versus 200-day ${money(sma200)}.`,
    },
    {
      id: "ma-200-up",
      label: "200-day average trending up",
      pass: sma200 !== null && sma200Prior !== null && sma200 > sma200Prior,
      detail: sma200 === null || sma200Prior === null
        ? missing
        : `200-day average is ${money(sma200)}, ${pct((sma200 - sma200Prior) / sma200Prior)} versus 21 sessions ago.`,
    },
    {
      id: "ma-50-200",
      label: "50-day average above the 200-day average",
      pass: sma50 !== null && sma200 !== null && sma50 > sma200,
      detail: sma50 === null || sma200 === null ? missing : `50-day ${money(sma50)} versus 200-day ${money(sma200)}.`,
    },
    {
      id: "price-50",
      label: "Price above the 50-day average",
      pass: price !== null && sma50 !== null && price > sma50,
      detail: price === null || sma50 === null ? missing : `Price ${money(price)} versus 50-day average ${money(sma50)}.`,
    },
    {
      id: "above-low",
      label: "Price at least 25% above the 252-session low",
      pass: aboveLow !== null && aboveLow >= 0.25,
      detail: aboveLow === null || yearLow === null
        ? "Need 252 sessions to measure the yearly low."
        : `Price is ${pct(aboveLow)} above the low of ${money(yearLow)}.`,
    },
    {
      id: "near-high",
      label: "Price within 25% of the 252-session high",
      pass: belowHigh !== null && belowHigh <= 0.25,
      detail: belowHigh === null || yearHigh === null
        ? "Need 252 sessions to measure the yearly high."
        : `Price is ${(belowHigh * 100).toFixed(1)}% under the high of ${money(yearHigh)}.`,
    },
  ];
}

type YearRow = { end: number; label: string; revenue: number | null; earnings: number | null };

function growth(latest: number, prior: number): number | null {
  if (prior === 0) return null;
  return (latest - prior) / Math.abs(prior);
}

function fundRules(
  quarterlyEps: number | null,
  quarterlySales: number | null,
  years: YearRow[],
  stockYear: number | null,
  niftyYear: number | null,
): Rule[] {
  const ordered = [...years].sort((a, b) => a.end - b.end);
  const latest = ordered[ordered.length - 1];
  const prior = ordered[ordered.length - 2];
  const older = ordered[ordered.length - 3];
  const earningsNow = latest && prior && latest.earnings !== null && prior.earnings !== null
    ? growth(latest.earnings, prior.earnings)
    : null;
  const earningsPrev = prior && older && prior.earnings !== null && older.earnings !== null
    ? growth(prior.earnings, older.earnings)
    : null;
  const salesNow = latest && prior && latest.revenue !== null && prior.revenue !== null
    ? growth(latest.revenue, prior.revenue)
    : null;
  const salesPrev = prior && older && prior.revenue !== null && older.revenue !== null
    ? growth(prior.revenue, older.revenue)
    : null;
  const yearLabel = latest?.label ?? "the latest year";

  return [
    {
      id: "q-eps",
      label: "Quarterly earnings growth is positive",
      pass: quarterlyEps !== null && quarterlyEps > 0,
      detail: quarterlyEps === null
        ? "The feed has no year-on-year quarterly earnings growth."
        : `Latest reported quarterly earnings growth is ${pct(quarterlyEps)}.`,
    },
    {
      id: "q-sales",
      label: "Quarterly sales growth is positive",
      pass: quarterlySales !== null && quarterlySales > 0,
      detail: quarterlySales === null
        ? "The feed has no year-on-year quarterly sales growth."
        : `Latest reported quarterly sales growth is ${pct(quarterlySales)}.`,
    },
    {
      id: "y-eps",
      label: "Annual earnings growth is positive",
      pass: earningsNow !== null && earningsNow > 0,
      detail: earningsNow === null
        ? "Need two annual earnings figures."
        : `${yearLabel} earnings growth is ${pct(earningsNow)}.`,
    },
    {
      id: "y-eps-accel",
      label: "Annual earnings growth is accelerating",
      pass: earningsNow !== null && earningsPrev !== null && earningsNow > earningsPrev,
      detail: earningsNow === null || earningsPrev === null
        ? "Need three annual earnings figures to compare the growth rates."
        : `${yearLabel} growth ${pct(earningsNow)} versus the prior year's ${pct(earningsPrev)}.`,
    },
    {
      id: "y-sales",
      label: "Annual sales growth is positive",
      pass: salesNow !== null && salesNow > 0,
      detail: salesNow === null
        ? "Need two annual sales figures."
        : `${yearLabel} sales growth is ${pct(salesNow)}.`,
    },
    {
      id: "y-sales-accel",
      label: "Annual sales growth is accelerating",
      pass: salesNow !== null && salesPrev !== null && salesNow > salesPrev,
      detail: salesNow === null || salesPrev === null
        ? "Need three annual sales figures to compare the growth rates."
        : `${yearLabel} growth ${pct(salesNow)} versus the prior year's ${pct(salesPrev)}.`,
    },
    {
      id: "rs-nifty",
      label: "12-month return leads Nifty",
      pass: stockYear !== null && niftyYear !== null && stockYear > niftyYear,
      detail: stockYear === null || niftyYear === null
        ? "Need 252 sessions for the stock and for Nifty."
        : `Stock ${pct(stockYear)} versus Nifty ${pct(niftyYear)} over 252 sessions.`,
    },
  ];
}

type Swing = { index: number; kind: "high" | "low"; price: number };

function swings(bars: Bar[]): Swing[] {
  const wing = 5;
  const points: Swing[] = [];
  for (let i = wing; i < bars.length - wing; i++) {
    let isHigh = true;
    let isLow = true;
    for (let j = i - wing; j <= i + wing; j++) {
      if (j === i) continue;
      if (bars[j].high >= bars[i].high) isHigh = false;
      if (bars[j].low <= bars[i].low) isLow = false;
    }
    if (isHigh) points.push({ index: i, kind: "high", price: bars[i].high });
    else if (isLow) points.push({ index: i, kind: "low", price: bars[i].low });
  }
  const alternating: Swing[] = [];
  for (const point of points) {
    const previous = alternating[alternating.length - 1];
    if (!previous || previous.kind !== point.kind) {
      alternating.push(point);
      continue;
    }
    if (point.kind === "high" && point.price >= previous.price) alternating[alternating.length - 1] = point;
    if (point.kind === "low" && point.price <= previous.price) alternating[alternating.length - 1] = point;
  }
  return alternating;
}

type Contraction = { depth: number; highIndex: number; lowIndex: number; pivot: number };

function contractionsOf(bars: Bar[]): Contraction[] {
  const found: Contraction[] = [];
  const points = swings(bars);
  for (let i = 0; i < points.length - 1; i++) {
    const high = points[i];
    const low = points[i + 1];
    if (high.kind !== "high" || low.kind !== "low" || high.price <= 0) continue;
    const depth = (high.price - low.price) / high.price;
    if (depth < 0.02) continue;
    found.push({ depth, highIndex: high.index, lowIndex: low.index, pivot: high.price });
  }
  return found;
}

function entryView(bars: Bar[]): StageScreen["entry"] {
  const window = bars.slice(-120);
  const contractions = contractionsOf(window);
  const recent = contractions.slice(-3);
  const tightening = recent.length >= 2 && recent.every((item, index) => index === 0 || item.depth < recent[index - 1].depth);
  const last = recent[recent.length - 1];
  const tight = tightening && last !== undefined && last.depth <= 0.12;
  const depths = recent.map((item) => round(item.depth * 100, 1));
  const priorVolumes = window.slice(-51, -1).map((bar) => bar.volume);
  const averageVolume = mean(priorVolumes);
  const lastBar = window[window.length - 1];

  if (!tight || !last || averageVolume === null || !lastBar) {
    const detail = recent.length < 2
      ? "Fewer than two pullbacks of at least 2% in the last 120 sessions."
      : !tightening
        ? `Pullbacks are ${depths.map((item) => `${item.toFixed(1)}%`).join(" then ")}. Each one needs to be smaller than the one before it.`
        : `The latest pullback is ${pct(last?.depth ?? 0).replace("+", "")}. A tight base needs that last pullback at or under 12%.`;
    return {
      pass: false,
      state: "none",
      pivot: last?.pivot ?? null,
      contractions: depths,
      rules: [{ id: "vcp", label: "Tight base, then a breakout on volume", pass: false, detail }],
    };
  }

  const volumeDry = (() => {
    const previous = recent[recent.length - 2];
    const lastVol = mean(window.slice(last.highIndex, last.lowIndex + 1).map((bar) => bar.volume));
    const prevVol = mean(window.slice(previous.highIndex, previous.lowIndex + 1).map((bar) => bar.volume));
    return lastVol !== null && prevVol !== null && lastVol < prevVol;
  })();
  let cross: number | null = null;
  for (let i = last.lowIndex + 1; i < window.length; i++) {
    if (window[i].close > last.pivot && window[i - 1].close <= last.pivot) cross = i;
  }
  const sessionsSince = cross === null ? null : window.length - 1 - cross;
  const breakoutBar = cross === null ? null : window[cross];
  const breakoutVolume = breakoutBar !== null && breakoutBar.volume >= averageVolume * 1.5;
  const depthText = depths.map((item) => `${item.toFixed(1)}%`).join(" → ");

  if (cross === null || sessionsSince === null || sessionsSince > 5) {
    const state = cross !== null && sessionsSince !== null && sessionsSince > 5 ? "extended" : "base";
    const detail = state === "extended"
      ? `Contractions ${depthText}. Price cleared ${money(last.pivot)} ${sessionsSince} sessions ago, so this is no longer a fresh breakout.`
      : `Contractions ${depthText}. Pivot is ${money(last.pivot)}. Price has not closed through it.${volumeDry ? " Volume dried up on the last pullback." : ""}`;
    return {
      pass: false,
      state,
      pivot: round(last.pivot, 2),
      contractions: depths,
      rules: [{ id: "vcp", label: "Tight base, then a breakout on volume", pass: false, detail }],
    };
  }

  if (!breakoutVolume) {
    return {
      pass: false,
      state: "light_volume",
      pivot: round(last.pivot, 2),
      contractions: depths,
      rules: [{
        id: "vcp",
        label: "Tight base, then a breakout on volume",
        pass: false,
        detail: `Contractions ${depthText}. Close is through ${money(last.pivot)}, and breakout volume is under 1.5 times the prior 50-session average.`,
      }],
    };
  }

  return {
    pass: true,
    state: "breakout",
    pivot: round(last.pivot, 2),
    contractions: depths,
    rules: [{
      id: "vcp",
      label: "Tight base, then a breakout on volume",
      pass: true,
      detail: `Contractions ${depthText}. Close is through ${money(last.pivot)} within the last 5 sessions, on at least 1.5 times the prior 50-session volume.`,
    }],
  };
}

async function loadFundamentals(yahoo: string): Promise<{ quarterlyEps: number | null; quarterlySales: number | null; years: YearRow[]; error: string | null }> {
  const empty = { quarterlyEps: null, quarterlySales: null, years: [] as YearRow[], error: null as string | null };
  try {
    let auth = await yahooSession();
    const load = (active: Session) => {
      const url = new URL(`https://query1.finance.yahoo.com/v10/finance/quoteSummary/${encodeURIComponent(yahoo)}`);
      url.searchParams.set("modules", "financialData,incomeStatementHistory");
      url.searchParams.set("crumb", active.crumb);
      return fetchJson(url.toString(), { cookie: active.cookie });
    };
    let loaded = await load(auth);
    if (loaded.status === 401) {
      session = undefined;
      auth = await yahooSession(true);
      loaded = await load(auth);
    }
    const summary = record(record(loaded.body)?.quoteSummary);
    const result = record(Array.isArray(summary?.result) ? summary.result[0] : null);
    if (loaded.status !== 200 || !result) {
      return { ...empty, error: `No earnings feed for ${yahoo} (${str(record(summary?.error)?.code) ?? `HTTP ${loaded.status}`}).` };
    }
    const financial = record(result.financialData);
    const rows = Array.isArray(record(result.incomeStatementHistory)?.incomeStatementHistory)
      ? record(result.incomeStatementHistory)?.incomeStatementHistory as unknown[]
      : [];
    const years: YearRow[] = rows.map((row) => {
      const item = record(row);
      const end = record(item?.endDate);
      return {
        end: num(end?.raw) ?? 0,
        label: str(end?.fmt) ?? "year",
        revenue: rawNumber(item?.totalRevenue),
        earnings: rawNumber(item?.netIncome),
      };
    }).filter((row) => row.end > 0);
    return {
      quarterlyEps: rawNumber(financial?.earningsGrowth),
      quarterlySales: rawNumber(financial?.revenueGrowth),
      years,
      error: null,
    };
  } catch (error) {
    const message = error instanceof Error ? error.message : "Earnings request failed.";
    return { ...empty, error: message };
  }
}

function verdictOf(templatePass: boolean, fundamentalPass: boolean, entryPass: boolean): { verdict: StageScreen["verdict"]; verdictDetail: string } {
  if (!templatePass) {
    return {
      verdict: "out",
      verdictDetail: "Out. One Stage 2 rule failed, so the stock is not a candidate.",
    };
  }
  if (!fundamentalPass) {
    return {
      verdict: "out",
      verdictDetail: "Out. The Stage 2 template passes, and the earnings or relative-strength check does not.",
    };
  }
  if (!entryPass) {
    return {
      verdict: "watch",
      verdictDetail: "Watch. Stage 2 and earnings pass. There is no fresh breakout on volume.",
    };
  }
  return {
    verdict: "breakout",
    verdictDetail: "All three conditions are met: Stage 2, accelerating earnings, and a fresh breakout on volume.",
  };
}

export async function stageScreenFor(target: ScreenTarget): Promise<StageScreen | NotStock | ScreenFailure> {
  if (target.kind === "index") {
    return {
      ok: true,
      applicable: false,
      symbol: target.input,
      reason: "An index is not a Stage 2 stock. Ask for an NSE stock symbol.",
    };
  }
  try {
    const [chart, niftyChart] = await Promise.all([loadChart(target.yahoo), loadChart("^NSEI")]);
    if ("error" in chart) return { ok: false, symbol: target.yahoo, error: chart.error };
    const rules = templateRules(chart.bars);
    const templatePass = rules.every((rule) => rule.pass);
    const fundamentals = await loadFundamentals(target.yahoo);
    const fundList = fundamentals.error
      ? [{ id: "fund-feed", label: "Earnings feed", pass: false, detail: fundamentals.error }]
      : fundRules(
          fundamentals.quarterlyEps,
          fundamentals.quarterlySales,
          fundamentals.years,
          trailingReturn(chart.bars.map((bar) => bar.close), 252),
          "error" in niftyChart ? null : trailingReturn(niftyChart.bars.map((bar) => bar.close), 252),
        );
    const entry = entryView(chart.bars);
    const fundamentalPass = fundList.every((rule) => rule.pass);
    const verdict = verdictOf(templatePass, fundamentalPass, entry.pass);
    const price = chart.bars[chart.bars.length - 1]?.close ?? null;
    return {
      ok: true,
      applicable: true,
      symbol: target.input,
      yahooSymbol: target.yahoo,
      name: chart.name,
      asOf: chart.asOf,
      price: price === null ? null : round(price, 2),
      verdict: verdict.verdict,
      verdictDetail: verdict.verdictDetail,
      template: { pass: templatePass, rules },
      fundamentals: { pass: fundamentalPass, rules: fundList },
      entry,
      note: "Stage 2 uses the eight rules on this desk. Earnings growth is year on year from the Yahoo feed. A breakout is a close through the last contraction high within 5 sessions, on at least 1.5 times the prior 50-session volume. This is research context, not a trade recommendation.",
    };
  } catch (error) {
    const message = error instanceof Error ? error.message : "Stage 2 screen failed.";
    return { ok: false, symbol: target.yahoo, error: message };
  }
}
