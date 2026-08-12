"""A persistent background asyncio event loop for use from Streamlit.

Streamlit reruns the whole script on every interaction, so calling
asyncio.run() per callback would tear down any long-lived resource (like an
open MCP ClientSession) between reruns. Instead we start a single event loop
on a background thread once per process and submit coroutines to it from
Streamlit's (synchronous) main thread, blocking for the result.

Each Streamlit session keeps its own McpManager (with its own streams/session,
opened with that user's access token) in st.session_state — this shared loop
is just the plumbing that runs everyone's coroutines, not shared auth state.
"""

import asyncio
import atexit
import threading
from concurrent.futures import Future
from typing import Any, Coroutine, TypeVar

T = TypeVar("T")


class AsyncRuntime:
    _instance: "AsyncRuntime | None" = None
    _instance_lock = threading.Lock()

    def __init__(self) -> None:
        self._loop = asyncio.new_event_loop()
        self._thread = threading.Thread(target=self._run_loop, name="mcp-async-runtime", daemon=True)
        self._thread.start()
        atexit.register(self.shutdown)

    def _run_loop(self) -> None:
        asyncio.set_event_loop(self._loop)
        self._loop.run_forever()

    @classmethod
    def instance(cls) -> "AsyncRuntime":
        with cls._instance_lock:
            if cls._instance is None:
                cls._instance = AsyncRuntime()
            return cls._instance

    def run(self, coro: Coroutine[Any, Any, T], timeout: float | None = 60) -> T:
        future: Future[T] = asyncio.run_coroutine_threadsafe(coro, self._loop)
        return future.result(timeout=timeout)

    def shutdown(self) -> None:
        if self._loop.is_running():
            self._loop.call_soon_threadsafe(self._loop.stop)


def run_async(coro: Coroutine[Any, Any, T], timeout: float | None = 60) -> T:
    return AsyncRuntime.instance().run(coro, timeout=timeout)
