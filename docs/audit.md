# Kiểm tra lỗi bổ sung

Đợt kiểm tra đầu dùng mốc `c7dd5c8`. Phần này ghi lỗi đã kiểm tra và cách sửa, không coi số
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

## Kết quả đợt kiểm tra trước

- 151 lượt test, không failure/error/skip; gồm test concurrency lặp 100 lần.
- Coverage dòng gộp 1327/1653 (80,3%); không loại GUI hoặc domain khỏi số đo.
- Checkstyle 0 vi phạm; bản sao độc lập của server và client đều clean verify thành công.
- Smoke JAR, thử tải và đọc bản sao dữ liệu cũ đều đạt. Các số liệu thuộc đợt kiểm tra trước.

Test hồi quy: [client transport](../client/src/test/java/com/example/auction/client/ClientConnectionTest.java),
[Socket/server](../server/src/test/java/com/example/auction/server/SocketIntegrationTest.java),
[DAO](../server/src/test/java/com/example/auction/server/dao/FileStoreTest.java),
[domain](../common/src/test/java/com/example/auction/common/model/AuctionRulesTest.java),
[GUI](../client/src/test/java/com/example/auction/client/ClientWorkflowTest.java).

## Giới hạn còn lại

TCP demo chưa có TLS hay cơ chế chống dò mật khẩu phù hợp dịch vụ Internet. PAID là mô phỏng.
Snapshot ghi đồng bộ toàn bộ dữ liệu nên không dành cho tải lớn; kiểm tra 24 client không
chứng minh khả năng chịu hàng nghìn người. Chưa có idempotency key xuyên restart: nếu mất
phản hồi sau khi server đã lưu, cần kết nối lại và kiểm trạng thái trước khi gửi lại lệnh.
Các kiểm tra này giảm rủi ro trong phạm vi đã thử, không bảo đảm mọi thiết bị và mọi tình
huống đều không có lỗi.

## Đối chiếu lại phạm vi BTL ngày 18/11/2026

Hướng dẫn BTL LTNC-2026 xác định đấu giá đồng thời và cập nhật qua Observer/Socket là bắt buộc.
Ba mục tùy chọn đã gỡ khỏi chương trình và bản trình bày. Bảng lịch sử giá vẫn cập nhật qua Socket.

| Tình huống | Tái hiện trước sửa | Hành vi sau sửa |
|---|---|---|
| Seller chỉ sửa mô tả nháp | Mở form với lịch trễ 5 phút, đợi rồi lưu. Server tính lại giờ mở theo thời điểm lưu. | Form gửi lịch mở mới khi trường tương ứng thay đổi. Mô tả có thể sửa mà giữ nguyên lịch. |
| Seller hủy khi phiên đã có giá | Đặt giá rồi hủy bằng tài khoản Seller. Giá hợp lệ bị mất hiệu lực theo quyết định của người bán. | Chỉ Admin được hủy trước khi thanh toán. Lịch sử và giá cao nhất trước khi hủy vẫn có thể xem. |
| Phiên hủy chưa có giá | Server cho phép xóa nhưng nút bị khóa trong giao diện. | Chủ sở hữu hoặc Admin có thể xóa phiên đó từ giao diện. |
| Phiên hết giờ chưa có giá | Thông báo hướng người xem tìm người thắng. | Chi tiết và thông báo thể hiện phiên kết thúc mà chưa có ai đặt giá. |

Hai trường hợp đầu được tái hiện trên JAR cũ và đối chiếu lại bằng JAR mới.
Kiểm thử Socket bảo vệ lịch mở, quyền hủy và lịch sử. Test tích hợp JavaFX kiểm tra form sửa nháp
và cập nhật giá từ client thứ hai. Bản sao dữ liệu demo và snapshot thử của bản cũ đều đọc được.
Snapshot thử có lượt giá và cấu hình đặt giá tự động trước khi chuyển phiên bản. Giá đã chấp nhận
vẫn có trong lịch sử, và lượt đặt mới giữ nguyên hạn đóng.

## Rà đủ năm vai trước khi đóng gói

- AuctionManager duy nhất quản lý vòng đời server và kho. Test khởi động, dừng, mở lại
  bằng cùng dữ liệu và kiểm lần khởi động lỗi vì trùng cổng nhả khóa kho.
- Factory Method tạo đúng ba loại sản phẩm; test kiểm cả thuộc tính riêng của từng loại.
- Hai đăng nhập cùng lúc vào một tài khoản trước đây có thể cùng quan sát chưa có session
  mới và đều giữ quyền. Server xử lý việc thay session và gửi phản hồi dưới cùng monitor;
  kiểm thử Socket xác nhận đúng một session còn thực hiện được lệnh.
- Kiểm thử GUI tạo, sửa, xóa bằng Seller, mở danh sách tài khoản bằng Admin và giữ lịch
  khi Seller sửa thông tin. Phân vai và bằng chứng kỹ thuật nằm ở [requirements.md](requirements.md).

## Rà soát trước khi đưa bản BTL lên GitHub

| Trường hợp | Tái hiện và kết quả sau sửa |
|---|---|
| Sửa cùng một phiên từ hai bản form | Bản form cũ từng ghi đè nội dung vừa lưu. Client gửi phiên bản đã mở; server trả CONFLICT nếu dữ liệu đã đổi. Sửa mô tả giữ nguyên thời điểm và thời lượng khi người dùng giữ các trường lịch. |
| Giá khởi điểm bằng trần đặt giá | Trước đây server cho tạo phiên dù mọi lượt đặt giá đều bị từ chối. Tạo và sửa phiên yêu cầu giá khởi điểm thấp hơn trần. Lượt đặt đúng trần vẫn hợp lệ khi cao hơn giá hiện tại. |
| Server mở lại sau khi cả lịch mở và đóng đã qua | Phiên nháp từng phát RUNNING rồi FINISHED. Server áp dụng các bước chuyển trạng thái và chỉ công bố kết quả FINISHED. |
| Sắp xếp lịch sử qua ranh giới tháng | Cột thời gian từng sắp xếp theo chữ hiển thị. Bảng hiện so sánh thời điểm thực và định dạng ngày, tháng, năm riêng khi hiển thị. |

Ba lỗi nghiệp vụ được tái hiện bằng JUnit trước khi sửa. Kiểm thử tích hợp JavaFX kiểm sắp
thứ tự ngày 30/11 và 01/12 trong bảng lịch sử. Giao diện tiếp tục nhận giá và lịch sử qua Socket.
