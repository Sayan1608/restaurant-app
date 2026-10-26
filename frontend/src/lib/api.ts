export type Restaurant = {
  id: string;
  name: string;
  description: string | null;
  address: string;
  createdAt: string;
};

export type MenuItem = {
  id: string;
  categoryId: string;
  categoryName: string;
  name: string;
  description: string | null;
  price: number;
  imageUrl: string | null;
  available: boolean;
};

export type MenuCategory = {
  id: string;
  name: string;
  displayOrder: number;
  items: MenuItem[];
};

export type Menu = { restaurantId: string; restaurantName: string; categories: MenuCategory[] };
export type CurrentUser = { id: string; name: string; email: string; role: "CUSTOMER" | "ADMIN"; createdAt: string };
export type ApiError = { code: string; message: string; fieldErrors?: Record<string, string> };

const baseUrl = (process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080").replace(/\/$/, "");

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${baseUrl}${path}`, { ...init, credentials: "include" });
  if (!response.ok) {
    const error = (await response.json().catch(() => null)) as ApiError | null;
    throw new Error(error?.message ?? "Something went wrong. Please try again.");
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export const api = {
  listRestaurants: () => request<Restaurant[]>("/api/restaurants"),
  getMenu: (id: string) => request<Menu>(`/api/restaurants/${id}/menu`),
  me: () => request<CurrentUser>("/api/auth/me"),
  csrf: () => request<{ token: string; headerName: string }>("/api/auth/csrf"),
  async authenticate(path: "register" | "login", payload: Record<string, string>) {
    const csrf = await this.csrf();
    const registration = path === "register";
    if (registration) {
      await request<CurrentUser>("/api/auth/register", {
        method: "POST",
        headers: { "Content-Type": "application/json", [csrf.headerName]: csrf.token },
        body: JSON.stringify(payload),
      });
    }
    const loginCsrf = registration ? await this.csrf() : csrf;
    return request<CurrentUser>("/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json", [loginCsrf.headerName]: loginCsrf.token },
      body: JSON.stringify(payload),
    });
  },
  async logout() {
    const csrf = await this.csrf();
    await request<void>("/api/auth/logout", { method: "POST", headers: { [csrf.headerName]: csrf.token } });
  },
  async ask(restaurantId: string, message: string) {
    const csrf = await this.csrf();
    return request<{ reply: string }>("/api/chat/messages", {
      method: "POST",
      headers: { "Content-Type": "application/json", [csrf.headerName]: csrf.token },
      body: JSON.stringify({ restaurantId, message }),
    });
  },
};
