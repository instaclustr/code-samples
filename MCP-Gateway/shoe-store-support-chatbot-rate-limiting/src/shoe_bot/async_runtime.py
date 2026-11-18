"""Persistent asyncio loop for Streamlit (MCP requires long-lived connections)."""

from __future__ import annotations

import asyncio
import threading
from collections.abc import Coroutine
from typing import Any, TypeVar

import streamlit as st

T = TypeVar("T")


def _start_background_loop() -> asyncio.AbstractEventLoop:
    loop = asyncio.new_event_loop()

    def run() -> None:
        asyncio.set_event_loop(loop)
        loop.run_forever()

    thread = threading.Thread(target=run, name="shoe-bot-async", daemon=True)
    thread.start()
    return loop


def get_event_loop() -> asyncio.AbstractEventLoop:
    """Return a background event loop tied to this Streamlit session."""
    if "async_loop" not in st.session_state:
        st.session_state.async_loop = _start_background_loop()
    return st.session_state.async_loop


def run_async(coro: Coroutine[Any, Any, T]) -> T:
    """Run a coroutine on the session's background loop."""
    loop = get_event_loop()
    future = asyncio.run_coroutine_threadsafe(coro, loop)
    return future.result()
