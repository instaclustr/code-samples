"""Streamlit chatbot demonstrating Auth0 login + MCP Gateway role-based access control.

Flow:
1. User clicks "Log in with Auth0" -> redirected to Auth0's /authorize.
2. Auth0 authenticates the user and redirects back with ?code=&state=.
3. This app exchanges the code for an access token (which carries the
   caller's role(s) in a custom claim, per the Auth0 post-login Action).
4. The access token is sent as a Bearer token on every MCP request to the
   Gateway's Virtual Server, which enforces its per-persona Access Control
   List (allow/deny per tool) before this app ever sees a result.

Log out and log back in as a different Auth0 test user (each login forces
the credential prompt) to see how the same prompt behaves differently
depending on which persona/role is signed in.
"""

import json
import logging

import streamlit as st

from rbac_shoe_bot import auth
from rbac_shoe_bot.async_runtime import run_async
from rbac_shoe_bot.bedrock_agent import BedrockAgent
from rbac_shoe_bot.config import AppConfig, load_config
from rbac_shoe_bot.mcp_manager import McpManager

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

st.set_page_config(page_title="Shoe Store Support (RBAC Demo)", page_icon="👟")

# Cosmetic-only lookups for the sidebar/chat, keyed on the demo's two example
# roles. Any other role still renders fine, just without a themed color/icon.
_ROLE_BADGE_COLORS = {"support-agent": "green", "readonly-auditor": "blue"}
_CHAT_AVATARS = {"user": "🧑", "assistant": "👟"}


@st.cache_resource
def get_config() -> AppConfig:
    return load_config()


def _ensure_session_state() -> None:
    st.session_state.setdefault("access_token", None)
    st.session_state.setdefault("token_payload", None)
    st.session_state.setdefault("roles", [])
    st.session_state.setdefault("oauth_state", None)
    st.session_state.setdefault("mcp_manager", None)
    st.session_state.setdefault("tool_specs", [])
    st.session_state.setdefault("messages", [])
    st.session_state.setdefault("mcp_error", None)


def _reset_session() -> None:
    manager = st.session_state.get("mcp_manager")
    if manager is not None:
        try:
            run_async(manager.close())
        except Exception:  # noqa: BLE001
            pass

    st.session_state["access_token"] = None
    st.session_state["token_payload"] = None
    st.session_state["roles"] = []
    st.session_state["mcp_manager"] = None
    st.session_state["tool_specs"] = []
    st.session_state["messages"] = []
    st.session_state["mcp_error"] = None
    st.session_state["oauth_state"] = None


def _handle_oauth_callback(config: AppConfig) -> None:
    params = st.query_params
    code = params.get("code")
    if not code:
        return

    returned_state = params.get("state")
    # Verified against the process-level pending-state store (see auth.py),
    # NOT st.session_state -- the Streamlit session that issued this login
    # attempt may not be the same one handling the callback after the round
    # trip to Auth0, so session_state can't be trusted here.
    if not auth.consume_pending_state(returned_state):
        st.session_state["oauth_state"] = None
        st.query_params.clear()
        st.error(
            "Login state didn't match or expired (possibly a stale/duplicate "
            "callback, or the login took over 10 minutes) — please log in again."
        )
        return

    with st.spinner("Completing login..."):
        try:
            token_set = auth.exchange_code_for_token(config.auth0, code)
        except Exception as exc:  # noqa: BLE001
            # consume_pending_state() above already popped this state from the
            # store, so it must not be reused: leaving it in session_state
            # would make the *next* login attempt silently resubmit this now-
            # unregistered state and fail with a "didn't match" error instead
            # of a fresh, retryable one.
            st.session_state["oauth_state"] = None
            st.query_params.clear()
            st.error(f"Login failed while exchanging the authorization code: {exc}")
            return

    payload = auth.decode_jwt_payload(token_set.access_token)
    roles = auth.extract_roles(payload, config.auth0.roles_claim)

    st.session_state["access_token"] = token_set.access_token
    st.session_state["token_payload"] = payload
    st.session_state["roles"] = roles
    st.session_state["mcp_manager"] = None
    st.session_state["tool_specs"] = []
    st.session_state["messages"] = []
    st.session_state["mcp_error"] = None
    st.session_state["oauth_state"] = None

    st.query_params.clear()
    st.rerun()


