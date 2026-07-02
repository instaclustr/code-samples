# A2A Protocol: A Practical Introduction for Builders

When teams start building agentic systems seriously, they quickly run into an interoperability problem. One team uses one framework, another team uses something else, and each agent stack has its own assumptions about state, messages, tools, and transport. You can wire them together manually, but the integration cost scales badly.

That is the problem the **Agent2Agent (A2A) Protocol** is designed to solve: a shared communication contract for agent-to-agent collaboration across implementation boundaries.

This article gives a practical introduction to A2A, with a focus on what engineering teams need to understand before writing code:

- what A2A is and is not,
- the core objects in the protocol,
- how runtime interactions work,
- and how to turn those concepts into implementation modules.

---

## 1) Why agent interoperability matters

Most agent projects start in a single runtime with a single framework. That is fine for a prototype, but production systems tend to become multi-agent and multi-team:

- one agent specializes in document analysis,
- another handles workflow orchestration,
- another owns retrieval from domain systems.

Without a protocol, every handoff is ad hoc. You get custom payload shapes, inconsistent status semantics, and one-off retry logic. With A2A, the aim is not to force all agents into one stack, but to give them a common language for collaboration.

---

## 2) What A2A is (and is not)

A2A is an **inter-agent protocol**, not an agent framework. It standardizes how agents discover each other, exchange messages, track tasks, and deliver artifacts.

It is also useful to be explicit about boundaries:

- **A2A is not a replacement for MCP.**  
  MCP standardizes agent-to-tool communication. A2A standardizes agent-to-agent communication.
- **A2A is not a framework SDK.**  
  You can implement A2A from LangGraph, ADK, custom Java services, or anything else.
- **A2A is not an end-user chat product.**  
  It is a machine-to-machine interoperability layer.

For a visual orientation, see:

**Diagram:** `docs/diagrams/a2a-components-uml.png`

That component view is intentionally architectural: where responsibilities sit, what boundaries matter, and which parts of the system hold task state, artifacts, and update channels.

---

## 3) Core building blocks

A2A has a small set of objects that carry most of the protocol’s meaning:

- **AgentCard**: discovery and capability metadata
- **Message**: a turn in communication (`user` or `agent`) with one or more content parts
- **Task**: stateful unit of work
- **Artifact**: output produced by task execution
- **Part**: modality container (text, file, structured data, etc.)

If you are new to A2A, begin with the simplified object model:

**Diagram:** `docs/diagrams/a2a-object-model-simple.png`

This is the best mental model for first contact: `SendMessageRequest` carries a `Message`, tasks collect history and outputs, and update events reference tasks/artifacts as execution progresses.

For implementation detail (status objects, event payload shapes, configuration objects), use:

**Diagram:** `docs/diagrams/a2a-object-model.png`

---

## 4) Runtime interaction flow

The easiest way to understand A2A behavior is as a runtime sequence:

1. Client discovers a remote agent via `AgentCard`.
2. Client calls `SendMessage`.
3. Server responds with either:
   - an immediate `Message`, or
   - a `Task` for long-running work.
4. Client tracks progress via streaming/push events or polling (`GetTask`).
5. Task reaches a terminal state and returns artifacts/results.
6. Optional cancellation via `CancelTask`.

**Diagram:** `docs/diagrams/a2a-runtime-sequence.png`

Two practical points matter here:

- **Immediate and task-based responses are both normal.**  
  Client code should model this explicitly, not as an edge case.
- **Update channel strategy should be deliberate.**  
  Streaming/push gives richer UX for long-running tasks; polling is a fallback, not a first choice for high-throughput scenarios.

---

## 5) Task lifecycle semantics

Task lifecycle semantics are where many implementations become brittle. You need predictable state handling to support retries, user prompts, cancel flows, and terminal behavior.

For protocol-aligned state naming:

**Diagram:** `docs/diagrams/a2a-task-lifecycle-strict.png`

The strict lifecycle makes it clear that:

- `submitted` and `working` are not terminal,
- `input-required` is a control state that expects another client turn,
- terminal outcomes include `completed`, `failed`, `canceled`, and `rejected`.

If you want a friendlier visual for broader audiences, use:

**Diagram:** `docs/diagrams/a2a-task-lifecycle.png`

---

## 6) From protocol concepts to code modules

A2A is much easier to implement when you map protocol objects to explicit modules from day one. A common structure:

- **discovery module**  
  `AgentCard` fetch, parsing, capability checks
- **operations client**  
  `SendMessage`, `GetTask`, `CancelTask`, subscription/push setup
- **task state module**  
  state transitions, terminal checks, idempotency guards
- **event handling module**  
  status/artifact update processing
- **artifact module**  
  artifact persistence, indexing, retrieval
- **model module**  
  protocol DTOs/enums/validation

Use the detailed object model diagram as the source of truth for DTO boundaries and relationship cardinality:

**Diagram:** `docs/diagrams/a2a-object-model.png`

This helps avoid common anti-patterns like collapsing task state into loosely typed maps or mixing protocol transport concerns with domain logic.

---

## 7) Common implementation pitfalls

Teams repeatedly run into the same issues:

1. **Treating `SendMessage` as always synchronous**  
   Long-running operations require robust task handling and lifecycle awareness.

2. **Under-specifying `input-required` loops**  
   If you do not explicitly handle follow-up turns, interactions stall.

3. **Inconsistent state naming between docs and code**  
   Use strict protocol enums/constants in code paths and tests.

4. **Weak terminal state handling**  
   Clients often handle `completed` but forget strong behavior for `failed`, `canceled`, `rejected`.

5. **Conflating A2A and tool invocation contracts**  
   Keep A2A (agent-to-agent) and MCP/tool semantics cleanly separated in architecture.

---

## 8) Where this goes next

For an intro post, this is enough to build a durable mental model:

- components and boundaries,
- runtime interaction sequence,
- task lifecycle semantics,
- object relationships for implementation.

From here, useful follow-on deep dives are:

- transport/binding choices (JSON-RPC, HTTP/REST, gRPC),
- production hardening (timeouts, retries, auth, observability),
- and protocol-to-platform mappings (for example, event streaming backplanes).

If you are implementing today, start with the simplified object model and strict task lifecycle, then wire the runtime sequence one operation at a time.

---

## Diagram index used in this post

- `docs/diagrams/a2a-components-uml.png`
- `docs/diagrams/a2a-runtime-sequence.png`
- `docs/diagrams/a2a-task-lifecycle-strict.png`
- `docs/diagrams/a2a-task-lifecycle.png` (optional audience-friendly variant)
- `docs/diagrams/a2a-object-model-simple.png`
- `docs/diagrams/a2a-object-model.png`
