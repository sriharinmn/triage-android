export class PaymentDeclinedError extends Error {
  constructor(reason) {
    super(`Card declined: ${reason}`);
    this.name = "PaymentDeclinedError";
    this.reason = reason;
  }
}

export function chargeCard(amountCents, card) {
  if (card.availableCents < amountCents) {
    throw new PaymentDeclinedError("insufficient_funds");
  }
  return { status: "captured", amountCents };
}
