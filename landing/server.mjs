import http from 'node:http';
import crypto from 'node:crypto';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { promises as fs } from 'node:fs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const PUBLIC_DIR = path.join(__dirname, 'public');
const DEFAULT_DATA_FILE = path.join(__dirname, 'data', 'leads.json');
const DEFAULT_EVENTS_FILE = path.join(__dirname, 'data', 'events.log');

const MIME_TYPES = {
  '.css': 'text/css; charset=utf-8',
  '.html': 'text/html; charset=utf-8',
  '.ico': 'image/x-icon',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.js': 'application/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.txt': 'text/plain; charset=utf-8',
  '.webp': 'image/webp',
  '.xml': 'application/xml; charset=utf-8'
};

const ALLOWED_GOALS = new Set([
  'sommeil',
  'energie',
  'concentration',
  'composition_corporelle',
  'routine'
]);

const KNOWN_EVENTS = new Set([
  'lp_view',
  'cta_click',
  'form_start',
  'form_submit_success',
  'form_submit_error'
]);

const NAME_REGEX = /^[\p{L}\p{M}][\p{L}\p{M}' -]{0,79}$/u;
const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/i;
const EVENT_REGEX = /^[a-z0-9_]{2,64}$/;
const MAX_BODY_BYTES = 64 * 1024;

function lowerTrimmed(value) {
  return String(value ?? '').trim().toLowerCase();
}

function parseBoolean(value) {
  if (value === true || value === 'true' || value === '1' || value === 1 || value === 'on') {
    return true;
  }
  return false;
}

function sanitizeName(value) {
  const trimmed = String(value ?? '').trim();
  if (!trimmed || trimmed.length > 80 || !NAME_REGEX.test(trimmed)) {
    return null;
  }
  return trimmed;
}

function sanitizeGoal(value) {
  const trimmed = lowerTrimmed(value);
  return ALLOWED_GOALS.has(trimmed) ? trimmed : null;
}

function sanitizeSource(value) {
  const trimmed = String(value ?? '').trim();
  if (!trimmed) {
    return 'manual';
  }
  return trimmed.slice(0, 100);
}

function sanitizeEmail(value) {
  const trimmed = String(value ?? '').trim();
  if (!trimmed || trimmed.length > 254 || !EMAIL_REGEX.test(trimmed)) {
    return null;
  }
  return trimmed.toLowerCase();
}

function securityHeaders() {
  return {
    'Content-Security-Policy': "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; script-src 'self'; connect-src 'self'; form-action 'self'; base-uri 'self'; frame-ancestors 'none'",
    'Permissions-Policy': 'geolocation=(), microphone=(), camera=()',
    'Referrer-Policy': 'strict-origin-when-cross-origin',
    'X-Content-Type-Options': 'nosniff',
    'X-Frame-Options': 'DENY'
  };
}

function writeHeaders(res, headers = {}) {
  for (const [key, value] of Object.entries({ ...securityHeaders(), ...headers })) {
    res.setHeader(key, value);
  }
}

function sendJson(res, statusCode, payload, headers = {}) {
  const body = JSON.stringify(payload);
  res.statusCode = statusCode;
  writeHeaders(res, {
    'Cache-Control': 'no-store',
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(body),
    ...headers
  });
  res.end(body);
}

function sendHtml(res, statusCode, html) {
  res.statusCode = statusCode;
  writeHeaders(res, {
    'Cache-Control': 'no-store',
    'Content-Type': 'text/html; charset=utf-8',
    'Content-Length': Buffer.byteLength(html)
  });
  res.end(html);
}

function redirect(res, location, statusCode = 302) {
  res.statusCode = statusCode;
  writeHeaders(res, {
    Location: location,
    'Cache-Control': 'no-store'
  });
  res.end();
}

async function parseBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let totalBytes = 0;

    req.on('data', (chunk) => {
      totalBytes += chunk.length;
      if (totalBytes > MAX_BODY_BYTES) {
        reject(new Error('BODY_TOO_LARGE'));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });

    req.on('end', () => {
      const raw = Buffer.concat(chunks).toString('utf8').trim();
      if (!raw) {
        resolve({});
        return;
      }

      const contentType = (req.headers['content-type'] || '').split(';')[0].trim();
      try {
        if (contentType === 'application/x-www-form-urlencoded') {
          resolve(Object.fromEntries(new URLSearchParams(raw).entries()));
          return;
        }

        resolve(JSON.parse(raw));
      } catch {
        reject(new Error('INVALID_JSON'));
      }
    });

    req.on('error', reject);
  });
}

