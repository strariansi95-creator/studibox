-- ОДНОРАЗОВАЯ миграция уже лежащих в базе данных (SQL Editor -> Run).
-- Нужна, пока Яндекс.Диск недоступен и новый импорт ничего не перезапишет.
-- После обновления index.ts новые импорты уже сразу кладут правильные данные.
--
--  1) Названия групп: "КИТ-ВР-26-1 - 30 СТУДЕНТОВ" -> "КИТ-ВР-26-1"
--  2) Преподаватели: "Яковлева Л.Е.(Аммосов В.Н.)"  -> "Яковлева Л.Е."
--
-- Всё в одной транзакции: если что-то пойдёт не так, ничего не изменится.
-- Сначала можно посмотреть, что будет изменено (запускать отдельно, до миграции):
--   select name from public.groups where name ~ '[0-9]+[[:space:]]*([Сс][Тт][Уу][Дд][Ее][Нн][Тт]|[Чч][Ее][Лл])';
--   select distinct teacher from public.schedule where teacher like '%(%';

begin;

-- Убирает хвост "- 30 СТУДЕНТОВ" / "30 студентов" / "(30 студентов)" / ", 28 чел." —
-- разделитель + число + "студент…"/"чел…". Регистр перечислен явно: сравнение без
-- учёта регистра для кириллицы зависит от локали базы, так надёжнее.
create or replace function pg_temp.strip_students(n text) returns text
language sql immutable as $$
  select btrim(regexp_replace(
    n,
    '[[:space:],;(–—-]+[0-9]+[[:space:]]*([Сс][Тт][Уу][Дд][Ее][Нн][Тт]|[Чч][Ее][Лл]).*$',
    ''
  ))
$$;

-- 1.1 Чистые группы (если их ещё нет)
insert into public.groups (name)
select distinct pg_temp.strip_students(name)
from public.groups
where pg_temp.strip_students(name) <> name
on conflict (name) do nothing;

-- 1.2 Строки, которые после переименования совпали бы с уже существующими чистыми
delete from public.schedule s
where pg_temp.strip_students(s.group_name) <> s.group_name
  and exists (
    select 1 from public.schedule c
    where c.group_name = pg_temp.strip_students(s.group_name)
      and c.date = s.date and c.lesson_number = s.lesson_number
      and c.subgroup = s.subgroup and c.subject = s.subject
  );

-- 1.3 ...и дубли среди самих переименовываемых строк (две "грязные" записи -> одно имя)
delete from public.schedule s
using public.schedule o
where pg_temp.strip_students(s.group_name) <> s.group_name
  and pg_temp.strip_students(o.group_name) = pg_temp.strip_students(s.group_name)
  and o.date = s.date and o.lesson_number = s.lesson_number
  and o.subgroup = s.subgroup and o.subject = s.subject
  and o.id < s.id;

-- 1.4 Переименовать занятия и перепривязать к чистой группе.
-- ВАЖНО: schedule.group_id -> groups(id) on delete cascade, поэтому сначала
-- перепривязываем занятия, и только потом удаляем старые группы.
update public.schedule s
set group_name = pg_temp.strip_students(s.group_name),
    group_id   = g.id
from public.groups g
where pg_temp.strip_students(s.group_name) <> s.group_name
  and g.name = pg_temp.strip_students(s.group_name);

-- 1.5 Старые группы с хвостом (на них уже ничего не ссылается)
delete from public.groups
where pg_temp.strip_students(name) <> name;

-- 2. Преподаватели: скобки вместе с содержимым (и незакрытая скобка) убираются,
-- несколько преподавателей через запятую остаются.
update public.schedule
set teacher = btrim(
      regexp_replace(
        regexp_replace(
          regexp_replace(teacher, '[[:space:]]*\([^)]*\)?', ' ', 'g'),
          '[[:space:]]+', ' ', 'g'),
        '[[:space:]]+,', ',', 'g'),
      ' ,;')
where teacher like '%(%';

-- 3. Поднять версию данных. Приложение скачивает расписание заново, только когда
-- серверная версия отличается от сохранённой на телефоне; эта миграция версию
-- сама не меняет, поэтому без этого шага приложения не увидели бы правок.
update public.metadata
set value = (case when value ~ '^[0-9]+$' then value::int else 0 end + 1)::text,
    updated_at = now()
where key = 'version';

commit;

-- Проверка после миграции (запускать отдельно): под какими именами лежат дни.
-- Каждая группа должна быть ОДНА, без хвоста "СТУДЕНТОВ", и с понедельником:
--   select group_name, count(*) as занятий, min(date), max(date)
--   from public.schedule group by 1 order by 1;
--   select date, day_of_week, count(*) from public.schedule group by 1, 2 order by 1;
