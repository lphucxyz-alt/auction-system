# Giao thức Socket JSON v1

UTF-8, một JSON object mỗi dòng (tối đa 64 KiB), TCP mặc định `127.0.0.1:5000`.
Envelope: `{"type":"LOGIN","requestId":"uuid","payload":{"username":"…","password":"…"}}`.
Server trả `RESULT` / `ERROR` cùng requestId. `ERROR.payload` chứa `code`, `message`.
Mọi request sau đăng nhập dùng danh tính của socket, không tin `userId` do client gửi.
Không trả passwordHash, maxBid của người khác hoặc Java serialized object trên mạng.

| Yêu cầu | Payload | Quyền / kết quả |
|---|---|---|
| REGISTER | username, password, role BIDDER/SELLER | Công khai; trả user (không đăng nhập tự động) |
| LOGIN | username, password | Trả user, gắn session; chỉ một danh tính/socket |
| LOGOUT | {} | Xóa session, subscriptions |
| LIST_AUCTIONS | afterId (cursor, khuyến nghị), hoặc offset (mặc định 0) | Tối đa 50 auctions theo id tăng dần, nextAfterId (null nếu hết), nextOffset, total |
| GET_HISTORY | auctionId, offset (mặc định 0) | Đăng nhập; 100 lượt/trang, nextOffset, total |
| GET_AUCTION | auctionId | Đăng nhập; trả auction, 100 lượt history gần nhất |
| SUBSCRIBE | auctionId | Đăng nhập; nhận snapshot + sự kiện |
| UNSUBSCRIBE | auctionId | Xóa theo dõi |
| CREATE_AUCTION | name, description, category ELECTRONICS/ART/VEHICLE, startingPrice (số nguyên VND), extra1, extra2, startDelaySeconds, durationSeconds, antiSniping | Seller/Admin; trả auction |
| UPDATE_AUCTION | auctionId và cùng trường CREATE | Chủ sở hữu/Admin, chỉ OPEN chưa bắt đầu |
| DELETE_AUCTION | auctionId | Chủ sở hữu/Admin, chỉ OPEN/CANCELED chưa có bid |
| START_AUCTION | auctionId | Chủ sở hữu/Admin; bắt đầu ngay, giữ độ dài phiên |
| CANCEL_AUCTION | auctionId | Chủ sở hữu/Admin; OPEN/RUNNING/FINISHED |
| PLACE_BID | auctionId, amount | Bidder, số nguyên dương > giá hiện tại |
| AUTO_BID | auctionId, maxBid, increment | Bidder; tối đa 10^15 VND, ưu tiên đăng ký sớm khi cùng trần |
| REMOVE_AUTO_BID | auctionId | Bidder; bỏ cấu hình auto-bid của mình |
| PAY_AUCTION | auctionId | Người thắng FINISHED; thanh toán mô phỏng → PAID |
| LIST_USERS | {} | Admin; chỉ id, username, role |

Sự kiện đẩy không có requestId: `AUCTION_UPDATED` cho danh sách; `NEW_BID`,
`AUCTION_FINISHED` cho subscriber. Payload chứa `auction` snapshot có `version` tăng dần.
Client bỏ snapshot cũ, cập nhật JavaFX qua Platform.runLater; không polling mạng.
Thời gian là ISO-8601 UTC từ server; client hiển thị múi giờ máy người dùng.
Anti-sniping: bid trong 10 giây cuối kéo endTime tới ít nhất now + 30 giây.
Snapshot gồm lịch sử bid gần nhất và winnerId khi FINISHED/PAID. Phản hồi SUBSCRIBE là snapshot
cùng cơ chế version, tránh mất cập nhật giữa GET và SUBSCRIBE.
Yêu cầu tối đa 64 KiB; phản hồi tối đa 4 MiB. Snapshot chứa 100 lượt gần nhất và
`historyCount`; lịch sử đầy đủ vẫn lưu ở server và truy cập bằng GET_HISTORY.

Mã lỗi: BAD_REQUEST, AUTH_FAILED, NOT_LOGGED_IN, FORBIDDEN, NOT_FOUND,
INVALID_BID, AUCTION_CLOSED, CONFLICT, STORAGE_ERROR, INTERNAL.
Socket demo không mã hóa: chỉ dùng localhost/mạng tin cậy; triển khai Internet cần TLS.

## Ranh giới code

Client dùng `client.protocol.Message/JsonLines`; server dùng `server.protocol.Message/JsonLines`.
Mỗi bên có bản triển khai JSON nhỏ riêng để build độc lập. Không truyền entity Java hoặc
chia sẻ mật khẩu hash/DAO qua classpath. Khi đổi contract, cập nhật tài liệu này, codec/test
của từng bên và chạy test tích hợp để tránh hai bản hiểu khác nhau.

## Tải danh sách và xử lý phản hồi trễ

Client mới lấy trang kế bằng nextAfterId/afterId để xóa phần tử ở trang trước không làm
mất vị trí đọc. Offset còn để tương thích client cũ; nó không tạo snapshot nhất quán khi
các phần tử bị xóa trong lúc phân trang. Phiên mới trong lúc tải được nhận bằng sự kiện.
Khi tải toàn bộ history, client giữ version đã chọn và hủy áp dụng kết quả cũ nếu phiên đã
cập nhật. Các lệnh GUI được gửi theo thứ tự; không tự retry mutation khi timeout/mất mạng.
Timeout đóng kết nối để giải phóng cả write bị chặn; kết nối lại rồi kiểm tra trạng thái.
Hàng đợi gửi mỗi session tối đa 256 frame và 8 MiB, tính cả frame đang ghi. Hết ngân sách
hoặc phản hồi vượt 4 MiB thì ngắt client đó.
