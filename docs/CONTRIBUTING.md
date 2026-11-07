# Làm việc trong nhóm

1. Chọn một thay đổi cụ thể, ví dụ kiểm tra bước giá hoặc bổ sung màn hình người bán. Ghi người phụ trách và cách kiểm tra trong issue của nhóm.
2. Tạo nhánh riêng, sửa mã trong tầng liên quan và bổ sung test cho thay đổi nghiệp vụ.
3. Chạy `mvn test`. Nếu sửa FXML, CSS hoặc controller, chạy thêm `mvn "-Dtest.fx=true" verify`.
4. Commit theo từng thay đổi đã kiểm tra. Ví dụ `feat: add seller listing editor` hoặc `test: cover equal proxy ceilings`. Mở pull request để một thành viên khác đọc và chạy thử.
5. Khi review, người viết giải thích dữ liệu đi qua client, server và SQLite như thế nào. Chỉ đưa mã nguồn, tài nguyên và tài liệu vào Git. Dữ liệu tài khoản nằm trong thư mục runtime `data`.
