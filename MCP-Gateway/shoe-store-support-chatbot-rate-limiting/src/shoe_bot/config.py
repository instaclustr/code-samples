"""Application configuration loaded from a single JSON file."""

from __future__ import annotations

import hashlib
import json
import os
from functools import lru_cache
from pathlib import Path
from typing import Any, Literal

from pydantic import BaseModel, Field

PROJECT_ROOT = Path(__file__).resolve().parent.parent.parent
DEFAULT_CONFIG_PATH = PROJECT_ROOT / "config.json"


class BedrockConfig(BaseModel):
    region: str = "us-east-1"
    model_id: str = "us.anthropic.claude-3-5-sonnet-20241022-v2:0"
    system_prompt: str = (
        "You are a helpful assistant. Use available tools when they help "
        "answer the user. Be concise and accurate."
    )
    max_tokens: int = 4096
    temperature: float = 0.5
    max_tool_rounds: int = 10


class McpServerConfig(BaseModel):
    """Single MCP server entry (Cursor-compatible plus HTTP transports)."""

    name: str
    transport: Literal["stdio", "sse", "streamable-http"] = "stdio"
    command: str | None = None
    args: list[str] = Field(default_factory=list)
    env: dict[str, str] = Field(default_factory=dict)
    cwd: str | None = None
    url: str | None = None
    headers: dict[str, str] = Field(default_factory=dict)


class McpConfig(BaseModel):
    enabled: bool = True
    servers: dict[str, dict[str, Any]] = Field(default_factory=dict)


class UiConfig(BaseModel):
    """Display-only settings (masking and quick-prompt cluster names)."""

    # Always-on display aliases, e.g. {"my-prefix": "demo"}.
    aliases: dict[str, str] = Field(default_factory=dict)
    # Initial state of the "Mask sensitive values" toggle (UUIDs, IPs, emails).
    mask_default: bool = True
    kafka_cluster_name: str = "your-kafka-cluster"
    cassandra_cluster_name: str = "your-cassandra-cluster"
    kafka_topic: str = "support-requests"
    cassandra_table: str = "catalog.products"


class AppConfig(BaseModel):
    bedrock: BedrockConfig = Field(default_factory=BedrockConfig)
    mcp: McpConfig = Field(default_factory=McpConfig)
    ui: UiConfig = Field(default_factory=UiConfig)

    def mcp_server_configs(self) -> list[McpServerConfig]:
        """Parse MCP server entries from config."""
        configs: list[McpServerConfig] = []

        for name, spec in self.mcp.servers.items():
            if not isinstance(spec, dict):
                continue

            transport = spec.get("transport")
            if transport is None:
                if spec.get("url"):
                    url = spec["url"]
                    transport = "sse" if "/sse" in url else "streamable-http"
                else:
                    transport = "stdio"

            cwd = spec.get("cwd")
            if cwd and not Path(cwd).is_absolute():
                cwd = str(PROJECT_ROOT / cwd)

            configs.append(
                McpServerConfig(
                    name=name,
                    transport=transport,
                    command=spec.get("command"),
                    args=spec.get("args") or [],
                    env=spec.get("env") or {},
                    cwd=cwd,
                    url=spec.get("url"),
                    headers=spec.get("headers") or {},
                )
            )

        return configs


def resolve_config_path(path: str | Path | None = None) -> Path:
    """Resolve config file path (env SHOE_BOT_CONFIG overrides default)."""
    raw = path or os.environ.get("SHOE_BOT_CONFIG", "config.json")
    p = Path(raw)
    if not p.is_absolute():
        p = PROJECT_ROOT / p
    return p


def load_config(path: str | Path | None = None) -> AppConfig:
    """Load application config from JSON."""
    config_path = resolve_config_path(path)
    if not config_path.exists():
        raise FileNotFoundError(f"Config not found: {config_path}")

    data = json.loads(config_path.read_text(encoding="utf-8"))
    return AppConfig.model_validate(data)


@lru_cache
def get_config() -> AppConfig:
    """Load and cache application config."""
    return load_config()


# Backwards-compatible aliases
get_settings = get_config
AppSettings = AppConfig


def load_mcp_servers() -> list[McpServerConfig]:
    """Return MCP server configs from the active app config."""
    return get_config().mcp_server_configs()


BEDROCK_MAX_TOOL_NAME = 64


def tool_name(server: str, tool: str) -> str:
    """Bedrock-safe tool name: 'server__tool' if it fits, else 'tool_<hash>'."""
    safe_server = server.replace("-", "_").replace(" ", "_")
    full = f"{safe_server}__{tool}"
    if len(full) <= BEDROCK_MAX_TOOL_NAME:
        return full
    digest = hashlib.sha1(full.encode("utf-8")).hexdigest()[:8]
    # Keep the tail of the tool name (the descriptive part); 55 + "_" + 8 = 64.
    return f"{tool[-(BEDROCK_MAX_TOOL_NAME - 9):]}_{digest}"


def parse_tool_name(prefixed: str) -> tuple[str, str]:
    """Split namespaced tool name into (server, tool)."""
    server, _, tool = prefixed.partition("__")
    if not tool:
        raise ValueError(f"Invalid tool name (expected server__tool): {prefixed}")
    return server, tool
