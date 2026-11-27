// Very high 10-minute volume, then a follow-up, then the session's futures open interest.
// The candle close decides buy or sell. Ten-minute open interest is outside this feed.

import { inflateRawSync } from "node:zlib";
import { resolveSymbol } from "./market";

const USER_AGENT =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

export type VolumeSide = "Buy" | "Sell" | "Middle";
export type VolumeOi = "New shorts" | "New longs" | "Shorts covering" | "Longs leaving" | "Flat" | "Unavailable";
export type VolumeDecisionName = "Buy" | "Sell" | "Stand aside" | "Waiting";

export type VolumeCandle = {
  time: string;
  date: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volumeMultiple: number;
  closeLocation: number;
  side: VolumeSide;
  followUp: boolean;
  followUpDetail: string;
  oi: VolumeOi;
  oiDetail: string;
  decision: VolumeDecisionName;
  reason: string;
};

export type VolumeReport = {
  ok: true;
  applicable: boolean;
  symbol: string;
  reason?: string;
  candles: VolumeCandle[];
  decision: VolumeDecisionName | "None";
  note: string;
};

export type VolumeFailure = { ok: false; error: string };

type Bar = {
  unix: number;
  date: string;
  label: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
};

type SessionOi = { oi: number; change: number } | null;

const oiCache = new Map<string, SessionOi>();

function round(value: number, digits = 2): number {
  const scale = 10 ** digits;
  return Math.round(value * scale) / scale;
}

function median(values: number[]): number {
  if (values.length === 0) return 0;
  const sorted = [...values].sort((a, b) => a - b);
  const mid = Math.floor(sorted.length / 2);
  return sorted.length % 2 === 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
}

function istParts(unix: number): { year: number; month: number; day: number; hour: number; minute: number } {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: "Asia/Kolkata",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).formatToParts(new Date(unix * 1000));
  const pick = (type: string) => Number(parts.find((part) => part.type === type)?.value ?? "0");
  return { year: pick("year"), month: pick("month"), day: pick("day"), hour: pick("hour"), minute: pick("minute") };
}

