# Studibox backend — новый Supabase-проект (копипаст-инструкция)

Три файла, по порядку:

1. `sql/schema.sql` — таблицы.
2. `sql/cron.sql` — автообновление раз в 30 минут (подставить свой адрес).
3. `supabase/functions/schedule-api/index.ts` — сама Edge Function.

Источник расписания уже встроен в код:
`https://disk.360.yandex.ru/i/bfF89K1c0tsG3g` — менять ничего не нужно,
если ссылка не поменяется. Если когда-нибудь поменяется — не трогая код,
можно переопределить через переменную окружения `SCHEDULE_SOURCE_URL`
(шаг 5 ниже).

## 1. Создать проект

[supabase.com/dashboard](https://supabase.com/dashboard) → **New
project**. Запомните два значения из **Project Settings → API**:

- **Project URL** (`https://czhsyilbrgylvjjjrlyh.supabase.co`)
- **service_role key** (секретный, не anon-ключ — понадобится на шаге 4)

## 2. Таблицы

**SQL Editor** → New query → вставить целиком содержимое `sql/schema.sql`
→ Run. Скрипт безопасно перезапускать повторно (`if not exists` везде).

## 3. Edge Function

**Edge Functions** → **Create a function** → имя: `schedule-api` →
в редакторе стереть заготовку и вставить целиком
`supabase/functions/schedule-api/index.ts` → **Deploy**.

Если дашборд создаёт функцию с авторизацией по умолчанию — зайдите в её
настройки и выключите **Enforce JWT verification** (приложение дёргает
её без токена пользователя; это публичное чтение расписания, ничего
чувствительного она не отдаёт).

## 4. Переменная окружения

**Edge Functions → schedule-api → Settings → Secrets** (или
`supabase secrets set`, если пользуетесь CLI):

| Имя | Значение |
|---|---|
| `SERVICE_ROLE_KEY` | service_role key из шага 1 |

(`SUPABASE_URL` и `SUPABASE_SERVICE_ROLE_KEY` Supabase подставляет сама —
их задавать не нужно; `SERVICE_ROLE_KEY` отдельно нужен только для
`app.service_role_key` в cron-задаче на шаге 6.)

## 5. (Необязательно) свой источник XLSX

Если ссылка на Яндекс.Диск когда-нибудь изменится — не трогая код,
добавьте секрет:

| Имя | Значение |
|---|---|
| `SCHEDULE_SOURCE_URL` | новая ссылка на `disk.360.yandex.ru/i/...` |

## 6. Автообновление раз в 30 минут

1. `sql/cron.sql` уже настроен на этот проект (`czhsyilbrgylvjjjrlyh`) —
   менять ничего не нужно, если проект снова не сменится.
2. **Database → Extensions** → включите `pg_cron` и `pg_net`, если ещё
   не включены.
3. **SQL Editor** → вставить `sql/cron.sql` как есть → Run.

Без этого шага расписание тоже будет обновляться — каждый раз, когда
приложение дёргает `/api/version` (это устроено внутри функции), просто
не строго по расписанию, а «по первому обращению после получаса».
Cron делает обновление предсказуемым независимо от активности в
приложении.

## 7. Проверка

Откройте по очереди (замените `czhsyilbrgylvjjjrlyh`):

```
https://czhsyilbrgylvjjjrlyh.supabase.co/functions/v1/schedule-api/api/health
https://czhsyilbrgylvjjjrlyh.supabase.co/functions/v1/schedule-api/api/import?force=1
https://czhsyilbrgylvjjjrlyh.supabase.co/functions/v1/schedule-api/api/version
https://czhsyilbrgylvjjjrlyh.supabase.co/functions/v1/schedule-api/api/groups
https://czhsyilbrgylvjjjrlyh.supabase.co/functions/v1/schedule-api/schedule/<ЛЮБАЯ_ГРУППА_ИЗ_/api/groups>
https://czhsyilbrgylvjjjrlyh.supabase.co/functions/v1/schedule-api/api/debug/dates
```

После `?force=1` в `/api/version` должно появиться `status: "ok"` и
`message` с числом занятий. `/schedule/<группа>` должен отдавать
обычный плоский JSON-массив занятий с полем `date` в формате
`YYYY-MM-DD` — настоящим календарным днём, не шаблоном «по
понедельникам», как было в старом backend.

## 8. Подключить Android

Единственная правка в проекте — `android/app/build.gradle.kts`, поле
`BASE_URL`:

```kotlin
buildConfigField(
    "String",
    "BASE_URL",
    "\"https://czhsyilbrgylvjjjrlyh.supabase.co/functions/v1/schedule-api/\""
)
```

Маршруты в `ScheduleApi.kt` менять не нужно — этот backend специально
отвечает на `schedule/{group}` (без `/api/`) и на `api/health` /
`api/version` / `api/groups` (с `/api/`) — ровно в том виде, в каком их
уже вызывает текущий код приложения.

Коррекция дат (`DateSequenceCorrector.kt` на Android,
`correctDateSequence` в этом `index.ts`) теперь **избыточна**, но не
вредна: этот backend сам хранит корректные `date` в Postgres, поэтому
Android просто не найдёт подозрительных дат для исправления.
