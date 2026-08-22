# Releasing to Google Play

Full walkthrough with Console navigation lives in the release runbook artifact.
This is the command reference.

## Prerequisites

- Android Studio with **SDK Platform 36** installed (not present by default)
- JDK 17+ (Android Studio bundles one)

## 1. Create the upload key (once)

```bash
keytool -genkeypair -v -keystore upload-keystore.jks \
    -alias upload -keyalg RSA -keysize 2048 -validity 10000
```

Answer `yes` at the "Is CN=... correct?" prompt — the default is *no*. Press
Enter at the key-password prompt to reuse the keystore password.

Back this file up somewhere private and durable before continuing.

## 2. Wire it into the build

```bash
cp keystore.properties.example keystore.properties
# edit keystore.properties: set storePassword and keyPassword
```

`keystore.properties`, `*.jks` and `upload-keystore*` are gitignored.

## 3. Build the bundle

```bash
./gradlew bundleRelease
# -> app/build/outputs/bundle/release/app-release.aab
```

## 4. Verify before uploading

```bash
# should print "jar verified"
jarsigner -verify app/build/outputs/bundle/release/app-release.aab

# record the fingerprint; it must match the upload certificate in Play Console
keytool -list -v -keystore upload-keystore.jks -alias upload | grep SHA256
```

## 5. Upload

Play Console → Test and release → Testing → Closed testing → Create new release.

Google verifies your upload signature, strips it, and re-signs with the app
signing key it holds (Play App Signing — mandatory for new apps shipping an
AAB). The upload key can be reset by support if lost; the app signing key
cannot change, which is why Google holding it is the safe default.

## Every subsequent release

Bump `version.properties` (versionCode must increase and be unique per upload),
rebuild, upload. See CLAUDE.md for the versioning convention.

## Store listing

Copy for every Play Console field is in `store/listing.md`; graphics are in
`store/`. Privacy policy is `PRIVACY.md` and needs a public URL — GitHub Pages
on this repo is the quickest route.
