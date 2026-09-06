# PKMAPP Handoff

## Workspace

- Work only in the Ubuntu WSL project: `/home/uug/projects/pkmAPP`.
- Windows mirror for Android Studio: `D:\pkmAPP`.
- Git remote: `git@github.com:ziyang-aa/PKMAPP.git`.
- Branch: `main`.
- Latest committed phase-two work: `f523b74` (`feat: add record amount calculator`).
- Commits are local-only and `main` is ahead of `origin/main`; push after record/details UI verification.

## Working Agreement

- Run development commands from the WSL project directory.
- Verify changes in WSL, then push them to GitHub.
- The user pulls the repository in Windows Android Studio to run the app.
- Do not use the Windows project copy as the development workspace.

## WSL Environment

- Java 21 is installed as a user-local JDK at:
  `/home/uug/.local/jdks/openjdk-21/usr/lib/jvm/java-21-openjdk-amd64`.
- In direct non-interactive WSL calls, use the explicit JDK 21 environment; otherwise Gradle may use JDK 17 and attempt an unnecessary toolchain download.
- Android SDK: `/home/uug/Android/Sdk`.
- `local.properties` points to the WSL SDK and must remain local-only.
- GitHub SSH authentication is working. `ssh -T git@github.com` authenticates as `ziyang-aa`.

## Verification

Run from `/home/uug/projects/pkmAPP`:

```bash
./gradlew clean testDebugUnitTest compileDebugAndroidTestSources assembleDebug
```

Latest result: `BUILD SUCCESSFUL`.

The debug APK is generated at:
`app/build/outputs/apk/debug/app-debug.apk`.

## Implemented

- AndroidX Fragment and RecyclerView dependencies.
- ViewBinding and launcher `MainActivity`.
- Forest storybook palette, dimensions, paper-card and bottom-bar resources.
- Five shipped WebP assets in `app/src/main/res/drawable-nodpi/`.
- `AppDestination` with exactly five bottom-bar destinations.
- Fixed bottom navigation: `明细`, `图表`, `记账`, `攒钱`, `我的`.
- Home as one continuously horizontally scrollable panorama.
- Horizontal scroll position restoration across configuration changes.
- Five feature-page Fragment shells with navigation title IDs.
- JVM tests for destination ordering and panorama sizing.
- Android test sources for palette resources and five-button navigation.
- README build instructions and prototype scope.
- Phase two: immutable in-memory ledger/transaction repository, monthly totals and listeners.
- Phase two: integer-cent amount parser and `BigDecimal` four-operator calculator.

## Phase-Two Progress

- Design: `docs/superpowers/specs/2026-09-06-record-details-frontend-design.md` (`a2b5723`).
- Plan: `docs/superpowers/plans/2026-09-06-record-details-frontend.md` (`cfcce74`); Tasks 1–2 are complete and JVM-tested.
- Task 3 is test-first in progress: `MainNavigationTest.recordPage_hasAmountCategoryAndSaveControls` is intentionally uncommitted and currently fails because record-page IDs do not exist yet.
- Remaining: record form, details list/summary, end-to-end Espresso flow, final build/device QA, README update and push.

## Known Worktree State

- Existing unrelated line-ending changes remain in `gradle/wrapper/gradle-wrapper.properties` and `gradlew.bat`; do not revert them without explicit instruction.
- Keep the intentional uncommitted `MainNavigationTest.java` and plan checkbox updates, then continue Task 3.

## Next Steps

1. Continue Task 3 by replacing the record placeholder with the Java/XML entry form; retain its failing UI test until the IDs compile.
2. Complete Task 4 details RecyclerView/monthly summary and Task 5 end-to-end verification.
3. Push only after final verification, then run on emulator/phone. If a device is available in WSL, run:

```bash
./gradlew connectedDebugAndroidTest
```

4. Update this document at every completed feature phase before pushing, recording commits, tests, environment and remaining work.
