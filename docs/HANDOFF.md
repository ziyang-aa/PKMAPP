# PKMAPP Handoff

## Workspace

- Current development and build workspace: `D:\pkmAPP` (Windows Android Studio project).
- Historical WSL source path: `/home/uug/projects/pkmAPP`; do not switch back unless the user asks.
- Git remote: `git@github.com:ziyang-aa/PKMAPP.git`.
- Branch: `main`.
- Latest committed Android checkpoint: `168cf12` (`feat: add chart trends and rankings`). The current visual-design checkpoint adds the HTML visual board, converted category WebPs, and an editable Figma board.
- This is a runnable local preview checkpoint. Do not commit or push it unless the user explicitly asks.

## Working Agreement

- Run development and verification commands from `D:\pkmAPP`.
- The user runs the APK in Windows Android Studio or an Android emulator.
- The user has explicitly overridden the historical WSL workflow: use `D:\pkmAPP` directly for development and builds. The WSL paths below are historical reference only.

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
- Record entry now starts with a four-column category grid; selecting a category reveals the amount, note, calculator and save area. Date selection uses three independent dropdowns for year/month/day, defaults to the system calendar date, and the page uses a faint Treecko decoration. Category buttons use the normalized user-provided artwork.
- Details now uses a green monthly balance card followed by lightweight exchange-rate and borrowing-statistics cards, matching the HTML hierarchy while clearly marking their data functionality as a later phase.
- Details now supports switching between current in-memory ledgers and creating a named ledger that is immediately selected. The summary and transaction list refresh from the active ledger.
- The record-page Android test now follows the intended sequence: choose a category first, then verify the entry controls are visible. It compiles, but still needs an emulator/phone run because no WSL device is connected.
- The five feature pages now use the user-provided transparent WebP “open door” illustration as a 180dp-wide clickable return-to-home entrance, replacing the previous text buttons.
- The return-door WebP is alpha-cropped and shown in a compact 120dp-wide touch target; the details page also has a wheel-style year/month selector that filters its monthly balance and transaction list together.
- Charts now has frontend income/expense and week/month/year controls, a native drawn trend line and a category ranking. It reads and aggregates the current in-memory ledger transactions by selected type and period; trend points, totals, comparison text and ranking now share the same live data.
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
- Latest HTML refinement: the five non-home mobile pages now use the supplied `宝可梦账本素材\装饰素材\5个页面的固定背景\背景.png` as a fixed phone-layer background. The Home panorama remains independent, and the new background stays in place while each page's content scrolls.
- Latest HTML-only follow-up (not committed or pushed): the Profile screen now has a single 每日签到 entry and a bottom-sheet 打卡记录 dialog. The preview enforces one sign-in per day, does not support backfill, persists the current preview state in local storage, and marks checked calendar dates with green numbered cells. Level rules are capped at Lv.8 森林贤者 after 365 cumulative valid check-ins.
- Latest HTML-only follow-up (not committed or pushed): the 打卡记录 dialog now restores month navigation. Previous / next month controls regenerate the seven-column calendar while keeping checked dates green and numbered.

## Android Visual Implementation (2026-09-07, D:\\pkmAPP)

