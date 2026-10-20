-- ОДНОРАЗОВАЯ чистка после обновления index.ts.
--
-- Раньше в имени группы оставался хвост из заголовка Excel:
--   "КИТ-ТЭСИС-26-1 - 30 СТУДЕНТОВ"
-- Теперь парсер сохраняет чистое имя "КИТ-ТЭСИС-26-1". Старые строки с
-- хвостом сами не исчезнут (импорт заменяет только занятия за те даты, что
-- есть в файле, а таблицу groups не чистит) — в приложении в списке были бы
-- дубли групп. Выполнить в SQL Editor ОДИН раз, потом запустить импорт:
--   .../functions/v1/schedule-api/api/import?force=1

delete from public.schedule
where group_name ~* '\s[-–—]\s+\d+\s+(студент|чел)';

delete from public.groups g
where not exists (select 1 from public.schedule s where s.group_id = g.id);
