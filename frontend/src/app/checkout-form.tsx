"use client";

import { useRef, useState, type FormEvent } from "react";
import { type CheckoutRequest, type OrderResponse } from "@/lib/api";

type CheckoutDisplayLine = {
  menuItemId: string;
  name: string;
  quantity: number;
  unitPrice: number;
};

type CheckoutFormProps = {
  restaurantId: string;
  customerName: string;
  lines: CheckoutDisplayLine[];
  placeOrder: (idempotencyKey: string, payload: CheckoutRequest) => Promise<OrderResponse>;
  onComplete: (order: OrderResponse) => void;
  onBack: () => void;
};

const money = (amount: number) => new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  maximumFractionDigits: 2,
}).format(amount);

export default function CheckoutForm({ restaurantId, customerName, lines, placeOrder, onComplete, onBack }: CheckoutFormProps) {
  const [contactName, setContactName] = useState(customerName);
  const [contactPhone, setContactPhone] = useState("");
  const [deliveryAddress, setDeliveryAddress] = useState("");
  const [deliveryNotes, setDeliveryNotes] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const retryKey = useRef<{ fingerprint: string; key: string } | null>(null);
  const subtotalEstimate = lines.reduce((sum, line) => sum + line.unitPrice * line.quantity, 0);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting || lines.length === 0) return;
    setSubmitting(true);
    setError("");

    const payload: CheckoutRequest = {
      restaurantId,
      items: lines.map(({ menuItemId, quantity }) => ({ menuItemId, quantity })),
      contactName: contactName.trim(),
      contactPhone: contactPhone.trim(),
      deliveryAddress: deliveryAddress.trim(),
      deliveryNotes: deliveryNotes.trim(),
    };
    const fingerprint = JSON.stringify(payload);
    const idempotencyKey = retryKey.current?.fingerprint === fingerprint
      ? retryKey.current.key
      : crypto.randomUUID();
    retryKey.current = { fingerprint, key: idempotencyKey };

    try {
      const order = await placeOrder(idempotencyKey, payload);
      retryKey.current = null;
      onComplete(order);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "We couldn't place your order. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return <section className="checkout-content" aria-labelledby="checkout-title">
    <button className="back-link" type="button" onClick={onBack}>← Back to bag</button>
    <p className="eyebrow">JUST A FEW DETAILS</p>
    <h2 id="checkout-title">Where should we bring it?</h2>
    <div className="checkout-order-summary" aria-label="Order summary">
      {lines.map((line) => <div key={line.menuItemId}>
        <span>{line.name} <small>× {line.quantity}</small></span>
        <strong>{money(line.unitPrice * line.quantity)}</strong>
      </div>)}
      <div><span>Subtotal estimate</span><strong>{money(subtotalEstimate)}</strong></div>
      <p>Delivery fee and tax are ₹0 for this MVP. Final total is confirmed by the server.</p>
    </div>
    <form className="checkout-form" onSubmit={submit}>
      <label htmlFor="checkout-name">Name for the order</label>
      <input id="checkout-name" name="contactName" autoComplete="name" maxLength={120} required
        value={contactName} onChange={(event) => setContactName(event.target.value)} />
      <label htmlFor="checkout-phone">Phone number</label>
      <input id="checkout-phone" name="contactPhone" type="tel" autoComplete="tel" maxLength={32} required
        value={contactPhone} onChange={(event) => setContactPhone(event.target.value)} />
      <label htmlFor="checkout-address">Delivery address</label>
      <textarea id="checkout-address" name="deliveryAddress" autoComplete="street-address" maxLength={1000}
        required rows={3} value={deliveryAddress} onChange={(event) => setDeliveryAddress(event.target.value)} />
      <label htmlFor="checkout-notes">Delivery notes <span>(optional)</span></label>
      <textarea id="checkout-notes" name="deliveryNotes" maxLength={500} rows={2}
        value={deliveryNotes} onChange={(event) => setDeliveryNotes(event.target.value)} />
      {error && <p className="form-error" role="alert">{error}</p>}
      <button className="button button-dark checkout-submit" type="submit" disabled={submitting || lines.length === 0}>
        {submitting ? "Placing your order…" : "Place order"}
      </button>
    </form>
  </section>;
}
