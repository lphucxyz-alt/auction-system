# GAVEL — Hệ thống đấu giá trực tuyến

Bài tập lớn Lập trình nâng cao: ứng dụng **Java 21 + JavaFX/FXML**, server **TCP Socket JSON**,
MVC, lưu file có serialization, kiểm thử JUnit và GitHub Actions.

## Chạy nhanh

Yêu cầu **JDK 21** (có `javac`, không chỉ JRE), **Maven 3.8.7+**. Linux desktop cần GTK3;
Windows/macOS dùng JavaFX native dependency Maven tự chọn. Chạy từ thư mục gốc repository.

```bash
mvn clean install
java -jar server/target/server-1.0-SNAPSHOT.jar --demo
```

Mở terminal khác cho mỗi client:

```bash
mvn -f client/pom.xml javafx:run
```

Trên môi trường cloud đã chuẩn bị toolchain, hoặc Linux/macOS:

```bash
bash scripts/build.sh
bash scripts/server.sh --demo
bash scripts/client.sh
```

`build.sh` chạy cả test, coverage, Checkstyle. `client.sh` cài các module vào Maven local trước
khi mở GUI. Cần desktop/display khi chạy GUI thông thường; CI dùng Monocle headless.

### Tài khoản demo

Chỉ được tạo khi truyền `--demo` **và kho người dùng đang trống**. Chế độ thông thường không có mật khẩu mặc định.

| Tên | Mật khẩu demo | Vai trò |
|---|---|---|
| admin | DemoAdmin123! | ADMIN |
| seller | DemoSeller123! | SELLER |
| bidder1 | DemoBidder123! | BIDDER |
| bidder2 | DemoBidder123! | BIDDER |

Dữ liệu demo nằm cùng kho dữ liệu đã chọn. Dùng thư mục riêng để thử lại:

```bash
java -Dauction.data=data/demo-2 -jar server/target/server-1.0-SNAPSHOT.jar --demo
```

Đăng ký công khai chỉ cấp BIDDER hoặc SELLER. Để tạo admin trong kho mới không dùng demo,
đặt `AUCTION_ADMIN_USER` và `AUCTION_ADMIN_PASSWORD` trong môi trường của server; không
đưa giá trị thật vào Git. Server chỉ bootstrap nếu chưa có admin. Username 2–32 ký tự
ASCII chữ/số/`_`; mật khẩu 6–128 ký tự.

## Chức năng

- Đăng ký, đăng nhập, đăng xuất; kiểm tra quyền ở server theo danh tính của socket.
- Seller tạo/sửa/xóa phiên nháp, mở ngay hoặc đặt lịch tự mở; Admin quản lý tất cả phiên và xem tài khoản.
- Điện tử / nghệ thuật / xe: tên, mô tả, thông số riêng, giá khởi điểm, lịch bắt đầu/kết thúc.
- Đặt giá bằng số nguyên VND, cập nhật người dẫn đầu, khóa từng phiên, lịch sử chỉ tăng.
- Server đẩy giá và trạng thái qua Socket; GUI cập nhật bằng `Platform.runLater`, bỏ snapshot cũ theo version.
- Tự đóng đúng hạn, chọn người thắng, hủy và xác nhận **thanh toán mô phỏng** bởi người thắng.
- **Auto-bid:** trần riêng, bước giá, PriorityQueue; cùng trần ưu tiên đăng ký trước; không vượt trần.
- **Anti-sniping:** lượt giá trong 10 giây cuối kéo thời gian còn lại lên 30 giây.
- **LineChart realtime**, lịch sử bid, tìm kiếm/lọc trạng thái/phiên của mình.
- Khôi phục người dùng, hash mật khẩu, phiên, lịch sử và auto-bid sau khi restart; tự đóng phiên đã hết hạn.

Phiên đang RUNNING không được sửa/xóa. Hủy giữ lại lịch sử; chỉ xóa OPEN/CANCELED chưa có bid.
Phiên FINISHED không có bid không được chuyển PAID. Số tiền tối đa 10^15 VND.

## Kiểm thử và chất lượng

```bash
mvn clean verify                 # JUnit + Checkstyle Google + JaCoCo
python3 scripts/check_coverage.py # kiểm tra coverage dòng toàn bộ >= 60%
python3 scripts/smoke.py          # JAR thật, 3 socket, kết thúc + khôi phục sau restart
```

Cloud: `source scripts/env.sh` trước các lệnh Java/Python smoke; `bash scripts/build.sh`
tự thêm Maven settings/proxy. Các shell khác dùng JDK/Maven đã cài trên máy.

Báo cáo: `common/target/site/jacoco/index.html`, `server/target/site/jacoco/index.html`,
`client/target/site/jacoco/index.html`. Test GUI chạy đăng nhập, bid thật, nhận bid của client
khác và kiểm tra biểu đồ, không dùng dữ liệu giả. Ảnh test ở `client/target/screenshots/`.
Coverage gồm cả GUI; không loại lớp nghiệp vụ ra để tăng số đo.

## Cấu hình và dữ liệu

| Tham số server (`-D` phải đứng trước `-jar`) | Mặc định |
|---|---|
| auction.host | 127.0.0.1 |
| auction.port | 5000 (0 để hệ điều hành chọn cổng) |
| auction.data | data |

GUI cho nhập host/port ở màn hình đăng nhập. Có thể truyền `-Dauction.host=...`
`-Dauction.port=...` cho `mvn -f client/pom.xml javafx:run`. Script dùng biến
`AUCTION_HOST`, `AUCTION_PORT`, `AUCTION_DATA` tương ứng.

Chỉ server truy cập `data/auction-state.bin`. Mỗi thay đổi được serialize vào file tạm,
fsync rồi thay thế nguyên tử; `server.lock` ngăn hai tiến trình dùng chung kho. File lỗi
không bị tự ghi đè; lỗi ghi khiến server ngừng nhận lệnh nghiệp vụ đến khi restart.
Sao lưu kho khi đã dừng server. Không tải file serialization không đáng tin cậy vào kho.
Thư mục `data/` đã được bỏ qua bởi Git.

Socket hiện dùng TCP không mã hóa, dành cho demo localhost/mạng tin cậy. Triển khai Internet
cần bổ sung TLS và vận hành phù hợp. Hệ thống không kết nối cổng thanh toán/ngân hàng thật.
Realtime hiển thị 100 lượt gần nhất; nút tải toàn bộ lịch sử đọc theo trang từ server.
Danh sách phiên cũng đọc theo trang để không phình thông điệp.

## Tài liệu bàn giao

- [Đối chiếu yêu cầu và bằng chứng](docs/requirements.md)
- [Kiến trúc, OOP và thuật toán](docs/architecture.md)
- [Giao thức JSON](docs/protocol.md)
- [Kịch bản demo và cách giải thích code](docs/demo.md)
- [Nội dung trình bày](docs/slides.md)
- [Báo cáo kỹ thuật](docs/report.md) — [PDF](docs/report.pdf), [Word](docs/report.docx)
- [Slide PowerPoint](docs/presentation.pptx) (có thể chỉnh sửa)

Bản triển khai giữ namespace `com.example.auction` của repository dự phòng. Hai file code
đính kèm dùng namespace khác và còn mock/echo; được dùng để đối chiếu hướng thiết kế,
không nhập `.git`, `target/` hoặc dữ liệu build cũ từ ZIP vào repository này.

Xuất lại báo cáo/slide: `python3 scripts/export_docs.py` với các gói Python tùy chọn
`reportlab`, `python-pptx`, `python-docx` và font Unicode theo hướng dẫn trong script.
