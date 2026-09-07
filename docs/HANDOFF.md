# PKMAPP Handoff

## Workspace

- Work only in the Ubuntu WSL project: `/home/uug/projects/pkmAPP`.
- Windows mirror for Android Studio: `D:\pkmAPP`.
- Git remote: `git@github.com:ziyang-aa/PKMAPP.git`.
- Branch: `main`.
- Latest committed Android checkpoint: `168cf12` (`feat: add chart trends and rankings`). The current visual-design checkpoint adds the HTML visual board, converted category WebPs, and an editable Figma board.
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
- The return-door WebP is alpha-cropped and shown in a compact 120dp-wide touch target; the details page also has a wheel-style year/month selector that filters its monthly balance and transaction list together.
- Charts now has frontend income/expense and week/month/year controls, a native drawn trend line and a category ranking. It reads the current in-memory ledger totals/categories; trend points remain presentation data until a later analytics-data phase.
- Detail records now use a `RecyclerView` and a JVM-tested date grouping model. Each date appears once as 今天、昨天或月日标题; records retain explicit 收入/支出 text alongside their colour. Empty ledgers show a “去记一笔” action.
- `MainNavigationTest` now contains a full save-to-details scenario that uses a unique note to prove the newly entered record renders. It has compiled but has not run without a connected WSL-visible device.
- Custom categories and the calculator dialog are complete. Remaining: real exchange-rate/borrowing data, ledger deletion, connected-device end-to-end/visual test, charts, savings, profile data and later local persistence.

## Known Worktree State

- Existing unrelated line-ending changes remain in `gradle/wrapper/gradle-wrapper.properties` and `gradlew.bat`; do not revert them without explicit instruction.
- The pre-existing line-ending changes remain alongside the current visual-phase files; do not stage the line-ending-only files.

## HTML / Figma Visual Phase (2026-09-07)

- Per the user's latest instruction, the HTML visual board is maintained directly in `D:\pkmAPP\design\pkmapp-visual-redesign.html`; do not overwrite the existing untracked `design\mastergo\pkmapp-redesign.html`.
- The board contains six responsive mobile screens: 首页、明细、图表、记账、攒钱、我的. It uses warm paper, forest green, wood/orange accents, 48dp-equivalent touch targets, accessible labels/focus states, and reduced-motion support. The record screen demonstrates the progressive category-first flow with expense and income WebP categories.
- The converted category assets are in `app/src/main/res/drawable-nodpi/` and should remain available to the Android XML implementation. The design board references the same resource filenames.
- The editable Figma board is in the user's file [Figma Basics](https://www.figma.com/design/LAcQw2liQ2kj2eGcDNUEF0/Figma-basics?node-id=2603-22), section node `2603:22` (`PKMAPP · HTML视觉稿`). Local PKMAPP color and layout variables are defined in that section, and the PNG transfer assets are placed into the phone frames so the character illustrations render in Figma.
- TDD evidence: `node design/pkmapp-visual-redesign.test.js` passes both structural/accessibility checks. Browser verification confirmed six screens, 18 expense categories, the clean default category-selection state, and the local page has no HTML-to-design capture dependency after the Figma fallback.
- The two HTML-to-Figma capture attempts remained pending after repeated polling and showed a capture timeout in the browser. The manual Figma board is therefore the editable source of truth for this phase; no capture output was used as a completed result.
- HTML follow-up (not synced to Figma): the Home screen is now a single `主页面.png` forest-den image from `宝可梦账本素材\装饰素材\主页面背景`, with pointer dragging and left/right keyboard/button nudges changing its background position; Home no longer contains shortcut cards or recent-transaction functions.
- HTML follow-up (not synced to Figma): after a category is selected, the Record screen keeps the category grid visible and reveals a forest calculator directly below it. The calculator supports decimal input, addition/subtraction/multiplication/division, clear/backspace, result evaluation and amount-field writeback.
- Latest HTML verification: four Node checks pass; browser verification confirmed `购物` → calculator visible → `2 + 3 =` writes `5.00`, panorama nudge moves `--panorama-x` from `0px` to `72px`, and browser error logs are empty. Figma remains intentionally unchanged for this follow-up.
- Latest HTML refinement: the calculator now follows the supplied reference's four-column keypad order (`7 8 9 AC / 4 5 6 + / 1 2 3 − / . 0 backspace =`) with a flat paper surface; the date selectors remain below the calculator so date entry is still retained.
- Latest HTML refinement: the Home panorama is edge-to-edge inside the phone viewport with no card margin, radius or shadow around the forest image. Figma is intentionally not updated until the user confirms this direction.
- Latest HTML refinement: the Record screen now places the retained 发生日期 selectors above the calculator, uses warm paper surfaces instead of a white calculator background, and removes the redundant 返回选择分类 action. Figma remains intentionally unchanged.
- Latest HTML refinement: the Charts screen centers the 支出 / 收入 toggle and places the supplied 草苗龟 illustration on the left and 木守宫 illustration on the right, sized as compact decorative companions. Figma remains intentionally unchanged.
- Latest HTML refinement: the Profile screen now presents a borderless forest identity Hero using the existing sleeping Bulbasaur art, a clearer 2+1 asset hierarchy, flat grouped management lists, and a smaller 森林档案 heading. No new Profile actions or assets were added; Figma remains intentionally unchanged.

## Next Steps

1. Make the record-page instrumentation test select a category before asserting the progressively revealed entry controls, then run it on an emulator/phone.
2. Implement the real exchange-rate and borrowing-statistics data flows after the frontend layout is confirmed.
3. Replace the transaction `LinearLayout` with a date-grouped `RecyclerView`, then add device-level end-to-end verification. If a device is available in WSL, run:

```bash
./gradlew connectedDebugAndroidTest
```

4. Update this document at every completed feature phase before pushing, recording commits, tests, environment and remaining work.
