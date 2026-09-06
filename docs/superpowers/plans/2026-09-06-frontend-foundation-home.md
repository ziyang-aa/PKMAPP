# Frontend Foundation and Panorama Home Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a runnable Java/XML Android frontend foundation with the forest-storybook design system, a continuously pannable home scene, a fixed five-entry bottom bar, and navigable feature-page shells.

**Architecture:** `MainActivity` owns a `FragmentContainerView` and a custom `MaterialButtonToggleGroup`. `HomeFragment` is the default destination and contains one wide scene inside a `HorizontalScrollView`; feature destinations are independent fragments. Shared colors, dimensions, typography, and card shapes live in Android resources so later feature plans reuse one design system.

**Tech Stack:** Java 11, Android Gradle Plugin 9.1.1, AndroidX AppCompat, AndroidX Fragment 1.8.9, RecyclerView 1.4.0, Material Components, XML layouts, ViewBinding, JUnit 4, Espresso.

**Spec:** `docs/superpowers/specs/2026-09-05-pokemon-ledger-frontend-design.md`

## Global Constraints

- Application code uses Java 11 and XML layouts; do not introduce Kotlin or Jetpack Compose.
- Keep one `MainActivity`; every screen is a Fragment.
- The home scene is one continuous horizontal image, never a carousel or a set of paged images.
- The five fixed feature entries are 明细、图表、记账、攒钱、我的; Home has no sixth navigation item.
- Use forest palette values exactly as defined in the spec.
- Use Vector Drawable resources for functional icons and WebP only for illustrations.
- This phase uses frontend-only temporary state and adds no database or network access.

---

### Task 1: Build Configuration, Repository Hygiene, and Required Assets

**Files:**
- Modify: `.gitignore`
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Copy: `artwork/processed/android_ready/bg_forest_camp_landscape.webp` to `app/src/main/res/drawable-nodpi/bg_home_panorama_placeholder.webp`
- Copy: `artwork/processed/android_ready/bg_paper_texture.webp` to `app/src/main/res/drawable-nodpi/bg_paper_texture.webp`
- Copy: `artwork/processed/android_ready/bulbasaur_sleeping.webp` to `app/src/main/res/drawable-nodpi/bulbasaur_sleeping.webp`
- Copy: `artwork/processed/android_ready/treecko_waiting.webp` to `app/src/main/res/drawable-nodpi/treecko_waiting.webp`
- Copy: `artwork/processed/android_ready/turtwig_carrying_acorn_jar.webp` to `app/src/main/res/drawable-nodpi/turtwig_carrying_acorn_jar.webp`

**Interfaces:**
- Consumes: Existing Android application module and converted WebP library.
- Produces: ViewBinding classes, Fragment/RecyclerView APIs, a launcher Activity declaration, and five starter illustrations available through `R.drawable`.

- [x] **Step 1: Record the baseline build**

Run:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

Expected: either the generated empty project builds or the exact pre-existing build failure is recorded before changes.

- [x] **Step 2: Add stable AndroidX dependencies**

Add to `gradle/libs.versions.toml`:

```toml
fragment = "1.8.9"
recyclerview = "1.4.0"

fragment = { group = "androidx.fragment", name = "fragment", version.ref = "fragment" }
recyclerview = { group = "androidx.recyclerview", name = "recyclerview", version.ref = "recyclerview" }
```

Add to `app/build.gradle.kts`:

```kotlin
buildFeatures {
    viewBinding = true
}

dependencies {
    implementation(libs.fragment)
    implementation(libs.recyclerview)
}
```

- [x] **Step 3: Declare the launcher Activity**

Change the self-closing `<application />` into an application containing:

