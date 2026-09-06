# Record and Details Frontend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a Java/XML, in-memory record-entry and details workflow in which a saved income or expense immediately appears in the active ledger’s summary and dated transaction list.

**Architecture:** A process-wide `InMemoryLedgerRepository` owns immutable ledger and transaction data plus change listeners. Pure `data` and `record` logic remains Android-free and is proven with JVM tests; `RecordFragment` validates user input and calls the repository, while `DetailsFragment` subscribes and renders a `RecyclerView` with a summary header and date groups. Dialogs, date selection and feedback use Material/Android platform controls rather than adding a navigation or persistence framework.

**Tech Stack:** Java 11, XML layouts, ViewBinding, AndroidX Fragment/RecyclerView, Material Components, JUnit 4, Espresso.

**Spec:** `docs/superpowers/specs/2026-09-06-record-details-frontend-design.md`

## Global Constraints

- Keep the existing one-Activity, Fragment navigation architecture and fixed five-item bottom bar.
- Application code stays Java/XML; do not add Kotlin, Compose, Room, networking, contact permissions or cloud storage.
- Store money as non-negative integer cents; never use `double` or `float` for business amounts.
- Preserve the existing forest palette, 4/8dp spacing, Vector Drawable functional icons and WebP-only illustrations.
- All interactive icon and chip hit areas are at least 48dp and all meaningful icon-only controls have Chinese `contentDescription` text.
- This phase implements only in-memory data, record entry and details display. It deliberately excludes transaction edit/delete, real FX, borrowing data, charts, savings, profile assets and persistence.
- Do not stage or change the pre-existing line-ending-only modifications to `gradle/wrapper/gradle-wrapper.properties` or `gradlew.bat`.

---

### Task 1: Immutable Ledger Domain and In-Memory Repository

**Files:**
- Create: `app/src/main/java/com/example/pkmapp/data/TransactionType.java`
- Create: `app/src/main/java/com/example/pkmapp/data/Ledger.java`
- Create: `app/src/main/java/com/example/pkmapp/data/Transaction.java`
- Create: `app/src/main/java/com/example/pkmapp/data/MonthlyTotals.java`
- Create: `app/src/main/java/com/example/pkmapp/data/LedgerDataListener.java`
- Create: `app/src/main/java/com/example/pkmapp/data/InMemoryLedgerRepository.java`
- Create: `app/src/test/java/com/example/pkmapp/data/InMemoryLedgerRepositoryTest.java`

**Interfaces:**
- Consumes: Java collections and epoch-millisecond dates only; no Android classes.
- Produces: `TransactionType { EXPENSE, INCOME }`; immutable `Ledger(String id, String name)`; immutable `Transaction(String id, String ledgerId, TransactionType type, long amountInCents, String category, String note, long occurredAtMillis)`; `MonthlyTotals(long incomeInCents, long expenseInCents)`; and the `InMemoryLedgerRepository` singleton API listed below.

- [x] **Step 1: Write failing repository tests**

Create `InMemoryLedgerRepositoryTest.java` with deterministic UTC timestamp helpers and these assertions:

