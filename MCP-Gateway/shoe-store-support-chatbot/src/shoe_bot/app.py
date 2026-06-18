"""Streamlit chat UI for Shoe Bot."""

from __future__ import annotations

import logging

import streamlit as st

from shoe_bot.async_runtime import run_async
from shoe_bot.bedrock_agent import BedrockAgent
from shoe_bot.config import AppConfig, get_config, load_mcp_servers
from shoe_bot.mcp_manager import McpManager

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


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


def render_chat(config: AppConfig) -> None:
    col_title, col_action = st.columns([6, 1])
    with col_title:
        st.title("Shoe Store Support ChatBot")
    with col_action:
        if st.button("Clear chat", use_container_width=True):
            st.session_state.chat_history = []
            if st.session_state.agent:
                st.session_state.agent.reset()
            st.rerun()

    if st.session_state.bootstrap_error:
        st.warning(f"MCP unavailable: {st.session_state.bootstrap_error}")

    for turn in st.session_state.chat_history:
        with st.chat_message(turn["role"]):
            st.markdown(turn["content"])
            if turn.get("tool_calls"):
                with st.expander("Tool calls"):
                    st.json(turn["tool_calls"])

    prompt = st.chat_input("Message Shoe Bot…")
    if not prompt:
        return

    agent = _get_agent(config)

    st.session_state.chat_history.append({"role": "user", "content": prompt})
    with st.chat_message("user"):
        st.markdown(prompt)

    with st.chat_message("assistant"):
        with st.spinner("Thinking…"):
            try:
                turn = run_async(agent.chat(prompt))
            except Exception as exc:
                logger.exception("Bedrock chat failed")
                st.error(f"Error: {exc}")
                st.session_state.chat_history.pop()
                return

        st.markdown(turn.content or "_(no response)_")
        if turn.tool_calls:
            with st.expander("Tool calls"):
                st.json(turn.tool_calls)

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