```xml
<activity
    android:name=".MainActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

- [x] **Step 4: Keep generated and private working files out of Git**

Ensure `.gitignore` contains:

```gitignore
/.superpowers/
/宝可梦账本素材/
/artwork/processed/android_ready/
```

Only Android resources copied into `app/src/main/res/` are shipped and versioned.

- [x] **Step 5: Copy the five phase-one WebP resources**

Run:

```powershell
Copy-Item -LiteralPath 'artwork\processed\android_ready\bg_forest_camp_landscape.webp' -Destination 'app\src\main\res\drawable-nodpi\bg_home_panorama_placeholder.webp'
Copy-Item -LiteralPath 'artwork\processed\android_ready\bg_paper_texture.webp' -Destination 'app\src\main\res\drawable-nodpi\bg_paper_texture.webp'
Copy-Item -LiteralPath 'artwork\processed\android_ready\bulbasaur_sleeping.webp' -Destination 'app\src\main\res\drawable-nodpi\bulbasaur_sleeping.webp'
Copy-Item -LiteralPath 'artwork\processed\android_ready\treecko_waiting.webp' -Destination 'app\src\main\res\drawable-nodpi\treecko_waiting.webp'
Copy-Item -LiteralPath 'artwork\processed\android_ready\turtwig_carrying_acorn_jar.webp' -Destination 'app\src\main\res\drawable-nodpi\turtwig_carrying_acorn_jar.webp'
```

- [x] **Step 6: Verify configuration and resources**

Run:

```powershell
.\gradlew.bat assembleDebug
```

Expected: `BUILD SUCCESSFUL`; generated ViewBinding support is available and all copied resource names pass Android resource validation.

- [ ] **Step 7: Commit the configuration baseline**

```powershell
git add .gitignore gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/res/drawable-nodpi
git commit -m "build: configure frontend foundation"
```

---

### Task 2: Forest Storybook Design System

**Files:**
- Modify: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/res/values/themes.xml`
- Modify: `app/src/main/res/values-night/themes.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/dimens.xml`
- Create: `app/src/main/res/values/styles.xml`
- Create: `app/src/main/res/color/nav_item_tint.xml`
- Create: `app/src/main/res/drawable/bg_paper_card.xml`
- Create: `app/src/main/res/drawable/bg_bottom_bar.xml`
- Create: `app/src/androidTest/java/com/example/pkmapp/ForestThemeResourceTest.java`

**Interfaces:**
- Consumes: Material Components theme support from Task 1.
- Produces: `Theme.PkmAPP`, `Widget.PkmAPP.PaperCard`, `Widget.PkmAPP.BottomNavButton`, shared spacing values, and named forest colors.

- [x] **Step 1: Write a failing resource test**

Create `ForestThemeResourceTest.java`:

```java
@RunWith(AndroidJUnit4.class)
public final class ForestThemeResourceTest {
    private final Context context = ApplicationProvider.getApplicationContext();

    @Test
    public void forestPalette_matchesApprovedDesign() {
        assertEquals(Color.rgb(245, 241, 231), ContextCompat.getColor(context, R.color.cream_background));
        assertEquals(Color.rgb(78, 129, 70), ContextCompat.getColor(context, R.color.forest_green));
        assertEquals(Color.rgb(199, 91, 75), ContextCompat.getColor(context, R.color.expense_red));
        assertEquals(Color.rgb(59, 53, 44), ContextCompat.getColor(context, R.color.ink));
    }
}
```

- [x] **Step 2: Compile the test and verify it fails**

Run:

```powershell
.\gradlew.bat compileDebugAndroidTestSources
```

Expected: compilation fails because the four named color resources do not exist.

- [x] **Step 3: Add the approved palette and shared dimensions**

Define these values in `colors.xml`:

```xml
<color name="cream_background">#F5F1E7</color>
<color name="paper_card">#FFF8DF</color>
<color name="forest_green">#4E8146</color>
<color name="leaf_green">#A8D58C</color>
<color name="wood_brown">#7B5C3B</color>
<color name="warm_orange">#D48A4A</color>
<color name="expense_red">#C75B4B</color>
<color name="ink">#3B352C</color>
```

Define `space_4`, `space_8`, `space_12`, `space_16`, `space_24`, `radius_card`, `bottom_bar_height`, and `touch_target` in `dimens.xml`; use 4dp, 8dp, 12dp, 16dp, 24dp, 20dp, 76dp, and 48dp respectively.

- [x] **Step 4: Create reusable paper-card and bottom-navigation styles**

`bg_paper_card.xml` uses `paper_card`, a 20dp corner radius, and a 1dp `#335C4938` stroke. `bg_bottom_bar.xml` uses `paper_card`, 24dp top corners, and the same subtle stroke. `nav_item_tint.xml` maps checked state to `forest_green` and default state to `wood_brown`.

