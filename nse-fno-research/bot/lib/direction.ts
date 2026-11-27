// Last three NSE sessions of futures open interest for the F&O book.
// Price and combined futures open interest versus the previous session decide the reading.

import { inflateRawSync } from "node:zlib";
import { fnoBook } from "./morning";

const USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
const IST_MS = 5.5 * 60 * 60 * 1000;
const DAY_MS = 24 * 60 * 60 * 1000;
const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

export const DIRECTION_RULE = 3;

export type DirectionReading = "New shorts" | "New longs" | "Longs leaving" | "Shorts covering" | "Flat";

export type DirectionDay = {
  date: string;
  label: string;
  close: number;
  priceChange: number;
  oiChangePercent: number;
  reading: DirectionReading;
};

export type DirectionRow = {
  symbol: string;
  name: string;
  sector: string;
  close: number;
  change3: number;
  days: DirectionDay[];
  shortDays: number;
  longDays: number;
};

export type FuturesLatest = {
  symbol: string;
  name: string;
  sector: string;
  close: number;
  priceChange: number;
  oiChangePercent: number;
  reading: DirectionReading;
};

export type DirectionReport = {
  ok: true;
  ready: true;
  ruleVersion: number;
  sessions: string[];
  builtAt: string;
  shorts: DirectionRow[];
  longs: DirectionRow[];
  gate: { shorts: DirectionRow[]; longs: DirectionRow[] };
  book: FuturesLatest[];
  note: string;
};

export type DirectionFailure = { ok: false; error: string };

type BookRow = { oi: number; change: number; close: number; near: string };

function istClock(now: Date) {
  const shifted = new Date(now.getTime() + IST_MS);
  return {
    year: shifted.getUTCFullYear(),
    month: shifted.getUTCMonth(),
    day: shifted.getUTCDate(),
    weekday: shifted.getUTCDay(),
    minutes: shifted.getUTCHours() * 60 + shifted.getUTCMinutes(),
  };
}

function shift(year: number, month: number, day: number, days: number) {
  const date = new Date(Date.UTC(year, month, day) + days * DAY_MS);
  return { year: date.getUTCFullYear(), month: date.getUTCMonth(), day: date.getUTCDate(), weekday: date.getUTCDay() };
}

function dateKey(year: number, month: number, day: number): string {
  return `${year}-${String(month + 1).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
}

function labelOf(key: string): string {
  const [year, month, day] = key.split("-").map(Number);
  return `${day} ${MONTHS[month - 1]}`;
}

function previousWeekday(year: number, month: number, day: number) {
  let cursor = { year, month, day, weekday: new Date(Date.UTC(year, month, day)).getUTCDay() };
  for (let step = 0; step < 7; step += 1) {
    cursor = shift(cursor.year, cursor.month, cursor.day, -1);
    if (cursor.weekday !== 0 && cursor.weekday !== 6) return cursor;
  }
  return cursor;
}

export function expectedSession(now = new Date()): string {
  const clock = istClock(now);
  let cursor = { year: clock.year, month: clock.month, day: clock.day, weekday: clock.weekday };
  const afterClose = clock.weekday !== 0 && clock.weekday !== 6 && clock.minutes >= 18 * 60 + 30;
  if (!afterClose) cursor = previousWeekday(cursor.year, cursor.month, cursor.day);
  while (cursor.weekday === 0 || cursor.weekday === 6) cursor = previousWeekday(cursor.year, cursor.month, cursor.day);
  return dateKey(cursor.year, cursor.month, cursor.day);
}

function round(value: number, digits = 2): number {
  const scale = 10 ** digits;
  return Math.round(value * scale) / scale;
}

function readingOf(priceChange: number, oiChange: number): DirectionReading {
  if (Math.abs(priceChange) < 0.3 || oiChange === 0) return "Flat";
  if (priceChange > 0 && oiChange > 0) return "New longs";
  if (priceChange > 0) return "Shorts covering";
  if (oiChange > 0) return "New shorts";
  return "Longs leaving";
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

function columnIndex(header: string[]): Record<string, number> {
  const index: Record<string, number> = {};
  header.forEach((name, position) => {
    index[name] = position;
  });
  return index;
}

async function loadBook(ymd: string, symbols: Set<string>): Promise<Map<string, BookRow> | null> {
  const url = `https://nsearchives.nseindia.com/content/fo/BhavCopy_NSE_FO_0_0_0_${ymd}_F_0000.csv.zip`;
  const response = await fetch(url, {
    headers: { "user-agent": USER_AGENT, accept: "application/zip,*/*" },
    signal: AbortSignal.timeout(40_000),
  });
  if (response.status === 404) return null;
  if (!response.ok) throw new Error(`NSE F&O file for ${ymd} returned HTTP ${response.status}.`);
  const csv = unzipCsv(Buffer.from(await response.arrayBuffer()));
  const lines = csv.split(/\r?\n/);
  const header = lines[0]?.split(",") ?? [];
  const col = columnIndex(header);
  const book = new Map<string, BookRow>();
  for (const line of lines.slice(1)) {
    if (!line) continue;
    const cell = line.split(",");
    if (cell[col.FinInstrmTp] !== "STF") continue;
    const symbol = cell[col.TckrSymb];
    if (!symbols.has(symbol)) continue;
    const oi = Number(cell[col.OpnIntrst] || 0);
    const change = Number(cell[col.ChngInOpnIntrst] || 0);
    const close = Number(cell[col.UndrlygPric] || 0);
    const expiry = cell[col.XpryDt] || "9999";
    const current = book.get(symbol);
    if (!current) {
      book.set(symbol, { oi, change, close, near: expiry });
      continue;
    }
    current.oi += oi;
    current.change += change;
    if (expiry < current.near) {
      current.close = close;
      current.near = expiry;
    }
  }
  return book;
}

