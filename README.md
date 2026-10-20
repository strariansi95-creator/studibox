# Студибокс (Studibox)

Нативное Android-приложение для просмотра расписания занятий + backend-часть
(Supabase Edge Function), которая забирает XLSX с Яндекс.Диска и отдаёт REST API.

```
Yandex Disk → Backend (Edge Function) → XLSX parser → Postgres → REST API → Android → Room cache
```

## Структура

```
F:\AIAPP2
 ├── android\                  Android Studio проект (Kotlin, Compose, Material 3)
 │   ├── app\
 │   ├── gradle\
 │   ├── keystore\             ключ для подписи release-сборки
 │   ├── build.gradle.kts
 │   ├── settings.gradle.kts
 │   ├── gradlew / gradlew.bat
 │   └── build-release.bat     сборка одной командой
 └── backend\
     ├── sql\schema.sql        таблицы groups / schedule / metadata / import_log
     ├── sql\cron.sql          автозапуск импорта раз в 30 минут
     └── supabase\functions\schedule-api\index.ts
```

## Сборка Release APK (Windows)

```bat
cd /d F:\AIAPP2\android
build-release.bat
```

Скрипт сам проверит наличие `gradle-wrapper.jar`, выполнит `clean`, прогонит
unit-тесты дат и парсера, затем соберёт APK.

Вручную то же самое:

```bat
cd /d F:\AIAPP2\android
gradlew.bat clean
gradlew.bat testReleaseUnitTest
gradlew.bat assembleRelease
```

Готовый APK:

```
F:\AIAPP2\android\app\build\outputs\apk\release\app-release.apk
```

### Про gradle-wrapper.jar

`gradle-wrapper.jar` — бинарный файл, он не входит в этот исходник.
Получить его можно любым из двух способов:

1. Открыть `F:\AIAPP2\android` в Android Studio — при первой синхронизации
   IDE создаст wrapper автоматически.
2. Либо, если Gradle установлен глобально:
   `gradle wrapper --gradle-version 8.9`

`build-release.bat` пытается сделать второй вариант сам.

## Подпись

Release подписывается ключом `android\keystore\studibox-release.jks`
(параметры в `android\keystore.properties`). Оба файла в репозиторий
не входят (`.gitignore`) — храните их копию отдельно.
Благодаря этому APK устанавливается на устройство сразу, без ручной подписи.
Для публикации в Google Play замените ключ на свой.

## Релизные сборки (GitHub Actions)

Ветка **`release`** — релизная. Каждый push в неё запускает
`.github/workflows/release-apk.yml`: собирается подписанный APK и
публикуется в [Releases](../../releases) с тегом `v<versionName>-<номер сборки>`.

Порядок выпуска:

```bat
git checkout release
git merge main
git push
```

Для подписи в CI нужны секреты репозитория (Settings → Secrets and variables → Actions):

| Секрет | Что это |
|---|---|
| `KEYSTORE_BASE64` | файл `studibox-release.jks` в base64 |
| `KEYSTORE_PASSWORD` | `storePassword` из `keystore.properties` |
| `KEY_ALIAS` | `keyAlias` |
| `KEY_PASSWORD` | `keyPassword` |

Без секретов workflow не падает, а пропускает сборку с предупреждением.

## Проверка APK после сборки

```bat
cd /d F:\AIAPP2\android
"%LOCALAPPDATA%\Android\Sdk\build-tools\35.0.0\aapt.exe" dump badging app\build\outputs\apk\release\app-release.apk | findstr /C:"package" /C:"uses-permission"
adb install -r app\build\outputs\apk\release\app-release.apk
adb shell am start -n com.studibox.app/.MainActivity
adb logcat -s StudiboxNet StudiboxSync StudiboxNotif
```

Ожидаемое: `package: name='com.studibox.app'`, среди permissions —
`android.permission.INTERNET` и `android.permission.POST_NOTIFICATIONS`.

## Backend

1. `backend\sql\schema.sql` — выполнить в SQL Editor Supabase.
   Скрипт идемпотентный: `create table if not exists`, существующие данные не трогает.
2. `backend\supabase\functions\schedule-api\index.ts` — задеплоить:
   `supabase functions deploy schedule-api`
3. `backend\sql\cron.sql` — опционально, автоимпорт раз в 30 минут
   через `pg_cron` + `pg_net`.

