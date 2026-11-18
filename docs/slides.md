# Hệ thống đấu giá trực tuyến — Nội dung trình bày

## 1. Bài toán

Nhiều người cùng đấu giá sản phẩm, giá thay đổi realtime, phiên tự đóng và chọn một người thắng.
Ba vai trò: Bidder, Seller, Admin. Công nghệ Java 21, JavaFX, Maven, Socket JSON.

## 2. Kiến trúc

Client và server là hai ứng dụng riêng trong parent Maven ba module. JavaFX/FXML →
Controller → AuctionConnection → Socket JSON → Service/domain → DAO/file. Test GUI ghép
hai bên trong test scope của client.
Chỉ server thay đổi dữ liệu nghiệp vụ; client hiển thị snapshot có version.

## 3. OOP và patterns

Entity → User/Item/Auction; User → Bidder/Seller/Admin; Item → Electronics/Art/Vehicle.
Đóng gói, trừu tượng, kế thừa, đa hình. Mỗi ItemType override Factory Method để tạo Item.
AuctionManager là Singleton khởi động và đóng server. Observer phát giá đã được lưu.

## 4. Đồng thời

ReentrantLock từng phiên; giá, leader, history ghi cùng transaction domain.
Facade tuần tự hóa persist + event. Bid đến endTime bị từ chối ngay cả khi clock chạy trễ.
Notify ngoài khóa; mỗi socket có hàng đợi riêng.

## 5. Kết quả phiên đấu giá

Server tự đóng phiên theo lịch. Người đặt giá cao nhất được xác nhận thanh toán mô phỏng.
Nếu phiên chưa có giá, hệ thống kết thúc phiên và hiển thị kết quả không có người thắng.

## 6. Lưu dữ liệu và bảo vệ tài khoản

PBKDF2 + salt, không gửi hash trên mạng. Phân quyền kiểm tra ở server.
Serialization server-local, object filter, fsync + atomic replace, process lock.
TCP demo chưa có TLS; PAID là mô phỏng.

## 7. Kiểm thử và demo

JUnit domain + socket nhiều client + restart + GUI headless nhận bid thật.
Checkstyle Google, JaCoCo và GitHub Actions. Dùng số liệu mới nhất trong requirements.md
hoặc báo cáo target; không đọc số lượng test cũ khi đã thay đổi code.
Chạy kịch bản trong demo.md.

## 8. Phân công bàn giao

A trình bày mô hình và mẫu thiết kế. B giải thích khóa, giá và hạn đóng. C trình bày Socket
và lưu dữ liệu. D thao tác JavaFX. E trình bày Maven, CI, kiểm thử và quản lý Seller/Admin.
