"""Streamlit chat UI for Shoe Bot."""

from __future__ import annotations

import json
import logging
import re

import streamlit as st

from shoe_bot.async_runtime import run_async
from shoe_bot.bedrock_agent import BedrockAgent
from shoe_bot.config import AppConfig, get_config, load_mcp_servers
from shoe_bot.masking import mask_obj, mask_text
from shoe_bot.mcp_manager import McpManager

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

AVATARS = {"user": "🧑", "assistant": "👟"}
SHOW_FIRST_CALLS = 3
_RETRY_AFTER = re.compile(r"retry after (\d+)\s*s", re.IGNORECASE)


# ---------------------------------------------------------------------------
# Connection / session
# ---------------------------------------------------------------------------


async def connect_mcp(configs) -> McpManager:
    manager = McpManager(configs=configs)
    await manager.connect()
    return manager


def _init_session() -> None:
    if "initialized" not in st.session_state:
        st.session_state.initialized = False
        st.session_state.mcp_manager = None
        st.session_state.agent = None
        st.session_state.chat_history = []
        st.session_state.bootstrap_error = None


def _bootstrap(config: AppConfig) -> None:
    if st.session_state.initialized:
        return

    st.session_state.initialized = True
    st.session_state.config = config

    if not config.mcp.enabled:
        logger.info("MCP disabled in config")
        return

    try:
        servers = load_mcp_servers()
        if not servers:
            st.session_state.bootstrap_error = "No MCP servers defined in config."
            return
        st.session_state.mcp_manager = run_async(connect_mcp(servers))
        logger.info(
            "Connected to %d MCP server(s), %d tools",
            len(servers),
            st.session_state.mcp_manager.tool_count,
        )
    except Exception as exc:
        logger.exception("MCP bootstrap failed")
        st.session_state.bootstrap_error = str(exc)


def _get_agent(config: AppConfig) -> BedrockAgent:
    if st.session_state.agent is None:
        b = config.bedrock
        st.session_state.agent = BedrockAgent(
            region=b.region,
            model_id=b.model_id,
            system_prompt=b.system_prompt,
            max_tokens=b.max_tokens,
            temperature=b.temperature,
            max_tool_rounds=b.max_tool_rounds,
            mcp=st.session_state.mcp_manager,
        )
    return st.session_state.agent


# ---------------------------------------------------------------------------
# Display helpers (masking is display-only)
# ---------------------------------------------------------------------------


def _sensitive(config: AppConfig) -> bool:
    """True when the 'Mask sensitive values' toggle is on."""
    return bool(st.session_state.get("mask_sensitive", config.ui.mask_default))


def _m(text: str, config: AppConfig) -> str:
    return mask_text(text, config.ui.aliases, _sensitive(config))


def _display_names() -> dict[str, str]:
    """Map Bedrock tool names to the real (backend-prefixed) tool names."""
    mgr = st.session_state.mcp_manager
    if not mgr:
        return {}
    return {t["bedrock_name"]: t["name"] for t in mgr.tool_summary()}


def _group_tools(tools: list[dict[str, str]]) -> dict[str, list[str]]:
    groups: dict[str, list[str]] = {}
    for t in tools:
        backend, sep, short = t["name"].partition("_")
        if not sep:
            backend, short = "other", t["name"]
        groups.setdefault(backend, []).append(short)
    return dict(sorted(groups.items()))


def _retry_after(result: str) -> str | None:
    m = _RETRY_AFTER.search(result or "")
    return m.group(1) if m else None


def _is_rate_limited(call: dict) -> bool:
    return call.get("status") == "error" and "rate limit" in (call.get("result") or "").lower()


def _show_result(text: str) -> None:
    try:
        st.json(json.loads(text))
    except (ValueError, TypeError):
        st.code(text, language=None)


def _render_call(call: dict, names: dict[str, str], config: AppConfig) -> None:
    name = names.get(call.get("name", ""), call.get("name", "tool"))
    is_error = call.get("status") == "error"
    result = _m(call.get("result") or "", config)
    args = mask_obj(call.get("input") or {}, config.ui.aliases, _sensitive(config))

    st.caption(f"🔧 Calling `{name}`")
    if args:
        st.code(json.dumps(args, indent=2), language="json")

    label = (
        f"🚫 Tool call failed — `{name}`" if is_error else f"✅ Tool result — `{name}`"
    )
    with st.expander(label, expanded=is_error):
        wait = _retry_after(result) if _is_rate_limited(call) else None
        if wait:
            st.badge(f"Rate limited · retry after {wait}s", icon="⏱️", color="orange")
        _show_result(result)


