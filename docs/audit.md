# Kiểm tra lỗi bổ sung

Mốc đối chiếu trước sửa: `c7dd5c8`. Phần này ghi lỗi đã kiểm tra và cách sửa, không coi số
lượng test hay coverage là bằng chứng rằng dự án không còn lỗi.

## Lỗi đã xác minh

| Trường hợp | Hậu quả trước sửa | Sửa và kiểm tra |
|---|---|---|
| Phản hồi ERROR thiếu code/message | Future bị xóa khỏi pending trước khi parse lỗi, không bao giờ hoàn tất | Kiểm phản hồi trước khi xóa pending; malformed response đóng transport và hoàn tất các future. Test `malformedErrorResponseCompletesRequestInsteadOfLeavingItPending` |
| Mất kết nối trước lúc controller gắn callback | GUI bỏ lỡ trạng thái mất mạng | Lưu nguyên nhân và báo lại một lần khi gắn listener. Test `disconnectListenerAttachedAfterFailureStillRuns` |
| Socket write bị peer chặn | request giữ cùng monitor với close; thao tác đóng/đăng xuất có thể treo | Khóa writer tách khỏi khóa trạng thái; đóng socket không chờ writer. Test dùng peer không đọc và bộ đệm nhỏ, tên `closeDoesNotWaitForBlockedWriter` |
| Observer ở tầng ứng dụng ném lỗi | Lệnh đã được lưu nhưng server trả BAD_REQUEST; người dùng có thể thử lại và tạo trùng | Cách ly lỗi từng observer khỏi kết quả commit. Test `brokenObserverDoesNotTurnCommittedCommandIntoAnError` |
| Xóa phiên ở trang trước khi đang tải trang sau | Offset dịch chuyển làm bỏ sót hoặc lặp phiên | Client mới dùng cursor afterId theo thứ tự id ổn định. Test 55 phiên, xóa một phiên rồi đọc tiếp còn đúng 5 phiên |
| Lọc bảng làm phiên đang chọn biến mất | Nút bid vẫn trỏ vào lựa chọn cũ | Xóa lựa chọn, chi tiết và subscription khi phiên không còn trong kết quả lọc. Kiểm tra bằng FXML/GUI thật |
| Sắp xếp cột tiền theo chuỗi | 10.000 có thể đứng trước 2.000 khi sắp tăng | So sánh giá trị số, giữ sort khi render lại. Regression so sánh 2.000 và 10.000 |
| Phản hồi lịch sử cũ đến sau bid mới | Bảng history mới bị dữ liệu cũ ghi đè | Kiểm version của phiên trong cả chuỗi GET_HISTORY; nếu đã đổi thì giữ snapshot mới và báo tải lại. Test gửi NEW_BID trước phản hồi history cũ |
| FileStore đã close vẫn đọc/ghi được | Đối tượng cũ có thể ghi sau khi một FileStore khác đã lấy khóa thư mục | load/save/close đồng bộ và kiểm quyền sở hữu khóa; close nhiều lần an toàn. Hai test vòng đời trong FileStoreTest |
| Object filter chưa giới hạn chiều dài mảng | Có thể cấp phát mảng lớn trước khi nhận ra snapshot sai schema | Chặn arrayLength lớn hơn 1.000.000 trong filter. Test xác nhận InvalidClassException ngay tại filter |

Các lỗi phản hồi mạng, observer, cursor, vòng đời DAO, filter mảng và lựa chọn GUI đã được
chạy test trên bản chưa sửa để thấy thất bại trước khi sửa. Với hai lỗi sort/history,
đã tạo bản sao source riêng, thay MainController bằng bản c7dd5c8 và xác nhận các regression
mới thất bại. Checkout đang sửa không bị ghi đè trong phép thử này.

## Rà soát bổ sung

