-- Автоматическая проверка Яндекс.Диска примерно раз в 30 минут.
-- Требуются расширения pg_cron и pg_net (включаются в Supabase Dashboard
-- -> Database -> Extensions).
--
-- Адрес ниже уже настроен на проект czhsyilbrgylvjjjrlyh — менять не
-- нужно, если проект не сменится снова.
--
-- timeout_milliseconds обязателен: скачивание XLSX с Яндекса почти всегда
-- дольше дефолтных 5 секунд pg_net, и cron тогда помечает запрос как
-- Timeout, хотя функция ещё работает. JWT на функции выключен, в заголовке
-- достаточно публичного anon-ключа.

create extension if not exists pg_cron;
create extension if not exists pg_net;

select cron.unschedule('studibox-import')
where exists (select 1 from cron.job where jobname = 'studibox-import');

select cron.schedule(
    'studibox-import',
    '*/30 * * * *',
    $$
    select net.http_post(
        url     := 'https://czhsyilbrgylvjjjrlyh.supabase.co/functions/v1/schedule-api/api/import',
        headers := jsonb_build_object(
            'Content-Type', 'application/json',
            'Authorization', 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImN6aHN5aWxicmd5bHZqampybHloIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTExNzIxMzcsImV4cCI6MjEwNjc0ODEzN30._bHKK_0P7UYvJFQLg_-PTztcor1bDHjsukCaKox2k8E',
            'apikey', 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImN6aHN5aWxicmd5bHZqampybHloIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTExNzIxMzcsImV4cCI6MjEwNjc0ODEzN30._bHKK_0P7UYvJFQLg_-PTztcor1bDHjsukCaKox2k8E'
        ),
        body    := '{}'::jsonb,
        timeout_milliseconds := 120000
    );
    $$
);
