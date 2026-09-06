# PKMAPP Handoff

## Workspace

- Work only in the Ubuntu WSL project: `/home/uug/projects/pkmAPP`.
- Windows mirror for Android Studio: `D:\pkmAPP`.
- Git remote: `git@github.com:ziyang-aa/PKMAPP.git`.
- Branch: `main`.
- Latest local commit: `5cd5e00` (`feat: build forest storybook frontend foundation`).

## Working Agreement

- Run development commands from the WSL project directory.
- Verify changes in WSL, then push them to GitHub.
- The user pulls the repository in Windows Android Studio to run the app.
- Do not use the Windows project copy as the development workspace.

## WSL Environment

- Java 21 is installed as a user-local JDK at:
  `/home/uug/.local/jdks/openjdk-21/usr/lib/jvm/java-21-openjdk-amd64`.
- `~/.bashrc` sets `JAVA_HOME` and prepends the JDK 21 `bin` directory to `PATH`.
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

## Plan Progress

- Tasks 1-5 implementation steps are complete and verified through compilation/builds.
- Task 6 complete: clean build, APK resource inspection, and README documentation.
- Task 1, Task 2, Task 3, Task 4, and Task 5 commit steps remain represented by the combined commit above rather than separate commits.
- Instrumentation execution and emulator visual QA are still pending because no device was connected in WSL.
- The plan file records implementation and verification steps that were completed; device-only visual QA, commit-step checkboxes, and push-step checkbox remain unchecked where applicable.

## Known Worktree State

- Existing unrelated line-ending changes remain in `gradle/wrapper/gradle-wrapper.properties` and `gradlew.bat`; do not revert them without explicit instruction.
- This handoff document is included in the current handoff commit.

## Next Steps

1. Pull or fetch `main` in Windows Android Studio.
2. Run the app on an emulator or phone and perform visual QA.
3. If a device is available in WSL, run:

```bash
./gradlew connectedDebugAndroidTest
```

4. Continue with the next product feature only after the phase-one shell is visually accepted.