```java
@Test
public void addTransaction_notifiesListenerAndUpdatesOnlyActiveLedger() {
    InMemoryLedgerRepository repository = InMemoryLedgerRepository.createForTest();
    AtomicInteger notifications = new AtomicInteger();
    LedgerDataListener listener = notifications::incrementAndGet;
    repository.addListener(listener);

    Ledger first = repository.getCurrentLedger();
    Ledger second = repository.createLedger("旅行账本");
    repository.switchLedger(second.getId());
    repository.addTransaction(TransactionType.EXPENSE, 2_580L, "餐饮", "午饭", noonUtc(2026, 9, 6));

    assertEquals(3, notifications.get());
    assertEquals(1, repository.getTransactionsForCurrentLedger().size());
    repository.switchLedger(first.getId());
    assertTrue(repository.getTransactionsForCurrentLedger().isEmpty());
}

@Test
public void currentMonthTotals_separatesIncomeExpenseAndComputesBalance() {
    InMemoryLedgerRepository repository = InMemoryLedgerRepository.createForTest();
    repository.addTransaction(TransactionType.INCOME, 100_000L, "工资", "九月工资", noonUtc(2026, 9, 1));
    repository.addTransaction(TransactionType.EXPENSE, 2_580L, "餐饮", "午饭", noonUtc(2026, 9, 6));
    repository.addTransaction(TransactionType.EXPENSE, 3_000L, "交通", "八月车票", noonUtc(2026, 8, 30));

    MonthlyTotals totals = repository.getCurrentMonthTotals(noonUtc(2026, 9, 15));

    assertEquals(100_000L, totals.getIncomeInCents());
    assertEquals(2_580L, totals.getExpenseInCents());
    assertEquals(97_420L, totals.getBalanceInCents());
}

@Test(expected = IllegalArgumentException.class)
public void addTransaction_rejectsZeroAmount() {
    InMemoryLedgerRepository.createForTest().addTransaction(
            TransactionType.EXPENSE, 0L, "餐饮", "", noonUtc(2026, 9, 6));
}
```

- [x] **Step 2: Run the repository test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests com.example.pkmapp.data.InMemoryLedgerRepositoryTest`

Expected: compilation fails because the data package and repository do not exist.

- [x] **Step 3: Implement immutable models and repository contract**

Implement the following public API exactly. Trim names/categories/notes in constructors; reject blank ledger name/category, `amountInCents <= 0`, null transaction type and `occurredAtMillis <= 0` with `IllegalArgumentException`.

```java
public final class InMemoryLedgerRepository {
    public static InMemoryLedgerRepository getInstance();
    static InMemoryLedgerRepository createForTest();
    public List<Ledger> getLedgers();
    public Ledger getCurrentLedger();
    public Ledger createLedger(String name);
    public void switchLedger(String ledgerId);
    public Transaction addTransaction(TransactionType type, long amountInCents,
            String category, String note, long occurredAtMillis);
    public List<Transaction> getTransactionsForCurrentLedger();
    public MonthlyTotals getCurrentMonthTotals(long referenceTimeMillis);
    public void addListener(LedgerDataListener listener);
    public void removeListener(LedgerDataListener listener);
}
```

`getTransactionsForCurrentLedger()` returns an unmodifiable list sorted by `occurredAtMillis` descending and then `id` descending. `getCurrentMonthTotals` uses `Calendar` with an explicit UTC timezone to compare year/month, so minSdk 24 does not require Java time desugaring. `MonthlyTotals.getBalanceInCents()` returns income minus expense. Initialize `getInstance()` with one `生活账本` and six dated September 2026 demo records; `createForTest()` starts with one empty `生活账本`.

- [x] **Step 4: Run the repository test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests com.example.pkmapp.data.InMemoryLedgerRepositoryTest`

Expected: all repository tests pass, including validation, active-ledger isolation, ordering, listener notification and month totals.

- [x] **Step 5: Commit the domain layer**

```bash
git add app/src/main/java/com/example/pkmapp/data app/src/test/java/com/example/pkmapp/data
git commit -m "feat: add in-memory ledger repository"
```

### Task 2: Money Parsing and Calculator Engine

**Files:**
- Create: `app/src/main/java/com/example/pkmapp/record/MoneyParser.java`
- Create: `app/src/main/java/com/example/pkmapp/record/CalculatorEngine.java`
- Create: `app/src/test/java/com/example/pkmapp/record/MoneyParserTest.java`
- Create: `app/src/test/java/com/example/pkmapp/record/CalculatorEngineTest.java`

**Interfaces:**
- Consumes: Java `BigDecimal` and `RoundingMode`; no Android classes.
- Produces: `MoneyParser.parseYuanToCents(String amountText)` returning `long`; `MoneyParser.formatCents(long cents)` returning a `¥`-prefixed two-decimal string; and calculator methods `append(String token)`, `backspace()`, `clear()`, `getExpression()`, `evaluateToCents()`.

