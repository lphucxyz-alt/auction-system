# Chạy trên máy cá nhân với 5–6 terminal

Máy được kiểm tra: Linux, JDK 21, Maven 3.8.7. Thư mục đang sửa là
`/home/phuc-quang/IdeaProjects/auction-system`. Một bản clone cũ ở
`/home/phuc-quang/auction-system` có FXML/CSS chưa commit; bản đó được giữ nguyên.
Hãy chạy tất cả lệnh từ cùng một bản clone để tránh nhìn giao diện cũ.

## Chuẩn bị một lần

```bash
cd ~/IdeaProjects/auction-system
java -version
javac -version
mvn -version
mvn -B -ntp clean verify
```

Giữ nguyên source và không chạy `clean` trong lúc các client đang hoạt động.
Mỗi lệnh client dưới đây tạo một tiến trình JavaFX/cửa sổ riêng; terminal cần giữ mở.

## Từng terminal

Mỗi terminal trước tiên chạy `cd ~/IdeaProjects/auction-system`.

| Terminal | Lệnh | Sau khi mở GUI |
|---|---|---|
| 1 — Server | `bash scripts/demo-server.sh` | Chờ `Auction server ready on 127.0.0.1:5000` |
| 2 — Người bán | `bash scripts/client.sh seller` | Chọn **Người bán**, bấm **Đăng nhập** |
| 3 — Người mua 1 | `bash scripts/client.sh bidder1` | Chọn **Người mua 1**, bấm **Đăng nhập** |
| 4 — Người mua 2 | `bash scripts/client.sh bidder2` | Chọn **Người mua 2**, bấm **Đăng nhập** |
| 5 — Quản trị | `bash scripts/client.sh admin` | Chọn **Quản trị**, bấm **Đăng nhập** |
| 6 — Người mua mới (tùy chọn) | `bash scripts/client.sh bidder3` | Nhập mật khẩu mới, mở **Đăng ký**, chọn Người mua, tạo tài khoản; nhập lại mật khẩu để đăng nhập |

Tham số sau `client.sh` chỉ điền tên tài khoản, không đăng nhập tự động.
Các nút dùng thử điền cả tên và mật khẩu demo; không tạo hay bỏ qua xác thực server.

| Username | Mật khẩu trên kho demo mới | Vai trò |
|---|---|---|
| `seller` | `DemoSeller123!` | Người bán |
| `bidder1` | `DemoBidder123!` | Người mua |
| `bidder2` | `DemoBidder123!` | Người mua |
| `admin` | `DemoAdmin123!` | Quản trị |

Server demo dùng kho riêng `data/local-demo`, giữ dữ liệu qua mỗi lần chạy.
`--demo` chỉ thêm tài khoản khi kho tài khoản trống. Nếu kho có tài khoản cũ,
mật khẩu bảng trên không được áp dụng lại. Để thử kho mới mà giữ nguyên kho cũ:

```bash
AUCTION_DATA=data/demo-lan-2 bash scripts/demo-server.sh
```

Chỉ chạy một server trên một cổng; đổi kho không cho phép mở hai server chung cổng.
Nếu cần cổng khác, dùng `AUCTION_PORT=5001` ở cả lệnh server và từng lệnh client.
Các lệnh trên chạy server/client của repo phụ, không dùng server repo chính.

## Demo nhanh khoảng 3 phút

1. Seller bấm **+ Tạo phiên**. Tên: `Camera demo`; giá khởi điểm `1000`;
   mở sau `300` giây; thời lượng `90` giây; bỏ chọn gia hạn. Lưu.
2. Seller chọn Camera vừa tạo, bấm **Bắt đầu**. Trạng thái chuyển **Đang đấu**.
3. Hai bidder chọn cùng Camera. Bidder1 nhập `1100` rồi **Đặt giá**;
   bidder2 nhập `1200`. Hai bên thấy giá/lịch sử/biểu đồ cập nhật mà không bấm tải lại.
