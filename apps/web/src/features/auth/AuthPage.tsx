import React from "react";
import { useState } from "react";
import { BrainCircuit } from "lucide-react";
import { api, ApiError } from "../../api/client";
import type { AuthSession } from "../../types";

type AuthPageProps = {
  onAuthenticated: (session: AuthSession) => void;
};

export function AuthPage({ onAuthenticated }: AuthPageProps) {
  const [mode, setMode] = useState<"register" | "login">("register");
  const [displayName, setDisplayName] = useState("Movindu");
  const [email, setEmail] = useState(`movindu+${Date.now()}@example.com`);
  const [password, setPassword] = useState("password123");
  const [error, setError] = useState("");

  async function submit() {
    setError("");

    try {
      const session = mode === "register"
        ? await api.register({ displayName, email, password })
        : await api.login({ email, password });

      onAuthenticated(session);
    } catch (exception) {
      if (exception instanceof ApiError) {
        setError(exception.message);
      } else {
        setError("Authentication failed.");
      }
    }
  }

  return (
    <main className="auth-shell">
      <section className="auth-brand">
        <div className="brand-mark">
          <BrainCircuit size={34} />
        </div>
        <h1>CollabMind</h1>
        <p>
          AI-native workspace for technical teams to discuss, investigate,
          summarize, and act through connected agents and tools.
        </p>

        <div className="auth-points">
          <span>Realtime rooms</span>
          <span>AI agents</span>
          <span>MCP tools</span>
          <span>GitHub integration</span>
        </div>
      </section>

      <section className="auth-card">
        <div className="mode-switch">
          <button
            className={mode === "register" ? "active" : ""}
            onClick={() => setMode("register")}
          >
            Register
          </button>
          <button
            className={mode === "login" ? "active" : ""}
            onClick={() => setMode("login")}
          >
            Login
          </button>
        </div>

        <div className="form-stack">
          {mode === "register" ? (
            <label>
              Display name
              <input value={displayName} onChange={(event) => setDisplayName(event.target.value)} />
            </label>
          ) : null}

          <label>
            Email
            <input value={email} onChange={(event) => setEmail(event.target.value)} />
          </label>

          <label>
            Password
            <input
              value={password}
              type="password"
              onChange={(event) => setPassword(event.target.value)}
            />
          </label>

          {error ? <div className="error-box">{error}</div> : null}

          <button className="primary-action" onClick={submit}>
            {mode === "register" ? "Create account" : "Enter workspace"}
          </button>
        </div>
      </section>
    </main>
  );
}

