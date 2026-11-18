# Báo cáo kỹ thuật — Hệ thống đấu giá trực tuyến

## 1. Mục tiêu và phạm vi

Xây dựng hệ thống Client–Server Java đáp ứng các chức năng của đề BTL LTNC 2026:
quản lý tài khoản theo vai trò, quản lý sản phẩm, đặt giá hợp lệ, realtime và kết thúc phiên.
Giao diện dùng JavaFX/FXML và xử lý lỗi kết nối, dữ liệu. Phạm vi theo các yêu cầu bắt buộc
trong Hướng dẫn BTL LTNC-2026. Kế hoạch phân công năm vai công việc nằm ở `weekly.md`, phần thực hiện có bằng chứng
riêng trong nhật ký thay đổi.

## 2. Phân tích nghiệp vụ

Người dùng đăng ký BIDDER/SELLER và đăng nhập trước khi truy cập phiên. Seller lập một
phiên nháp với sản phẩm và thời gian, có thể sửa nháp và xóa phiên chưa có giá khi còn
nháp hoặc đã hủy. Server tự
chuyển OPEN sang RUNNING theo lịch; seller cũng có thể mở ngay. Bidder gửi số tiền nguyên
lớn hơn giá hiện tại. Server kiểm tra vai trò, thời gian, giá, ghi người dẫn đầu và thông báo.
Khi hết hạn server chuyển FINISHED; người dẫn đầu là người thắng duy nhất. Nếu không có
bid, không có người thắng và không được PAID. Thanh toán trong phạm vi BTL là xác nhận
mô phỏng, chỉ người thắng có quyền. Admin giám sát tài khoản và quản lý các phiên. Khi
phiên đã có giá, chỉ Admin được hủy trước khi thanh toán.

## 3. Thiết kế

Các sơ đồ và quyết định chi tiết ở [architecture.md](architecture.md), giao thức ở
[protocol.md](protocol.md). `server` chứa domain, Socket controller, service và DAO. `client` có FXML/CSS, controller,
view model và kết nối riêng. Client không có server/domain dependency ở runtime. Bộ test GUI
với server thật nằm trong test scope của `client`, không vào gói chạy của client.
Mỗi trách nhiệm nằm ở một tầng, không dùng dữ liệu mock trong màn hình vận hành.

AuctionManager Singleton sở hữu vòng đời quy trình server. Mỗi ItemType override một
factory method để tạo đúng loại sản phẩm. Observer chuyển giá đã chấp nhận từ domain
qua Socket tới bảng giá trên JavaFX.

## 4. Thuật toán và tính đúng

Điều kiện chấp nhận bid: đang RUNNING, startTime ≤ now < endTime, bidder hợp lệ, amount >
currentPrice và amount ≤10^15. Các điều kiện được kiểm tra khi giữ khóa. Cập nhật giá,
người dẫn đầu và lịch sử diễn ra trước khi nhả khóa. Do đó history tăng nghiêm ngặt, không
rollback và không có hai người dẫn đầu cho cùng một snapshot.

Mỗi lượt giá hợp lệ tạo một giao dịch và giữ nguyên hạn đóng phiên. Server lưu thay
đổi trước khi phát cập nhật tới client. Bảng lịch sử trên GUI dùng dữ liệu từ sự kiện
của server.

## 5. Lưu trữ và độ tin cậy

PBKDF2WithHmacSHA256 120.000 vòng, salt 16 byte ngẫu nhiên cho mỗi tài khoản. So sánh hash
constant-time. Đăng ký trùng tên dùng putIfAbsent. Public DTO chỉ có id, username, role.
Session gắn với socket; không tin userId/role gửi trong một command nghiệp vụ.

DAO serialize snapshot local, listener transient được gắn lại sau load. Object filter và
schema kiểm tra định dạng; file lỗi không tự reset. File mới được fsync rồi move nguyên tử;
process lock ngăn ghi chồng từ hai server. Khi IO ghi thất bại, service ngừng nhận command
đến khi restart. Việc mất kết nối ngay sau khi commit vẫn có thể khiến client chưa biết kết
quả, nên GUI không tự retry các mutation.

## 6. Kiểm thử

JUnit kiểm thử mô hình phiên, cạnh tranh 100 luồng lặp 100 lần, lỗi observer, vòng đời
và giá ở đúng hạn. Test socket tạo server ở cổng
ngẫu nhiên và kho tạm, kiểm tra phân quyền, dữ liệu sai, nhiều bidder, sự kiện và khôi phục.
Test JavaFX tải FXML thật, đăng nhập qua Socket, bấm bid và nhận cập nhật từ client thứ hai.
Smoke script chạy file JAR ở tiến trình riêng rồi restart để xác minh gói chạy.

Checkstyle Google là bước bắt buộc trong verify. JaCoCo đo cả server/client qua test riêng và test tích hợp; script
kiểm tổng coverage dòng ≥60%. GitHub Actions đã cấu hình chạy cùng lệnh, chưa khẳng định
đã chạy trên GitHub khi chưa push. Số liệu hiện tại ghi ở [requirements.md](requirements.md).

Đợt kiểm tra bổ sung có test tái hiện lỗi ở bản trước, không chỉ chạy lại test đã có.
Các lỗi mạng, lưu trữ và GUI cùng cách sửa được ghi ở [audit.md](audit.md). Thử tải dùng
JAR thật và kho tạm; số liệu kiểm thử hiện tại xem requirements.md/validation.json.

## 7. Đánh giá và giới hạn

Các chức năng nghiệp vụ có thể kiểm chứng trong một môi trường chạy được, cùng hướng dẫn
và kịch bản demo. Không thể suy ra điểm chấm tuyệt đối từ việc test xanh: người nộp cần
hiểu code và trình bày. File snapshot ghi đồng bộ là lựa chọn đơn giản, chưa tối ưu cho tải
lớn. TCP không mã hóa nên demo ở localhost hoặc mạng tin cậy. Thanh toán thật, database
server, triển khai Internet và installers không thuộc bản triển khai hiện tại.

## 8. Giao diện và đánh giá thiết kế

Giao diện dùng CSS tập trung với bảng màu chỉnh ở đầu file; FXML mở được để thiết kế
bằng Scene Builder. Login có hướng dẫn server/tài khoản demo và đăng ký. Nút thao tác
được bật/tắt theo trạng thái, lỗi kết nối giải thích bước cần kiểm tra.

Smoke JAR kiểm năm kết nối và khởi động lại server. Test tích hợp render JavaFX bằng
Monocle, gồm cửa sổ 1000×700. Kết quả hiện tại nằm ở [requirements.md](requirements.md).

Bảng đối chiếu kỹ thuật theo năm vai A–E nằm ở [requirements.md](requirements.md).
Trách nhiệm về sơ đồ lớp gốc, đóng góp, review PR và vấn đáp thuộc về nhóm. Xem thêm
[hướng dẫn chạy nhiều terminal](local-demo.md).
