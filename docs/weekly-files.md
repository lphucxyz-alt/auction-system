# Tuần nào làm file nào?

Lộ trình này chia **file và phần code cần triển khai/đọc** theo tuần 6–15, dựa trên cấu trúc hiện tại.
Đây là kế hoạch triển khai và ôn tập, không phải ngày tạo file hay lịch sử commit trong quá khứ.
Một file có thể xuất hiện nhiều tuần vì mỗi tuần bổ sung một nhóm phương thức; không sao chép code vào các thư mục tuần.

Bốn mảng và nhánh: **A** domain (`feature/domain-model`), **B** đấu giá (`feature/auction-model`),
**C** server/mạng (`spike/socket-echo`), **D** client/tích hợp (`feature/client-detail-ui`).

## Cách đọc một file làm qua nhiều tuần

Ví dụ `Auction.java`:

| Tuần | Phần trong cùng file |
|---|---|
| 6 | Thuộc tính, constructor, thời gian và trạng thái ban đầu |
| 7 | `placeBid`, lịch sử, khóa, `addListener`/`removeListener` |
| 8 | Kiểm trạng thái/deadline, `changeStatus`, `startNow`, `update` |
| 10 | Hoàn thiện phiên bản dữ liệu và nối sự kiện ở tầng ứng dụng |
| 11–12 | Sửa tình huống biên được test phát hiện, chốt lifecycle |
| 13 | `configureAutoBid`, `resolveAutoBids`, `removeAutoBid`, anti-sniping trong `accept` |

Đọc tuần 6 không có nghĩa phải hiểu toàn bộ tính năng tuần 13 ngay. Trong bản hiện tại các phần này đã nằm cùng một file hoàn chỉnh.

## Tuần 6 — Khung dự án và lớp cơ bản

| Mảng | File cần làm/đọc | Phần code và công việc của tuần |
|---|---|---|
| A | [Entity.java](../server/src/main/java/com/example/auction/common/model/Entity.java)<br>[User.java](../server/src/main/java/com/example/auction/common/model/User.java)<br>[Bidder.java](../server/src/main/java/com/example/auction/common/model/Bidder.java)<br>[Seller.java](../server/src/main/java/com/example/auction/common/model/Seller.java)<br>[Admin.java](../server/src/main/java/com/example/auction/common/model/Admin.java)<br>[Item.java](../server/src/main/java/com/example/auction/common/model/Item.java)<br>[Electronics.java](../server/src/main/java/com/example/auction/common/model/Electronics.java)<br>[Art.java](../server/src/main/java/com/example/auction/common/model/Art.java)<br>[Vehicle.java](../server/src/main/java/com/example/auction/common/model/Vehicle.java)<br>[docs/architecture.md](../docs/architecture.md) | Thiết kế cây kế thừa; viết thuộc tính, constructor, getter, getRole/getCategory/printInfo. Thống nhất sơ đồ trước khi thêm nghiệp vụ. |
| B | [Auction.java](../server/src/main/java/com/example/auction/common/model/Auction.java)<br>[AuctionStatus.java](../server/src/main/java/com/example/auction/common/model/AuctionStatus.java)<br>[BidTransaction.java](../server/src/main/java/com/example/auction/common/model/BidTransaction.java) | Tạo dữ liệu một phiên: item, sellerId, startTime/endTime, currentPrice; định nghĩa trạng thái và một giao dịch giá. Tuần này chưa cần auto-bid. |
| C | [server/pom.xml](../server/pom.xml)<br>[ServerMain.java](../server/src/main/java/com/example/auction/server/ServerMain.java)<br>[network/AuctionServer.java](../server/src/main/java/com/example/auction/server/network/AuctionServer.java) | Tạo project server độc lập, điểm vào main và khung ServerSocket. Chạy thử nhận/gửi một dòng giữa hai tiến trình trước khi triển khai toàn bộ lệnh. |
| D | [client/pom.xml](../client/pom.xml)<br>[App.java](../client/src/main/java/com/example/auction/client/App.java)<br>[LoginController.java](../client/src/main/java/com/example/auction/client/LoginController.java)<br>[login.fxml](../client/src/main/resources/com/example/auction/client/login.fxml)<br>[auction.css](../client/src/main/resources/com/example/auction/client/auction.css) | Tạo project client độc lập; chạy JavaFX, tải FXML và chuyển dữ liệu nhập từ form vào controller. Chưa yêu cầu đăng nhập với server thật. |

**Chốt tuần:** Server và client tự biên dịch; màn hình đăng nhập mở được; các lớp có thể tạo đối tượng và in thông tin.

## Tuần 7 — Đặt giá, khóa và Observer