Cron не обязателен: backend проверяет источник сам при обращении к `/api/version`,
если с прошлой проверки прошло больше 30 минут. Приложение опрашивает этот
endpoint каждые 30 минут через WorkManager, поэтому цепочка обновления
замкнута и работает без какой-либо ручной настройки:

```
Android (WorkManager, 30 мин) → /api/version → проверка Яндекса → новый SHA-256
   → импорт → version++ → Android видит новую версию → перекачивает расписание → Room → UI
```

Ручной запуск и диагностика:

```
GET  .../api/source             — какой способ загрузки сработал, размер и SHA-256 файла
POST .../api/import             — импорт, если файл изменился
POST .../api/import?force=1     — импорт даже если SHA-256 не изменился
```

## Источник расписания

```
https://docs.360.yandex.ru/view/d/eSjfjNM06Zcyzwqv5124yiPegnqahzm72s0qoIz-cKg6Y1haOWRpYXFSZw
```

Это ссылка Яндекс Документов, а не публичная ссылка Диска, поэтому напрямую
в Disk API она не подставляется. Внутри её токена (base64url) лежит
`<32 байта хеша>:<id публичного ресурса>` — для этой ссылки id равен `cXZ9diaqRg`.
Backend извлекает его сам и перебирает способы загрузки по порядку:

1. Disk API с `public_key=https://disk.yandex.ru/i/cXZ9diaqRg`
2. Disk API с `public_key=https://disk.360.yandex.ru/i/cXZ9diaqRg`
3. Disk API с исходной ссылкой Документов как `public_key`
4. Прямое скачивание ссылки с `?download=1` и без него
5. То же самое для запасной ссылки `https://disk.360.yandex.ru/i/bfF89K1c0tsG3g`

Первый сработавший способ используется и записывается в `metadata.source_strategy`.
Если файл за ссылкой заменят новым — подхватится автоматически, ссылка постоянная.
Сменить источник можно без правки кода: переменная окружения `SCHEDULE_SOURCE_URL`
у Edge Function.

Проверить, что источник читается, можно прямо в браузере:
`.../api/source` вернёт `{"ok":true,"strategy":"...","bytes":...,"sha256":"..."}`.

### Как backend защищает данные

- Файл скачивается по постоянной публичной ссылке через Yandex Disk API,
  с редиректами, проверкой `Content-Type` и сигнатуры ZIP/XLSX.
- Считается SHA-256; если хеш не изменился — импорт не выполняется.
- XLSX сначала полностью парсится, проверяется количество групп и занятий,
  и только потом подменяются рабочие данные.
- Любая ошибка (недоступен Яндекс, битый файл, слишком мало занятий) —
  старое расписание остаётся на месте, ошибка пишется в `import_log`.

## Правило дат

День недели **всегда** вычисляется из ISO-даты и никогда из текста заголовка.
Если в XLSX лист `160926` подписан «Вторник», а дата внутри — 16.09.2026,
то днём недели будет **среда (3)**.

Гарантируется в трёх местах независимо:

| Слой | Механизм |
|---|---|
| Парсер XLSX | `isoDayOfWeek(date)` из реальной даты листа |
| Postgres | триггер `enforce_day_of_week` → `extract(isodow from date)` |
| Android | `DateUtils.dayOfWeek(date)`, серверное `day_of_week` только логируется |

Нумерация: 1 = понедельник … 7 = воскресенье.
На экране расписания доступны ПН–СБ; воскресенье корректно показывается как
день без занятий. Занятия выбираются строго по ISO-дате, поэтому смешать
15.09.2026 и 16.09.2026 технически невозможно.

## Заметки

Таблица `notes` в Room — только локальные данные пользователя.
Синхронизация расписания трогает лишь таблицы `groups` и `lessons`,
поэтому заметки не исчезают при обновлении и переживают перезапуск приложения.
Заметку можно создать без даты, на конкретную дату или на день недели.

## API

```
GET /api/health              → {"ok":true,"time":"..."}
GET /api/version             → {"version":30,"updatedAt":"...","status":"ok","message":"..."}
GET /api/groups              → [{"id":23,"name":"КИТ-ВР-26"}, ...]
GET /api/schedule/{group}    → занятия выбранной группы
```

Базовый URL зашит в `app/build.gradle.kts` (`BuildConfig.BASE_URL`).