- The Android implementation now uses the supplied `装饰素材\\主页面背景\\主页面.png` for the full-width horizontally pannable Home panorama. The five inner pages use `装饰素材\\5个页面的固定背景\\背景.png` as a fixed backdrop behind scrolling content.
- Charts now centers the 支出 / 收入 switch between the supplied `草苗龟与储蓄罐插画生成.png` and `儿童插画：持放大镜观察金币.png` illustrations.
- Record now uses the converted category WebP assets, keeps category selection visible, and reveals the retained 发生日期 plus an inline four-column calculator after a category is selected. The redundant return-to-category action is not present.
- Profile now presents the forest archive layout and a bottom-sheet daily check-in calendar. Check-in state is stored locally, allows only the current day once, does not expose backfill actions, renders checked dates as green date numbers, supports month navigation, and caps progression at Lv.8 森林贤者.
- TDD evidence: the new `CheckInProgressTest` first failed on the missing current/requested-date API, then passed after implementation. `D:\\pkmAPP\\.\\gradlew.bat testDebugUnitTest --no-daemon --console=plain` and `D:\\pkmAPP\\.\\gradlew.bat assembleDebug --no-daemon --console=plain` both pass under the installed JDK 25 runtime. No Git commit or push was made.
- Visual follow-up: calendar date cells now use plain date text with an explicit green background for checked days, avoiding MaterialButton's default background tint. The bottom navigation now has a rounded paper-card container, in-card spacing, and a light-green rounded selected capsule to match the supplied design reference.
- Chart follow-up: the Android chart page now follows the HTML card composition with centered income/expense controls, soft selected capsules, a filled smooth trend chart, and illustrated ranking rows. The supplied `装饰素材\\记账类型按钮` PNGs were copied into new `type_*` Android resources and are used by the record and chart pages.
- Runtime fix: removed the direct `MaterialButton` background override that caused `MaterialButtonToggleGroup` to crash during `MainActivity` inflation. Selected states now use `backgroundTint` selectors. `testDebugUnitTest assembleDebug` passes under JDK 25; after ADB was configured, the app was installed and launched on the real device.
- Record-page visual follow-up: compared against the HTML calculator rules, category items now use consistent 116dp cards with 64dp normalized/crop-fitted artwork and a selected border. The entry state no longer wraps the whole form in an extra card or shows a redundant calculator toggle. The calculator now has a paper-deep panel, expression/header row, light note field, separated four-column keys, and orange equals key. The updated debug APK was installed and launched successfully on real device `10AE632GDX000H9` before the latest follow-up.

## Android Details Visual Follow-up (2026-09-08, `D:\pkmAPP`)

- The Details page now follows the HTML composition: compact door/title header, full-width paper ledger selector, separate month and 本月 controls, a green monthly balance card, two lightweight 森林小工具 cards, and a dated transaction paper list.
- Transaction rows now load the normalized category artwork through `CategoryIconResolver`, so labels such as 餐饮、交通、网购 resolve to the user-provided `norm_type_expense_*.png` / `norm_type_income_*.png` assets instead of placeholder icons. Unknown labels use the custom fallback.
- Added a 本月 action to restore the selected month to the system calendar month. The existing month picker still filters the balance and transaction list together.
- Verification completed without committing or pushing: `D:\pkmAPP\.\gradlew.bat testDebugUnitTest --tests com.example.pkmapp.record.CategoryIconResolverTest --tests com.example.pkmapp.record.RecordDateStateTest --no-daemon --console=plain` and `D:\pkmAPP\.\gradlew.bat assembleDebug --no-daemon --console=plain` both passed. The APK was installed on `emulator-5554`, and a screenshot confirmed the Details page and three category illustrations rendered correctly.

## Android Profile and Check-in Visual Follow-up (2026-09-08, `D:\pkmAPP`)

- The Profile page now follows the HTML forest-file layout: a green identity Hero with the sleeping Bulbasaur illustration, a flat forest asset overview, a compact daily check-in entry, borderless grouped management rows, and a smaller forest-themed footer note.
- The check-in UI is now an XML-backed paper bottom sheet matching the HTML dialog hierarchy: `DAILY FOREST LOG`, level/progress panel, Monday-first calendar with neighboring-month dates, green numbered checked dates, legend, and a green action button that becomes disabled after today's check-in. Month navigation and the no-backfill/once-per-day rules remain unchanged.
- Added small forest-themed vector icons and profile/check-in drawables under `app/src/main/res/drawable/`; no Kotlin or Compose code was introduced. The latest APK was installed on `emulator-5554` and visually inspected on both the Profile page and the open check-in dialog.
- Latest record-page follow-up (not committed or pushed): the expense/income switch is now a paper-deep split control with a left rounded edge and right straight edge for 支出, mirrored for 收入, matching the supplied design reference. A record owns exactly one category: selecting another item clears the previous selected state. The selected area now shows an illustration plus `已选择：分类名`; 发生日期 is shown above the calculator as three single-line year/month/day boxes, and selecting a category posts a smooth scroll toward the calculator. Local `assembleDebug` passes after this change; the phone is currently disconnected, so the final visual check is pending reconnection.
- Date dropdown follow-up: the three date boxes now open separate paper-styled dropdowns—year, month and day are selected independently. The day list is limited to the valid number of days in the currently selected month; switching month/year clamps an out-of-range day safely. `RecordDateStateTest` and `assembleDebug` pass, and the dropdown was visually confirmed in the software-rendered emulator. No commit or push was made.