- [ ] **Step 1: Write failing money and calculator tests**

Add the following cases:

```java
@Test
public void parseYuanToCents_acceptsTwoDecimalPlaces() {
    assertEquals(1_258L, MoneyParser.parseYuanToCents("12.58"));
    assertEquals(700L, MoneyParser.parseYuanToCents("7"));
}

@Test(expected = IllegalArgumentException.class)
public void parseYuanToCents_rejectsMoreThanTwoDecimalPlaces() {
    MoneyParser.parseYuanToCents("12.345");
}

@Test
public void calculator_respectsMultiplyBeforeAdd() {
    CalculatorEngine calculator = new CalculatorEngine();
    calculator.append("1"); calculator.append("+"); calculator.append("2");
    calculator.append("×"); calculator.append("3");
    assertEquals(700L, calculator.evaluateToCents());
}

@Test(expected = ArithmeticException.class)
public void calculator_rejectsDivisionByZero() {
    CalculatorEngine calculator = new CalculatorEngine();
    calculator.append("8"); calculator.append("÷"); calculator.append("0");
    calculator.evaluateToCents();
}
```

- [ ] **Step 2: Run logic tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests com.example.pkmapp.record.MoneyParserTest --tests com.example.pkmapp.record.CalculatorEngineTest`

Expected: compilation fails because parsing and calculator classes are missing.

- [ ] **Step 3: Implement deterministic cents parsing and calculation**

`MoneyParser.parseYuanToCents` accepts only a positive decimal containing at most two fractional digits, uses `BigDecimal.movePointRight(2).longValueExact()`, and throws `IllegalArgumentException("金额最多保留两位小数")` for malformed or non-positive values. `formatCents` uses `BigDecimal.valueOf(cents, 2)` and `Locale.CHINA` formatting.

`CalculatorEngine` accepts digit, decimal point and the visible operators `+`, `-`, `×`, `÷`. Evaluate the tokenized expression with a two-stack precedence algorithm using `BigDecimal`, return a positive result in cents through `MoneyParser`, and throw `IllegalArgumentException("请输入完整算式")` for malformed input. Do not evaluate by reflection, JavaScript or a third-party dependency.

- [ ] **Step 4: Run logic tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests com.example.pkmapp.record.MoneyParserTest --tests com.example.pkmapp.record.CalculatorEngineTest`

Expected: all valid arithmetic cases pass; malformed values and divide-by-zero fail predictably without an Android runtime.

- [ ] **Step 5: Commit entry logic**

```bash
git add app/src/main/java/com/example/pkmapp/record app/src/test/java/com/example/pkmapp/record
git commit -m "feat: add record amount calculator"
```

### Task 3: Build the Record Entry Screen

**Files:**
- Modify: `app/src/main/res/layout/fragment_record.xml`
- Create: `app/src/main/res/layout/dialog_calculator.xml`
- Create: `app/src/main/res/layout/dialog_custom_category.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values/styles.xml`
- Create: `app/src/main/res/drawable/ic_calculator.xml`
- Create: `app/src/main/res/drawable/ic_calendar.xml`
- Create: `app/src/main/res/drawable/ic_arrow_backspace.xml`
- Modify: `app/src/main/java/com/example/pkmapp/record/RecordFragment.java`
- Modify: `app/src/androidTest/java/com/example/pkmapp/MainNavigationTest.java`

**Interfaces:**
- Consumes: `InMemoryLedgerRepository.addTransaction`, `TransactionType`, `MoneyParser` and `CalculatorEngine` from Tasks 1–2, plus `MainActivity.showDestination(AppDestination.DETAILS)`.
- Produces: IDs `record_type_expense`, `record_type_income`, `record_amount_layout`, `record_amount_input`, `record_category_group`, `record_date_button`, `record_note_input`, `record_calculator_button`, and `record_save_button`.

