"""Loads config.json into typed config objects used across the app."""

import json
import os
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

# src/rbac_shoe_bot/config.py -> parents[0]=rbac_shoe_bot, [1]=src, [2]=project root
DEFAULT_CONFIG_PATH = Path(__file__).resolve().parents[2] / "config.json"


@dataclass
class BedrockConfig:
    region: str = "us-east-1"
    model_id: str = "us.anthropic.claude-haiku-4-5-20251001-v1:0"
    system_prompt: str = "You are a helpful assistant."
    max_tokens: int = 4096
    temperature: float = 0.5
    max_tool_rounds: int = 10


@dataclass
class Auth0Config:
    domain: str
    client_id: str
    client_secret: str
    audience: str
    scope: str = "openid profile email mcp_roles"
    roles_claim: str = "https://mcp_gateway/user_roles"
    redirect_uri: str = "http://localhost:8501"
    logout_return_to: str = "http://localhost:8501"

    @property
    def authorize_url(self) -> str:
        return f"https://{self.domain}/authorize"

    @property
    def token_url(self) -> str:
        return f"https://{self.domain}/oauth/token"

    @property
    def logout_url(self) -> str:
        return f"https://{self.domain}/v2/logout"


@dataclass
class McpServerConfig:
    name: str
    transport: str
    url: str


@dataclass
class AppConfig:
    bedrock: BedrockConfig
    auth0: Auth0Config
    mcp_servers: list[McpServerConfig] = field(default_factory=list)


def load_config(path: str | Path | None = None) -> AppConfig:
    config_path = Path(path) if path else Path(os.environ.get("SHOE_BOT_CONFIG", DEFAULT_CONFIG_PATH))
    if not config_path.exists():
        raise FileNotFoundError(
            f"Config file not found at {config_path}. Copy config.example.json to "
            "config.json and fill in your Auth0 and MCP Gateway details."
        )

    with config_path.open() as f:
        raw: dict[str, Any] = json.load(f)

    if "auth0" not in raw:
        raise ValueError(
            "config.json is missing an 'auth0' section. This demo requires Auth0 "
            "OAuth login — see config.example.json for the required fields."
        )

    bedrock = BedrockConfig(**raw.get("bedrock", {}))
    auth0 = Auth0Config(**raw["auth0"])

    servers_raw = raw.get("mcp", {}).get("servers", {})
    mcp_servers = [
        McpServerConfig(name=name, transport=cfg["transport"], url=cfg["url"]) for name, cfg in servers_raw.items()
    ]

    if not mcp_servers:
        raise ValueError("config.json's mcp.servers must define at least one MCP server.")

    return AppConfig(bedrock=bedrock, auth0=auth0, mcp_servers=mcp_servers)
