export const GATEWAY = 'https://localhost:8080';

export function apiUrl(path, development = import.meta.env?.DEV === true) {
  if (!path.startsWith('/api/')) throw new Error('Only Gateway API paths are supported.');
  return (development ? '' : GATEWAY) + path;
}

export class ApiError extends Error {
  constructor(message, status = 0) { super(message); this.status = status; }
}

// Claims are UI hints only. Gateway and services verify the signature and permissions.
export function readSession(token) {
  try {
    const segment = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const claims = JSON.parse(new TextDecoder().decode(Uint8Array.from(atob(segment), c => c.charCodeAt(0))));
    if (!Number.isFinite(claims.exp) || claims.exp * 1000 <= Date.now()) throw new Error();
    if (!Array.isArray(claims.roles) || !claims.roles.some(role => ['payrollClerk', 'payrollApprover'].includes(role))) throw new Error();
    if (!Array.isArray(claims.employees) || !claims.employees.every(id => typeof id === 'string' && /^[A-Za-z0-9_-]{1,64}$/.test(id))) throw new Error();
    return { token, expiresAt: claims.exp * 1000, roles: claims.roles, employees: [...new Set(claims.employees)], username: claims.sub };
  } catch { throw new ApiError('The login response did not contain a valid, unexpired staff session. Please sign in again.', 401); }
}

export async function request(path, { token, method = 'GET', body, signal, onUnauthorized } = {}) {
  const url = apiUrl(path);
  let response;
  try {
    response = await fetch(url, {
      method, signal: signal ? AbortSignal.any([signal, AbortSignal.timeout(20000)]) : AbortSignal.timeout(20000),
      credentials: 'omit', cache: 'no-store',
      headers: { Accept: 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(body ? { 'Content-Type': 'application/json' } : {}) },
      ...(body ? { body: JSON.stringify(body) } : {}),
    });
  } catch (error) {
    if (signal?.aborted) throw error;
    throw new ApiError(error.name === 'TimeoutError'
      ? 'Gateway request timed out. Check service readiness, then refresh. A submitted change may already have committed; check versions before resubmitting.'
      : 'Cannot reach the HTTPS Gateway. In development, check the Vite proxy and NODE_EXTRA_CA_CERTS certificate trust; in production, check Gateway connectivity and CORS.');
  }
  const text = await response.text();
  let data; try { data = text ? JSON.parse(text) : null; } catch { data = null; }
  if (!response.ok) {
    if (response.status === 401) onUnauthorized?.();
    const messages = {
      401: token ? 'Your session expired or was rejected. Sign in again.' : 'Sign-in failed. Check your username and password.',
      403: 'Access denied. Your role or employee permissions do not allow this action.',
      404: 'The requested record is unavailable. For login, check that demo authentication is enabled.',
      409: 'This record changed or the operation is already complete. Refresh before trying again.',
      503: 'A backend service is not ready. Wait for discovery registration, then refresh.',
    };
    // Spring problem details are sanitized by the backend. Never render raw HTML responses.
    const detail = response.status === 400 && typeof data?.detail === 'string' ? ` ${data.detail}` : '';
    throw new ApiError((messages[response.status] || `Request failed (HTTP ${response.status}).`) + detail, response.status);
  }
  if (text && data === null) throw new ApiError('The Gateway returned an unexpected response. Please refresh.');
  return data;
}

export function validateBackdate(amount, date, version) {
  if (!/^\d{1,12}(\.\d{1,2})?$/.test(amount)) return 'Enter a nonnegative amount with up to 12 integer digits and 2 decimal places.';
  if (!/^\d{4}-\d{2}-01$/.test(date) || Number.isNaN(Date.parse(date))) return 'Choose the first day of a month.';
  if (!Number.isSafeInteger(Number(version)) || Number(version) < 1) return 'Expected version must be a positive whole number.';
  return '';
}
