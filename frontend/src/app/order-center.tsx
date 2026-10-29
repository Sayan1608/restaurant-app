"use client";

import { useCallback, useEffect, useState } from "react";
import type { OrderDetail, OrderPage, OrderStatus, OrderSummary } from "@/lib/api";

type OrderCenterProps = {
  isAdmin: boolean;
  onClose: () => void;
  loadMyOrders: (page: number, size: number) => Promise<OrderPage<OrderSummary>>;
  loadAdminOrders: (page: number, size: number, status?: OrderStatus) => Promise<OrderPage<OrderDetail>>;
  loadOrder: (orderId: string) => Promise<OrderDetail>;
  updateOrderStatus: (orderId: string, status: OrderStatus) => Promise<OrderDetail>;
};

const statuses: OrderStatus[] = ["PLACED", "CONFIRMED", "PREPARING", "READY", "COMPLETED", "CANCELLED"];
const transitions: Record<OrderStatus, OrderStatus[]> = {
  PLACED: ["CONFIRMED", "CANCELLED"],
  CONFIRMED: ["PREPARING", "CANCELLED"],
  PREPARING: ["READY"],
  READY: ["COMPLETED"],
  COMPLETED: [],
  CANCELLED: [],
};
const money = (amount: number) => new Intl.NumberFormat("en-IN", {
  style: "currency", currency: "INR", maximumFractionDigits: 2,
}).format(amount);
const dateTime = (value: string) => new Intl.DateTimeFormat("en-IN", {
  dateStyle: "medium", timeStyle: "short",
}).format(new Date(value));

