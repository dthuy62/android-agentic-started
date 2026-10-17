# Data, errors and coroutines

- Repositories return `Outcome<T>` for expected failures. `data/` turns exceptions into `AppError`, usually with
  `outcomeOf { ... }` from `core/common`, which rethrows `CancellationException`.
- Never catch `CancellationException` without rethrowing it.
- Inject `DispatcherProvider`; never reference `Dispatchers.IO` directly.
- Add a use case only for real logic or reuse (`SendMessageUseCase`). No pass-through use cases.
- Interfaces go in `domain/repository` or `domain/engine`, implementations in `data/`, one `@Binds` in
  `di/RepositoryModule.kt`.
- Avoid primitive obsession for finite or risky concepts: enums, sealed types or `@JvmInline value class` instead
  of raw `String`, `Int` or `Boolean`. Parse wire values at the `data/` boundary.
- Prefer AndroidX/Jetpack or a maintained library over a hand-written helper. Versions go only in
  `gradle/libs.versions.toml`.
- Room 3 (`androidx.room3`): change entities or DAOs, bump the database version and provide a migration;
  `app/schemas/` is generated. Steps: `docs/module-guides.md`.