| Mảng | File cần làm/đọc | Phần code và công việc của tuần |
|---|---|---|
| A | [ItemType.java](../server/src/main/java/com/example/auction/common/model/ItemType.java)<br>[ItemFactory.java](../server/src/main/java/com/example/auction/common/model/ItemFactory.java)<br>[AuctionManager.java](../server/src/main/java/com/example/auction/server/AuctionManager.java)<br>[Demo.java](../server/src/main/java/com/example/auction/server/Demo.java) | Viết Factory cho ba loại sản phẩm, Singleton holder và ví dụ kiểm tra đa hình/đối tượng singleton. |
| B | [Auction.java](../server/src/main/java/com/example/auction/common/model/Auction.java)<br>[BidListener.java](../server/src/main/java/com/example/auction/common/model/BidListener.java)<br>[BidTransaction.java](../server/src/main/java/com/example/auction/common/model/BidTransaction.java)<br>[AuctionTest.java](../server/src/test/java/com/example/auction/common/model/AuctionTest.java) | Bổ sung placeBid, addListener/removeListener, history và khóa theo phiên. Ghi currentPrice/leader/history trong cùng khóa; báo Observer sau khi nhả khóa. Test giá thấp, sự kiện và cạnh tranh nhiều luồng. |
| C | [network/AuctionServer.java](../server/src/main/java/com/example/auction/server/network/AuctionServer.java) | Hoàn thiện xử lý từng socket, luồng đọc/ghi và đóng kết nối. Đây là cùng file của tuần 6, được bổ sung phần quản lý nhiều client. |
| D | [AuctionRow.java](../client/src/main/java/com/example/auction/client/AuctionRow.java)<br>[MainController.java](../client/src/main/java/com/example/auction/client/MainController.java)<br>[main.fxml](../client/src/main/resources/com/example/auction/client/main.fxml) | Tạo model hiển thị và bảng danh sách/khung chi tiết: initialize, renderList, detail. Khi làm từ đầu có thể dùng dữ liệu thử trong nhánh UI, rồi thay bằng dữ liệu server ở tuần 9–10. |

**Chốt tuần:** placeBid chạy an toàn khi có nhiều người gửi; bảng hiển thị được các phiên và thông tin sản phẩm.

## Tuần 8 — Ngoại lệ, trạng thái và tách trách nhiệm

| Mảng | File cần làm/đọc | Phần code và công việc của tuần |
|---|---|---|
| A | [Item.java](../server/src/main/java/com/example/auction/common/model/Item.java)<br>[ItemFactory.java](../server/src/main/java/com/example/auction/common/model/ItemFactory.java)<br>[AuctionException.java](../server/src/main/java/com/example/auction/common/exception/AuctionException.java)<br>[InvalidBidException.java](../server/src/main/java/com/example/auction/common/exception/InvalidBidException.java)<br>[AuctionClosedException.java](../server/src/main/java/com/example/auction/common/exception/AuctionClosedException.java)<br>[AuthenticationException.java](../server/src/main/java/com/example/auction/common/exception/AuthenticationException.java) | Kiểm tra tên/giá/thông số đầu vào, thống nhất ngoại lệ cho dữ liệu, giá và xác thực; rà lại đóng gói các lớp. |
| B | [Auction.java](../server/src/main/java/com/example/auction/common/model/Auction.java)<br>[AuctionStatus.java](../server/src/main/java/com/example/auction/common/model/AuctionStatus.java)<br>[AuctionService.java](../server/src/main/java/com/example/auction/server/AuctionService.java)<br>[AuctionTest.java](../server/src/test/java/com/example/auction/common/model/AuctionTest.java)<br>[AdvancedAuctionTest.java](../server/src/test/java/com/example/auction/common/model/AdvancedAuctionTest.java)<br>[ServiceTest.java](../server/src/test/java/com/example/auction/server/ServiceTest.java) | Hoàn thiện changeStatus, startNow, update, requireRunning; từ chối bid khi chưa mở/đã hết giờ. Viết test lifecycle, giá sát deadline, cập nhật nháp sai. AuctionService là dịch vụ demo nhỏ, không phải Socket controller của ứng dụng chính. |
| C | [UserService.java](../server/src/main/java/com/example/auction/server/UserService.java)<br>[ApiException.java](../server/src/main/java/com/example/auction/server/ApiException.java)<br>[ServiceTest.java](../server/src/test/java/com/example/auction/server/ServiceTest.java) | Viết register/login, kiểm tra trùng tên, hash mật khẩu có salt; xác định lỗi nghiệp vụ cần trả cho client. Phân quyền theo tài khoản ở server, không tin role do lệnh bid tự khai. |
| D | [SellerController.java](../client/src/main/java/com/example/auction/client/SellerController.java)<br>[seller.fxml](../client/src/main/resources/com/example/auction/client/seller.fxml)<br>[LoginController.java](../client/src/main/java/com/example/auction/client/LoginController.java)<br>[MainController.java](../client/src/main/java/com/example/auction/client/MainController.java) | Viết form sản phẩm, payload/kiểm tra ô nhập và hiển thị lỗi. Tách việc điều khiển GUI khỏi model, không đưa thuật toán chọn người thắng vào controller. |

