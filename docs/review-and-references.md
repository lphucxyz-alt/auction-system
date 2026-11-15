# Review repo phụ và nguồn tham khảo

Ngày kiểm tra: 15/11/2026. Repo phụ: `lphucxyz-alt/auction-system`, mốc trước sửa
`ac2b632`. Repo chính đối chiếu: `lphucxyz-alt/online-auction-system-1`, nhánh `dev`,
commit `8d5d670`. Không coi kết quả của repo phụ là kết quả của repo chính.

## Nhận xét

Repo phụ có nền chức năng tốt hơn vẻ ngoài: đăng nhập qua Socket thật, server kiểm
quyền, lưu snapshot, lịch sử/version, nhiều client và ba tính năng nâng cao. Điểm yếu
trước sửa là người mới khó tự chạy và giao diện chưa hướng dẫn luồng sử dụng.

Đánh giá giao diện trước sửa khoảng **4,5–5/10** theo cảm nhận về bố cục, phân cấp chữ,
màu sắc và khả năng tự sử dụng. Bản sửa nhắm mức **7–7,5/10**, không phải điểm từ giảng
viên hay một phép đo khách quan. Cần người dùng xem ảnh và chạy trên màn hình của mình.

| Phát hiện từ code/chạy thử | Ý nghĩa | Xử lý trong đợt này |
|---|---|---|
| Login không cho biết dùng tài khoản nào; `--demo` chỉ seed kho trống | Mở GUI được nhưng không biết đăng nhập | Thêm hướng dẫn, nút điền demo và script dùng kho demo riêng |
| Hai clone phụ trên máy lệch commit; bản ở home có sửa chưa commit | Dễ sửa một nơi, chạy một nơi | Chọn bản `IdeaProjects` mới hơn, giữ nguyên sửa ở bản home, ghi đường dẫn rõ |
| `scripts/server.sh` chỉ build nếu chưa có JAR | Có thể chạy mã cũ sau khi sửa source | Luôn Maven package trước khi chạy |
| Bảng, giá, nhóm điều khiển ít phân cấp; filter/role dùng mã tiếng Anh | Khó phân biệt trạng thái và hành động chính | CSS riêng, panel, giá nổi bật, badge, nhãn tiếng Việt |
| Nút sửa/xóa/bắt đầu hiện với phiên đang chạy | Server từ chối nhưng người dùng phải thử mới biết | Khóa nút theo trạng thái; server vẫn kiểm quyền/lifecycle |
| Form chỉ dựa vào placeholder, thông số từng nhóm chưa rõ | Khó nhập khi placeholder biến mất | Thêm nhãn và gợi ý theo nhóm sản phẩm |
| Lỗi kết nối có thể lộ thông báo kỹ thuật tiếng Anh | Người dùng không biết bước tiếp theo | Nêu rõ kiểm tra server/địa chỉ/cổng; test dừng server thật |

Chi tiết thao tác: [local-demo.md](local-demo.md). Ảnh từ chạy JavaFX thật nằm ở
`docs/images/`; không dùng ảnh dựng để chứng minh chức năng.

## Điểm còn cần làm rõ trước khi dùng làm bài nộp

1. **Singleton chưa nằm trong luồng ứng dụng chính.** `AuctionManager` là Singleton
   quản lý Item dùng ở `Demo`; `AuctionApplication` mới quản lý phiên của server thật.
   Không nói Singleton này đang vận hành toàn bộ phiên. Nếu thang chấm yêu cầu pattern
   được dùng trong luồng chính, nhóm cần thiết kế tiếp và giải thích lựa chọn.
2. **Simple Factory khác Factory Method.** `ItemFactory.create` là static switch tạo
   sản phẩm, không có creator subclass override factory method. Có dùng factory,
   nhưng không nên gọi đó là Factory Method GoF mà không giải thích khác biệt.
   Không thêm nhiều lớp chỉ để làm đẹp danh sách pattern trong đợt sửa UI.
3. **Có khóa ở hai tầng.** Auction có ReentrantLock theo phiên; `AuctionApplication`
   lại tuần tự hóa command, persist và event bằng monitor chung. Đủ đơn giản cho BTL
   nhỏ; không chứng minh các phiên ghi dữ liệu chạy song song hay chịu tải Internet.
