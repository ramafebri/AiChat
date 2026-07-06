# AiChat

AiChat is an Android chat application that combines **cloud AI** and **on-device AI** with a customizable **Skills** system. Users can chat with Gemini over the internet, run Gemma locally without sending messages to the cloud, and manage markdown skill files that give the assistant structured instructions and context.

---

## Overview

AiChat is built for users who want a flexible AI assistant on Android with two inference backends and extensible behavior through skills.

| Mode | Model | Where it runs | Internet required |
|------|-------|---------------|-------------------|
| **Chat** | Gemini 2.0 Flash | Google AI via Firebase | Yes |
| **On-device (Gemma)** | Gemma 4 E4B (`.litertlm`) | On the phone via MediaPipe | No (after model download) |

Both chat modes can **manage skills through natural language** — the AI can list, read, create, update, and delete skill files on your behalf using tool/function calling.

---

## Features

### Dual chat experiences

**Cloud chat (Gemini)**  
- Uses Firebase AI Logic with the `gemini-2.0-flash` model.  
- Streams responses into the conversation UI.  
- Supports multi-turn conversations with session history stored locally.  
- Can call skill-management tools (`listSkills`, `getSkillContent`, `createSkill`, `updateSkill`, `deleteSkill`) and loop until a final answer is ready.

