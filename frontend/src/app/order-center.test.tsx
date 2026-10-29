import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import OrderCenter from "./order-center";
import type { OrderDetail, OrderPage, OrderSummary } from "@/lib/api";

const summary: OrderSummary = {
  id: "order-1",
  restaurantId: "restaurant-1",
  status: "PLACED",
  items: [{ menuItemId: "item-1", itemName: "Paneer Bowl", unitPrice: 49.99, quantity: 2, lineTotal: 99.98 }],
  subtotal: 99.98,
  deliveryFee: 0,
  tax: 0,
  total: 99.98,
  createdAt: "2026-10-26T10:30:00Z",
};

const detail: OrderDetail = {
  ...summary,
  customerId: "customer-1",
  customerName: "Asha Customer",
  contactName: "Asha Customer",
  contactPhone: "+91 98765 43210",
  deliveryAddress: "22 Test Street, Bengaluru",
  deliveryNotes: "Ring the bell",
  updatedAt: "2026-10-26T10:30:00Z",
};

const page = <T,>(content: T[], pageNumber = 0, totalPages = 1): OrderPage<T> => ({
  content,
  page: pageNumber,
  size: 10,
  totalElements: content.length,
  totalPages,
});

afterEach(cleanup);

describe("OrderCenter", () => {
  it("shows customer order history and loads private details on request", async () => {
    const user = userEvent.setup();
    const loadOrder = vi.fn().mockResolvedValue(detail);
    render(<OrderCenter
      isAdmin={false}
      onClose={vi.fn()}
      loadMyOrders={vi.fn().mockResolvedValue(page([summary]))}
      loadAdminOrders={vi.fn().mockResolvedValue(page([]))}
      loadOrder={loadOrder}
      updateOrderStatus={vi.fn()}
    />);

    expect(await screen.findByText(/Paneer Bowl/)).toBeTruthy();
    expect(screen.getByText("PLACED")).toBeTruthy();
    await user.click(screen.getByRole("button", { name: "View details for order-1" }));
    expect(await screen.findByText(/22 Test Street, Bengaluru/)).toBeTruthy();
    expect(screen.getByText("+91 98765 43210")).toBeTruthy();
    expect(screen.getByText("Ring the bell")).toBeTruthy();
    expect(loadOrder).toHaveBeenCalledWith("order-1");
  });

  it("lets admins filter and page through incoming orders, then update a valid status", async () => {
    const user = userEvent.setup();
    const confirmed = { ...detail, status: "CONFIRMED" as const };
    const loadAdminOrders = vi.fn()
      .mockResolvedValueOnce(page([{ ...detail, status: "PLACED" as const }], 0, 2))
      .mockResolvedValueOnce(page([{ ...detail, status: "PLACED" as const }], 0, 2))
      .mockResolvedValueOnce(page([confirmed], 0, 2))
      .mockResolvedValueOnce(page([confirmed], 1, 2));
    const updateOrderStatus = vi.fn().mockResolvedValue(confirmed);
    render(<OrderCenter
      isAdmin
      onClose={vi.fn()}
      loadMyOrders={vi.fn().mockResolvedValue(page([]))}
      loadAdminOrders={loadAdminOrders}
      loadOrder={vi.fn().mockResolvedValue(detail)}
      updateOrderStatus={updateOrderStatus}
    />);

    expect(await screen.findByText("Asha Customer")).toBeTruthy();
    await user.selectOptions(screen.getByLabelText("Filter by status"), "PLACED");
    await waitFor(() => expect(loadAdminOrders).toHaveBeenLastCalledWith(0, 10, "PLACED"));
    await user.selectOptions(screen.getByLabelText("Status for order-1"), "CONFIRMED");
    await user.click(screen.getByRole("button", { name: "Save status for order-1" }));
    await waitFor(() => expect(updateOrderStatus).toHaveBeenCalledWith("order-1", "CONFIRMED"));
    await user.click(screen.getByRole("button", { name: "Next page" }));
    expect(await screen.findByText("Page 2 of 2")).toBeTruthy();
  });

  it("shows empty and failure states for customer history", async () => {
    const { rerender } = render(<OrderCenter
      isAdmin={false}
      onClose={vi.fn()}
      loadMyOrders={vi.fn().mockResolvedValue(page([]))}
      loadAdminOrders={vi.fn().mockResolvedValue(page([]))}
      loadOrder={vi.fn()}
      updateOrderStatus={vi.fn()}
    />);
    expect(await screen.findByText("No orders yet. Your first good meal is waiting." )).toBeTruthy();

    rerender(<OrderCenter
      isAdmin={false}
      onClose={vi.fn()}
      loadMyOrders={vi.fn().mockRejectedValue(new Error("Orders are unavailable"))}
      loadAdminOrders={vi.fn().mockResolvedValue(page([]))}
      loadOrder={vi.fn()}
      updateOrderStatus={vi.fn()}
    />);
    expect((await screen.findByRole("alert")).textContent).toContain("Orders are unavailable");
  });
});
