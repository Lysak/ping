# Google Play & Android compliance checklist

Two layers: **code/config** (in this repo, covered by the implementation plan) and
**account/console** (operator does these in Play Console — not code).

## Code / config — current best practice (already in the plan)

- **`targetSdk = 37`, `compileSdk = 37`.** Play requires new apps and updates to
  target within one year of the latest major release; bump every year. (Task 1.)
- **Jetpack Compose + Material 3 Expressive**, AndroidX only — no deprecated `AsyncTask`,
  `Loader`, support-library, or greylisted APIs. (Whole plan.)
- **Edge-to-edge** with real `WindowInsets` — mandatory behaviour on Android 15+.
  (Tasks 1, 12, 13.)
- **Predictive back** opted in (`enableOnBackInvokedCallback`). (Task 1.)
- **Themed app icon** (adaptive + `monochrome`). (Task 14.)
- **R8 full mode + resource shrinking** for release. (Task 1.)
- **Android App Bundle (`.aab`)**, not APK, for upload. (Task 14.)
- **16 KB page size**: the app ships no `jniLibs` of its own, but two AndroidX
  transitive deps carry tiny native stubs — `libandroidx.graphics.path.so`
  (vector path parsing) and `libdatastore_shared_counter.so` (DataStore
  multi-process counter). Both are built by Google with 16 KB-aligned `PT_LOAD`
  segments (`p_align = 0x4000`), so they are page-size safe. Re-check after a
  dependency bump: `./gradlew :app:bundleRelease` then inspect
  `base/lib/arm64-v8a/*.so` `LOAD` alignment.
- **Permissions:** `INTERNET` only. No sensitive/high-risk permission, no
  `<queries>`, so no Permissions Declaration Form needed.
- **No ads SDK, no analytics, no crash reporter, no Firebase, no Play Services**
  → Data Safety form is trivially "no data collected, no data shared".
- **No accounts** → the "account deletion" policy requirement does not apply.
- SPDX GPL-3.0 headers; `LICENSE` present; source is public.

## Account / console — operator checklist (do in Play Console)

### Android developer verification (deadline 2026-09-30) — the email you got

Google now requires every developer to be **verified**, and each app's
**package name + signing keys registered**. Steps:

1. **Play Console → complete developer identity verification.**
   - Personal / individual account: confirm legal name, address, email, phone;
     upload a government ID if asked.
   - Organisation account: D-U-N-S number + org details.
2. **Register this app:** package name **`com.lysak.ping`**.
3. **Register signing keys:** enrol in **Play App Signing** (default for new
   apps). Google holds the *app signing key*; you hold the *upload key*.
   - Create the upload keystore once, in the repo root (`android/ping/`) — see
     `README.md` § Release for the same command:
     ```
     keytool -genkeypair -v -keystore upload-keystore.jks -keyalg RSA -keysize 4096 \
       -validity 10000 -alias upload \
       -dname "CN=Dmytrii Lysak, OU=Ping, O=Dmytrii Lysak, L=Kyiv, C=UA"
     ```
   - Copy `keystore.properties.example` to `keystore.properties` (git-ignored)
     and fill it in — this is exactly what `app/build.gradle.kts` reads
     (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`).
   - `upload-keystore.jks` + `keystore.properties` are git-ignored (`*.jks`,
     `keystore.properties`). Back up the keystore and both passwords somewhere
     safe — losing the upload key is recoverable via Google, losing nothing is
     better.
   - In Play Console, register the **upload key certificate** (export with
     `keytool -export -rfc -alias upload -keystore upload-keystore.jks -file upload.pem`)
     and let Google generate/hold the app signing key.
4. If you ever distribute the `.aab`/APK **outside Play** too, the same signing
   keys must be registered for verification — same keystore, so nothing extra.
5. Re-check the Play Console "Inbox" and "Policy status" pages after submitting;
   verification can take a few days.

> This is identity + key registration only. It does **not** change the code.
> Our job in-repo: produce a correctly signed `.aab` and never lose the keystore.

### Store listing requirements

- **Privacy policy URL** — required for all apps. Host `PRIVACY.md` (GitHub
  Pages, or a gist, or the repo's raw file) and paste the URL in
  Play Console → App content → Privacy policy.
- **Data safety form** — declare: no data collected, no data shared, no
  encryption-in-transit claim needed beyond the default. ICMP/TCP probes send
  no user data.
- **App access** — "All functionality available without special access" (no
  login).
- **Content rating questionnaire** — utility, no objectionable content →
  "Everyone / PEGI 3".
- **Target audience** — not designed for children; 13+.
- **Ads declaration** — "No, this app does not contain ads."

### Make "no ads" visible from the listing — must implement (Task 14)

Google shows no "ad-free" badge and forbids promotional text nuances in the
title, so the ad-free / tracker-free stance has to be carried by the listing
assets. All five are required, not optional:

1. **App title:** `Ping — No Ads` (13 chars). Factual, usually passes review.
   If Google ever rejects it as promotional metadata, fall back to `Ping` —
   the listing is not blocked, only the title reverts.
2. **Feature graphic (1024x500):** large text `NO ADS · NO TRACKING · NO ACCOUNT`.
   Promotional text is allowed here.
3. **First screenshot:** top overlay `No ads. No tracking. Open source.`
4. **Short description (80 chars):** first line
   `No ads. No tracking. No account. Just ping.`
5. **Full description:** a dedicated "What this app does NOT do" section
   (no ads, no analytics, no accounts, no third-party SDKs, no telemetry).

Plus the free real signal: **Data safety form → "No data collected / shared"**,
which Google renders as a visible card on the store page.

Feedback without SDKs: use Play Console **Statistics** (installs/uninstalls,
active devices), **Android vitals** (crashes/ANRs), and **Ratings & reviews**.
Do **not** add Firebase/GA4 — it would break the "collects nothing" claim.
- **Government apps / financial / health** — none apply.
- **`fastlane/metadata/android/`** holds the listing text in-repo (Task 14) so
  it is version-controlled; upload manually or via Play Console.

### Release

- Upload the **`.aab`** to a **closed testing** track first (Play now expects a
  testing history for new personal developer accounts before production —
  typically ~14 days with a handful of testers), then promote to production.
- Fill the **release notes** (`fastlane/.../changelogs/1.txt`).
