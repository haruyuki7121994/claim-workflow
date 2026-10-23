import test, { afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { api, ApiError, transitionRequest } from './api.js';

const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });

test('transition sends the loaded version, reason and the same retry key', () => {
  const request = transitionRequest({ version: 7 }, 'APPROVED', '  Assessment complete  ', 'request-123');
  assert.equal(request.headers['Idempotency-Key'], 'request-123');
  assert.deepEqual(request.body, { expectedVersion: 7, toState: 'APPROVED', reason: 'Assessment complete' });
});

test('API attaches bearer authentication and JSON body', async () => {
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/v1/claims');
    assert.equal(options.headers.Authorization, 'Bearer token-123');
    assert.equal(options.body, '{"amount":"500.00"}');
    return new Response('{"claimId":"claim-1"}', { status: 201 });
  };
  assert.deepEqual(await api('/claims', { token: 'token-123', method: 'POST', body: { amount: '500.00' } }), { claimId: 'claim-1' });
});

test('API preserves concurrency status, business code and validation details', async () => {
  globalThis.fetch = async () => new Response(JSON.stringify({ code: 'CONCURRENT_MODIFICATION', message: 'Claim changed', details: { version: 8 } }), { status: 409 });
  await assert.rejects(api('/claims/claim-1/transitions'), error => error instanceof ApiError && error.status === 409 && error.code === 'CONCURRENT_MODIFICATION' && error.details.version === 8);
});

test('API reports a non-JSON proxy failure clearly', async () => {
  globalThis.fetch = async () => new Response('<html>Bad gateway</html>', { status: 502 });
  await assert.rejects(api('/claims'), error => error.status === 502 && error.message.includes('Spring Boot'));
});
