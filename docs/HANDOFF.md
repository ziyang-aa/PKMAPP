# PKMAPP Handoff

## Workspace

- Work only in the Ubuntu WSL project: `/home/uug/projects/pkmAPP`.
- Windows mirror for Android Studio: `D:\pkmAPP`.
- Git remote: `git@github.com:ziyang-aa/PKMAPP.git`.
- Branch: `main`.
- Latest committed UI checkpoint: `1293784` (`style: clean details test whitespace`); the next checkpoint adds the source-level record-to-details test and refreshed delivery notes.
- This is a runnable preview checkpoint: push it so the user can inspect the UI in Android Studio before the remaining enhancements.

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
- The record form and details summary/list now compile and pass their UI-source checks. `825180e` connects saving a record to the details screen.
- All five feature pages now expose a left-aligned return action that goes back to the horizontally pannable Home screen.
- Record entry now starts with a four-column category grid; selecting a category reveals the amount, note, calculator and save area. Date selection uses three wheel pickers for year/month/day, and the page uses a faint Treecko decoration. Category icons remain unified vector placeholders by user choice.
- Details now places a horizontal "森林小工具" card above the monthly balance card. It exposes the planned exchange-rate and borrowing-statistics entry points, while clearly marking their data functionality as a later phase.
- Details now supports switching between current in-memory ledgers and creating a named ledger that is immediately selected. The summary and transaction list refresh from the active ledger.
- The record-page Android test now follows the intended sequence: choose a category first, then verify the entry controls are visible. It compiles, but still needs an emulator/phone run because no WSL device is connected.
- The five feature pages now use the user-provided transparent WebP “open door” illustration as a 180dp-wide clickable return-to-home entrance, replacing the previous text buttons.
- Detail records now use a `RecyclerView` and a JVM-tested date grouping model. Each date appears once as 今天、昨天或月日标题; records retain explicit 收入/支出 text alongside their colour. Empty ledgers show a “去记一笔” action.
- `MainNavigationTest` now contains a full save-to-details scenario that uses a unique note to prove the newly entered record renders. It has compiled but has not run without a connected WSL-visible device.
- Custom categories and the calculator dialog are complete. Remaining: real exchange-rate/borrowing data, ledger deletion, connected-device end-to-end/visual test, charts, savings, profile data and later local persistence.

## Known Worktree State

- Existing unrelated line-ending changes remain in `gradle/wrapper/gradle-wrapper.properties` and `gradlew.bat`; do not revert them without explicit instruction.
- Only the pre-existing line-ending changes remain in the working tree; do not stage them.

## Next Steps

1. Make the record-page instrumentation test select a category before asserting the progressively revealed entry controls, then run it on an emulator/phone.
2. Implement the real exchange-rate and borrowing-statistics data flows after the frontend layout is confirmed.
3. Replace the transaction `LinearLayout` with a date-grouped `RecyclerView`, then add device-level end-to-end verification. If a device is available in WSL, run:

```bash
./gradlew connectedDebugAndroidTest
```

4. Update this document at every completed feature phase before pushing, recording commits, tests, environment and remaining work.
