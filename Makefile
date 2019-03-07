default: build

ACCOUNT_DATABASE_URL ?= postgres://postgres:NOPE@localhost:5432/account
MESSAGE_DATABASE_URL ?= postgres://postgres:NOPE@localhost:5432/message

VERSION := 1.49
TARGET := target/TextSecureServer-$(VERSION).jar
CONFIG := config/seed.yml
JAVA := java $(JAVA_OPTS)

ifdef REDIS_URL
  REDIS_DIR_URL=$(REDIS_URL)/0
  REDIS_CACHE_URL=$(REDIS_URL)/0
endif

get_db_username = $(shell echo $(1) | awk -F'://' {'print $$2'} | awk -F':' {'print $$1'})
get_db_password = $(shell echo $(1) | awk -F'://' {'print $$2'} | awk -F':' {'print $$2'} | awk -F'@' {'print $$1'})
get_db_host = $(shell echo $(1) | awk -F'@' {'print $$2'})
ifset = $(if $(1),$(2)="$(1)")

ARGS := \
		$(call ifset,$(S3_ACCESSKEY),-Ddw.s3.accessKey) \
		$(call ifset,$(S3_ACCESSSECRET),-Ddw.s3.accessSecret) \
		$(call ifset,$(S3_ATTACHMENTSBUCKET),-Ddw.s3.attachmentsBucket) \
		$(call ifset,$(S3_ENDPOINT),-Ddw.s3.endpoint) \
		$(call ifset,$(S3_REGION),-Ddw.s3.region) \
		$(call ifset,$(REDIS_DIR_URL),-Ddw.directory.url) \
		$(call ifset,$(REDIS_CACHE_URL),-Ddw.cache.url) \
		$(call ifset,$(PORT),-Ddw.server.applicationConnectors[0].port) \
		$(call ifset,$(ADMIN_PORT),-Ddw.server.adminConnectors[0].port) \
		$(call ifset,$(CCSM_PARTNER_TOKEN),-Ddw.trusted.partners[0].token) \
		$(call ifset,$(GCM_APIKEY),-Ddw.gcm.apiKey) \
		$(call ifset,$(APN_CERT),-Ddw.apn.pushCertificate) \
		$(call ifset,$(APN_KEY),-Ddw.apn.pushKey) \
		$(call ifset,$(APN_BUNDLEID),-Ddw.apn.bundleId) \
		$(if $(CCSM_PARTNER_TOKEN),-Ddw.trusted.partners[0].name=CCSM) \
		$(if $(PROM_METRICS),-Ddw.promMetrics.enabled=true) \
		$(if $(SENTRY_DSN),-Ddw.logging.appenders[0].type=sentry) \
		$(if $(SENTRY_DSN),-Ddw.logging.appenders[0].threshold=ERROR) \
		$(if $(SENTRY_DSN),-Ddw.logging.appenders[0].dsn=$(SENTRY_DSN)) \
		$(if $(SENTRY_DSN),-Ddw.logging.appenders[0].environment=$(STACK_ENV)) \
		-Ddw.database.url=jdbc:postgresql://$(call get_db_host,$(ACCOUNT_DATABASE_URL)) \
		-Ddw.database.user=$(call get_db_username,$(ACCOUNT_DATABASE_URL)) \
		-Ddw.database.password=$(call get_db_password,$(ACCOUNT_DATABASE_URL)) \
		-Ddw.messageStore.url=jdbc:postgresql://$(call get_db_host,$(MESSAGE_DATABASE_URL)) \
		-Ddw.messageStore.user=$(call get_db_username,$(MESSAGE_DATABASE_URL)) \
		-Ddw.messageStore.password=$(call get_db_password,$(MESSAGE_DATABASE_URL))


RUN := $(JAVA) $(ARGS) -jar $(TARGET)

build:
	mvn install -DskipTests

clean:
	mvn clean

test:
	mvn test

dbmigrate:
	$(RUN) accountdb migrate $(CONFIG)
	$(RUN) messagedb migrate $(CONFIG)

run: dbmigrate
	$(RUN) server $(CONFIG)

docker-build:
	docker build -t textsecure-server .

docker-run:
	docker run -it -p 8080:8080 textsecure-server
