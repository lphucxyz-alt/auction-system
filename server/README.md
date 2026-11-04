# Server đấu giá

Server quản lý người dùng, sản phẩm, phiên, giá và file dữ liệu. Client chỉ gửi JSON qua TCP.

Mã domain được chuyển từ `common/` về `server/src/`. Package Java
`com.example.auction.common.model` và `common.exception` được giữ lại để các file
`auction-state.bin` đã tạo ở bản trước vẫn đọc được. Chữ `common` trong tên package là
lịch sử đặt tên; các lớp này không được đóng gói cùng client.

Các nhóm lớp:

- `common/model`: Entity, User, Item, Auction, BidTransaction và các lớp con.
- `server`: dịch vụ tài khoản, xử lý lệnh, vòng đời phiên.
- `server/network`: nhận/gửi JSON, phiên đăng nhập trên mỗi socket.
- `server/dao`: đọc/ghi snapshot, khóa thư mục dữ liệu.

Có thể chép riêng thư mục `server/` sang máy khác và chạy:

```bash
mvn clean verify
java -jar target/server-1.0-SNAPSHOT-all.jar --demo
```

Không cần client, JavaFX, POM gốc hoặc module common. JAR `-all` chứa Gson để chạy trực tiếp;
JAR không có hậu tố là thư viện server cho bộ test tích hợp.

`auction.data` mặc định là `data/` tương đối với thư mục chạy lệnh. Khi chạy bằng
`bash scripts/server.sh --demo` ở gốc repository, dữ liệu cũ ở `data/` được dùng tiếp.
Để chỉ rõ một kho đã có, truyền `-Dauction.data=/đường/dẫn/data` trước `-jar`.
