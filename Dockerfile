FROM maven:3-jdk-8
RUN apt-get update && \
    apt-get install -y make && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*
WORKDIR /usr/src
COPY . .
RUN make
ENV ACCOUNT_DATABASE_URL=postgres://postgres:@db/account \
    MESSAGE_DATABASE_URL=postgres://postgres:@db/message \
    REDIS_URL=redis://redis:6379
EXPOSE 8180
CMD ["make", "run"]
