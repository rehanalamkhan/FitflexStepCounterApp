# FitflexStepCounter

[![JitPack](https://jitpack.io/v/rehanalamkhan/FitflexStepCounterApp.svg)](https://jitpack.io/#rehanalamkhan/FitflexStepCounterApp)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0)

Android library for **hardware step counting** (`Sensor.TYPE_STEP_COUNTER`), **Room** persistence, a **foreground service** with **Activity Recognition** gating, **boot restart**, and an optional **Navigation** UI (home + settings + weekly **MPAndroidChart** bar chart).

**Full documentation:** [docs/FitflexStepCounter-Integration-Guide.md](docs/FitflexStepCounter-Integration-Guide.md) · printable [docs/FitflexStepCounter-Integration-Guide.pdf](docs/FitflexStepCounter-Integration-Guide.pdf) (regenerate with `python3 docs/build_integration_pdf.py`).

---

## Requirements

- **minSdk** 21 (library and sample app)
- **Hardware** `TYPE_STEP_COUNTER` sensor (manifest feature is `required=true` in this project; set `false` if you need broader device support)
- **Java 17** + **core library desugaring** (library uses `java.time`)

---

## Installation

### JitPack

**`settings.gradle.kts`** — add JitPack next to Google and Maven Central:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

**`app/build.gradle.kts`:**

```kotlin
dependencies {
    implementation("com.github.rehanalamkhan:FitflexStepCounterApp:1.0.7")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}

android {
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
}
```

### Local module

```kotlin
// settings.gradle.kts
include(":FitflexStepCounter")

// app/build.gradle.kts
dependencies {
    implementation(project(":FitflexStepCounter"))
}
```

---

## Permissions & manifest

Your **application** manifest should include (or rely on merge from the library) at least:

```xml
<uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

The library adds **`FOREGROUND_SERVICE_HEALTH`**, registers **`StepCounterService`**, **`StepCounterServiceLauncher`**, and **`ActivityRecognitionUpdateReceiver`** via manifest merge.

---

## Usage

Host apps can open `StepCounterFragment` in two supported ways:

- **Navigation Component:** declare `com.step.counter.StepCounterFragment` in your app nav graph and navigate with `NavController`.
- **FragmentManager only:** call `StepCounterHost.show(...)` against a normal app-owned container.

Full integration details, back-press behavior, and anti-patterns are documented in [docs/FitflexStepCounter-Integration-Guide.md](docs/FitflexStepCounter-Integration-Guide.md).

### 1. Host layout

Provide a container for the library root fragment, e.g. `fragment_container`:

```xml
<androidx.fragment.app.FragmentContainerView
    android:id="@+id/fragment_container"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

Do not target your `NavHostFragment` container id from the activity `FragmentManager`.

### 2. Embed `StepCounterFragment` without Navigation Component

```kotlin
import com.step.counter.integration.StepCounterHost

StepCounterHost.show(
    fragmentManager = supportFragmentManager,
    containerId = R.id.fragment_container,
)
```

Manual equivalent:

```kotlin
import com.step.counter.StepCounterFragment

supportFragmentManager.beginTransaction()
    .replace(R.id.fragment_container, StepCounterFragment.newInstance(), "StepCounter")
    .addToBackStack("StepCounter")
    .commit()
```

### 3. Embed `StepCounterFragment` with Navigation Component

```xml
<fragment
    android:id="@+id/stepCounterFragment"
    android:name="com.step.counter.StepCounterFragment"
    android:label="@string/step_counter" />
```

```kotlin
findNavController().navigate(R.id.stepCounterFragment)
```

`StepCounterFragment` calls **`StepCounter.init`**, requests **runtime permissions** (`ACTIVITY_RECOGNITION` and `POST_NOTIFICATIONS` on API 33+), then starts **`StepCounterService`** as a foreground service.

### 4. Optional: observe stats in your own UI

```kotlin
import com.step.counter.features.home.presentation.StatsDetailsViewModel

class MainActivity : AppCompatActivity() {
    private val viewModel: StatsDetailsViewModel by viewModels { StatsDetailsViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.day.collect { stats ->
                    // stats.stepsTaken, stats.distanceTravelled, stats.calorieBurned, stats.goal
                }
            }
        }
    }
}
```

---

## How it works (short)

- **`StepCounterService`** registers **`TYPE_STEP_COUNTER`**, applies an **Activity Recognition** gate (walking/running/on-foot vs vehicle/bike/still), and forwards deltas to **`StepCounterController`**, which batches writes to Room (50-step batches, flush on destroy).
- **`StatsDetailsViewModel`** reads today’s **`Day`** from Room; **`HomeFragment`** shows metrics and a Sun–Sat bar chart; **`SettingsFragment`** edits **`daily_goal`** in SharedPreferences.

---

## Sample app

This repository’s **`:app`** module demonstrates integration: see `app/src/main/java/com/counter/step/MainActivity.kt`.

---

## License

Apache 2.0 (see library `pom` in `FitflexStepCounter/build.gradle.kts`).
