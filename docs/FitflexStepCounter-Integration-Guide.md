# FitflexStepCounter integration guide

This guide explains how to embed `StepCounterFragment` in host apps that use Navigation Component, and in host apps that use only `FragmentManager`.

## What the library owns

- `StepCounterFragment` is the public root entry point.
- Inside the root fragment, the library hosts its own nested `NavHostFragment` for home and settings screens.
- Host apps do not need to declare the library's internal `step_counter_nav_graph` in their own navigation graph.

## What host apps must provide

- A normal app-owned fragment container such as `R.id.fragment_container`.
- Runtime permissions required by the library manifest merge.
- A way to present and dismiss `StepCounterFragment`.

## Supported host patterns

### Case 1: Navigation Component

Add the library fragment as a destination in your app graph:

```xml
<fragment
    android:id="@+id/stepCounterFragment"
    android:name="com.step.counter.StepCounterFragment"
    android:label="@string/step_counter" />
```

Navigate from any fragment that already has a `NavController`:

```kotlin
findNavController().navigate(R.id.stepCounterFragment)
```

Back handling:

- Inside the library, back first pops the library's internal graph.
- When the internal graph is already at its start destination, back pops your app graph and returns to the previous screen.

### Case 2: FragmentManager only

Use a dedicated container owned by your activity or parent fragment:

```kotlin
import com.step.counter.integration.StepCounterHost

StepCounterHost.show(
    fragmentManager = supportFragmentManager,
    containerId = R.id.fragment_container,
)
```

Equivalent manual transaction:

```kotlin
supportFragmentManager.beginTransaction()
    .replace(R.id.fragment_container, StepCounterFragment.newInstance(), "StepCounter")
    .addToBackStack("StepCounter")
    .commit()
```

Back handling:

- Inside the library, back first pops the library's internal graph.
- When the internal graph is already at its start destination, back pops the host `FragmentManager` back stack entry that presented `StepCounterFragment`.

## Do not do this

Do not add or replace `StepCounterFragment` directly into a `NavHostFragment` container using the activity `supportFragmentManager`:

```kotlin
// Wrong
supportFragmentManager.beginTransaction()
    .add(R.id.nav_host_fragment, StepCounterFragment.newInstance())
    .addToBackStack("StepCounter")
    .commit()
```

Why this breaks back:

- `nav_host_fragment` is already owned by Navigation Component.
- The activity `FragmentManager` and the `NavHostFragment` child `FragmentManager` manage different back stacks.
- The app `NavController` still reports the previous destination, so host back callbacks may think the user is still on the old screen.
- The system back gesture and back button may appear to do nothing, show the wrong screen, or leave overlapping fragments in the same container.

## Best practices

- Use either Navigation Component or a normal fragment container, not both on the same container id.
- If your app already uses Navigation Component, declare `StepCounterFragment` in your nav graph and navigate with `NavController`.
- If your app does not use Navigation Component, use `StepCounterHost` or a manual `replace(...).addToBackStack(...)` transaction against your own container.
- Keep the fragment tag and back stack name stable when you use manual transactions. `StepCounterHost` defaults to `"StepCounter"`.
- Do not nest `StepCounterFragment` inside another library-owned `NavHostFragment` container unless that container is a normal app-owned view id.

## Back press and system back gesture

`StepCounterFragment` registers an `OnBackPressedCallback` on the activity `OnBackPressedDispatcher`.

Order of handling:

1. Pop the library's internal navigation stack when the user is on settings or another internal destination.
2. Pop the host that presented `StepCounterFragment`:
   - parent `NavController` when the fragment is a nav destination
   - parent `FragmentManager` back stack when the fragment was shown with `addToBackStack(...)`
3. Delegate to the next enabled callback only when neither stack can pop.

This works with the system back gesture because predictive back uses the same dispatcher chain.

## Internal library navigation helpers

Library screens use optional Navigation helpers:

- `Fragment.safeNavigate(...)`
- `Fragment.safeBack()`

These helpers prefer the nearest `NavController` and fall back to the parent `FragmentManager` back stack when no controller is attached.

## Sample app

The `:app` module in this repository demonstrates the FragmentManager integration path through `StepCounterHost.show(...)`.

## Troubleshooting

| Symptom | Likely cause | Fix |
| --- | --- | --- |
| Back does nothing after opening StepCounter | Fragment was injected into `nav_host_fragment` with the activity `FragmentManager` | Navigate through `NavController` or use a separate container |
| Blank screen after back | Mixed `add()` / `replace()` on the same container without a matching back stack | Use `replace(...).addToBackStack(...)` or a nav destination |
| Settings back works, host back does not | Host transaction did not call `addToBackStack(...)` | Add a back stack entry or navigate through `NavController` |
| Duplicate StepCounter instances | Host opened the screen twice | Reuse `StepCounterHost.show(...)` or check `findFragmentByTag(...)` before committing |