def _render_call_compact(call: dict, names: dict[str, str], config: AppConfig) -> None:
    name = names.get(call.get("name", ""), call.get("name", "tool"))
    icon = "🚫" if call.get("status") == "error" else "✅"
    result = _m(call.get("result") or "", config).replace("\n", " ")
    if len(result) > 140:
        result = result[:140] + "…"
    st.markdown(f"{icon} `{name}` — {result}")


def _render_tool_calls(calls: list[dict], config: AppConfig) -> None:
    if not calls:
        return
    names = _display_names()
    total = len(calls)
    ok = sum(1 for c in calls if c.get("status") != "error")
    limited = sum(1 for c in calls if _is_rate_limited(c))
    other_err = sum(1 for c in calls if c.get("status") == "error") - limited

    summary = f"**{total} tool call{'s' if total != 1 else ''}** — ✅ {ok} succeeded"
    if limited:
        summary += f", ⏱️ {limited} rate limited"
    if other_err:
        summary += f", 🚫 {other_err} failed"
    st.markdown(summary)

    if total <= SHOW_FIRST_CALLS + 2:
        for call in calls:
            _render_call(call, names, config)
        return

    head = calls[:SHOW_FIRST_CALLS]
    rest = calls[SHOW_FIRST_CALLS:]
    for call in head:
        _render_call(call, names, config)

    # Make sure the first refusal is always visible.
    first_limited = next((c for c in rest if _is_rate_limited(c)), None)
    if first_limited is not None:
        st.caption("First rate-limited call:")
        _render_call(first_limited, names, config)

    with st.expander(f"Show all remaining calls ({len(rest)})"):
        for call in rest:
            _render_call_compact(call, names, config)


# ---------------------------------------------------------------------------
# Quick prompts
# ---------------------------------------------------------------------------


def _quick_prompts(config: AppConfig) -> dict[str, list[tuple[str, str]]]:
    ui = config.ui
    metric_table = ui.cassandra_table.replace(".", "::")
    kafka_metric = f"kt::{ui.kafka_topic}::messagesInPerTopic"
    cass_metric = f"cf::{metric_table}::readCount"

    # With masking on, ask the agent to leave IDs/IPs out; with it off, include them.
    if _sensitive(config):
        extras = "Do not show cluster IDs or IP addresses."
        list_cols = "name, application, status"
        detail_cols = "name, application, status, node count, region and version"
    else:
        extras = "Include the cluster ID and each node's IP address."
        list_cols = "name, ID, application, status"
        detail_cols = "name, ID, application, status, node count, region and version"

    kafka_metrics = (
        f"Use listActiveClusters to find the cluster ID of the Kafka cluster named "
        f"{ui.kafka_cluster_name}, then use getClusterMetrics on that cluster ID with "
        f"metric {kafka_metric}, type latest. Only report the numbers "
        f"(one_minute_rate and mean_rate). Don't convert rates to counts."
    )
    return {
        "🎫 Support tickets (Kafka)": [
            ("📊 Topic rate (before / after)", kafka_metrics),
            (
                "🚨 Flood: 50 escalations",
                "A customer is very upset and wants this escalated. File 50 separate "
                "support requests for order 1, one submit_request call each, numbered "
                "1 to 50 in the issue text (for example \"Escalation 1 of 50: package "
                "not delivered\"). Do not combine them or skip any.",
            ),
            (
                "📨 Refill test (1 request)",
                "File one support request for order 1: \"Refill test: package not "
                "delivered\".",
            ),
        ],
        "👟 Catalog (Cassandra)": [
            (
                "🔥 Flood: 40 lookups + metrics",
                f"Look up the details for SHOE-001 40 separate times, one "
                f"get_product_details call each. Then, immediately after the last "
                f"lookup, use listActiveClusters to find the cluster ID of the "
                f"Cassandra cluster named {ui.cassandra_cluster_name}, and then use "
                f"getClusterMetrics on that cluster ID with metrics {cass_metric} and "
                f"n::reads, type latest. Report the values per node and the total. "
                f"Only report the numbers.",
            ),
            ("💰 Supplier cost", "What is the supplier cost for SHOE-001?"),
            ("🔎 Product details", "What are the details for SHOE-001?"),
            (
                "🧩 Cassandra cluster details",
                f"Use listActiveClusters to find the Cassandra cluster named "
                f"{ui.cassandra_cluster_name}, then use getCassandraCluster on it. "
                f"Show its {detail_cols}, current operations and each node's status. "
                f"{extras}",
            ),
        ],
        "🧰 Clusters": [
            (
                "🖥️ Which clusters are running?",
                f"List my active clusters as a table with these columns: "
                f"{list_cols}. {extras}",
            ),
            (
                "📋 Cluster details",
                f"List my active clusters as a table with these columns: "
                f"{detail_cols}. Use the get cluster tool for each application as "
                f"needed. {extras}",
            ),
            (
                "🎫 Kafka cluster details",
                f"Use listActiveClusters to find the Kafka cluster named "
                f"{ui.kafka_cluster_name}, then use getKafkaCluster on it. Show its "
                f"{detail_cols}, current operations and each node's status. {extras}",
            ),
        ],
    }


