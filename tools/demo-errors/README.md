# demo-errors

Sends realistic test errors to the owner's demo Sentry org (`demo-org`, EU), so Triage has real data to show on the phone. It uses Sentry's official Node SDK. It is not part of the app.

| Target | Sentry project | What it sends |
|---|---|---|
| `shop` | `triage-demo` | real errors thrown by the pretend Node shop backend in `shop/` (9 scenarios in `shop-scenarios.mjs`, including 30 one-event webhook issues so the inbox has more than one 25-issue page) |
| `android` | `triage-demo-android` | hand-built Java crash events in the shape the Sentry Android SDK sends (7 scenarios in `android-events.mjs`) |

Each scenario becomes one Sentry issue. Events are spread over the past days, with varied users, environments (production/staging), levels, tags and breadcrumbs.

## Setup

```bash
cd tools/demo-errors
npm install
```

Put the two DSNs in `dsn.local.json` (gitignored) or in the `TRIAGE_DEMO_DSN` / `TRIAGE_DEMO_ANDROID_DSN` environment variables:

```json
{ "triage-demo": "https://…@….ingest.de.sentry.io/…", "triage-demo-android": "https://…@….ingest.de.sentry.io/…" }
```

A DSN only allows sending events, not reading. Sentry's MCP tool `find_dsns` (or the project's Client Keys page) shows it.

## Use

```bash
node send.mjs shop                        # the full shop batch
node send.mjs android                     # the full Android batch
node send.mjs shop --scenario webhook-failures  # just one scenario, as in the full batch
node send.mjs shop --only cache-refused   # one event now from a newer release: regresses a resolved issue
node send.mjs android --burst socket-timeout   # 60 events now: escalates an issue archived "until escalating"
node send.mjs shop --fresh cart-total     # one event now under a new fingerprint: a brand-new issue (a "new issue" alert)
node send.mjs shop --dry-run              # print the events instead of sending
```

Rerunning a batch adds events to the same issues and gives each issue a fresh latest event. The sender flushes after each scenario because the SDK silently drops events beyond 64 in flight. Messages that differ only by a number are grouped into one issue by Sentry unless they carry their own `fingerprint` (see `webhook-failures`).

## Privacy

The sender strips anything from the laptop:
- stack frames are rewritten to `/srv/shop-api/…`, and the sender's own frames are dropped;
- no hostname or OS context is sent;
- users get documentation-range IPs (`203.0.113.x`) and `@example.com` emails, so Sentry can't geolocate the sender.

The free Developer plan includes 5k errors a month. One full run of both batches is about 123 events.