## Android Charts Data and Point Tooltip Follow-up (2026-09-08, `D:\pkmAPP`)

- `ChartDataCalculator` now builds week/month/year amount buckets from the active in-memory ledger for the selected 支出 or 收入 type. The chart total, comparison text and top-three category ranking are filtered to the same period and transaction type.
- Added `bucketCounts` with a JVM regression test so each plotted bucket can report its transaction count. `TrendChartView` keeps the paper storybook presentation while drawing dynamic labels, non-zero points and a clickable point tooltip.
- Tapping a non-zero curve node shows a compact paper tooltip containing the exact date, formatted amount and the bucket count, for example `9月3日` and `¥88.80 · 1 笔支出`. Zero-value buckets remain part of the curve but do not show selectable dots.
- Verification completed without committing or pushing: `D:\pkmAPP\.\gradlew.bat testDebugUnitTest --tests com.example.pkmapp.charts.ChartDataCalculatorTest --no-daemon --console=plain` and `D:\pkmAPP\.\gradlew.bat assembleDebug --no-daemon --console=plain` both returned `BUILD SUCCESSFUL`. The APK was installed on `emulator-5554`; visual inspection confirmed the dynamic monthly curve and point tooltip. Generated `build-charts-*.png` screenshots were left in place and are not staged.

## Android Fixed Forest Background Follow-up (2026-09-08, `D:\pkmAPP`)

- The five feature pages—Details, Charts, Record, Savings and Profile—continue to use the user-provided `D:\pkmAPP\宝可梦账本素材\装饰素材\5个页面的固定背景\背景.png`, copied as `app/src/main/res/drawable-nodpi/bg_fixed_forest.png`.
- Removed the extra high-opacity cream overlay from `bg_fixed_forest_backdrop.xml`, so the watercolor leaf paper is visible as the actual page background. Each page keeps the backdrop on its root `ScrollView`/`NestedScrollView`; only the content moves when scrolling, while the background stays fixed to the viewport.
- `D:\pkmAPP\.\gradlew.bat assembleDebug --no-daemon --console=plain` returned `BUILD SUCCESSFUL`. The APK was installed on `emulator-5554`; top and scrolled Charts screenshots confirmed the fixed background behavior. No commit or push was made.

## Android Record Save and Savings Goals Follow-up (2026-09-08, `D:\pkmAPP`)

- Fixed the record save path so a calculator expression is accepted even when the hidden amount field has not been populated by pressing `=`. `RecordAmountResolver` first prefers an explicit amount field and otherwise evaluates the current calculator expression.
- Saving a valid record now writes through `InMemoryLedgerRepository`, which immediately updates the active ledger and its listeners, then shows a paper-style `收录成功` dialog. The dialog offers `继续记一笔` (reset the form) and `查看明细` (open the refreshed Details page).
- Implemented the Savings page with a forest hero, two seeded local goals, progress bars, remaining amounts, goal creation and deposit dialogs. `SavingsGoalStore` persists goals and deposits in `SharedPreferences`; deposits cap at the target and show a completed state.
- Verification: `D:\pkmAPP\.\gradlew.bat testDebugUnitTest --tests com.example.pkmapp.record.RecordAmountResolverTest --tests com.example.pkmapp.savings.SavingsGoalTest assembleDebug --no-daemon --console=plain` returned `BUILD SUCCESSFUL`. The APK installed successfully on `emulator-5554`.
- Emulator checks: entered a category and `5` without pressing `=`, tapped `收进森林账本`, confirmed `收录成功`, then confirmed Details contained `-¥5.00`. Deposited `100` into `秋日旅行`, confirmed `已存 ¥2620.00` / `还差 ¥3380.00`, force-stopped and relaunched the app, and confirmed the same goal balance persisted. Recent logcat contained no `FATAL EXCEPTION`.
- This phase was completed locally without committing or pushing. The generated `build-savings-page.png` and `build-record-success-dialog.png` screenshots remain untracked for visual review; do not batch-stage them.

## Android Chart Character Cutout Follow-up (2026-09-08, `D:\pkmAPP`)