def _render_login_screen(config: AppConfig) -> None:
    st.title("👟 Shoe Store Support — RBAC Demo")
    st.write(
        "This demo signs you in with Auth0 and connects to the MCP Gateway using "
        "your access token. The Gateway enforces per-persona tool access via its "
        "Access Control Lists — log in as different test users to see which "
        "actions each persona is allowed to take."
    )

    # Generate the CSRF state ONCE per session and reuse it across reruns
    # (st.link_button triggers a rerun on click before the browser actually
    # navigates to Auth0, so regenerating on every render would invalidate
    # the very link being followed). It's also registered in the process-
    # level pending-state store, since the callback may land in a different
    # Streamlit session than the one that issued it -- see auth.py.
    if not st.session_state.get("oauth_state"):
        new_state = auth.new_state()
        st.session_state["oauth_state"] = new_state
        auth.register_pending_state(new_state)
    login_url = auth.build_login_url(config.auth0, st.session_state["oauth_state"])

    st.link_button("Log in with Auth0", login_url, type="primary")
    st.caption(
        "Each login forces Auth0's credential prompt, even if you're already "
        "signed in, so you can switch test users/personas freely."
    )

    if not config.auth0.roles_claim.startswith("https://"):
        st.warning(
            "Your configured `roles_claim` doesn't look namespaced "
            "(e.g. `https://mcp_gateway/user_roles`). Auth0 requires namespaced "
            "custom claims unless you're using a fully custom API."
        )


async def _connect_mcp(config: AppConfig, access_token: str) -> tuple[McpManager, list[dict]]:
    manager = McpManager(config.mcp_servers)
    await manager.connect(access_token)
    tool_specs = await manager.get_tool_specs()
    return manager, tool_specs


def _ensure_mcp_connection(config: AppConfig) -> McpManager | None:
    if st.session_state["mcp_manager"] is not None:
        return st.session_state["mcp_manager"]

    if st.session_state["mcp_error"] is not None:
        st.error(st.session_state["mcp_error"])
        if st.button("Retry connecting to the MCP Gateway"):
            st.session_state["mcp_error"] = None
            st.rerun()
        return None

    try:
        with st.spinner("Connecting to the MCP Gateway..."):
            manager, tool_specs = run_async(_connect_mcp(config, st.session_state["access_token"]))
    except Exception as exc:  # noqa: BLE001
        message = (
            "Couldn't connect to the MCP Gateway with your current access token. "
            "This usually means the Gateway rejected the token (check the Virtual "
            "Server's OAuth issuer/JWKS/audience config) or it isn't reachable. "
            f"Details: {exc}"
        )
        st.session_state["mcp_error"] = message
        st.error(message)
        return None

    st.session_state["mcp_manager"] = manager
    st.session_state["tool_specs"] = tool_specs

    if not tool_specs:
        st.warning(
            "Connected, but no tools were discovered for your role. If your "
            "Access Control List is an Allowlist, this role may have no allowed "
            "tools configured on the Virtual Server."
        )

    return manager


def _group_tool_names(tool_names: list[str]) -> dict[str, list[str]]:
    """Group `server_toolname` MCP tool names by their server prefix for display."""
    groups: dict[str, list[str]] = {}
    for name in tool_names:
        server, _, rest = name.partition("_")
        groups.setdefault(server, []).append(rest or name)
    return groups


