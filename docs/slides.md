# Hệ thống đấu giá trực tuyến — Nội dung trình bày

## 1. Bài toán

Nhiều người cùng đấu giá sản phẩm, giá thay đổi realtime, phiên tự đóng và chọn một người thắng.
Ba vai trò: Bidder, Seller, Admin. Công nghệ Java 21, JavaFX, Maven, Socket JSON.

## 2. Kiến trúc

Client và server có POM độc lập. JavaFX/FXML → Controller → AuctionConnection →
Socket JSON → Service/domain → DAO/file. Test ghép hai bên ở integration-tests.
Chỉ server thay đổi dữ liệu nghiệp vụ; client hiển thị snapshot có version.

## 3. OOP và patterns

Entity → User/Item/Auction; User → Bidder/Seller/Admin; Item → Electronics/Art/Vehicle.
Encapsulation, abstraction, inheritance, polymorphism. Factory tạo Item, Observer phát bid,
Singleton holder trong AuctionManager. Chiếu sơ đồ ở architecture.md.

## 4. Đồng thời

ReentrantLock từng phiên; giá, leader, history ghi cùng transaction domain.
Facade tuần tự hóa persist + event. Bid đến endTime bị từ chối ngay cả khi clock chạy trễ.
Notify ngoài khóa; mỗi socket có hàng đợi riêng.

## 5. Tính năng nâng cao

PriorityQueue giải quyết trần auto-bid theo max và thứ tự đăng ký.
Anti-sniping dùng endTime động. LineChart cập nhật từ sự kiện server.

## 6. Lưu dữ liệu và bảo vệ tài khoản

PBKDF2 + salt, không gửi hash trên mạng. Phân quyền kiểm tra ở server.
Serialization server-local, object filter, fsync + atomic replace, process lock.
TCP demo chưa có TLS; PAID là mô phỏng.

## 7. Kiểm thử và demo

JUnit domain + socket nhiều client + restart + GUI headless nhận bid thật.
Checkstyle Google, JaCoCo và GitHub Actions. Dùng số liệu mới nhất trong requirements.md
hoặc báo cáo target; không đọc số lượng test cũ khi đã thay đổi code.
Chạy kịch bản trong demo.md.

## 8. Giới hạn và hướng phát triển

Snapshot file ghi đồng bộ phù hợp BTL, chưa tối ưu throughput nhiều phiên.
Hướng tiếp theo: JDBC transaction, TLS, idempotency xuyên restart, phân trang theo cursor,
triển khai desktop installers và thanh toán qua nhà cung cấp thực khi có yêu cầu.