- Replaced the chart-side character references with the transparent cutouts supplied in `D:\pkmAPP\宝可梦账本素材\装饰素材\支持与收入卡通人物\支出 .png` and `收入.png`.
- Added safe Android resource copies `app/src/main/res/drawable-nodpi/chart_expense_character.png` and `chart_income_character.png`, then updated `fragment_charts.xml`. The old untracked `turtwig_chart.png` and `treecko_chart.png` were left untouched.
- Updated `design/pkmapp-visual-redesign.html` to reference the same cutout source directory; Figma was not changed.
- Verification: `D:\pkmAPP\.\gradlew.bat assembleDebug --no-daemon --console=plain` returned `BUILD SUCCESSFUL`; the APK installed on `emulator-5554` and the chart screenshot confirmed both character images render without white rectangles. No `FATAL EXCEPTION` appeared in the sampled logcat. No commit or push was made.

## Android Details HTML-Approved Visual Follow-up (2026-09-08, `D:\pkmAPP`)

- After the user approved the HTML visual pass, Android Details now uses the same compact hierarchy: ledger, month and 本月 controls share one row; the balance amount is the visual anchor; 森林小工具 cards are reduced to lightweight two-column entries; the transaction list no longer has an outer paper card and remains grouped by date.
- Date headers now show the calendar date with relative context (`9月6日 · 今天`, `9月5日 · 昨天`); transaction rows use smaller normalized category art, emphasize the signed amount, and place the lighter 收入/支出 label underneath.
- Money display now uses thousands separators while retaining two decimal places, so ledger totals and transaction amounts follow the HTML copy style such as `¥6,416.40`.
- TDD evidence: `DetailsListItemTest` first failed on the missing date-plus-relative header, then passed after implementation; `MoneyParserTest` first failed on missing thousands grouping, then passed after implementation.
- Verification without committing or pushing: `D:\pkmAPP\.\gradlew.bat testDebugUnitTest assembleDebug --no-daemon --console=plain` and `node design/pkmapp-visual-redesign.test.js` passed. The APK was installed and launched on `emulator-5554`; `build-details-native-grouped-ready.png` shows the updated Details page, and the last 300 logcat lines contained no `FATAL EXCEPTION`.

## Android Details Tools and Borrowing Follow-up (2026-09-08, `D:\pkmAPP`)

- Details ledger and month controls now use paper-style custom selectors instead of the old Android `NumberPicker`/single-choice dialogs. Ledger switching, new-ledger creation, month navigation and 本月 reset remain functional.
- Added the independent Exchange page. It supports CNY/USD/EUR/JPY/HKD, amount conversion, currency swap, loading/error states and the last successful local cache. The service requests Frankfurter's public latest-rate endpoint (`/v2/rate/{base}/{quote}`) off the main thread; no API key is stored.
- Added the independent Borrowing page. 借出/借入 both require amount, person and date; note is optional. Records are stored as JSON in `SharedPreferences`, summarized by direction and rendered in descending local-date groups.
- Record now includes the income-side 借入 category. Saving 借出 or 借入 opens a paper prompt asking whether to continue into the borrowing tool; 去填写 carries over direction, amount, date and source transaction ID. The normal categories retain the existing 收录成功 dialog.
- No new cutout work is required for this phase. The feature pages continue to use `D:\pkmAPP\宝可梦账本素材\装饰素材\5个页面的固定背景\背景.png`; existing category and character assets are reused.
- Verification completed locally without committing or pushing: `D:\pkmAPP\.\gradlew.bat testDebugUnitTest assembleDebug --no-daemon --console=plain` returned `BUILD SUCCESSFUL`; `node design/pkmapp-visual-redesign.test.js` returned four `PASS` lines. The APK installed successfully on `emulator-5554`.
- Emulator checks covered the modern Details ledger/month sheets, Exchange page, Record → 借出 → borrowing prompt → prefilled Borrowing form, and saving a dated `借出给 XiaoLin` record showing `¥5.00`. The post-install launch log had no `FATAL EXCEPTION`; the SDK XML version 4 message remained a non-fatal tooling warning.
- This phase remains local only. Do not stage the generated `build-tools-stage-*.png` screenshots or unrelated existing worktree files, and do not commit/push until the user gives that instruction.

## Android Borrowing Calendar and Ledger Sync Follow-up (2026-09-08, `D:\pkmAPP`)

