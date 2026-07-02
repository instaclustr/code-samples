# A2A Diagrams Guide

This folder contains a layered set of diagrams for explaining the A2A protocol from concept to implementation.

## Recommended Reading Order (for blog/story flow)

| Order | Diagram | Purpose |
|---|---|---|
| 1 | `a2a-components-uml` | Show the major protocol/system components and boundaries |
| 2 | `a2a-runtime-sequence` | Show runtime interaction flow across participants |
| 3 | `a2a-task-lifecycle-strict` | Show task state semantics with spec-style naming |
| 4 | `a2a-object-model-simple` | Introduce the core object relationships quickly |
| 5 | `a2a-object-model` | Deep implementation-level object model |
| — | `a2a-hero-part2-nouns` | **Blog hero** — Part 2 object model (discovery → dialogue → deliverable) |
| — | `a2a-hero-part3-runtime-fork` | **Blog hero** — Part 3 runtime (discovery → Message/Task fork → poll/stream/push) |
| — | `a2a-hero-part5-java-three-examples` | **Blog hero** — Part 5 Clockwork Agent (three examples: time, countdown, confirm) |
| — | `a2a-ecosystem-landscape` | **LinkedIn / reference** — A2A support breadth (standard → backers → SDKs → frameworks → gaps) |

**Word / print exports:** use `*-word.png` (3–4× scale). Insert via **Insert → Pictures** — avoid pasting from clipboard, which recompresses. SVG also works in recent Word if inserted as a file.

---

## Diagram Summaries

### 1) `a2a-components-uml`
**Meaning**  
High-level architecture: user/app, client agent, remote A2A server, task manager, artifact store, push/stream channel, protocol bindings, and security boundary.

**How to interpret**  
Read as component responsibilities and boundaries (what lives where), not step-by-step runtime timing.

**How it helps understand A2A**  
Clarifies A2A as an interoperability protocol layer between agents.

**How a developer uses it**  
Use as a module map when scaffolding example code (`agent-card`, `operations`, `task-store`, `events`, `security`).

---

### 2) `a2a-runtime-sequence`
**Meaning**  
Timeline view of request handling: discovery, `SendMessage`, immediate vs task-based path, updates, polling fallback, artifacts, and cancel.

**How to interpret**  
Top-to-bottom timeline. `alt/par/loop/opt` blocks represent branching, parallel paths, polling loops, and optional behavior.

**How it helps understand A2A**  
Shows core runtime contract: response can be immediate (`Message`) or long-running (`Task` + events).

**How a developer uses it**  
Translate directly into handlers/client logic (`SendMessage`, `GetTask`, `CancelTask`, streaming/subscription/webhook behavior).

---

### 3) `a2a-task-lifecycle` (friendly)
**Meaning**  
Human-readable lifecycle labels (`Submitted`, `Working`, `InputRequired`, terminal outcomes).

**How to interpret**  
State machine with allowed transitions and terminal states.

**How it helps understand A2A**  
Easy conceptual model for readers new to protocol state.

**How a developer uses it**  
Good for dashboard labels, demo output, and documentation for non-protocol experts.

---

### 4) `a2a-task-lifecycle-strict` (spec-style)
**Meaning**  
Same lifecycle using spec-style names (`submitted`, `working`, `input-required`, etc.).

**How to interpret**  
Use as the canonical naming reference for protocol-level behavior.

**How it helps understand A2A**  
Connects conceptual lifecycle to exact wire/protocol vocabulary.

**How a developer uses it**  
Drive enum/constants, transition tests, and contract validation.

---

### 5) `a2a-object-model-simple`
**Meaning**  
Minimal object relationship map (`AgentCard`, `Message`, `Task`, `Part`, `Artifact`, update events).

**How to interpret**  
A compact “mental model” before diving into full schema detail.

**How it helps understand A2A**  
Reduces cognitive load for intro audiences.

**How a developer uses it**  
Start here when building a PoC with just the essential DTOs.

