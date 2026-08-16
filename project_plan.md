# MiniCompose Project Plan

## Goal
Build a small Compose-like runtime in Kotlin/JVM as a learning project. The purpose is to understand the core mechanics behind Jetpack Compose by implementing them in small, inspectable steps rather than recreating the entire framework.

## Learning Principles
- Prefer explicit runtime mechanics over ergonomic magic.
- Keep milestones narrow enough that each one teaches a single idea clearly.
- Separate rendering concerns from composition/runtime concerns so failures are easier to reason about.
- Delay optimization and advanced features until the basic runtime model is understandable.

## Platform and Repo Direction
- Platform: Kotlin/JVM
- UI host: Swing
- Build: Gradle
- JDK target: 21
- Repo structure: single module initially

Swing is the preferred host because it gives a visible UI with minimal platform overhead. The repo should stay single-module at first so the focus stays on runtime behavior rather than project structure.

## Scope for the First Learning Version
- `Text`
- `Button`
- `Column`
- `Row`
- `mutableStateOf`-style state
- `remember`
- composition and recomposition
- basic layout
- basic event handling
- minimal debugging visibility

## Non-Goals for V1
- No compiler plugin
- No real `@Composable` transformation
- No modifiers chain
- No effects API
- No theming system
- No animation
- No multiplatform support
- No large widget set
- No attempt to match Jetpack Compose internals exactly

## Architecture Overview
1. State system
   - Observable mutable state
   - Read tracking during composition
   - Invalidation on writes

2. Composition engine
   - Execute screen functions through an explicit composer/context
   - Build a UI tree
   - Schedule recomposition

3. Slot storage
   - Back `remember`
   - Preserve values across recompositions
   - Keep storage tied to stable scope identity and slot order

4. Renderer bridge
   - Convert the runtime UI tree into Swing components
   - Update the visible UI from runtime output

5. Layout primitives
   - Represent `Column` and `Row` in the UI tree
   - Keep layout intent explicit even if Swing performs the actual widget layout

6. Debugging support
   - Recomposition counters
   - Dirty-scope logs
   - Optional tree dumps

## API Direction
The first version should not pretend to be real Compose syntax. Because there is no compiler plugin, the runtime API should stay explicit and teachable.

Planned shape:
- Root execution through a render/composition entrypoint
- Primitive functions such as `Text(composer, text)` and `Button(composer, text, onClick)`
- Container functions such as `Column(composer) { ... }` and `Row(composer) { ... }`
- State through `mutableStateOf(initial)` and `state.value`
- Memory through `remember(composer) { ... }`

This keeps state tracking, slot access, and recomposition visible instead of hidden behind transformed callsites.

## Milestones

### Phase 0: Repo Integration
Purpose:
Prepare the repo for the learning project without over-designing the structure.

Planned work:
- Convert the current Java-only Gradle setup to Kotlin/JVM
- Add an application entrypoint
- Create `src/main/kotlin` and `src/test/kotlin`
- Organize code by package, not subprojects
- Store this roadmap in the repo as the canonical plan

Deliverable:
- A Kotlin/JVM project ready for incremental runtime implementation

### Phase 1: Manual UI Tree to Swing
Purpose:
Learn the renderer boundary independently from composition/runtime logic.

Planned work:
- Define a minimal in-memory `UiNode` model
- Include node types for `Text`, `Button`, `Column`, and `Row`
- Hand-construct a small static UI tree in the demo entrypoint
- Implement a Swing renderer that turns that tree into visible components

Deliverable:
- A window showing static text and a button rendered from a manually built tree

Why this is separate:
- It proves the node model and renderer work before introducing a composer
- It isolates rendering bugs from runtime bugs

### Phase 2: Composer Builds the Same Static Tree
Purpose:
Introduce declarative runtime execution without adding state yet.

Planned work:
- Add a `Composer` or `CompositionContext`
- Implement primitive UI functions that emit `UiNode`s through the composer
- Build the same tree from runtime execution instead of manual construction
- Reuse the same Swing renderer from Phase 1

Deliverable:
- The same static UI now comes from MiniCompose runtime execution

Why this is separate:
- It teaches how a declarative API lowers into a runtime tree
- It keeps rendering stable while composition logic is introduced

### Phase 3: State and Full-Root Recomposition
Purpose:
Teach how state invalidates composition and triggers rerendering.

Planned work:
- Implement `MutableState<T>` and `mutableStateOf`
- Track reads during composition
- Mark the root composition dirty on writes
- Rerun the full composition and rerender the whole tree

Deliverable:
- A counter demo where button clicks update visible text through full rerender

### Phase 4: Scopes and `remember`
Purpose:
Teach how composition-local memory survives recomposition.

Planned work:
- Introduce stable scope identity based on execution path
- Add slot storage
- Implement `remember`
- Preserve remembered values while scope structure and slot order remain stable

Deliverable:
- `remember` works and can be explained in terms of scope plus slot index

### Phase 5: Layout Primitives
Purpose:
Make tree structure more expressive and closer to Compose basics.

