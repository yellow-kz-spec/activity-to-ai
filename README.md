## Branching and Merging Assignment

This repository is used to practice the Git branching and merging workflow for the Software Quality Management course.

For this assignment, I created a separate branch, made and committed changes in this branch, and then used a Pull Request to review and merge the changes into the main branch.

# Activity to MD

Activity to MD is an Android application that converts Garmin and Strava workout activities into structured Markdown files optimized for analysis by ChatGPT, Claude, Gemini, and other AI assistants.

The app is Android-first and share-first. Its primary output is a compact, detailed, source-aware `.md` file—not a recreation of a fitness platform's UI.

## Product status

### Current working functionality: Garmin Connect

From a public Garmin activity, the user taps **Share**, selects **Activity to MD**, and the app:

- accepts the Garmin public activity URL without Garmin login or OAuth;
- detects the activity type automatically;
- loads the public page and available Garmin activity data;
- exports summary data, laps, zones, and numerical time series to Markdown;
- removes coordinates, polylines, and other mapped location fields from the export;
- saves the file to the configured folder through Android's Storage Access Framework without overwriting an existing file.

After the initial folder selection, the normal flow requires no manual URL copy/paste and no full app workflow after Share.

### Current working functionality: Strava

The repository contains Strava OAuth, encrypted local token storage, refresh support, share-link resolution, API activity/stream retrieval, historical Open-Meteo enrichment, and Markdown export.

Strava's Android share payload uses `ACTION_SEND` with MIME type `image/*`, an activity share URL in `EXTRA_TEXT`, and an image stream. The image is not intended to be the activity data source. Unlike Garmin public activity pages, Strava share pages lead to authentication and are not suitable for anonymous page extraction. API testing has confirmed detailed activity data and high-resolution streams, so the planned flow is:

`Strava → Share → Activity to MD → identify activity → Strava API → Markdown`

Strava requires one-time OAuth setup. Garmin does not.

## Architecture direction

The target architecture separates platform-specific acquisition from the Markdown exporter:

```text
Share Intent / Activity Input
        ↓
Source Detector
        ↓
Source Adapter (Garmin / Strava / future)
        ↓
NormalizedActivity + source-specific extensions
        ↓
Markdown Exporter
        ↓
Configured output folder
```

Both source adapters now feed the same canonical model and renderer while preserving useful platform-specific metrics. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md). The canonical export contract is [Activity to MD Format Specification v1](docs/ACTIVITY_TO_MD_FORMAT_SPEC_V1.md).

## Markdown output

Exports are designed to be structured, AI-readable, compact enough to share, and detailed enough for meaningful workout analysis. Missing optional or source-specific metrics are not errors. Maps and decorative UI are out of scope, and raw GPS coordinates are not included by default.

## Build requirements

- JDK 17
- Android SDK 36
- Gradle compatible with Android Gradle Plugin 8.12

Build and install the Android app from the command line using a compatible local Gradle installation. The repository currently does not include Gradle wrapper scripts.

## Strava development setup

This setup enables the existing development OAuth connection only; it does not enable Strava activity export.

1. Create a Strava API application at `https://www.strava.com/settings/api`.
2. Set **Authorization Callback Domain** to exactly `strava-auth.garminaiexporter.com` (no scheme or path).
3. Create an untracked `local.properties` file in the project root and add:

   ```properties
   STRAVA_CLIENT_ID=your_numeric_client_id
   STRAVA_CLIENT_SECRET=your_client_secret
   ```

   These names may instead be supplied as Gradle properties or environment variables. `local.properties` is ignored by Git. Credential lookup uses that order: `local.properties`, Gradle properties, then environment variables. The build fails with a clear error if either value is absent, so it cannot silently produce an APK with a permanently disabled Connect Strava button.
4. Build and install the app, open it from the launcher, and tap **Connect Strava**.

The current development callback is:

```text
activitytomd://strava-auth.garminaiexporter.com/callback
```

The callback scheme, host, and path are generated from the single callback definition in `app/build.gradle.kts`. OAuth requests `activity:read_all`.

### Strava security note

Strava requires `client_secret` for authorization-code exchange and token refresh. The private development build currently injects it into `BuildConfig`, which places it in the APK and is not secure for public distribution. A production release will likely need a small HTTPS backend/proxy for token exchange and refresh. Never commit client secrets, access tokens, refresh tokens, athlete data, or test credentials.

Stored access and refresh tokens are encrypted with AES-GCM using a non-exportable Android Keystore key. Disconnect currently removes local authorization data only; it does not revoke the Strava grant remotely.

## Known limitations

- Android is the only supported client platform.
- Garmin page extraction depends on public Garmin web behavior and may require maintenance when Garmin changes it.
- The launcher label still displays `Garmin to AI`, and package names, callback identifiers, storage paths, and historical artifacts retain legacy Garmin-oriented naming. Technical renaming is a separate migration.
