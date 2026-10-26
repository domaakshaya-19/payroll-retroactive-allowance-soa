import test from 'node:test';
import assert from 'node:assert/strict';
import { apiUrl, readSession, request, validateBackdate, GATEWAY } from './api.js';

test('development login and protected calls are same-origin; production retains HTTPS Gateway', () => {
  for (const path of ['/api/auth/token', '/api/allowances/employees/EMP001', '/api/retro-adjustments/employees/EMP001']) {
    assert.equal(apiUrl(path, true), path);
    assert.equal(apiUrl(path, false), GATEWAY + path);
  }
  assert.throws(() => apiUrl('https://localhost:8083/api/retro-adjustments', true), /Gateway API/);
});

function token(claims) { return `header.${Buffer.from(JSON.stringify(claims)).toString('base64url')}.signature`; }
const staff = { sub: 'clerk', exp: Math.floor(Date.now() / 1000) + 900, roles: ['payrollClerk'], employees: ['EMP001'] };

test('session reads actual staff role and employee claims, rejects expired/non-staff sessions', () => {
  assert.deepEqual(readSession(token(staff)).employees, ['EMP001']);
  assert.deepEqual(readSession(token(staff)).roles, ['payrollClerk']);
  assert.throws(() => readSession(token({ ...staff, exp: 1 })), /unexpired staff session/);
  assert.throws(() => readSession(token({ ...staff, roles: ['payrollSnapshotReader'] })), /staff session/);
  assert.throws(() => readSession('invalid'), /staff session/);
});

test('backdate validation accepts zero and rejects invalid precision, dates and expected versions', () => {
  assert.equal(validateBackdate('3000.00', '2026-08-01', '1'), '');
  assert.equal(validateBackdate('0', '2026-08-01', '2'), '');
  for (const value of ['-1', '1.001', '1000000000000', '1e3', 'NaN']) assert.notEqual(validateBackdate(value, '2026-08-01', '1'), '');
  assert.match(validateBackdate('3000', '2026-08-02', '1'), /first day/);
  assert.match(validateBackdate('3000', '2026-13-01', '1'), /first day/);
  assert.match(validateBackdate('3000', '2026-08-01', '1.5'), /whole number/);
});

test('requests use HTTPS Gateway only and send auth/expectedVersion; no browser credential persistence', async t => {
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.equal(url, `${GATEWAY}/api/allowances/employees/EMP001/HOUSING/backdate`);
    assert.equal(options.headers.Authorization, 'Bearer test-token');
    assert.equal(options.credentials, 'omit');
    assert.equal(JSON.parse(options.body).expectedVersion, 1);
    return new Response('{"ruleVersion":2}', { status: 201 });
  });
  assert.deepEqual(await request('/api/allowances/employees/EMP001/HOUSING/backdate', { token: 'test-token', method: 'POST', body: { expectedVersion: 1, monthlyAmount: 3000, effectiveFrom: '2026-08-01' } }), { ruleVersion: 2 });
  await assert.rejects(request('https://localhost:8083/api/retro-adjustments'), /Gateway API/);
});

test('401 expires a protected session while 403 preserves it and explains access denial', async t => {
  let expired = 0;
  t.mock.method(globalThis, 'fetch', async () => new Response('', { status: 401 }));
  await assert.rejects(request('/api/allowances/employees/EMP001', { token: 'test-token', onUnauthorized: () => expired++ }), error => error.status === 401);
  assert.equal(expired, 1);
  globalThis.fetch.mock.mockImplementation(async () => new Response('', { status: 403 }));
  await assert.rejects(request('/api/allowances/employees/EMP999', { token: 'test-token', onUnauthorized: () => expired++ }), /Access denied/);
  assert.equal(expired, 1);
});

test('conflicts and network/certificate failures remain visible and are not retried', async t => {
  let calls = 0;
  t.mock.method(globalThis, 'fetch', async () => { calls++; return new Response('', { status: 409 }); });
  await assert.rejects(request('/api/allowances/employees/EMP001'), /Refresh before/);
  assert.equal(calls, 1);
  globalThis.fetch.mock.mockImplementation(async () => { throw new TypeError('Failed to fetch'); });
  await assert.rejects(request('/api/allowances/employees/EMP001'), /certificate trust/);
});
