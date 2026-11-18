# Kịch bản trình diễn và giải thích

## Chuẩn bị

1. `mvn clean verify`; `python3 scripts/check_coverage.py`.
2. Chạy `java -Dauction.data=data/demo -jar server/target/server-1.0-SNAPSHOT-all.jar --demo`.
3. Mở 3 client bằng `bash scripts/client.sh` từ 3 terminal.
4. Đăng nhập seller, bidder1 và bidder2 với tài khoản trong README.

## Demo 7–10 phút

1. **Tài khoản:** đăng ký bidder mới, thử mật khẩu sai để thấy lỗi từ server.
2. **CRUD:** seller tạo phiên Camera với giá khởi điểm 1000, mở sau 300s, thời lượng 60s,
   sửa tên hoặc mô tả và kiểm tra lịch mở được giữ. Tạo một nháp khác rồi xóa.
3. **Bắt đầu:** chọn phiên, seller bấm Bắt đầu. Hai bidder chọn cùng phiên.
4. **Realtime:** bidder1 đặt 1100, cả hai màn hình đổi giá, lịch sử cập nhật.
   bidder2 đặt 1200. Thử 1100: bị từ chối, giá không lùi.
5. **Kết thúc:** đợi đủ thời lượng; mọi subscriber thấy kết quả. Bid mới bị từ chối.
   Người thắng xác nhận thanh toán mô phỏng → PAID; tài khoản khác không có quyền.
6. **Lưu trữ:** dừng server bằng Ctrl+C, các client báo mất kết nối. Mở lại cùng kho,
   đăng xuất/đăng nhập lại, xác minh lịch sử, người thắng và PAID vẫn giữ nguyên.
7. **Admin:** đăng nhập admin, xem tài khoản và thử hủy một phiên chưa thanh toán đã có giá.
   Kiểm tra lịch sử giá vẫn hiển thị sau khi hủy.

Kịch bản tự động tương đương chạy `python3 scripts/smoke.py` trên JAR thật với kho tạm.
Không xóa dữ liệu demo cũ để “làm đẹp” kết quả mà chưa sao lưu nếu cần giữ lại.

## Những câu cần giải thích được

- Vì sao currentPrice, leader và history phải được đổi trong cùng khóa?
- Tại sao phải kiểm tra endTime trong placeBid, không chỉ dùng scheduler?
- Vì sao listener chạy ngoài khóa; hàng đợi socket ngăn client chậm ảnh hưởng người khác?
- Observer, Factory, Singleton nằm ở đâu? Singleton khởi động và đóng server ở đâu?
- Tại sao không trả User trực tiếp qua Gson? Password hash có salt bảo vệ điều gì?
- requestId phân biệt RESULT với NEW_BID như thế nào? version ngăn snapshot cũ thế nào?
- Tại sao không gọi socket.readLine trên JavaFX Application Thread?
- Quyền được server suy ra từ đâu; vì sao ẩn nút trên GUI chưa đủ để bảo vệ?
- File tạm + atomic move bảo vệ dữ liệu thế nào; mất mạng sau ghi có nghĩa gì?
- PAID là mô phỏng, chưa chứng minh thanh toán thực ngoài hệ thống.

## Xử lý khi trình diễn

- `Address already in use`: dừng đúng tiến trình cũ hoặc dùng cổng khác ở server và GUI.
- `Data directory đang được dùng`: không cho hai server dùng chung `auction.data`.
- `release version 21 not supported`: kiểm tra cả `java -version`, `javac -version`, `mvn -version` và JAVA_HOME.
- GUI không mở trên server headless: dùng máy có desktop; test Monocle chỉ dành cho CI.
- Không thấy tài khoản demo: `--demo` chỉ seed kho trống. Dùng kho demo riêng hoặc tài khoản đã có.
- Phiên không đóng: kiểm tra giờ kết thúc và trạng thái phiên do server trả về.