function getClientIp(req) {
  const forwarded = req.headers['x-forwarded-for'];
  if (typeof forwarded === 'string' && forwarded.length > 0) {
    return forwarded.split(',')[0].trim();
  }
  const candidate = req.socket.remoteAddress || '0.0.0.0';
  return candidate.startsWith('::ffff:') ? candidate.replace('::ffff:', '') : candidate;
}

function hashIp(ip) {
  const salt = process.env.IP_HASH_SALT || 'biotrack-landing';
  return crypto.createHash('sha256').update(`${salt}:${ip}`).digest('hex');
}

function isSafePublicPath(filePath) {
  return filePath.startsWith(PUBLIC_DIR + path.sep) || filePath === PUBLIC_DIR;
}

function resolvePublicPath(requestPath) {
  let decodedPath;
  try {
    decodedPath = decodeURIComponent(requestPath);
  } catch {
    return null;
  }

  const cleanPath = decodedPath.split('?')[0].split('#')[0];
  const requested = cleanPath === '/' ? '/index.html' : cleanPath;
  const normalized = path.posix.normalize(requested);
  const withoutLeadingSlash = normalized.startsWith('/') ? normalized.slice(1) : normalized;
  const absolutePath = path.join(PUBLIC_DIR, withoutLeadingSlash);
  return isSafePublicPath(absolutePath) ? absolutePath : null;
}

const fileLocks = new Map();

async function withFileLock(filePath, operation) {
  const previous = fileLocks.get(filePath) || Promise.resolve();
  const current = previous
    .catch(() => undefined)
    .then(operation)
    .finally(() => {
      if (fileLocks.get(filePath) === current) {
        fileLocks.delete(filePath);
      }
    });

  fileLocks.set(filePath, current);
  return current;
}

async function ensureFile(filePath, fallbackContent) {
  await fs.mkdir(path.dirname(filePath), { recursive: true });
  try {
    await fs.access(filePath);
  } catch {
    await fs.writeFile(filePath, fallbackContent, 'utf8');
  }
}

async function readLeadStore(dataFile) {
  await ensureFile(dataFile, JSON.stringify({ leads: [] }, null, 2));
  const raw = await fs.readFile(dataFile, 'utf8');
  try {
    const parsed = JSON.parse(raw);
    if (!parsed || !Array.isArray(parsed.leads)) {
      return { leads: [] };
    }
    return parsed;
  } catch {
    return { leads: [] };
  }
}

async function writeLeadStore(dataFile, store) {
  const tmpPath = `${dataFile}.${process.pid}.tmp`;
  await fs.writeFile(tmpPath, JSON.stringify(store, null, 2), 'utf8');
  await fs.rename(tmpPath, dataFile);
}

function validateLeadPayload(payload) {
  const errors = [];

  const firstName = sanitizeName(payload.first_name);
  if (!firstName) {
    errors.push('first_name');
  }

  const lastName = sanitizeName(payload.last_name);
  if (!lastName) {
    errors.push('last_name');
  }

  const email = sanitizeEmail(payload.email);
  if (!email) {
    errors.push('email');
  }

  const primaryGoal = sanitizeGoal(payload.primary_goal);
  if (!primaryGoal) {
    errors.push('primary_goal');
  }

  const consentMarketing = parseBoolean(payload.consent_marketing);
  if (!consentMarketing) {
    errors.push('consent_marketing');
  }

  return {
    errors,
    normalized: {
      first_name: firstName,
      last_name: lastName,
      email,
      primary_goal: primaryGoal,
      consent_marketing: consentMarketing,
      source: sanitizeSource(payload.source),
      created_at: new Date().toISOString()
    }
  };
}

