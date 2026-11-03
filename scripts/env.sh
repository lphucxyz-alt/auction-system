#!/usr/bin/env bash
# Optional cloud toolchain; ordinary machines use their installed JDK 21 / Maven.
if [ -d /workspace/.auction-tools/jdk-21.0.5+11 ]; then
  export JAVA_HOME=/workspace/.auction-tools/jdk-21.0.5+11
  export PATH="$JAVA_HOME/bin:/workspace/.auction-tools/apache-maven-3.9.9/bin:$PATH"
  export MAVEN_OPTS="${MAVEN_OPTS:-} -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
  AUCTION_MAVEN_ARGS=(-s /workspace/.auction-tools/settings.xml -Dmaven.repo.local=/workspace/.auction-tools/repository)
else
  AUCTION_MAVEN_ARGS=()
fi
