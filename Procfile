web:    java $JAVA_OPTS -Ddw.http.port=$PORT -Ddw.http.adminPort=$ADMIN_PORT -Ddw.federation.name=$FEDERATION_NAME -Ddw.federation.herokuPeers="$FEDERATED_PEERS" -Ddw.twilio.accountId=$TWILIO_ACCOUNT_SID -Ddw.twilio.accountToken=$TWILIO_ACCOUNT_TOKEN -Ddw.twilio.number=$TWILIO_NUMBER -Ddw.nexmo.apiKey=$NEXMO_KEY -Ddw.nexmo.apiSecret=$NEXMO_SECRET -Ddw.nexmo.number=$NEXMO_NUMBER -Ddw.gcm.apiKey=$GCM_KEY -Ddw.apn.certificate="$APN_CERTIFICATE" -Ddw.apn.key="$APN_KEY" -Ddw.s3.accessKey=$AWS_ACCESS_KEY -Ddw.s3.accessSecret=$AWS_SECRET_KEY -Ddw.s3.attachmentsBucket=$AWS_ATTACHMENTS_BUCKET -Ddw.memcache.servers=$MEMCACHIER_SERVERS -Ddw.memcache.user=$MEMCACHIER_USERNAME -Ddw.memcache.password=$MEMCACHIER_PASSWORD -Ddw.redis.url=$REDIS_URL -Ddw.database.driverClass=org.postgresql.Driver -Ddw.database.user=`echo $DATABASE_URL | awk -F'://' {'print $2'} | awk -F':' {'print $1'}` -Ddw.database.password=`echo $DATABASE_URL | awk -F'://' {'print $2'} | awk -F':' {'print $2'} | awk -F'@' {'print $1'}` -Ddw.database.url=jdbc:postgresql://`echo $DATABASE_URL | awk -F'@' {'print $2'}` -jar target/TextSecureServer-0.99.1.jar server
dir:    java $JAVA_OPTS -Ddw.http.port=$PORT -Ddw.http.adminPort=$ADMIN_PORT -Ddw.federation.name=$FEDERATION_NAME -Ddw.federation.herokuPeers="$FEDERATED_PEERS" -Ddw.twilio.accountId=$TWILIO_ACCOUNT_SID -Ddw.twilio.accountToken=$TWILIO_ACCOUNT_TOKEN -Ddw.twilio.number=$TWILIO_NUMBER -Ddw.nexmo.apiKey=$NEXMO_KEY -Ddw.nexmo.apiSecret=$NEXMO_SECRET -Ddw.nexmo.number=$NEXMO_NUMBER -Ddw.gcm.apiKey=$GCM_KEY -Ddw.apn.certificate="$APN_CERTIFICATE" -Ddw.apn.key="$APN_KEY" -Ddw.s3.accessKey=$AWS_ACCESS_KEY -Ddw.s3.accessSecret=$AWS_SECRET_KEY -Ddw.s3.attachmentsBucket=$AWS_ATTACHMENTS_BUCKET -Ddw.memcache.servers=$MEMCACHIER_SERVERS -Ddw.memcache.user=$MEMCACHIER_USERNAME -Ddw.memcache.password=$MEMCACHIER_PASSWORD -Ddw.redis.url=$REDIS_URL -Ddw.database.driverClass=org.postgresql.Driver -Ddw.database.user=`echo $DATABASE_URL | awk -F'://' {'print $2'} | awk -F':' {'print $1'}` -Ddw.database.password=`echo $DATABASE_URL | awk -F'://' {'print $2'} | awk -F':' {'print $2'} | awk -F'@' {'print $1'}` -Ddw.database.url=jdbc:postgresql://`echo $DATABASE_URL | awk -F'@' {'print $2'}` -jar target/TextSecureServer-0.99.1.jar directory