- Replaced the borrowing page's three-column date `PopupWindow` with an XML-backed forest-paper `BottomSheetDialog`: selected date header, Monday-first six-week grid, previous/next month navigation, muted adjacent-month dates, green circular selected date, and 取消/确定 actions. The quick choices 今天、昨天、前天 are not present.
- The date picker starts from the system date, keeps changes as a draft until 确定, and updates the borrowing form only after confirmation. Selecting a date from an adjacent month moves the visible month with it.
- Standalone entries in the borrowing tool now also call `InMemoryLedgerRepository.addTransaction`: 借出 maps to `EXPENSE / 借出`, 借入 maps to `INCOME / 借入`; the counterparty and optional note are preserved in the main ledger note. Records opened from the Record page carry a source transaction id and skip this insertion to prevent duplicates.
- Added pure `BorrowingCalendarModel` and `BorrowingTransactionSync` rules with JVM tests. `D:\pkmAPP\.\gradlew.bat testDebugUnitTest assembleDebug --no-daemon --console=plain` returned `BUILD SUCCESSFUL` under the installed JDK 25 runtime. The APK was installed on `emulator-5554`; the calendar sheet, circular selected date, month navigation and date confirmation were visually checked. Generated `build-borrowing-calendar*.png` screenshots remain untracked.
- This phase remains local only. No Git commit or push was made; do not stage unrelated existing worktree files.

## Android Details Month Selector Visual Follow-up (2026-09-08, `D:\pkmAPP`)

- Reworked `dialog_details_month_picker.xml` to match the Record page's dropdown language: year and month remain independently scrollable, but the individual choices no longer have card/box backgrounds.
- Increased the selector typography to 18sp with 52dp rows, enlarged the 年份/月份 labels and title, and kept the current year/month as forest-green bold text only. Existing cancel, close, confirm, scrolling and selected-month filtering behavior remain unchanged.
- Added `DetailsPickerLabels.pickerOptionText` with a JVM regression test so the zero-based month values still render as 1月 through 12月 while years keep their 年 suffix. The red test first failed because the formatter was absent, then passed after implementation.
- Verification completed locally: `D:\pkmAPP\.\gradlew.bat testDebugUnitTest assembleDebug --no-daemon --console=plain` returned `BUILD SUCCESSFUL`; the APK was installed on `emulator-5554` and the month sheet was visually checked. `build-details-month-dropdown-text.png` remains untracked. No commit or push was made.

## Android Savings Hero and Charts Ranking Follow-up (2026-09-08, `D:\pkmAPP`)

- The Savings page's green Hero card now has a tighter storybook composition: 232dp height, larger title and supporting copy, a 176dp Turtwig illustration, and a soft green circular glow behind the character. Savings goal cards and data behavior were not changed.
- The Charts page's 分类排行榜 no longer uses the outer `PaperCard` background, border or inset card padding. Ranking rows now sit directly on the fixed forest-paper background and reuse the Details transaction list's transparent row treatment with subtle bottom separators; dynamic sorting, category art, counts, amounts and existing interactions remain unchanged.
- `D:\pkmAPP\.\gradlew.bat testDebugUnitTest assembleDebug --no-daemon --console=plain` returned `BUILD SUCCESSFUL`; the APK was installed on `emulator-5554`. Screenshots `build-after-hero.png` and `build-after-ranking.png` visually confirmed both layouts. The SDK XML version 4 message is a non-fatal tooling warning.
- This phase remains local only. No Git commit or push was made; do not batch-stage the existing dirty worktree or generated screenshots.

## Android Profile Identity Hero Visual Correction (2026-09-08, `D:\pkmAPP`)

- Corrected the green forest identity Hero on the Profile page, which was the card omitted from the previous visual pass. The card is now 232dp high, the sleeping Bulbasaur art is 200dp and slightly lowered, and the background includes a large pale-green glow plus a smaller upper-right circle to match the supplied design reference.
- Only `fragment_profile.xml` and `bg_profile_hero.xml` were changed; asset overview, check-in, management rows and data behavior were left untouched.
- The APK was rebuilt and installed on `emulator-5554`; `build-after-profile-hero-final.png` was visually inspected. `D:\pkmAPP\.\gradlew.bat testDebugUnitTest assembleDebug --no-daemon --console=plain` returned `BUILD SUCCESSFUL`; the SDK XML version 4 message remains a non-fatal tooling warning.
- This phase remains local only. No Git commit or push was made; do not batch-stage the existing dirty worktree or generated screenshots.

