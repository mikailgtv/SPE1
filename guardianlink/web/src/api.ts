export type ApiError = Error & { status?: number; code?: string };
export type Tokens = { accessToken: string; refreshToken: string; expiresIn: number; tokenType?: string };

const API_BASE = (import.meta.env.VITE_API_BASE_URL as string | undefined)?.replace(/\/$/, '') ?? '';
if (API_BASE && (!API_BASE.startsWith('/') || API_BASE.startsWith('//'))) {
  throw new Error('VITE_API_BASE_URL must be a same-origin path; absolute API origins are rejected.');
}

export function apiUrl(path: string): string {
  return `${API_BASE}${path.startsWith('/') ? path : `/${path}`}`;
}

async function readError(response: Response): Promise<ApiError> {
  let payload: { code?: string; message?: string } = {};
  try { payload = await response.json(); } catch { /* non-JSON error body */ }
  const error = new Error(payload.message || `HTTP ${response.status}`) as ApiError;
  error.status = response.status;
  error.code = payload.code;
  return error;
}

async function jsonRequest<T>(path: string, token: string | null, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set('Accept', 'application/json');
  if (token) headers.set('Authorization', `Bearer ${token}`);
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
  const response = await fetch(apiUrl(path), { ...init, headers, credentials: 'omit', cache: 'no-store' });
  if (!response.ok) throw await readError(response);
  if (response.status === 204) return undefined as T;
  const type = response.headers.get('content-type') || '';
  if (!type.includes('application/json')) throw new Error('پاسخ API از نوع JSON نیست.');
  return response.json().then(normalizeKeys) as Promise<T>;
}

function normalizeKeys(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(normalizeKeys);
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value as Record<string, unknown>).map(([key, item]) => [key.replace(/_([a-z0-9])/g, (_, letter: string) => letter.toUpperCase()), normalizeKeys(item)]));
  }
  return value;
}

function snakeKeys(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(snakeKeys);
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value as Record<string, unknown>).map(([key, item]) => [key.replace(/[A-Z]/g, letter => `_${letter.toLowerCase()}`), snakeKeys(item)]));
  }
  return value;
}

let refreshInFlight: Promise<Tokens> | null = null;

export async function request<T>(
  path: string,
  accessToken: string | null,
  refreshToken: string | null,
  onTokens: (tokens: Tokens) => void,
  init: RequestInit = {},
): Promise<T> {
  try {
    return await jsonRequest<T>(path, accessToken, init);
  } catch (error) {
    const status = (error as ApiError).status;
    if (status !== 401 || !accessToken || !refreshToken || path === '/v1/auth/refresh' || path === '/v1/auth/login') throw error;
    if (!refreshInFlight) {
      refreshInFlight = jsonRequest<Tokens>('/v1/auth/refresh', null, {
        method: 'POST', body: jsonBody({ refreshToken }),
      }).finally(() => { refreshInFlight = null; });
    }
    const rotated = await refreshInFlight;
    onTokens(rotated);
    return jsonRequest<T>(path, rotated.accessToken, init);
  }
}

export function jsonBody(value: unknown): string { return JSON.stringify(snakeKeys(value)); }

export const routes = {
  login: '/v1/auth/login', register: '/v1/auth/register', logout: '/v1/auth/logout', me: '/v1/me',
  family: '/v1/families/me', devices: '/v1/families/me/devices', notifications: '/v1/families/me/notifications',
  unread: '/v1/families/me/notifications/unread-count', sosActive: '/v1/families/me/sos/active',
};

export type FamilyDetail = { family: { id: string; name: string; createdAt: string }; members: { id: string; userId: string; role: string }[] };
export type Device = {
  id: string; maskedId: string; deviceName: string; manufacturer: string; model: string;
  androidVersion: string; appVersion: string; batteryLevel: number | null; batteryBand: string;
  lastSeen: string | null; lastSync: string | null; status: string; presence: string; health: string;
  connectionState: string; permissions: Record<string, string>; createdAt: string;
};
export type User = { id: string; email: string; createdAt: string };
export type UsageDay = { date: string; totalMinutes: number };
export type AppUsage = { packageName: string; appName: string; category: string; usageMinutes: number; launchCount: number; percent: number };
export type UsageReport = {
  today: { date: string; totalMinutes: number; limitMinutes: number | null; limitEnabled: boolean; remainingMinutes: number | null; state: string; topApps: AppUsage[] };
  weekly: { days: UsageDay[]; totalMinutes: number; averageMinutes: number; mostUsedDay: string | null; leastUsedDay: string | null };
  settings: { dailyLimitMinutes: number; nearPct: number; enabled: boolean };
  schedules: { id: string; name: string; startTime: string; endTime: string; daysOfWeek: number; enabled: boolean }[];
};
export type LocationCurrent = {
  fix: { latitude: number; longitude: number; accuracyM: number | null; timestamp: string; source: string; isCurrent: boolean } | null;
  sharingEnabled: boolean; permissionStatus: string; providerStatus: string; health: string; staleAfterSec: number; retentionDays: number;
};
export type Zone = { id: string; name: string; latitude: number; longitude: number; radiusM: number; enabled: boolean; state: string; lastTransitionAt: string | null; lastEntryAt: string | null; lastExitAt: string | null; notifyOnEntry: boolean; notifyOnExit: boolean; notifyEnabled: boolean; lastKnownAccuracy: number | null };
export type ZonesResponse = { zones: Zone[]; active: number; maxZones: number; cooldownSec: number; minAccuracyM: number; minRadiusM: number; maxRadiusM: number };
export type AppRulesResponse = { ruleVersion: number; managementMode: string; usageAccessGranted: boolean; rules: { id: string; packageName: string; appName: string; ruleType: string; dailyLimitSeconds: number | null; enabled: boolean; version: number; lastEnforcementStatus: string; lastEnforcementAt: string | null }[] };
export type CatalogResponse = { apps: { packageName: string; appName: string; category: string; installed: boolean; usageTodaySeconds: number | null; rule: AppRulesResponse['rules'][number] | null }[]; managementMode: string; enforcementCapability: string; usageAccessGranted: boolean; ruleVersion: number; inventoryUpdatedAt: string | null; policyLastSyncAt: string | null };
export type WebSettings = { enabled: boolean; defaultPolicy: string; requestedMode: string; alertsEnabled: boolean; ruleVersion: number; reportedMode: string; reportedStatus: string; reportedReason: string; dnsStatus: string; vpnStatus: string; timezoneId: string; lastReportedAt: string | null; rules: { id: string; ruleType: string; domain: string | null; category: string | null; action: string; enabled: boolean; version: number; categoryEnforceable: boolean }[]; activeOverrides: { id: string; domain: string; action: string; expiresAt: string }[]; categoryProviderAvailable: boolean };
export type Notification = { id: string; deviceId: string; eventType: string; category: string; severity: string; titleKey: string; bodyKey: string; metadata: string; occurredAt: string; status: string; isRead: boolean; deviceName: string };
export type NotificationPage = { items: Notification[]; nextBefore: string | null; unreadCount: number };
export type Sos = { id: string; deviceId: string; deviceName: string; status: string; activatedAt: string; trackingEnabled: boolean; trackingStatus: string; trackingReason: string | null; latestLocation: { latitude: number; longitude: number; accuracyM: number | null; timestamp: string; provider: string; source: string } | null; permissionStatus: string; providerStatus: string; batteryLevel: number | null; networkState: string; resolveNote: string | null };
