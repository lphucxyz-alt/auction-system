# Kịch bản trình diễn và giải thích

## Chuẩn bị

1. `mvn clean verify`; `python3 scripts/check_coverage.py`.
2. Chạy `java -Dauction.data=data/demo -jar server/target/server-1.0-SNAPSHOT-all.jar --demo`.
3. Mở 3 client bằng `mvn -f client/pom.xml javafx:run` từ 3 terminal.
4. Đăng nhập seller, bidder1 và bidder2 với tài khoản trong README.

## Demo 7–10 phút

1. **Tài khoản:** đăng ký bidder mới, thử mật khẩu sai để thấy lỗi từ server.
2. **CRUD:** seller tạo phiên Camera với giá khởi điểm 1000, mở sau 300s, thời lượng 60s,
   tắt anti-sniping trong demo đóng nhanh. Sửa tên/mô tả trước khi mở. Tạo nháp khác rồi xóa.
3. **Bắt đầu:** chọn phiên, seller bấm Bắt đầu. Hai bidder chọn cùng phiên.
4. **Realtime:** bidder1 đặt 1100, cả hai màn hình đổi giá, lịch sử và biểu đồ cập nhật.
   bidder2 đặt 1200. Thử 1100: bị từ chối, giá không lùi.
5. **Auto-bid:** bidder1 đặt trần 2000 bước 100, bidder2 đặt thủ công 1500. Server tự trả
   thay bidder1 nhưng không vượt 2000. Bidder2 đặt 2100 để thắng trần auto.
6. **Kết thúc:** đợi đủ thời lượng; mọi subscriber thấy kết quả. Bid mới bị từ chối.
   Người thắng xác nhận thanh toán mô phỏng → PAID; tài khoản khác không có quyền.
7. **Lưu trữ:** dừng server bằng Ctrl+C, các client báo mất kết nối. Mở lại cùng kho,
   đăng xuất/đăng nhập lại, xác minh lịch sử, người thắng và PAID vẫn giữ nguyên.
8. **Anti-sniping riêng:** tạo phiên 20s bật anti-sniping, đặt bid khi còn ≤10s. Đồng hồ
   tăng còn khoảng 30s. Nếu demo đóng nhanh, dùng một phiên khác có tắt tính năng này.
9. **Admin:** đăng nhập admin; xem tài khoản, quản lý/hủy phiên người bán khác.

Kịch bản tự động tương đương chạy `python3 scripts/smoke.py` trên JAR thật với kho tạm.
Không xóa dữ liệu demo cũ để “làm đẹp” kết quả mà chưa sao lưu nếu cần giữ lại.

## Những câu cần giải thích được

- Vì sao currentPrice, leader và history phải được đổi trong cùng khóa?
- Tại sao phải kiểm tra endTime trong placeBid, không chỉ dùng scheduler?
- Vì sao listener chạy ngoài khóa; hàng đợi socket ngăn client chậm ảnh hưởng người khác?
- Observer, Factory, Singleton nằm ở đâu? Singleton holder khác một biến global thế nào?
- Tại sao không trả User trực tiếp qua Gson? Password hash có salt bảo vệ điều gì?
- requestId phân biệt RESULT với NEW_BID như thế nào? version ngăn snapshot cũ thế nào?
- Tại sao không gọi socket.readLine trên JavaFX Application Thread?
- Auto-bid giải quyết trần bằng PriorityQueue ra sao? Khi cùng trần ai được ưu tiên?
- Quyền được server suy ra từ đâu; vì sao ẩn nút trên GUI chưa đủ để bảo vệ?
- File tạm + atomic move bảo vệ dữ liệu thế nào; mất mạng sau ghi có nghĩa gì?
- PAID là mô phỏng, chưa chứng minh thanh toán thực ngoài hệ thống.

## Xử lý khi trình diễn

- `Address already in use`: dừng đúng tiến trình cũ hoặc dùng cổng khác ở server và GUI.
- `Data directory đang được dùng`: không cho hai server dùng chung `auction.data`.
- `release version 21 not supported`: kiểm tra cả `java -version`, `javac -version`, `mvn -version` và JAVA_HOME.
- GUI không mở trên server headless: dùng máy có desktop; test Monocle chỉ dành cho CI.
- Không thấy tài khoản demo: `--demo` chỉ seed kho trống. Dùng kho demo riêng hoặc tài khoản đã có.
- Phiên không đóng: kiểm tra anti-sniping và endTime mới; tắt anti ở phiên demo đóng nhanh.
