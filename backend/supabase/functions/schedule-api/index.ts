// Studibox schedule API (Supabase Edge Function, Deno).
//
// Маршруты:
//   GET  /api/health
//   GET  /api/version
//   GET  /api/groups
//   GET  /schedule/{group}      — БЕЗ префикса /api/, см. комментарий ниже
//   GET  /api/debug/dates[?group=]
//   GET  /api/debug/parse   — что парсер видит в файле (без записи в базу)
//   POST /api/import   — проверка Яндекс.Диска и импорт нового XLSX
//
// Ключевое правило: день недели вычисляется из ISO-даты,
// текст «Понедельник/Вторник/...» из XLSX никогда не имеет приоритета.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.4";
import * as XLSX from "https://esm.sh/xlsx@0.18.5";

// Источник расписания. Можно переопределить переменной окружения
// SCHEDULE_SOURCE_URL, не трогая код.
const SOURCE_URL = Deno.env.get("SCHEDULE_SOURCE_URL") ??
  "https://disk.360.yandex.ru/i/bfF89K1c0tsG3g";

// Запасной источник, если основной окажется недоступен.
const FALLBACK_SOURCE_URL = Deno.env.get("SCHEDULE_SOURCE_URL_FALLBACK") ??
  "https://docs.360.yandex.ru/view/d/eSjfjNM06Zcyzwqv5124yiPegnqahzm72s0qoIz-cKg6Y1haOWRpYXFSZw";

const YANDEX_DOWNLOAD_API =
  "https://cloud-api.yandex.net/v1/disk/public/resources/download?public_key=";

// Как часто backend сам проверяет источник, если его дёргают запросами.
const AUTO_CHECK_INTERVAL_MS = 30 * 60 * 1000;

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

const db = createClient(SUPABASE_URL, SERVICE_KEY, {
  auth: { persistSession: false },
});

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...CORS, "Content-Type": "application/json; charset=utf-8" },
  });
}

// ---------------------------------------------------------------- даты

/** 1 = понедельник ... 7 = воскресенье. Только из даты, не из текста. */
function isoDayOfWeek(date: Date): number {
  const dow = date.getUTCDay(); // 0 = воскресенье
  return dow === 0 ? 7 : dow;
}

function toIso(date: Date): string {
  return date.toISOString().slice(0, 10);
}

function addDays(date: Date, n: number): Date {
  return new Date(date.getTime() + n * 86400 * 1000);
}

/** "02.09.26" — как в исходном XLSX, для диагностических логов. */
function formatDmyShort(date: Date): string {
  const dd = String(date.getUTCDate()).padStart(2, "0");
  const mm = String(date.getUTCMonth() + 1).padStart(2, "0");
  const yy = String(date.getUTCFullYear()).slice(-2);
  return `${dd}.${mm}.${yy}`;
}

/** "02.10.2026" — для CANDIDATE_DATE, где год должен быть виден полностью. */
function formatDmyFull(date: Date): string {
  const dd = String(date.getUTCDate()).padStart(2, "0");
  const mm = String(date.getUTCMonth() + 1).padStart(2, "0");
  return `${dd}.${mm}.${date.getUTCFullYear()}`;
}

/**
 * "updatedAt" — уже существующий ориентир (metadata.updated_at, он же
 * поле updatedAt в /api/version), отдельного механизма под него не
 * заводим. Supabase отдаёт микросекунды (6 знаков дроби), а JS Date
 * понимает максимум миллисекунды — лишние цифры аккуратно отрезаем, сам
 * часовой пояс (+00:00 и т.п.) не трогаем, чтобы instant остался верным.
 */
function parseTimestampAnchor(raw: string | null): Date {
  if (!raw) return new Date();
  const trimmed = raw.replace(/(\.\d{3})\d+/, "$1");
  const parsed = new Date(trimmed);
  return Number.isNaN(parsed.getTime()) ? new Date() : parsed;
}

/** Excel-серийный номер даты -> Date (UTC). */
function excelSerialToDate(serial: number): Date | null {
  if (!Number.isFinite(serial) || serial < 1 || serial > 80000) return null;
  // Excel считает от 1899-12-30 (с учётом известного бага 1900 года).
  const ms = Math.round((serial - 25569) * 86400 * 1000);
  const date = new Date(ms);
  return Number.isNaN(date.getTime()) ? null : date;
}

const DAY_NAMES = [
  "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье",
];

/**
 * Разбор даты из произвольной ячейки/заголовка/имени листа.
 *
 * [assumedYear] используется ТОЛЬКО как последний резерв для формата
 * "ДД.ММ" без года (редкий, но реальный случай — например, когда в
 * исходном XLSX год потерялся при копировании шаблона на последний день
 * недели). Обычный разбор ничего про день недели не знает и не должен
 * знать — это по-прежнему чистая функция даты.
 */
function parseAnyDate(value: unknown, assumedYear?: number): Date | null {
  if (value === null || value === undefined) return null;

  if (value instanceof Date) {
    return Number.isNaN(value.getTime()) ? null : value;
  }

  if (typeof value === "number") {
    return excelSerialToDate(value);
  }

  const text = String(value).trim();
  if (!text) return null;

  // ISO
  let m = text.match(/(\d{4})-(\d{2})-(\d{2})/);
  if (m) return new Date(Date.UTC(+m[1], +m[2] - 1, +m[3]));

  // DD.MM.YYYY / DD/MM/YYYY
  m = text.match(/(\d{1,2})[.\/-](\d{1,2})[.\/-](\d{4})/);
  if (m) return new Date(Date.UTC(+m[3], +m[2] - 1, +m[1]));

  // DD.MM.YY
  m = text.match(/(\d{1,2})[.\/-](\d{1,2})[.\/-](\d{2})(?!\d)/);
  if (m) return new Date(Date.UTC(2000 + +m[3], +m[2] - 1, +m[1]));

  // «16 сентября 2026»
  const months = [
    "январ", "феврал", "март", "апрел", "ма", "июн",
    "июл", "август", "сентябр", "октябр", "ноябр", "декабр",
  ];
  m = text.toLowerCase().match(/(\d{1,2})\s+([а-яё]+)\s*(\d{4})?/);
  if (m) {
    const monthIndex = months.findIndex((prefix) => m![2].startsWith(prefix));
    if (monthIndex >= 0) {
      const year = m[3] ? +m[3] : new Date().getUTCFullYear();
      return new Date(Date.UTC(year, monthIndex, +m[1]));
    }
  }

  // DD.MM БЕЗ года — только если год передан явно извне (см. doc-комментарий).
  // Якорится по всей строке, чтобы не задевать "08:30" или другие похожие
  // по форме, но не являющиеся датой значения.
  if (assumedYear !== undefined) {
    const mPartial = text.match(/^(\d{1,2})[.\/-](\d{1,2})$/);
    if (mPartial) {
      const d = +mPartial[1];
      const mo = +mPartial[2];
      if (d >= 1 && d <= 31 && mo >= 1 && mo <= 12) {
        return new Date(Date.UTC(assumedYear, mo - 1, d));
      }
    }
  }

  // Чистый DDMMYY — имя листа, самый низкий приоритет.
  if (/^\d{6}$/.test(text)) {
    const d = +text.slice(0, 2);
    const mo = +text.slice(2, 4);
    const y = 2000 + +text.slice(4, 6);
    if (d >= 1 && d <= 31 && mo >= 1 && mo <= 12) {
      return new Date(Date.UTC(y, mo - 1, d));
    }
  }

  return null;
}

function normalizeTime(value: unknown): string {
  if (typeof value === "number" && value > 0 && value < 1) {
    // Excel хранит время как долю суток.
    const totalMinutes = Math.round(value * 24 * 60);
    const h = Math.floor(totalMinutes / 60);
    const min = totalMinutes % 60;
    return `${String(h).padStart(2, "0")}:${String(min).padStart(2, "0")}`;
  }
  const text = String(value ?? "").trim();
  const m = text.match(/(\d{1,2})[:.\-](\d{2})/);
  if (!m) return "";
  const h = +m[1];
  const min = +m[2];
  if (h > 23 || min > 59) return "";
  return `${String(h).padStart(2, "0")}:${String(min).padStart(2, "0")}`;
}

