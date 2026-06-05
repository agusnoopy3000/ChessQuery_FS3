import os
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, DeclarativeBase

# DATABASE_URL explícito (docker-compose / local) o construido desde partes
# (cluster ECS: el password viene de Secrets Manager como DB_PASSWORD, sin
# quedar en texto plano dentro del task-def).
DATABASE_URL = os.getenv("DATABASE_URL")
if not DATABASE_URL:
    _host = os.getenv("DB_HOST", "localhost")
    _port = os.getenv("DB_PORT", "5432")
    _user = os.getenv("DB_USER", "chessquery")
    _pwd = os.getenv("DB_PASSWORD", "chessquery_dev")
    _name = os.getenv("DB_NAME", "etl_db")
    DATABASE_URL = f"postgresql://{_user}:{_pwd}@{_host}:{_port}/{_name}"

engine = create_engine(DATABASE_URL)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


class Base(DeclarativeBase):
    pass


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
