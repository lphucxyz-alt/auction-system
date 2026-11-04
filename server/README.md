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

Hướng dẫn build độc lập và chạy sẽ nằm ở phần cài đặt của repository khi hoàn tất việc tách module.