4. Thử `1100` lần nữa: server từ chối, giá không lùi.
5. Chờ hết giờ; người đặt `1200` thắng nếu không có ai đặt cao hơn. Chỉ người thắng
   có nút xác nhận thanh toán mô phỏng.
6. Admin xem tài khoản hoặc quản lý phiên. Đóng client bằng nút X; dừng server bằng
   Ctrl+C ở terminal 1. Mở lại cùng kho để kiểm tra kết quả đã lưu.

Ba phiên mẫu ban đầu có thể đã hết hạn ở lần demo trước. Khi đó tạo phiên mới;
không xóa kho chỉ để có lại phiên đang chạy. Auto-bid và gia hạn xem [demo.md](demo.md).

## Khi không đăng nhập được

- **Không kết nối được server:** kiểm terminal 1 có dòng ready, địa chỉ `127.0.0.1`,
  cổng `5000`. `mvn javafx:run` chỉ chạy GUI, không tự khởi động server.
- **Sai tài khoản/mật khẩu:** chọn đúng demo và đúng kho. Tài khoản `alice/123456`
  của prototype repo chính không phải tài khoản repo phụ.
- **Address already in use:** dừng server cũ ở terminal của nó, hoặc đổi cổng cả hai bên.
  Không tắt ngẫu nhiên các tiến trình Java khác trên máy.
- **Kho đang được dùng:** một thư mục dữ liệu chỉ cho một server; dừng server cũ trước.
- **Giao diện vẫn cũ:** kiểm `pwd` là bản trong `IdeaProjects`, đóng client rồi chạy lại.
- **Bấm Enter:** đăng nhập bằng nút mặc định; đăng ký thành công cần nhập lại mật khẩu.
- **Không thấy giá thay đổi:** các bidder phải chọn cùng một phiên đang ở trạng thái
  Đang đấu; số nhập không có dấu chấm/phẩy, phải lớn hơn giá hiện tại.

## CSS và Scene Builder

File chỉnh giao diện:

```text
client/src/main/resources/com/example/auction/client/
  auction.css     màu, font, nút, bảng, badge trạng thái, biểu đồ
  login.fxml      bố cục đăng nhập/đăng ký/dùng thử
  main.fxml       bố cục danh sách, chi tiết và đặt giá
  seller.fxml     bố cục form tạo/sửa phiên
```

Ví dụ đổi màu thương hiệu ở đầu `auction.css`:

```css
.root {
  -auction-accent: #256b55;
  -auction-accent-hover: #1d5544;
  -auction-soft: #eef8f2;
}
```

Sửa các dòng đã có trong `.root`, không cần tạo thêm block trùng. Các màu này là
looked-up colors của JavaFX; không dùng `var(--color)`, flexbox hay CSS trình duyệt.
Đóng và chạy lại client để Maven chép resource mới vào `target/classes`.

Mở trực tiếp từng `.fxml` bằng [Gluon Scene Builder](https://gluonhq.com/products/scene-builder/).
Trong IntelliJ có thể chọn **Open in Scene Builder** sau khi cấu hình đường dẫn ứng dụng.
Ba FXML khai báo `stylesheets="@auction.css"` nên preview đọc cùng file CSS cạnh chúng.
Preview chỉ có bố cục tĩnh; dữ liệu, vai trò, sự kiện cần chạy app thật.
Đổi màu ở CSS, đổi bố cục ở FXML. Giữ `fx:controller`, `fx:id` và `onAction` để không làm
đứt liên kết với Java. Không đổi sang custom control nếu chỉ cần nút/bảng có sẵn.

Tài liệu gốc: [JavaFX 21 CSS](https://openjfx.io/javadoc/21/javafx.graphics/javafx/scene/doc-files/cssref.html)
và [Scene Builder](https://github.com/gluonhq/scenebuilder/wiki).
