Signal-Server
=================
Signal E2E message server.


Building
--------

    make


Running
--------

    make run



Configuration
--------

Configuration is achieved via setting env vars.

### Required

    APN_CERT
    APN_KEY
    APN_BUNDLEID

    GCM_APIKEY
    GCM_SENDERID

    ACCOUNT_DATABASE_URL
    MESSAGE_DATABASE_URL

    REDIS_URL

    S3_ACCESSKEY
    S3_ACCESSSECRET
    S3_ATTACHMENTSBUCKET

### Optional

    PORT
    ADMIN_PORT
    CCSM_PARTNER_TOKEN


License
--------
Licensed under the AGPLv3: https://www.gnu.org/licenses/agpl-3.0.html

* Copyright 2013 Open Whisper Systems
* Copyright 2017-2018 Forsta Inc.
