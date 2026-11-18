"""Display-only masking of sensitive values.

Two layers:
- Aliases (e.g. ``my-prefix`` -> ``demo``) are always applied, so names read cleanly.
- Sensitive masking (UUIDs, IPv4 addresses, emails -> ``•••``) is applied only
  when ``sensitive`` is True (the UI toggle).

Masking only changes what the UI shows. The agent and the MCP tools always
receive the real values.
"""

from __future__ import annotations

import re
from typing import Any

MASK = "•••"

_EMAIL = re.compile(r"[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}")
_UUID = re.compile(
    r"\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b"
)
_IPV4 = re.compile(r"\b(?:(?:25[0-5]|2[0-4]\d|1?\d?\d)\.){3}(?:25[0-5]|2[0-4]\d|1?\d?\d)\b")


def mask_text(
    text: str,
    aliases: dict[str, str] | None = None,
    sensitive: bool = True,
) -> str:
    """Apply sensitive masking (if enabled) and then aliases to text."""
    if not text:
        return text
    out = text
    if sensitive:
        # Emails first, so an address containing an alias key is masked whole.
        out = _EMAIL.sub(MASK, out)
        out = _UUID.sub(MASK, out)
        out = _IPV4.sub(MASK, out)
    for old, new in (aliases or {}).items():
        if old:
            out = re.sub(re.escape(old), new, out, flags=re.IGNORECASE)
    return out


def mask_obj(
    obj: Any,
    aliases: dict[str, str] | None = None,
    sensitive: bool = True,
) -> Any:
    """Recursively apply mask_text to every string in a dict/list structure."""
    if isinstance(obj, str):
        return mask_text(obj, aliases, sensitive)
    if isinstance(obj, dict):
        return {k: mask_obj(v, aliases, sensitive) for k, v in obj.items()}
    if isinstance(obj, list):
        return [mask_obj(v, aliases, sensitive) for v in obj]
    return obj
