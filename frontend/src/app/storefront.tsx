"use client";

import { useEffect, useMemo, useState } from "react";
import { api, type CurrentUser, type Menu, type MenuItem, type OrderResponse, type Restaurant } from "@/lib/api";
import CheckoutForm from "./checkout-form";
import OrderCenter from "./order-center";

type CartLine = { item: MenuItem; quantity: number };
type ChatLine = { from: "assistant" | "you"; text: string };

const money = (amount: number) => new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 }).format(amount);

export default function Storefront() {
  const [restaurant, setRestaurant] = useState<Restaurant | null>(null);
  const [menu, setMenu] = useState<Menu | null>(null);
  const [cart, setCart] = useState<CartLine[]>([]);
  const [cartHydrated, setCartHydrated] = useState(false);
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [cartOpen, setCartOpen] = useState(false);
  const [checkoutOpen, setCheckoutOpen] = useState(false);
  const [completedOrder, setCompletedOrder] = useState<OrderResponse | null>(null);
  const [ordersOpen, setOrdersOpen] = useState(false);
  const [authOpen, setAuthOpen] = useState(false);
  const [chatOpen, setChatOpen] = useState(false);
  const [authMode, setAuthMode] = useState<"login" | "register" | "forgot" | "reset">("login");
  const [authBusy, setAuthBusy] = useState(false);
  const [authMessage, setAuthMessage] = useState("");
  const [recoveryEmail, setRecoveryEmail] = useState("");
  const [chatInput, setChatInput] = useState("");
  const [chatBusy, setChatBusy] = useState(false);
  const [chatLines, setChatLines] = useState<ChatLine[]>([{ from: "assistant", text: "Hi! Ask me about the menu, ingredients, or what might suit your craving." }]);

  useEffect(() => {
    let active = true;
    Promise.all([api.listRestaurants(), api.me().catch(() => null)]).then(async ([restaurants, currentUser]) => {
      if (!active) return;
      setUser(currentUser);
      const first = restaurants[0];
      if (!first) { setLoading(false); return; }
      setRestaurant(first);
      const result = await api.getMenu(first.id);
      if (active) {
        setMenu(result);
        const currentItems = new Map(result.categories.flatMap((category) => category.items).map((item) => [item.id, item]));
        setCart((lines) => lines.flatMap((line) => {
          const currentItem = currentItems.get(line.item.id);
          return currentItem ? [{ ...line, item: currentItem }] : [];
        }));
      }
    }).catch((reason: unknown) => {
      if (active) setError(reason instanceof Error ? reason.message : "The menu could not be loaded.");
    }).finally(() => { if (active) setLoading(false); });
    try {
      const raw = window.localStorage.getItem("restaurant-cart-v1");
      if (raw) setCart(JSON.parse(raw) as CartLine[]);
    } catch { window.localStorage.removeItem("restaurant-cart-v1"); }
    finally { setCartHydrated(true); }
    return () => { active = false; };
  }, []);

  useEffect(() => { if (cartHydrated) window.localStorage.setItem("restaurant-cart-v1", JSON.stringify(cart)); }, [cart, cartHydrated]);

  const itemCount = cart.reduce((sum, line) => sum + line.quantity, 0);
  const subtotal = useMemo(() => cart.reduce((sum, line) => sum + line.item.price * line.quantity, 0), [cart]);
  const add = (item: MenuItem) => setCart((lines) => {
    const found = lines.find((line) => line.item.id === item.id);
    return found ? lines.map((line) => line.item.id === item.id ? { ...line, quantity: line.quantity + 1 } : line) : [...lines, { item, quantity: 1 }];
  });
  const changeQuantity = (id: string, amount: number) => setCart((lines) => lines.map((line) => line.item.id === id ? { ...line, quantity: line.quantity + amount } : line).filter((line) => line.quantity > 0));
  const startCheckout = () => {
    setCompletedOrder(null);
    setCheckoutOpen(true);
    if (!user) { setAuthMode("login"); setAuthOpen(true); }
  };

  async function submitAuth(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); setAuthBusy(true); setAuthMessage("");
    if (authMode !== "login" && authMode !== "register") { setAuthBusy(false); return; }
    const data = new FormData(event.currentTarget);
    const payload = Object.fromEntries([...data.entries()].map(([key, value]) => [key, String(value)]));
    try {
      const signedIn = await api.authenticate(authMode, payload);
      setUser(signedIn); setAuthOpen(false);
    } catch (reason) { setAuthMessage(reason instanceof Error ? reason.message : "Could not sign you in."); }
    finally { setAuthBusy(false); }
  }

  async function requestPasswordReset(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); setAuthBusy(true); setAuthMessage("");
    const data = new FormData(event.currentTarget);
    const email = String(data.get("email") ?? "").trim();
    try {
      const result = await api.requestPasswordReset(email);
      setRecoveryEmail(email); setAuthMessage(result.message); setAuthMode("reset");
    } catch (reason) { setAuthMessage(reason instanceof Error ? reason.message : "Could not request a reset code."); }
    finally { setAuthBusy(false); }
  }

  async function confirmPasswordReset(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); setAuthBusy(true); setAuthMessage("");
    const data = new FormData(event.currentTarget);
    try {
      const result = await api.resetPassword(recoveryEmail, String(data.get("otp") ?? ""), String(data.get("newPassword") ?? ""));
      setAuthMode("login"); setAuthMessage(result.message);
    } catch (reason) { setAuthMessage(reason instanceof Error ? reason.message : "Could not reset your password."); }
    finally { setAuthBusy(false); }
  }

  async function signOut() {
    try { await api.logout(); setUser(null); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "Could not sign out."); }
  }

  async function sendChat(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const prompt = chatInput.trim();
    if (!prompt || !restaurant || chatBusy) return;
    setChatInput(""); setChatLines((lines) => [...lines, { from: "you", text: prompt }]); setChatBusy(true);
    try {
      const result = await api.ask(restaurant.id, prompt);
      setChatLines((lines) => [...lines, { from: "assistant", text: result.reply }]);
    } catch (reason) {
      setChatLines((lines) => [...lines, { from: "assistant", text: reason instanceof Error ? reason.message : "The assistant is unavailable right now." }]);
    } finally { setChatBusy(false); }
  }

  return <main className="site-shell">
    <div className="announcement">A little something delicious is just around the corner <span>✳</span></div>
    <header className="topbar">
      <a className="brand" href="#top" aria-label="Goodfolk home"><span className="brand-mark">g.</span><span>goodfolk<span className="brand-dot">.</span></span></a>
      <nav className="main-nav" aria-label="Main navigation"><a href="#menu">Menu</a><a href="#story">Our kitchen</a><a href="#faq">FAQ</a></nav>
      <div className="header-actions">
        {user ? <><span className="welcome">Hi, {user.name.split(" ")[0]}</span><button className="text-button" onClick={() => setOrdersOpen(true)}>{user.role === "ADMIN" ? "Manage orders" : "My orders"}</button><button className="text-button" onClick={signOut}>Sign out</button></> : <button className="text-button" onClick={() => { setAuthMode("login"); setAuthOpen(true); }}>Sign in</button>}
        <button className="cart-button" onClick={() => setCartOpen(true)} aria-label={`Open bag, ${itemCount} items`}><span>Bag</span><span className="bag-count">{itemCount}</span></button>
      </div>
    </header>

    <section className="hero" id="top">
      <div className="hero-copy"><p className="eyebrow">GOOD FOOD, GOOD MOOD</p><h1>Made with care.<br /><em>Shared with joy.</em></h1><p className="hero-description">Comforting favourites, bright flavours, and little moments worth slowing down for.</p><a className="button button-dark" href="#menu">Explore the menu <span>↘</span></a><div className="hero-note"><span className="sparkle">✳</span><span>Freshly made<br />with the good stuff</span></div></div>
      <div className="hero-art" aria-label="Illustration of a freshly prepared meal"><div className="sun-shape"/><div className="plate"><div className="plate-rim"><div className="dish"><span className="leaf leaf-one"/><span className="leaf leaf-two"/><span className="tomato tomato-one"/><span className="tomato tomato-two"/><span className="grain grain-one"/><span className="grain grain-two"/><span className="grain grain-three"/><span className="herb">✳</span></div></div></div><div className="art-label">A GOOD<br />LITTLE LUNCH</div><span className="art-sparkle">✳</span></div>
      <div className="hero-bottom"><span>MADE FOR THE EVERYDAY</span><span>✳</span><span>LOVED A LITTLE EXTRA</span></div>
    </section>

    <section className="menu-section" id="menu">
      <div className="section-heading"><div><p className="eyebrow">FROM OUR KITCHEN</p><h2>A menu to <em>feel good about.</em></h2></div><p>Thoughtfully sourced, freshly prepared, and always made with a little extra love.</p></div>
      {loading ? <div className="state-card">Bringing the good things to the table…</div> : error ? <div className="state-card error-state" role="alert">{error}</div> : !menu || !menu.categories.some((category) => category.items.length) ? <div className="state-card">Our menu is getting ready. Please check back soon.</div> : <>
        <div className="menu-tabs"><span>THE GOOD STUFF</span><span>{menu.categories.length} little collections</span></div>
        {menu.categories.map((category, index) => <section className="category" key={category.id}><div className="category-title"><span className="category-number">0{index + 1}</span><h3>{category.name}</h3><span className="category-line"/></div><div className="food-grid">{category.items.map((item) => <article className="food-card" key={item.id}>
          <div className={`food-art food-art-${index % 4}`}><span className="food-art-label">GOODFOLK<br />KITCHEN</span><span className="dish-illustration"><i/><i/><i/><b>✳</b></span></div>
          <div className="food-details"><div><h4>{item.name}</h4><span className="food-price">{money(item.price)}</span></div><p>{item.description || "A fresh little favourite from our kitchen."}</p><button className="add-button" onClick={() => add(item)} aria-label={`Add ${item.name} to bag`}>Add to bag <span>+</span></button></div>
        </article>)}</div></section>)}
      </>}
    </section>

    <section className="story-section" id="story"><div className="story-visual"><div className="story-circle"><span>GOOD<br />THINGS<br /><em>GROW</em><br />HERE</span><b>✳</b></div><span className="story-caption">A LITTLE CARE IN EVERY BITE</span></div><div className="story-copy"><p className="eyebrow">A NOTE FROM OUR KITCHEN</p><h2>Good food feels<br /><em>like being home.</em></h2><p>We believe a meal can turn an ordinary day into a good one. So we cook with ingredients we love, recipes we grew up with, and the kind of care you can taste.</p><a href="#menu" className="underlined-link">Come find your favourite <span>↗</span></a></div></section>

    <section className="closing-band" id="faq"><span>✳</span><p>Something on your mind?</p><button onClick={() => setChatOpen(true)}>Ask our little helper <span>↗</span></button></section>
    <footer className="footer"><a className="brand" href="#top"><span className="brand-mark">g.</span><span>goodfolk<span className="brand-dot">.</span></span></a><span>Good food. Good people. Good days.</span><span>Made with care <b>✳</b></span></footer>

    {ordersOpen && user && <OrderCenter isAdmin={user.role === "ADMIN"} onClose={() => setOrdersOpen(false)} loadMyOrders={api.myOrders} loadAdminOrders={api.adminOrders} loadOrder={api.getOrder} updateOrderStatus={api.updateOrderStatus} />}

    {cartOpen && <div className="overlay" onMouseDown={(event) => { if (event.target === event.currentTarget) setCartOpen(false); }}><aside className="drawer" aria-label="Your bag"><div className="drawer-head"><div><p className="eyebrow">YOUR LITTLE BAG</p><h2>{completedOrder ? "Order placed." : "Good choices."}</h2></div><button className="close-button" onClick={() => { setCartOpen(false); setCompletedOrder(null); setCheckoutOpen(false); }} aria-label="Close bag">?</button></div>{completedOrder ? <section className="order-confirmation" aria-label="Order confirmation"><span className="confirmation-mark">?</span><h3>Thanks, your order is in.</h3><p>Order <strong>#{completedOrder.id}</strong> is <strong>{completedOrder.status.toLowerCase()}</strong>.</p><div><span>Order total</span><strong>{money(completedOrder.total)}</strong></div><button className="button button-dark" onClick={() => { setCartOpen(false); setCompletedOrder(null); }}>Lovely, thanks</button></section> : checkoutOpen && user ? <CheckoutForm restaurantId={restaurant?.id ?? ""} customerName={user.name} lines={cart.map(({ item, quantity }) => ({ menuItemId: item.id, name: item.name, quantity, unitPrice: item.price }))} placeOrder={api.createOrder} onBack={() => setCheckoutOpen(false)} onComplete={(order) => { setCompletedOrder(order); setCart([]); setCheckoutOpen(false); }} /> : cart.length ? <><div className="cart-lines">{cart.map(({ item, quantity }) => <div className="cart-line" key={item.id}><div><h3>{item.name}</h3><span>{money(item.price)} each</span></div><div className="quantity-control"><button onClick={() => changeQuantity(item.id, -1)} aria-label={`Remove one ${item.name}`}>?</button><span>{quantity}</span><button onClick={() => changeQuantity(item.id, 1)} aria-label={`Add one ${item.name}`}>+</button></div></div>)}</div><div className="cart-summary"><div><span>Subtotal estimate</span><strong>{money(subtotal)}</strong></div><p>Final prices and availability are confirmed by the kitchen at checkout. Tax and delivery fee are ?0 for now.</p><button className="button button-dark checkout-button" onClick={startCheckout}>{user ? "Continue to checkout" : "Sign in to checkout"} <span>?</span></button></div></> : <div className="empty-cart"><span>?</span><p>Your bag is waiting for something lovely.</p><button className="button button-dark" onClick={() => { setCartOpen(false); document.getElementById("menu")?.scrollIntoView({ behavior: "smooth" }); }}>Explore the menu</button></div>}</aside></div>}

    {authOpen && <div className="overlay" onMouseDown={(event) => { if (event.target === event.currentTarget) setAuthOpen(false); }}><section className="auth-modal" aria-label="Account access"><button className="close-button" onClick={() => setAuthOpen(false)} aria-label="Close">×</button><p className="eyebrow">GOOD TO HAVE YOU</p><h2>{authMode === "login" ? "Welcome back." : authMode === "register" ? "Come on in." : authMode === "forgot" ? "Reset your password." : "Enter your code."}</h2>{authMode === "forgot" ? <form onSubmit={requestPasswordReset} className="auth-form"><label>Email address<input name="email" type="email" autoComplete="email" required maxLength={320}/></label>{authMessage && <p className="form-error" role="status">{authMessage}</p>}<button className="button button-dark" disabled={authBusy}>{authBusy ? "Sending…" : "Send verification code"}</button></form> : authMode === "reset" ? <form onSubmit={confirmPasswordReset} className="auth-form"><p>Enter the six digit code sent to {recoveryEmail} and choose a new password.</p><label>Verification code<input name="otp" inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" minLength={6} maxLength={6} required/></label><label>New password<input name="newPassword" type="password" autoComplete="new-password" minLength={12} maxLength={72} required/></label>{authMessage && <p className="form-error" role="alert">{authMessage}</p>}<button className="button button-dark" disabled={authBusy}>{authBusy ? "Resetting…" : "Reset password"}</button></form> : <><form onSubmit={submitAuth} className="auth-form">{authMode === "register" && <label>Your name<input name="name" autoComplete="name" required maxLength={120}/></label>}<label>Email address<input name="email" type="email" autoComplete="email" required maxLength={320}/></label><label>Password<input name="password" type="password" autoComplete={authMode === "login" ? "current-password" : "new-password"} required minLength={authMode === "register" ? 12 : undefined} maxLength={72}/></label>{authMessage && <p className={authMessage.startsWith("Your password has") ? "form-success" : "form-error"} role="status">{authMessage}</p>}<button className="button button-dark" disabled={authBusy}>{authBusy ? "One moment…" : authMode === "login" ? "Sign in" : "Create account"}</button></form>{authMode === "login" && <p className="auth-switch"><button onClick={() => { setAuthMode("forgot"); setAuthMessage(""); }}>Forgot password?</button></p>}<p className="auth-switch">{authMode === "login" ? "New around here?" : "Already one of us?"} <button onClick={() => { setAuthMode(authMode === "login" ? "register" : "login"); setAuthMessage(""); }}>{authMode === "login" ? "Create an account" : "Sign in"}</button></p></>}</section></div>}

    {chatOpen && <div className="chat-panel"><div className="chat-head"><div><span className="chat-icon">✳</span><div><strong>Ask goodfolk</strong><small>Menu &amp; ordering helper · AI</small></div></div><button onClick={() => setChatOpen(false)} aria-label="Close assistant">×</button></div><div className="chat-messages" aria-live="polite">{chatLines.map((line, index) => <p className={`chat-bubble ${line.from}`} key={`${index}-${line.from}`}>{line.text}</p>)}{chatBusy && <p className="chat-bubble assistant">Let me have a little look…</p>}</div><form className="chat-form" onSubmit={sendChat}><label className="sr-only" htmlFor="chat-prompt">Ask a menu question</label><input id="chat-prompt" value={chatInput} onChange={(event) => setChatInput(event.target.value)} maxLength={1000} placeholder="Ask us anything…"/><button disabled={chatBusy || !chatInput.trim()} aria-label="Send question">↑</button></form><p className="chat-note">I can help with the public menu and ordering FAQs. I can’t place or change an order.</p></div>}
  </main>;
}
