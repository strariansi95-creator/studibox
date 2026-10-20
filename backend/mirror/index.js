'use strict';

/**
 * Studibox RU-зеркало перед Supabase Edge Function.
 *
 * Зачем: у части операторов РФ (Билайн и другие) нестабильно
 * блокируется/режется трафик к Supabase/Cloudflare. Эта функция живёт на
 * Yandex Cloud Functions — в российской инфраструктуре, которую операторы
 * РФ не блокируют, — и просто перекладывает запрос на настоящий backend.
 *
 * Ничего не знает про формат расписания: получает путь в query-параметре
 * ?p=, дёргает https://<SUPABASE_HOST><p> и отдаёт ответ как есть. Логика
 * импорта XLSX, парсер и Supabase-функция schedule-api НЕ меняются.
 *
 * Переменные окружения (задаются в консоли Yandex Cloud Functions):
 *   SUPABASE_HOST — например "gdjpevzioenbeyammukm.supabase.co"
 *   ALLOWED_PREFIX — необязательно, по умолчанию "/functions/v1/schedule-api/"
 *                    (защита от использования функции как открытого прокси)
 *   CACHE_TTL_MS   — необязательно, по умолчанию 180000 (3 минуты)
 */

const https = require('https');

const SUPABASE_HOST = process.env.SUPABASE_HOST || 'gdjpevzioenbeyammukm.supabase.co';
const ALLOWED_PREFIX = process.env.ALLOWED_PREFIX || '/functions/v1/schedule-api/';
const CACHE_TTL_MS = Number(process.env.CACHE_TTL_MS || 180000);
const UPSTREAM_TIMEOUT_MS = 10000;

// Переживает "тёплые" повторные вызовы функции на одном инстансе.
// На холодном старте пустой — это ожидаемо и не страшно.
const cache = new Map(); // path -> { status, headers, body, expiresAt }

function readCache(path) {
  const hit = cache.get(path);
  if (!hit) return null;
  if (hit.expiresAt < Date.now()) {
    cache.delete(path);
    return null;
  }
  return hit;
}

function writeCache(path, status, headers, body) {
  if (status < 200 || status >= 300) return;
  cache.set(path, { status, headers, body, expiresAt: Date.now() + CACHE_TTL_MS });
}

function fetchUpstream(path) {
  return new Promise((resolve, reject) => {
    const req = https.request(
      {
        host: SUPABASE_HOST,
        path,
        method: 'GET',
        headers: { accept: 'application/json' },
        timeout: UPSTREAM_TIMEOUT_MS,
      },
      (res) => {
        const chunks = [];
        res.on('data', (chunk) => chunks.push(chunk));
        res.on('end', () => {
          resolve({
            status: res.statusCode || 502,
            headers: res.headers,
            body: Buffer.concat(chunks).toString('utf8'),
          });
        });
      }
    );
    req.on('timeout', () => req.destroy(new Error('upstream timeout')));
    req.on('error', reject);
    req.end();
  });
}

function extractRequestedPath(event) {
  // Основной контракт: клиент передаёт исходный путь в ?p=...
  const fromQuery = event.queryStringParameters && event.queryStringParameters.p;
  if (fromQuery) return fromQuery;

  // На случай прямого вызова с полным путём (например, при ручной проверке
  // через API Gateway) — берём путь запроса как есть.
  if (typeof event.path === 'string' && event.path.length > 1) return event.path;

  return null;
}

module.exports.handler = async function (event) {
  const method = event.httpMethod || 'GET';
  if (method !== 'GET') {
    return { statusCode: 405, body: JSON.stringify({ error: 'method_not_allowed' }) };
  }

  const path = extractRequestedPath(event);
  if (!path || !path.startsWith(ALLOWED_PREFIX)) {
    return {
      statusCode: 400,
      headers: { 'content-type': 'application/json; charset=utf-8' },
      body: JSON.stringify({ error: 'bad_path', hint: 'ожидался ?p=' + ALLOWED_PREFIX + '...' }),
    };
  }

  const cached = readCache(path);
  if (cached) {
    return {
      statusCode: cached.status,
      headers: { 'content-type': 'application/json; charset=utf-8', 'x-studibox-mirror-cache': 'hit' },
      body: cached.body,
    };
  }

  try {
    const upstream = await fetchUpstream(path);
    writeCache(path, upstream.status, upstream.headers, upstream.body);
    return {
      statusCode: upstream.status,
      headers: { 'content-type': 'application/json; charset=utf-8', 'x-studibox-mirror-cache': 'miss' },
      body: upstream.body,
    };
  } catch (err) {
    return {
      statusCode: 502,
      headers: { 'content-type': 'application/json; charset=utf-8' },
      body: JSON.stringify({ error: 'upstream_unreachable', message: String(err && err.message || err) }),
    };
  }
};
