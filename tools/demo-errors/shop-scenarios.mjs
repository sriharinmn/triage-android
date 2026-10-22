// Errors from the pretend Node shop backend in ./shop. Each scenario becomes one Sentry issue.

import { warmSessionCache } from "./shop/cache.mjs";
import { checkout } from "./shop/cart.mjs";
import { categoryPath } from "./shop/catalog.mjs";
import { findOrdersForUser } from "./shop/db.mjs";
import { chargeCard } from "./shop/payments.mjs";
import { fetchShippingQuote } from "./shop/upstream.mjs";

function captureThrown(Sentry, run, mechanism) {
  try {
    run();
  } catch (error) {
    // The SDK reads a custom mechanism (e.g. unhandled) from hint.data.
    Sentry.captureException(error, mechanism ? { data: { mechanism } } : undefined);
  }
}

const UNHANDLED_REJECTION = { type: "auto.node.onunhandledrejection", handled: false };

const apiCall = (method, url, status) => ({
  type: "http",
  category: "http",
  data: { method, url, status_code: status },
});

export const shopScenarios = [
  {
    name: "cart-total",
    count: 24,
    users: 11,
    userSeed: 100,
    environments: ["production", "production", "production", "staging"],
    newestHoursAgo: 0.05,
    oldestDaysAgo: 6,
    tags: { route: "POST /api/checkout" },
    breadcrumbs: [
      apiCall("GET", "/api/cart", 200),
      { category: "query", message: "SELECT sku, quantity FROM cart_items WHERE cart_id = $1" },
      apiCall("POST", "/api/checkout", 500),
    ],
    send: (Sentry) =>
      captureThrown(Sentry, () =>
        checkout(
          { items: [{ sku: "TENT-2P", quantity: 1 }, { sku: "LANTERN-OLD", quantity: 2 }] },
          [{ sku: "TENT-2P", price: 129.0 }],
        ),
      ),
  },
  {
    name: "orders-timeout",
    count: 9,
    users: 6,
    userSeed: 200,
    environments: ["production", "staging"],
    newestHoursAgo: 2,
    oldestDaysAgo: 3,
    tags: { route: "GET /api/orders", db: "postgres" },
    breadcrumbs: [apiCall("GET", "/api/orders", 504)],
    send: (Sentry) => captureThrown(Sentry, () => findOrdersForUser("u_1042")),
  },
  {
    name: "payment-declined",
    count: 6,
    users: 5,
    userSeed: 300,
    level: "warning",
    environments: ["production"],
    newestHoursAgo: 5,
    oldestDaysAgo: 4,
    tags: { route: "POST /api/payments", "payment.provider": "cardpay" },
    breadcrumbs: [apiCall("POST", "/api/payments", 402)],
    send: (Sentry) => captureThrown(Sentry, () => chargeCard(4999, { availableCents: 1200 })),
  },
  {
    name: "cache-refused",
    count: 3,
    users: 0,
    userSeed: 0,
    level: "fatal",
    environments: ["production"],
    newestHoursAgo: 26,
    oldestDaysAgo: 2,
    tags: { component: "session-cache" },
    send: (Sentry) => captureThrown(Sentry, () => warmSessionCache(), UNHANDLED_REJECTION),
  },
  {
    name: "category-loop",
    count: 2,
    users: 2,
    userSeed: 400,
    environments: ["staging"],
    newestHoursAgo: 30,
    oldestDaysAgo: 5,
    tags: { route: "GET /api/categories/:id" },
    send: (Sentry) => captureThrown(Sentry, () => categoryPath("outdoor")),
  },
  {
    name: "shipping-json",
    count: 7,
    users: 4,
    userSeed: 500,
    environments: ["production"],
    newestHoursAgo: 0.5,
    oldestDaysAgo: 1.5,
    tags: { route: "GET /api/shipping/quote", upstream: "parcelhub" },
    breadcrumbs: [apiCall("GET", "https://api.parcelhub.example/v2/quote", 503)],
    send: (Sentry) => captureThrown(Sentry, () => fetchShippingQuote()),
  },
  {
    name: "slow-checkout",
    count: 4,
    users: 3,
    userSeed: 600,
    level: "warning",
    environments: ["production"],
    newestHoursAgo: 1,
    oldestDaysAgo: 2,
    tags: { route: "POST /api/checkout" },
    // captureEvent, not captureMessage: this SDK attaches a stack to messages, which would title the issue after a frame.
    send: (Sentry) => Sentry.captureEvent({ message: "Slow checkout: took longer than the 5s budget" }),
  },
  {
    name: "flag-fallback",
    count: 1,
    users: 1,
    userSeed: 700,
    level: "info",
    environments: ["staging"],
    newestHoursAgo: 48,
    oldestDaysAgo: 2,
    send: (Sentry) => Sentry.captureEvent({ message: "Feature flag new-checkout fell back to its default value" }),
  },
];