4. **Admin hiện chủ yếu xem tài khoản và quản lý phiên.** Không có màn hình CRUD tài
   khoản đầy đủ; không mô tả là quản trị thương mại hoàn chỉnh.
5. **Lưu file thay database cần đối chiếu yêu cầu lớp.** Hướng dẫn tuần có serialization;
   đề nhắc DAO/Database. Kế hoạch cũng ghi cần xác nhận cách lưu với giảng viên.
6. **Tài liệu có khác biệt cần giữ đúng nguồn.** Đề ghi nhóm 3–4 người; kế hoạch lịch
   sử chia 5 vai; repo phụ có kế hoạch 4 mảng. Hướng dẫn chung ghi trình bày tuần 15,
   kế hoạch ghi tuần 14. Không tự biến những giả định này thành quy định đã xác nhận.
7. **Giới hạn kỹ thuật còn nguyên:** TCP chưa TLS, PAID mô phỏng, ghi toàn snapshot,
   chưa có idempotency xuyên restart. Kiểm thử đạt không có nghĩa không còn lỗi.

## So với repo chính

Ở `dev@8d5d670`, `LoginController` dùng `LocalAuthStorage`, danh sách trong
`MainController`/`SellerController`/`AdminController` được tạo riêng ở mỗi màn hình.
`handlePlaceBid` đổi giá trên view model tại client; `ClientHandler` phía server vẫn echo.
Do đó nhiều cửa sổ không chia sẻ phiên đấu giá như repo phụ. `LoginController` còn rẽ
nhánh Seller hoặc main nên Admin chưa đi vào `admin.fxml`.

Repo chính vẫn **mvn verify thành công với 138 lượt test** (122 common, 11 server,
5 client) trên máy này. Đây là ví dụ test xanh chưa chứng minh luồng GUI ↔ server đã nối.
Kiểm tra từ source và test hiện có, chưa thực hiện một đợt audit toàn diện mọi nhánh của
repo chính. File báo cáo tuần 8 chưa commit trong repo chính được giữ nguyên.

Hai repo khác package (`com.auction` và `com.example.auction`), cấu trúc module và
wire contract. Nên mang từng ý tưởng/test sang theo PR của nhóm; không chép nguyên
controller hoặc merge cả repo phụ để bỏ qua công việc tích hợp của repo chính.

## Tài liệu bài tập đã đối chiếu

Các PDF gốc nằm trên máy, không được sao chép lên GitHub public trong đợt này:

| Tài liệu | Nội dung dùng để review |
|---|---|
| `Videos/BTL/2026-BTL (1).pdf` và bản đề trong `Documents/Doc/Lập trình nâng cao/Bài tập lớn/` | Chức năng, OOP, network/MVC, pattern và bảng chấm |
| `Videos/BTL/Hướng dẫn BTL LTNC-2026.pdf` | Lộ trình 6–15, GUI/FXML, Socket, serialization, coverage ≥60% |
| `Videos/BTL/Kế hoạch dự án Hệ thống đấu giá trực tuyến (nhóm 5 người).pdf` | Mốc MVP, demo nhiều client, quy trình và các giả định cần xác nhận |
| `Videos/BTL/Phân công chi tiết 5 vai trò A-B-C-D-E theo từng tuần (Tuần 6 - Tuần 14).pdf` | Trách nhiệm theo vai, tiêu chí kiểm thử và bàn giao |
| `Videos/BTL/Báo cáo rà soát code Hệ thống đấu giá trực tuyến (Tuần 6–7).pdf` | Lỗi lịch sử, nguyên nhân, hạn chế của lần kiểm tra cũ |
| `Documents/Doc/Lập trình nâng cao/Tài liệu tham khảo/Online Auction System – Việc cần sửa theo từng thành viên (tuần 7).pdf` | Đăng nhập/FXML, thống nhất model, lock/Observer, Socket nhiều dòng |
| README, `docs/decisions.md`, báo cáo tuần 8 ở repo chính | Quy tắc giá, protocol đề xuất, phần còn mock và nhiệm vụ tích hợp |
| README, README từng app và toàn bộ Markdown trong `docs/` repo phụ | Kiến trúc, protocol thật, test, audit cũ, lịch tuần, report, demo và slides |

