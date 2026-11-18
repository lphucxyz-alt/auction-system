# Server đấu giá

Server quản lý người dùng, sản phẩm, phiên, giá và file dữ liệu. Client chỉ gửi JSON qua TCP.

Domain và exception nằm trong module Maven `common`, giữ nguyên package Java
`com.example.auction.common.model` và `common.exception` để các file `auction-state.bin`
đã tạo ở bản trước vẫn đọc được. Server phụ thuộc module `common`; client không dùng nó
trong mã chạy.

Các nhóm lớp:

- `common`: Entity, User, Item, Auction, BidTransaction, exception và các lớp con.
- `server`: dịch vụ tài khoản, xử lý lệnh, vòng đời phiên.
- `server/network`: nhận/gửi JSON, phiên đăng nhập trên mỗi socket.
- `server/dao`: đọc/ghi snapshot, khóa thư mục dữ liệu.

Từ thư mục gốc repo, build server cùng module cần thiết:

```bash
mvn -pl server -am clean verify
java -jar server/target/server-1.0-SNAPSHOT-all.jar --demo
```

Không cần chạy client. JAR `-all` chứa common và Gson để chạy trực tiếp;
JAR không có hậu tố là thư viện server cho bộ test tích hợp.

`auction.data` mặc định là `data/` tương đối với thư mục chạy lệnh. Khi chạy bằng
`bash scripts/server.sh --demo` ở gốc repository, dữ liệu cũ ở `data/` được dùng tiếp.
Để chỉ rõ một kho đã có, truyền `-Dauction.data=/đường/dẫn/data` trước `-jar`.