## Изменения (замена ApiMapper + редизайн UI)

**ApiMapper.kt заменён** на рабочую версию, соответствующую реальному формату API:
`{ group, updatedAt, version, days: [{ date, day, lessons: [{ date, dayOfWeek, timeStart, timeEnd, subject, teacher, room, type, subgroup }] }] }`.
День недели по-прежнему всегда вычисляется из `date`, серверный `dayOfWeek` только логируется при расхождении.

Backend, Supabase, API, `ScheduleApi.kt`, Room-схема (кроме одного поля ниже) и
существующая синхронизация не менялись.

**Одно добавленное поле**: `LessonEntity.type` (тип занятия — Лекция/Практика/Лабораторная),
нужно для бейджа в новой карточке. Добавлено через настоящую Room-миграцию
`MIGRATION_1_2` (`ALTER TABLE lessons ADD COLUMN type ...`), а не через
`fallbackToDestructiveMigration` — поэтому таблица `notes` эту миграцию не проходит
и заметки при обновлении приложения не теряются.

**Что нового в интерфейсе:**
- Карточка пары: крупное время, предмет — самый заметный элемент, чип с типом занятия,
  преподаватель/аудитория — второстепенным текстом, пустые поля скрываются.
- Текущая пара подсвечивается синей полосой и подписью «СЕЙЧАС», следующая — «СЛЕДУЮЩАЯ»;
  определяются строго по `startTime <= сейчас < endTime` (реальное время телефона),
  а не по номеру пары — см. `ui/common/LessonTiming.kt` и тесты `LessonTimingTest.kt`.
- Прошедшие пары — с пониженной непрозрачностью, не серые и не нечитаемые.
- Фильтр подгрупп «Все / 1 / 2» — только фильтр отображения, в Room и на сервере
  ничего не удаляет и не меняет; выбор сохраняется в DataStore и переживает перезапуск.
  Переключатель скрыт, если у группы нет занятий с подгруппами.
- Заголовок дня: «Сегодня / Четверг, 17 сентября» либо «Четверг / 17 сентября» для
  другого дня; кнопка «Сегодня» показывается только когда выбран не сегодняшний день.
- Экран «Сегодня»: приветствие по времени суток, группа, день, блоки «Сейчас» /
  «Следующая» / «Остальные пары», раздел заметок.
- Понятные пустые состояния: «Расписание пока недоступно» + кнопка «Обновить» (если у
  группы вообще нет кэша) отдельно от «В этот день занятий нет» (если день просто пустой).
- Аккуратный loading state («Загружаем список групп…») вместо голого спиннера.
- Ошибки синхронизации показываются понятным текстом в снекбаре с кнопкой «Повторить»
  при офлайне; технические детали остаются только в Logcat.
- Тёмная/светлая/системная тема — везде использованы токены `MaterialTheme.colorScheme`,
  отдельно проверенных бросающихся в глаза проблем контраста не введено.

Навигация внизу оставлена с 4 вкладками (Сегодня/Расписание/Заметки/Настройки) —
раздел «Заметки» уже был рабочей частью приложения, и удалять готовую функцию
только ради вида нижней панели не стал; сами иконки и вид панели не менялись
(и так Material 3 `NavigationBar`).

### Сборка (JDK 21)

```bat
cd /d F:\AIAPP2\android
set "JAVA_HOME=C:\Users\struc\.jdks\jbr-21.0.11"
set "PATH=%JAVA_HOME%\bin;%PATH%"
gradlew.bat clean
gradlew.bat testReleaseUnitTest
gradlew.bat assembleRelease
```

APK: `F:\AIAPP2\android\app\build\outputs\apk\release\app-release.apk`

## Режим преподавателя (продолжение разработки)

**Баг подгруппы исправлен**: `util/SubgroupFormat.kt` нормализует любой формат
исходного значения (`"1"`, `"1 подгруппа"`, `" 2 "`) в короткую метку для чипа
(`"1"`) и полную подпись для карточки (`"1 подгруппа"`), без дублирования слова.
Используется и в `Components.kt` (текст в карточке), и в `SubgroupSelector` (чипы).

**Роль пользователя**: `UserRole.STUDENT` / `UserRole.TEACHER` в `SettingsStore`.
Первый запуск — `RolePickerScreen` (Студент/Преподаватель). Существующие
пользователи с уже выбранной группой этот экран не видят вообще: как только
у пользователя когда-либо появляется группа или преподаватель, выставляется
флаг `roleChosen=true` насовсем, поэтому даже если группа позже пропадёт с
сервера — вернёт на выбор ГРУППЫ, а не заново на выбор роли.

