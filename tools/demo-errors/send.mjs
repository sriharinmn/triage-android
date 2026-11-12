// Sends realistic test errors to the owner's demo Sentry projects so Triage has real data to show.
//
//   node send.mjs shop                   errors from a pretend Node shop backend (project triage-demo)
//   node send.mjs android                crashes from a pretend Android shop app (project triage-demo-android)
//   node send.mjs shop --scenario <name> send just one scenario, as in the full batch
//   node send.mjs shop --only <name>     send one scenario now, from a newer release (use it to regress a resolved issue)
//   node send.mjs shop --burst <name>    send 60 events of one scenario now (use it to escalate an archived issue)
//   node send.mjs shop --fresh <name>    send one event now as a brand-new issue (use it to test "new issue" alerts)
//   add --dry-run                        print the events instead of sending them
//
// DSNs come from TRIAGE_DEMO_DSN / TRIAGE_DEMO_ANDROID_DSN or from dsn.local.json (gitignored).

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import * as Sentry from "@sentry/node";
import { androidScenarios } from "./android-events.mjs";
import { shopScenarios } from "./shop-scenarios.mjs";

const TARGETS = {
  shop: {
    project: "triage-demo",
    envVar: "TRIAGE_DEMO_DSN",
    release: "shop-api@1.4.2",
    newerRelease: "shop-api@1.5.0",
    serverName: "shop-api-7f9c",
    scenarios: shopScenarios,
  },
  android: {
    project: "triage-demo-android",
    envVar: "TRIAGE_DEMO_ANDROID_DSN",
    release: "com.example.shop@2.3.1+231",
    newerRelease: "com.example.shop@2.4.0+240",
    serverName: undefined,
    scenarios: androidScenarios,
  },
};

const HOUR = 3600;
const DAY = 24 * HOUR;

function readDsn(target) {
  if (process.env[target.envVar]) return process.env[target.envVar];
  try {
    const local = JSON.parse(readFileSync(new URL("./dsn.local.json", import.meta.url), "utf8"));
    if (local[target.project]) return local[target.project];
  } catch {
    // Fall through to the error below.
  }
  throw new Error(`No DSN for ${target.project}: set ${target.envVar} or add it to dsn.local.json`);
}

// This folder as it appears in Node stack frames (forward slashes), e.g. C:/Users/.../demo-errors/
const APP_ROOT = fileURLToPath(new URL(".", import.meta.url)).replaceAll("\\", "/");
const HARNESS_FILES = ["send.mjs", "shop-scenarios.mjs", "android-events.mjs"];

/**
 * Keeps only the pretend app's frames and shows them under /srv/shop-api/ like a deployed service, so the events
 * carry neither this laptop's paths nor the frames of this script and the SDK.
 */
function cleanFrames(frames) {
  return frames
    .filter((frame) => {
      const file = frame.filename ?? "";
      if (file.includes("/node_modules/") || file.startsWith("node:internal")) return false;
      const local = file.toLowerCase().startsWith(APP_ROOT.toLowerCase());
      return !local || !HARNESS_FILES.includes(file.slice(APP_ROOT.length));
    })
    .map((frame) => {
      for (const key of ["filename", "abs_path"]) {
        const value = frame[key];
        if (value?.toLowerCase().startsWith(APP_ROOT.toLowerCase())) frame[key] = `/srv/shop-api/${value.slice(APP_ROOT.length)}`;
      }
      return frame;
    });
}

function pick(list) {
  return list[Math.floor(Math.random() * list.length)];
}

function randomBetween(min, max) {
  return min + Math.random() * (max - min);
}

