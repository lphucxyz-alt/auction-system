# Kế hoạch năm vai A–E theo tuần

Phân vai dựa trên tài liệu **Phân công chi tiết 5 vai trò A-B-C-D-E**. Kết quả kỹ thuật của
từng vai nằm trong [bảng bàn giao](requirements.md). Nhật ký và PR thực tế là căn cứ ghi
tên, công việc và mức đóng góp của từng thành viên.

## Nhánh và phạm vi

| Vai | Nhánh công việc | Phần phụ trách | Điểm bàn giao |
|---|---|---|---|
| A — Domain và mẫu thiết kế | `feature/domain-model` | Entity/User/Item, cây kế thừa, Singleton, Factory Method, hỗ trợ Observer | Lớp dữ liệu và kiểm thử mẫu thiết kế |
| B — Logic đấu giá và đa luồng | `feature/auction-model` | Auction, BidTransaction, trạng thái, khóa, tự đóng và ngoại lệ | Quy tắc giá, người thắng và kiểm thử đồng thời |
| C — Server, mạng và dữ liệu | `spike/socket-echo` | Socket JSON, session, quyền, đăng nhập, lưu file và đẩy sự kiện | Giao thức, server chạy riêng và phục hồi dữ liệu |
| D — Client GUI và mạng | `feature/client-detail-ui` | JavaFX/FXML MVC, đăng nhập/đăng ký, danh sách, chi tiết, đặt giá realtime | Client chạy riêng; hai client thấy cùng giá và lịch sử |
| E — Khung dự án, test và GUI phụ | `feature/maven-setup` | Maven, GitHub Actions, Checkstyle, JaCoCo, README, Seller CRUD và Admin | Build kiểm tra được; Seller và Admin thao tác qua GUI với server thật |

## Tuần 6–15

| Tuần | A — Domain | B — Đấu giá | C — Server/mạng | D — Client GUI | E — Build, test, Seller/Admin |
|---|---|---|---|---|---|
| 6 | Xác định lớp và mẫu thiết kế | Chốt quy tắc giá, trạng thái và cấu trúc Auction | Thử Socket echo | Chạy JavaFX/FXML và form đăng nhập | Khung Maven và quy ước Git |
| 7 | Viết Factory Method, kiểm cây kế thừa | BidTransaction, khóa theo phiên và Observer | Xử lý mỗi kết nối và đóng socket | Danh sách phiên và chi tiết | Bố cục Seller/Admin và test nền |
| 8 | Kiểm dữ liệu biên và ngoại lệ | Kiểm thời gian, chuyển trạng thái và giá sai | Xác thực, đăng ký và phân quyền | Tách FXML, controller và model hiển thị | Test JUnit và form Seller |
| 9 | Kiểm dữ liệu JSON và serialization | Nối Auction vào dịch vụ lệnh | Chốt protocol, Socket và DAO file | Nối đăng nhập/danh sách qua mạng | Maven, Checkstyle, GitHub Actions |
| 10 | Kiểm Singleton và Factory trong luồng thật | Nối sự kiện giá và tự đóng | Kiểm nhiều client và subscription | Đặt giá, chi tiết realtime và lịch sử | Seller CRUD, Admin, JaCoCo ≥60% |
| 11 | Kiểm ownership và dữ liệu biên | Test bid sát hạn và nhiều bidder | Kiểm lỗi mạng, client chậm, lỗi ghi file | Test GUI và xử lý mất kết nối | Test hết luồng tạo/sửa/xóa và quyền Admin |
| 12 | Review mẫu thiết kế và mã domain | Chốt hủy, người thắng và PAID mô phỏng | Kiểm JAR và khôi phục dữ liệu | Kiểm client chạy từ thư mục riêng | Build độc lập, CI và kiểm thử chéo |
| 13 | Rà API, tài liệu lớp và test | Rà hạn đóng, lịch mở, quyền hủy | Kiểm phiên và lịch sử sau restart | Hoàn thiện GUI và kịch bản demo | Kiểm gói bàn giao và hướng dẫn terminal |
| 14 | Tập giải thích OOP và pattern | Tập giải thích khóa, Observer và hạn đóng | Tập giải thích giao thức, DAO và quyền | Chạy thử demo GUI | Chuẩn bị số liệu test, slide và gói dự phòng |
| 15 | Trình bày thiết kế | Trình bày nghiệp vụ | Trình bày mạng và dữ liệu | Chạy demo | Trình bày CI và kiểm thử |

Hướng dẫn của thầy ghi tuần 15 trình bày. Kế hoạch nhóm dùng tuần 14 và tuần 15 dự phòng.
[Bảng tra từng file](weekly-files.md) dùng để đọc code và chuẩn bị vấn đáp.

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
Người thực hiện / mảng A, B, C, D hoặc E:
Đã hoàn thành:
Chưa hoàn thành:
Phụ thuộc / người đang chờ:
Commit hoặc PR thật:
Lệnh kiểm thử và kết quả:
Việc tuần tới:
```
