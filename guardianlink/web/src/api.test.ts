import { afterEach, describe, expect, it, vi } from 'vitest';
import { apiUrl, request } from './api';

afterEach(() => { vi.restoreAllMocks(); });

describe('API client', () => {
  it('keeps browser API paths relative for same-origin hosting', () => {
    expect(apiUrl('/v1/me')).toBe('/v1/me');
    expect(apiUrl('health/live')).toBe('/health/live');
  });

  it('rotates a refresh token after 401 and retries once with the new bearer token', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify({ code: 'SESSION_EXPIRED', message: 'expired' }), { status: 401, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ access_token: 'next-access', refresh_token: 'next-refresh', expires_in: 900, token_type: 'Bearer' }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'parent' }), { status: 200, headers: { 'Content-Type': 'application/json' } }));
    const setTokens = vi.fn();
    const result = await request<{ id: string }>('/v1/me', 'old-access', 'old-refresh', setTokens);
    expect(result.id).toBe('parent');
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock.mock.calls[1][1]?.body).toBe(JSON.stringify({ refresh_token: 'old-refresh' }));
    expect(new Headers(fetchMock.mock.calls[2][1]?.headers).get('Authorization')).toBe('Bearer next-access');
    expect(setTokens).toHaveBeenCalledWith({ accessToken: 'next-access', refreshToken: 'next-refresh', expiresIn: 900, tokenType: 'Bearer' });
  });

  it('does not refresh on a 403 authorization failure', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(new Response(JSON.stringify({ code: 'FORBIDDEN' }), { status: 403, headers: { 'Content-Type': 'application/json' } }));
    await expect(request('/v1/families/me', 'access', 'refresh', vi.fn())).rejects.toMatchObject({ status: 403, code: 'FORBIDDEN' });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
