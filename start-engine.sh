#!/usr/bin/env bash
# ==============================================================================
# Production Deployment & Startup Script for High-Performance Order Matching Engine
# ==============================================================================

set -e

echo "=========================================================================="
echo " Building High-Performance Order Matching Engine..."
echo "=========================================================================="
mvn clean package -DskipTests

JAR_FILE="target/order-matching-engine-1.0.0-SNAPSHOT.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "Error: Jar file $JAR_FILE not found after build!"
    exit 1
fi

echo "=========================================================================="
echo " Starting Matching Engine & gRPC Server with Optimized JVM Flags..."
echo "=========================================================================="

# Low-Latency Production JVM Configuration:
# - Generational ZGC for <1ms pause times
# - Pre-touched heap to prevent OS page fault latency spikes
# - Cache-line aware reflection exports for Java 17/21
JAVA_OPTS="
  -XX:+UseZGC
  -XX:+ZGenerational
  -Xms4g
  -Xmx4g
  -XX:+AlwaysPreTouch
  -XX:+UseLargePages
  -XX:ZAllocationSpikeTolerance=5
  -XX:GuaranteedSafepointInterval=0
  -XX:+UseNUMA
  --add-exports=java.base/jdk.internal.ref=ALL-UNNAMED
  --add-opens=java.base/java.lang=ALL-UNNAMED
  --add-opens=java.base/java.nio=ALL-UNNAMED
  --add-opens=java.base/sun.nio.ch=ALL-UNNAMED
  -Dcom.sun.management.jmxremote
  -Dcom.sun.management.jmxremote.port=9010
  -Dcom.sun.management.jmxremote.authenticate=false
  -Dcom.sun.management.jmxremote.ssl=false
"

exec java $JAVA_OPTS -jar "$JAR_FILE" "$@"