Set `Theme.PkmAPP` to inherit `Theme.Material3.DayNight.NoActionBar`, set the window background to `cream_background`, and use the same palette in the night resource so the first release has one intentional visual theme instead of an accidental dark mode.

- [x] **Step 5: Verify the resource contract**

Run:

```powershell
.\gradlew.bat compileDebugAndroidTestSources assembleDebug
```

Expected: both tasks succeed. Run the instrumentation test later when an emulator is available.

- [ ] **Step 6: Commit the design system**

```powershell
git add app/src/main/res app/src/androidTest/java/com/example/pkmapp/ForestThemeResourceTest.java
git commit -m "feat: add forest storybook design system"
```

---

### Task 3: Destination Model and Single-Activity Navigation

**Files:**
- Create: `app/src/main/java/com/example/pkmapp/navigation/AppDestination.java`
- Create: `app/src/test/java/com/example/pkmapp/navigation/AppDestinationTest.java`
- Create: `app/src/main/java/com/example/pkmapp/MainActivity.java`
- Create: `app/src/main/res/layout/activity_main.xml`
- Create: `app/src/main/res/drawable/ic_list.xml`
- Create: `app/src/main/res/drawable/ic_chart.xml`
- Create: `app/src/main/res/drawable/ic_add_ledger.xml`
- Create: `app/src/main/res/drawable/ic_savings.xml`
- Create: `app/src/main/res/drawable/ic_profile.xml`

**Interfaces:**
- Consumes: `Theme.PkmAPP`, shared colors/styles, and Fragment support.
- Produces: enum `AppDestination { HOME, DETAILS, CHARTS, RECORD, SAVINGS, PROFILE }`, `MainActivity.showDestination(AppDestination)`, and view IDs `nav_details`, `nav_charts`, `nav_record`, `nav_savings`, `nav_profile`.

- [x] **Step 1: Write the failing destination test**

```java
public final class AppDestinationTest {
    @Test
    public void featureDestinations_haveExactlyFiveBottomBarItems() {
        assertEquals(
                Arrays.asList(DETAILS, CHARTS, RECORD, SAVINGS, PROFILE),
                AppDestination.bottomBarDestinations());
    }

    @Test
    public void home_isNotABottomBarItem() {
        assertFalse(AppDestination.bottomBarDestinations().contains(HOME));
    }
}
```

- [x] **Step 2: Run the test to verify it fails**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests com.example.pkmapp.navigation.AppDestinationTest
```

Expected: FAIL because `AppDestination` does not exist.

- [x] **Step 3: Implement the destination enum**

Implement `bottomBarDestinations()` as an unmodifiable list in the required order. Do not put Android resource references in the enum so the ordering test remains a plain JVM test.

- [x] **Step 4: Run the destination test**

Run the same targeted Gradle test. Expected: two tests PASS.

- [x] **Step 5: Build the Activity layout and navigation controller**

`activity_main.xml` contains a `FragmentContainerView` constrained above a fixed `MaterialButtonToggleGroup`. The group contains five equal-width `MaterialButton` children with vector icons and the required Chinese labels. Configure `singleSelection="true"` and `selectionRequired="false"` so Home can display with no checked feature button.

`MainActivity` must:

```java
public void showDestination(AppDestination destination) {
    Fragment fragment = createFragment(destination);
    getSupportFragmentManager().beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.fragment_container, fragment, destination.name())
            .commit();
    currentDestination = destination;
    syncBottomBar(destination);
}
```

On first creation call `showDestination(HOME)`. Map the five buttons to the five feature destinations. Register an `OnBackPressedCallback`: when a feature page is visible, show Home and clear the toggle selection; when Home is visible, disable the callback and delegate to the Activity's normal back behavior.

- [x] **Step 6: Verify the Activity compiles**

Run:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

Expected: all JVM tests pass and the debug APK builds.

- [ ] **Step 7: Commit single-Activity navigation**

```powershell
git add app/src/main/java app/src/main/res/layout/activity_main.xml app/src/main/res/drawable app/src/test
git commit -m "feat: add single activity navigation"
```

---

### Task 4: Continuous Horizontal Panorama Home

**Files:**
- Create: `app/src/main/java/com/example/pkmapp/home/HomeFragment.java`
- Create: `app/src/main/java/com/example/pkmapp/home/PanoramaSizing.java`
- Create: `app/src/test/java/com/example/pkmapp/home/PanoramaSizingTest.java`
- Create: `app/src/main/res/layout/fragment_home.xml`
- Create: `app/src/main/res/layout/view_home_panorama.xml`

**Interfaces:**
- Consumes: `bg_home_panorama_placeholder`, `bg_paper_texture`, and the Activity fragment container.
- Produces: `HomeFragment` and `PanoramaSizing.requiredWidthPx(int viewportWidthPx)`.

- [x] **Step 1: Write the failing panorama sizing tests**

```java
public final class PanoramaSizingTest {
    @Test
    public void panorama_isWiderThanTheViewport() {
        assertEquals(1080, PanoramaSizing.requiredWidthPx(360));
    }