// ------------------------------------------------------- загрузка файла

async function sha256Hex(bytes: Uint8Array): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

const XLSX_SIGNATURE = [0x50, 0x4b, 0x03, 0x04]; // ZIP-заголовок, с которого начинается xlsx

/**
 * Ссылка вида https://docs.360.yandex.ru/view/d/<token> — это Яндекс Документы.
 * Внутри token (base64url) лежит "<32 байта хеша>:<id публичного ресурса Диска>",
 * поэтому из неё можно восстановить обычную публичную ссылку Диска.
 */
function diskLinksFromDocsToken(url: string): string[] {
  const match = url.match(/\/view\/d\/([A-Za-z0-9_-]+)/);
  if (!match) return [];
  try {
    let token = match[1].replace(/-/g, "+").replace(/_/g, "/");
    while (token.length % 4 !== 0) token += "=";
    const binary = atob(token);
    const colon = binary.lastIndexOf(":");
    if (colon < 0) return [];
    const id = binary.slice(colon + 1);
    if (!/^[A-Za-z0-9_-]{6,32}$/.test(id)) return [];
    console.log(`Из ссылки Документов извлечён id публичного ресурса: ${id}`);
    return [
      `https://disk.yandex.ru/i/${id}`,
      `https://disk.360.yandex.ru/i/${id}`,
    ];
  } catch (error) {
    console.warn("Не удалось разобрать токен ссылки Документов:", error);
    return [];
  }
}

/** Все public_key-кандидаты, которые имеет смысл попробовать, по приоритету. */
function buildPublicKeyCandidates(): string[] {
  const candidates = [
    ...diskLinksFromDocsToken(SOURCE_URL),
    SOURCE_URL,
    FALLBACK_SOURCE_URL,
    ...diskLinksFromDocsToken(FALLBACK_SOURCE_URL),
  ];
  return [...new Set(candidates.filter(Boolean))];
}

function validateXlsx(bytes: Uint8Array, contentType: string): void {
  if (contentType.includes("text/html")) {
    throw new Error(`получен HTML вместо XLSX (Content-Type: ${contentType})`);
  }
  if (bytes.length < 1024) {
    throw new Error(`файл подозрительно мал: ${bytes.length} байт`);
  }
  if (!XLSX_SIGNATURE.every((byte, i) => bytes[i] === byte)) {
    throw new Error("неверная сигнатура файла, это не XLSX");
  }
}

async function fetchRetry(url: string, init: RequestInit = {}): Promise<Response> {
  let lastError: Error | null = null;
  for (let attempt = 0; attempt < 3; attempt++) {
    try {
      const response = await fetch(url, { ...init, redirect: "follow" });
      if (response.status === 429 || response.status === 503 || response.status === 504) {
        lastError = new Error(`HTTP ${response.status}`);
        await new Promise((resolve) => setTimeout(resolve, 1500 * (attempt + 1)));
        continue;
      }
      return response;
    } catch (error) {
      lastError = error instanceof Error ? error : new Error(String(error));
      await new Promise((resolve) => setTimeout(resolve, 1500 * (attempt + 1)));
    }
  }
  throw lastError ?? new Error("fetch failed");
}

