# MiniCompose Agent Guide

## Overview
MiniCompose is a small Kotlin/JVM learning project that builds a Compose-like runtime in small, inspectable steps.

Stable facts:
- Kotlin/JVM on Java 21
- Swing as the UI host
- Single-module Gradle project
- Canonical roadmap in `project_plan.md`

## What Matters Most
- Keep changes small and tied to the roadmap.
- Prefer explicit runtime behavior over hidden magic.
- Preserve the separation between:
  - UI tree/model code
  - Swing rendering code
  - demo entrypoint code
  - tests
- Do not add Compose-style syntax or state/runtime machinery unless the roadmap step calls for it.

## Repo Layout
- `src/main/kotlin/com/kkoser/minicompose`
  - app entrypoint and demo wiring
- `src/main/kotlin/com/kkoser/minicompose/ui`
  - `UiNode` model and Swing renderer
- `src/test/kotlin/com/kkoser/minicompose`
  - app-level tests
- `src/test/kotlin/com/kkoser/minicompose/ui`
  - renderer tests
- `project_plan.md`
  - canonical milestone plan

## Useful Commands
- Run tests:
  - `GRADLE_USER_HOME=/tmp/minicompose-gradle ./gradlew --no-daemon test`
- Run the app:
  - `GRADLE_USER_HOME=/tmp/minicompose-gradle ./gradlew --no-daemon run`
- Check status:
  - `git status --short`

If Gradle needs to download or reuse artifacts, prefer `GRADLE_USER_HOME=/tmp/minicompose-gradle` so the workspace stays clean.

## Screenshot Workflow
The repo currently uses a headless offscreen render for visual verification when a real desktop session is unavailable.

Recommended flow:
1. Build or run the app code.
2. Render the Swing content panel offscreen from the real app code.
3. Write the image artifact under `output/playwright/<step>/`.
4. Inspect the PNG before concluding the task.

Practical notes:
- A real GUI screenshot is preferred when a display server is available.
- If the environment is headless, it is acceptable to render the panel offscreen and capture that image instead.
- Keep generated artifacts out of version control.

## Working Conventions
- Use `apply_patch` for repo edits.
- Keep code comments short and only where they help explain non-obvious logic.
- Avoid adding new dependencies unless a roadmap step needs them.
- Prefer tests that validate the node tree and renderer separately.
- If a change affects rendering, verify with both tests and a visual capture when possible.

## Future Work Expectations
When adding new runtime features, keep the plan visible in code and tests:
- model first
- renderer second
- composition/runtime after the renderer works

If a future change touches screenshot behavior or visual verification, update this guide so the workflow stays discoverable.