- [ ] **Step 1: Extend the instrumentation test with the required record controls**

Add this test to `MainNavigationTest.java`:

```java
@Test
public void recordPage_hasAmountCategoryAndSaveControls() {
    onView(withId(R.id.nav_record)).perform(click());
    onView(withId(R.id.record_amount_input)).check(matches(isDisplayed()));
    onView(withId(R.id.record_category_group)).check(matches(isDisplayed()));
    onView(withId(R.id.record_date_button)).check(matches(isDisplayed()));
    onView(withId(R.id.record_save_button)).check(matches(isDisplayed()));
}
```

- [ ] **Step 2: Compile Android test sources to verify the new IDs fail**

Run: `./gradlew compileDebugAndroidTestSources`

Expected: compilation fails because the record IDs do not exist.

- [ ] **Step 3: Replace the placeholder layout with a native, accessible form**

Use a `NestedScrollView` containing: the existing title; a `MaterialButtonToggleGroup` for expense/income; a `TextInputLayout` with decimal `TextInputEditText`; a 48dp calculator `MaterialButton` using `ic_calculator`; a labeled `ChipGroup` for categories; a 48dp date `MaterialButton` with `ic_calendar`; a `TextInputLayout`/multi-line note input; and a full-width forest-green save `MaterialButton`.

Use `MaterialButton` chips with explicit Chinese text (do not use emoji glyphs). `RecordFragment` must render expense categories `餐饮、网购、日用、交通、娱乐、住房、自定义` and income categories `工资、奖金、兼职、红包、理财、其他、自定义`; changing type resets the selected category. Set today as the initial date using `Calendar.getInstance()`.

Use `MaterialDatePicker` for date selection. For custom category use `MaterialAlertDialogBuilder` with `dialog_custom_category.xml`; trim the field and use `TextInputLayout.setError("请输入分类名称")` when blank. Add an `OnBackPressedCallback` only while calculator dialog is open; otherwise retain the Activity back behavior.

- [ ] **Step 4: Wire validation, calculator and save navigation**

On save, call `MoneyParser.parseYuanToCents`. Send parser errors to `record_amount_layout.setError(...)`; if no category is selected, show `Toast` text `请选择分类`. On success, call:

```java
repository.addTransaction(selectedType, amountInCents, selectedCategory,
        binding.recordNoteInput.getText().toString(), selectedDateMillis);
Toast.makeText(requireContext(), "已收进森林账本", Toast.LENGTH_SHORT).show();
((MainActivity) requireActivity()).showDestination(AppDestination.DETAILS);
```

The calculator dialog has a non-editable expression display, digit/operator buttons, clear/backspace and `使用结果`. A divide-by-zero or malformed expression leaves the dialog open and displays an error below the expression. `使用结果` writes `MoneyParser.formatCents(result).replace("¥", "")` into `record_amount_input`.

- [ ] **Step 5: Recompile and run record tests**

Run: `./gradlew testDebugUnitTest compileDebugAndroidTestSources`

Expected: all JVM tests pass and Android test sources compile with the new record UI IDs.

- [ ] **Step 6: Commit the record form**

```bash
git add app/src/main/java/com/example/pkmapp/record app/src/main/res/layout/fragment_record.xml app/src/main/res/layout/dialog_calculator.xml app/src/main/res/layout/dialog_custom_category.xml app/src/main/res/drawable/ic_calculator.xml app/src/main/res/drawable/ic_calendar.xml app/src/main/res/drawable/ic_arrow_backspace.xml app/src/main/res/values app/src/androidTest/java/com/example/pkmapp/MainNavigationTest.java
git commit -m "feat: add interactive record form"
```

### Task 4: Render Ledger Details, Summaries and Empty State

