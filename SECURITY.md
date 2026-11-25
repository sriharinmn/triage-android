# Security

Triage holds a Sentry auth token on your phone, so security reports matter.

**Please don't open a public issue for a security problem.** Email **triagedev2026@gmail.com** instead, with what you
found and how to reproduce it. You'll get a reply within a week.

## What's in scope
- The Android app in this repository: token storage (Android Keystore), network security, notification actions,
  anything that could leak a token or Sentry data.
- The website in `site/`.

## What Triage does with your data
The app talks only to the Sentry server you choose. There is no Triage server, no analytics and no crash reporting.
The token is encrypted on the phone with an Android Keystore key and never leaves it except in requests to your Sentry
server.
