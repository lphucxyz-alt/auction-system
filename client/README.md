# Client JavaFX

Ứng dụng client cần JDK 21, Maven và parent POM ở thư mục gốc repo. Khi chạy, client là
tiến trình riêng kết nối server qua TCP/JSON; domain và server không nằm trong runtime client.

```bash
mvn -pl client -am clean verify
bash scripts/client.sh
```

Nhập host và cổng server trên màn hình đăng nhập. Mặc định là `127.0.0.1:5000`.
Nếu server ở máy khác, nhập IP của máy đó; server phải được cấu hình lắng nghe trên mạng.

- FXML/CSS: giao diện đăng nhập, danh sách/chi tiết, form sản phẩm.
- Controller: nhận thao tác và hiển thị kết quả.
- `AuctionRow`: dữ liệu hiển thị nhận qua JSON, không phải entity của server.
- `AuctionConnection`: gửi yêu cầu, ghép requestId, nhận sự kiện trên luồng riêng.
- `client/protocol`: cách đọc/ghi JSON theo `docs/protocol.md` trong repo.

Test đơn vị của client dùng socket thử nghiệm. Test mở GUI và server thật cùng lúc nằm
trong `client/src/test` và chạy khi build module client.
Client không giữ mật khẩu hash, luật đấu giá, dữ liệu serialization hay DAO.

Ba FXML dùng chung `auction.css` qua `stylesheets="@auction.css"`, nên Scene Builder
đọc được CSS cạnh file. Đổi bảng màu ở đầu CSS, bố cục ở FXML; giữ fx:id/onAction.
Chạy lại `bash scripts/client.sh` sau khi sửa để copy resource mới. Tài khoản demo cần server
đã seed; nút dùng thử chỉ điền tên/mật khẩu. Nếu có cả repo, xem
[hướng dẫn nhiều terminal](../docs/local-demo.md) và [review](../docs/review-and-references.md).
