# AAAltimeter

An Android Auto app that shows your GPS elevation over time as a chart, with an
indicator for whether you're currently gaining or losing height.

Status: **scaffold only**. The app installs, appears in Android Auto, and shows a
placeholder screen. GPS recording and the chart come next.

## Project layout

```
app/src/main/
  AndroidManifest.xml          Declares the car app service, category and min Car API level
  res/xml/automotive_app_desc.xml   Tells Android Auto this is a template-based car app
  kotlin/com/supachris28/aaaltimeter/
    MainActivity.kt            Tiny phone-side screen (so the app has a launcher icon)
    car/AltimeterCarAppService.kt   Entry point Android Auto binds to
    car/AltimeterSession.kt    One session per car connection
    car/AltimeterScreen.kt     The screen shown on the car display
gradle/libs.versions.toml      All dependency and plugin versions in one place
.github/workflows/build.yml    CI: builds a debug APK on every push / PR
```

Key versions: Kotlin 2.2, Android Gradle Plugin 8.13, Gradle 8.14, compile/target
SDK 36, min SDK 26 (Android 8.0), Car App Library 1.7.

## Android development in 2026, for someone who last did it ~2014

What changed since the Eclipse/ADT days:

- **Kotlin** is the default language. Java still works, but all docs and samples lead with Kotlin.
- **Android Studio** (IntelliJ-based) is the only IDE. Eclipse ADT is long gone.
- **Gradle with Kotlin DSL** (`*.gradle.kts`) replaces Ant and Groovy build files. Versions
  live in a *version catalog* (`gradle/libs.versions.toml`) instead of being scattered around.
- **AndroidX** (`androidx.*`) replaced the old support library (`android.support.*`).
- **Jetpack Compose** is the modern phone UI toolkit. It isn't used on the car screen, though.
- **Android Auto apps can't draw arbitrary UI.** You use the
  [Car App Library](https://developer.android.com/training/cars/apps), which gives you a fixed
  set of driver-safe *templates* (lists, panes, grids, maps). The car host renders them.
- **SDK management** happens in Android Studio's SDK Manager; you rarely touch `sdkmanager` directly.

## Setup

1. Install [Android Studio](https://developer.android.com/studio) (latest stable). It bundles a
   JDK, so you don't need to install Java separately.
2. Open this folder in Android Studio (**File → Open**). It will sync Gradle and offer to install
   any missing SDK platforms; accept.
3. In **Settings → Languages & Frameworks → Android SDK → SDK Tools**, tick
   **Android Auto Desktop Head Unit Emulator** and apply.

Command line build (from the repo root):

```bash
./gradlew assembleDebug        # APK lands in app/build/outputs/apk/debug/
./gradlew installDebug         # build and install on a USB-connected phone
```

CI also builds the debug APK on every push; you can download it from the run's
**Artifacts** section on GitHub.

## Running it

### Option A: Desktop Head Unit (DHU) on your computer

The DHU is a desktop window that pretends to be a car screen, driven by your real phone.

1. On the phone, enable **Developer options** (Settings → About phone → tap *Build number* 7 times)
   and turn on **USB debugging**.
2. Enable Android Auto developer mode: open Android Auto settings (Settings → search
   "Android Auto"), scroll to the bottom and tap **Version** 10 times. Accept the prompt.
3. In Android Auto's overflow menu (⋮) → **Developer settings**: turn on
   **Unknown sources** (required for sideloaded apps), then choose **Start head unit server**
   from the same menu.
4. Connect the phone by USB, install the app (`./gradlew installDebug` or the Run button), then:

   ```bash
   adb forward tcp:5277 tcp:5277
   $ANDROID_HOME/extras/google/auto/desktop-head-unit     # .exe on Windows
   ```

5. AAAltimeter appears in the DHU's app launcher.

### Option B: In your actual car

1. Do steps 1 to 3 above (you don't need the head unit server).
2. Install the app on the phone (USB with `./gradlew installDebug`, or copy the APK from a CI run
   onto the phone and open it).
3. Plug into the car (or connect wirelessly). AAAltimeter shows up in the Android Auto launcher.
   If it doesn't, double-check **Unknown sources** is on; Android Auto hides sideloaded apps without it.

## Why the POI category

Every Android Auto app has to declare a category in the manifest, and the Play Store only accepts
car apps in specific categories (navigation, POI, parking, charging, IoT, media, messaging, weather,
and a few others). An altimeter isn't any of these exactly. **POI** (point of interest) is the closest
non-navigation fit: it gives us list, pane and grid templates, and doesn't require turn-by-turn
directions. The elevation chart will be rendered as an image inside those templates.

The alternative is **navigation**, which unlocks drawing directly on the car screen (a smoother
live chart), but Play Store review expects navigation apps to actually navigate. For sideloading it
doesn't matter; it's a one-line change in `AndroidManifest.xml` if we decide to switch.
Either way, Play Store approval for an altimeter isn't guaranteed.
