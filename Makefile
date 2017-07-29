
default: build

PORT ?= 8180
ADMIN_PORT ?= 8181
DATABASE_URL ?= postgres://postgres@localhost:5432/tss

VERSION := 0.99.1
TARGET := target/TextSecureServer-$(VERSION).jar
CONFIG := config/seed.yml
JAVA := java $(JAVA_OPTS)
DB_USERNAME := $(shell echo $(DATABASE_URL) | awk -F'://' {'print $$2'} | awk -F':' {'print $$1'})
DB_PASSWORD := $(shell echo $(DATABASE_URL) | awk -F'://' {'print $$2'} | awk -F':' {'print $$2'} | awk -F'@' {'print $$1'})
DB_HOST := $(shell echo $(DATABASE_URL) | awk -F'@' {'print $$2'})
ARGS := \
		-Ddw.twilio.accountId=$(TWILIO_ACCOUNT_ID) \
		-Ddw.twilio.accountToken=$(TWILIO_ACCOUNT_TOKEN) \
		-Ddw.twilio.numbers[0]=$(TWILIO_NUMBER) \
		-Ddw.twilio.localDomain=$(TWILIO_DOMAIN) \
		-Ddw.push.host=$(PUSH_HOST) \
		-Ddw.push.port=$(PUSH_PORT) \
		-Ddw.push.username=$(PUSH_USERNAME) \
		-Ddw.push.password=$(PUSH_PASSWORD) \
		-Ddw.s3.accessKey=$(AWS_ACCESS_KEY) \
		-Ddw.s3.accessSecret=$(AWS_SECRET_KEY) \
		-Ddw.s3.attachmentsBucket=$(AWS_ATTACHMENTS_BUCKET) \
		-Ddw.directory.url=$(REDIS_URL)/0 \
		-Ddw.cache.url=$(REDIS_URL)/1 \
		-Ddw.server.applicationConnectors[0].port=$(PORT) \
		-Ddw.server.adminConnectors[0].port=$(ADMIN_PORT) \
		-Ddw.database.user=$(DB_USERNAME) \
		-Ddw.database.password=$(DB_PASSWORD) \
		-Ddw.database.url=jdbc:postgresql://$(DB_HOST) \
		-Ddw.messageStore.user=$(DB_USERNAME) \
		-Ddw.messageStore.password=$(DB_PASSWORD) \
		-Ddw.messageStore.url=jdbc:postgresql://$(DB_HOST) \

RUN := $(JAVA) $(ARGS) -jar $(TARGET)


build:
	mvn install

dbmigrate:
	$(RUN) accountdb migrate $(CONFIG)
	$(RUN) messagedb migrate $(CONFIG)

run: dbmigrate
	$(RUN) server $(CONFIG)
