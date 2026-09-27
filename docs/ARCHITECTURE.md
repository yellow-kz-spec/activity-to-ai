# Architecture

## Status

This document describes the implemented canonical export architecture for Activity to MD. Its source of truth is [Activity to MD Format Specification v1](ACTIVITY_TO_MD_FORMAT_SPEC_V1.md).

## Target data flow

```text
Share Intent / Activity Input
        ↓
Source Detector
        ↓
Source Adapter
   ├── Garmin
   ├── Strava
   └── Future source
        ↓
NormalizedActivity
  + source-specific extensions
        ↓
Markdown Exporter
        ↓
Configured output folder
```

## Responsibilities

### Source detector

Inspects an Android share intent or other supported input and selects the appropriate adapter. Detection may use the URL host/path, MIME type, and other safe intent metadata. It should not contain platform parsing or export formatting logic.

### Source adapters

Each adapter owns platform-specific input validation, authentication requirements, retrieval, and translation into the normalized model.

- **Garmin adapter:** receives a public Garmin activity URL, extracts data available from the public Garmin page context, and maps it to `NormalizedActivity` without Garmin login or OAuth.
- **Strava adapter:** receives a Strava share URL from an `ACTION_SEND` payload, identifies the activity, retrieves details and high-resolution streams through the authenticated Strava API, and maps them to `NormalizedActivity`.
- **Future adapters:** may represent another platform or a file such as FIT/TCX/GPX. No additional source is currently claimed as supported.

### Normalized activity

`NormalizedActivity` should represent common concepts where possible: identity, source, activity type, timestamps, duration, distance, pace/speed, heart rate, power, elevation, laps/splits, and time-series data.

It must also allow source-specific enrichment rather than forcing every platform into the same field set. Garmin Training Effect, Performance Condition, Stamina, and running dynamics can coexist with Strava splits, best efforts, gear, suffer score, or API-only stream data. A missing platform-specific metric is not an error.

### Markdown exporter

The exporter consumes normalized data and extensions without knowing how the source was authenticated or scraped. It produces a consistent, structured, compact, AI-readable `.md` file while retaining useful source context. It should not reproduce platform UI, maps, or decorative content. Raw GPS coordinates are excluded by default unless a future feature establishes a clear need.

### Output storage

Android's Storage Access Framework provides a user-selected destination folder and persisted permission. The export pipeline must use safe file naming and must not silently overwrite an existing file. Existing legacy storage identifiers are unchanged until a separate migration is designed.

## Authentication boundaries

Garmin and Strava deliberately use different acquisition models:

- Garmin's current source uses anonymous public activity pages and must not be made dependent on Strava-style OAuth.
- Strava public share pages are not equivalent data sources: they redirect into login/authentication. Strava therefore uses OAuth and its API.

The current private development build performs Strava token exchange/refresh with a locally injected client secret. Because an APK cannot keep that secret confidential, a public production design will likely place exchange and refresh behind a small HTTPS backend/proxy. Secrets and tokens must never be committed or documented.

## Extensibility rule

Adding a source should primarily add detection and an adapter/mapping. It should not require redesigning the core Markdown exporter. Schema evolution should be driven by genuinely shared concepts or explicit source extensions, not by pretending every platform exposes identical data.
