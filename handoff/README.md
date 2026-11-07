# Bàn giao tham khảo tuần 8 cho A–E

Dùng **[auction-week8-ABCDE-v2.zip](auction-week8-ABCDE-v2.zip)**. Bản v2 thực hiện quy trình: lấy phần tuần 6–8 từ repo dự phòng đã hoàn chỉnh để thành viên phát triển tiếp trên repo chính.

**[Tải ZIP v2 trực tiếp](https://github.com/lphucxyz-alt/auction-system/raw/refs/heads/handoff/week8-ABCDE/handoff/auction-week8-ABCDE-v2.zip)**

## Nguồn và nơi áp dụng

| Mục đích | Repo / nhánh | Commit |
|---|---|---|
| Code mẫu đã hoàn chỉnh | `lphucxyz-alt/auction-system`, `main` | `ac2b632a141011a51fbe630fe8259544148572f5` |
| Nền repo chính để tiếp tục tuần 8 | `lphucxyz-alt/online-auction-system-1`, `dev` | `b2f7bba5016c592385876082f21d189edf4ad6f4` |

ZIP cũ chỉ dùng code nền repo chính, nên chưa đáp ứng yêu cầu lấy mẫu từ repo dự phòng. Giữ file cũ trong lịch sử để đối chiếu; sử dụng **v2** cho công việc tiếp theo.

## Cách dùng

1. Giải nén thư mục `auction system online` và chọn vai A/B/C/D/E.
2. Gửi cả `prompt Pdf.pdf` và `code_tuan_6_7_8.txt` của vai đó cho Gemini hoặc AI được chọn.
3. AI đọc phần `BACKUP_REFERENCE` là mã mẫu nguyên văn; `TARGET_CONTEXT` là mã nền repo chính để kiểm tra API. Không chép đè toàn bộ mã mẫu vào repo chính.
4. Áp dụng hợp đồng chung **W8REF2** về dữ liệu, lifecycle, dịch vụ mock và quyền sửa file. Mỗi người tự đọc, kiểm tra, tạo commit và push nhánh công việc của mình theo quy trình nhóm.

Mỗi thư mục có đúng hai file, tổng cộng mười file. Trích đoạn ghi commit, đường dẫn, dòng nguồn và SHA256. Nhãn tuần phân loại chức năng theo tài liệu môn học, không giả lập ngày viết hay ba phiên bản lịch sử. Đoạn trích không phải project Java độc lập để biên dịch nguyên TXT.

## Thứ tự tích hợp

- A bàn giao `Money` và bảng `AuctionStatus`; E bàn giao `UiAlerts` và khung Admin trước.
- D bàn giao model/dịch vụ mock dùng chung; C hoàn thiện auth/DAO và B hoàn thiện Auction theo API đã chốt.
- D hoàn thiện list/detail/live/login; E hoàn thiện Seller và CI trên các phần chung đã có.
- Test và review trước khi ghép; phần thiếu dependency phải ghi rõ, không tự tạo helper/model trùng trên nhánh khác.

Tuần 8 giữ API/package và ba module hiện có của repo chính để không phá phần đang chạy. Tuần 9–10 chuyển đồng bộ giao thức/DTO và build độc lập theo hướng repo mẫu; auto-bid/anti-sniping/biểu đồ dành cho giai đoạn sau.

## Đã kiểm tra

- ZIP CRC hợp lệ, đúng 10 file; 25 trang PDF có font tiếng Việt và không tràn trang.
- Tất cả đoạn tham khảo khớp từng byte với đúng dòng của code mẫu; toàn bộ 58 file ngữ cảnh khớp commit repo chính.
- Cả năm vai dùng cùng hợp đồng W8REF2 và checksum hợp đồng.
- Hai nền đã được kiểm thử: mẫu 151 test, Checkstyle 0, coverage dòng 80,3%; nền repo chính 133 lượt test. Các số này không phải kết quả của code tuần 8 mà thành viên chưa triển khai.

Lần bàn giao này chỉ thêm tài liệu và ZIP trên nhánh của repo dự phòng. Không push code lên repo chính.