## Android Profile Typography, Floating Record Button and Launcher Icon (2026-09-08, `D:\pkmAPP`)

- Enlarged the Profile forest identity Hero typography and level pill to reduce unused space while keeping the 232dp Hero, sleeping Bulbasaur art, and pale-green forest glow treatment.
- Increased the bottom navigation height to 88dp and made the Record action a 60dp double-ring paper/forest-green plus button raised 8dp above the navigation surface. The circle is rendered inside the icon so the MaterialButton background contract remains intact; tapping it still opens `记一笔`.
- Replaced the Android launcher adaptive-icon foreground with `D:\pkmAPP\宝可梦账本素材\装饰素材\重新生成版\妙蛙种子记账插画生成.png` and changed the adaptive background from the Android template grid to a pale forest-paper color. Existing legacy density resources were not deleted.
- `D:\pkmAPP\.\gradlew.bat testDebugUnitTest assembleDebug --no-daemon --console=plain` returned `BUILD SUCCESSFUL`; the APK was installed on `emulator-5554`. After clearing logcat and force-restarting the app, `FRESH_LOGCAT_FATAL: none` and `MainActivity` resumed. Visual checks used `build-after-profile-hero-final.png`, `build-after-record-nav-floating.png`, `build-record-nav-action.png` and `build-app-icon-home.png`.
- This phase remains local only. No Git commit or push was made; do not batch-stage the existing dirty worktree or generated screenshots. The SDK XML version 4 message is a non-fatal tooling warning.

## Android App Name and Home Forest Nook Background (2026-09-08, `D:\pkmAPP`)

- Changed the user-facing app name from `pkmAPP` to `芽叶记` through `@string/app_name`; package name and Java package structure remain unchanged.
- Added `app/src/main/res/drawable-nodpi/home_panorama_replacement.png` from `D:\pkmAPP\宝可梦账本素材\装饰素材\主页面背景\主页面素材替换版.png`. The original `home_panorama.png` was preserved. The existing horizontal panorama behavior remains, with the new artwork filling the home scene.
- The replacement background already contains the three forest partners, so the old independent Bulbasaur/Treecko/Turtwig overlays were removed to avoid duplicated characters. Added a light text group on the home scene: `FOREST NOTEBOOK`, `芽叶记`, `把每一笔生活，留在森林里`, and a small daily-goal line. The copy now uses warm white/light cream text with a wood-brown shadow for contrast and remains a typography-only overlay rather than another functional card.
- The APK was rebuilt and installed on `emulator-5554`; after launch, cleared-logcat verification returned `FRESH_LOGCAT_FATAL: none`. `build-home-new-background.png` visually confirms the initial scene and `build-home-new-background-panned.png` confirms horizontal panning still reveals the continuous artwork.
- This phase remains local only. No Git commit or push was made; do not batch-stage the existing dirty worktree or generated screenshots. The SDK XML version 4 message is a non-fatal tooling warning.

## Android Home Copy Contrast and App Name Correction (2026-09-08, `D:\pkmAPP`)

- Corrected the user-facing name from “芽叶计” to “芽叶记”.
- Changed the home copy from forest-green/wood-brown to warm white/light cream with the semantic `home_text_shadow` wood-brown shadow, so it remains readable over the treehouse wood grain and leaves without changing its position or interaction.
- Rebuilt, installed and launched the APK; cleared-logcat verification returned `FRESH_LOGCAT_FATAL: none`. `build-home-text-contrast.png` visually confirms the corrected title and supporting copy.
- This phase remains local only. No Git commit or push was made; do not batch-stage the existing dirty worktree or generated screenshots.

## Android Home Panorama Drag Range Follow-up (2026-09-08, `D:\pkmAPP`)

- Expanded the Home panorama scene from three viewport widths to four while keeping the main ImageView on `centerCrop`, so the artwork keeps its original proportions and horizontal dragging reaches much closer to the supplied image boundaries without squeezing the scene.
- First entry now starts at the horizontal center of the panorama; saved scroll positions continue to be restored after recreation.
- Verification: `D:\pkmAPP\.\gradlew.bat testDebugUnitTest --no-daemon --console=plain` and `D:\pkmAPP\.\gradlew.bat assembleDebug --no-daemon --console=plain` both returned `BUILD SUCCESSFUL`. The APK was installed on `emulator-5554`; visual checks confirmed the initial left edge and the right edge after repeated horizontal swipes. No `FATAL EXCEPTION` appeared in the sampled logcat.
- This phase remains local only. No Git commit or push was made.