**Chốt tuần:** Test giá sai/phiên đóng/mật khẩu sai qua; form sản phẩm báo lỗi thay vì bỏ qua đầu vào hỏng.

## Tuần 9 — Giao thức, đăng nhập qua mạng và lưu file

| Mảng | File cần làm/đọc | Phần code và công việc của tuần |
|---|---|---|
| A | [Entity.java](../server/src/main/java/com/example/auction/common/model/Entity.java)<br>[User.java](../server/src/main/java/com/example/auction/common/model/User.java)<br>[Item.java](../server/src/main/java/com/example/auction/common/model/Item.java)<br>[BidTransaction.java](../server/src/main/java/com/example/auction/common/model/BidTransaction.java)<br>[Auction.java](../server/src/main/java/com/example/auction/common/model/Auction.java) | Đảm bảo các đối tượng cần lưu hỗ trợ serialization; giữ nguyên danh tính UUID. Listener không được lưu cùng dữ liệu, phải được gắn lại sau load. |
| B | [AuctionApplication.java](../server/src/main/java/com/example/auction/server/AuctionApplication.java)<br>[AuctionService.java](../server/src/main/java/com/example/auction/server/AuctionService.java)<br>[ServiceTest.java](../server/src/test/java/com/example/auction/server/ServiceTest.java) | Nối domain vào tầng xử lý lệnh. Xác định facade nhận command, gọi đúng nghiệp vụ và tạo dữ liệu trả về; không gọi trực tiếp GUI từ Auction. |
| C | [docs/protocol.md](../docs/protocol.md)<br>[server/Message.java](../server/src/main/java/com/example/auction/server/protocol/Message.java)<br>[server/MessageType.java](../server/src/main/java/com/example/auction/server/protocol/MessageType.java)<br>[server/ErrorCode.java](../server/src/main/java/com/example/auction/server/protocol/ErrorCode.java)<br>[server/JsonLines.java](../server/src/main/java/com/example/auction/server/protocol/JsonLines.java)<br>[network/AuctionServer.java](../server/src/main/java/com/example/auction/server/network/AuctionServer.java)<br>[dao/FileStore.java](../server/src/main/java/com/example/auction/server/dao/FileStore.java)<br>[server/MessageTest.java](../server/src/test/java/com/example/auction/server/protocol/MessageTest.java)<br>[SocketIntegrationTest.java](../server/src/test/java/com/example/auction/server/SocketIntegrationTest.java) | Chốt JSON Lines/requestId/RESULT/ERROR; triển khai REGISTER, LOGIN, LIST_AUCTIONS và FileStore.load/save. Viết test mã hóa JSON, đăng nhập thật, lưu/tải và lỗi dữ liệu. |
| D | [client/Message.java](../client/src/main/java/com/example/auction/client/protocol/Message.java)<br>[client/MessageType.java](../client/src/main/java/com/example/auction/client/protocol/MessageType.java)<br>[client/ErrorCode.java](../client/src/main/java/com/example/auction/client/protocol/ErrorCode.java)<br>[client/JsonLines.java](../client/src/main/java/com/example/auction/client/protocol/JsonLines.java)<br>[AuctionConnection.java](../client/src/main/java/com/example/auction/client/AuctionConnection.java)<br>[LoginController.java](../client/src/main/java/com/example/auction/client/LoginController.java)<br>[MainController.java](../client/src/main/java/com/example/auction/client/MainController.java)<br>[.github/workflows/ci.yml](../.github/workflows/ci.yml) | Viết request/readLoop của client theo contract, nối đăng nhập và loadPage thật. Nhận mạng ngoài luồng JavaFX, dùng Platform.runLater; bật build/test/Checkstyle trên CI. |

**Chốt tuần:** Client đăng nhập được với server thật, danh sách lấy qua JSON; tắt/mở server không mất dữ liệu đã lưu.

## Tuần 10 — MVP nhiều client và realtime

