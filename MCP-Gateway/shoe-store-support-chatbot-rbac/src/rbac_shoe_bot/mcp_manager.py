"""Owns MCP client sessions against one or more Streamable HTTP MCP servers.

Every session is opened with the current user's Auth0 access token attached
as a `Authorization: Bearer <token>` header, so the MCP Gateway can:

1. Validate the token (issuer, JWKS, audience) against the OAuth config on
   the Virtual Server.
2. Read the role(s) out of the configured Roles Claim Name.
3. Allow or deny each tool call per that role's Access Control List entry.

This module supports both the pre- and post-"v2" shapes of the `mcp` Python
SDK's streamable HTTP client, since the header-passing API changed between
SDK versions (older versions accept `headers=` directly; newer versions
require configuring an `httpx.AsyncClient`).
"""

import logging
from contextlib import AsyncExitStack
from typing import Any

from mcp import ClientSession

from rbac_shoe_bot.config import McpServerConfig

logger = logging.getLogger(__name__)


class ToolAccessDeniedError(RuntimeError):
    """Raised when the Gateway's Access Control List denies a tool call for the caller's role."""


async def _open_streamable_http(stack: AsyncExitStack, url: str, headers: dict[str, str]):
    """Open a streamable-http transport, handling both mcp SDK API shapes."""
    try:
        # Older / widely-deployed API: headers passed straight to the transport.
        from mcp.client.streamable_http import streamablehttp_client

        read, write, _get_session_id = await stack.enter_async_context(
            streamablehttp_client(url=url, headers=headers)
        )
        return read, write
    except ImportError:
        # Newer API: headers/auth configured on an httpx.AsyncClient instead.
        import httpx
        from mcp.client.streamable_http import streamable_http_client

        http_client = httpx.AsyncClient(headers=headers, follow_redirects=True)
        await stack.enter_async_context(http_client)
        read, write = await stack.enter_async_context(streamable_http_client(url=url, http_client=http_client))
        return read, write


class McpManager:
    def __init__(self, servers: list[McpServerConfig]):
        self._servers = servers
        self._stack: AsyncExitStack | None = None
        self._sessions: dict[str, ClientSession] = {}
        self._tool_to_server: dict[str, str] = {}

    async def connect(self, access_token: str) -> None:
        await self.close()
        self._stack = AsyncExitStack()
        headers = {"Authorization": f"Bearer {access_token}"}

        for server in self._servers:
            read, write = await _open_streamable_http(self._stack, server.url, headers)
            session = await self._stack.enter_async_context(ClientSession(read, write))
            await session.initialize()
            self._sessions[server.name] = session

            tools_result = await session.list_tools()
            for tool in tools_result.tools:
                self._tool_to_server[tool.name] = server.name

        if not self._sessions:
            raise RuntimeError("No MCP servers connected.")

    async def close(self) -> None:
        if self._stack is not None:
            await self._stack.aclose()
        self._stack = None
        self._sessions = {}
        self._tool_to_server = {}

    async def get_tool_specs(self) -> list[dict[str, Any]]:
        """Bedrock Converse `toolConfig.tools` entries for every tool this identity can see.

        Note: the Gateway only returns tools the caller's persona is allowed to
        discover/call under its Access Control List, so this list can differ
        by role even against the same Virtual Server.
        """
        specs: list[dict[str, Any]] = []
        for session in self._sessions.values():
            result = await session.list_tools()
            for tool in result.tools:
                specs.append(
                    {
                        "toolSpec": {
                            "name": tool.name,
                            "description": tool.description or "",
                            "inputSchema": {
                                "json": tool.inputSchema or {"type": "object", "properties": {}},
                            },
                        }
                    }
                )
        return specs

    async def call_tool(self, name: str, arguments: dict[str, Any]) -> Any:
        server_name = self._tool_to_server.get(name)
        if server_name is None:
            raise ValueError(f"Unknown tool: {name}")

        session = self._sessions[server_name]
        result = await session.call_tool(name, arguments)

        if getattr(result, "isError", False):
            # The Gateway reports ACL denials (and other tool-level failures)
            # as an error-flagged CallToolResult rather than an HTTP error, so
            # surface that distinctly for the UI.
            message = _extract_text(result) or "Access denied or tool call failed."
            logger.warning("Tool call %s denied/failed: %s", name, message)
            raise ToolAccessDeniedError(message)

        return result


def _extract_text(result: Any) -> str:
    texts = []
    for block in getattr(result, "content", []) or []:
        text = getattr(block, "text", None)
        if text:
            texts.append(text)
    return " ".join(texts)