**On-device chat (Gemma)**  
- Runs inference locally with [MediaPipe Tasks GenAI](https://developers.google.com/mediapipe/solutions/genai/llm_inference).  
- Downloads the Gemma 4 E4B LiteRT model (~3.5 GB) on first use from Hugging Face, or accepts a sideloaded model file.  
- Shows download and initialization progress in the UI.  
- Uses a JSON-based tool-calling protocol so Gemma can perform the same skill operations as Gemini.  
- Works offline once the model is on the device.

### Skills

Skills are **markdown files** stored in the app’s private storage (`files/skill/`). Each skill is a reusable instruction or context document — for example, coding style rules, persona prompts, or domain knowledge.

You can manage skills in two ways:

1. **Skills screen** — browse skills in a grid, create, edit, and delete them manually.  
2. **Through chat** — ask either Gemini or Gemma to create or update skills in natural language.

Skill file names must use letters, numbers, hyphens, and underscores only (e.g. `my-coding-style.md`).

### Chat sessions

- Conversations are persisted with **Realm Kotlin**.  
- Multiple sessions are supported; the navigation drawer lists recent chats.  
- The first user message in a session becomes the session title (truncated to 50 characters).  
- Sessions can be deleted from the drawer.

### AppFunctions (system integration)

AiChat exposes skill operations as **Android AppFunctions** (`SkillFunctions`). These allow authorized system agents and assistants (such as Google Gemini on supported devices) to manage skills on the user’s behalf using documented, typed functions:

- `listSkills`
- `getSkillContent`
- `createSkill`
- `updateSkill`
- `deleteSkill`

App metadata in `app_metadata.xml` describes the app’s purpose for the Android agent ecosystem.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        MainActivity                          │
│  Modal drawer │ Navigation 3 │ Compose screens             │
└───────────────┬─────────────────────────┬───────────────────┘
                │                         │
     ┌──────────▼──────────┐   ┌──────────▼──────────┐
     │     ChatScreen      │   │    ChatScreenV2     │
     │   (Gemini / cloud)  │   │  (Gemma / on-device)│
     └──────────┬──────────┘   └──────────┬──────────┘
                │                         │
     ┌──────────▼──────────┐   ┌──────────▼──────────┐
     │   ChatViewModel     │   │  GemmaChatViewModel │
     └──────────┬──────────┘   └──────────┬──────────┘
                │                         │
     ┌──────────▼──────────┐   ┌──────────▼──────────┐
     │  GeminiChatManager  │   │ GemmaToolChatManager│
     │  (Firebase AI tools)│   │ (JSON tool calling) │
     └──────────┬──────────┘   └──────────┬──────────┘
                │                         │
                └────────────┬────────────┘
                             │
                  ┌──────────▼──────────┐
                  │   SkillRepository   │
                  │  (markdown files)   │
                  └─────────────────────┘

     ┌──────────────────────────────────┐
     │         ChatRepository           │
     │    (Realm — sessions/messages)   │
     └──────────────────────────────────┘
```

### Tech stack

| Layer | Technology |
|-------|------------|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Navigation | Navigation 3 (`NavKey` + typed routes) |
| DI | Hilt |
| Local database | Realm Kotlin |
| Cloud AI | Firebase AI Logic (Gemini) |
| On-device AI | MediaPipe Tasks GenAI |
| Agent API | Android AppFunctions |
| Async | Kotlin Coroutines, Flow |
| Serialization | Kotlinx Serialization |

### Project structure

```
app/src/main/java/com/rama/aichat/
├── appfunctions/     # AppFunctions + Gemma tool catalog
├── data/
│   ├── model/        # Realm models (ChatSession, ChatMessage, SkillFile)
│   └── repository/   # ChatRepository, SkillRepository
├── di/               # Hilt modules (Database, Inference)
├── inference/        # GeminiChatManager, GemmaInferenceManager, GemmaToolChatManager
├── navigation/       # NavKey routes
└── ui/
    ├── chat/         # Chat screens + ViewModels
    ├── components/   # Drawer, message bubbles, skill grid items
    ├── skill/        # Skill list & edit screens
    └── theme/        # Compose theme
```

---

## Requirements

- **Android Studio** (recent version with AGP 9.x support)
- **JDK 17**
- **Android device or emulator** — API 24+ (Android 7.0); target SDK 37
- **Firebase project** — required for cloud chat (Gemini)
- **~3.5 GB free storage** — required for the on-device Gemma model
- **Internet** — required for Gemini chat and for the initial Gemma model download

---

## Setup

### 1. Clone the repository

```bash
git clone https://github.com/ramafebri/AiChat.git
cd AiChat
```

### 2. Configure Firebase (cloud chat)

1. Create a project in the [Firebase Console](https://console.firebase.google.com/).  
2. Add an Android app with package name `com.rama.aichat`.  
3. Download `google-services.json` and place it in the `app/` directory.  
4. Enable **Firebase AI** / Google AI backend for your project.

> `google-services.json` is listed in `app/.gitignore` and is not committed to the repository.

### 3. Build and run

Open the project in Android Studio and run the `app` module on a device or emulator:

```bash
./gradlew :app:assembleDebug
```

Or use **Run** in Android Studio.

### 4. On-device model (Gemma)

On first launch of **On-device (Gemma)** chat, the app downloads:

- **File:** `gemma-4-E4B-it.litertlm`  
- **Source:** [litert-community/gemma-4-E4B-it-litert-lm](https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm)

If the download fails or you prefer manual installation, sideload the model with ADB:

```bash
adb push gemma-4-E4B-it.litertlm /data/data/com.rama.aichat/files/gemma-4-E4B-it.litertlm
```

You can also place the file in a local `models/` folder during development (this folder is not pushed to GitHub due to file size limits).

---

## Usage

### Navigation drawer

Open the drawer from any screen to switch between:

- **Chat** — Gemini cloud assistant  
- **On-device (Gemma)** — local Gemma assistant  
- **Skills** — manage markdown skill files  

The drawer also shows saved chat sessions, with options to start a new chat or delete a session.

### Managing skills manually

1. Open **Skills** from the drawer.  
2. Tap **+** to create a new skill, or tap an existing skill to edit.  
3. Write markdown content and save.  
4. Use the delete action to remove a skill (with confirmation).

### Managing skills via chat

Example prompts:

- *“List all my skills.”*  
- *“Create a skill called `kotlin-style` with guidelines for writing clean Kotlin.”*  
- *“Update `my-skill.md` to add a section about error handling.”*  
- *“Delete the skill `old-notes.md`.”* (the assistant should confirm before deleting)

---

## Permissions

| Permission | Purpose |
|------------|---------|
| `INTERNET` | Gemini API, Gemma model download |

---

## Configuration notes

### Gemma inference settings

Defined in `GemmaInferenceManager`:

- Max tokens: `1024`  
- Top-K: `40`  
- Temperature: `0.8`  

### Gemini model

Configured in `GeminiChatManager` as `gemini-2.0-flash` with a system instruction describing the app and skill tools.

---

## License

No license file is included yet. Add one if you plan to open-source or distribute the project.

---

## Author

Built by [ramafebri](https://github.com/ramafebri).
