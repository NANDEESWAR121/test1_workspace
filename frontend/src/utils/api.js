import { encryptPayload, decryptPayload } from './crypto';

/**
 * Installs a global fetch interceptor for all /api calls.
 *
 * - Outgoing requests: JSON body is AES-encrypted into { payload: "..." }
 *   (already-encrypted bodies with a `payload` key are skipped to prevent double-encryption)
 * - Incoming responses: { payload: "..." } bodies are AES-decrypted transparently
 *
 * This makes every API call in the browser DevTools Network tab show only
 * encrypted payload data — no plain text product, tenant, or user data.
 *
 * The interceptor is transparent to all components: no code changes needed
 * in Dashboard.js, Login.js, etc. — they all call fetch() as normal.
 */

/** Safely resolve the URL string from a fetch `input` argument. */
function resolveUrl(input) {
  if (typeof input === 'string') return input;
  if (input && typeof input.url === 'string') return input.url;
  if (input && typeof input.href === 'string') return input.href;
  if (input) return String(input);
  return '';
}

export const setupFetchInterceptor = () => {
  const originalFetch = globalThis.fetch.bind(globalThis);

  globalThis.fetch = async (input, init = {}) => {
    const url = resolveUrl(input);

    // Only intercept /api calls; pass everything else through unchanged
    if (!url.includes('/api')) {
      return originalFetch(input, init);
    }

    // ── Encrypt outgoing request body ──────────────────────────────────
    let processedInit = { ...init };
    const method = (processedInit.method || 'GET').toUpperCase();

    if (
      processedInit.body &&
      typeof processedInit.body === 'string' &&
      method !== 'GET' &&
      method !== 'DELETE'
    ) {
      try {
        const parsed = JSON.parse(processedInit.body);
        // Skip if already a { payload: "..." } envelope (e.g. from Login.js manual encryption)
        if (parsed && typeof parsed === 'object' && !parsed.payload) {
          const encrypted = await encryptPayload(parsed);
          processedInit = { ...processedInit, body: JSON.stringify(encrypted) };
        }
      } catch {
        // Not valid JSON (e.g. plain text) — send as-is
      }
    }

    // ── Send the actual request ────────────────────────────────────────
    const response = await originalFetch(input, processedInit);

    // Read the body ONCE and cache it so both json() and text() can be called
    const bodyText = await response.text().catch(() => '');

    /**
     * json(): parses response, decrypts { payload } if present, returns plain object/array.
     */
    const decryptedJson = async () => {
      if (!bodyText) return null;
      try {
        const parsed = JSON.parse(bodyText);
        if (parsed?.payload) {
          return await decryptPayload(parsed);
        }
        return parsed;
      } catch {
        return bodyText;
      }
    };

    /**
     * text(): decrypts if response is a { payload } envelope, returns the inner
     * string (or message/error field for error objects) so error handlers still work.
     */
    const decryptedText = async () => {
      if (!bodyText) return '';
      try {
        const parsed = JSON.parse(bodyText);
        if (parsed?.payload) {
          const dec = await decryptPayload(parsed);
          if (typeof dec === 'string') return dec;
          if (dec?.message) return dec.message;
          if (dec?.error) return dec.error;
          return JSON.stringify(dec);
        }
      } catch {
        // Not JSON — return raw text
      }
      return bodyText;
    };

    return {
      ok: response.ok,
      status: response.status,
      statusText: response.statusText,
      headers: response.headers,
      redirected: response.redirected,
      type: response.type,
      url: response.url,
      json: decryptedJson,
      text: decryptedText,
      blob: () => Promise.resolve(new Blob([bodyText])),
    };
  };
};
