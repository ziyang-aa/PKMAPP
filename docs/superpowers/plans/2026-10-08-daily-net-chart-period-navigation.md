# Daily Net and Chart Period Navigation Implementation Plan

> **For agentic workers:** Execute inline in this session, one task at a time, keeping existing unrelated worktree changes out of each commit.

**Goal:** Show per-day net income in transaction history and make chart summaries, rankings and point cards respond to the selected week, month or year.

**Architecture:** Extend `DetailsListItem` to aggregate each date's income minus expenses and expose it to the date-header row. Use one selected chart date in `ChartsFragment` for all period calculations, with week navigation, month/year selection, and selected-period category aggregation in `ChartDataCalculator`; make point selection toggle in `TrendChartView`.

**Tech Stack:** Android Java, XML layouts/ViewBinding, AndroidX, Material Components, JUnit 4, Gradle Wrapper, Android emulator.

---

### Task 1: Add daily net values to date groups

**Files:**
- Modify: `app/src/main/java/com/example/pkmapp/details/DetailsListItem.java`
- Modify: `app/src/main/java/com/example/pkmapp/details/TransactionListAdapter.java`
- Modify: `app/src/main/res/layout/item_transaction_date_header.xml` (inspect actual header resource before implementation; if the header is programmatically built, change only its existing binder)
- Test: `app/src/test/java/com/example/pkmapp/details/DetailsListItemTest.java`

- [x] Add JUnit cases with same-day income/expense transactions for positive, negative and zero net amounts. Assert header model exposes `incomeCents - expenseCents`.
- [x] Run `./gradlew.bat testDebugUnitTest --tests com.example.pkmapp.details.DetailsListItemTest --no-daemon --console=plain`; confirm the new assertions fail because date headers have no net value.
- [x] Add daily net cents to date-header items and aggregate by local calendar day while walking the already date-sorted transactions. Add a small right-aligned value to the date-header row using green for positive and red for negative values; zero displays as “收支平衡”. Format with an explicit sign and the existing `MoneyParser`.
- [x] Re-run the targeted test and `git diff --check`.

### Task 2: Make chart aggregation use an explicit selected date

**Files:**
- Modify: `app/src/main/java/com/example/pkmapp/charts/ChartDataCalculator.java`
- Modify: `app/src/main/java/com/example/pkmapp/charts/ChartsFragment.java`
- Test: `app/src/test/java/com/example/pkmapp/charts/ChartDataCalculatorTest.java`

- [ ] Add tests for selected prior week and selected month/year buckets, including transactions on both sides of period boundaries; add category amount and count assertions for the same selected period.
- [ ] Run `./gradlew.bat testDebugUnitTest --tests com.example.pkmapp.charts.ChartDataCalculatorTest --no-daemon --console=plain`; confirm the newly required category aggregation API is absent or returns no selected-period result.
- [ ] Add a category aggregation helper that accepts transactions, transaction type, reference date and period, and returns category amount/count pairs filtered by `startOfPeriod` and `endOfPeriod`. Use the existing period-boundary helpers and do not duplicate calendar math in the Fragment.
- [ ] Replace the fixed `Calendar.getInstance()` reference with a selected chart date. Feed it to buckets, comparison totals, labels and category aggregation. Format period heading and ranking count labels from the selected period.
- [ ] Re-run the targeted calculator tests and `git diff --check`.

### Task 3: Add period controls and endpoint dates

**Files:**
- Modify: `app/src/main/res/layout/fragment_charts.xml`
- Modify: `app/src/main/java/com/example/pkmapp/charts/ChartsFragment.java`
- Modify: `app/src/main/java/com/example/pkmapp/charts/TrendChartView.java`
- Test: `app/src/test/java/com/example/pkmapp/charts/ChartDataCalculatorTest.java`

- [ ] Add a calendar-boundary test that moves a selected date backward and forward by one week and verifies Monday/Sunday bucket dates, including month and year rollover.
- [ ] Run the targeted calculator test and confirm the new week-navigation boundary assertions fail before adding date movement helpers.
- [ ] Add previous/next week icon buttons and a selected-period label to the chart controls. Show week arrows only in week mode; make the label open a month/year picker in month mode or a year picker in year mode. Keep one selected `Calendar` as the source for all chart state, and restore it with Fragment saved state.
- [ ] Show actual selected year/month (or year) in the trend heading. Draw the week x-axis as weekday plus month/day on separate lines at Monday and Sunday, keeping the chart plot clear of those labels.
- [ ] Re-run the targeted test, then build with `./gradlew.bat assembleDebug --no-daemon --console=plain`.

### Task 4: Toggle trend point cards on repeated taps

**Files:**
- Modify: `app/src/main/java/com/example/pkmapp/charts/TrendChartView.java`
- Test: `app/src/androidTest/java/com/example/pkmapp/TrendChartViewTest.java` (create only if the current instrumentation setup can exercise Canvas touch behavior reliably; otherwise verify the hit toggle directly on emulator)

- [ ] Define the expected point behavior: first tap selects the point, a second tap on the same point clears selection, and a tap on another point selects it instead.
- [ ] Verify the existing behavior leaves a point selected after repeated taps.
- [ ] Apply the toggle on completed tap, retain selected tooltip content as date/total/count/type, and leave empty buckets unselectable.
- [ ] Build and run the focused chart test if added.

### Task 5: Emulator acceptance

**Files:**
- Build artifact: `app/build/outputs/apk/debug/app-debug.apk`

- [ ] Run `./gradlew.bat testDebugUnitTest assembleDebug --no-daemon --console=plain` and confirm success.
- [ ] Install the APK on `emulator-5554`, open the Details page, and verify days with positive, negative and zero net are represented correctly without overlapping date text.
- [ ] Open Charts and verify rankings/count labels change for week, month and year; navigate across a week/month/year boundary; verify selected period headings and Monday/Sunday dates.
- [ ] Tap a nonzero chart point twice to confirm the card opens then closes; tap a different point and confirm its date and totals replace the prior card.
- [ ] Capture final emulator screenshots for Details and Charts and inspect text fit, tap targets, clipping and scroll behavior.
