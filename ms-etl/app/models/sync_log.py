from sqlalchemy import Column, Integer, String, DateTime, Text
from datetime import datetime
from app.database import Base


class EtlSyncLog(Base):
    __tablename__ = "etl_sync_log"

    id = Column(Integer, primary_key=True, autoincrement=True)
    source = Column(String(20), nullable=False)  # FIDE, AJEFECH, CHESS_RESULTS, LICHESS
    started_at = Column(DateTime, nullable=False, default=datetime.utcnow)
    finished_at = Column(DateTime, nullable=True)
    status = Column(String(10), nullable=False, default="RUNNING")  # RUNNING, SUCCESS, FAILED
    records_processed = Column(Integer, default=0)
    records_failed = Column(Integer, default=0)
    error_message = Column(Text, nullable=True)
    cb_state = Column(String(10), nullable=False, default="CLOSED")  # CLOSED, OPEN, HALF_OPEN
