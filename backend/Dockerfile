FROM python:3.13-slim

WORKDIR /app

RUN apt-get update && apt-get install -y build-essential libpq-dev && rm -rf /var/lib/apt/lists/*

RUN pip install uv

ENV VIRTUAL_ENV=/app/.venv
ENV PATH="/app/.venv/bin:$PATH"

# 의존성 파일만 복사해서 캐시 활용
COPY pyproject.toml uv.lock* /app/

RUN uv venv --python 3.13 && uv sync --frozen --active

# 이후에 코드 복사
COPY . /app

EXPOSE 8000

CMD ["python", "manage.py", "runserver", "0.0.0.0:8000"]