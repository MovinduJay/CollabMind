import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "../../api/client";
import { AuthPage } from "./AuthPage";

vi.mock("../../api/client", async () => {
  const actual =
    await vi.importActual<typeof import("../../api/client")>(
      "../../api/client",
    );
  return { ...actual, api: { register: vi.fn(), login: vi.fn() } };
});

describe("AuthPage", () => {
  beforeEach(() => vi.clearAllMocks());

  it("shows the complete registration experience", () => {
    render(<AuthPage onAuthenticated={vi.fn()} />);
    expect(screen.getByRole("heading", { name: "CollabMind" })).toBeVisible();
    expect(screen.getByLabelText("Display name")).toBeVisible();
    expect(screen.getByLabelText("Email")).toBeVisible();
    expect(screen.getByLabelText("Password")).toBeVisible();
    expect(
      screen.getByRole("button", { name: "Create account" }),
    ).toBeEnabled();
  });

  it("switches to login and returns the authenticated session", async () => {
    const user = userEvent.setup();
    const onAuthenticated = vi.fn();
    const session = {
      accessToken: "token",
      userId: "user-1",
      displayName: "Maya",
      email: "maya@example.com",
    };
    vi.mocked(api.login).mockResolvedValue(session);
    render(<AuthPage onAuthenticated={onAuthenticated} />);

    await user.click(screen.getByRole("button", { name: "Login" }));
    await user.clear(screen.getByLabelText("Email"));
    await user.type(screen.getByLabelText("Email"), "maya@example.com");
    await user.clear(screen.getByLabelText("Password"));
    await user.type(screen.getByLabelText("Password"), "secret123");
    await user.click(screen.getByRole("button", { name: "Enter workspace" }));

    expect(api.login).toHaveBeenCalledWith({
      email: "maya@example.com",
      password: "secret123",
    });
    expect(onAuthenticated).toHaveBeenCalledWith(session);
  });
});
