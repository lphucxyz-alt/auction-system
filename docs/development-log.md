# Nhật ký thay đổi thực tế

Kế hoạch tuần nằm ở `weekly.md`. Nhật ký này chỉ ghi các thay đổi đã thực hiện và commit
của chính đợt làm hiện tại; không đại diện cho quá trình của một nhóm trong các tuần trước.

| Mảng | Nhánh | Thay đổi thực tế | Commit |
|---|---|---|---|
| A | feature/domain-model | Chuyển model, exception và test domain về server, giữ tên lớp để đọc snapshot cũ | `8203d52` |
| B | feature/auction-model | Viết gọn tính trần auto-bid, kiểm dữ liệu update nháp, thêm hai test biên | `bdf193a` |
| C | spike/socket-echo | POM độc lập cho hai ứng dụng, protocol riêng, tách test tích hợp, kiểm build khi chép riêng thư mục | `517036a` |

Các nhánh được làm lần lượt trên cùng checkout trong đợt refactor này. Nhánh B kế thừa A;
C kế thừa B; D kế thừa C. Đó là thứ tự tích hợp thực tế, không mô phỏng bốn người commit
song song. Các nhánh cũ đã tồn tại trên GitHub được giữ nguyên.
