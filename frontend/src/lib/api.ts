// Calls to the Java API. Same origin, session cookie, and the CSRF token echoed from the XSRF-TOKEN cookie.
// The frontend never computes an algorithm result: it only shows what these calls return.

export interface FieldProblem {
  field: string;
  problem: string;
}

export class ApiError extends Error {
  readonly status: number;
  readonly fields: FieldProblem[];

  constructor(status: number, message: string, fields: FieldProblem[] = []) {
    super(message);
    this.status = status;
    this.fields = fields;
  }
}

function csrfToken(): string | null {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : null;
}

let onUnauthorized: (() => void) | null = null;

/** Called when any request comes back 401, so the app can return to the sign-in page. */
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler;
}

type Method = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

export async function request<T>(method: Method, path: string, body?: unknown, headers: Record<string, string> = {}): Promise<T> {
  const isForm = body instanceof FormData;
  const h: Record<string, string> = { Accept: "application/json", ...headers };
  if (body !== undefined && !isForm) h["Content-Type"] = "application/json";
  if (method !== "GET") {
    const token = csrfToken();
    if (token) h["X-XSRF-TOKEN"] = token;
  }
  let response: Response;
  try {
    response = await fetch(path, {
      method,
      headers: h,
      credentials: "same-origin",
      body: body === undefined ? undefined : isForm ? body : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, "Can't reach the server. Check that it is running.");
  }
  if (response.status === 204) return undefined as T;
  const text = await response.text();
  let data: unknown = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = text;
    }
  }
  if (!response.ok) {
    if (response.status === 401 && onUnauthorized && !path.startsWith("/api/auth/login")) onUnauthorized();
    const err = (data ?? {}) as { message?: string; fields?: FieldProblem[] };
    throw new ApiError(response.status, err.message ?? `Request failed (${response.status}).`, err.fields ?? []);
  }
  return data as T;
}

export const api = {
  get: <T>(path: string) => request<T>("GET", path),
  post: <T>(path: string, body?: unknown, headers?: Record<string, string>) => request<T>("POST", path, body, headers),
  put: <T>(path: string, body?: unknown) => request<T>("PUT", path, body),
  patch: <T>(path: string, body?: unknown) => request<T>("PATCH", path, body),
  del: <T>(path: string) => request<T>("DELETE", path),
};

/** Builds a query string, leaving out empty values. */
export function qs(params: Record<string, string | number | boolean | null | undefined>): string {
  const parts = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null && v !== "")
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`);
  return parts.length ? `?${parts.join("&")}` : "";
}

/** Downloads a file from the API (CSV exports) using the session. */
export async function download(path: string, filename: string) {
  const response = await fetch(path, { credentials: "same-origin" });
  if (!response.ok) throw new ApiError(response.status, "Download failed.");
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}
