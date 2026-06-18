"""AWS Bedrock Converse agent with MCP tool execution."""

from __future__ import annotations

import logging
from dataclasses import dataclass, field
from typing import Any

import boto3
from botocore.config import Config

from shoe_bot.mcp_manager import McpManager

logger = logging.getLogger(__name__)


@dataclass
class ChatTurn:
    role: str
    content: str
    tool_calls: list[dict[str, Any]] = field(default_factory=list)


@dataclass
class BedrockAgent:
    region: str
    model_id: str
    system_prompt: str
    max_tokens: int = 4096
    temperature: float = 0.5
    max_tool_rounds: int = 10
    mcp: McpManager | None = None
    messages: list[dict[str, Any]] = field(default_factory=list)

    def __post_init__(self) -> None:
        retry = Config(
            region_name=self.region,
            retries={"max_attempts": 5, "mode": "adaptive"},
            connect_timeout=10,
            read_timeout=120,
        )
        self._client = boto3.client(
            "bedrock-runtime",
            region_name=self.region,
            config=retry,
        )

    def reset(self) -> None:
        self.messages = []

    def _system(self) -> list[dict[str, str]]:
        extra = ""
        if self.mcp and self.mcp.connected:
            tools = self.mcp.tool_summary()
            if tools:
                names = ", ".join(t["bedrock_name"] for t in tools)
                extra = f"\n\nYou have access to these tools: {names}"
        return [{"text": self.system_prompt + extra}]

    def _tool_config(self) -> dict[str, Any] | None:
        if self.mcp and self.mcp.connected and self.mcp.tool_count:
            return self.mcp.bedrock_tool_config()
        return None

    async def chat(self, user_message: str) -> ChatTurn:
        self.messages.append(
            {"role": "user", "content": [{"text": user_message}]}
        )

        tool_calls: list[dict[str, Any]] = []
        rounds = 0

        while rounds < self.max_tool_rounds:
            rounds += 1
            kwargs: dict[str, Any] = {
                "modelId": self.model_id,
                "messages": self.messages,
                "system": self._system(),
                "inferenceConfig": {
                    "maxTokens": self.max_tokens,
                    "temperature": self.temperature,
                },
            }
            tool_config = self._tool_config()
            if tool_config:
                kwargs["toolConfig"] = tool_config

            response = self._client.converse(**kwargs)
            output = response["output"]["message"]
            self.messages.append(output)
            stop_reason = response.get("stopReason", "")

            text_parts: list[str] = []
            for block in output.get("content", []):
                if "text" in block:
                    text_parts.append(block["text"])

            if stop_reason == "tool_use":
                if not self.mcp or not self.mcp.connected:
                    return ChatTurn(
                        role="assistant",
                        content="Model requested tools but no MCP servers are connected.",
                        tool_calls=tool_calls,
                    )

                tool_results = []
                for block in output.get("content", []):
                    if "toolUse" not in block:
                        continue
                    use = block["toolUse"]
                    name = use["name"]
                    tool_id = use["toolUseId"]
                    args = use.get("input") or {}

                    tool_calls.append(
                        {"name": name, "input": args, "toolUseId": tool_id}
                    )

                    try:
                        result_text = await self.mcp.call_tool(name, args)
                        status = "success"
                    except Exception as exc:
                        logger.exception("Tool %s failed", name)
                        result_text = f"Error: {exc}"
                        status = "error"

                    tool_results.append(
                        {
                            "toolResult": {
                                "toolUseId": tool_id,
                                "content": [{"text": result_text}],
                                "status": status,
                            }
                        }
                    )

                self.messages.append({"role": "user", "content": tool_results})
                continue

            if stop_reason in ("end_turn", "stop_sequence", "max_tokens"):
                content = "\n".join(text_parts) if text_parts else ""
                if stop_reason == "max_tokens" and not content:
                    content = "(Response truncated — increase max tokens.)"
                return ChatTurn(
                    role="assistant",
                    content=content,
                    tool_calls=tool_calls,
                )

            return ChatTurn(
                role="assistant",
                content=f"Stopped: {stop_reason}",
                tool_calls=tool_calls,
            )

        return ChatTurn(
            role="assistant",
            content="Stopped after maximum tool-use rounds.",
            tool_calls=tool_calls,
        )
