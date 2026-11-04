# Nhật ký thay đổi thực tế

Kế hoạch tuần nằm ở `weekly.md`. Nhật ký này chỉ ghi các thay đổi đã thực hiện và commit
của chính đợt làm hiện tại; không đại diện cho quá trình của một nhóm trong các tuần trước.

| Mảng | Nhánh | Thay đổi thực tế | Commit |
|---|---|---|---|
| A | feature/domain-model | Chuyển model, exception và test domain về server, giữ tên lớp để đọc snapshot cũ | `8203d52` |
| B | feature/auction-model | Viết gọn tính trần auto-bid, kiểm dữ liệu update nháp, thêm hai test biên | `bdf193a` |
| C | spike/socket-echo | POM độc lập cho hai ứng dụng, protocol riêng, tách test tích hợp, kiểm build khi chép riêng thư mục | `517036a` |
| D | feature/client-detail-ui | Đơn giản hóa FXML/CSS, sửa kích thước cửa sổ/cột bảng, lập kế hoạch bốn mảng và cập nhật tài liệu | `669a5b5` |

Các nhánh được làm lần lượt trên cùng checkout trong đợt refactor này. Nhánh B kế thừa A;
C kế thừa B; D kế thừa C. Đó là thứ tự tích hợp thực tế, không mô phỏng bốn người commit
song song. Các nhánh cũ đã tồn tại trên GitHub được giữ nguyên.

## Kết quả kiểm tra sau tích hợp

- Build toàn repo: 137 lượt test, 0 failure/error/skip; Checkstyle 0 vi phạm.
- Coverage gộp từ test đơn vị và tích hợp: 1211/1542 dòng, 78,5%.
- Chép riêng server rồi client ra thư mục tạm: cả hai clean verify thành công.
- Kiểm tra dependency/import và nội dung JAR: không lẫn mã ứng dụng còn lại.
- Smoke test JAR thật: đặt giá, auto-bid, sự kiện đóng, PAID mô phỏng, restart đều đạt.
- Thử bản sao snapshot demo bản trước: đọc dữ liệu và đăng nhập tài khoản cũ thành công.

Các kết quả này đo trên môi trường hiện tại; kết quả CI trên GitHub xem lần chạy tương ứng.

## Kiểm tra lỗi bổ sung sau c7dd5c8

Nhánh `fix/audit-connection-and-events`, commit sửa code `7e3f8f8`: kiểm tra sâu luồng mạng,
lưu file, observer và GUI; các lỗi cùng test tái hiện nằm ở [audit.md](audit.md).
Kết quả sau sửa: 151 lượt test, 0 failure/error/skip, Checkstyle 0 vi phạm, coverage
1327/1653 dòng (80,3%). Bản JAR thật qua thử 24 bidder đồng thời, 400 bid có client không
đọc, restart và đọc bản sao snapshot cũ. Hai ứng dụng vẫn build/test từ thư mục sao chép riêng.
