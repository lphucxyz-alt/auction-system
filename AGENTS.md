# Làm việc trong auction-system

Đây là repo phụ để thử nghiệm BTL Java 21/JavaFX. Repo chính là
`lphucxyz-alt/online-auction-system-1`; không tự chuyển thay đổi sang repo chính.

- Đọc README và `docs/protocol.md` trước khi sửa giao tiếp. Không suy ra hành vi từ tên lớp.
- Một lỗi phải có bước tái hiện và nguyên nhân cụ thể. Không sửa theo phỏng đoán hoặc
  bỏ validation/test để làm build xanh.
- Giữ source dễ giải thích khi vấn đáp. Không thêm dependency, tầng trừu tượng, pattern
  hoặc tính năng chưa có nhu cầu cụ thể. Mỗi thay đổi phải phục vụ yêu cầu đang làm.
- Client chỉ hiển thị và gửi lệnh; server quyết định giá, quyền và người thắng. Không thay
  Socket thật bằng dữ liệu giả để minh họa một chức năng chưa nối mạng.
- Bố cục nằm trong FXML, giao diện trong `auction.css`, hành vi trong Controller.
  Giữ `fx:id`/`onAction` đồng bộ; mở được bằng Scene Builder. Không ghi CSS inline trong Java.
- Không đọc mạng hoặc chờ future trên JavaFX thread; dùng `Platform.runLater` để cập nhật UI.
- Không tự xóa/reset `data/`, đổi tên lớp serialization hoặc sửa biểu đồ lớp gốc.
- Với lỗi logic/mạng, thêm test bắt đúng hành vi lỗi; với bố cục, chạy GUI và xem ảnh thật.
  Không coi coverage cao hoặc nhiều test là chứng minh hết lỗi.
- Chỉ báo build/test/CI thành công sau khi đọc kết quả mới. Ghi lệnh, phạm vi, giới hạn;
  không tạo lịch sử đóng góp, số đo hiệu năng hoặc điểm chấm giả.

Trước bàn giao thay đổi code:

```bash
mvn -B -ntp clean verify
python3 scripts/check_architecture.py
python3 scripts/check_coverage.py
python3 scripts/smoke.py
```

Nếu đổi POM/ranh giới module, chạy thêm `bash scripts/check_independent_builds.sh`.
Nếu đổi UI, xem `integration-tests/target/screenshots/` và kiểm tra ở cửa sổ nhỏ.
Không cài framework/skill mới chỉ để thực hiện những kiểm tra này.

Nguồn và cách áp dụng: [docs/review-and-references.md](docs/review-and-references.md).
