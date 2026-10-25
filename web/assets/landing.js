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
    meta.textContent = `${release.name || release.tag_name} · ${mb} МБ · обновлено ${date} · Android 8.0+`;
  } catch {
    /* оставляем ссылку на страницу релизов */
  }
})();