function createRateLimiter() {
  const buckets = new Map();
  return function checkRateLimit(key, limit, windowMs) {
    const now = Date.now();
    const current = buckets.get(key) || [];
    const recent = current.filter((timestamp) => now - timestamp < windowMs);

    if (recent.length >= limit) {
      const oldest = recent[0];
      const retryAfterMs = Math.max(windowMs - (now - oldest), 1000);
      buckets.set(key, recent);
      return {
        allowed: false,
        retryAfter: Math.ceil(retryAfterMs / 1000)
      };
    }

    recent.push(now);
    buckets.set(key, recent);
    return { allowed: true, retryAfter: 0 };
  };
}

async function appendEvent(eventsFile, entry) {
  await ensureFile(eventsFile, '');
  await fs.appendFile(eventsFile, `${JSON.stringify(entry)}\n`, 'utf8');
}

async function dispatchConfirmation({ lead, confirmationUrl, webhookUrl }) {
  if (webhookUrl) {
    try {
      const response = await fetch(webhookUrl, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json; charset=utf-8'
        },
        body: JSON.stringify({
          type: 'double_opt_in',
          lead_id: lead.id,
          email: lead.email,
          first_name: lead.first_name,
          last_name: lead.last_name,
          confirmation_url: confirmationUrl,
          primary_goal: lead.primary_goal,
          created_at: lead.created_at
        })
      });

      if (!response.ok) {
        console.error(`[AJUSTE] Confirmation webhook failed with status ${response.status}`);
      }
      return;
    } catch (error) {
      console.error('[AJUSTE] Confirmation webhook request failed:', error.message);
    }
  }

  console.info(`[AJUSTE] Double opt-in URL for ${lead.email}: ${confirmationUrl}`);
}

function defaultNotFoundPage() {
  return `<!doctype html>
<html lang="fr">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>Page introuvable</title>
  </head>
  <body style="font-family: sans-serif; padding: 40px;">
    <h1>Page introuvable</h1>
    <p>La ressource demandée n'existe pas.</p>
    <p><a href="/">Retour à la landing page</a></p>
  </body>
</html>`;
}

