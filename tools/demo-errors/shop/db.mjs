export class DatabaseTimeoutError extends Error {
  constructor(message) {
    super(message);
    this.name = "DatabaseTimeoutError";
  }
}

function runQuery(sql, timeoutMs) {
  // The pool is exhausted, so every query waits until the timeout fires.
  throw new DatabaseTimeoutError(`Query timed out after ${timeoutMs}ms`);
}

export function findOrdersForUser(userId) {
  return runQuery("SELECT * FROM orders WHERE user_id = $1 ORDER BY created_at DESC", 5000);
}