## Android Asset Management and Asset Card Visual Follow-up (2026-09-09, `D:\pkmAPP`)

- Replaced the Profile page's demo asset numbers with zero-backed user state. New ledgers start with `¥0.00` initial assets and liabilities; production demo transaction/seed data was removed from `InMemoryLedgerRepository`.
- Added `AssetSnapshot` and per-ledger `AssetStore`. Current assets equal starting assets plus the active ledger's income/expense net flow; net worth equals current assets minus liabilities. Profile listens to ledger changes and refreshes these values immediately; starting values persist in `SharedPreferences`.
- Added the “管理我的资产” entry and a warm paper forest bottom sheet showing current assets, net worth and liabilities, with editable initial assets/liabilities. Zero and blank values are accepted; invalid values are reported on the corresponding field.
- Updated the asset card to a pale-green gradient and added transparent `asset_treasure_chest.png` and `asset_coin_stack.png` copied from `D:\pkmAPP\宝可梦账本素材\装饰素材\重新生成版`. Per the latest user feedback, the plaque and overlaid “森林宝藏” text were removed from the asset card; the copied plaque file remains untouched but is no longer referenced by the layout. The presentation stays within the approved storybook language and avoids old Android dialog styling.
- Targeted verification: `D:\pkmAPP\.\gradlew.bat testDebugUnitTest --tests com.example.pkmapp.data.AssetSnapshotTest --tests com.example.pkmapp.record.MoneyParserTest --tests com.example.pkmapp.data.InMemoryLedgerRepositoryTest assembleDebug --no-daemon --console=plain` returned `BUILD SUCCESSFUL`. Full test execution still reports two unrelated existing `PanoramaSizingTest` failures because the implementation currently uses a 3x panorama multiplier while those tests expect 4x; the homepage was not changed in this phase.
- The APK was reinstalled on `emulator-5554`; the zero-value asset card and management sheet were previously visually checked, while the final gradient/plaque resize was build-verified only because another task was using the emulator. No new `FATAL EXCEPTION` appeared in the sampled logcat.
- This phase remains local only. No Git commit or push was made; do not stage unrelated worktree files or generated screenshots.

## Next Steps

1. Let the user visually inspect the latest APK and confirm the Details tools direction, especially the exchange cache/error copy and borrowing form density.
2. Add deeper device-level checks for 借入、暂不统计 and app-restart persistence when the user wants a broader acceptance pass.
3. Consider replacing the current in-memory transaction repository with Room only after the visual/interaction direction is accepted; the borrowing records already have local JSON persistence.
4. When the user explicitly requests delivery, precisely stage only the relevant source/resources/tests/docs, then commit and push to `git@github.com:ziyang-aa/PKMAPP.git`.

## Android Ledger Persistence and Exchange Refresh (2026-09-09)

- Main ledgers and transactions now persist in local `SharedPreferences` as JSON. `MainActivity` initializes the persistent repository before creating feature fragments; creating/switching ledgers and adding transactions immediately save the current state, including the selected ledger.
- Added `LedgerCodec` and a persistence-backed repository regression test that recreates the repository and confirms the saved transaction remains available. Test-only repositories remain in memory.
- Exchange-rate requests now disable HTTP caching and send `Cache-Control: no-cache`, `Pragma: no-cache` and `Accept: application/json`. Opening or resuming the exchange page, changing either currency and pressing refresh all request the latest available value from Frankfurter; while the page is visible it refreshes every five minutes. Failed requests still use the last successful cached snapshot and clearly retain its date; successful responses display the returned rate date and the local sync time.
- Verification: `D:\pkmAPP\.\gradlew.bat testDebugUnitTest --tests com.example.pkmapp.exchange.* assembleDebug --no-daemon --console=plain` returned `BUILD SUCCESSFUL`. The emulator displayed `刚刚同步 · 04:10` and `1 CNY ≈ 0.14908 USD · 2026-09-09` after opening and manually refreshing the exchange page; the visual screen showed no error state. The SDK XML version 4 message remained a non-fatal tooling warning.
- This phase remains local only. No Git commit or push was made.