def _render_sidebar() -> None:
    with st.sidebar:
        st.success("Signed in", icon="✅")

        st.write("**Role(s):**")
        roles = st.session_state["roles"]
        if roles:
            for role in roles:
                st.badge(role, color=_ROLE_BADGE_COLORS.get(role, "gray"))
        else:
            st.caption("(no roles claim found on token)")

        st.write("**Tools discovered:**")
        if st.session_state["tool_specs"]:
            tool_names = [spec["toolSpec"]["name"] for spec in st.session_state["tool_specs"]]
            for server, names in sorted(_group_tool_names(tool_names).items()):
                st.caption(f"_{server}_")
                for name in sorted(names):
                    st.markdown(f"- `{name}`")
        else:
            st.caption("No tools allowed for this role.")

        st.divider()
        if st.button("Log out", use_container_width=True):
            _reset_session()
            st.rerun()

        payload = st.session_state["token_payload"] or {}
        subject = payload.get("sub", "unknown")
        st.caption(f"Subject: `{subject}`")


def _tool_names_by_use_id(messages: list[dict]) -> dict[str, str]:
    """Map each toolUseId to its tool name.

    Bedrock's `toolResult` blocks only carry a `toolUseId`, not the tool name
    (that lives on the earlier `toolUse` block) -- so results are labeled by
    looking back at the request that produced them.
    """
    names: dict[str, str] = {}
    for message in messages:
        for block in message.get("content", []):
            tool_use = block.get("toolUse")
            if tool_use:
                names[tool_use["toolUseId"]] = tool_use["name"]
    return names


def _render_message(message: dict, tool_names_by_use_id: dict[str, str]) -> None:
    role = message.get("role", "assistant")
    with st.chat_message(role, avatar=_CHAT_AVATARS.get(role)):
        for block in message.get("content", []):
            if "text" in block:
                st.write(block["text"])
            elif "toolUse" in block:
                st.caption(f"🔧 Calling `{block['toolUse']['name']}`...")
            elif "toolResult" in block:
                tool_result = block["toolResult"]
                is_error = tool_result.get("status") == "error"
                tool_name = tool_names_by_use_id.get(tool_result.get("toolUseId"))
                label = "🚫 Tool call failed" if is_error else "✅ Tool result"
                if tool_name:
                    label += f" — `{tool_name}`"
                with st.expander(label, expanded=is_error):
                    for content_block in tool_result.get("content", []):
                        text = content_block.get("text", "")
                        try:
                            st.json(json.loads(text))
                        except (json.JSONDecodeError, TypeError):
                            st.code(text)


def _render_chat(config: AppConfig) -> None:
    manager = _ensure_mcp_connection(config)
    if manager is None:
        return

    tool_names_by_use_id = _tool_names_by_use_id(st.session_state["messages"])
    for message in st.session_state["messages"]:
        _render_message(message, tool_names_by_use_id)

    prompt = st.chat_input("Ask about products, pricing, orders, or returns...")
    if not prompt:
        return

    st.session_state["messages"].append({"role": "user", "content": [{"text": prompt}]})

    agent = BedrockAgent(
        region=config.bedrock.region,
        model_id=config.bedrock.model_id,
        system_prompt=config.bedrock.system_prompt,
        max_tokens=config.bedrock.max_tokens,
        temperature=config.bedrock.temperature,
        max_tool_rounds=config.bedrock.max_tool_rounds,
    )

    async def execute_tool(name: str, arguments: dict) -> object:
        return await manager.call_tool(name, arguments)

    with st.spinner("Thinking..."):
        try:
            updated_conversation = run_async(
                agent.run(st.session_state["messages"], st.session_state["tool_specs"], execute_tool)
            )
        except Exception as exc:  # noqa: BLE001
            st.error(f"The agent hit an error: {exc}")
            return

    st.session_state["messages"] = updated_conversation
    st.rerun()


def main() -> None:
    config = get_config()
    _ensure_session_state()
    _handle_oauth_callback(config)

    if not st.session_state["access_token"]:
        _render_login_screen(config)
        return

    st.title("👟 Shoe Store Support — RBAC Demo")
    roles_display = ", ".join(st.session_state["roles"]) or "no role"
    st.caption(f"Signed in as **{roles_display}**")
    _render_sidebar()
    _render_chat(config)


if __name__ == "__main__":
    main()