| Mảng | File cần làm/đọc | Phần code và công việc của tuần |
|---|---|---|
| A | [AuctionApplication.java](../server/src/main/java/com/example/auction/server/AuctionApplication.java)<br>[Item.java](../server/src/main/java/com/example/auction/common/model/Item.java)<br>[User.java](../server/src/main/java/com/example/auction/common/model/User.java) | Rà userView/view và dữ liệu sản phẩm đưa lên mạng: chỉ thông tin public, không lộ passwordHash hay rule trần của người khác. |
| B | [Auction.java](../server/src/main/java/com/example/auction/common/model/Auction.java)<br>[BidListener.java](../server/src/main/java/com/example/auction/common/model/BidListener.java)<br>[AuctionApplication.java](../server/src/main/java/com/example/auction/server/AuctionApplication.java) | Nối attach/changed/emit/tick với sự kiện bid, tăng version; tự mở/đóng theo lịch và xác định người dẫn đầu/người thắng. |
| C | [network/AuctionServer.java](../server/src/main/java/com/example/auction/server/network/AuctionServer.java)<br>[AuctionApplication.java](../server/src/main/java/com/example/auction/server/AuctionApplication.java)<br>[SocketIntegrationTest.java](../server/src/test/java/com/example/auction/server/SocketIntegrationTest.java) | Làm SUBSCRIBE/UNSUBSCRIBE, NEW_BID/AUCTION_FINISHED, kiểm quyền CRUD/PLACE_BID; test hai socket nhận cùng kết quả. |
| D | [AuctionConnection.java](../client/src/main/java/com/example/auction/client/AuctionConnection.java)<br>[MainController.java](../client/src/main/java/com/example/auction/client/MainController.java)<br>[SellerController.java](../client/src/main/java/com/example/auction/client/SellerController.java)<br>[main.fxml](../client/src/main/resources/com/example/auction/client/main.fxml)<br>[seller.fxml](../client/src/main/resources/com/example/auction/client/seller.fxml)<br>[integration-tests/pom.xml](../integration-tests/pom.xml)<br>[ClientWorkflowTest.java](../integration-tests/src/test/java/com/example/auction/client/ClientWorkflowTest.java)<br>[scripts/check_coverage.py](../scripts/check_coverage.py) | Nối event/merge, thao tác tạo/sửa/bắt đầu/đặt giá và hiển thị kết quả; bỏ snapshot cũ theo version. Tách test chạy chung sang integration-tests, đo coverage cả client và server. |

**Chốt tuần:** Một seller và hai bidder chạy xuyên suốt: tạo → mở → bid → nhận giá mới → tự đóng → cùng thấy người thắng; coverage tổng ít nhất 60%.

## Tuần 11 — Kiểm thử tình huống biên và lỗi kết nối

| Mảng | File cần làm/đọc | Phần code và công việc của tuần |
|---|---|---|
| A | [Item.java](../server/src/main/java/com/example/auction/common/model/Item.java)<br>[UserService.java](../server/src/main/java/com/example/auction/server/UserService.java)<br>[ServiceTest.java](../server/src/test/java/com/example/auction/server/ServiceTest.java) | Rà giới hạn độ dài, giá tiền, tên tài khoản và dữ liệu rỗng; bổ sung test đầu vào sai. |
| B | [AuctionTest.java](../server/src/test/java/com/example/auction/common/model/AuctionTest.java)<br>[AdvancedAuctionTest.java](../server/src/test/java/com/example/auction/common/model/AdvancedAuctionTest.java)<br>[Auction.java](../server/src/main/java/com/example/auction/common/model/Auction.java) | Rà concurrentBidsKeepConsistent, observer lỗi, thời điểm hết hạn và tính bất biến của history; sửa chỉ khi test chỉ ra lỗi. |
| C | [server/JsonLines.java](../server/src/main/java/com/example/auction/server/protocol/JsonLines.java)<br>[network/AuctionServer.java](../server/src/main/java/com/example/auction/server/network/AuctionServer.java)<br>[dao/FileStore.java](../server/src/main/java/com/example/auction/server/dao/FileStore.java)<br>[SocketIntegrationTest.java](../server/src/test/java/com/example/auction/server/SocketIntegrationTest.java) | Chặn frame quá lớn, xử lý client chậm, process lock, snapshot hỏng và lỗi ghi file; kiểm server không xác nhận thao tác chưa lưu được. |
| D | [client/ClientConnectionTest.java](../client/src/test/java/com/example/auction/client/ClientConnectionTest.java)<br>[AuctionConnection.java](../client/src/main/java/com/example/auction/client/AuctionConnection.java)<br>[MainController.java](../client/src/main/java/com/example/auction/client/MainController.java)<br>[ClientWorkflowTest.java](../integration-tests/src/test/java/com/example/auction/client/ClientWorkflowTest.java) | Test phản hồi đến khác thứ tự, socket đóng giữa yêu cầu, pending request thất bại, thông báo mất mạng và nút theo vai trò. |

**Chốt tuần:** Lỗi của một kết nối không làm hỏng client khác; không treo GUI hoặc tiếp tục ghi sau lỗi lưu trữ.

## Tuần 12 — Đóng gói độc lập và hoàn thiện luồng bắt buộc

