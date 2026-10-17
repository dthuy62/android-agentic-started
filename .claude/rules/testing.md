---
paths:
  - "app/src/test/**"
  - "app/src/androidTest/**"
---

# Tests

- Test ViewModels and use cases on the JVM with fakes of domain interfaces; add a fake next to the existing ones
  (`ui/chat/Fakes.kt`) instead of using a mocking library.
- Use `MainDispatcherRule` for code that touches `Dispatchers.Main`.
- Assert on `state.value` and collected effects, not on internal calls.
- One behaviour per test; name tests after the behaviour (`sending blank message does nothing`).
- Cover the failure path: a fake that returns `Outcome.Failure` and the UI state it produces.
- Add Compose tests against `XContent` only when a screen has non-trivial rendering logic.
