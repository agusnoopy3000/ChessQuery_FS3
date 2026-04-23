import pytest
import asyncio
from app.services.circuit_breaker import CircuitBreaker


@pytest.mark.asyncio
async def test_circuit_breaker_opens_after_threshold():
    cb = CircuitBreaker("test", failure_threshold=3, reset_timeout=999)

    async def failing_func():
        raise Exception("Simulated failure")

    for _ in range(3):
        with pytest.raises(Exception):
            await cb.call(failing_func)

    assert cb.get_state() == CircuitBreaker.OPEN


@pytest.mark.asyncio
async def test_circuit_breaker_rejects_when_open():
    cb = CircuitBreaker("test", failure_threshold=1, reset_timeout=999)

    async def failing_func():
        raise Exception("fail")

    with pytest.raises(Exception):
        await cb.call(failing_func)

    assert cb.get_state() == CircuitBreaker.OPEN

    with pytest.raises(Exception, match="OPEN"):
        await cb.call(failing_func)


@pytest.mark.asyncio
async def test_circuit_breaker_closed_on_success():
    cb = CircuitBreaker("test")

    async def ok_func():
        return "ok"

    result = await cb.call(ok_func)
    assert result == "ok"
    assert cb.get_state() == CircuitBreaker.CLOSED
