"""Connect to MCP servers and expose tools to Bedrock."""

from __future__ import annotations

import logging
from contextlib import AsyncExitStack
from dataclasses import dataclass, field
from typing import Any

import httpx
from mcp import ClientSession, StdioServerParameters
from mcp.client.sse import sse_client
from mcp.client.stdio import stdio_client
from mcp.client.streamable_http import streamable_http_client
from mcp.types import Tool as McpTool

from shoe_bot.config import McpServerConfig, parse_tool_name, tool_name

logger = logging.getLogger(__name__)


@dataclass
class RegisteredTool:
    server: str
    tool: McpTool
    bedrock_name: str


@dataclass
class McpManager:
    """Manages one or more MCP server sessions."""

    configs: list[McpServerConfig]
    _stack: AsyncExitStack = field(default_factory=AsyncExitStack, repr=False)
    _sessions: dict[str, ClientSession] = field(default_factory=dict, repr=False)
    _tools: list[RegisteredTool] = field(default_factory=list, repr=False)
    connected: bool = False

    async def connect(self) -> None:
        if self.connected:
            return

        self._tools.clear()
        self._sessions.clear()

        for cfg in self.configs:
            session = await self._open_session(cfg)
            await session.initialize()
            result = await session.list_tools()
            self._sessions[cfg.name] = session

            for t in result.tools:
                self._tools.append(
                    RegisteredTool(
                        server=cfg.name,
                        tool=t,
                        bedrock_name=tool_name(cfg.name, t.name),
                    )
                )
            logger.info("MCP server %s: %d tools", cfg.name, len(result.tools))

        self.connected = True

    async def disconnect(self) -> None:
        if not self.connected and not self._stack:
            return
        await self._stack.aclose()
        self._stack = AsyncExitStack()
        self._sessions.clear()
        self._tools.clear()
        self.connected = False

    async def _open_session(self, cfg: McpServerConfig) -> ClientSession:
        if cfg.transport == "stdio":
            if not cfg.command:
                raise ValueError(f"MCP server '{cfg.name}' requires 'command' for stdio")
            params = StdioServerParameters(
                command=cfg.command,
                args=cfg.args,
                env=cfg.env or None,
                cwd=cfg.cwd,
            )
            read, write = await self._stack.enter_async_context(stdio_client(params))
            return await self._stack.enter_async_context(ClientSession(read, write))

        if not cfg.url:
            raise ValueError(f"MCP server '{cfg.name}' requires 'url' for {cfg.transport}")

        headers = cfg.headers or None
        if cfg.transport == "sse":
            read, write = await self._stack.enter_async_context(
                sse_client(cfg.url, headers=headers)
            )
            return await self._stack.enter_async_context(ClientSession(read, write))

        if cfg.transport == "streamable-http":
            client = httpx.AsyncClient(headers=headers, timeout=60.0)
            await self._stack.enter_async_context(client)
            read, write, _ = await self._stack.enter_async_context(
                streamable_http_client(cfg.url, http_client=client)
            )
            return await self._stack.enter_async_context(ClientSession(read, write))

        raise ValueError(f"Unsupported transport: {cfg.transport}")

    def bedrock_tool_config(self) -> dict[str, Any]:
        """Convert registered MCP tools to Bedrock toolConfig."""
        tools = []
        for reg in self._tools:
            schema = reg.tool.inputSchema
            if hasattr(schema, "model_dump"):
                schema = schema.model_dump(by_alias=True, exclude_none=True)
            tools.append(
                {
                    "toolSpec": {
                        "name": reg.bedrock_name,
                        "description": reg.tool.description or reg.tool.name,
                        "inputSchema": {"json": schema},
                    }
                }
            )
        return {"tools": tools}

    @property
    def tool_count(self) -> int:
        return len(self._tools)

    def tool_summary(self) -> list[dict[str, str]]:
        return [
            {
                "server": r.server,
                "name": r.tool.name,
                "bedrock_name": r.bedrock_name,
                "description": r.tool.description or "",
            }
            for r in self._tools
        ]

    async def call_tool(self, bedrock_name: str, arguments: dict[str, Any]) -> str:
        server, tool = parse_tool_name(bedrock_name)
        session = self._sessions.get(server)
        if session is None:
            raise ValueError(f"No MCP session for server '{server}'")

        logger.info("Calling tool %s on server %s", tool, server)
        result = await session.call_tool(tool, arguments)
        parts: list[str] = []
        for block in result.content:
            if hasattr(block, "text") and block.text:
                parts.append(block.text)
            else:
                parts.append(str(block))
        return "\n".join(parts) if parts else "(empty tool result)"
