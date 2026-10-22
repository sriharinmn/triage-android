export function connectCache(host, port) {
  const error = new Error(`connect ECONNREFUSED ${host}:${port}`);
  error.code = "ECONNREFUSED";
  error.syscall = "connect";
  throw error;
}

export function warmSessionCache() {
  return connectCache("10.0.3.7", 6379);
}