# ---------------------------------------------------------------------------
# Layout
# ---------------------------------------------------------------------------


def render_sidebar(config: AppConfig) -> str | None:
    """Render the sidebar; return a quick prompt if a button was clicked."""
    selected: str | None = None
    mgr = st.session_state.mcp_manager

    with st.sidebar:
        st.header("👟 Shoe Bot")

        if st.session_state.bootstrap_error:
            st.badge("MCP unavailable", icon="🚫", color="red")
        elif mgr:
            st.badge(f"Connected · {mgr.tool_count} tools", icon="✅", color="green")
        else:
            st.badge("MCP disabled", color="gray")

        st.toggle(
            "🔒 Mask sensitive values",
            value=config.ui.mask_default,
            key="mask_sensitive",
            help="Hides UUIDs, IP addresses and emails on screen. "
            "The agent still sees the real values.",
        )

        if st.button("🧹 Clear chat", use_container_width=True):
            st.session_state.chat_history = []
            if st.session_state.agent:
                st.session_state.agent.reset()
            st.rerun()

        st.divider()
        st.subheader("Quick prompts")
        for group, items in _quick_prompts(config).items():
            st.caption(group)
            for label, prompt in items:
                if st.button(label, key=f"qp_{label}", use_container_width=True):
                    selected = prompt

        if mgr:
            st.divider()
            with st.expander("🔧 Available tools"):
                for backend, names in _group_tools(mgr.tool_summary()).items():
                    st.markdown(f"**{backend}**")
                    for n in names:
                        st.markdown(f"- `{n}`")

    return selected


def render_chat(config: AppConfig) -> None:
    st.title("👟 Shoe Store Support")
    st.caption(
        "A support assistant that looks up orders, products and tickets "
        "through MCP Gateway."
    )

    if st.session_state.bootstrap_error:
        st.warning(f"MCP unavailable: {_m(st.session_state.bootstrap_error, config)}")

    quick = render_sidebar(config)

    for turn in st.session_state.chat_history:
        with st.chat_message(turn["role"], avatar=AVATARS[turn["role"]]):
            st.markdown(_m(turn["content"] or "", config))
            _render_tool_calls(turn.get("tool_calls") or [], config)

    prompt = st.chat_input("Message Shoe Bot…") or quick
    if not prompt:
        return

    agent = _get_agent(config)

    st.session_state.chat_history.append({"role": "user", "content": prompt})
    with st.chat_message("user", avatar=AVATARS["user"]):
        st.markdown(_m(prompt, config))

    with st.chat_message("assistant", avatar=AVATARS["assistant"]):
        with st.spinner("Thinking…"):
            try:
                turn = run_async(agent.chat(prompt))
            except Exception as exc:
                logger.exception("Bedrock chat failed")
                st.error(f"Error: {_m(str(exc), config)}")
                st.session_state.chat_history.pop()
                return

        st.markdown(_m(turn.content or "_(no response)_", config))
        _render_tool_calls(turn.tool_calls, config)

    st.session_state.chat_history.append(
        {
            "role": "assistant",
            "content": turn.content,
            "tool_calls": turn.tool_calls,
        }
    )


def main() -> None:
    st.set_page_config(
        page_title="Shoe Bot",
        page_icon="👟",
        layout="wide",
    )
    _init_session()
    config = get_config()
    _bootstrap(config)
    render_chat(config)


if __name__ == "__main__":
    main()
