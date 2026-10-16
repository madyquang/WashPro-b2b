# Backend: build bằng Maven, chạy bằng JRE gọn nhẹ
# JDK 25 build được cả cấu hình java.version 21 lẫn 25
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
# Tải thư viện trước (được cache khi chỉ sửa mã nguồn)
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
ENV TZ=Asia/Ho_Chi_Minh \
    JAVA_TOOL_OPTIONS="-Duser.timezone=Asia/Ho_Chi_Minh"
COPY --from=build /app/target/washpro-b2b-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