export function createLandingServer(options = {}) {
  const dataFile = options.dataFile || process.env.LEADS_DATA_FILE || DEFAULT_DATA_FILE;
  const eventsFile = options.eventsFile || process.env.EVENTS_FILE || DEFAULT_EVENTS_FILE;
  const leadRateLimit = options.leadRateLimit ?? 10;
  const leadRateWindowMs = options.leadRateWindowMs ?? 60 * 60 * 1000;
  const eventRateLimit = options.eventRateLimit ?? 240;
  const eventRateWindowMs = options.eventRateWindowMs ?? 60 * 60 * 1000;
  const baseUrl = (options.baseUrl || process.env.PUBLIC_BASE_URL || `http://localhost:${process.env.PORT || 8787}`).replace(/\/$/, '');
  const confirmationWebhookUrl = options.confirmationWebhookUrl || process.env.CONFIRMATION_WEBHOOK_URL || '';

  const checkRateLimit = createRateLimiter();

  return http.createServer(async (req, res) => {
    const method = req.method || 'GET';
    const url = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`);

    if (url.pathname === '/api/health' && method === 'GET') {
      sendJson(res, 200, { ok: true, timestamp: new Date().toISOString() });
      return;
    }

    if (url.pathname === '/api/leads' && method === 'POST') {
      const ip = getClientIp(req);
      const limit = checkRateLimit(`lead:${ip}`, leadRateLimit, leadRateWindowMs);
      if (!limit.allowed) {
        sendJson(
          res,
          429,
          {
            success: false,
            error_code: 'RATE_LIMIT'
          },
          {
            'Retry-After': String(limit.retryAfter)
          }
        );
        return;
      }

      let payload;
      try {
        payload = await parseBody(req);
      } catch (error) {
        if (error.message === 'BODY_TOO_LARGE') {
          sendJson(res, 400, { success: false, error_code: 'VALIDATION_ERROR', fields: ['body'] });
          return;
        }
        sendJson(res, 400, { success: false, error_code: 'VALIDATION_ERROR', fields: ['body'] });
        return;
      }

      const honeypot = String(payload.company ?? payload.website ?? '').trim();
      if (honeypot.length > 0) {
        sendJson(res, 201, {
          success: true,
          lead_id: crypto.randomUUID()
        });
        return;
      }

      const { errors, normalized } = validateLeadPayload(payload);
      if (errors.length > 0) {
        sendJson(res, 400, {
          success: false,
          error_code: 'VALIDATION_ERROR',
          fields: errors
        });
        return;
      }

      const token = crypto.randomBytes(24).toString('hex');
      const confirmationUrl = `${baseUrl}/api/leads/confirm?token=${token}`;
      const now = new Date().toISOString();

      const creationResult = await withFileLock(dataFile, async () => {
        const store = await readLeadStore(dataFile);
        const duplicate = store.leads.find((lead) => lead.email === normalized.email);
        if (duplicate) {
          return { duplicate: true };
        }

        const lead = {
          id: crypto.randomUUID(),
          first_name: normalized.first_name,
          last_name: normalized.last_name,
          email: normalized.email,
          primary_goal: normalized.primary_goal,
          consent_marketing: true,
          consent_timestamp: now,
          source: normalized.source,
          created_at: normalized.created_at,
          status: 'pending',
          confirmation_token: token,
          confirmation_sent_at: now,
          confirmed_at: null,
          ip_hash: hashIp(ip),
          user_agent: String(req.headers['user-agent'] || '').slice(0, 250)
        };

        store.leads.push(lead);
        await writeLeadStore(dataFile, store);
        return { duplicate: false, lead };
      });

      if (creationResult.duplicate) {
        sendJson(res, 409, {
          success: false,
          error_code: 'EMAIL_ALREADY_EXISTS'
        });
        return;
      }

      await dispatchConfirmation({
        lead: creationResult.lead,
        confirmationUrl,
        webhookUrl: confirmationWebhookUrl
      });

      const responsePayload = {
        success: true,
        lead_id: creationResult.lead.id,
        confirmation_required: true
      };

      if ((process.env.NODE_ENV || 'development') !== 'production') {
        responsePayload.debug_confirmation_url = confirmationUrl;
      }

      sendJson(res, 201, responsePayload);
      return;
    }

    if (url.pathname === '/api/leads/confirm' && method === 'GET') {
      const token = String(url.searchParams.get('token') || '').trim();
      if (!token || token.length < 12 || token.length > 128) {
        sendHtml(
          res,
          400,
          `<!doctype html><html lang="fr"><body style="font-family:sans-serif;padding:40px;"><h1>Lien invalide</h1><p>Ce lien de confirmation est invalide.</p><p><a href="/">Retour</a></p></body></html>`
        );
        return;
      }

      const confirmation = await withFileLock(dataFile, async () => {
        const store = await readLeadStore(dataFile);
        const lead = store.leads.find((item) => item.confirmation_token === token);
        if (!lead) {
          return { ok: false };
        }

        lead.status = 'confirmed';
        lead.confirmed_at = new Date().toISOString();
        lead.confirmation_token = null;
        await writeLeadStore(dataFile, store);
        return { ok: true, lead };
      });

      if (!confirmation.ok) {
        sendHtml(
          res,
          404,
          `<!doctype html><html lang="fr"><body style="font-family:sans-serif;padding:40px;"><h1>Lien expiré ou déjà utilisé</h1><p>Le lien de confirmation n'est plus valide.</p><p><a href="/">Retour</a></p></body></html>`
        );
        return;
      }

      const redirectTarget = `/merci.html?confirmed=1&email=${encodeURIComponent(confirmation.lead.email)}`;
      redirect(res, redirectTarget);
      return;
    }

    if (url.pathname === '/api/events' && method === 'POST') {
      const ip = getClientIp(req);
      const limit = checkRateLimit(`events:${ip}`, eventRateLimit, eventRateWindowMs);
      if (!limit.allowed) {
        sendJson(
          res,
          429,
          {
            success: false,
            error_code: 'RATE_LIMIT'
          },
          {
            'Retry-After': String(limit.retryAfter)
          }
        );
        return;
      }

      let payload;
      try {
        payload = await parseBody(req);
      } catch {
        sendJson(res, 400, { success: false, error_code: 'VALIDATION_ERROR', fields: ['body'] });
        return;
      }

      const eventName = String(payload.event || '').trim();
      if (!EVENT_REGEX.test(eventName)) {
        sendJson(res, 400, { success: false, error_code: 'VALIDATION_ERROR', fields: ['event'] });
        return;
      }

      const known = KNOWN_EVENTS.has(eventName) || eventName.startsWith('faq_');
      const eventPayload = {
        id: crypto.randomUUID(),
        event: eventName,
        known,
        path: String(payload.path || '').slice(0, 200),
        session_id: String(payload.session_id || '').slice(0, 80),
        referrer: String(payload.referrer || '').slice(0, 200),
        context: payload.context && typeof payload.context === 'object' ? payload.context : {},
        ip_hash: hashIp(ip),
        user_agent: String(req.headers['user-agent'] || '').slice(0, 250),
        created_at: new Date().toISOString()
      };

      await appendEvent(eventsFile, eventPayload);
      res.statusCode = 204;
      writeHeaders(res, { 'Cache-Control': 'no-store' });
      res.end();
      return;
    }

    if (url.pathname.startsWith('/api/')) {
      sendJson(res, 404, { success: false, error_code: 'NOT_FOUND' });
      return;
    }

    if (method !== 'GET' && method !== 'HEAD') {
      sendJson(res, 405, { success: false, error_code: 'METHOD_NOT_ALLOWED' });
      return;
    }

    const publicFilePath = resolvePublicPath(url.pathname);
    if (!publicFilePath) {
      sendHtml(res, 404, defaultNotFoundPage());
      return;
    }

    try {
      const fileBuffer = await fs.readFile(publicFilePath);
      const extension = path.extname(publicFilePath).toLowerCase();
      const contentType = MIME_TYPES[extension] || 'application/octet-stream';
      const isHtml = extension === '.html';

      res.statusCode = 200;
      writeHeaders(res, {
        'Cache-Control': isHtml ? 'public, max-age=120' : 'public, max-age=86400',
        'Content-Type': contentType,
        'Content-Length': fileBuffer.length
      });

      if (method === 'HEAD') {
        res.end();
      } else {
        res.end(fileBuffer);
      }
    } catch {
      sendHtml(res, 404, defaultNotFoundPage());
    }
  });
}

export async function startLandingServer(options = {}) {
  const port = Number(options.port || process.env.PORT || 8787);
  const host = options.host || process.env.HOST || '0.0.0.0';
  const server = createLandingServer(options);

  await new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(port, host, resolve);
  });

  console.info(`AJUSTE landing running on http://${host}:${port}`);
  return server;
}

if (process.argv[1] && path.resolve(process.argv[1]) === __filename) {
  startLandingServer().catch((error) => {
    console.error('Failed to start landing server:', error);
    process.exit(1);
  });
}