| Mảng | File cần làm/đọc | Phần code và công việc của tuần |
|---|---|---|
| A | [server/README.md](../server/README.md)<br>[docs/architecture.md](../docs/architecture.md)<br>[scripts/check_architecture.py](../scripts/check_architecture.py) | Kiểm domain/exception/DAO nằm ở server. Giải thích package common.model còn giữ để đọc snapshot cũ, không phải module dùng chung. |
| B | [AuctionStatus.java](../server/src/main/java/com/example/auction/common/model/AuctionStatus.java)<br>[Auction.java](../server/src/main/java/com/example/auction/common/model/Auction.java)<br>[AuctionApplication.java](../server/src/main/java/com/example/auction/server/AuctionApplication.java)<br>[AdvancedAuctionTest.java](../server/src/test/java/com/example/auction/common/model/AdvancedAuctionTest.java) | Chốt PAY_AUCTION/CANCEL_AUCTION; chỉ người thắng xác nhận PAID mô phỏng, không có bid thì không có người thắng. Hoàn thiện điều kiện sửa/xóa nháp. |
| C | [server/pom.xml](../server/pom.xml)<br>[ServerMain.java](../server/src/main/java/com/example/auction/server/ServerMain.java)<br>[scripts/server.sh](../scripts/server.sh)<br>[scripts/smoke.py](../scripts/smoke.py)<br>[scripts/check_independent_builds.sh](../scripts/check_independent_builds.sh) | Đóng gói JAR -all, chạy từ riêng thư mục server, kiểm restart tiến trình thật. Thử source/POM khi không có mã client. |
| D | [client/pom.xml](../client/pom.xml)<br>[client/README.md](../client/README.md)<br>[scripts/client.sh](../scripts/client.sh)<br>[scripts/check_independent_builds.sh](../scripts/check_independent_builds.sh)<br>[.github/workflows/ci.yml](../.github/workflows/ci.yml) | Build từ riêng thư mục client, không cần server artifact hay POM gốc. Bổ sung job CI cho từng app và kiểm tra toàn bộ cùng nhau. |

**Chốt tuần:** Chép riêng từng ứng dụng ra thư mục khác vẫn build/test được; hai bản chạy được tiếp tục trao đổi đúng protocol.

## Tuần 13 — Các chức năng nâng cao

| Mảng | File cần làm/đọc | Phần code và công việc của tuần |
|---|---|---|
| A | [docs/architecture.md](../docs/architecture.md)<br>[docs/protocol.md](../docs/protocol.md)<br>[docs/requirements.md](../docs/requirements.md) | Review API, mô tả trạng thái và quy tắc nâng cao để tài liệu khớp mã; cập nhật bảng đối chiếu đề. |
| B | [Auction.java](../server/src/main/java/com/example/auction/common/model/Auction.java)<br>[AdvancedAuctionTest.java](../server/src/test/java/com/example/auction/common/model/AdvancedAuctionTest.java) | Viết configureAutoBid/removeAutoBid/resolveAutoBids bằng PriorityQueue; xử lý trần, bước giá, hòa trần. Bổ sung anti-sniping trong accept và test endTime. |
| C | [AuctionApplication.java](../server/src/main/java/com/example/auction/server/AuctionApplication.java)<br>[dao/FileStore.java](../server/src/main/java/com/example/auction/server/dao/FileStore.java)<br>[SocketIntegrationTest.java](../server/src/test/java/com/example/auction/server/SocketIntegrationTest.java) | Nối AUTO_BID/REMOVE_AUTO_BID, giữ rule qua restart; không đưa trần riêng vào JSON public. Kiểm clock đọc đúng endTime sau gia hạn. |
| D | [MainController.java](../client/src/main/java/com/example/auction/client/MainController.java)<br>[main.fxml](../client/src/main/resources/com/example/auction/client/main.fxml)<br>[ClientWorkflowTest.java](../integration-tests/src/test/java/com/example/auction/client/ClientWorkflowTest.java)<br>[README.md](../README.md)<br>[docs/demo.md](../docs/demo.md) | Nối các ô maxBid/increment và biểu đồ LineChart trong detail; mỗi event cập nhật điểm giá. Hoàn thiện cách chạy và kịch bản demo. |

**Chốt tuần:** Auto-bid không vượt trần, anti-sniping có test, biểu đồ nhận giá thật từ client khác. Chức năng bắt buộc vẫn qua test.

## Tuần 14 — Rà soát và chuẩn bị trình bày

