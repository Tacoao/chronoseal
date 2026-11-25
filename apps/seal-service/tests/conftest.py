from __future__ import annotations

import os
from datetime import UTC, datetime, timedelta

import pytest

from app.keys import MasterKey
from app.sealer import TimeLockSealer

START = datetime(2026, 11, 25, 10, 0, tzinfo=UTC)


class FakeClock:
    def __init__(self, start: datetime) -> None:
        self.now = start

    def __call__(self) -> datetime:
        return self.now

    def advance(self, **delta: float) -> None:
        self.now += timedelta(**delta)


@pytest.fixture
def clock() -> FakeClock:
    return FakeClock(START)


@pytest.fixture
def master_key() -> MasterKey:
    return MasterKey("test-key", os.urandom(32))


@pytest.fixture
def sealer(master_key: MasterKey, clock: FakeClock) -> TimeLockSealer:
    return TimeLockSealer(master_key, clock)
