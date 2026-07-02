# A2A on GitHub — Reference List

Curated projects that support the [Agent2Agent (A2A) Protocol](https://a2a-protocol.org/latest/). Useful as a bookmark list for the Scaling Agent Systems series and LinkedIn taster posts.

**Last reviewed:** 2026-07-01 (GitHub API: stars, `pushed_at`).

**How to read this list:** Prefer **official `a2aproject` SDKs + `a2a-samples`** for your stack, then framework-native paths (ADK, LangGraph, Microsoft Agent Framework), then community adapters. Verify agents with [a2a-inspector](https://github.com/a2aproject/a2a-inspector) or [a2a-tck](https://github.com/a2aproject/a2a-tck).

---

## Tier 1 — Official (`a2aproject`)

Linux Foundation org — spec-aligned, actively maintained.

| Project | Stars | Last push | Notes |
|---------|------:|-----------|-------|
| [a2aproject/A2A](https://github.com/a2aproject/A2A) | ~24.6k | 2026-07-01 | Spec, docs, SDK index; **v1.0.1** release |
| [a2aproject/a2a-python](https://github.com/a2aproject/a2a-python) | ~2.0k | 2026-06-18 | **Official Python SDK** — `pip install a2a-sdk`; JSON-RPC, REST, gRPC; v1.x |
| [a2aproject/a2a-js](https://github.com/a2aproject/a2a-js) | ~564 | 2026-06-30 | Official **JavaScript / TypeScript** SDK |
| [a2aproject/a2a-java](https://github.com/a2aproject/a2a-java) | ~449 | 2026-06-30 | Official **Java** SDK (Quarkus ecosystem) |
| [a2aproject/a2a-go](https://github.com/a2aproject/a2a-go) | ~411 | 2026-06-26 | Official **Go** SDK |
| [a2aproject/a2a-dotnet](https://github.com/a2aproject/a2a-dotnet) | ~242 | 2026-06-26 | Official **.NET** SDK |
| [a2aproject/a2a-rs](https://github.com/a2aproject/a2a-rs) | ~42 | 2026-06-26 | Official **Rust** SDK (newer, smaller community) |
| [a2aproject/a2a-samples](https://github.com/a2aproject/a2a-samples) | ~1.7k | 2026-06-30 | **Reference implementations** — see sample index below |
| [a2aproject/a2a-inspector](https://github.com/a2aproject/a2a-inspector) | ~440 | 2026-02-28 | Validation / debug tool for Agent Cards and agents |
| [a2aproject/a2a-tck](https://github.com/a2aproject/a2a-tck) | ~38 | 2026-06-29 | Compatibility test kit for server implementers |

### Notable paths under `a2a-samples`

Under `samples/python/agents/` (and related trees):

| Sample folder | Framework / topic |
|---------------|-------------------|
| `langgraph` | LangGraph currency agent (streaming, push, multi-turn) |
| `crewai` | CrewAI |
| `semantickernel` | Semantic Kernel |
| `llama_index_file_chat` | LlamaIndex |
| `adk_*` | Google ADK (cloud run, currency, facts, skills, etc.) |
| `ag2` | AG2 (formerly AutoGen) |
| `azureaifoundry_sdk` | Azure AI Foundry |
| `dice_agent_grpc` / `dice_agent_rest` | gRPC and REST binding examples |
| `a2a_mcp` / `a2a-mcp-without-framework` | A2A + MCP |
| `helloworld` | Minimal starter |

---

## Tier 2 — Major frameworks (native or first-class A2A)

| Project | Stars | Last push | A2A support |
|---------|------:|-----------|-------------|
| [google/adk-python](https://github.com/google/adk-python) | ~20.4k | 2026-07-01 | **Native A2A** — `RemoteA2aAgent`, `to_a2a()`, cross-language examples |
| [google/adk-go](https://github.com/google/adk-go) | ~8.3k | 2026-07-01 | Go ADK; A2A multi-agent patterns in Google documentation |
| [langchain-ai/langgraph](https://github.com/langchain-ai/langgraph) | ~36k | 2026-07-01 | **`A2ARemoteGraph`** — call remote A2A agents as graph nodes; see `a2a-samples/.../langgraph` |
| **Microsoft Agent Framework** | — | 2026 docs | NuGet: `Microsoft.Agents.AI.A2A`, `Microsoft.Agents.AI.Hosting.A2A.AspNetCore` — [A2A hosting](https://learn.microsoft.com/en-us/agent-framework/hosting/agent-to-agent), [A2A agent client](https://learn.microsoft.com/en-us/agent-framework/agents/providers/agent-to-agent) |

---

## Tier 3 — Community adapters and alternate SDKs

Smaller repos; verify against A2A v1 spec before production use.

| Project | Stars | Last push | Notes |
|---------|------:|-----------|-------|
| [hybroai/a2a-adapter](https://github.com/hybroai/a2a-adapter) | ~86 | 2026-06-20 | `pip install a2a-adapter` — wrap LangGraph, CrewAI, LangChain, n8n, etc. as A2A servers |
| [qntx/ra2a](https://github.com/qntx/ra2a) | ~166 | 2026-06-26 | Community **Rust** SDK (alongside official `a2a-rs`) |
| [cpp-agan-team/a2a-cpp-sdk](https://github.com/cpp-agan-team/a2a-cpp-sdk) | ~58 | 2025-11-30 | **C++** SDK — unofficial |
| [DracoBlue/a2a-ai-provider](https://github.com/DracoBlue/a2a-ai-provider) | ~31 | 2026-06-12 | A2A provider for **Vercel AI SDK** |
| [5enxia/langgraph-multiagent-with-a2a](https://github.com/5enxia/langgraph-multiagent-with-a2a) | small | 2025 | Supervisor multi-agent pattern (educational) |
| [mrgoonie/a2a-langgraph-boilerplate](https://github.com/mrgoonie/a2a-langgraph-boilerplate) | ~40 | 2025 | LangGraph + A2A starter |

---

## Tier 4 — Infrastructure (A2A-aware)

| Project | Stars | Last push | Notes |
|---------|------:|-----------|-------|
| [agentgateway/agentgateway](https://github.com/agentgateway/agentgateway) | ~3.6k | 2026-06-30 | Agentic proxy — routing, auth, policy for **A2A + MCP** (Linux Foundation / Solo.io ecosystem) |
| [a2aproject/a2a-gateway](https://github.com/a2aproject/a2a-gateway) | 0 | 2026-05-14 | Early / placeholder in org — watch, do not depend on yet |

---

## Quick start by language

| Language | Start here |
|----------|------------|
| **Python** | [a2a-python](https://github.com/a2aproject/a2a-python) + matching [a2a-samples](https://github.com/a2aproject/a2a-samples) agent |
| **TypeScript** | [a2a-js](https://github.com/a2aproject/a2a-js) |
| **Java** | [a2a-java](https://github.com/a2aproject/a2a-java) |
| **Go** | [a2a-go](https://github.com/a2aproject/a2a-go) + [adk-go](https://github.com/google/adk-go) |
| **.NET** | [a2a-dotnet](https://github.com/a2aproject/a2a-dotnet) + Microsoft Agent Framework A2A packages |
| **Rust** | [a2a-rs](https://github.com/a2aproject/a2a-rs) or [qntx/ra2a](https://github.com/qntx/ra2a) |

---

## Related (complementary, not A2A)

| Project | Role |
|---------|------|
| [modelcontextprotocol/servers](https://github.com/modelcontextprotocol/servers) | **MCP** — agent-to-tools (use *with* A2A, not instead of) |
| [google/adk-python](https://github.com/google/adk-python) | Build agents; expose via A2A |

---

## What to exclude from a “good support” list

- Generic awesome lists that only mention A2A in passing
- Tiny repos with no recent pushes and no releases
- Marketing-only pages without inspectable code

---

## One-liner (LinkedIn / intro)

> A2A support is real and recent: official multi-language SDKs under [`a2aproject`](https://github.com/a2aproject), a large [`a2a-samples`](https://github.com/a2aproject/a2a-samples) catalog, native paths in **Google ADK** and **LangGraph**, Microsoft **Agent Framework** hosting/clients, and adapters like **`a2a-adapter`** — but depth varies; validate with **`a2a-inspector`** or the **TCK**.

---

## Local series links

- [Part 2 — Object model](blogs/scaling-agents-part-2-a2a-intro.md)
- [Part 3 — Runtime](blogs/scaling-agents-part-3-a2a-runtime.md)
- [Ecosystem visual](diagrams/a2a-ecosystem-visual-hero.png)
- [Java demo traces](examples/trace.md)

---

## Refreshing this list

```bash
curl -sL "https://api.github.com/orgs/a2aproject/repos?per_page=100&sort=updated" | \
  python3 -c "import json,sys; [print(r['name'], r['stargazers_count'], r['pushed_at'][:10]) for r in json.load(sys.stdin)]"
```

Update star counts and `pushed_at` periodically; tiers are editorial, not automatic.