| Mảng | File cần làm/đọc | Phần code và công việc của tuần |
|---|---|---|
| A | [docs/architecture.md](../docs/architecture.md)<br>[docs/report.md](../docs/report.md) | Rà cây lớp và giải thích OOP/Factory/Singleton bằng đúng code hiện có. |
| B | [docs/report.md](../docs/report.md)<br>[docs/demo.md](../docs/demo.md) | Viết phần giải thích khóa, Observer, deadline và auto-bid; tập các câu hỏi về race condition. |
| C | [server/README.md](../server/README.md)<br>[docs/protocol.md](../docs/protocol.md)<br>[scripts/smoke.py](../scripts/smoke.py) | Chuẩn bị server demo và kho dự phòng; chạy thử đóng phiên/restart trên máy trình bày. |
| D | [login.fxml](../client/src/main/resources/com/example/auction/client/login.fxml)<br>[main.fxml](../client/src/main/resources/com/example/auction/client/main.fxml)<br>[auction.css](../client/src/main/resources/com/example/auction/client/auction.css)<br>[docs/slides.md](../docs/slides.md)<br>[scripts/export_docs.py](../scripts/export_docs.py)<br>[docs/presentation.pptx](../docs/presentation.pptx)<br>[docs/report.pdf](../docs/report.pdf)<br>[docs/report.docx](../docs/report.docx) | Rà chỗ chữ bị cắt, kích thước cửa sổ và ô nhập. Chỉnh nội dung slide/báo cáo, xuất file và tập demo; chỉ sửa lỗi giao diện đã quan sát. |

**Chốt tuần:** Build/test xanh, tài liệu khớp bản chạy; trình bày nếu lịch lớp là tuần 14, còn không tiếp tục tập dượt.

## Tuần 15 — Bàn giao và trình bày theo lịch lớp

| Mảng | File cần làm/đọc | Phần code và công việc của tuần |
|---|---|---|
| A | [docs/architecture.md](../docs/architecture.md)<br>[docs/development-log.md](../docs/development-log.md) | Giải thích phần domain và đối chiếu công việc đã có bằng chứng. |
| B | [docs/demo.md](../docs/demo.md)<br>[docs/report.md](../docs/report.md) | Trình bày thuật toán/trạng thái, chạy lại các tình huống bid sai và kết thúc phiên. |
| C | [scripts/server.sh](../scripts/server.sh)<br>[scripts/smoke.py](../scripts/smoke.py)<br>[server/README.md](../server/README.md) | Vận hành server demo, kiểm dữ liệu và chuẩn bị cách khởi động lại khi cần. |
| D | [README.md](../README.md)<br>[docs/presentation.pptx](../docs/presentation.pptx)<br>[docs/requirements.md](../docs/requirements.md)<br>[docs/validation.json](../docs/validation.json)<br>[docs/weekly.md](../docs/weekly.md) | Chốt hướng dẫn và kết quả thực thi, trình bày GUI, đóng gói bản nộp; ghi đúng đóng góp và trạng thái thực tế. |

**Chốt tuần:** Nộp đúng bản đã kiểm tra và giải thích được code. Nếu lớp đã chấm tuần 14 thì đây là tuần dự phòng.

## Tra ngược: một file liên quan những tuần nào?

Tuần dưới đây là tuần theo lộ trình, không phải mốc commit. Bảng này giúp tìm phần cần học khi mở một file.

