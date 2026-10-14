# Online Auction System

Bài tập lớn Lập trình nâng cao - Hệ thống đấu giá trực tuyến (Java 21, JavaFX, Maven).

## Yêu cầu môi trường
- JDK 21
- Maven 3.9+

## Build & Test
```bash
mvn clean install -DskipTests
mvn test
cd client
mvn javafx:run
mkdir -p .github/workflows docs

cat << 'EOF' > README.md
# Online Auction System

Bai tap lon Lap trinh nang cao - He thong dau gia truc tuyen (Java 21, JavaFX, Maven).

## Yeu cau moi truong
- JDK 21
- Maven 3.9+

## Build va Test
- Chay lenh: mvn clean install -DskipTests
- Chay test: mvn test

## Chay Client
- Chay lenh: cd client && mvn javafx:run