function parseArgs(argv) {
  const [targetName, ...rest] = argv;
  const options = { targetName, scenario: null, only: null, burst: null, fresh: null, dryRun: false };
  for (let i = 0; i < rest.length; i++) {
    if (rest[i] === "--dry-run") options.dryRun = true;
    else if (rest[i] === "--scenario") options.scenario = rest[++i];
    else if (rest[i] === "--only") options.only = rest[++i];
    else if (rest[i] === "--burst") options.burst = rest[++i];
    else if (rest[i] === "--fresh") options.fresh = rest[++i];
    else throw new Error(`Unknown option ${rest[i]}`);
  }
  return options;
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  const target = TARGETS[options.targetName];
  if (!target) throw new Error(`First argument must be one of: ${Object.keys(TARGETS).join(", ")}`);

  Sentry.init({
    dsn: readDsn(target),
    release: target.release,
    environment: "production",
    serverName: target.serverName,
    sendDefaultPii: false,
    tracesSampleRate: 0,
    skipOpenTelemetrySetup: true,
    // Keep stack-trace helpers; drop integrations that report this laptop (OS, modules, hostname) or that would drop
    // the repeated identical events on purpose (Dedupe).
    integrations: (defaults) =>
      defaults.filter((integration) => ["ContextLines", "LinkedErrors", "FunctionToString"].includes(integration.name)),
    // --dry-run prints each finished event instead of sending it.
    beforeSend: options.dryRun ? (event) => (console.log(JSON.stringify(event, null, 2)), null) : undefined,
  });

  const now = Date.now() / 1000;
  // A fingerprint no earlier event had, so Sentry opens a brand-new issue for --fresh instead of adding to the old one.
  const freshFingerprint = options.fresh
    ? [options.fresh, "fresh", `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`]
    : null;
  let scenarios = target.scenarios;
  if (options.scenario || options.only || options.burst || options.fresh) {
    const name = options.scenario ?? options.only ?? options.burst ?? options.fresh;
    scenarios = scenarios.filter((scenario) => scenario.name === name);
    if (scenarios.length === 0) throw new Error(`No scenario named ${name}`);
  }

  let sent = 0;
  for (const scenario of scenarios) {
    const count = options.burst ? 60 : options.only || options.fresh ? 1 : scenario.count;
    const users = Array.from({ length: scenario.users }, (_, i) => `user-${scenario.userSeed + i}`);
    for (let i = 0; i < count; i++) {
      // Spread events between newestHoursAgo and oldestDaysAgo so first/last seen differ per issue.
      // The first event sits at the newest bound, so a rerun always gives each issue a fresh latest event.
      const ageSeconds =
        options.only || options.burst || options.fresh
          ? 0
          : i === 0
            ? scenario.newestHoursAgo * HOUR
            : randomBetween(scenario.newestHoursAgo * HOUR, scenario.oldestDaysAgo * DAY);
      const environment = pick(scenario.environments);
      const userIndex = Math.floor(Math.random() * users.length);
      const user = users[userIndex];
      Sentry.withScope((scope) => {
        // An explicit documentation-range IP (203.0.113.0/24) stops Sentry from geolocating the sender's real IP.
        if (user) scope.setUser({ id: user, email: `${user}@example.com`, ip_address: `203.0.113.${userIndex + 1}` });
        scope.setTags(scenario.tags ?? {});
        if (scenario.level) scope.setLevel(scenario.level);
        for (const crumb of scenario.breadcrumbs ?? []) scope.addBreadcrumb(crumb);
        scope.addEventProcessor((event) => {
          event.timestamp = now - ageSeconds;
          event.environment = environment;
          if (options.only || options.burst) event.release = target.newerRelease;
          // Set here, after the scope's own fingerprint, so it wins (webhook-failures sets one per partner).
          if (freshFingerprint) event.fingerprint = freshFingerprint;
          // Breadcrumbs lead up to the error a few seconds apart.
          const crumbs = event.breadcrumbs ?? [];
          crumbs.forEach((crumb, index) => {
            crumb.timestamp = event.timestamp - (crumbs.length - index) * 2;
          });
          // The Node SDK defaults server_name to this laptop's hostname; a phone app has none.
          if (!target.serverName) delete event.server_name;
          // It also adds its own runtime (node) to every event, which the hand-built Java crashes never ran on.
          if (target === TARGETS.android) delete event.contexts?.runtime;
          for (const exception of event.exception?.values ?? []) {
            if (exception.stacktrace?.frames) exception.stacktrace.frames = cleanFrames(exception.stacktrace.frames);
          }
          return event;
        });
        scenario.send(Sentry, target, i);
      });
      sent++;
    }
    // The SDK holds at most 64 events in flight and silently drops the rest, so drain after every scenario
    // (each sends at most 60).
    await Sentry.flush(15000);
  }

  const flushed = await Sentry.flush(15000);
  console.log(`${flushed ? "Sent" : "Tried to send"} ${sent} events to ${target.project}${flushed ? "" : " (flush timed out)"}`);
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