function dateKey(year: number, month: number, day: number): string {
  return `${year}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
}

function labelOf(unix: number, hour: number, minute: number): string {
  const clock = istParts(unix);
  const months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
  return `${clock.day} ${months[clock.month - 1]} ${clock.year}, ${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")} IST`;
}

function slotOf(hour: number, minute: number): { hour: number; minute: number } | null {
  const minutes = hour * 60 + minute;
  const open = 9 * 60 + 15;
  const close = 15 * 60 + 30;
  if (minutes < open || minutes >= close) return null;
  if (minutes >= 15 * 60 + 15) return { hour: 15, minute: 15 };
  const slot = open + Math.floor((minutes - open) / 10) * 10;
  return { hour: Math.floor(slot / 60), minute: slot % 60 };
}

async function tenMinuteBars(yahoo: string): Promise<Bar[]> {
  const url =
    "https://query1.finance.yahoo.com/v8/finance/chart/" +
    encodeURIComponent(yahoo) +
    "?interval=5m&range=5d&includePrePost=false";
  const response = await fetch(url, {
    headers: { "user-agent": USER_AGENT, accept: "application/json" },
    signal: AbortSignal.timeout(20_000),
  });
  if (!response.ok) throw new Error(`The 10-minute prices returned HTTP ${response.status}.`);
  const body = await response.json() as { chart?: { result?: unknown[] } };
  const result = body.chart?.result?.[0] as {
    timestamp?: number[];
    indicators?: { quote?: Array<Record<string, Array<number | null>>> };
  } | undefined;
  const times = result?.timestamp ?? [];
  const quote = result?.indicators?.quote?.[0];
  if (!quote) throw new Error("The 10-minute prices came back empty.");
  const grouped = new Map<string, Bar>();
  for (let index = 0; index < times.length; index += 1) {
    const open = quote.open?.[index];
    const high = quote.high?.[index];
    const low = quote.low?.[index];
    const close = quote.close?.[index];
    const volume = quote.volume?.[index];
    if (open == null || high == null || low == null || close == null || volume == null) continue;
    const clock = istParts(times[index]);
    const slot = slotOf(clock.hour, clock.minute);
    if (!slot) continue;
    const date = dateKey(clock.year, clock.month, clock.day);
    const key = `${date} ${slot.hour}:${slot.minute}`;
    const current = grouped.get(key);
    if (!current) {
      grouped.set(key, {
        unix: times[index],
        date,
        label: labelOf(times[index], slot.hour, slot.minute),
        open,
        high,
        low,
        close,
        volume,
      });
      continue;
    }
    current.high = Math.max(current.high, high);
    current.low = Math.min(current.low, low);
    current.close = close;
    current.volume += volume;
  }
  return [...grouped.values()].sort((a, b) => a.unix - b.unix);
}

function unzipCsv(bytes: Buffer): string {
  let offset = 0;
  while (offset + 30 <= bytes.length) {
    if (bytes.readUInt32LE(offset) !== 0x04034b50) break;
    const method = bytes.readUInt16LE(offset + 8);
    const compressed = bytes.readUInt32LE(offset + 18);
    const nameLength = bytes.readUInt16LE(offset + 26);
    const extraLength = bytes.readUInt16LE(offset + 28);
    const start = offset + 30 + nameLength + extraLength;
    const data = bytes.subarray(start, start + compressed);
    if (method === 0) return data.toString("utf8");
    if (method === 8) return inflateRawSync(data).toString("utf8");
    throw new Error("The F&O file uses an unsupported compression.");
  }
  throw new Error("The F&O file was not a zip.");
}

async function sessionOi(symbol: string, date: string): Promise<SessionOi> {
  const ymd = date.replace(/-/g, "");
  const key = `${ymd}:${symbol}`;
  if (oiCache.has(key)) return oiCache.get(key) ?? null;
  const url = `https://nsearchives.nseindia.com/content/fo/BhavCopy_NSE_FO_0_0_0_${ymd}_F_0000.csv.zip`;
  const response = await fetch(url, {
    headers: { "user-agent": USER_AGENT, accept: "application/zip,*/*" },
    signal: AbortSignal.timeout(40_000),
  });
  if (response.status === 404) {
    oiCache.set(key, null);
    return null;
  }
  if (!response.ok) throw new Error(`NSE F&O file for ${date} returned HTTP ${response.status}.`);
  const csv = unzipCsv(Buffer.from(await response.arrayBuffer()));
  const lines = csv.split(/\r?\n/);
  const header = lines[0]?.split(",") ?? [];
  const col = new Map(header.map((name, index) => [name, index]));
  let oi = 0;
  let change = 0;
  let found = false;
  for (const line of lines.slice(1)) {
    if (!line) continue;
    const cell = line.split(",");
    if (cell[col.get("FinInstrmTp") ?? -1] !== "STF") continue;
    if (cell[col.get("TckrSymb") ?? -1] !== symbol) continue;
    oi += Number(cell[col.get("OpnIntrst") ?? -1] || 0);
    change += Number(cell[col.get("ChngInOpnIntrst") ?? -1] || 0);
    found = true;
  }
  const row = found ? { oi, change } : null;
  oiCache.set(key, row);
  return row;
}

function readingOf(priceChange: number, oiChange: number): VolumeOi {
  if (Math.abs(priceChange) < 0.3 || oiChange === 0) return "Flat";
  if (priceChange > 0 && oiChange > 0) return "New longs";
  if (priceChange > 0) return "Shorts covering";
  if (oiChange > 0) return "New shorts";
  return "Longs leaving";
}

function nextDate(bars: Bar[], date: string): string {
  return bars.find((bar) => bar.date > date)?.date ?? date;
}

function buildCandle(bars: Bar[], index: number, multiple: number, oi: SessionOi, sessionOpen: number, sessionClose: number): VolumeCandle {
  const bar = bars[index];
  const span = bar.high - bar.low;
  const closeLocation = span > 0 ? (bar.close - bar.low) / span : 0.5;
  const side: VolumeSide =
    bar.close <= bar.open && closeLocation <= 0.35 ? "Sell" : bar.close >= bar.open && closeLocation >= 0.65 ? "Buy" : "Middle";
  const until = nextDate(bars, bar.date);
  const later = bars.filter((item) => item.unix > bar.unix && item.date <= until);
  const extended = later.some((item) => (side === "Sell" ? item.close < bar.close : item.close > bar.close));
  const reclaimed = later.some((item) => (side === "Sell" ? item.close > bar.open : item.close < bar.open));
  const followUp = side !== "Middle" && extended && !reclaimed;
  const extreme = later.length === 0 ? null : later.reduce((best, item) => (side === "Sell" ? Math.min(best, item.close) : Math.max(best, item.close)), later[0].close);
  const followUpDetail = later.length === 0
    ? "No later 10-minute candle has printed yet."
    : reclaimed
      ? `A later candle closed back through the open of ${round(bar.open)}.`
      : extended && extreme !== null
        ? `A later close reached ${round(extreme)}, ${side === "Sell" ? "under" : "over"} this candle's close, without reclaiming ${round(bar.open)}.`
        : "Later candles have not extended the close, and they have not reclaimed the open.";
  const sessionMove = sessionOpen > 0 ? (sessionClose / sessionOpen - 1) * 100 : 0;
  const oiName = oi ? readingOf(sessionMove, oi.change) : "Unavailable";
  const prior = oi ? oi.oi - oi.change : 0;
  const oiPercent = oi && prior > 0 ? round((oi.change / prior) * 100) : null;
  const oiDetail = !oi
    ? `The futures file for ${bar.date} is not available yet.`
    : `That session went from ${round(sessionOpen)} to ${round(sessionClose)}. Combined futures open interest ${oi.change >= 0 ? "rose" : "fell"} ${Math.abs(oi.change).toLocaleString("en-IN")}${oiPercent === null ? "" : ` (${oiPercent >= 0 ? "+" : ""}${oiPercent}%)`}. That is ${oiName.toLowerCase()}.`;
  let decision: VolumeDecisionName = "Stand aside";
  let reason = "The close sat in the middle of the bar, so it does not choose a side.";
  if (side !== "Middle" && later.length === 0) {
    decision = "Waiting";
    reason = "The volume candle has no later 10-minute candle to confirm it.";
  } else if (side !== "Middle" && reclaimed) {
    decision = "Stand aside";
    reason = "Later candles closed back through the volume candle's open.";
  } else if (side !== "Middle" && !extended) {
    decision = "Waiting";
    reason = "The follow-up has not closed beyond the volume candle yet.";
  } else if (side !== "Middle" && !oi) {
    decision = "Waiting";
    reason = "The follow-up is there. The futures file for that session is not out, so open interest cannot confirm it.";
  } else if (side === "Sell" && followUp && oiName === "New shorts") {
    decision = "Sell";
    reason = "The candle closed near its low, later prices followed it down, and futures open interest rose. That is a sell.";
  } else if (side === "Buy" && followUp && oiName === "New longs") {
    decision = "Buy";
    reason = "The candle closed near its high, later prices followed it up, and futures open interest rose. That is a buy.";
  } else if (side !== "Middle" && followUp) {
    decision = "Stand aside";
    reason = `The candle and the follow-up point ${side === "Sell" ? "down" : "up"}, but the session's futures open interest reads ${oiName.toLowerCase()}. That does not support it.`;
  }
  return {
    time: bar.label,
    date: bar.date,
    open: round(bar.open),
    high: round(bar.high),
    low: round(bar.low),
    close: round(bar.close),
    volumeMultiple: round(multiple, 1),
    closeLocation: round(closeLocation, 2),
    side,
    followUp,
    followUpDetail,
    oi: oiName,
    oiDetail,
    decision,
    reason,
  };
}

export async function volumeDecision(input: string): Promise<VolumeReport | VolumeFailure> {
  try {
    const resolved = resolveSymbol(input);
    if ("ok" in resolved) return resolved;
    if (resolved.kind === "index") {
      return {
        ok: true,
        applicable: false,
        symbol: resolved.input,
        candles: [],
        decision: "None",
        reason: "A 10-minute volume decision uses a stock's cash bars and its futures open interest. An index is outside this section.",
        note: "",
      };
    }
    const bars = await tenMinuteBars(resolved.yahoo);
    if (bars.length < 10) throw new Error("Not enough 10-minute bars to judge volume.");
    const flagged: { index: number; multiple: number }[] = [];
    for (let index = 0; index < bars.length; index += 1) {
      const bar = bars[index];
      const others = bars.filter((item, position) => item.date === bar.date && position !== index && item.volume >= 500).map((item) => item.volume);
      const sessionMedian = median(others);
      const trailMedian = median(bars.slice(Math.max(0, index - 12), index).map((item) => item.volume));
      if (sessionMedian <= 0) continue;
      const versusSession = bar.volume / sessionMedian;
      const versusTrail = trailMedian > 0 ? bar.volume / trailMedian : versusSession;
      if (versusSession >= 6 && versusTrail >= 4) flagged.push({ index, multiple: versusSession });
    }
    const chosen = flagged.slice(-3);
    const candles: VolumeCandle[] = [];
    for (const item of chosen) {
      const bar = bars[item.index];
      const session = bars.filter((candidate) => candidate.date === bar.date);
      const oi = await sessionOi(resolved.input, bar.date);
      candles.push(buildCandle(bars, item.index, item.multiple, oi, session[0].open, session[session.length - 1].close));
    }
    const latest = [...candles].reverse().find((candle) => candle.decision === "Buy" || candle.decision === "Sell") ?? candles[candles.length - 1];
    return {
      ok: true,
      applicable: true,
      symbol: resolved.input,
      candles,
      decision: latest?.decision ?? "None",
      note: "A very high 10-minute candle has at least six times the rest of that session's volume and four times the prior hour. The bars are 5-minute prices joined from 9:15 IST. Buy or sell needs a later close beyond that candle, without reclaiming its open, and futures open interest that rose with the move. The open interest is the day's combined futures book, not a 10-minute print. This is a research brief, not an order.",
    };
  } catch (error) {
    const message = error instanceof Error ? error.message : "The 10-minute volume did not load.";
    return { ok: false, error: message };
  }
}