function sessionDates(now: Date): string[] {
  const clock = istClock(now);
  const found: string[] = [];
  let cursor = { year: clock.year, month: clock.month, day: clock.day };
  for (let step = 0; step < 16 && found.length < 8; step += 1) {
    const weekday = new Date(Date.UTC(cursor.year, cursor.month, cursor.day)).getUTCDay();
    if (weekday !== 0 && weekday !== 6) found.push(dateKey(cursor.year, cursor.month, cursor.day).replace(/-/g, ""));
    cursor = shift(cursor.year, cursor.month, cursor.day, -1);
  }
  return found;
}

export async function directionReport(now = new Date()): Promise<DirectionReport | DirectionFailure> {
  try {
    const symbols = new Set(fnoBook().map((name) => name.symbol));
    const books: { key: string; rows: Map<string, BookRow> }[] = [];
    for (const ymd of sessionDates(now)) {
      const rows = await loadBook(ymd, symbols);
      if (!rows) continue;
      const key = `${ymd.slice(0, 4)}-${ymd.slice(4, 6)}-${ymd.slice(6, 8)}`;
      books.push({ key, rows });
      if (books.length === 4) break;
    }
    if (books.length < 4) return { ok: false, error: "The futures file did not have four sessions to compare." };
    books.reverse();
    const prior = books[0];
    const sessions = books.slice(1);
    const rows: DirectionRow[] = [];
    for (const name of fnoBook()) {
      if (sessions.some((day) => !day.rows.has(name.symbol)) || !prior.rows.has(name.symbol)) continue;
      let previous = prior.rows.get(name.symbol)!;
      const days: DirectionDay[] = [];
      for (const day of sessions) {
        const current = day.rows.get(name.symbol)!;
        const priceChange = previous.close > 0 ? (current.close / previous.close - 1) * 100 : 0;
        const oiChangePercent = previous.oi > 0 ? (current.change / previous.oi) * 100 : 0;
        days.push({
          date: day.key,
          label: labelOf(day.key),
          close: round(current.close),
          priceChange: round(priceChange),
          oiChangePercent: round(oiChangePercent),
          reading: readingOf(priceChange, current.change),
        });
        previous = current;
      }
      const first = prior.rows.get(name.symbol)!.close;
      const last = days[days.length - 1];
      rows.push({
        symbol: name.symbol,
        name: name.match,
        sector: name.sector,
        close: last.close,
        change3: first > 0 ? round((last.close / first - 1) * 100) : 0,
        days,
        shortDays: days.filter((day) => day.reading === "New shorts").length,
        longDays: days.filter((day) => day.reading === "New longs").length,
      });
    }
    const shorts = rows.filter((row) => row.shortDays > 0).sort((a, b) => b.shortDays - a.shortDays || a.change3 - b.change3);
    const longs = rows.filter((row) => row.longDays > 0).sort((a, b) => b.longDays - a.longDays || b.change3 - a.change3);
    return {
      ok: true,
      ready: true,
      ruleVersion: DIRECTION_RULE,
      sessions: sessions.map((day) => day.key),
      builtAt: now.toISOString(),
      shorts,
      longs,
      gate: {
        shorts: shorts.filter((row) => row.shortDays >= 2).slice(0, 3),
        longs: longs.filter((row) => row.longDays >= 2).slice(0, 3),
      },
      book: rows.map((row) => {
        const day = row.days[row.days.length - 1];
        return {
          symbol: row.symbol,
          name: row.name,
          sector: row.sector,
          close: row.close,
          priceChange: day.priceChange,
          oiChangePercent: day.oiChangePercent,
          reading: day.reading,
        };
      }),
      note: "New shorts is a lower close with futures open interest up. New longs is a higher close with futures open interest up. The percent next to the close is versus the previous session. The open-interest percent is the combined change in the near month and the next month, versus the previous book. One session is a print. Two or three sessions in the same reading is the direction. These labels are a research brief, not an order.",
    };
  } catch (error) {
    const message = error instanceof Error ? error.message : "The futures direction did not load.";
    return { ok: false, error: message };
  }
}
