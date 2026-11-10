import { createContext, useCallback, useContext, useMemo, useRef, useState, type ReactNode } from 'react';
import { jsonBody, request as apiRequest, routes, type Tokens } from './api';

// Deliberately no persistent storage: credentials do not survive reloads and are never written to web storage.
export type AuthContextValue = {
  accessToken: string | null;
  refreshToken: string | null;
  request: <T>(path: string, init?: RequestInit) => Promise<T>;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
};
const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [tokens, setTokenState] = useState<Tokens | null>(null);
  const tokenRef = useRef<Tokens | null>(null);
  const setTokens = useCallback((next: Tokens | null) => { tokenRef.current = next; setTokenState(next); }, []);

  const request = useCallback(async <T,>(path: string, init?: RequestInit) => {
    const current = tokenRef.current;
    try { return await apiRequest<T>(path, current?.accessToken ?? null, current?.refreshToken ?? null, setTokens, init); }
    catch (error) {
      const status = (error as Error & { status?: number }).status;
      if (status === 401 && current) setTokens(null);
      throw error;
    }
  }, [setTokens]);

  const login = useCallback(async (email: string, password: string) => {
    const next = await apiRequest<Tokens>(routes.login, null, null, setTokens, { method: 'POST', body: jsonBody({ email, password }) });
    setTokens(next);
  }, [setTokens]);

  const register = useCallback(async (email: string, password: string) => {
    const next = await apiRequest<Tokens>(routes.register, null, null, setTokens, { method: 'POST', body: jsonBody({ email, password }) });
    setTokens(next);
  }, [setTokens]);

  const logout = useCallback(async () => {
    const current = tokenRef.current;
    try {
      if (current) await apiRequest<void>(routes.logout, current.accessToken, current.refreshToken, setTokens, { method: 'POST', body: jsonBody({ refreshToken: current.refreshToken }) });
    } finally { setTokens(null); }
  }, [setTokens]);

  const value = useMemo(() => ({ accessToken: tokens?.accessToken ?? null, refreshToken: tokens?.refreshToken ?? null, request, login, register, logout }), [tokens, request, login, register, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within AuthProvider');
  return context;
}
