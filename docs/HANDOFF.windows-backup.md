# PKMAPP Handoff

## Workspace

- Use the Ubuntu WSL project, not the Windows copy.
- WSL project path: `/home/uug/projects/pkmAPP`
- Windows mirror: `D:\pkmAPP`
- Git remote: `git@github.com:ziyang-aa/PKMAPP.git`
- Current branch: `main`
- Last known commit: `ce9c263` (`chore: ignore isolated worktrees`)

## Working Agreement

- Run all development commands from the WSL project directory.
- Make code changes and verify them in WSL.
- Push verified changes to GitHub. The user will pull the repository in Windows Android Studio to run the app.
- Do not use the Windows project copy as the development workspace.

## WSL Environment

Configured for user `uug`:

- Ubuntu 26.04 LTS
- OpenJDK 17.0.20
- `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`
- Python 3.14.4 is available; no Python virtual environment is required unless a Python tool is introduced.
- Native Android SDK: `/home/uug/Android/Sdk`
- Installed Android SDK packages:
  - Android SDK Command-line Tools 19.0
  - Android SDK Platform 36
  - Android SDK Build-Tools 36.0.0
  - Android SDK Platform-Tools
- `~/.bashrc` contains `ANDROID_HOME`, `ANDROID_SDK_ROOT`, and SDK command-line/platform-tools PATH entries.
- Project-local `local.properties` has been changed in WSL to `sdk.dir=/home/uug/Android/Sdk`. This file is local-only and must not be committed.

## Verification Command

Run from the WSL project directory:

```bash
./gradlew testDebugUnitTest assembleDebug
```

Gradle 9.3.1 was downloaded and the daemon started after the SDK was configured. The final result of that build was not captured because the conversation was interrupted; rerun the command before beginning implementation.

## Project Status

The repository is still essentially an Android template. The frontend described below has not been implemented.

- Existing app source contains only template tests, launcher resources, base values, manifest, and Gradle configuration.
- `MainActivity`, destination model, fragments, layouts, navigation, design system, repository, and product features are all still missing.
- The working tree previously showed modifications to `gradle/wrapper/gradle-wrapper.properties` and `gradlew.bat`, likely line-ending differences from moving between Windows and WSL. Inspect before changing; do not discard unrelated work.

## Product Documents

Read these before implementation:

- Design specification: `docs/superpowers/specs/2026-09-05-pokemon-ledger-frontend-design.md`
- Implementation plan: `docs/superpowers/plans/2026-09-06-frontend-foundation-home.md`
- Artwork workflow: `artwork/README.md`

The plan has six tasks and 42 unchecked steps:

1. Build configuration, repository hygiene, and five phase-one assets.
2. Forest storybook design system.
3. Single-activity navigation and five-item bottom bar.
4. Continuously horizontally pannable home panorama.
5. Five feature-page shells.
6. QA, README documentation, and verified remote baseline.

## Design Constraints

- Java 11 and XML layouts only. Do not introduce Kotlin or Jetpack Compose.
- One `MainActivity`; all pages are Fragments.
- Use ViewBinding.
- Home is one continuous horizontal scene, not a carousel.
- Bottom navigation has exactly five entries: `明细`, `图表`, `记账`, `攒钱`, `我的`. Home is not a sixth item.
- Use vector drawables for functional icons and WebP for illustrations.
- Phase one uses temporary frontend-only state. Do not add a database or network layer.

## Artwork

- Converted assets are in `artwork/processed/android_ready/` (about 49 WebP files).
- Do not reference that working directory from the app at runtime.
- Copy only selected, approved files into `app/src/main/res/drawable-nodpi/`.
- The phase-one plan names five initial assets: panorama background, paper texture, Bulbasaur sleeping, Treecko waiting, and Turtwig carrying an acorn jar.

## Recommended Immediate Next Step

From `/home/uug/projects/pkmAPP`, rerun the verification command. If it succeeds, execute Task 1 of `docs/superpowers/plans/2026-09-06-frontend-foundation-home.md` incrementally, updating the plan checkboxes only after each verified step.
