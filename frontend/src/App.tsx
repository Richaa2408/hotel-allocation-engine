import { lazy, Suspense, type ReactNode } from "react";
import { createBrowserRouter, Navigate, RouterProvider } from "react-router";
import { useAuth } from "@/auth/AuthContext";
import { AppShell } from "@/components/layout/AppShell";
import { LoadingRows } from "@/components/ui/feedback";
import Login from "@/pages/Login";

const Overview = lazy(() => import("@/pages/overview"));
const Reservations = lazy(() => import("@/pages/reservations"));
const Inventory = lazy(() => import("@/pages/inventory"));
const Assignment = lazy(() => import("@/pages/assignment"));
const Risk = lazy(() => import("@/pages/risk"));
const Lab = lazy(() => import("@/pages/lab"));
const Analytics = lazy(() => import("@/pages/analytics"));
const Settings = lazy(() => import("@/pages/settings"));
const Help = lazy(() => import("@/pages/help"));

function RequireAuth({ children }: { children: ReactNode }) {
  const { me, checking } = useAuth();
  if (checking) return <LoadingRows rows={4} />;
  if (!me) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

function RequireAdmin({ children }: { children: ReactNode }) {
  const { can } = useAuth();
  return can("ADMIN") ? <>{children}</> : <Navigate to="/" replace />;
}

const page = (node: ReactNode) => <Suspense fallback={<LoadingRows />}>{node}</Suspense>;

const router = createBrowserRouter([
  { path: "/login", element: <Login /> },
  {
    path: "/",
    element: (
      <RequireAuth>
        <AppShell />
      </RequireAuth>
    ),
    children: [
      { index: true, element: page(<Overview />) },
      { path: "reservations", element: page(<Reservations />) },
      { path: "inventory", element: page(<Inventory />) },
      { path: "assignment", element: page(<Assignment />) },
      { path: "risk", element: page(<Risk />) },
      { path: "lab", element: page(<Lab />) },
      { path: "analytics", element: page(<Analytics />) },
      { path: "settings", element: page(<RequireAdmin><Settings /></RequireAdmin>) },
      { path: "help", element: page(<Help />) },
      { path: "*", element: <Navigate to="/" replace /> },
    ],
  },
]);

export default function App() {
  return <RouterProvider router={router} />;
}
