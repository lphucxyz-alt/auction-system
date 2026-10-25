# Giao thức Client - Server

- Kênh: TCP Socket, mỗi thông điệp là một dòng JSON kết thúc bằng `\n`.
- Trường: `type`, `requestId` (Server chép lại vào phản hồi), `payload`.
- Sự kiện Server tự đẩy (`NEW_BID`, `AUCTION_FINISHED`) không có `requestId`.
- Mọi thay đổi giao thức phải sửa file này trước khi sửa code.

| type | Hướng | payload |
|---|---|---|
| REGISTER | C->S | username, password, role |
| LOGIN | C->S | username, password |
| LIST_AUCTIONS | C->S | (rỗng) |
| PLACE_BID | C->S | auctionId, amount |
| SUBSCRIBE | C->S | auctionId |
| RESULT | S->C | tùy yêu cầu |
| ERROR | S->C | code, message |
| NEW_BID | S->C | auctionId, bidderId, amount, time |
| AUCTION_FINISHED | S->C | auctionId, winnerId, finalPrice |
