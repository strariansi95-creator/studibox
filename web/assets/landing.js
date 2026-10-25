"use strict";

// ------------------------------------------------------------------ звонки
// Прошедшие пары вычеркнуты, текущая закрашивается маркером по мере того,
// как идёт пара, подпись словами говорит, что сейчас.

const bellsList = document.getElementById("bells-list");
const bellsNow = document.getElementById("bells-now");

function toMinutes(hhmm) {
  const [h, m] = hhmm.split(":").map(Number);
  return h * 60 + m;
}

function plural(n, one, few, many) {
  const mod10 = n % 10;
  const mod100 = n % 100;
  if (mod10 === 1 && mod100 !== 11) return one;
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return few;
  return many;
}

function duration(mins) {
  if (mins < 60) return `${mins} ${plural(mins, "минута", "минуты", "минут")}`;
  const h = Math.floor(mins / 60);
  const m = mins % 60;
  const hours = `${h} ${plural(h, "час", "часа", "часов")}`;
  return m ? `${hours} ${m} мин` : hours;
}

function updateBells() {
  const now = new Date();
  const nowMin = now.getHours() * 60 + now.getMinutes() + now.getSeconds() / 60;
  const rows = [...bellsList.children];
  const isSunday = now.getDay() === 0;
  let text = "";
  let nextMarked = false;

  rows.forEach((li, i) => {
    const start = toMinutes(li.dataset.start);
    const end = toMinutes(li.dataset.end);
    li.classList.remove("is-past", "is-current", "is-next");
    li.style.removeProperty("--p");
    if (isSunday) return;

    if (nowMin >= end) {
      li.classList.add("is-past");
    } else if (nowMin >= start) {
      li.classList.add("is-current");
      li.style.setProperty("--p", ((nowMin - start) / (end - start)).toFixed(3));
      text = `Идёт ${i + 1} пара, до звонка ${duration(Math.ceil(end - nowMin))}`;
    } else if (!nextMarked) {
      nextMarked = true;
      li.classList.add("is-next");
      if (!text) {
        const wait = duration(Math.ceil(start - nowMin));
        text = i === 0 ? `Первая пара через ${wait}` : `Перемена, ${i + 1} пара через ${wait}`;
      }
    }
  });

  if (isSunday) text = "Сегодня воскресенье, пар нет";
  else if (!text) text = "Пары на сегодня закончились";
  bellsNow.textContent = text;
}

updateBells();
setInterval(updateBells, 20_000);

// ------------------------------------------------------------------ последний APK
// Кнопка «Скачать» ведёт прямо на APK последнего релиза. Если GitHub API
// недоступен (лимит запросов, нет сети) — остаётся ссылка на страницу релиза.

(async () => {
  const button = document.getElementById("download-btn");
  const meta = document.getElementById("release-meta");
  try {
    const response = await fetch(
      "https://api.github.com/repos/strariansi95-creator/studibox/releases/latest",
      { headers: { Accept: "application/vnd.github+json" } }
    );
    if (!response.ok) return;
    const release = await response.json();
    const apk = (release.assets || []).find((a) => a.name.endsWith(".apk"));
    if (!apk) return;
    button.href = apk.browser_download_url;
    const mb = (apk.size / 1024 / 1024).toFixed(1).replace(".", ",");
    const date = new Date(release.published_at).toLocaleDateString("ru-RU", { day: "numeric", month: "long" });
    meta.textContent = `${release.name || release.tag_name}, ${mb} МБ, обновлено ${date}. Нужен Android 8.0 или новее.`;
  } catch {
    /* оставляем ссылку на страницу релизов */
  }
})();
