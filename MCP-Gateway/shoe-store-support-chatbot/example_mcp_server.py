#!/usr/bin/env python3
"""Minimal MCP server for local testing (stdio transport)."""

from mcp.server.fastmcp import FastMCP

mcp = FastMCP("shoe-bot-demo")


@mcp.tool()
def echo(message: str) -> str:
    """Echo a message back to the caller."""
    return message


@mcp.tool()
def add(a: float, b: float) -> str:
    """Add two numbers and return the sum."""
    return str(a + b)


if __name__ == "__main__":
    mcp.run()
