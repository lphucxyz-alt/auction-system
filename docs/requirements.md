# Đối chiếu đề BTL và bằng chứng kiểm tra

Đối chiếu `2026-BTL (1).pdf`, `Hướng dẫn BTL LTNC-2026.pdf` và kế hoạch dự án đã cung cấp.
Không áp dụng phần chia vai cá nhân theo yêu cầu người dùng; không tạo lịch sử đóng góp giả.

| Yêu cầu | Vị trí triển khai | Bằng chứng |
|---|---|---|
| Entity, User và ba vai trò, Item và ba nhóm | common/model | Phân cấp abstract, override role/category/printInfo |
| OOP, đóng gói | Entity, Item, User, Auction | Trường private, lịch sử bất biến, Factory |
| Singleton / Factory / Observer | AuctionManager, ItemFactory, BidListener | Demo Singleton, tạo sản phẩm thật qua Factory, sự kiện bid thật |
| Đăng ký/đăng nhập | UserService, AuctionServer.Session | Test đăng nhập, vai trò, trùng tài khoản, hash |
| Admin | AuctionApplication | Danh sách user không lộ hash, quản lý mọi phiên |
| CRUD sản phẩm | AuctionApplication + SellerController | Tạo/sửa/xóa nháp, ownership, khóa sửa khi đang chạy |
| Đặt giá và người dẫn đầu | Auction.placeBid | Bid > giá hiện tại, số nguyên, không tự bid |
| Tự đóng, người thắng, lifecycle | tick, AuctionStatus | Đúng deadline, FINISHED/PAID/CANCELED, không PAID nếu không thắng |
| Xử lý lỗi | ApiException, domain exceptions, AuctionConnection | Dữ liệu sai, mất mạng, timeout, lỗi lưu, snapshot hỏng |
| JavaFX/FXML + MVC | client/resources + controllers | Test tải FXML, login/bid thật, ảnh chụp từ test |
| Concurrency | Auction lock + service command monitor | 100 luồng lặp 100 lần; 12 socket đồng thời |
| Realtime qua Socket | AuctionServer + AuctionConnection | Hai client, server đẩy, version, GUI/biểu đồ cập nhật |
| Lưu/tải serialization | FileStore | Atomic save, process lock, restart giữ người dùng/phiên/auto-bid |
| Maven / coding convention | pom.xml | verify bắt buộc Checkstyle Google, 0 vi phạm |
| JUnit / coverage ≥60% | */src/test + JaCoCo | Đã đo và kiểm ngưỡng cả GUI |
| GitHub Actions | .github/workflows/ci.yml | Đã cấu hình; chưa có bằng chứng remote run khi chưa push |
| Auto-bid | Auction.resolveAutoBids | PriorityQueue, cap, increment, hòa trần, lưu rule |
| Anti-sniping | Auction.accept | Trong 10s cuối gia hạn còn 30s, test endTime |
| Biểu đồ realtime | MainController.chart | Nhận bid từ socket thứ hai, kiểm số điểm chart |
| Tài liệu, báo cáo, demo | README + docs | Hướng dẫn chạy, giao thức, kiến trúc, báo cáo, slide |

## Kết quả tại môi trường hiện tại

Lệnh cuối `mvn -B -ntp verify` (kèm settings cloud) hoàn thành **BUILD SUCCESS**:

| Module | Lượt test | Fail / Error / Skip | Coverage dòng |
|---|---:|---|---:|
| common | 115 | 0 / 0 / 0 | 227/294 = 77,2% |
| server | 13 | 0 / 0 / 0 | 478/602 = 79,4% |
| client | 2 | 0 / 0 / 0 | 383/616 = 62,2% |
| **Tổng** | **130** | **0 / 0 / 0** | **1088/1512 = 72,0%** |

130 là số **lượt thực thi**, gồm test cạnh tranh được lặp 100 lần; không phải 130 phương thức
test độc lập. Không loại GUI hoặc lớp nghiệp vụ khỏi báo cáo để tăng coverage.
Checkstyle Google có **0 vi phạm** ở cả bốn reactor projects.

`python3 scripts/smoke.py` cũng PASS trên JAR thật: login, CRUD, hai bidder, auto-bid,
sự kiện tự đóng, xác nhận mô phỏng, restart tiến trình và kiểm dữ liệu giữ nguyên.
Hai test bổ sung kiểm lịch sử 105 lượt với phân trang và IO ghi thất bại fail-stop.

![Đăng nhập từ test FXML](images/login.png)
![Bid thật và biểu đồ nhận dữ liệu từ client thứ hai](images/auction-live.png)

## Phần cần người nộp thực hiện

- Đọc và giải thích được toàn bộ code; tập demo trên máy sẽ trình bày.
- Điền thông tin môn học/nhóm vào báo cáo, điều chỉnh slide theo thời lượng thực tế.
- Commit/push khi đã xem lại thay đổi, kiểm tra run GitHub Actions thật sau push.
- Lịch sử commit, trình bày và điểm chấm không thể được thay thế bởi một lượt triển khai.
- PAID là mô phỏng, Socket demo chưa có TLS; không mô tả đây là dịch vụ thương mại hoàn chỉnh.