| File | Tuần theo lộ trình |
|---|---|
| [.github/workflows/ci.yml](../.github/workflows/ci.yml) | 9, 12 |
| [README.md](../README.md) | 13, 15 |
| [client/README.md](../client/README.md) | 12 |
| [client/pom.xml](../client/pom.xml) | 6, 12 |
| [client/src/main/java/com/example/auction/client/App.java](../client/src/main/java/com/example/auction/client/App.java) | 6 |
| [client/src/main/java/com/example/auction/client/AuctionConnection.java](../client/src/main/java/com/example/auction/client/AuctionConnection.java) | 9, 10, 11 |
| [client/src/main/java/com/example/auction/client/AuctionRow.java](../client/src/main/java/com/example/auction/client/AuctionRow.java) | 7 |
| [client/src/main/java/com/example/auction/client/LoginController.java](../client/src/main/java/com/example/auction/client/LoginController.java) | 6, 8, 9 |
| [client/src/main/java/com/example/auction/client/MainController.java](../client/src/main/java/com/example/auction/client/MainController.java) | 7, 8, 9, 10, 11, 13 |
| [client/src/main/java/com/example/auction/client/SellerController.java](../client/src/main/java/com/example/auction/client/SellerController.java) | 8, 10 |
| [client/src/main/java/com/example/auction/client/protocol/ErrorCode.java](../client/src/main/java/com/example/auction/client/protocol/ErrorCode.java) | 9 |
| [client/src/main/java/com/example/auction/client/protocol/JsonLines.java](../client/src/main/java/com/example/auction/client/protocol/JsonLines.java) | 9 |
| [client/src/main/java/com/example/auction/client/protocol/Message.java](../client/src/main/java/com/example/auction/client/protocol/Message.java) | 9 |
| [client/src/main/java/com/example/auction/client/protocol/MessageType.java](../client/src/main/java/com/example/auction/client/protocol/MessageType.java) | 9 |
| [client/src/main/resources/com/example/auction/client/auction.css](../client/src/main/resources/com/example/auction/client/auction.css) | 6, 14 |
| [client/src/main/resources/com/example/auction/client/login.fxml](../client/src/main/resources/com/example/auction/client/login.fxml) | 6, 14 |
| [client/src/main/resources/com/example/auction/client/main.fxml](../client/src/main/resources/com/example/auction/client/main.fxml) | 7, 10, 13, 14 |
| [client/src/main/resources/com/example/auction/client/seller.fxml](../client/src/main/resources/com/example/auction/client/seller.fxml) | 8, 10 |
| [client/src/test/java/com/example/auction/client/ClientConnectionTest.java](../client/src/test/java/com/example/auction/client/ClientConnectionTest.java) | 11 |
| [docs/architecture.md](../docs/architecture.md) | 6, 12, 13, 14, 15 |
| [docs/demo.md](../docs/demo.md) | 13, 14, 15 |
| [docs/development-log.md](../docs/development-log.md) | 15 |
| [docs/presentation.pptx](../docs/presentation.pptx) | 14, 15 |
| [docs/protocol.md](../docs/protocol.md) | 9, 13, 14 |
| [docs/report.docx](../docs/report.docx) | 14 |
| [docs/report.md](../docs/report.md) | 14, 15 |
| [docs/report.pdf](../docs/report.pdf) | 14 |
| [docs/requirements.md](../docs/requirements.md) | 13, 15 |
| [docs/slides.md](../docs/slides.md) | 14 |
| [docs/validation.json](../docs/validation.json) | 15 |
| [docs/weekly.md](../docs/weekly.md) | 15 |
| [integration-tests/pom.xml](../integration-tests/pom.xml) | 10 |
| [integration-tests/src/test/java/com/example/auction/client/ClientWorkflowTest.java](../integration-tests/src/test/java/com/example/auction/client/ClientWorkflowTest.java) | 10, 11, 13 |
| [scripts/check_architecture.py](../scripts/check_architecture.py) | 12 |
| [scripts/check_coverage.py](../scripts/check_coverage.py) | 10 |
| [scripts/check_independent_builds.sh](../scripts/check_independent_builds.sh) | 12 |
| [scripts/client.sh](../scripts/client.sh) | 12 |
| [scripts/export_docs.py](../scripts/export_docs.py) | 14 |
| [scripts/server.sh](../scripts/server.sh) | 12, 15 |
| [scripts/smoke.py](../scripts/smoke.py) | 12, 14, 15 |
| [server/README.md](../server/README.md) | 12, 14, 15 |
| [server/pom.xml](../server/pom.xml) | 6, 12 |
| [server/src/main/java/com/example/auction/common/exception/AuctionClosedException.java](../server/src/main/java/com/example/auction/common/exception/AuctionClosedException.java) | 8 |
| [server/src/main/java/com/example/auction/common/exception/AuctionException.java](../server/src/main/java/com/example/auction/common/exception/AuctionException.java) | 8 |
| [server/src/main/java/com/example/auction/common/exception/AuthenticationException.java](../server/src/main/java/com/example/auction/common/exception/AuthenticationException.java) | 8 |
| [server/src/main/java/com/example/auction/common/exception/InvalidBidException.java](../server/src/main/java/com/example/auction/common/exception/InvalidBidException.java) | 8 |
| [server/src/main/java/com/example/auction/common/model/Admin.java](../server/src/main/java/com/example/auction/common/model/Admin.java) | 6 |
| [server/src/main/java/com/example/auction/common/model/Art.java](../server/src/main/java/com/example/auction/common/model/Art.java) | 6 |
| [server/src/main/java/com/example/auction/common/model/Auction.java](../server/src/main/java/com/example/auction/common/model/Auction.java) | 6, 7, 8, 9, 10, 11, 12, 13 |
| [server/src/main/java/com/example/auction/common/model/AuctionStatus.java](../server/src/main/java/com/example/auction/common/model/AuctionStatus.java) | 6, 8, 12 |
| [server/src/main/java/com/example/auction/common/model/BidListener.java](../server/src/main/java/com/example/auction/common/model/BidListener.java) | 7, 10 |
| [server/src/main/java/com/example/auction/common/model/BidTransaction.java](../server/src/main/java/com/example/auction/common/model/BidTransaction.java) | 6, 7, 9 |
| [server/src/main/java/com/example/auction/common/model/Bidder.java](../server/src/main/java/com/example/auction/common/model/Bidder.java) | 6 |
| [server/src/main/java/com/example/auction/common/model/Electronics.java](../server/src/main/java/com/example/auction/common/model/Electronics.java) | 6 |
| [server/src/main/java/com/example/auction/common/model/Entity.java](../server/src/main/java/com/example/auction/common/model/Entity.java) | 6, 9 |
| [server/src/main/java/com/example/auction/common/model/Item.java](../server/src/main/java/com/example/auction/common/model/Item.java) | 6, 8, 9, 10, 11 |
| [server/src/main/java/com/example/auction/common/model/ItemFactory.java](../server/src/main/java/com/example/auction/common/model/ItemFactory.java) | 7, 8 |
| [server/src/main/java/com/example/auction/common/model/ItemType.java](../server/src/main/java/com/example/auction/common/model/ItemType.java) | 7 |
| [server/src/main/java/com/example/auction/common/model/Seller.java](../server/src/main/java/com/example/auction/common/model/Seller.java) | 6 |
| [server/src/main/java/com/example/auction/common/model/User.java](../server/src/main/java/com/example/auction/common/model/User.java) | 6, 9, 10 |
| [server/src/main/java/com/example/auction/common/model/Vehicle.java](../server/src/main/java/com/example/auction/common/model/Vehicle.java) | 6 |
| [server/src/main/java/com/example/auction/server/ApiException.java](../server/src/main/java/com/example/auction/server/ApiException.java) | 8 |
| [server/src/main/java/com/example/auction/server/AuctionApplication.java](../server/src/main/java/com/example/auction/server/AuctionApplication.java) | 9, 10, 12, 13 |
| [server/src/main/java/com/example/auction/server/AuctionManager.java](../server/src/main/java/com/example/auction/server/AuctionManager.java) | 7 |
| [server/src/main/java/com/example/auction/server/AuctionService.java](../server/src/main/java/com/example/auction/server/AuctionService.java) | 8, 9 |
| [server/src/main/java/com/example/auction/server/Demo.java](../server/src/main/java/com/example/auction/server/Demo.java) | 7 |
| [server/src/main/java/com/example/auction/server/ServerMain.java](../server/src/main/java/com/example/auction/server/ServerMain.java) | 6, 12 |
| [server/src/main/java/com/example/auction/server/UserService.java](../server/src/main/java/com/example/auction/server/UserService.java) | 8, 11 |
| [server/src/main/java/com/example/auction/server/dao/FileStore.java](../server/src/main/java/com/example/auction/server/dao/FileStore.java) | 9, 11, 13 |
| [server/src/main/java/com/example/auction/server/network/AuctionServer.java](../server/src/main/java/com/example/auction/server/network/AuctionServer.java) | 6, 7, 9, 10, 11 |
| [server/src/main/java/com/example/auction/server/protocol/ErrorCode.java](../server/src/main/java/com/example/auction/server/protocol/ErrorCode.java) | 9 |
| [server/src/main/java/com/example/auction/server/protocol/JsonLines.java](../server/src/main/java/com/example/auction/server/protocol/JsonLines.java) | 9, 11 |
| [server/src/main/java/com/example/auction/server/protocol/Message.java](../server/src/main/java/com/example/auction/server/protocol/Message.java) | 9 |
| [server/src/main/java/com/example/auction/server/protocol/MessageType.java](../server/src/main/java/com/example/auction/server/protocol/MessageType.java) | 9 |
| [server/src/test/java/com/example/auction/common/model/AdvancedAuctionTest.java](../server/src/test/java/com/example/auction/common/model/AdvancedAuctionTest.java) | 8, 11, 12, 13 |
| [server/src/test/java/com/example/auction/common/model/AuctionTest.java](../server/src/test/java/com/example/auction/common/model/AuctionTest.java) | 7, 8, 11 |
| [server/src/test/java/com/example/auction/server/ServiceTest.java](../server/src/test/java/com/example/auction/server/ServiceTest.java) | 8, 9, 11 |
| [server/src/test/java/com/example/auction/server/SocketIntegrationTest.java](../server/src/test/java/com/example/auction/server/SocketIntegrationTest.java) | 9, 10, 11, 13 |
| [server/src/test/java/com/example/auction/server/protocol/MessageTest.java](../server/src/test/java/com/example/auction/server/protocol/MessageTest.java) | 9 |

## Tự kiểm tra với mã hiện tại

Các lệnh sau kiểm tra bản đầy đủ đang có, không khôi phục một snapshot lịch sử của từng tuần:

```bash
mvn -f server/pom.xml -Dtest=AuctionTest,AdvancedAuctionTest test
mvn -f server/pom.xml -Dtest=ServiceTest,SocketIntegrationTest test
mvn -f client/pom.xml clean verify
mvn clean verify
python3 scripts/check_architecture.py
python3 scripts/check_coverage.py
bash scripts/check_independent_builds.sh
```

Khi làm thật từng tuần, cập nhật trạng thái/bằng chứng ở [weekly.md](weekly.md).
Các commit đã thực hiện xem ở [development-log.md](development-log.md).
Tài liệu này không thay đổi lịch sử Git hoặc ghi nhận rằng các file đã được viết trong những tuần trước.