**Files:**
- Modify: `app/src/main/res/layout/fragment_details.xml`
- Create: `app/src/main/res/layout/item_transaction.xml`
- Create: `app/src/main/res/layout/item_transaction_date_header.xml`
- Create: `app/src/main/res/layout/dialog_ledger_name.xml`
- Create: `app/src/main/java/com/example/pkmapp/details/DetailsListItem.java`
- Create: `app/src/main/java/com/example/pkmapp/details/TransactionListAdapter.java`
- Modify: `app/src/main/java/com/example/pkmapp/details/DetailsFragment.java`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/drawable/ic_currency_exchange.xml`
- Create: `app/src/main/res/drawable/ic_handshake.xml`
- Create: `app/src/main/res/drawable/ic_add.xml`

**Interfaces:**
- Consumes: `InMemoryLedgerRepository.getLedgers`, `getCurrentLedger`, `switchLedger`, `createLedger`, `getTransactionsForCurrentLedger`, `getCurrentMonthTotals`, `addListener` and `removeListener`; `MoneyParser.formatCents`.
- Produces: an adapter with `submit(List<DetailsListItem> items)` and `DetailsFragment` public IDs `details_ledger_button`, `details_tools_row`, `details_summary_card`, `details_empty_state`, `details_transaction_list`, and `details_add_record_button`.

- [ ] **Step 1: Add failing pure list-grouping tests**

Create `app/src/test/java/com/example/pkmapp/details/DetailsListItemTest.java` that supplies three UTC-dated `Transaction` objects and asserts a public static grouping method `DetailsListItem.fromTransactions(List<Transaction>, long nowMillis)` returns this sequence:

```java
assertEquals(DetailsListItem.Kind.DATE_HEADER, items.get(0).getKind());
assertEquals("今天", items.get(0).getHeaderLabel());
assertEquals(DetailsListItem.Kind.TRANSACTION, items.get(1).getKind());
assertEquals("昨天", items.get(2).getHeaderLabel());
assertEquals("9月4日", items.get(4).getHeaderLabel());
```

- [ ] **Step 2: Run the grouping test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests com.example.pkmapp.details.DetailsListItemTest`

Expected: compilation fails because `DetailsListItem` is not implemented.

- [ ] **Step 3: Implement list model, adapter and layouts**

`DetailsListItem` is an immutable `DATE_HEADER` or `TRANSACTION` row. Its static grouping method compares local calendar day against `nowMillis`: same day is `今天`, previous day is `昨天`, all other dates are `M月d日`. It preserves repository’s descending transaction order.

`TransactionListAdapter` extends `RecyclerView.Adapter<RecyclerView.ViewHolder>` with two view types. Header rows use `item_transaction_date_header.xml`; transaction rows use `item_transaction.xml` and display category, note-or-category fallback, formatted date, a visible `收入` or `支出` label and `+¥…`/`-¥…` amount. Expense uses `expense_red`, income uses `forest_green`; the text label makes the distinction non-colour-only.

Replace `fragment_details.xml` with a `NestedScrollView` containing current-ledger button, horizontal `details_tools_row` with two paper-card `MaterialButton` entries (汇率换算 and 借钱统计), summary paper card, empty-state vertical group and `RecyclerView`. Give the scrolling root bottom padding of `bottom_bar_height` plus `space_24` so it cannot be covered by the fixed nav.

- [ ] **Step 4: Implement repository-driven details behavior**

In `onViewCreated`, create one adapter and set `LinearLayoutManager`. In `onResume`, register a `LedgerDataListener` that calls `renderState()`; in `onPause`, remove the same listener. `renderState()` updates the ledger button, month title, three labelled totals, and swaps the empty state versus list based on `getTransactionsForCurrentLedger()`.

Clicking `details_ledger_button` opens a single-choice `MaterialAlertDialog` populated from `repository.getLedgers()`; selecting calls `switchLedger(id)`. Its positive button `新建账本` opens `dialog_ledger_name.xml`, validates nonblank with `TextInputLayout.setError("请输入账本名称")`, calls `createLedger(name)` and then `switchLedger(created.getId())`. The two tool cards display their currently-unavailable explanatory toast. `details_add_record_button` calls `MainActivity.showDestination(AppDestination.RECORD)`.

