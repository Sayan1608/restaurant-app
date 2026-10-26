import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import CheckoutForm from "./checkout-form";

const order = {
  id: "order-1",
  restaurantId: "restaurant-1",
  status: "PLACED" as const,
  items: [{ menuItemId: "item-1", itemName: "Paneer Bowl", unitPrice: 49.99, quantity: 2, lineTotal: 99.98 }],
  subtotal: 99.98,
  deliveryFee: 0,
  tax: 0,
  total: 99.98,
  createdAt: "2026-10-26T00:00:00Z",
};

describe("CheckoutForm", () => {
  beforeEach(() => {
    Object.defineProperty(globalThis.crypto, "randomUUID", {
      configurable: true,
      value: vi.fn().mockReturnValue("9dd3b960-20c2-4502-aea2-b779b57e2a3b"),
    });
  });

  afterEach(() => { cleanup(); vi.restoreAllMocks(); });

  it("submits only item identifiers and quantities with the delivery details", async () => {
    const user = userEvent.setup();
    const placeOrder = vi.fn().mockResolvedValue(order);
    const onComplete = vi.fn();
    render(<CheckoutForm
      restaurantId="restaurant-1"
      customerName="Asha Customer"
      lines={[{ menuItemId: "item-1", name: "Paneer Bowl", quantity: 2, unitPrice: 49.99 }]}
      placeOrder={placeOrder}
      onComplete={onComplete}
      onBack={vi.fn()}
    />);

    await user.type(screen.getByLabelText("Phone number"), "+91 98765 43210");
    await user.type(screen.getByLabelText("Delivery address"), "22 Test Street, Bengaluru");
    await user.click(screen.getByRole("button", { name: "Place order" }));

    await waitFor(() => expect(placeOrder).toHaveBeenCalledOnce());
    expect(placeOrder).toHaveBeenCalledWith("9dd3b960-20c2-4502-aea2-b779b57e2a3b", {
      restaurantId: "restaurant-1",
      items: [{ menuItemId: "item-1", quantity: 2 }],
      contactName: "Asha Customer",
      contactPhone: "+91 98765 43210",
      deliveryAddress: "22 Test Street, Bengaluru",
      deliveryNotes: "",
    });
    expect(onComplete).toHaveBeenCalledWith(order);
  });

  it("reuses an idempotency key when retrying an unchanged checkout", async () => {
    const user = userEvent.setup();
    const placeOrder = vi.fn().mockRejectedValueOnce(new Error("Temporary network issue"))
      .mockResolvedValueOnce(order);
    render(<CheckoutForm
      restaurantId="restaurant-1"
      customerName="Asha Customer"
      lines={[{ menuItemId: "item-1", name: "Paneer Bowl", quantity: 2, unitPrice: 49.99 }]}
      placeOrder={placeOrder}
      onComplete={vi.fn()}
      onBack={vi.fn()}
    />);

    await user.type(screen.getByLabelText("Phone number"), "+91 98765 43210");
    await user.type(screen.getByLabelText("Delivery address"), "22 Test Street, Bengaluru");
    await user.click(screen.getByRole("button", { name: "Place order" }));
    expect((await screen.findByRole("alert")).textContent).toContain("Temporary network issue");
    await user.click(screen.getByRole("button", { name: "Place order" }));

    await waitFor(() => expect(placeOrder).toHaveBeenCalledTimes(2));
    expect(placeOrder.mock.calls[0]?.[0]).toBe(placeOrder.mock.calls[1]?.[0]);
  });

  it("shows order submission errors and allows the customer to return to the cart", async () => {
    const user = userEvent.setup();
    const onBack = vi.fn();
    render(<CheckoutForm
      restaurantId="restaurant-1"
      customerName="Asha Customer"
      lines={[{ menuItemId: "item-1", name: "Paneer Bowl", quantity: 1, unitPrice: 49.99 }]}
      placeOrder={vi.fn().mockRejectedValue(new Error("Item is no longer available"))}
      onComplete={vi.fn()}
      onBack={onBack}
    />);

    await user.type(screen.getByLabelText("Phone number"), "+91 98765 43210");
    await user.type(screen.getByLabelText("Delivery address"), "22 Test Street, Bengaluru");
    await user.click(screen.getByRole("button", { name: "Place order" }));
    expect((await screen.findByRole("alert")).textContent).toContain("Item is no longer available");
    fireEvent.click(screen.getByRole("button", { name: /Back to bag/ }));
    expect(onBack).toHaveBeenCalledOnce();
  });
});
