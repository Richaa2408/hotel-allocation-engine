import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { api, ApiError, setUnauthorizedHandler } from "@/lib/api";
import type { Me, Role } from "@/lib/types";

interface AuthState {
  me: Me | null;
  checking: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  can: (...roles: Role[]) => boolean;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<Me | null>(null);
  const [checking, setChecking] = useState(true);
  const queryClient = useQueryClient();

  useEffect(() => {
    setUnauthorizedHandler(() => setMe(null));
    (async () => {
      try {
        await api.get("/api/auth/csrf"); // sets the XSRF-TOKEN cookie before any POST
        setMe(await api.get<Me>("/api/auth/me"));
      } catch (e) {
        if (!(e instanceof ApiError) || e.status !== 401) console.error(e);
        setMe(null);
      } finally {
        setChecking(false);
      }
    })();
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const user = await api.post<Me>("/api/auth/login", { username, password });
    await api.get("/api/auth/csrf"); // the session changed, so refresh the token cookie
    queryClient.clear();
    setMe(user);
  }, [queryClient]);

  const logout = useCallback(async () => {
    try {
      await api.post("/api/auth/logout");
    } finally {
      queryClient.clear();
      setMe(null);
      await api.get("/api/auth/csrf").catch(() => undefined);
    }
  }, [queryClient]);

  const can = useCallback((...roles: Role[]) => !!me && roles.includes(me.role), [me]);

  return <AuthContext.Provider value={{ me, checking, login, logout, can }}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth outside AuthProvider");
  return ctx;
}

export const roleLabel: Record<Role, string> = {
  ADMIN: "Administrator",
  MANAGER: "Manager",
  FRONT_DESK: "Front desk",
};
