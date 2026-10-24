# AAAltimeter

An Android Auto app that shows your GPS elevation over time as a chart, with an
indicator for whether you're currently gaining or losing height.

Status: **chart on the car screen**. While the app is open in Android Auto it records GPS
altitude in the background and shows a chart of the last 30 minutes, the current elevation, and a
big climbing / descending / level indicator.

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
    car/ElevationChartRenderer.kt   Draws the chart and indicator as a bitmap
    recording/ElevationRecordingService.kt   Foreground service that listens to GPS
    elevation/                 Smoothing, trend detection and history (plain Kotlin, unit tested)
app/src/test/                  JVM unit tests (./gradlew testDebugUnitTest)
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

### Installing the CI APK on your phone

1. GitHub delivers the artifact as a **.zip**. Unzip it first; the phone can't install the zip
   itself. Inside is `app-debug.apk`.
2. Copy the APK to the phone and open it. Allow "Install unknown apps" for whichever app you opened
   it from (Files, Chrome, Drive...) when prompted.
3. If Play Protect warns about an unrecognised app, choose **More details → Install anyway**.
4. **"App not installed" / "conflicts with an existing package"**: an earlier copy on the phone was
   signed with a different key. Uninstall AAAltimeter from the phone, then install again. Debug
   builds are now signed with the checked-in `app/debug.keystore`, so this should only happen
   once; later CI APKs install as updates over each other.

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

## How elevation is worked out

- **Recording.** Opening the app on the car screen starts a foreground service (you'll see a
  "Recording elevation" notification on the phone) that asks for a GPS fix every second. Closing
  the app in the car stops it. The phone screen also has Start/Stop buttons for testing without a car.
  The first time, tap **Grant on phone** on the car screen (or Start on the phone) to allow location.
- **Sea level.** GPS measures height above a mathematical ellipsoid, which in the UK is about 50 m
  off real height above sea level. On Android 14+ the app corrects for this with Android's built-in
  geoid model; on older phones it shows the raw GPS value. The trend is unaffected.
- **Smoothing.** GPS altitude jitters by 5 to 20 m. Readings with poor vertical accuracy (over 50 m)
  are dropped, readings implying more than 20 m/s vertical speed are treated as spikes, and the rest go
  through a time-weighted moving average (about 15 s lag).
- **Trend.** A straight line is fitted through the last 60 s of smoothed altitude. Climbing or
  descending needs at least 9 m/min (roughly a 1% gradient at 50 km/h) to start, and drops back to
  level below 4.5 m/min, so the indicator doesn't flicker.
- **History.** The last 3 hours are kept in memory. Nothing is saved to disk yet.

## The car screen

The chart is drawn as a 640×400 image and placed in a pane template, which the car shows beside
two text rows. Across the top of the image: current elevation on the left, and on the right an
arrow with the rate (orange up for climbing, green down for descending, a grey bar for level).
Below that is the smoothed altitude over the last 30 minutes (or the whole recording, at least
5 minutes wide, if it's shorter). The altitude axis snaps to round numbers and never spans less than
20 m, so GPS wobble on flat ground doesn't look like hills. Gaps of over a minute in GPS show as
breaks in the line. The screen refreshes at most every 2 seconds, and follows the car's day/night
mode. Cars with Car API level below 4 (rare) can't show pane images and get the text rows only.

## Why the POI category

Every Android Auto app has to declare a category in the manifest, and the Play Store only accepts
car apps in specific categories (navigation, POI, parking, charging, IoT, media, messaging, weather,
and a few others). An altimeter isn't any of these exactly. **POI** (point of interest) is the closest
non-navigation fit: it gives us list, pane and grid templates, and doesn't require turn-by-turn
directions. The elevation chart is rendered as an image inside a pane template.

The alternative is **navigation**, which unlocks drawing directly on the car screen (a smoother
live chart), but Play Store review expects navigation apps to actually navigate. For sideloading it
doesn't matter; it's a one-line change in `AndroidManifest.xml` if we decide to switch.
Either way, Play Store approval for an altimeter isn't guaranteed.
