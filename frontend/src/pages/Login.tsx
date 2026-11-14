import { useState, type FormEvent } from "react";
import { Navigate } from "react-router";
import { useAuth } from "@/auth/AuthContext";
import { Button } from "@/components/ui/button";
import { Field, Input } from "@/components/ui/form";
import { ErrorBox } from "@/components/ui/feedback";

export default function Login() {
  const { me, login } = useAuth();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  if (me) return <Navigate to="/" replace />;

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(username.trim(), password);
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="grid min-h-full lg:grid-cols-[1fr_1.1fr]">
      <div className="hidden flex-col justify-between bg-navy-900 p-10 lg:flex">
        <div className="flex items-center gap-2.5">
          <img src="/favicon.svg" alt="" className="size-8" />
          <span className="text-lg font-semibold text-white">StayFlow</span>
        </div>
        <div className="max-w-md">
          <p className="text-2xl font-semibold leading-snug text-white">
            Sell the right number of rooms, put every guest in a room that fits, and check new bookings against the
            watchlist.
          </p>
          <ul className="mt-6 space-y-3 text-sm text-navy-300">
            <li>Availability for any stay from a segment tree, so a full night is never oversold.</li>
            <li>Room assignment by maximum flow, with the reason when a guest can't be placed.</li>
            <li>Watchlist screening with a Bloom filter, confirmed against the full list.</li>
          </ul>
        </div>
        <p className="text-xs text-navy-300/70">CIA 3 Advanced Algorithm project. Demo data is synthetic.</p>
      </div>

      <div className="flex items-center justify-center px-4 py-12">
        <form onSubmit={submit} className="w-full max-w-sm space-y-5" noValidate>
          <div>
            <div className="mb-6 flex items-center gap-2 lg:hidden">
              <img src="/favicon.svg" alt="" className="size-7" />
              <span className="font-semibold">StayFlow</span>
            </div>
            <h1 className="text-xl font-semibold tracking-tight">Sign in</h1>
            <p className="mt-1 text-sm text-muted">Use your staff account. Demo accounts are listed in the README.</p>
          </div>
          <Field label="Username">
            {(id) => (
              <Input id={id} autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value)} autoFocus />
            )}
          </Field>
          <Field label="Password">
            {(id) => (
              <Input
                id={id}
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            )}
          </Field>
          <ErrorBox error={error} />
          <Button type="submit" variant="primary" className="w-full" loading={busy} disabled={!username || !password}>
            Sign in
          </Button>
        </form>
      </div>
    </div>
  );
}
