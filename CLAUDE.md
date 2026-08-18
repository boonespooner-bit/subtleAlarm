# Subtle Alarm — project conventions

## Versioning (required on every change)

Every change that alters the app must ship as a new version:

1. Bump `version.properties` at the repo root — increment `versionCode` by 1
   and bump `versionName` (patch/minor as appropriate). This file is the single
   source of truth; both `app/build.gradle.kts` and `scripts/build-offline.sh`
   read it.
2. Rebuild the APK (`scripts/build-offline.sh <framework-jar>` in SDK-less
   environments, `./gradlew assembleDebug` otherwise).
3. Copy the build to `apk/SubtleAlarm-v<versionName>.apk` and commit it
   alongside the source change. Keep prior versions' APKs in `apk/`.

## Signing

All debug builds must be signed with the committed keystore
`scripts/debug.keystore` (store/key password `android`, alias
`androiddebugkey`). Never regenerate it — a new key breaks `adb install -r`
upgrades for anyone who installed an earlier build.

## Build constraints

The app is intentionally zero-dependency (pure Android framework, Java 8
compatible — no lambdas/method references, because the offline path dexes with
legacy `dx`). Keep it that way unless the user asks otherwise.