export default function OrderCenter({ isAdmin, onClose, loadMyOrders, loadAdminOrders, loadOrder, updateOrderStatus }: OrderCenterProps) {
  const [page, setPage] = useState(0);
  const [statusFilter, setStatusFilter] = useState<OrderStatus | "">("");
  const [orders, setOrders] = useState<OrderSummary[] | OrderDetail[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [detail, setDetail] = useState<OrderDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [selectedStatuses, setSelectedStatuses] = useState<Record<string, OrderStatus>>({});
  const [savingId, setSavingId] = useState("");
  const [reload, setReload] = useState(0);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const result = isAdmin
        ? await loadAdminOrders(page, 10, statusFilter || undefined)
        : await loadMyOrders(page, 10);
      setOrders(result.content);
      setTotalPages(result.totalPages);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Orders could not be loaded.");
    } finally {
      setLoading(false);
    }
  }, [isAdmin, loadAdminOrders, loadMyOrders, page, statusFilter]);

  useEffect(() => {
    const timer = window.setTimeout(() => { void refresh(); }, 0);
    return () => window.clearTimeout(timer);
  }, [refresh, reload]);

  async function openDetails(orderId: string) {
    setDetailLoading(true);
    setError("");
    try { setDetail(await loadOrder(orderId)); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "Order details could not be loaded."); }
    finally { setDetailLoading(false); }
  }

  async function saveStatus(orderId: string, currentStatus: OrderStatus) {
    const nextStatus = selectedStatuses[orderId] ?? currentStatus;
    if (nextStatus === currentStatus) return;
    setSavingId(orderId);
    setError("");
    try {
      await updateOrderStatus(orderId, nextStatus);
      setReload((value) => value + 1);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Order status could not be updated.");
    } finally {
      setSavingId("");
    }
  }

  const title = isAdmin ? "Incoming orders" : "Your orders";
  return <div className="overlay" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
    <aside className="drawer order-drawer" aria-label={title}>
      <div className="drawer-head"><div><p className="eyebrow">GOODFOLK ORDER DESK</p><h2>{detail ? "Order details." : title}</h2></div>
        <button className="close-button" onClick={onClose} aria-label="Close orders">×</button>
      </div>
      {detail ? <section className="order-detail-view">
        <button className="back-link" onClick={() => setDetail(null)}>← Back to orders</button>
        <OrderDetails detail={detail} />
      </section> : <>
        {isAdmin && <label className="order-filter">Filter by status
          <select value={statusFilter} onChange={(event) => { setStatusFilter(event.target.value as OrderStatus | ""); setPage(0); }}>
            <option value="">All orders</option>
            {statuses.map((status) => <option key={status} value={status}>{status}</option>)}
          </select>
        </label>}
        {error && <p className="form-error order-error" role="alert">{error}</p>}
        {loading ? <div className="state-card">Loading orders…</div> : orders.length === 0 ? <div className="state-card">{isAdmin ? "No orders match this view." : "No orders yet. Your first good meal is waiting."}</div> :
          <div className="order-list">{orders.map((order) => <article className="order-card" key={order.id}>
            <div className="order-card-heading"><div><span className="eyebrow">ORDER</span><strong>#{order.id}</strong></div><span className={`status-pill status-${order.status.toLowerCase()}`}>{order.status}</span></div>
            <p className="order-created">{dateTime(order.createdAt)}</p>
            {isAdmin && "customerName" in order && <div className="admin-order-contact"><strong>{order.customerName}</strong><span>{order.contactPhone}</span><span>{order.deliveryAddress}</span></div>}
            <ul className="order-item-list">{order.items.map((item) => <li key={`${order.id}-${item.menuItemId ?? item.itemName}`}><span>{item.itemName} × {item.quantity}</span><strong>{money(item.lineTotal)}</strong></li>)}</ul>
            <div className="order-card-total"><span>Total</span><strong>{money(order.total)}</strong></div>
            <button className="order-detail-button" onClick={() => void openDetails(order.id)} disabled={detailLoading}>
              {detailLoading ? "Opening…" : `View details for ${order.id}`}
            </button>
            {isAdmin && <div className="order-status-editor">
              <label htmlFor={`order-status-${order.id}`}>Update status</label>
              <select id={`order-status-${order.id}`} aria-label={`Status for ${order.id}`}
                value={selectedStatuses[order.id] ?? order.status}
                onChange={(event) => setSelectedStatuses((current) => ({ ...current, [order.id]: event.target.value as OrderStatus }))}>
                <option value={order.status}>{order.status}</option>
                {transitions[order.status].map((status) => <option key={status} value={status}>{status}</option>)}
              </select>
              <button className="button button-dark" disabled={savingId === order.id || (selectedStatuses[order.id] ?? order.status) === order.status}
                onClick={() => void saveStatus(order.id, order.status)}>
                {savingId === order.id ? "Saving…" : `Save status for ${order.id}`}
              </button>
            </div>}
          </article>)}</div>}
        {totalPages > 1 && <nav className="order-pagination" aria-label="Order pages">
          <button onClick={() => setPage((value) => Math.max(0, value - 1))} disabled={page === 0}>Previous page</button>
          <span>Page {page + 1} of {totalPages}</span>
          <button onClick={() => setPage((value) => Math.min(totalPages - 1, value + 1))} disabled={page >= totalPages - 1}>Next page</button>
        </nav>}
      </>}
    </aside>
  </div>;
}

function OrderDetails({ detail }: { detail: OrderDetail }) {
  return <div className="order-detail-content">
    <span className={`status-pill status-${detail.status.toLowerCase()}`}>{detail.status}</span>
    <h3>Order #{detail.id}</h3>
    <p className="order-created">Placed {dateTime(detail.createdAt)}</p>
    <h4>Items</h4>
    <ul className="order-item-list">{detail.items.map((item) => <li key={`${item.menuItemId ?? item.itemName}-${item.quantity}`}><span>{item.itemName} × {item.quantity}</span><strong>{money(item.lineTotal)}</strong></li>)}</ul>
    <div className="order-card-total"><span>Order total</span><strong>{money(detail.total)}</strong></div>
    <h4>Delivery details</h4>
    <p>{detail.contactName}<br /><a href={`tel:${detail.contactPhone}`}>{detail.contactPhone}</a><br />{detail.deliveryAddress}</p>
    {detail.deliveryNotes && <p><strong>Note:</strong> {detail.deliveryNotes}</p>}
  </div>;
}
