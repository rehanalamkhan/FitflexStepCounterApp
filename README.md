**FitflexStepCounter**

**FitflexStepCounter** is a modern Android step counter library that uses the device’s hardware step counter sensor to provide accurate, persistent, and background-safe step tracking.

## ✨ Features

* Hardware-based step counting (low battery usage)
* Background service for continuous tracking
* Room database for step history
* MPAndroidChart integration for step visualization
* Auto-restart on device boot
* ViewModel & Lifecycle-aware architecture

---

## 📦 Installation (JitPack)

### 1. Add JitPack Repository

**settings.gradle**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### 2. Add Dependency

```kotlin
implementation("com.github.rehanalamkhan:FitflexStepCounterApp:1.0.0")
coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
```

---

## ⚙️ Required Configuration

### Java & Desugaring

```kotlin
compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    isCoreLibraryDesugaringEnabled = true
}
```

---

## 🔐 Permissions & Features

**AndroidManifest.xml**

```xml
<uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<uses-feature
    android:name="android.hardware.sensor.stepcounter"
    android:required="true" />
```

📌 **Runtime permissions required**

* `ACTIVITY_RECOGNITION`
* `POST_NOTIFICATIONS` (Android 13+)

---

## ▶️ Usage

Request required permissions **before launching** `StepCounterMainActivity`.

```kotlin
startActivity(
    Intent(this, StepCounterMainActivity::class.java)
)
```

A complete permission-handling example is required for proper functionality.

---

## 🧩 Notes

* Requires a device with a **hardware step counter sensor**
* **Minimum SDK:** 24
* Optimized for modern Android versions
