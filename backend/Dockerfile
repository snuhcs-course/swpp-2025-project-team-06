FROM python:3.13-slim

WORKDIR /app

RUN apt-get update && apt-get install -y build-essential libpq-dev && rm -rf /var/lib/apt/lists/*

# 프로젝트 복사
COPY . /app

# uv 설치
RUN pip install uv

# ✅ Docker 전용 가상환경 생성
ENV VIRTUAL_ENV=/app/.venv
RUN uv venv --python 3.13 && uv sync --frozen --active

# ✅ PATH에 가상환경 추가 (이게 중요)
ENV PATH="/app/.venv/bin:$PATH"

EXPOSE 8000

CMD ["python", "manage.py", "runserver", "0.0.0.0:8000"]