Planned work:
- Flesh out `Column` and `Row`
- Support nested layouts and simple spacing rules
- Keep layout intent visible in the node model

Deliverable:
- Predictable nested row/column demos

### Phase 6: Event Flow and Debug Visibility
Purpose:
Make user interaction and recomposition behavior easier to inspect.

Planned work:
- Route Swing events back into runtime callbacks
- Add recomposition counters
- Add invalidation logs
- Add optional UI tree dump output

Deliverable:
- An interactive demo where recomposition behavior is inspectable

### Phase 7: Scoped Recomposition
Purpose:
Teach how recomposition can avoid re-executing unaffected subtrees.

Planned work:
- Track invalidation at the scope level
- Re-execute affected scopes in supported cases
- Keep full-root recomposition as a safe fallback
- Add instrumentation that makes reused versus recomposed scopes visible
- Add demos and tests that prove unaffected sibling scopes are skipped

Deliverable:
- Demos showing common cases where unaffected sibling scopes are skipped

Why this is separate:
- It teaches scoped recomposition before introducing more invasive storage changes
- It keeps the first partial-recomposition implementation focused on scheduling and reuse behavior

### Phase 8: Slot Table and Structural Reuse
Purpose:
Revisit storage and tree reuse only after scoped recomposition has shown its needs.

Planned work:
- Replace the simplified remember storage with a more Compose-like slot-table structure
- Preserve reuse across more structural cases where path-based storage becomes limiting
- Tighten the rules for cache reuse when scope structure changes

Deliverable:
- A runtime storage model that supports the scoped recomposition rules without overfitting the first implementation

Why this is separate:
- The slot-table rewrite is larger and easier to understand after scoped recomposition already works
- It avoids coupling the first scoped recomposition milestone to a storage redesign

### Phase 9: Parallel Composition Exploration
Purpose:
Explore whether independent parts of the tree can be composed concurrently without obscuring the runtime model.

Planned work:
- Identify safe boundaries for parallel execution of independent scopes
- Experiment with scheduling scope recomposition work across threads or tasks
- Keep deterministic behavior and the existing fallback path intact
- Measure whether the added complexity is justified for this learning project

Deliverable:
- A documented experiment showing which parts of the runtime can or cannot benefit from parallel composition

Why this is separate:
- Parallel composition is a runtime experiment, not a prerequisite for scoped recomposition
- It is easier to reason about after the scope model and reuse rules are already in place

### Phase 10: Compiler Plugin and Compose Parity
Purpose:
Move beyond the explicit teaching API and explore how a compiler plugin changes the runtime model and bring the behavior closer to real Compose in a controlled major step.

Current progress:
- A Kotlin compiler-plugin lowers the primitive API to composer-aware runtime calls.
- `@MiniComposable` callsites now lower to explicit runtime call groups that capture inputs, retain cached output, and skip clean function bodies.
- Call-group `remember` slots and state reads are isolated from their parent scope.
- Generated cleanup uses `try`/`finally`, and lowering preserves named-argument evaluation order.
- Repeated callsites currently use call-order identity; callers use `key(...)` when they need identity to survive reordering.

Remaining work:
- Extend input comparison to every reusable runtime node group, beginning with `Column` and `Row` spacing, so cached layout nodes cannot retain stale arguments.
- Model restartable and skippable groups more explicitly in debug output and tests.
- Revisit `remember`, keys, and scope identity to match Compose behavior more closely.
- Compare the explicit runtime behavior with Compose semantics to highlight what changes and why.

Deliverable:
- A prototype that shows how a compiler plugin changes callsite lowering and recomposition behavior compared with the explicit runtime version

Why this is separate:
- It is a major conceptual step beyond the teaching runtime
- The explicit runtime model should be understood first so the plugin’s effect is obvious and educational

## Suggested Package Organization
- `runtime`
  - composer/composition lifecycle
  - state and invalidation
  - scopes and slots
- `ui`
  - UI node model
  - Swing renderer/applier
  - layout node types
- `demo`
  - entrypoint
  - sample screens used to learn and verify behavior

## Testing Strategy
- Verify manual UI tree rendering independently from composition
- Verify composer-built trees match expected structure
- Verify state reads and writes register and invalidate correctly
- Verify `remember` preserves values across recomposition when scope order is stable
- Verify button clicks trigger state changes and rerendering
- Verify scoped recomposition skips unaffected siblings in supported cases
- Verify slot-table reuse still preserves remembered values in stable scopes
- Verify any parallel composition experiment preserves correctness and fallback behavior

## Definition of Success
The project is successful if it becomes easy to explain and inspect:
- how a declarative screen function becomes a UI tree
- how that tree is rendered into Swing components
- how state reads are tracked
- how writes trigger invalidation
- how `remember` stores and restores values
- how recomposition is scheduled
- how scoped recomposition differs from full-root rerendering

## Implementation Notes for Future Sessions
- Keep milestones small and testable
- Prefer a working but naive version before optimizing behavior
- Avoid introducing compiler-plugin ideas until the runtime model is clearly understood
- If a milestone mixes two kinds of fundamentals, split it before implementing
