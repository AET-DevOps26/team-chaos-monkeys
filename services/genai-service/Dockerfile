FROM python:3.12-slim AS build

WORKDIR /app

COPY requirements.txt .

RUN pip install --no-cache-dir --prefix=/install -r requirements.txt

FROM python:3.12-slim

WORKDIR /app

COPY --from=build /install /usr/local

COPY . .

EXPOSE 8087

ENTRYPOINT ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8087"]
