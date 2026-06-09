FROM eclipse-temurin:21-jre-alpine

LABEL org.opencontainers.image.title="mcpm"
LABEL org.opencontainers.image.description="MCP Package Manager"
LABEL org.opencontainers.image.licenses="Apache-2.0"
LABEL org.opencontainers.image.url="https://github.com/mcpm/mcpm"

RUN addgroup -S mcpm && adduser -S mcpm -G mcpm

WORKDIR /home/mcpm

COPY mcpm-cli/target/mcpm-cli-*.jar /usr/local/lib/mcpm.jar

RUN echo '#!/bin/sh\nexec java -jar /usr/local/lib/mcpm.jar "$@"' > /usr/local/bin/mcpm \
    && chmod +x /usr/local/bin/mcpm

USER mcpm

ENTRYPOINT ["mcpm"]
CMD ["--help"]
