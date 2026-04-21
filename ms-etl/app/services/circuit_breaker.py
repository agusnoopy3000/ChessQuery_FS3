import time
import logging

logger = logging.getLogger(__name__)


class CircuitBreaker:
    CLOSED = "CLOSED"
    OPEN = "OPEN"
    HALF_OPEN = "HALF_OPEN"

    def __init__(self, name: str, failure_threshold: int = 3, reset_timeout: int = 30):
        self.name = name
        self.state = self.CLOSED
        self.failure_count = 0
        self.failure_threshold = failure_threshold
        self.reset_timeout = reset_timeout
        self.last_failure_time: float | None = None

    async def call(self, func, *args, **kwargs):
        if self.state == self.OPEN:
            if self.last_failure_time and (time.time() - self.last_failure_time) >= self.reset_timeout:
                logger.info(f"CircuitBreaker [{self.name}]: OPEN → HALF_OPEN")
                self.state = self.HALF_OPEN
            else:
                raise Exception(f"CircuitBreaker [{self.name}] is OPEN. Rejecting call.")

        try:
            result = await func(*args, **kwargs)
            self._on_success()
            return result
        except Exception as e:
            self._on_failure(e)
            raise

    def _on_success(self):
        if self.state == self.HALF_OPEN:
            logger.info(f"CircuitBreaker [{self.name}]: HALF_OPEN → CLOSED")
        self.state = self.CLOSED
        self.failure_count = 0
        self.last_failure_time = None

    def _on_failure(self, exc: Exception):
        self.failure_count += 1
        self.last_failure_time = time.time()
        logger.warning(
            f"CircuitBreaker [{self.name}]: failure {self.failure_count}/{self.failure_threshold} — {exc}"
        )
        if self.failure_count >= self.failure_threshold:
            if self.state != self.OPEN:
                logger.error(f"CircuitBreaker [{self.name}]: CLOSED → OPEN")
            self.state = self.OPEN

    def get_state(self) -> str:
        if self.state == self.OPEN and self.last_failure_time:
            if (time.time() - self.last_failure_time) >= self.reset_timeout:
                self.state = self.HALF_OPEN
        return self.state

    def reset(self):
        self.state = self.CLOSED
        self.failure_count = 0
        self.last_failure_time = None
