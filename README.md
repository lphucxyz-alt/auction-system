# Hệ thống đấu giá trực tuyến

Bài tập lớn Lập trình nâng cao, dùng Java 21, JavaFX/FXML, Socket JSON và Maven.
Người bán tạo phiên, người mua đặt giá, server cập nhật các client và tự đóng phiên khi hết giờ.

## Cấu trúc

```text
server/              Ứng dụng server độc lập: domain, service, socket, DAO, test
client/              Ứng dụng JavaFX độc lập: giao diện, controller, kết nối, test
integration-tests/   Kiểm thử client và server cùng nhau, báo cáo coverage tổng
scripts/             Lệnh kiểm tra, demo và xuất tài liệu
docs/               Giao thức, kiến trúc, kế hoạch tuần, báo cáo
pom.xml              Gom các project để kiểm tra toàn bộ, không bắt buộc khi chạy riêng
```

Hai ứng dụng có POM riêng, **không phụ thuộc POM gốc hoặc artifact của nhau**. Có thể chép
riêng `client/` hoặc `server/` sang thư mục/máy khác để build. Client không có luật đấu giá,
entity người dùng có password hash, DAO hoặc file lưu của server. Hai bên chỉ thống nhất
[dạng JSON gửi qua Socket](docs/protocol.md).

Phần domain cũ giữ package `com.example.auction.common.model` để đọc snapshot trước đây,
nhưng source của nó hiện chỉ nằm trong `server/`. Không còn module Maven `common` dùng chung.

## Chạy trên máy

Cần **JDK 21** (có cả `java` và `javac`) và **Maven 3.8.7+**. Linux chạy GUI cần desktop/GTK3.

Tại thư mục gốc repository, mở terminal thứ nhất:

```bash
mvn -f server/pom.xml clean verify
java -jar server/target/server-1.0-SNAPSHOT-all.jar --demo
```

Giữ server chạy, mở terminal thứ hai cũng ở thư mục gốc:

```bash
mvn -f client/pom.xml javafx:run
```

Lệnh client tự biên dịch mã client, không cần `mvn install` server trước.
Để chạy hai người mua cùng lúc, mở thêm terminal và chạy lại lệnh client.
Nhập server `127.0.0.1`, cổng `5000` trên màn hình đăng nhập.

Nếu chép từng ứng dụng ra riêng, xem [server/README.md](server/README.md) và
[client/README.md](client/README.md). Các script tiện dụng trên Linux/macOS:

```bash
bash scripts/server.sh --demo
bash scripts/client.sh
```

### Tài khoản demo

`--demo` chỉ tạo dữ liệu nếu kho tài khoản đang trống. Không đổi mật khẩu tài khoản đã có.

| Tên | Mật khẩu demo | Vai trò |
|---|---|---|
| seller | DemoSeller123! | Người bán |
| bidder1 | DemoBidder123! | Người mua |
| bidder2 | DemoBidder123! | Người mua |
| admin | DemoAdmin123! | Quản trị |

Nếu đã dùng kho cũ và không nhớ mật khẩu, có thể thử với kho khác mà vẫn giữ dữ liệu cũ:

```bash
java -Dauction.data=data/demo-moi -jar server/target/server-1.0-SNAPSHOT-all.jar --demo
```

Chế độ thông thường không có mật khẩu mặc định. Đăng ký trên GUI chỉ cấp BIDDER/SELLER.
Để tạo admin đầu tiên, đặt `AUCTION_ADMIN_USER` và `AUCTION_ADMIN_PASSWORD` trong môi trường
của server. Username 2–32 ký tự chữ/số/`_`, mật khẩu 6–128 ký tự.

## Cách sử dụng

1. Seller tạo sản phẩm: tên, mô tả, nhóm sản phẩm, giá khởi điểm, lịch mở và thời lượng.
2. Chỉ sửa/xóa khi còn nháp; bấm **Bắt đầu** để mở ngay hoặc đợi lịch server tự mở.
3. Hai bidder chọn cùng phiên, đặt số nguyên VND lớn hơn giá hiện tại.
4. Giá, người dẫn đầu, lịch sử và biểu đồ cập nhật qua sự kiện từ server.
5. Hết giờ, người dẫn đầu là người thắng. Người thắng có thể xác nhận **thanh toán mô phỏng**.