- [ ] **Step 5: Run grouping and full compilation verification**

Run: `./gradlew testDebugUnitTest compileDebugAndroidTestSources assembleDebug`

Expected: data, calculator and details JVM tests pass; debug APK assembles with the details RecyclerView resources.

- [ ] **Step 6: Commit the details screen**

```bash
git add app/src/main/java/com/example/pkmapp/details app/src/main/res/layout/fragment_details.xml app/src/main/res/layout/item_transaction.xml app/src/main/res/layout/item_transaction_date_header.xml app/src/main/res/layout/dialog_ledger_name.xml app/src/main/res/drawable/ic_currency_exchange.xml app/src/main/res/drawable/ic_handshake.xml app/src/main/res/drawable/ic_add.xml app/src/main/res/values app/src/test/java/com/example/pkmapp/details
git commit -m "feat: add ledger details workflow"
```

### Task 5: End-to-End UI Test, Documentation and Delivery Verification

**Files:**
- Modify: `app/src/androidTest/java/com/example/pkmapp/MainNavigationTest.java`
- Modify: `README.md`

**Interfaces:**
- Consumes: the `record_*` and `details_*` view IDs from Tasks 3–4.
- Produces: an Espresso test proving a record save makes the details summary/list visible, and accurate build/run instructions.

- [ ] **Step 1: Add a failing save-to-details Espresso flow**

Extend `MainNavigationTest.java` with:

```java
@Test
public void saveExpense_opensDetailsWithNewTransactionVisible() {
    onView(withId(R.id.nav_record)).perform(click());
    onView(withId(R.id.record_amount_input)).perform(typeText("12.58"));
    onView(withText("餐饮")).perform(click());
    onView(withId(R.id.record_save_button)).perform(click());
    onView(withId(R.id.title_details)).check(matches(isDisplayed()));
    onView(withText("餐饮")).check(matches(isDisplayed()));
    onView(withId(R.id.details_summary_card)).check(matches(isDisplayed()));
}
```

Add `closeSoftKeyboard()` after amount typing. If demo data uses the same category label in more than one row, scope the category click with `isDescendantOfA(withId(R.id.record_category_group))`.

- [ ] **Step 2: Compile Android test sources and confirm the test is recognized**

Run: `./gradlew compileDebugAndroidTestSources`

Expected: source compilation succeeds and the `saveExpense_opensDetailsWithNewTransactionVisible` test is listed by `./gradlew tasks --all` test discovery output when a device is available.

- [ ] **Step 3: Update README without expanding scope**

Replace the phase description with: the app has an in-memory income/expense entry workflow, ledger switching/creation, monthly summary and date-grouped details. State explicitly that app restart resets data and that real storage/network/borrowing/FX/charts/savings/profile data are still pending.

Add verification commands:

```bash
./gradlew testDebugUnitTest compileDebugAndroidTestSources assembleDebug
# When a WSL-visible emulator or device is connected:
./gradlew connectedDebugAndroidTest
```

- [ ] **Step 4: Run final verification**

Run: `./gradlew testDebugUnitTest compileDebugAndroidTestSources assembleDebug`

Expected: `BUILD SUCCESSFUL`, all JVM tests pass, Android test source compilation succeeds, and `app/build/outputs/apk/debug/app-debug.apk` exists. If a device is connected, run `./gradlew connectedDebugAndroidTest`; otherwise record that device visual QA remains pending rather than claiming it ran.

- [ ] **Step 5: Inspect working-tree scope and commit**

Run: `git status --short` and confirm only Task 5 files are staged; the two unrelated Gradle line-ending changes remain unstaged.

```bash
git add app/src/androidTest/java/com/example/pkmapp/MainNavigationTest.java README.md
git commit -m "test: verify record details workflow"
```

- [ ] **Step 6: Push the verified phase**

Run: `git push origin main`

Expected: the remote `main` contains all phase-two commits and no untracked or unrelated local files are uploaded.
