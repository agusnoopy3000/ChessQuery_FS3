FROM python:3.11-slim
WORKDIR /app
COPY ms-etl/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY ms-etl/main.py .
COPY ms-etl/app ./app
EXPOSE 8086
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8086"]
