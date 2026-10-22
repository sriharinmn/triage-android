// The shipping provider answers with an HTML maintenance page instead of JSON.
export function parseShippingQuote(body) {
  return JSON.parse(body);
}

export function fetchShippingQuote() {
  return parseShippingQuote("<!DOCTYPE html><html><body>Scheduled maintenance</body></html>");
}
