// Pretend shop backend. Each module fails in a way real services do, so the events carry real stack traces.

export function cartTotal(cart, catalog) {
  return cart.items.reduce((sum, item) => {
    const product = catalog.find((p) => p.sku === item.sku);
    // Bug: a SKU removed from the catalog leaves product undefined.
    return sum + product.price * item.quantity;
  }, 0);
}

export function checkout(cart, catalog) {
  const total = cartTotal(cart, catalog);
  return { total, currency: "EUR" };
}
