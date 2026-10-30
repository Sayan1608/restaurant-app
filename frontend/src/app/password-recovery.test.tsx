import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import Storefront from "./storefront";

const { requestPasswordReset, resetPassword, listRestaurants, me } = vi.hoisted(() => ({
  requestPasswordReset: vi.fn(),
  resetPassword: vi.fn(),
  listRestaurants: vi.fn(),
  me: vi.fn(),
}));

vi.mock("@/lib/api", () => ({
  api: {
    listRestaurants,
    me,
    requestPasswordReset,
    resetPassword,
  },
}));

describe("password recovery", () => {
  afterEach(() => { cleanup(); vi.resetAllMocks(); });

  it("requests an email code and submits the code with a new password", async () => {
    const user = userEvent.setup();
    listRestaurants.mockResolvedValue([]);
    me.mockResolvedValue(null);
    requestPasswordReset.mockResolvedValue({ message: "If an account exists for that email, a verification code has been sent." });
    resetPassword.mockResolvedValue({ message: "Your password has been reset. You can now sign in." });
    render(<Storefront />);

    await user.click(await screen.findByRole("button", { name: "Sign in" }));
    await user.click(screen.getByRole("button", { name: "Forgot password?" }));
    await user.type(screen.getByLabelText("Email address"), "customer@example.test");
    await user.click(screen.getByRole("button", { name: "Send verification code" }));

    expect(await screen.findByText(/Enter the six digit code sent to customer@example.test/)).toBeTruthy();
    expect(requestPasswordReset).toHaveBeenCalledWith("customer@example.test");
    await user.type(screen.getByLabelText("Verification code"), "123456");
    await user.type(screen.getByLabelText("New password"), "a much safer password");
    await user.click(screen.getByRole("button", { name: "Reset password" }));

    await waitFor(() => expect(resetPassword).toHaveBeenCalledWith(
      "customer@example.test", "123456", "a much safer password",
    ));
    expect(await screen.findByText("Your password has been reset. You can now sign in.")).toBeTruthy();
  });
});
