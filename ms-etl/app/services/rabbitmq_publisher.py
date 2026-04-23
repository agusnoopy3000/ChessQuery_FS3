import json
import uuid
import logging
import os
from datetime import datetime, timezone

import pika

logger = logging.getLogger(__name__)

RABBITMQ_URL = os.getenv("RABBITMQ_URL", "amqp://chessquery:chessquery_dev@localhost:5672/")
EXCHANGE = "ChessEvents"


def _get_connection():
    params = pika.URLParameters(RABBITMQ_URL)
    return pika.BlockingConnection(params)


def publish_event(event_type: str, payload: dict):
    try:
        connection = _get_connection()
        channel = connection.channel()
        channel.exchange_declare(exchange=EXCHANGE, exchange_type="topic", durable=True)
        message = {
            "eventId": str(uuid.uuid4()),
            "eventType": event_type,
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "payload": payload,
        }
        channel.basic_publish(
            exchange=EXCHANGE,
            routing_key=event_type,
            body=json.dumps(message),
            properties=pika.BasicProperties(
                content_type="application/json",
                delivery_mode=2,
            ),
        )
        connection.close()
        logger.info(f"Published event {event_type}: {payload}")
    except Exception as e:
        logger.error(f"Failed to publish event {event_type}: {e}")