    @Test
    public void panorama_neverShrinksBelowConfiguredMinimum() {
        assertEquals(1080, PanoramaSizing.requiredWidthPx(200));
    }
}
```

- [x] **Step 2: Run the test to verify it fails**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests com.example.pkmapp.home.PanoramaSizingTest
```

Expected: FAIL because `PanoramaSizing` does not exist.

- [x] **Step 3: Implement deterministic panorama width**

```java
public final class PanoramaSizing {
    private PanoramaSizing() {}

    public static int requiredWidthPx(int viewportWidthPx) {
        return Math.max(1080, viewportWidthPx * 3);
    }
}
```

- [x] **Step 4: Run the targeted tests**

Expected: both panorama sizing tests PASS.

- [x] **Step 5: Implement one scrollable scene**

`fragment_home.xml` contains one horizontal `HorizontalScrollView` with scroll bars disabled and exactly one child, an included `view_home_panorama.xml`. The included root is a `FrameLayout` whose width is updated after layout using `PanoramaSizing.requiredWidthPx(viewportWidth)` and whose height matches the available area.

The scene contains one full-size placeholder background `ImageView` using `centerCrop`, a subtle paper texture overlay, and three non-clickable character `ImageView`s positioned at different horizontal locations. They are compositional elements of one scene, not separate pages. Preserve the current horizontal scroll offset across configuration changes using `onSaveInstanceState`.

- [x] **Step 6: Verify build and unit tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

Expected: tests pass and the home Fragment compiles into the APK.

- [ ] **Step 7: Commit the panorama home**

```powershell
git add app/src/main/java/com/example/pkmapp/home app/src/main/res/layout/fragment_home.xml app/src/main/res/layout/view_home_panorama.xml app/src/test/java/com/example/pkmapp/home
git commit -m "feat: add pannable panorama home"
```

---

### Task 5: Five Feature Page Shells

**Files:**
- Create: `app/src/main/java/com/example/pkmapp/details/DetailsFragment.java`
- Create: `app/src/main/java/com/example/pkmapp/charts/ChartsFragment.java`
- Create: `app/src/main/java/com/example/pkmapp/record/RecordFragment.java`
- Create: `app/src/main/java/com/example/pkmapp/savings/SavingsFragment.java`
- Create: `app/src/main/java/com/example/pkmapp/profile/ProfileFragment.java`
- Create: `app/src/main/res/layout/fragment_details.xml`
- Create: `app/src/main/res/layout/fragment_charts.xml`
- Create: `app/src/main/res/layout/fragment_record.xml`
- Create: `app/src/main/res/layout/fragment_savings.xml`
- Create: `app/src/main/res/layout/fragment_profile.xml`
- Create: `app/src/androidTest/java/com/example/pkmapp/MainNavigationTest.java`

**Interfaces:**
- Consumes: `MainActivity.createFragment(AppDestination)` and the design-system resources.
- Produces: five instantiable feature fragments with stable title IDs: `title_details`, `title_charts`, `title_record`, `title_savings`, `title_profile`.

- [x] **Step 1: Write the navigation instrumentation test**

