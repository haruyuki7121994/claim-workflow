export class ApiError extends Error {
  constructor(status, body) {
    super(body.message || `Request failed (${status})`);
    this.status = status;
    this.code = body.code;
    this.details = body.details || {};
  }
}

export async function api(path, { token, body, method = 'GET', headers, signal } = {}) {
  const response = await fetch(`/api/v1${path}`, {
    method, signal, headers: {
      Accept: 'application/json',
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}), ...headers,
    },
    ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
  });
  const text = await response.text();
  let data;
  try { data = text ? JSON.parse(text) : {}; }
  catch { throw new ApiError(response.status, { message: 'The API returned an unreadable response. Check that Spring Boot is running.' }); }
  if (!response.ok) throw new ApiError(response.status, data);
  return data;
}

export function transitionRequest(claim, toState, reason, key) {
  return {
    method: 'POST',
    headers: { 'Idempotency-Key': key },
    body: { toState, expectedVersion: claim.version, reason: reason.trim() },
  };
}

export function label(value = '') {
  return value.replace(/([a-z])([A-Z])/g, '$1 $2').replaceAll('_', ' ').toLowerCase()
    .replace(/^./, character => character.toUpperCase());
}