Auto-bid nhận trần và bước giá; cùng trần thì người đăng ký sớm hơn được ưu tiên.
Anti-sniping kéo thời gian còn lại lên 30 giây nếu có bid trong 10 giây cuối.
Tắt anti-sniping khi cần demo phiên đóng nhanh. Số tiền tối đa là 10^15 VND.

Admin xem danh sách tài khoản và quản lý phiên của mọi người bán. Phiên đã có bid được
hủy nhưng vẫn giữ lịch sử; không xóa lịch sử để thay kết quả. Không có bid thì không có
người thắng và không được xác nhận PAID.

## Kiểm tra

```bash
mvn clean verify                          # server, client và integration-tests
python3 scripts/check_architecture.py      # không import/phụ thuộc nhầm ứng dụng
python3 scripts/check_coverage.py          # coverage tổng >= 60%, gồm cả GUI
bash scripts/check_independent_builds.sh   # chép riêng từng ứng dụng ra ngoài để build/test
python3 scripts/smoke.py                   # JAR server thật, nhiều socket, restart
```

Trong cloud hiện tại, chạy `source scripts/env.sh` trước các lệnh Maven/Python cần Java;
hoặc `bash scripts/build.sh`. Script không thay toolchain trên máy nếu không có đường dẫn cloud.

Test riêng từng ứng dụng: `mvn -f server/pom.xml clean verify` hoặc
`mvn -f client/pom.xml clean verify`. Test client riêng dùng socket thử nghiệm.
Test GUI nói chuyện với server thật ở `integration-tests/`, không nằm trong client.
Báo cáo tổng ở `integration-tests/target/site/jacoco-aggregate/index.html`;
ảnh từ test GUI ở `integration-tests/target/screenshots/`.

GitHub Actions kiểm tra build độc lập và tích hợp; trạng thái lần chạy thực tế xem ở tab Actions.

## Cấu hình và lưu dữ liệu

| Tham số Java, đặt trước `-jar` | Mặc định |
|---|---|
| `-Dauction.host=...` | 127.0.0.1 |
| `-Dauction.port=...` | 5000 |
| `-Dauction.data=...` | data, tương đối với thư mục chạy lệnh |

Script server cũng nhận `AUCTION_HOST`, `AUCTION_PORT`, `AUCTION_DATA`.
Chỉ server đọc/ghi `auction-state.bin`: ghi file tạm, fsync rồi thay thế nguyên tử.
Một thư mục dữ liệu chỉ dành cho một server. Lỗi file không bị tự ghi đè/reset.
Dừng server trước khi sao lưu hoặc đổi kho. `data/`, `target/` đã được Git bỏ qua.

Bản này dùng TCP không mã hóa để demo localhost/mạng tin cậy, chưa dùng cổng thanh toán thật.
Đổi tên các lớp serialization cần có cách chuyển đổi dữ liệu; không tự đổi package domain
rồi xóa snapshot của người dùng. GUI hiển thị 100 lượt gần nhất, có nút tải toàn bộ lịch sử.

## Phân công và tài liệu

- [Tuần nào làm file nào, phần code nào?](docs/weekly-files.md)
- [Kế hoạch tuần 6–15 cho bốn mảng](docs/weekly.md)
- [Các thay đổi thực tế và nhánh](docs/development-log.md)
- [Kiến trúc và thuật toán](docs/architecture.md)
- [Giao thức Socket](docs/protocol.md)
- [Đối chiếu yêu cầu và kết quả kiểm thử](docs/requirements.md)
- [Kịch bản demo](docs/demo.md)
- [Báo cáo](docs/report.md), [PDF](docs/report.pdf), [Word](docs/report.docx)
- [Nội dung trình bày](docs/slides.md), [PowerPoint](docs/presentation.pptx)

Kế hoạch tuần là công việc dự kiến cho nhóm, không thay thế bằng chứng đóng góp thực tế.
Lịch sử Git giữ ngày commit thực. Người trình bày cần đọc code và tập demo trên máy mình.