```java
@RunWith(AndroidJUnit4.class)
public final class MainNavigationTest {
    @Rule public ActivityScenarioRule<MainActivity> rule =
            new ActivityScenarioRule<>(MainActivity.class);

    @Test
    public void allFiveButtonsOpenTheirFeaturePage() {
        onView(withId(R.id.nav_details)).perform(click());
        onView(withId(R.id.title_details)).check(matches(isDisplayed()));
        onView(withId(R.id.nav_charts)).perform(click());
        onView(withId(R.id.title_charts)).check(matches(isDisplayed()));
        onView(withId(R.id.nav_record)).perform(click());
        onView(withId(R.id.title_record)).check(matches(isDisplayed()));
        onView(withId(R.id.nav_savings)).perform(click());
        onView(withId(R.id.title_savings)).check(matches(isDisplayed()));
        onView(withId(R.id.nav_profile)).perform(click());
        onView(withId(R.id.title_profile)).check(matches(isDisplayed()));
    }
}
```

- [ ] **Step 2: Compile the test and verify it fails**

Run:

```powershell
.\gradlew.bat compileDebugAndroidTestSources
```

Expected: compilation fails because the five Fragment classes or title IDs do not exist.

- [x] **Step 3: Implement focused page shells**

Each Fragment inflates its own binding and clears it in `onDestroyView`. Each layout provides a forest-styled toolbar title, a short phase label, and one relevant transparent character illustration. Do not implement feature forms or fake data in this phase; those belong to the later subsystem plans.

- [x] **Step 4: Connect every destination in `MainActivity.createFragment`**

Use an exhaustive switch over all six enum values. `HOME` returns `HomeFragment`; each feature value returns its matching fragment. Throw `IllegalArgumentException` only for a null destination.

- [x] **Step 5: Verify all source sets compile**

Run:

```powershell
.\gradlew.bat testDebugUnitTest compileDebugAndroidTestSources assembleDebug
```

Expected: JVM tests pass, instrumentation tests compile, and the debug APK builds. If an emulator is available, run `connectedDebugAndroidTest` and require `MainNavigationTest` to pass.

- [ ] **Step 6: Commit the navigable page shells**

```powershell
git add app/src/main/java app/src/main/res/layout app/src/androidTest
git commit -m "feat: add five feature page shells"
```

---

### Task 6: Phase-One QA and Remote Baseline

**Files:**
- Modify: `README.md`
- Modify: `docs/superpowers/plans/2026-09-06-frontend-foundation-home.md`

**Interfaces:**
- Consumes: all deliverables from Tasks 1-5.
- Produces: a reproducible build command, checked-off implementation plan, and a pushed phase-one baseline on `origin/main`.

- [x] **Step 1: Run the complete verification suite**

Run:

```powershell
.\gradlew.bat clean testDebugUnitTest compileDebugAndroidTestSources assembleDebug
```

Expected: `BUILD SUCCESSFUL`, zero failing JVM tests, successful Android-test compilation, and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [x] **Step 2: Inspect the APK resource contents**

Confirm the packaged resources include the five phase-one WebP files and do not include any PNG from `宝可梦账本素材/`.

- [ ] **Step 3: Perform visual QA on an emulator or connected phone**

Verify at minimum:

- the home scene fills the area above the bottom bar;
- dragging horizontally moves one continuous scene;
- the bottom bar remains fixed;
- all five labels and icons are readable and tappable;
- tapping each entry opens the correct shell;
- Back from a feature shell returns to Home;
- no transparent illustration shows a checkerboard or rectangular background.

- [x] **Step 4: Document how to run the prototype**

Add to `README.md` the commands `./gradlew assembleDebug` and `./gradlew testDebugUnitTest`, the APK path, and a note that this phase uses page shells and temporary frontend state only.

- [ ] **Step 5: Commit QA documentation**

```powershell
git add README.md docs/superpowers/plans/2026-09-06-frontend-foundation-home.md
git commit -m "docs: record frontend foundation verification"
```

- [ ] **Step 6: Push the verified baseline**

Run:

```powershell
git push -u origin main
```

Expected: local `main` tracks `origin/main` and the remote contains only verified project files and application-ready resources.
