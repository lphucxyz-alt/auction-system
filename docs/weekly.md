# Kế hoạch bốn mảng công việc theo tuần

Đây là **kế hoạch phân công**, không phải báo cáo rằng bốn người đã làm các việc dưới đây
trong những tuần đã qua. A/B/C/D là ký hiệu mảng phụ trách, chưa gán tên thành viên.
Khi sử dụng cho một nhóm thật, ghi tên và bằng chứng hoàn thành ở cuối mỗi tuần.

## Nhánh và phạm vi

| Mảng | Nhánh theo ảnh | Phần phụ trách | Điểm bàn giao |
|---|---|---|---|
| A — Domain | `feature/domain-model` | Entity/User/Item, kế thừa, Factory, Singleton, sơ đồ lớp | API domain, dữ liệu sản phẩm, test model |
| B — Đấu giá | `feature/auction-model` | Auction, trạng thái, khóa, Observer, auto-bid, anti-sniping | Quy tắc bid, snapshot kết quả, test đồng thời |
| C — Server và mạng | `spike/socket-echo` | Giao thức, socket, session, phân quyền, DAO, build server | JSON mẫu, server chạy độc lập, test giao thức |
| D — Client và tích hợp | `feature/client-detail-ui` | FXML/MVC, lớp mạng client, biểu đồ, test GUI, CI và tài liệu chạy | Client chạy độc lập, demo hai client |

Giữ tên `spike/socket-echo` để khớp ảnh. Khi prototype echo đã xong, C tiếp tục triển khai
request/response thật; tên nhánh không có nghĩa server cuối cùng chỉ echo.
Phần E (test/GUI phụ) trong kế hoạch năm người được chia lại: A/B/C viết test phần mình,
D ghép test xuyên suốt và CI. Mọi mảng cùng review tài liệu, không dồn hết test cho D.

## Tuần 6–15

| Tuần | A — Domain | B — Đấu giá | C — Server/mạng | D — Client/tích hợp | Điều kiện chốt tuần |
|---|---|---|---|---|---|
| 6 | Đọc đề, xác định Entity/User/Item; vẽ lớp và tạo các loại sản phẩm | Chốt quy tắc giá và sơ đồ trạng thái, constructor Auction | Tạo Maven server, thử Socket echo hai tiến trình | Tạo Maven client, chạy JavaFX/FXML và form đăng nhập | Các lớp cơ bản và hai ứng dụng đều build được |
| 7 | Factory, getter/đóng gói, test cây kế thừa | BidTransaction, khóa theo phiên, Observer sau nhả khóa | Bộ xử lý mỗi kết nối, đóng socket sạch | Bảng phiên và khung chi tiết với dữ liệu thử trong nhánh UI | Test nhiều luồng không làm giá lùi; mẫu UI chạy được |
| 8 | Kiểm tra dữ liệu sản phẩm, exception và tài liệu domain | Hoàn thiện lifecycle, deadline, test bid sai/phiên đóng | Xác thực tài khoản và phân quyền dịch vụ | Tách Controller/Model, form sản phẩm, hiển thị lỗi | Quy tắc nghiệp vụ có test; controller không tự chọn người thắng |
| 9 | Review JSON dữ liệu sản phẩm, kiểm serialization entity | Nối Auction vào dịch vụ lệnh | Chốt protocol; LOGIN/REGISTER/LIST; DAO file; test restart | Viết lớp mạng client, nối đăng nhập/danh sách thật; bật Checkstyle/CI | Một client đăng nhập qua Socket; dữ liệu còn sau restart |
| 10 | Review thông tin sản phẩm và người dùng trong snapshot | Nối Observer với sự kiện giá/đóng phiên | Nhiều client, subscription, requestId, lỗi socket | Đủ luồng đăng ký, danh sách, chi tiết, bid, quản lý; gom coverage | Seller tạo phiên, hai bidder thấy giá realtime; coverage tổng ≥60% |
| 11 | Kiểm ownership, dữ liệu biên, cập nhật sơ đồ | Test bid sát deadline và bid đồng thời | Giới hạn thông điệp, client chậm, lỗi ghi file | Test GUI với server thật, kiểm nút theo vai trò | Luồng bắt buộc chạy ổn, không polling liên tục |
| 12 | Review chéo test và đóng gói domain trong server | Kết thúc/PAID mô phỏng/hủy, xử lý không có người thắng | Build từ riêng thư mục server, kiểm JAR và khôi phục | Build từ riêng thư mục client, kiểm không import server | Hai bản độc lập build được và vẫn nói cùng protocol |
| 13 | Review API, cập nhật tài liệu lớp | Auto-bid bằng PriorityQueue, anti-sniping, test trần/hòa trần | Kiểm dữ liệu rule auto khi restart | Biểu đồ giá realtime, hoàn thiện README và kịch bản demo | Bắt buộc ổn trước khi ghép nâng cao; đóng gói bản trình diễn |
| 14 | Giải thích OOP/Factory/Singleton | Giải thích khóa/Observer/auto-bid | Giải thích Socket/DAO/quyền và lỗi mạng | Tổng hợp slide, chạy demo, sửa lỗi hiển thị cuối | Tập trình bày và chấm nếu lịch lớp ở tuần 14 |
| 15 | Review câu hỏi domain | Review câu hỏi thuật toán | Chuẩn bị bản dự phòng | Trình bày theo lịch chính thức, giữ gói chạy đã kiểm tra | Theo hướng dẫn tuần 15; nếu lớp chấm tuần 14 thì tuần này dự phòng |

Các mốc theo số tuần môn học, không tự gắn ngày lịch hay ghi lùi ngày commit.
Hướng dẫn chung ghi tuần 15 trình bày, kế hoạch nhóm ghi tuần 14; xác nhận lịch thực tế với giảng viên.

## Quy trình Git từ thời điểm áp dụng

1. Lấy nhánh tích hợp mới nhất, tạo nhánh công việc tương ứng.
2. Commit từng thay đổi có ý nghĩa với ngày và danh tính thực; không sửa lại lịch sử cũ.
3. Ghi phần đã xong, phụ thuộc và cách kiểm thử vào mẫu dưới đây.
4. Review/test trước khi ghép vào nhánh tích hợp, đưa bản ổn định vào `main`.
5. Với công việc phụ thuộc nhau, ghi nhánh gốc trong PR để người review biết thứ tự.

Không ghi nhiệm vụ dự kiến thành thành tích đã hoàn thành. Tiến độ thực của đợt refactor này
được ghi riêng ở [development-log.md](development-log.md), với commit hiện tại.

## Mẫu ghi nhận cuối tuần

```text
Tuần:
Người thực hiện / mảng A, B, C hoặc D:
Đã hoàn thành:
Chưa hoàn thành:
Phụ thuộc / người đang chờ:
Commit hoặc PR thật:
Lệnh kiểm thử và kết quả:
Việc tuần tới:
```
