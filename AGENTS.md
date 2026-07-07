# AGENTS.md

Guidance for AI coding agents (and humans) working in the **AiChat** repository.

## What this app is

AiChat is a native **Android** application (Kotlin + Jetpack Compose) that provides a chat
assistant with two interchangeable inference backends and an extensible **Skills** system:

- **Cloud chat** — talks to **Gemini 2.0 Flash** via **Firebase AI Logic**. Requires internet
  and a configured Firebase project.
- **On-device chat** — runs **Gemma 4 E4B** (`.litertlm`) fully locally via **MediaPipe Tasks
  GenAI**, so conversations never leave the device once the model is downloaded. Supports
  multimodal prompts (image + text) and on-device voice input.
- **Skills** — user-authored markdown files (stored in the app's private `files/skill/`
  directory) that give the assistant reusable instructions, personas, or domain knowledge.
  Both chat backends can list/read/create/update/delete skills through tool/function calling,
  and the same operations are exposed to the OS as **Android AppFunctions** so external system
  agents can manage skills on the user's behalf.

See `README.md` for the full feature list, setup instructions, and architecture diagram.

## Tech stack

- **Language:** Kotlin, JDK 17
- **UI:** Jetpack Compose, Material 3
- **Navigation:** Navigation 3 (typed `NavKey` routes)
- **DI:** Hilt
- **Local persistence:** Realm Kotlin (chat sessions/messages)
- **Cloud AI:** Firebase AI Logic (Gemini)
- **On-device AI:** MediaPipe Tasks GenAI + Tasks Vision (Gemma)
- **Agent integration:** Android AppFunctions
- **Async:** Kotlin Coroutines + Flow
- **Serialization:** Kotlinx Serialization (JSON, and typed nav routes)

## Project layout

```
app/src/main/java/com/rama/aichat/
├── appfunctions/     # AppFunctions entry points + Gemma JSON tool catalog
├── data/
│   ├── model/        # Realm models: ChatSession, ChatMessage, SkillFile
│   └── repository/   # ChatRepository (Realm), SkillRepository (markdown files)
├── di/               # Hilt modules: DatabaseModule, InferenceModule
├── inference/        # GeminiChatManager, GemmaInferenceManager, GemmaToolChatManager,
│                      # ImageAttachmentManager, VoiceInputManager
├── navigation/       # NavKey route definitions
└── ui/
    ├── chat/         # ChatScreen (Gemini) / ChatScreenV2 (Gemma) + ViewModels
    ├── components/   # AppDrawer, MessageBubble, SkillGridItem, BitmapLoader
    ├── skill/        # Skill list & edit screens/ViewModels
    └── theme/        # Compose theme (Color, Type, Theme)
```

Key files worth reading before making changes in a given area:

- `inference/GeminiChatManager.kt` — Firebase AI Gemini integration + tool-calling loop.
- `inference/GemmaToolChatManager.kt` — JSON-based tool-calling protocol for on-device Gemma
  (parses a tool call from the raw model output, executes it, feeds the result back in, loops
  up to `MAX_TOOL_CALLS_PER_TURN`).
- `inference/GemmaInferenceManager.kt` — MediaPipe LLM inference session lifecycle, model
  download/init, generation parameters (max tokens 1024, top-K 40, temperature 0.8).
- `appfunctions/SkillFunctions.kt` — `@AppFunction`-annotated skill operations (`listSkills`,
  `getSkillContent`, `createSkill`, `updateSkill`, `deleteSkill`) exposed to the OS; also called
  internally by both chat managers via `SkillFunctionCatalog`.
- `data/repository/SkillRepository.kt` — filesystem-backed CRUD for markdown skill files;
  validates file names (letters, numbers, hyphens, underscores only).
- `data/repository/ChatRepository.kt` — Realm-backed persistence for chat sessions/messages.

## Build & test commands

Run from the repository root (Windows: use `gradlew.bat`; macOS/Linux: `./gradlew`).

```bash
./gradlew :app:assembleDebug      # build debug APK
./gradlew :app:testDebugUnitTest  # run JVM unit tests
./gradlew :app:connectedAndroidTest # run instrumented tests (needs device/emulator)
./gradlew :app:lint               # Android lint
```

Building requires a `google-services.json` in `app/` (Firebase config, gitignored — not
committed). Without it, builds that depend on the Google Services plugin will fail; this is
expected in environments without Firebase credentials.

## Conventions

- Follow standard Kotlin style already used in the codebase (trailing commas avoided,
  4-space indent, constructor injection via `@Inject`, `@Singleton` for app-scoped managers).
- New skill-related capabilities should be added as methods on `SkillRepository` first, then
  wired into both `SkillFunctions` (AppFunctions/Gemini tool surface) and
  `SkillFunctionCatalog`/`GemmaToolChatManager` (on-device tool surface) so both backends and
  the OS-level AppFunctions stay in sync.
- Keep KDoc on `@AppFunction`-annotated methods in `SkillFunctions.kt` accurate — it is used
  directly as the function description for external agents (`isDescribedByKDoc = true`).
- Don't commit large binary model files (e.g. `.litertlm`); the `models/` directory is for
  local development only and is not pushed to the repository.
- Don't commit `app/google-services.json` or other secrets.

## Things to avoid

- Don't check in build output (`app/build/`, `.gradle/`) — these are generated and ignored.
- Don't hardcode Firebase/Gemini API keys or model URLs outside of the existing configuration
  points (`GeminiChatManager`, `GemmaInferenceManager`).
- Don't bypass `SkillRepository`'s file name validation when adding new skill entry points.