/** Скачивание по прямой ссылке с проверкой содержимого. */
async function fetchAndValidate(href: string): Promise<Uint8Array> {
  const response = await fetchRetry(href, {
    headers: {
      "User-Agent": "Studibox/1.0",
      Accept:
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet, application/octet-stream, */*",
    },
  });
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }
  const contentType = (response.headers.get("content-type") ?? "").toLowerCase();
  const bytes = new Uint8Array(await response.arrayBuffer());
  validateXlsx(bytes, contentType);
  return bytes;
}

export interface DownloadOutcome {
  bytes: Uint8Array;
  strategy: string;
}

/**
 * Достаёт актуальный XLSX. Перебирает стратегии по очереди, пока одна не сработает:
 *   1) Disk API по ссылке, восстановленной из токена Яндекс Документов;
 *   2) Disk API по исходной ссылке как public_key;
 *   3) прямое скачивание ссылки с ?download=1;
 *   4) то же самое для запасной ссылки.
 * Так источник можно поменять на любой публичный линк, и код подстроится сам.
 */
async function downloadXlsx(): Promise<DownloadOutcome> {
  const attempts: string[] = [];

  // Стратегии 1-2: официальный Disk API.
  for (const publicKey of buildPublicKeyCandidates()) {
    try {
      const metaResponse = await fetchRetry(
        YANDEX_DOWNLOAD_API + encodeURIComponent(publicKey),
        { headers: { Accept: "application/json", "User-Agent": "Studibox/1.0" } },
      );
      if (!metaResponse.ok) {
        attempts.push(`disk-api(${publicKey}): HTTP ${metaResponse.status}`);
        continue;
      }
      const meta = await metaResponse.json();
      if (!meta?.href) {
        attempts.push(`disk-api(${publicKey}): нет href в ответе`);
        continue;
      }
      const bytes = await fetchAndValidate(meta.href);
      console.log(`Файл получен через Disk API, public_key=${publicKey}`);
      return { bytes, strategy: `disk-api(${publicKey})` };
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      attempts.push(`disk-api(${publicKey}): ${message}`);
    }
  }

  // Стратегии 3-4: прямое скачивание.
  for (const base of [SOURCE_URL, FALLBACK_SOURCE_URL]) {
    for (const href of [`${base}?download=1`, base]) {
      try {
        const bytes = await fetchAndValidate(href);
        console.log(`Файл получен прямой загрузкой: ${href}`);
        return { bytes, strategy: `direct(${href})` };
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        attempts.push(`direct(${href}): ${message}`);
      }
    }
  }

  throw new Error(
    `не удалось получить XLSX ни одним способом. Попытки: ${attempts.join(" | ")}`,
  );
}

// ------------------------------------------------------------- парсер

interface ParsedLesson {
  group_name: string;
  date: string;
  day_of_week: number;
  lesson_number: number;
  start_time: string;
  end_time: string;
  subject: string;
  teacher: string;
  classroom: string;
  week_type: string;
  subgroup: string;
  type: string;
}

const GROUP_PATTERN = /^[А-ЯЁA-Z]{2,}[-\s][А-ЯЁA-Z0-9\-\s]{2,}$/i;

function looksLikeGroup(value: string): boolean {
  const text = value.trim();
  if (text.length < 3 || text.length > 40) return false;
  if (!/\d/.test(text)) return false;
  return GROUP_PATTERN.test(text);
}

/**
 * Собирает ВСЕ непротиворечивые даты-кандидаты для листа, в порядке
 * приоритета: сначала ячейки внутри листа (построчно), затем имя листа.
 * Раньше бралась только ПЕРВАЯ найденная дата — это было уязвимо ровно к
 * одному конкретному сценарию: если на листе выше самой даты занятия есть
 * ещё и шапка вида «Неделя 14.09–19.09», первой датой-кандидатом для
 * КАЖДОГО дня недели становился понедельник этой шапки. Для дней, у
 * которых шапки нет (или она стоит ниже настоящей даты), всё работало
 * верно — отсюда и выглядело так, будто «ломается только один день».
 * Теперь кандидатов несколько, и parseWorkbook сам выбирает первый из
 * них, ещё не занятый другим листом этого же импорта (см. usedDates).
 */
function collectDateCandidates(rows: unknown[][], sheetName: string): Date[] {
  const candidates: Date[] = [];
  const seenIso = new Set<string>();

  const add = (date: Date | null) => {
    if (!date) return;
    if (date.getUTCFullYear() < 2020 || date.getUTCFullYear() > 2100) return;
    const key = toIso(date);
    if (seenIso.has(key)) return;
    seenIso.add(key);
    candidates.push(date);
  };

  for (let r = 0; r < Math.min(rows.length, 20); r++) {
    for (const cell of rows[r] ?? []) {
      const text = String(cell ?? "");
      if (!/\d/.test(text) && typeof cell !== "number") continue;
      add(parseAnyDate(cell));
    }
  }
  add(parseAnyDate(sheetName));

  return candidates;
}

function daysBetween(a: Date, b: Date): number {
  return Math.abs(a.getTime() - b.getTime()) / 86400_000;
}

/**
 * Среди дат листа выбираем ту, что ближе к ориентиру (последний успешный
 * импорт). В шаблоне XLSX часто лежит «19.10.24» / Excel-сериал 2024 года
 * ВЫШЕ настоящей даты, а имя листа «191026» (= 19.10.2026) идёт последним.
 * Первый кандидат тогда побеждал, и понедельник текущей недели оказывался
 * субботой 2024-10-19 — в приложении на этой неделе его не было.
 */
function chooseSheetDate(
  candidates: Date[],
  usedDates: Set<string>,
  updatedAtAnchor: Date,
): Date | null {
  const unused = candidates.filter((d) => !usedDates.has(toIso(d)));
  if (unused.length === 0) return null;
  const WINDOW_DAYS = 60;
  const nearby = unused.filter((d) => daysBetween(d, updatedAtAnchor) <= WINDOW_DAYS);
  const pool = nearby.length > 0 ? nearby : unused;
  pool.sort((a, b) => daysBetween(a, updatedAtAnchor) - daysBetween(b, updatedAtAnchor));
  return pool[0];
}

/**
 * Резервный проход: ищет "ДД.ММ" без года среди тех же ячеек/имени листа,
 * подставляя год уже надёжно распознанных листов этой недели. Нужен для
 * листов, где основной разбор вообще не нашёл ни одной даты (например,
 * единственная дата на листе записана без года).
 */
function collectDateCandidatesWithAssumedYear(
  rows: unknown[][],
  sheetName: string,
  assumedYear: number,
): Date[] {
  const candidates: Date[] = [];
  const seenIso = new Set<string>();

  const add = (date: Date | null) => {
    if (!date) return;
    const key = toIso(date);
    if (seenIso.has(key)) return;
    seenIso.add(key);
    candidates.push(date);
  };

  for (let r = 0; r < Math.min(rows.length, 20); r++) {
    for (const cell of rows[r] ?? []) {
      add(parseAnyDate(String(cell ?? "").trim(), assumedYear));
    }
  }
  add(parseAnyDate(sheetName, assumedYear));

  return candidates;
}

interface SheetParseResult {
  sheetName: string;
  rawDate: Date;
  lessons: ParsedLesson[]; // date/day_of_week временные (= rawDate), финал — после correctDateSequence
}

interface CorrectedSheet {
  sheetName: string;
  lessons: ParsedLesson[];
  finalIso: string;
  finalDayOfWeek: number;
}

/**
 * Второй, независимый ориентир для дат — ПОМИМО занятости между листами
 * (см. collectDateCandidates/usedDates выше): правдоподобность внутри
 * недельной последовательности, с опорой на updatedAt.
 *
 * Главное правило из задачи: НЕ "месяц updatedAt -> все даты этого
 * месяца". Дата считается подозрительной, только если она НЕ продолжает
 * ни одного из своих соседей по листам (сосед ± 1 календарный день) — а
 * заменяется только тогда, когда у неё есть РОВНО ОДНА правдоподобная
 * альтернатива: тот же день месяца и тот же год, другой месяц, которая
 * встаёт ровно между соседями (или продолжает единственного известного
 * соседа на краю недели) и лежит в разумном окне вокруг updatedAt. Во
 * всех остальных случаях дата не трогается — лучше нестыковка в логах,
 * чем тихая неверная подмена.
 */
function correctDateSequence(
  sheets: SheetParseResult[],
  updatedAtAnchor: Date,
): CorrectedSheet[] {
  const ANCHOR_WINDOW_DAYS = 60;
  const n = sheets.length;
  const finalDates: Date[] = sheets.map((s) => s.rawDate);
  const updatedAtIso = toIso(updatedAtAnchor);

  for (let i = 0; i < n; i++) {
    const raw = sheets[i].rawDate;
    // Предыдущая дата берётся УЖЕ скорректированной (обработка идёт слева
    // направо) — так одна опечатка не мешает проверке следующего дня.
    const prev = i > 0 ? finalDates[i - 1] : null;
    const next = i < n - 1 ? sheets[i + 1].rawDate : null;

    const fitsPrev = prev !== null && toIso(addDays(prev, 1)) === toIso(raw);
    const fitsNext = next !== null && toIso(addDays(raw, 1)) === toIso(next);
    const looksFine = fitsPrev || fitsNext || (prev === null && next === null);

    if (looksFine) {
      const relation = raw.getTime() < updatedAtAnchor.getTime()
        ? "before update date"
        : raw.getTime() > updatedAtAnchor.getTime()
        ? "after update date"
        : "on update date";
      console.log(`UPDATED_AT = ${updatedAtIso}`);
      console.log(`RAW_DATE = ${formatDmyShort(raw)}`);
      console.log(`NORMALIZED_DATE = ${toIso(raw)}`);
      console.log(`REASON = valid date ${relation}`);
      continue;
    }

    // Не вписывается ни в одного соседа — ищем кандидата: тот же день и
    // год, другой месяц, который встаёт ровно между соседями (или
    // продолжает единственного известного).
    let candidate: Date | null = null;
    if (prev !== null && next !== null && toIso(addDays(prev, 1)) === toIso(addDays(next, -1))) {
      candidate = addDays(prev, 1);
    } else if (prev !== null && next === null) {
      candidate = addDays(prev, 1);
    } else if (next !== null && prev === null) {
      candidate = addDays(next, -1);
    }

    const sameDayAndYear = candidate !== null &&
      candidate.getUTCDate() === raw.getUTCDate() &&
      candidate.getUTCFullYear() === raw.getUTCFullYear() &&
      candidate.getUTCMonth() !== raw.getUTCMonth();

    // «19.10.24» рядом с листом 191026: тот же день и месяц, другой год.
    const sameDayAndMonthDifferentYear = candidate !== null &&
      candidate.getUTCDate() === raw.getUTCDate() &&
      candidate.getUTCMonth() === raw.getUTCMonth() &&
      candidate.getUTCFullYear() !== raw.getUTCFullYear();

    const withinAnchorWindow = candidate !== null &&
      Math.abs(candidate.getTime() - updatedAtAnchor.getTime()) <= ANCHOR_WINDOW_DAYS * 86400 * 1000;

    if (
      candidate !== null &&
      withinAnchorWindow &&
      (sameDayAndYear || sameDayAndMonthDifferentYear)
    ) {
      console.log(`UPDATED_AT = ${updatedAtIso}`);
      console.log(`RAW_DATE = ${formatDmyShort(raw)}`);
      console.log(`PREVIOUS_DATE = ${prev ? formatDmyShort(prev) : "-"}`);
      console.log(`NEXT_DATE = ${next ? formatDmyShort(next) : "-"}`);
      console.log(`CANDIDATE_DATE = ${formatDmyFull(candidate)}`);
      console.log(`NORMALIZED_DATE = ${toIso(candidate)}`);
      console.log(`REASON = inconsistent month / surrounding dates`);
      finalDates[i] = candidate;
    } else {
      console.log(`UPDATED_AT = ${updatedAtIso}`);
      console.log(`RAW_DATE = ${formatDmyShort(raw)}`);
      console.log(`PREVIOUS_DATE = ${prev ? formatDmyShort(prev) : "-"}`);
      console.log(`NEXT_DATE = ${next ? formatDmyShort(next) : "-"}`);
      console.log(`NORMALIZED_DATE = ${toIso(raw)}`);
      console.log(`REASON = does not match neighbours, kept as-is (no confident replacement)`);
    }
  }

  return sheets.map((s, i) => {
    const d = finalDates[i];
    return {
      sheetName: s.sheetName,
      lessons: s.lessons,
      finalIso: toIso(d),
      finalDayOfWeek: isoDayOfWeek(d),
    };
  });
}

// Маркер подгруппы: "подгруппа 2" / "подгр. 2" (старый формат) ИЛИ
// "2пг" / "2 пг" / "2 п.г." (формат из реальных файлов — короткое "пг").
// Без \b после кириллицы: в JS \b не распознаёт кириллицу как "словесный"
// символ, поэтому граница задана явно через negative lookahead.
const SUBGROUP_MARKER = /подгр(?:уппа)?\.?\s*(\d)|(\d)\s*п\.?г\.?(?![а-яёa-z])/i;

function matchSubgroup(text: string): string {
  const m = text.match(SUBGROUP_MARKER);
  if (!m) return "";
  return m[1] ?? m[2] ?? "";
}

// Тип занятия определяет, нужно ли вообще делить пару по подгруппам:
// на лабораторной подгруппы занимаются РАЗНЫМ (своя аудитория/иногда свой
// преподаватель), на лекции и практике — все вместе, одной группой,
// даже если это физически несколько подгрупп. Поэтому практика
// приравнена к лекции ("лекции и практика — у всех"), и только
// лабораторная разбивается на отдельные пары по подгруппам.
// Токен должен совпадать ПОЛНОСТЬЮ (^...$) — это короткая колонка
// ("л/р", "лек", "пр"), а не часть длинного текста предмета.
const LAB_TYPE_PATTERN = /^(л\s*\/\s*р\.?|лр\.?|лаб(?:ораторн[а-яё]*)?\.?)$/i;
const LECTURE_TYPE_PATTERN = /^лек(?:ция)?\.?$/i;
const PRACTICE_TYPE_PATTERN = /^(пр(?:актик[а-яё]*)?\.?|сем(?:инар)?\.?)$/i;

function classifyTypeToken(part: string): { isLab: boolean; label: string } | null {
  const t = part.trim();
  if (LAB_TYPE_PATTERN.test(t)) return { isLab: true, label: "Лабораторная" };
  if (LECTURE_TYPE_PATTERN.test(t)) return { isLab: false, label: "Лекция" };
  if (PRACTICE_TYPE_PATTERN.test(t)) return { isLab: false, label: "Практика" };
  return null;
}

interface CellLesson {
  subject: string;
  teacher: string;
  classroom: string;
  subgroup: string;
  type: string;
}

function buildCellLesson(parts: string[], isLab: boolean, typeLabel: string): CellLesson {
  const subject = parts[0] ?? "";
  let teacher = "";
  let classroom = "";

  for (const part of parts.slice(1)) {
    // Колонку типа ("л/р", "лек" и т.п.) сюда не пускаем отдельно — её
    // уже извлекли до вызова этой функции (см. splitCellIntoLessons),
    // иначе она ошибочно попадала бы в преподавателя или аудиторию.
    if (classifyTypeToken(part)) continue;

    const isRoom = /^(ауд\.?|каб\.?|к\.)?\s*[\dA-Za-zА-Яа-я\-]{1,10}$/.test(part) &&
      /\d/.test(part);
    if (isRoom && !classroom) classroom = part.replace(/^(ауд\.?|каб\.?|к\.)\s*/i, "");
    else if (!teacher) teacher = part;
    else if (!classroom) classroom = part;
  }

  // Явная метка "1пг"/"2пг" в названии — это подгруппа при ЛЮБОМ типе
  // занятия: например, «Иностранный язык … 1пг» (практика) идёт только у
  // первой подгруппы, а у второй в это же время свой преподаватель и
  // аудитория. Раньше подгруппа ставилась только лабораторным, и такие
  // пары в приложении выглядели как две разные пары в одно время.
  const subgroup = matchSubgroup(subject);
  const cleanSubject = subgroup
    ? subject.replace(SUBGROUP_MARKER, "").replace(/\s{2,}/g, " ").trim()
    : subject;

  return { subject: cleanSubject, teacher: cleanTeacher(teacher), classroom, subgroup, type: typeLabel };
}

/**
 * Разбирает текст ОДНОЙ ячейки расписания (один день, один предмет-слот
 * одной группы) на одну или НЕСКОЛЬКО пар.
 *
 * Зачем несколько: когда лабораторная поделена на подгруппы, в реальных
 * файлах обе подгруппы часто записаны ВНУТРИ ОДНОЙ ячейки — просто
 * второй строкой (перенос строки внутри ячейки). Раньше парсер брал
 * только первую строку как предмет, а всё остальное пытался раскидать по
 * преподавателю/аудитории ПЕРВОЙ пары — вторая подгруппа при этом
 * терялась целиком ("супабейс не видит именно 2-ю подгруппу").
 *
 * Эвристика: сначала бьём текст на части по переносам строк и обычным
 * разделителям ("/", "|", ";"), отдельно вычисляем тип занятия (первая
 * часть, похожая на "л/р"/"лек"/"пр"). Делим на несколько пар ТОЛЬКО
 * если тип — лабораторная И среди частей есть два и больше маркера
 * подгруппы (SUBGROUP_MARKER) — это несколько слипшихся пар. На
 * лекции/практике, а также когда маркер подгруппы один или ноль, —
 * всегда одна пара на всех, ровно как было раньше.
 */
function splitCellIntoLessons(cellText: string): CellLesson[] {
  const parts = cellText
    .split(/\s*[\n\/|;]\s*/)
    .map((p) => p.trim())
    .filter(Boolean);

  if (parts.length === 0) {
    return [{ subject: cellText, teacher: "", classroom: "", subgroup: "", type: "" }];
  }

  let isLab = false;
  let typeLabel = "";
  for (const part of parts) {
    const classified = classifyTypeToken(part);
    if (classified) {
      isLab = classified.isLab;
      typeLabel = classified.label;
      break;
    }
  }

  if (!isLab) {
    // Лекция/практика/неизвестный тип — всегда одна общая пара, даже
    // если в тексте есть что-то похожее на маркер подгруппы.
    return [buildCellLesson(parts, false, typeLabel)];
  }

  const blockStarts: number[] = [];
  parts.forEach((p, i) => {
    if (SUBGROUP_MARKER.test(p)) blockStarts.push(i);
  });

  if (blockStarts.length < 2) {
    // Лабораторная, но без явного деления на подгруппы в тексте —
    // одна пара, как было раньше.
    return [buildCellLesson(parts, true, typeLabel)];
  }

  const lessons: CellLesson[] = [];
  for (let i = 0; i < blockStarts.length; i++) {
    const start = blockStarts[i];
    const end = i + 1 < blockStarts.length ? blockStarts[i + 1] : parts.length;
    lessons.push(buildCellLesson(parts.slice(start, end), true, typeLabel));
  }
  return lessons;
}

/**
 * ФИО преподавателя: после фамилии и инициалов ничего быть не должно.
 * В исходном файле встречается "Яковлева Л.Е.(Аммосов В.Н.)" — скобки с
 * заменой/вторым лицом убираем вместе с содержимым (в том числе незакрытые).
 * Несколько преподавателей через запятую ("Софронова В.В., Сорокоумова Е.В.")
 * остаются как есть.
 */
function cleanTeacher(value: string): string {
  return value
    .replace(/\s*\([^)]*\)?/g, " ")
    .replace(/\s+/g, " ")
    .replace(/\s+,/g, ",")
    .replace(/^[\s,;]+|[\s,;]+$/g, "");
}

/**
 * Хвост с количеством студентов в заголовке группы. В файле заголовок
 * выглядит как "КИТ-ВР-26-1 - 30 СТУДЕНТОВ", а в базе и приложении нужен
 * только код группы "КИТ-ВР-26-1". Убираем всё, что начинается с
 * разделителя (пробел, дефис/тире, запятая, "(") + число + "студент…"/"чел…",
 * в любом регистре и написании ("30 студентов", "(30 студентов)", "- 1 студент",
 * ", 28 чел."). Число и слово обязательны, поэтому цифры самого кода группы
 * ("26-1") не затрагиваются. Без \b — в JS он не работает с кириллицей.
 */
const STUDENTS_TAIL = /[\s,;(–—-]+\d+\s*(?:студент|чел)[а-яё.]*\)?.*$/i;

function normalizeGroupName(value: string): string {
  let text = value.replace(/\s+/g, " ").trim();
  // В реальном XLSX заголовок группы выглядит, например:
  // "КИТ-ТЭСИС-26-1 - 30 СТУДЕНТОВ".
  // В БД/API нужен именно идентификатор группы без описания количества студентов.
  // Без \b: в JS он не считает кириллицу "словом", поэтому прежняя версия
  // (…студент(?:ов|а)?\b…) не срабатывала НИКОГДА и суффикс оставался в
  // имени группы ("КИТ-ТЭСИС-26-1 - 30 СТУДЕНТОВ" вместо "КИТ-ТЭСИС-26-1").
  text = text.replace(STUDENTS_TAIL, "").trim();
  return text;
}

function looksLikeGroupHeader(value: string): boolean {
  const raw = value.replace(/\s+/g, " ").trim();
  const text = normalizeGroupName(raw);
  if (text.length < 3 || text.length > 40) return false;
  if (!/\d/.test(text)) return false;
  // Хвост "- 30 СТУДЕНТОВ" бывает только у заголовка группы — это надёжнее,
  // чем форма самого названия (в нём могут быть пробелы, необычные символы).
  if (STUDENTS_TAIL.test(raw) && /^[А-ЯЁA-Z]/i.test(text)) return true;
  // Минимум две буквы в начале и дальше сегменты через дефис:
  // КИТ-ТЭСИС-26-1, КИТ-ВР-26. Прежняя проверка была мягче и принимала за
  // заголовок группы номера аудиторий вида "4-12" или "2-405" — такая
  // ячейка посреди расписания обрывала текущую группу и сбрасывала колонки.
  return /^[А-ЯЁA-Z]{2,}[А-ЯЁA-Z0-9]*(?:-[А-ЯЁA-Z0-9]+)+$/i.test(text);
}

function parseTimeRange(raw: unknown): { start: string; end: string } | null {
  const text = String(raw ?? "").trim();
  const m = text.match(/(\d{1,2}[:.]\d{2})\s*[-–—]\s*(\d{1,2}[:.]\d{2})/);
  if (!m) return null;
  const start = normalizeTime(m[1]);
  const end = normalizeTime(m[2]);
  return start && end ? { start, end } : null;
}

function parseTimeRangeFromRow(row: unknown[]): { start: string; end: string } | null {
  for (const raw of row) {
    const found = parseTimeRange(raw);
    if (found) return found;
  }
  return null;
}

function normalizeWorkbookType(value: string): string {
  const t = value.trim();
  if (!t) return "";
  if (LAB_TYPE_PATTERN.test(t)) return "Лабораторная";
  if (/^л$/i.test(t) || LECTURE_TYPE_PATTERN.test(t)) return "Лекция";
  if (PRACTICE_TYPE_PATTERN.test(t)) return "Практика";
  return t;
}

function findColumnByHeader(row: unknown[], names: RegExp[]): number {
  for (let i = 0; i < row.length; i++) {
    const text = String(row[i] ?? "").trim();
    if (!text) continue;
    if (names.some((re) => re.test(text))) return i;
  }
  return -1;
}

/**
 * Реальный XLSX имеет не "группы по колонкам", а блоки по строкам:
 *
 *   КИТ-ТЭСИС-26-1 - 30 СТУДЕНТОВ
 *   Время | Дисциплина | Вид | Преподаватель | Ауд.
 *   14:00-15:35 | Архитектура ... 1пг | л/р | Яковлева Л.Е. | 4
 *               | Основы алгоритмизации ... 2пг | л/р | Кириллина Т.Ц. | 403
 *   15:50-17:25 | ...
 *
 * ВАЖНО про объединённые ячейки. Когда пара поделена на подгруппы, время
 * в Excel объединено по вертикали на обе строки, а SheetJS отдаёт значение
 * объединённой ячейки ТОЛЬКО в её верхней строке — во второй строке
 * (то есть для второй подгруппы) колонка "Время" пустая. Раньше такие
 * строки молча отбрасывались (`if (!time) continue`) — вторая подгруппа
 * терялась целиком, а там, где их всё же оставляли, в таблице получалось
 * пустое время. Теперь время (и, если оно тоже объединено, вид занятия)
 * переносится вниз с предыдущей строки того же блока группы.
 */
function parseRowOrientedWorkbook(
  rows: unknown[][],
  rawDate: Date,
  sheetName: string,
): ParsedLesson[] {
  const lessons: ParsedLesson[] = [];
  let currentGroup = "";
  let timeColumn = -1;
  let subjectColumn = -1;
  let typeColumn = -1;
  let teacherColumn = -1;
  let roomColumn = -1;

  // Последнее встретившееся время / вид занятия в текущем блоке — для строк,
  // у которых соответствующая ячейка объединена с верхней и потому пустая.
  let lastTime: { start: string; end: string } | null = null;
  let lastType = "";

  for (let r = 0; r < rows.length; r++) {
    const row = rows[r] ?? [];
    const rowText = row.map((v) => String(v ?? "").trim()).filter(Boolean).join(" | ");
    if (!rowText) continue;

    // 1. Заголовок группы. Искать нужно по ВСЕМ строкам листа.
    for (const cell of row) {
      const text = String(cell ?? "").trim();
      if (text && looksLikeGroupHeader(text)) {
        const group = normalizeGroupName(text);
        if (group) {
          currentGroup = group;
          // Новый блок группы начинается заново: колонки и перенос вниз сбрасываем.
          timeColumn = subjectColumn = typeColumn = teacherColumn = roomColumn = -1;
          lastTime = null;
          lastType = "";
          console.log(`GROUP_HEADER sheet=${sheetName} row=${r + 1} group=${currentGroup}`);
          break;
        }
      }
    }

    // 2. Строка с названиями колонок. Индексы не захардкожены: ищем по тексту.
    const detectedTime = findColumnByHeader(row, [/^время$/i]);
    const detectedSubject = findColumnByHeader(row, [/^дисциплин/i]);
    const detectedType = findColumnByHeader(row, [/^вид(\s+занят\S*)?$/i]);
    const detectedTeacher = findColumnByHeader(row, [/^преподавател/i]);
    const detectedRoom = findColumnByHeader(row, [/^ауд\.?$/i, /^аудитор/i]);

    if (detectedTime >= 0) timeColumn = detectedTime;
    if (detectedSubject >= 0) subjectColumn = detectedSubject;
    if (detectedType >= 0) typeColumn = detectedType;
    if (detectedTeacher >= 0) teacherColumn = detectedTeacher;
    if (detectedRoom >= 0) roomColumn = detectedRoom;

    const isColumnHeaderRow = detectedTime >= 0 || detectedSubject >= 0 ||
      detectedType >= 0 || detectedTeacher >= 0 || detectedRoom >= 0;
    if (isColumnHeaderRow) {
      // Сама строка заголовков занятием не является и не должна наследовать время.
      lastTime = null;
      lastType = "";
      continue;
    }

    if (!currentGroup) continue;

    // 3. Время строки: своё, а если ячейка пустая (объединена) — от предыдущей строки.
    const ownTime = timeColumn >= 0 ? parseTimeRange(row[timeColumn]) : parseTimeRangeFromRow(row);
    if (ownTime) lastTime = ownTime;

    const subject = subjectColumn >= 0 ? String(row[subjectColumn] ?? "").trim() : "";
    if (!subject || subject === "-" || subject === "—") continue;

    const time = ownTime ?? lastTime;
    if (!time) continue; // ни своего времени, ни времени от предыдущей строки блока
    const timeInherited = !ownTime;

    const typeCell = typeColumn >= 0 ? String(row[typeColumn] ?? "").trim() : "";
    // Новый временной слот — вид занятия начинается заново; та же объединённая
    // ячейка вида (пустая во второй строке) — наследуем от первой.
    if (ownTime) lastType = typeCell;
    else if (typeCell) lastType = typeCell;
    const typeRaw = typeCell || (timeInherited ? lastType : "");

    const teacher = teacherColumn >= 0 ? cleanTeacher(String(row[teacherColumn] ?? "")) : "";
    const classroom = roomColumn >= 0
      ? String(row[roomColumn] ?? "").trim().replace(/^(ауд\.?|каб\.?|к\.)\s*/i, "")
      : "";

    // Строка без своего времени и без преподавателя/аудитории/вида — это не
    // вторая подгруппа, а, скорее всего, примечание под таблицей: не считаем парой.
    if (timeInherited && !teacher && !classroom && !typeCell) continue;

    // Явная метка "1пг"/"2пг" в названии — подгруппа при любом типе
    // занятия (см. buildCellLesson): практика иностранного языка тоже
    // бывает поделена на подгруппы с разными преподавателями.
    const subgroup = matchSubgroup(subject);
    const cleanSubject = subgroup
      ? subject.replace(SUBGROUP_MARKER, "").replace(/\s{2,}/g, " ").trim()
      : subject;
    const type = normalizeWorkbookType(typeRaw);

    lessons.push({
      group_name: currentGroup,
      date: toIso(rawDate),
      day_of_week: isoDayOfWeek(rawDate),
      lesson_number: 0, // проставляется в assignLessonNumbers по всему файлу
      start_time: time.start,
      end_time: time.end,
      subject: cleanSubject,
      teacher,
      classroom,
      week_type: "",
      subgroup,
      type,
    });

    console.log(
      `PARSED group=${currentGroup} date=${toIso(rawDate)} time=${time.start}-${time.end}` +
        `${timeInherited ? "(из предыдущей строки)" : ""} ` +
        `subject=${JSON.stringify(cleanSubject)} subgroup=${subgroup || "-"} ` +
        `type=${type || "-"} teacher=${JSON.stringify(teacher)} room=${JSON.stringify(classroom)}`,
    );
  }

  return lessons;
}

/**
 * Старый формат XLSX (группы действительно находятся в колонках) остаётся
 * поддержанным как fallback: если в листе нет занятий в строчном формате
 * (parseRowOrientedWorkbook), используется прежняя схема.
 */
function parseColumnOrientedWorkbook(
  rows: unknown[][],
  rawDate: Date,
  sheetName: string,
): ParsedLesson[] {
  const lessons: ParsedLesson[] = [];

  let headerRow = -1;
  let bestScore = 0;
  for (let r = 0; r < Math.min(rows.length, 100); r++) {
    const score = (rows[r] ?? []).filter((c) => looksLikeGroup(String(c ?? ""))).length;
    if (score > bestScore) {
      bestScore = score;
      headerRow = r;
    }
  }
  if (headerRow < 0 || bestScore === 0) return lessons;

  const groupColumns = new Map<number, string>();
  (rows[headerRow] ?? []).forEach((cell, index) => {
    const name = String(cell ?? "").trim().replace(/\s+/g, " ");
    if (looksLikeGroup(name)) groupColumns.set(index, normalizeGroupName(name));
  });
  if (groupColumns.size === 0) return lessons;

  const firstGroupColumn = Math.min(...groupColumns.keys());

  for (let r = headerRow + 1; r < rows.length; r++) {
    const row = rows[r] ?? [];
    let startTime = "";
    let endTime = "";

    for (let c = 0; c < firstGroupColumn; c++) {
      const text = String(row[c] ?? "").trim();
      const times = text.match(/(\d{1,2}[:.]\d{2})/g);
      if (times && times.length >= 2) {
        startTime = normalizeTime(times[0]);
        endTime = normalizeTime(times[1]);
        break;
      }
    }
    if (!startTime || !endTime) continue;

    for (const [column, groupName] of groupColumns) {
      const cellText = String(row[column] ?? "").trim();
      if (!cellText || cellText === "-" || cellText === "—") continue;

      for (const lesson of splitCellIntoLessons(cellText)) {
        lessons.push({
          group_name: groupName,
          date: toIso(rawDate),
          day_of_week: isoDayOfWeek(rawDate),
          lesson_number: 0,
          start_time: startTime,
          end_time: endTime,
          subject: lesson.subject,
          teacher: lesson.teacher,
          classroom: lesson.classroom,
          week_type: "",
          subgroup: lesson.subgroup,
          type: lesson.type,
        });
      }
    }
  }

  console.log(`COLUMN_FORMAT sheet=${sheetName} lessons=${lessons.length}`);
  return lessons;
}

/**
 * Номер пары — это порядковый номер времени начала в сетке расписания всего
 * файла (08:00 → 1, 09:50 → 2, ...), а не номер строки и не порядок появления
 * времени в листе: у группы, у которой первый день начинается с 09:50, это
 * всё равно 2-я пара, а у двух строк подгрупп одного слота номер одинаковый.
 */
function assignLessonNumbers(lessons: ParsedLesson[]): void {
  const starts = [...new Set(lessons.map((l) => l.start_time))].sort();
  const rank = new Map(starts.map((s, i) => [s, i + 1]));
  for (const lesson of lessons) lesson.lesson_number = rank.get(lesson.start_time) ?? 0;
}

function mergeUnique(a: string, b: string, separator: string): string {
  const parts = a ? a.split(separator).map((p) => p.trim()).filter(Boolean) : [];
  for (const piece of b.split(separator).map((p) => p.trim()).filter(Boolean)) {
    if (!parts.includes(piece)) parts.push(piece);
  }
  return parts.join(separator === "/" ? "/" : ", ");
}

/**
 * Занятия с одним и тем же ключом уникальности таблицы
 * (group_name, date, lesson_number, subgroup, subject) нельзя отправлять в
 * один upsert дважды — Postgres отвечает "ON CONFLICT DO UPDATE command
 * cannot affect row a second time" и ВЕСЬ импорт падает. Такое бывает, когда
 * одна пара с двумя преподавателями записана двумя строками: объединяем их в
 * одно занятие со списком преподавателей и аудиторий (это и есть одна пара).
 */
function dedupeLessons<T extends {
  group_name: string;
  date: string;
  lesson_number: number;
  subgroup: string;
  subject: string;
  teacher: string;
  classroom: string;
}>(items: T[]): T[] {
  const byKey = new Map<string, T>();
  for (const item of items) {
    const key = [item.group_name, item.date, item.lesson_number, item.subgroup, item.subject].join("|");
    const existing = byKey.get(key);
    if (!existing) {
      byKey.set(key, { ...item });
      continue;
    }
    existing.teacher = mergeUnique(existing.teacher, item.teacher, ",");
    existing.classroom = mergeUnique(existing.classroom, item.classroom, "/");
  }
  return [...byKey.values()];
}

function parseWorkbook(bytes: Uint8Array, updatedAtAnchor: Date): ParsedLesson[] {
  const workbook = XLSX.read(bytes, { type: "array", cellDates: true });
  const sheetResults: SheetParseResult[] = [];
  const usedDates = new Set<string>();

  for (const sheetName of workbook.SheetNames) {
    const sheet = workbook.Sheets[sheetName];
    const rows = XLSX.utils.sheet_to_json<unknown[]>(sheet, {
      header: 1,
      raw: true,
      defval: "",
    });
    if (!rows.length) continue;

    const candidates = collectDateCandidates(rows, sheetName);
    let sheetDate = chooseSheetDate(candidates, usedDates, updatedAtAnchor);

    if (!sheetDate) {
      const anchorIso = [...usedDates][0];
      const anchorYear = anchorIso ? +anchorIso.slice(0, 4) : updatedAtAnchor.getUTCFullYear();
      const fallbackCandidates = collectDateCandidatesWithAssumedYear(
        rows,
        sheetName,
        anchorYear,
      );
      sheetDate = chooseSheetDate(fallbackCandidates, usedDates, updatedAtAnchor);
    }

    if (!sheetDate) {
      console.warn(`Лист "${sheetName}": дата не определена, лист пропущен`);
      continue;
    }

    usedDates.add(toIso(sheetDate));

    // Сначала реальный формат: группы-блоки по строкам.
    let sheetLessons = parseRowOrientedWorkbook(rows, sheetDate, sheetName);

    // Старый формат, где группы являются колонками, — как запасной.
    if (sheetLessons.length === 0) {
      sheetLessons = parseColumnOrientedWorkbook(rows, sheetDate, sheetName);
    }

    console.log(
      `SHEET_PARSED sheet=${sheetName} rawDate=${toIso(sheetDate)} lessons=${sheetLessons.length}`,
    );

    sheetResults.push({
      sheetName,
      rawDate: sheetDate,
      lessons: sheetLessons,
    });
  }

  const corrected = correctDateSequence(sheetResults, updatedAtAnchor);
  const lessons: ParsedLesson[] = [];

  for (const { finalIso, finalDayOfWeek, lessons: sheetLessons } of corrected) {
    for (const lesson of sheetLessons) {
      lesson.date = finalIso;
      lesson.day_of_week = finalDayOfWeek;
    }
    lessons.push(...sheetLessons);

    console.log(`date = ${finalIso}`);
    console.log(`dayOfWeek = ${finalDayOfWeek}`);
    console.log(`dayName = ${DAY_NAMES[finalDayOfWeek - 1] ?? ""}`);
    console.log(`lessonsCount = ${sheetLessons.length}`);
  }

  assignLessonNumbers(lessons);
  return lessons;
}


/**
 * Диагностика разбора БЕЗ записи в базу (GET /api/debug/parse): что парсер
 * видит в каждом листе файла. Нужна, чтобы по одному ответу понять, почему
 * какие-то группы не попали в расписание: показывает все ячейки, похожие на
 * заголовок группы, принял их парсер или нет, и сколько занятий нашёл по
 * каждой группе, плюс первые строки листа как есть.
 */
function debugParseWorkbook(bytes: Uint8Array): unknown {
  const workbook = XLSX.read(bytes, { type: "array", cellDates: true });
  const sheets = [];

  for (const sheetName of workbook.SheetNames) {
    const rows = XLSX.utils.sheet_to_json<unknown[]>(workbook.Sheets[sheetName], {
      header: 1,
      raw: true,
      defval: "",
    });

    const dateCandidates = collectDateCandidates(rows, sheetName).map(toIso);

    const headerCandidates: Array<Record<string, unknown>> = [];
    rows.forEach((row, r) => {
      (row ?? []).forEach((cell, c) => {
        const raw = String(cell ?? "").trim();
        if (!raw) return;
        if (/студент|чел/i.test(raw) || looksLikeGroupHeader(raw)) {
          headerCandidates.push({
            row: r + 1,
            col: c + 1,
            raw: raw.slice(0, 70),
            normalized: normalizeGroupName(raw),
            accepted: looksLikeGroupHeader(raw),
          });
        }
      });
    });

    const lessonsByGroup: Record<string, number> = {};
    if (dateCandidates.length > 0) {
      const originalLog = console.log;
      console.log = () => {}; // подробные PARSED-логи здесь не нужны
      try {
        for (const l of parseRowOrientedWorkbook(rows, new Date(dateCandidates[0]), sheetName)) {
          lessonsByGroup[l.group_name] = (lessonsByGroup[l.group_name] ?? 0) + 1;
        }
      } finally {
        console.log = originalLog;
      }
    }

    sheets.push({
      sheet: sheetName,
      rows: rows.length,
      dateCandidates: dateCandidates.slice(0, 5),
      groupHeaderCandidates: headerCandidates.slice(0, 80),
      lessonsByGroup,
      firstRows: rows.slice(0, 8).map((row) =>
        (row ?? []).map((cell) => String(cell ?? "").slice(0, 28))
      ),
    });
  }

  return { sheets };
}

// -------------------------------------------------------------- импорт

async function getMeta(key: string): Promise<string | null> {
  const { data } = await db.from("metadata").select("value").eq("key", key).maybeSingle();
  return data?.value ?? null;
}

async function setMeta(key: string, value: string): Promise<void> {
  await db.from("metadata").upsert({ key, value, updated_at: new Date().toISOString() });
}

async function runImport(force: boolean): Promise<Response> {
  const startedAt = new Date().toISOString();
  let sha = "";

  try {
    const { bytes, strategy } = await downloadXlsx();
    await setMeta("source_strategy", strategy);
    await setMeta("last_checked_at", new Date().toISOString());
    sha = await sha256Hex(bytes);
    const previousSha = await getMeta("source_sha256");

    if (!force && previousSha === sha) {
      console.log("Файл не изменился, импорт не требуется");
      // Иначе /api/version навсегда показывает прошлый 504 Яндекса, хотя
      // файл уже снова скачивается, и кажется, что обновление «сломалось».
      await setMeta("status", "ok");
      await setMeta("message", "Файл не изменился");
      return json({ status: "unchanged", sha256: sha, strategy, startedAt });
    }

    // Ориентир "актуальный период расписания" — уже существующее
    // metadata.updated_at (оно же updatedAt в /api/version), отдельного
    // нового поля под это не заводим. Берём значение ДО того, как этот
    // импорт его перезапишет: это момент последнего УСПЕШНОГО обновления,
    // то есть прошлый "известно хороший" период. Если это самый первый
    // импорт и прошлого значения ещё нет — используем текущее время.
    const updatedAtAnchor = parseTimestampAnchor(await getMeta("updated_at"));

    // Полный разбор ДО любых изменений в рабочих данных.
    const lessons = parseWorkbook(bytes, updatedAtAnchor);
    const groupNames = [...new Set(lessons.map((l) => l.group_name))].sort();

    if (lessons.length === 0 || groupNames.length === 0) {
      throw new Error(
        `Импорт отклонён: занятий ${lessons.length}, групп ${groupNames.length}`,
      );
    }

    // Санити-проверка: новый файл не должен терять больше половины занятий.
    //
    // БЫЛО: сравнивали с количеством строк ВО ВСЕЙ таблице schedule —
    // а таблица копится без ограничения (удаляются только даты, которые
    // попадают в ТЕКУЩИЙ импорт, остальная история остаётся навсегда,
    // это намеренно — см. комментарии у runImport ниже). Один импорт
    // разбирает ОДНУ неделю расписания, поэтому уже через несколько
    // недель накопленная сумма неизбежно становится намного больше
    // одной недели — и эта проверка начинала отклонять АБСОЛЮТНО ЛЮБОЙ
    // последующий импорт навсегда, даже полностью корректный. Из-за
    // этого расписание переставало обновляться и в итоге "пропадало"
    // целиком, как только все сохранённые даты оказывались в прошлом.
    //
    // СТАЛО: сравниваем с тем, сколько занятий СЕЙЧАС лежит в Supabase
    // ИМЕННО за те даты, которые этот импорт собирается заменить —
    // яблоки с яблоками, а не неделя против всего семестра.
    const candidateDates = [...new Set(lessons.map((l) => l.date))];
    const { count: previousCountForDates } = candidateDates.length > 0
      ? await db
        .from("schedule")
        .select("id", { count: "exact", head: true })
        .in("date", candidateDates)
      : { count: 0 };

    if (previousCountForDates && lessons.length < previousCountForDates * 0.5) {
      throw new Error(
        `Импорт отклонён: занятий ${lessons.length} против ${previousCountForDates} ` +
          `ранее за те же даты (${candidateDates.join(", ")})`,
      );
    }

    // --- группы ---
    const { error: groupsUpsertError } = await db.from("groups").upsert(
      groupNames.map((name) => ({ name })),
      { onConflict: "name" },
    );
    if (groupsUpsertError) throw groupsUpsertError;
    const { data: groupRows, error: groupError } = await db
      .from("groups")
      .select("id, name");
    if (groupError) throw groupError;

    const groupIdByName = new Map<string, number>(
      (groupRows ?? []).map((row) => [row.name as string, row.id as number]),
    );

    const version = Number(await getMeta("version") ?? "0") + 1;

    // dedupeLessons: одна пара, записанная двумя строками (два преподавателя),
    // не должна уходить в upsert дважды — иначе Postgres роняет весь импорт.
    const payload = dedupeLessons(
      lessons
        .filter((lesson) => groupIdByName.has(lesson.group_name))
        .map((lesson) => ({
          ...lesson,
          group_id: groupIdByName.get(lesson.group_name)!,
          version,
        })),
    );

    // --- замена расписания ---
    // Порядок важен: сначала записываем НОВЫЕ занятия, и только потом удаляем
    // устаревшие за те же даты. Раньше было наоборот (удалить -> вставить), и
    // любая ошибка вставки оставляла эти даты совсем пустыми.
    const dates = [...new Set(payload.map((l) => l.date))];

    for (let i = 0; i < payload.length; i += 500) {
      const chunk = payload.slice(i, i + 500);
      const { error } = await db.from("schedule").upsert(chunk, {
        onConflict: "group_name,date,lesson_number,subgroup,subject",
      });
      if (error) throw error;
    }

    // Строки за эти даты, не затронутые текущим импортом (другая версия), —
    // это то, чего в новом файле больше нет (в т.ч. старые группы с хвостом).
    const { error: staleError } = await db
      .from("schedule")
      .delete()
      .in("date", dates)
      .neq("version", version);
    if (staleError) throw staleError;

    // Даты далеко за окном текущего файла (опечатка года вроде 2024-10-19)
    // не входят в `dates` текущего импорта, поэтому строка выше их не трогает.
    // Без этой зачистки они копятся навсегда и в приложении выглядят как
    // «расписание есть в Supabase, а на текущей неделе пусто».
    const keepFrom = toIso(addDays(new Date(), -21));
    const { error: oldDatesError } = await db
      .from("schedule")
      .delete()
      .lt("date", keepFrom);
    if (oldDatesError) throw oldDatesError;

    const lessonsByGroup: Record<string, number> = {};
    for (const l of payload) lessonsByGroup[l.group_name] = (lessonsByGroup[l.group_name] ?? 0) + 1;

    await setMeta("source_sha256", sha);
    await setMeta("version", String(version));
    await setMeta("updated_at", new Date().toISOString());
    await setMeta("status", "ok");
    await setMeta("message", `Импортировано занятий: ${payload.length}`);

    await db.from("import_log").insert({
      status: "ok",
      message: `Импортировано занятий: ${payload.length}`,
      sha256: sha,
      lessons: payload.length,
      groups: groupNames.length,
    });

    console.log(`Импорт завершён: ${payload.length} занятий, ${groupNames.length} групп`);
    return json({
      status: "ok",
      version,
      lessons: payload.length,
      groups: groupNames.length,
      lessonsByGroup,
      sha256: sha,
      strategy,
    });
  } catch (error) {
    // Никаких разрушительных действий: старое расписание остаётся рабочим.
    const message = error instanceof Error ? error.message : String(error);
    console.error("Импорт не выполнен:", message);
    await db.from("import_log").insert({
      status: "error",
      message,
      sha256: sha || null,
    });
    // Раньше статус/сообщение в metadata обновлялись ТОЛЬКО при успехе,
    // поэтому /api/version после отклонённого импорта продолжал молча
    // показывать "ok" от последнего успешного запуска — не было видно,
    // что импорт вообще перестал проходить (см. фикс санити-проверки
    // выше: именно так расписание и "пропадало" без единого признака
    // ошибки в приложении).
    await setMeta("status", "error");
    await setMeta("message", message);
    return json({ status: "error", message, startedAt }, 200);
  }
}

// ------------------------------------------- автообновление без cron

/**
 * Фоновая проверка источника, если с прошлой прошло больше 30 минут.
 * Благодаря этому расписание обновляется само, даже если pg_cron не настроен:
 * приложение и так дёргает /api/version каждые 30 минут.
 */
async function maybeAutoImport(): Promise<void> {
  try {
    const lastChecked = await getMeta("last_checked_at");
    if (lastChecked) {
      const age = Date.now() - new Date(lastChecked).getTime();
      if (Number.isFinite(age) && age < AUTO_CHECK_INTERVAL_MS) return;
    }
    // Сразу помечаем время проверки, чтобы параллельные запросы не запускали импорт дважды.
    await setMeta("last_checked_at", new Date().toISOString());
    console.log("Автопроверка источника расписания");

    const task = runImport(false).catch((error) => {
      console.error("Автоимпорт завершился ошибкой:", error);
    });

    // В Supabase Edge Runtime задача доживает до конца даже после ответа клиенту.
    const runtime = (globalThis as Record<string, unknown>).EdgeRuntime as
      | { waitUntil?: (p: Promise<unknown>) => void }
      | undefined;
    if (runtime?.waitUntil) runtime.waitUntil(task);
  } catch (error) {
    console.error("Не удалось запустить автопроверку:", error);
  }
}

// ------------------------------------------------------------- маршруты

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") {
    return new Response("ok", { headers: CORS });
  }

  const url = new URL(request.url);
  // Путь внутри функции: всё после /schedule-api
  const path = url.pathname.replace(/^.*schedule-api/, "") || "/";

  try {
    if (path === "/api/health" || path === "/" || path === "") {
      return json({ ok: true, time: new Date().toISOString() });
    }

    if (path === "/api/version") {
      // Каждое обращение приложения работает как «пульс» автообновления.
      await maybeAutoImport();
      const [version, updatedAt, status, message] = await Promise.all([
        getMeta("version"),
        getMeta("updated_at"),
        getMeta("status"),
        getMeta("message"),
      ]);
      return json({
        version: Number(version ?? 0),
        updatedAt,
        status: status ?? "unknown",
        message,
      });
    }

    if (path === "/api/groups") {
      const { data, error } = await db
        .from("groups")
        .select("id, name")
        .order("name", { ascending: true });
      if (error) throw error;
      return json(data ?? []);
    }

    // Без префикса "/api/" — так его уже вызывает текущий Android-клиент
    // (ScheduleApi.kt, @GET("schedule/{group}")); остальные маршруты
    // (/api/health, /api/version, /api/groups, /api/import,
    // /api/debug/dates, /api/source) Android вызывает С префиксом, и
    // здесь это специально оставлено как есть — после смены BASE_URL на
    // адрес этого нового проекта менять в Android больше ничего не нужно.
    if (path.startsWith("/schedule/")) {
      const group = decodeURIComponent(path.slice("/schedule/".length));
      if (!group) return json({ error: "group is required" }, 400);

      const { data, error } = await db
        .from("schedule")
        .select(
          "id, group_name, date, day_of_week, lesson_number, start_time, end_time, subject, teacher, classroom, week_type, subgroup, type",
        )
        .eq("group_name", group)
        .order("date", { ascending: true })
        .order("lesson_number", { ascending: true });
      if (error) throw error;
      return json(data ?? []);
    }

    if (path === "/api/debug/dates") {
      // Самостоятельная проверка п. 9-10 задачи: какие даты реально лежат
      // в Supabase прямо сейчас, без похода в SQL-консоль. Необязательный
      // ?group= сужает выборку до одной группы (иначе — по всем сразу).
      const groupFilter = url.searchParams.get("group");
      let query = db.from("schedule").select("date, day_of_week, group_name");
      if (groupFilter) query = query.eq("group_name", groupFilter);
      const { data, error } = await query;
      if (error) throw error;

      const byDate = new Map<string, { dayOfWeek: number; count: number; groups: Set<string> }>();
      for (const row of data ?? []) {
        const date = row.date as string;
        const entry = byDate.get(date) ?? {
          dayOfWeek: row.day_of_week as number,
          count: 0,
          groups: new Set<string>(),
        };
        entry.count += 1;
        entry.groups.add(row.group_name as string);
        byDate.set(date, entry);
      }

      const dates = [...byDate.entries()]
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([date, v]) => ({
          date,
          dayOfWeek: v.dayOfWeek,
          dayName: DAY_NAMES[v.dayOfWeek - 1] ?? "",
          lessonsCount: v.count,
          groupsCount: v.groups.size,
        }));

      return json({ dates });
    }

    if (path === "/api/debug/parse") {
      const { bytes, strategy } = await downloadXlsx();
      return json({ strategy, ...(debugParseWorkbook(bytes) as Record<string, unknown>) });
    }

    if (path === "/api/source") {
      // Диагностика: какой способ загрузки сработал и что сейчас в источнике.
      try {
        const { bytes, strategy } = await downloadXlsx();
        return json({
          ok: true,
          strategy,
          bytes: bytes.length,
          sha256: await sha256Hex(bytes),
          sourceUrl: SOURCE_URL,
          candidates: buildPublicKeyCandidates(),
        });
      } catch (error) {
        return json({
          ok: false,
          message: error instanceof Error ? error.message : String(error),
          sourceUrl: SOURCE_URL,
          candidates: buildPublicKeyCandidates(),
        });
      }
    }

    if (path === "/api/import") {
      const force = url.searchParams.get("force") === "1";
      return await runImport(force);
    }

    return json({ error: "not found", path }, 404);
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    console.error("Ошибка обработки запроса:", message);
    return json({ error: message }, 500);
  }
});
