# Client JavaFX

Ứng dụng này chỉ cần JDK 21 và Maven. Có thể chép riêng thư mục `client/` sang máy khác:
không cần `server/`, POM gốc hoặc thư viện nội bộ đã cài vào Maven local.

```bash
mvn clean verify
mvn javafx:run
```

Nhập host và cổng server trên màn hình đăng nhập. Mặc định là `127.0.0.1:5000`.
Nếu server ở máy khác, nhập IP của máy đó; server phải được cấu hình lắng nghe trên mạng.

- FXML/CSS: giao diện đăng nhập, danh sách/chi tiết, form sản phẩm.
- Controller: nhận thao tác và hiển thị kết quả.
- `AuctionRow`: dữ liệu hiển thị nhận qua JSON, không phải entity của server.
- `AuctionConnection`: gửi yêu cầu, ghép requestId, nhận sự kiện trên luồng riêng.
- `client/protocol`: cách đọc/ghi JSON theo `docs/protocol.md` trong repo.

Test của client dùng socket thử nghiệm, không import code server. Test mở GUI và server
thật cùng lúc đặt ở `integration-tests/` và chạy khi build toàn repo.
Client không giữ mật khẩu hash, luật đấu giá, dữ liệu serialization hay DAO.

Ba FXML dùng chung `auction.css` qua `stylesheets="@auction.css"`, nên Scene Builder
đọc được CSS cạnh file. Đổi bảng màu ở đầu CSS, bố cục ở FXML; giữ fx:id/onAction.
Chạy lại `mvn javafx:run` sau khi sửa để copy resource mới. Tài khoản demo cần server
đã seed; nút dùng thử chỉ điền tên/mật khẩu. Nếu có cả repo, xem
[hướng dẫn nhiều terminal](../docs/local-demo.md) và [review](../docs/review-and-references.md).