---

### 6) `a2a-object-model` (detailed)
**Meaning**  
Detailed UML-style data model including `TaskStatus`, `TaskState`, `MessageRole`, `Part` subtypes, request/config objects, and event objects.

**How to interpret**  
Pay attention to composition/association and cardinality to understand ownership and multiplicity.

**How it helps understand A2A**  
Shows A2A as a coherent object graph, not just endpoint names.

**How a developer uses it**  
Use as a blueprint for class design, schema mapping, serialization, and test fixtures.

---

## Which Diagram to Use Where

| Audience / Section | Best Diagram(s) |
|---|---|
| Intro “what is A2A?” | `a2a-components-uml` |
| “How it works at runtime” | `a2a-runtime-sequence` |
| “Task semantics / state transitions” | `a2a-task-lifecycle-strict` |
| “Data model quick view” | `a2a-object-model-simple` |
| “Implementation deep dive / appendix” | `a2a-object-model` |

---

## Assets and Formats

Each diagram is provided as:
- `.mmd` source (editable)
- `.svg` (best quality for docs/Word where supported)
- `.png` (high-resolution export)

---

## Draft Blog Outline (with diagram placement)

Use this as a starting structure for an intro post on A2A.

### 1) Why agent interoperability matters
- Problem: useful agents are often siloed by framework/vendor.
- Position A2A as a protocol layer for agent-to-agent collaboration.
- Keep this section conceptual and short.

**Diagram:** none (or a simple title visual)

### 2) What A2A is (and is not)
- Define A2A in one paragraph.
- Clarify A2A vs MCP (agent-to-agent vs agent-to-tool).
- Explain that A2A focuses on interoperable communication contracts.

**Diagram:** `a2a-components-uml`

### 3) Core building blocks
- Introduce the key protocol objects:
  - `AgentCard`
  - `Message`
  - `Task`
  - `Artifact`
  - `Part`
- Explain each in one or two lines.

**Diagram:** `a2a-object-model-simple`

### 4) Runtime request/response flow
- Walk through discovery and task initiation:
  - discover card
  - `SendMessage`
  - immediate `Message` vs long-running `Task`
- Show update strategies:
  - streaming/push
  - polling fallback (`GetTask`)
- Mention cancellation (`CancelTask`) and terminal outcomes.

**Diagram:** `a2a-runtime-sequence`

### 5) Task lifecycle semantics
- Explain state transitions and why they matter for clients.
- Distinguish friendly vs strict state naming for docs vs code.
- Tie lifecycle handling to robust UX and retries.

**Diagram:** `a2a-task-lifecycle-strict`  
Optional side-by-side for non-technical readers: `a2a-task-lifecycle`

### 6) From protocol to implementation
- Map protocol concepts to code modules:
  - card/discovery
  - operations client/server handlers
  - task state store
  - eventing pipeline
  - artifact persistence
- Show how DTO/schema choices follow object relationships.

**Diagram:** `a2a-object-model` (detailed)

### 7) Common implementation pitfalls
- Confusing immediate message responses with task-based responses.
- Not handling `input-required` loops.
- Weak handling of terminal states (`failed`, `canceled`, `rejected`).
- Inconsistent naming/enums between docs and wire models.

**Diagram:** optional callout from `a2a-runtime-sequence` and `a2a-task-lifecycle-strict`

### 8) Conclusion and next steps
- Summarize A2A mental model in three bullets.
- Point to follow-up content:
  - transport bindings
  - production hardening
  - Kafka-based implementation pattern

**Diagram:** optionally reuse `a2a-components-uml` as closing recap

---

## Optional Post Variants

- **Executive/intro audience:** use `a2a-components-uml` + `a2a-object-model-simple` only.
- **Developer audience:** include all five diagrams.
- **Implementation deep dive:** prioritize `a2a-runtime-sequence`, `a2a-task-lifecycle-strict`, and `a2a-object-model`.

