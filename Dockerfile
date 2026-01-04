FROM eclipse-temurin:21-jre-alpine

# [수정됨] tzdata 설치할 때 curl도 같이 설치 (한 줄로 처리)
RUN apk add --no-cache tzdata curl && \
    cp /usr/share/zoneinfo/Asia/Seoul /etc/localtime && \
    echo "Asia/Seoul" > /etc/timezone

ENV TZ=Asia/Seoul

WORKDIR /app
COPY target/*.jar app.jar

CMD ["java", "-Duser.timezone=Asia/Seoul", "-jar", "app.jar"]