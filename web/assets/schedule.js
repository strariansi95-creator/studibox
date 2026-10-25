// Веб-расписание Студибокса. Те же данные и правила, что в Android-приложении:
// день недели всегда вычисляется из ISO-даты, преподаватель видит одну
// карточку на пару даже если она идёт сразу у нескольких групп.
"use strict";

const API = "https://czhsyilbrgylvjjjrlyh.supabase.co/functions/v1/schedule-api";
const STUDY_DAYS = 6; // Пн–Сб
const DAY_SHORT = ["Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"];
const DAY_FULL = ["Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"];
const MONTHS_SHORT = ["янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек"];
const MONTHS = ["января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря"];

const $ = (id) => document.getElementById(id);

// ------------------------------------------------------------------ хранилище

const store = {
  get(key, fallback = null) {
    try {
      const raw = localStorage.getItem("sbx:" + key);
      return raw == null ? fallback : JSON.parse(raw);
    } catch {
      return fallback;
    }
  },
  set(key, value) {
    try {
      localStorage.setItem("sbx:" + key, JSON.stringify(value));
    } catch {
      /* приватный режим или переполнение — работаем без кэша */
    }
  },
};

// ------------------------------------------------------------------ даты

function parseIso(iso) {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(y, m - 1, d);
}
function iso(date) {
  const p = (n) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${p(date.getMonth() + 1)}-${p(date.getDate())}`;
}
/** 0 = понедельник … 6 = воскресенье. */
function weekdayIndex(date) {
  return (date.getDay() + 6) % 7;
}
function mondayOf(date) {
  const d = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  d.setDate(d.getDate() - weekdayIndex(d));
  return d;
}
function addDays(date, n) {
  const d = new Date(date);
  d.setDate(d.getDate() + n);
  return d;
}
/**
 * «Сейчас» по Якутску, а не по часам устройства: расписание СВФУ — местное,
 * и с компьютера в другом часовом поясе пары не должны подсвечиваться со сдвигом.
 * Возвращает обычный Date, у которого getHours()/getDay() — якутские.
 */
function yktNow() {
  try {
    const parts = Object.fromEntries(
      new Intl.DateTimeFormat("en-US", {
        timeZone: "Asia/Yakutsk",
        year: "numeric", month: "numeric", day: "numeric",
        hour: "numeric", minute: "numeric", second: "numeric",
        hourCycle: "h23",
      }).formatToParts(new Date()).map((p) => [p.type, p.value])
    );
    return new Date(+parts.year, +parts.month - 1, +parts.day, +parts.hour, +parts.minute, +parts.second);
  } catch {
    return new Date();
  }
}

/** В воскресенье учебная неделя уже прошла — показываем следующую. */
function currentWeekStart() {
  const today = yktNow();
  return mondayOf(weekdayIndex(today) === 6 ? addDays(today, 1) : today);
}
function currentStudyDay() {
  const day = weekdayIndex(yktNow());
  return day === 6 ? 0 : Math.min(day, STUDY_DAYS - 1);
}
function humanDate(date) {
  return `${date.getDate()} ${MONTHS[date.getMonth()]}`;
}
function minutes(hhmm) {
  const m = /^(\d{1,2}):(\d{2})/.exec(hhmm || "");
  return m ? Number(m[1]) * 60 + Number(m[2]) : null;
}

// ------------------------------------------------------------------ данные

async function getJsonOnce(path, timeoutMs) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(API + path, { signal: controller.signal });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return await response.json();
  } finally {
    clearTimeout(timer);
  }
}

/**
 * У части провайдеров соединение с Supabase иногда обрывается
 * (ERR_CONNECTION_RESET) — повторяем запрос с паузой, прежде чем сдаться.
 */
async function getJson(path, timeoutMs = 20000, attempts = 3) {
  let lastError;
  for (let i = 0; i < attempts; i++) {
    try {
      return await getJsonOnce(path, timeoutMs);
    } catch (e) {
      lastError = e;
      if (String(e.message).startsWith("HTTP 4")) break; // неверный запрос — повтор не поможет
      if (i < attempts - 1) await new Promise((r) => setTimeout(r, 700 * (i + 1)));
    }
  }
  throw lastError;
}

let serverVersion = null;

/** Версия расписания на сервере; null — сервер недоступен. */
async function loadVersion() {
  try {
    const v = await getJson("/api/version", 8000, 1);
    serverVersion = v.version ?? null;
    if (v.updatedAt) {
      const d = new Date(v.updatedAt);
      $("updated").textContent = `Обновлено ${humanDate(d)}, ${d.toLocaleTimeString("ru-RU", { hour: "2-digit", minute: "2-digit" })}`;
    }
  } catch {
    serverVersion = null;
  }
  return serverVersion;
}

async function loadGroups() {
  const cached = store.get("groups");
  if (cached && cached.version === serverVersion && serverVersion != null) return cached.items;
  try {
    const items = (await getJson("/api/groups")).map((g) => g.name).filter(Boolean);
    store.set("groups", { version: serverVersion, items });
    return items;
  } catch (e) {
    if (cached) return cached.items;
    throw e;
  }
}

/** Пары группы. Кэш используется, пока версия на сервере не изменилась (или сервер недоступен). */
async function loadGroupSchedule(group) {
  const key = "g:" + group;
  const cached = store.get(key);
  if (cached && (serverVersion == null || cached.version === serverVersion)) return cached.rows;
  try {
    const rows = await getJson("/schedule/" + encodeURIComponent(group), 30000);
    store.set(key, { version: serverVersion, rows });
    return rows;
  } catch (e) {
    if (cached) return cached.rows;
    throw e;
  }
}

/** Все группы сразу — для режима преподавателя (как refreshAllGroupsForTeacherMode в приложении). */
async function loadAllSchedules(groups, onProgress) {
  const result = [];
  let done = 0;
  let failed = 0;
  const queue = [...groups];
  async function worker() {
    while (queue.length) {
      const group = queue.shift();
      try {
        result.push(...(await loadGroupSchedule(group)));
      } catch {
        failed++;
      }
      onProgress(++done, groups.length);
    }
  }
  await Promise.all(Array.from({ length: 6 }, worker));
  if (failed === groups.length) throw new Error("Сервер расписания недоступен");
  return result;
}

/** «Иванова А.А., Петров Б.Б.» → два преподавателя. */
function splitTeachers(raw) {
  return (raw || "")
    .replace(/\([^)]*\)?/g, " ")
    .split(/[,;/\n]+/)
    .map((s) => s.replace(/\s+/g, " ").trim())
    .filter(Boolean);
}

function subgroupDigit(raw) {
  const m = /\d/.exec(raw || "");
  return m ? m[0] : "";
}

// ------------------------------------------------------------------ состояние

const params = new URLSearchParams(location.search);
const state = {
  mode: params.has("teacher") ? "teacher" : params.has("group") ? "student" : store.get("mode", "student"),
  group: params.get("group") || store.get("group"),
  teacher: params.get("teacher") || store.get("teacher"),
  subgroup: store.get("subgroup", ""),
  weekStart: currentWeekStart(),
  selectedDay: currentStudyDay(),
  groups: [],
  teachers: [],
  rows: [], // занятия выбранной группы или всех групп (для преподавателя)
  allLoaded: false,
};

function saveSelection() {
  store.set("mode", state.mode);
  if (state.group) store.set("group", state.group);
  if (state.teacher) store.set("teacher", state.teacher);
  store.set("subgroup", state.subgroup);
  const url = new URL(location.href);
  url.search = "";
  const value = state.mode === "teacher" ? state.teacher : state.group;
  if (value) url.searchParams.set(state.mode === "teacher" ? "teacher" : "group", value);
  history.replaceState(null, "", url);
}

// ------------------------------------------------------------------ выбор группы / ФИО

const input = $("picker-input");
const list = $("picker-list");
let activeIndex = -1;

function pickerOptions() {
  return state.mode === "teacher" ? state.teachers : state.groups;
}
function currentValue() {
  return state.mode === "teacher" ? state.teacher : state.group;
}

function normalizeSearch(s) {
  return s.toLowerCase().replace(/ё/g, "е").replace(/[\s.\-]+/g, "");
}

function renderPickerList() {
  const query = normalizeSearch(input.value);
  const options = pickerOptions();
  const matches = query && input.value !== currentValue()
    ? options.filter((o) => normalizeSearch(o).includes(query))
    : options;
  list.innerHTML = "";
  if (!matches.length) {
    const li = document.createElement("li");
    li.className = "empty";
    li.textContent = options.length ? "Ничего не найдено" : "Загружаем список…";
    list.append(li);
  }
  matches.slice(0, 200).forEach((option, i) => {
    const li = document.createElement("li");
    li.setAttribute("role", "option");
    li.id = "opt-" + i;
    li.textContent = option;
    li.setAttribute("aria-selected", String(i === activeIndex));
    li.addEventListener("mousedown", (e) => {
      e.preventDefault();
      choose(option);
    });
    list.append(li);
  });
}

function openPicker() {
  activeIndex = -1;
  renderPickerList();
  list.hidden = false;
  input.setAttribute("aria-expanded", "true");
}
function closePicker() {
  list.hidden = true;
  input.setAttribute("aria-expanded", "false");
  input.value = currentValue() || "";
  $("picker-clear").hidden = !input.value;
}

input.addEventListener("focus", () => {
  input.select();
  openPicker();
});
input.addEventListener("input", () => {
  activeIndex = -1;
  $("picker-clear").hidden = !input.value;
  renderPickerList();
  list.hidden = false;
});
input.addEventListener("blur", closePicker);
input.addEventListener("keydown", (e) => {
  const items = [...list.querySelectorAll("li[role=option]")];
  if (e.key === "ArrowDown" || e.key === "ArrowUp") {
    e.preventDefault();
    if (list.hidden) openPicker();
    activeIndex = (activeIndex + (e.key === "ArrowDown" ? 1 : -1) + items.length) % Math.max(items.length, 1);
    items.forEach((li, i) => li.setAttribute("aria-selected", String(i === activeIndex)));
    items[activeIndex]?.scrollIntoView({ block: "nearest" });
    input.setAttribute("aria-activedescendant", items[activeIndex]?.id || "");
  } else if (e.key === "Enter") {
    const item = items[activeIndex] || (items.length === 1 ? items[0] : null);
    if (item) {
      e.preventDefault();
      choose(item.textContent);
    }
  } else if (e.key === "Escape") {
    input.blur();
  }
});
$("picker-clear").addEventListener("click", () => {
  input.value = "";
  input.focus();
});

function choose(value) {
  if (state.mode === "teacher") state.teacher = value;
  else {
    if (state.group !== value) state.subgroup = "";
    state.group = value;
  }
  saveSelection();
  input.blur();
  showSchedule();
}

// ------------------------------------------------------------------ режим

document.querySelectorAll(".segmented button").forEach((button) => {
  button.addEventListener("click", () => setMode(button.dataset.mode));
});

function setMode(mode, load = true) {
  state.mode = mode;
  document.querySelectorAll(".segmented button").forEach((b) => {
    b.setAttribute("aria-selected", String(b.dataset.mode === mode));
  });
  const teacher = mode === "teacher";
  input.placeholder = teacher ? "Найдите себя: фамилия И.О." : "Найдите свою группу";
  $("picker-label").textContent = teacher ? "Преподаватель" : "Группа";
  input.value = currentValue() || "";
  $("picker-clear").hidden = !input.value;
  saveSelection();
  if (load) showSchedule();
}

// ------------------------------------------------------------------ неделя

$("prev-week").addEventListener("click", () => moveWeek(-7));
$("next-week").addEventListener("click", () => moveWeek(7));
$("today-btn").addEventListener("click", () => {
  state.weekStart = currentWeekStart();
  state.selectedDay = currentStudyDay();
  render();
});
function moveWeek(days) {
  state.weekStart = addDays(state.weekStart, days);
  render();
}

// ------------------------------------------------------------------ загрузка

function setStatus(html) {
  $("status").innerHTML = html;
}

function skeleton() {
  $("days").innerHTML = Array.from({ length: 3 }, () => '<div class="skeleton"></div>').join("");
}

/** quiet — на экране уже расписание из кэша: обновляем без заглушек и прогресса. */
async function showSchedule(quiet = false) {
  quiet = quiet === true;
  const value = currentValue();
  if (!quiet) {
    $("days").innerHTML = "";
    $("subgroups").hidden = true;
  }
  if (!value) {
    setStatus(
      state.mode === "teacher"
        ? "<b>Выберите преподавателя</b><span>Начните вводить фамилию в поле выше.</span>"
        : "<b>Выберите группу</b><span>Начните вводить название группы в поле выше.</span>"
    );
    if (state.mode === "teacher") ensureTeachers().catch(() => {});
    render();
    return;
  }

  if (!quiet) {
    setStatus("");
    skeleton();
  }
  try {
    if (state.mode === "teacher") {
      await ensureTeachers(quiet);
      if (currentValue() !== value) return;
    } else {
      state.rows = await loadGroupSchedule(value);
      if (currentValue() !== value) return;
    }
    setStatus("");
    render();
  } catch {
    if (quiet) return; // остаётся кэш — лучше старое расписание, чем ошибка
    $("days").innerHTML = "";
    setStatus('<b>Не удалось загрузить расписание</b><span>Проверьте интернет и попробуйте ещё раз.</span><button class="btn btn-secondary" id="retry">Повторить</button>');
    $("retry").addEventListener("click", () => showSchedule());
  }
}

let teachersPromise = null;

/** Список преподавателей строится по расписанию всех групп — грузим один раз. */
function ensureTeachers(quiet = false) {
  if (state.allLoaded) {
    state.rows = state.allRows;
    return Promise.resolve();
  }
  if (!teachersPromise) {
    if (!state.groups.length) return Promise.reject(new Error("Список групп недоступен"));
    if (state.mode === "teacher" && currentValue() && !quiet) {
      setStatus('<div class="spinner"></div><b>Собираем расписание всех групп</b><div class="progress"><span id="progress"></span></div>');
      $("days").innerHTML = "";
    }
    teachersPromise = loadAllSchedules(state.groups, (done, total) => {
      const bar = document.getElementById("progress");
      if (bar) bar.style.width = `${Math.round((done / total) * 100)}%`;
    })
      .then((rows) => {
        state.allRows = rows;
        state.allLoaded = true;
        const names = new Set();
        rows.forEach((r) => splitTeachers(r.teacher).forEach((n) => names.add(n)));
        state.teachers = [...names].sort((a, b) => a.localeCompare(b, "ru"));
        if (state.mode === "teacher") state.rows = rows;
        if (!list.hidden) renderPickerList();
      })
      .catch((e) => {
        teachersPromise = null;
        throw e;
      });
  }
  return teachersPromise;
}

// ------------------------------------------------------------------ отрисовка

/** Занятия на дату с учётом режима и подгруппы. */
function lessonsFor(dateIso) {
  const rows = state.rows.filter((r) => r.date === dateIso);
  if (state.mode === "teacher") {
    const mine = rows.filter((r) => splitTeachers(r.teacher).includes(state.teacher));
    const slots = new Map();
    for (const r of mine) {
      const key = [r.start_time, r.end_time, r.subject, r.subgroup].join("|");
      const slot = slots.get(key) || { ...r, groups: [] };
      if (!slot.groups.some((g) => g.group === r.group_name)) {
        slot.groups.push({ group: r.group_name, room: r.classroom });
      }
      slots.set(key, slot);
    }
    return [...slots.values()]
      .map((s) => ({ ...s, groups: s.groups.sort((a, b) => a.group.localeCompare(b.group, "ru")) }))
      .sort(byTime);
  }
  const sub = state.subgroup;
  const lessons = rows
    .filter((r) => !sub || !subgroupDigit(r.subgroup) || subgroupDigit(r.subgroup) === sub)
    .sort(byTime);
  return toSlots(lessons);
}

/**
 * Пары разных подгрупп в одно и то же время — одна карточка с частями
 * «1 подгруппа» / «2 подгруппа». Если у одновременных пар подгруппа не
 * указана, это не деление на подгруппы — такие пары остаются отдельными.
 */
function toSlots(lessons) {
  const byKey = new Map();
  for (const l of lessons) {
    const key = [l.lesson_number, l.start_time, l.end_time].join("|");
    if (!byKey.has(key)) byKey.set(key, []);
    byKey.get(key).push(l);
  }
  const slots = [];
  for (const group of byKey.values()) {
    const bySubgroup = group.length > 1 && group.every((l) => subgroupDigit(l.subgroup));
    const parts = bySubgroup
      ? [...group].sort((a, b) => subgroupDigit(a.subgroup).localeCompare(subgroupDigit(b.subgroup)))
      : null;
    if (parts) slots.push({ ...parts[0], parts });
    else group.forEach((l) => slots.push({ ...l, parts: [l] }));
  }
  return slots.sort(byTime);
}

function byTime(a, b) {
  return (a.lesson_number - b.lesson_number) || (minutes(a.start_time) ?? 0) - (minutes(b.start_time) ?? 0);
}

/** Текущая и следующая пара — по реальному времени, как в LessonTiming.kt. */
function timingFor(lessons, isToday) {
  const result = new Map();
  if (!isToday) return result;
  const now = yktNow();
  const nowMin = now.getHours() * 60 + now.getMinutes();
  let nextFound = false;
  for (const l of lessons) {
    const start = minutes(l.start_time);
    const end = minutes(l.end_time);
    if (start == null || end == null) continue;
    if (end <= nowMin) result.set(l, "past");
    else if (start <= nowMin) result.set(l, "current");
    else if (!nextFound) {
      result.set(l, "next");
      nextFound = true;
    }
  }
  return result;
}

const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);

function lessonMeta(l) {
  const meta = [];
  if (state.mode === "teacher") {
    l.groups.forEach((g) => meta.push(`<li>${esc(g.group)}${g.room ? `, ауд. ${esc(g.room)}` : ""}</li>`));
  } else {
    splitTeachers(l.teacher).forEach((t) => meta.push(`<li>${esc(t)}</li>`));
    if (l.classroom) meta.push(`<li>Аудитория ${esc(l.classroom)}</li>`);
  }
  return meta.length ? `<ul class="lesson-meta">${meta.join("")}</ul>` : "";
}

function lessonChips(l, withSubgroup) {
  const chips = [];
  if (l.type) chips.push(l.type);
  if (withSubgroup && subgroupDigit(l.subgroup)) chips.push(`${subgroupDigit(l.subgroup)} подгруппа`);
  if (l.week_type) chips.push(l.week_type);
  return chips.length
    ? `<div class="lesson-chips">${chips.map((c) => `<span class="chip-tag">${esc(c)}</span>`).join("")}</div>`
    : "";
}

function lessonCard(l, timing) {
  // Время стопкой: начало над концом, как пишут в тетради.
  const time = [l.start_time, l.end_time].filter(Boolean).join("\n") || "—";
  const tag = timing === "current" ? "Идёт сейчас" : timing === "next" ? "Следующая" : "";
  const parts = l.parts && l.parts.length > 1 ? l.parts : null;

  const body = parts
    ? (tag ? `<span class="state-tag state-tag-top">${tag}</span>` : "") +
      parts.map((p) => `
        <section class="lesson-part">
          <span class="part-label">${subgroupDigit(p.subgroup)} подгруппа</span>
          <h3 class="lesson-subject">${esc(p.subject || "Занятие")}</h3>
          ${lessonMeta(p)}
          ${lessonChips(p, false)}
        </section>`).join("")
    : `
        <h3 class="lesson-subject">${esc(l.subject || "Занятие")}</h3>
        ${tag ? `<span class="state-tag">${tag}</span>` : ""}
        ${lessonMeta(l)}
        ${lessonChips(l, true)}`;

  return `
    <article class="lesson ${timing ? "is-" + timing : ""} ${parts ? "is-split" : ""}">
      <span class="lesson-num" aria-label="${l.lesson_number} пара">${l.lesson_number > 0 ? l.lesson_number : ""}</span>
      <span class="lesson-time">${esc(time)}</span>
      <div class="lesson-body">${body}</div>
    </article>`;
}

function renderSubgroups() {
  const box = $("subgroups");
  if (state.mode !== "student" || !state.group) {
    box.hidden = true;
    return;
  }
  const digits = [...new Set(state.rows.map((r) => subgroupDigit(r.subgroup)).filter(Boolean))].sort();
  if (!digits.length) {
    box.hidden = true;
    return;
  }
  box.hidden = false;
  box.innerHTML = "<span>Подгруппа</span>" + ["", ...digits]
    .map((d) => `<button type="button" class="chip" data-sub="${d}" aria-pressed="${state.subgroup === d}">${d || "Все"}</button>`)
    .join("");
  box.querySelectorAll(".chip").forEach((chip) => {
    chip.addEventListener("click", () => {
      state.subgroup = chip.dataset.sub;
      saveSelection();
      render();
    });
  });
}

function render() {
  const today = yktNow();
  const todayIso = iso(today);
  const weekEnd = addDays(state.weekStart, STUDY_DAYS - 1);
  const short = matchMedia("(max-width: 420px)").matches;
  const label = (d) => (short ? `${d.getDate()} ${MONTHS_SHORT[d.getMonth()]}` : humanDate(d));
  $("week-label").textContent = `${label(state.weekStart)} — ${label(weekEnd)}`;
  $("today-btn").hidden = iso(currentWeekStart()) === iso(state.weekStart);

  renderSubgroups();
  if (!currentValue() || $("status").innerHTML) {
    $("day-tabs").innerHTML = "";
    return;
  }

  const days = Array.from({ length: STUDY_DAYS }, (_, i) => {
    const date = addDays(state.weekStart, i);
    const dateIso = iso(date);
    return { i, date, dateIso, lessons: lessonsFor(dateIso), isToday: dateIso === todayIso };
  });

  $("day-tabs").innerHTML = days
    .map((d) => `
      <button type="button" class="day-tab ${d.lessons.length ? "has-lessons" : ""} ${d.isToday ? "is-today" : ""}"
              data-day="${d.i}" aria-selected="${d.i === state.selectedDay}" aria-label="${DAY_FULL[d.i]}, ${humanDate(d.date)}">
        <small>${DAY_SHORT[d.i]}</small><b>${d.date.getDate()}</b><i></i>
      </button>`)
    .join("");
  $("day-tabs").querySelectorAll(".day-tab").forEach((tab) => {
    tab.addEventListener("click", () => {
      state.selectedDay = Number(tab.dataset.day);
      render();
    });
  });

  const anyLessons = days.some((d) => d.lessons.length);
  const hasData = state.rows.length > 0;
  $("days").innerHTML = days
    .map((d) => {
      const timing = timingFor(d.lessons, d.isToday);
      const body = d.lessons.length
        ? `<div class="day-list">${d.lessons.map((l) => lessonCard(l, timing.get(l))).join("")}</div>`
        : `<div class="day-empty">${!hasData ? "Расписание пока недоступно" : anyLessons ? "Пар нет" : "На этой неделе пар нет"}</div>`;
      return `
        <section class="day ${d.isToday ? "is-today" : ""} ${d.i === state.selectedDay ? "is-selected" : ""}">
          <h2>${DAY_FULL[d.i]} <small>${humanDate(d.date)}${d.isToday ? ", сегодня" : ""}</small></h2>
          ${body}
        </section>`;
    })
    .join("");
}

// ------------------------------------------------------------------ старт

/**
 * Мгновенный показ из кэша, до ответа сервера: при медленной связи человек
 * сразу видит своё расписание, а свежие данные подтянутся следом.
 */
function hydrateFromCache() {
  const groups = store.get("groups")?.items || [];
  state.groups = groups;
  if (state.mode === "student" && state.group) {
    const cached = store.get("g:" + state.group);
    if (!cached) return false;
    state.rows = cached.rows;
    return true;
  }
  if (state.mode === "teacher" && state.teacher && groups.length) {
    const all = groups.map((g) => store.get("g:" + g));
    if (all.some((c) => !c)) return false;
    state.rows = all.flatMap((c) => c.rows);
    return true;
  }
  return false;
}

(async function init() {
  setMode(state.mode, false);
  const hydrated = hydrateFromCache();
  if (hydrated) {
    setStatus("");
    render();
  } else {
    render();
    if (currentValue()) skeleton();
  }
  // Сначала версия (от неё зависит кэш) и список групп, потом расписание.
  await loadVersion();
  try {
    state.groups = await loadGroups();
  } catch {
    state.groups = [];
  }
  if (!list.hidden) renderPickerList();
  showSchedule(hydrated);
  // «Сейчас / далее» пересчитываются каждую минуту.
  setInterval(() => {
    if (!$("status").innerHTML) render();
  }, 60_000);
})();
