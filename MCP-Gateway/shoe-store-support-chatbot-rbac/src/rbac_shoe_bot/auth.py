"""Auth0 Authorization Code flow helpers.

This module implements the client side of the flow described in the MCP
Gateway "Auth0 example configuration" doc: the app redirects the user to
Auth0 to log in, requests the `mcp_roles` scope so Auth0's post-login Action
injects the user's roles into a custom claim on the access token, then
exchanges the returned authorization code for that access token.

The access token is passed straight through to the MCP Gateway as a Bearer
token — this app never validates the token signature itself. Token
validation and role-based tool authorization both happen on the Gateway
(the resource server), per the "Configure MCP Tool Access" doc.
"""

import base64
import json
import secrets
import threading
import time
from dataclasses import dataclass
from typing import Any
from urllib.parse import urlencode

import requests

from rbac_shoe_bot.config import Auth0Config


@dataclass
class TokenSet:
    access_token: str
    id_token: str | None
    expires_in: int
    token_type: str
    raw: dict[str, Any]


def new_state() -> str:
    """A random, unguessable value used to protect against CSRF on the callback."""
    return secrets.token_urlsafe(24)


# --- Process-level pending-state store -------------------------------------
#
# A full Auth0 login round trip navigates the browser away to a different
# origin and back. Streamlit's `st.session_state` is tied to a specific
# browser session, and that session does not reliably survive the trip (the
# WebSocket connection is torn down while the browser is on auth0.com, and by
# the time it reconnects to this app, Streamlit may have already started a
# brand-new session with empty session_state). Relying on session_state alone
# to verify the OAuth `state` therefore fails unpredictably.
#
# Instead, we track pending states in a plain module-level dict. Streamlit
# runs the whole app in a single Python process, so this dict is shared by
# every session/rerun in that process regardless of which browser session
# issued or is consuming a given state -- it isn't tied to any one session.
_pending_states: dict[str, float] = {}
_pending_states_lock = threading.Lock()
_PENDING_STATE_TTL_SECONDS = 600  # generous: enough time to type Auth0 credentials


def register_pending_state(state: str) -> None:
    """Record that `state` was just issued to a login attempt, process-wide."""
    with _pending_states_lock:
        _prune_pending_states_locked()
        _pending_states[state] = time.monotonic()


def consume_pending_state(state: str | None) -> bool:
    """Return True and forget `state` if it was issued and hasn't expired/been used."""
    if not state:
        return False
    with _pending_states_lock:
        _prune_pending_states_locked()
        if state not in _pending_states:
            return False
        del _pending_states[state]
        return True


def _prune_pending_states_locked() -> None:
    now = time.monotonic()
    expired = [s for s, issued_at in _pending_states.items() if now - issued_at > _PENDING_STATE_TTL_SECONDS]
    for s in expired:
        del _pending_states[s]


def build_login_url(auth0: Auth0Config, state: str) -> str:
    params = {
        "response_type": "code",
        "client_id": auth0.client_id,
        "redirect_uri": auth0.redirect_uri,
        "scope": auth0.scope,
        "audience": auth0.audience,
        "state": state,
        # Force the credential prompt even if Auth0 has an existing browser
        # session, so you can switch between test users/personas on every
        # login instead of silently reusing the last logged-in identity.
        "prompt": "login",
    }
    return f"{auth0.authorize_url}?{urlencode(params)}"


def build_logout_url(auth0: Auth0Config) -> str:
    params = {"client_id": auth0.client_id, "returnTo": auth0.logout_return_to}
    return f"{auth0.logout_url}?{urlencode(params)}"


def exchange_code_for_token(auth0: Auth0Config, code: str) -> TokenSet:
    resp = requests.post(
        auth0.token_url,
        json={
            "grant_type": "authorization_code",
            "client_id": auth0.client_id,
            "client_secret": auth0.client_secret,
            "code": code,
            "redirect_uri": auth0.redirect_uri,
        },
        timeout=10,
    )
    resp.raise_for_status()
    data = resp.json()
    return TokenSet(
        access_token=data["access_token"],
        id_token=data.get("id_token"),
        expires_in=data.get("expires_in", 0),
        token_type=data.get("token_type", "Bearer"),
        raw=data,
    )


def decode_jwt_payload(token: str) -> dict[str, Any]:
    """Decode a JWT payload for DISPLAY purposes only.

    This does NOT verify the token's signature or expiry. It exists so the UI
    can show which persona/role you're signed in as. The MCP Gateway performs
    the real verification (issuer, JWKS, audience) before honoring the token.
    """
    try:
        payload_segment = token.split(".")[1]
        padding = "=" * (-len(payload_segment) % 4)
        decoded = base64.urlsafe_b64decode(payload_segment + padding)
        return json.loads(decoded)
    except (IndexError, ValueError, UnicodeDecodeError):
        return {}


def extract_roles(token_payload: dict[str, Any], roles_claim: str) -> list[str]:
    roles = token_payload.get(roles_claim, [])
    if isinstance(roles, str):
        return [roles]
    if isinstance(roles, list):
        return [str(r) for r in roles]
    return []