- Hàng đợi gửi không chỉ giới hạn 256 thông điệp: giới hạn thêm 8 MiB dữ liệu đã mã hóa,
  tính cả frame đang ghi; mỗi phản hồi tối đa 4 MiB. Test dùng 50 sản phẩm có mô tả Unicode
  lớn, gửi 20 LIST_AUCTIONS từ client không đọc để kiểm ngắt kết nối trước khi dồn đủ phản hồi.
- Unsubscribe/subscribe từ GUI được gửi theo hàng đợi tuần tự, tránh đảo thứ tự khi đổi
  lựa chọn nhanh. Đăng xuất vẫn đóng được kết nối ngay cả khi write đang bị chặn.
- Khi xóa phiên hoặc đóng server, dọn subscription/observer tương ứng.
- Callback sự kiện phía client lỗi không làm mất phản hồi của các request khác.
- Timeout của request đóng transport và báo kết nối lại; không tự retry mutation.
- Lệnh xóa giữ id đã xác nhận, không lấy lại lựa chọn có thể đã đổi trong lúc chờ.
- Test socket giả dùng đúng giới hạn phản hồi 4 MiB của client khi kiểm dữ liệu lớn.

## Phạm vi kiểm tra

- Unit test và test Socket/JavaFX hiện có, cộng các regression nêu trên.
- 20 seed cố định × 200 thao tác đặt giá/đổi trần/bỏ rule/giá sai: giá tăng, leader khớp
  history, mọi giá tự động không vượt trần tại thời điểm chấp nhận.
- 50 lần cho đặt giá và đóng phiên chạy đua, kiểm một kết quả trạng thái/giá/history nhất quán.
- JAR server ở tiến trình riêng: CRUD, auto-bid, đóng phiên, thanh toán mô phỏng và restart.
- 24 client thật đặt giá đồng thời: người dẫn đầu có giá cao nhất, history không lùi.
- 400 bid khi một client không đọc: client chậm bị ngắt, client còn lại vẫn nhận kết quả.
- Build từ bản sao độc lập của từng thư mục server/client, kiểm không lẫn dependency.

Số liệu sau cùng được lưu trong [validation.json](validation.json) và
[requirements.md](requirements.md). Các test chạy trong kho tạm; không xóa/reset dữ liệu
người dùng để làm test qua.

## Kết quả cuối

- 151 lượt test, không failure/error/skip; gồm test concurrency lặp 100 lần.
- Coverage dòng gộp 1327/1653 (80,3%); không loại GUI hoặc domain khỏi số đo.
- Checkstyle 0 vi phạm; bản sao độc lập của server và client đều clean verify thành công.
- Smoke JAR, thử tải và đọc bản sao dữ liệu cũ đều đạt. Đây là kết quả ở môi trường hiện tại.

Test hồi quy: [client transport](../client/src/test/java/com/example/auction/client/ClientConnectionTest.java),
[Socket/server](../server/src/test/java/com/example/auction/server/SocketIntegrationTest.java),
[DAO](../server/src/test/java/com/example/auction/server/dao/FileStoreTest.java),
[domain](../server/src/test/java/com/example/auction/common/model/AdvancedAuctionTest.java),
[GUI](../integration-tests/src/test/java/com/example/auction/client/ClientWorkflowTest.java).

## Giới hạn còn lại

TCP demo chưa có TLS hay cơ chế chống dò mật khẩu phù hợp dịch vụ Internet. PAID là mô phỏng.
Snapshot ghi đồng bộ toàn bộ dữ liệu nên không dành cho tải lớn; kiểm tra 24 client không
chứng minh khả năng chịu hàng nghìn người. Chưa có idempotency key xuyên restart: nếu mất
phản hồi sau khi server đã lưu, cần kết nối lại và kiểm trạng thái trước khi gửi lại lệnh.
Các kiểm tra này giảm rủi ro trong phạm vi đã thử, không bảo đảm mọi thiết bị và mọi tình
huống đều không có lỗi.
