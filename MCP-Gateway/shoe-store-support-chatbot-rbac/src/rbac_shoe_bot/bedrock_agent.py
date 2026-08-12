"""A minimal AWS Bedrock Converse agent loop with MCP tool calling."""

import json
import logging
from typing import Any, Awaitable, Callable

import boto3

from rbac_shoe_bot.mcp_manager import ToolAccessDeniedError

logger = logging.getLogger(__name__)

ToolExecutor = Callable[[str, dict[str, Any]], Awaitable[Any]]


class BedrockAgent:
    def __init__(
        self,
        region: str,
        model_id: str,
        system_prompt: str,
        max_tokens: int,
        temperature: float,
        max_tool_rounds: int,
    ):
        self._client = boto3.client("bedrock-runtime", region_name=region)
        self._model_id = model_id
        self._system_prompt = system_prompt
        self._max_tokens = max_tokens
        self._temperature = temperature
        self._max_tool_rounds = max_tool_rounds

    async def run(
        self,
        messages: list[dict[str, Any]],
        tool_specs: list[dict[str, Any]],
        execute_tool: ToolExecutor,
    ) -> list[dict[str, Any]]:
        conversation = list(messages)

        for _ in range(self._max_tool_rounds):
            converse_kwargs: dict[str, Any] = {
                "modelId": self._model_id,
                "messages": conversation,
                "system": [{"text": self._system_prompt}],
                "inferenceConfig": {
                    "maxTokens": self._max_tokens,
                    "temperature": self._temperature,
                },
            }
            if tool_specs:
                converse_kwargs["toolConfig"] = {"tools": tool_specs}

            response = self._client.converse(**converse_kwargs)

            output_message = response["output"]["message"]
            conversation.append(output_message)

            tool_uses = [block["toolUse"] for block in output_message["content"] if "toolUse" in block]

            if not tool_uses or response.get("stopReason") != "tool_use":
                break

            tool_result_blocks = []
            for tool_use in tool_uses:
                tool_result_blocks.append(await self._run_tool(tool_use, execute_tool))

            conversation.append({"role": "user", "content": tool_result_blocks})

        return conversation

    async def _run_tool(self, tool_use: dict[str, Any], execute_tool: ToolExecutor) -> dict[str, Any]:
        name = tool_use["name"]
        arguments = tool_use.get("input", {})
        tool_use_id = tool_use["toolUseId"]

        try:
            result = await execute_tool(name, arguments)
            return {
                "toolResult": {
                    "toolUseId": tool_use_id,
                    "content": _format_tool_result(result),
                }
            }
        except ToolAccessDeniedError as exc:
            return {
                "toolResult": {
                    "toolUseId": tool_use_id,
                    "content": [{"text": f"Access denied: {exc}"}],
                    "status": "error",
                }
            }
        except Exception as exc:  # noqa: BLE001
            logger.warning("Tool call %s failed: %s", name, exc)
            return {
                "toolResult": {
                    "toolUseId": tool_use_id,
                    "content": [{"text": f"Tool call failed: {exc}"}],
                    "status": "error",
                }
            }


def _format_tool_result(result: Any) -> list[dict[str, Any]]:
    content = getattr(result, "content", None)
    if content is None:
        return [{"text": json.dumps(result, default=str)}]

    blocks = []
    for block in content:
        text = getattr(block, "text", None)
        if text is not None:
            blocks.append({"text": text})
        else:
            blocks.append({"text": json.dumps(block, default=str)})
    return blocks or [{"text": "(no content)"}]