Các zip snapshot và dump code lịch sử dùng làm dấu mốc tham khảo; không coi chúng là
trạng thái hiện tại thay cho Git/source đang build. Không xác nhận lại kết quả cũ chỉ
bằng đọc một báo cáo. Bằng chứng đợt sửa này được ghi riêng khi chạy kiểm tra.

## Repo nổi tiếng tham khảo và cách áp dụng

Số sao lấy trực tiếp từ GitHub API ngày 15/11/2026, chỉ dùng để đánh giá độ phổ biến.
Các repo dưới đây không chứng nhận dự án đã hết lỗi hoặc hết “AI slop”.

| Nguồn | Sao khi kiểm tra | Đọc và áp dụng |
|---|---:|---|
| [obra/superpowers](https://github.com/obra/superpowers) | 317.662 | Debug theo nguyên nhân; phải có bằng chứng mới trước khi báo hoàn tất |
| [multica-ai/andrej-karpathy-skills](https://github.com/multica-ai/andrej-karpathy-skills) | 233.158 | Giữ thay đổi sát yêu cầu, đơn giản, có tiêu chí kiểm chứng |
| [google/eng-practices](https://github.com/google/eng-practices) | 23.272 | Review thiết kế, tính đúng, concurrency, độ phức tạp, test và trải nghiệm thật |
| [mkpaz/atlantafx](https://github.com/mkpaz/atlantafx) | 1.470 | Tham khảo giao diện CSS, phân cấp, control có sẵn và hệ màu tập trung |
| [openjfx/samples](https://github.com/openjfx/samples) | 661 | Nguồn mẫu JavaFX chính thức và các cách build; không chọn vì số sao |

`andrej-karpathy-skills` đã chuyển từ `forrestchang` sang `multica-ai`; do cộng đồng
viết, không phải repo do Andrej Karpathy phát hành. `google/eng-practices` hiện archived;
đây là tài liệu review đã ổn định. AtlantaFX master hiện yêu cầu Java 25+/JavaFX 27+;
đợt này không thêm dependency đó vào dự án Java 21 và không chép nguyên theme/code.

Các trang đã đọc trực tiếp:

- [Superpowers: xác minh trước khi hoàn tất](https://github.com/obra/superpowers/blob/main/skills/verification-before-completion/SKILL.md)
- [Superpowers: debug có hệ thống](https://github.com/obra/superpowers/blob/main/skills/systematic-debugging/SKILL.md)
- [Karpathy-inspired coding guidelines](https://github.com/multica-ai/andrej-karpathy-skills/blob/main/CLAUDE.md)
- [Google: nội dung cần xem trong code review](https://google.github.io/eng-practices/review/reviewer/looking-for.html)
- [AtlantaFX README](https://github.com/mkpaz/atlantafx/blob/master/README.md)
- [JavaFX 21 CSS Reference](https://openjfx.io/javadoc/21/javafx.graphics/javafx/scene/doc-files/cssref.html)
- [Scene Builder](https://github.com/gluonhq/scenebuilder/wiki)

Chỉ rút các nguyên tắc phù hợp thành [AGENTS.md](../AGENTS.md) ngắn cho repo này.
Không cài một bộ framework, không thêm chuỗi agent và không biến review thành vài
dòng prompt rồi khẳng định đã bảo đảm chất lượng.

## Điều kiện kiểm tra thay đổi lần này

- Build sạch, Checkstyle và test của cả hệ thống; đo coverage từ báo cáo mới.
- Test nút demo chỉ điền, không bỏ qua đăng nhập; đăng ký qua server thật rồi đăng nhập.
- Dừng server thật để kiểm thông báo kết nối và nút đăng nhập được bật lại.
- Hai client gửi bid thật, kiểm GUI/lịch sử/biểu đồ nhận sự kiện; kiểm nút seller khi phiên chạy.
- Chụp Login, danh sách/đặt giá, biểu đồ, cửa sổ nhỏ và form Seller từ JavaFX thật.
- Smoke JAR thật với restart; chép riêng server/client để build độc lập.
- Phân biệt các bước đã chạy ở máy này với trạng thái CI sau push.

Kết quả mới nhất: [ui-validation.json](ui-validation.json). Số liệu trong
`validation.json`/`audit.md` là của lần audit trước; không tự gắn lại cho code mới.