**Откуда берутся ФИО преподавателей**: отдельного backend-эндпоинта нет и не
понадобился — `ScheduleRepository.refreshAllGroupsForTeacherMode()` скачивает
`/api/schedule/{group}` для КАЖДОЙ группы (до 5 запросов параллельно) через
**тот же самый, не изменённый `ApiMapper.kt`**, и складывает всё в ту же таблицу
`lessons` — она и раньше была устроена так, что можно безопасно хранить сразу
несколько групп (каждая группа заменяется независимо). Список ФИО — это просто
`SELECT DISTINCT teacher FROM lessons`.

**Несколько групп на одной паре**: `ui/common/TeacherAggregation.kt` схлопывает
занятия с одинаковой датой/временем/предметом в один `TeacherLessonSlot` со
списком групп — см. тест `TeacherAggregationTest.kt`.

**Фильтрация**: студент — группа + подгруппа (как было). Преподаватель — только
по ФИО, группа в фильтрации не участвует вообще, как и требовалось.

**Что изменено под капотом, но не в поведении студента**:
- `LessonDao` — добавлены запросы `observeTeachers/observeByTeacherAndDate/observeDatesForTeacher`,
  никаких изменений в существующих запросах и без миграции схемы (новых колонок не потребовалось).
- `ScheduleRepository.sync()` — **не тронут ни одной строкой**, студенческий путь
  синхронизации ровно тот же, что был.
- `SyncWorker` и `AppViewModel.refresh()` — добавлена развилка по роли:
  студент идёт в `sync()`, преподаватель — в `refreshAllGroupsForTeacherMode()`.
- `TodayScreen`/`ScheduleScreen` — рендер преподавателя добавлен через `if (isTeacher)`
  в начале, весь существующий студенческий код ниже не менялся.

**Известное ограничение**: локальные уведомления о занятиях (`LessonNotificationScheduler`)
сейчас продолжают работать только для студенческой выбранной группы — для роли
«Преподаватель» напоминания не планируются. Это не было явно в списке требований
для уведомлений и не ломает ничего существующего, но если нужно — расширяется
отдельно (нужно решить, от какой из его пар начинать напоминание, если у одной
пары сразу несколько групп).

### Файлы

Новые:
- `util/SubgroupFormat.kt`
- `ui/common/LessonTiming.kt` (переписан на обобщённую функцию)
- `ui/common/TeacherAggregation.kt`
- `ui/onboarding/RolePickerScreen.kt`
- `ui/teacher/TeacherPickerScreen.kt`, `ui/teacher/TeacherPickerViewModel.kt`
- Тесты: `SubgroupFormatTest.kt`, `TeacherAggregationTest.kt`

Изменены:
- `data/prefs/SettingsStore.kt` — `UserRole`, `selectedTeacher`, `roleChosen`
- `data/local/Daos.kt` — запросы для режима преподавателя
- `data/repo/ScheduleRepository.kt` — `refreshAllGroupsForTeacherMode` и читатели для преподавателя
- `sync/SyncWorker.kt`, `ui/AppViewModel.kt` — развилка по роли
- `ui/nav/StudiboxNav.kt` — маршруты `ROLE_PICKER`/`TEACHER_PICKER`, логика "настроено"
- `ui/settings/SettingsScreen.kt` — переключатель роли, «Сменить преподавателя»
- `ui/today/TodayViewModel.kt`, `ui/today/TodayScreen.kt` — ветка преподавателя
- `ui/schedule/ScheduleViewModel.kt`, `ui/schedule/ScheduleScreen.kt` — ветка преподавателя
- `ui/common/Components.kt` — `TeacherLessonCard`, исправление подписи подгруппы

### Сборка

```bat
cd /d F:\AIAPP2\android
set "JAVA_HOME=C:\Users\struc\.jdks\jbr-21.0.11"
set "PATH=%JAVA_HOME%\bin;%PATH%"
gradlew.bat clean
gradlew.bat testReleaseUnitTest
gradlew.bat assembleRelease
```

APK: `F:\AIAPP2\android\app\build\outputs\apk\release\app-release.apk`
