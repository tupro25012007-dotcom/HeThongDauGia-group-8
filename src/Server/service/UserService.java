package Server.service;
import Server.repository.UserRepository; //làm việc với bảng tài khoản trong database
import Server.repository.WalletRepository;
import Shared.model.*;  // dùng User, Bidder, Seller, Admin, UserRole
import java.sql.SQLException; // xử lí lỗi sqlite
import java.util.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import Shared.model.Wallet;
public class UserService {
    private final Map<String, User> usersByUsername = new HashMap<>(); // giúp tìm kiếm người dùng theo tên đặng nhập
    private final Map<String, User> usersById = new HashMap<>(); // giúp tìm kiếm người dùng qua ID
    private final Map<String, String> passwords = new HashMap<>(); // giúp xác thực mật khẩu người dùng nhập có khớp với trong database
    private final UserRepository userRepository = new UserRepository(); // nơi cập nhật tài khoản trong sqlite
    private final WalletRepository walletRepository = new WalletRepository();

    public UserService() {
        try {
            for (UserRepository.UserRow row : userRepository.findAll()) {
                User user = createUser(row.id(), row.createdAt(), row.username(), row.role());
                user.setBalance(row.balance());
                String key = normalize(row.username());
                usersByUsername.put(key, user);
                usersById.put(user.getId(), user);
                passwords.put(key, row.password());
            }
        } catch (SQLException ex) { // phòng trường hợp database lỗi khi cập nhật tài khoản
            throw new RuntimeException("Không nạp được tài khoản từ database", ex);
        }
    }

    public synchronized User register(String username, String password, UserRole role) {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Username không được để trống");
        String key = normalize(username);
        if (usersByUsername.containsKey(key)) throw new IllegalArgumentException("Username đã tồn tại");
        if (password == null || password.length() < 8) throw new IllegalArgumentException("Password phải có ít nhất 8 kí tự");
        if (role == null) throw new IllegalArgumentException("Role không hợp lệ");

        User user = createUser(username.trim(), role);
        try {
            if (userRepository.findByUsername(key).isPresent()) throw new IllegalArgumentException("Username đã tồn tại");
            userRepository.save(user.getId(), key, password, role, user.getCreatedAt(), user.getBalance());
        } catch (SQLException ex) {
            throw new RuntimeException("Không lưu được tài khoản vào database", ex);
        }
        usersByUsername.put(key, user);
        usersById.put(user.getId(), user);
        passwords.put(key, password);
        return user;
    }

    public synchronized Optional<User> login(String username, String password) {
        if (username == null || password == null) return Optional.empty();
        String key = normalize(username);
        String stored = passwords.get(key);
        return stored != null && stored.equals(password) ? Optional.ofNullable(usersByUsername.get(key)) : Optional.empty();
    }

    public synchronized Optional<User> findById(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(usersById.get(id));
    }

    public synchronized List<User> getAllUsers() { return new ArrayList<>(usersById.values()); }

    public synchronized BigDecimal getAvailableBalance(String userId) {
        User user = usersById.get(userId);
        if (user == null) throw new NoSuchElementException("Không tìm thấy tài khoản");
        return user.getBalance();
    }
    public synchronized void deposit(String userId, BigDecimal amount, String requestId) {
        if (amount == null || amount.signum() <= 0)
            throw new IllegalArgumentException("Số tiền nạp phải lớn hơn 0");
        if (requestId == null || requestId.isBlank())
            throw new IllegalArgumentException("Thiếu mã giao dịch nạp tiền");
        try {
            java.util.UUID.fromString(requestId);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Mã giao dịch nạp tiền không hợp lệ");
        }
        applyWalletEntries(List.of(new Wallet.Entry(userId, "deposit/" + requestId,
                "Nạp tiền mô phỏng", amount, LocalDateTime.now())));
    }

    /** Ghi số dư và sổ giao dịch trong cùng một SQLite transaction. */
    public synchronized void applyWalletEntries(List<Wallet.Entry> entries) {
        Map<String, BigDecimal> deltas = new HashMap<>();
        for (Wallet.Entry entry : entries) {
            if (entry.ownerId() == null || entry.reference() == null || entry.delta() == null)
                throw new IllegalArgumentException("Giao dịch ví thiếu thông tin");
            deltas.merge(entry.ownerId(), entry.delta(), BigDecimal::add);
        }
        Map<String, BigDecimal> updated = new HashMap<>();
        for (Map.Entry<String, BigDecimal> entry : deltas.entrySet()) {
            User user = usersById.get(entry.getKey());
            if (user == null) throw new NoSuchElementException("Không tìm thấy tài khoản");
            BigDecimal next = user.getBalance().add(entry.getValue());
            if (next.signum() < 0) throw new IllegalArgumentException("Số dư khả dụng không đủ");
            updated.put(user.getId(), next);
        }
        try {
            boolean recorded = walletRepository.apply(updated, entries);
            if (!recorded) return;
        } catch (SQLException ex) {
            throw new RuntimeException("Không lưu được giao dịch ví vào database", ex);
        }
        updated.forEach((id, balance) -> usersById.get(id).setBalance(balance));
    }

    public synchronized Wallet getWallet(String userId, BigDecimal held) {
        User user = usersById.get(userId);
        if (user == null) throw new NoSuchElementException("Không tìm thấy tài khoản");
        try {
            return new Wallet(user.getBalance().add(held), held, walletRepository.findRecent(userId));
        } catch (SQLException ex) {
            throw new RuntimeException("Không tải được lịch sử ví", ex);
        }
    }

    public synchronized void settleSeller(String sellerId, BigDecimal amount,
                                          Wallet.Entry entry,
                                          Server.repository.AuctionRepository.AuctionRow auction) {
        try {
            if (walletRepository.settleSeller(sellerId, amount, entry, auction)) {
                User seller = usersById.get(sellerId);
                if (seller == null) throw new NoSuchElementException("Không tìm thấy Seller");
                seller.setBalance(seller.getBalance().add(amount));
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Không ghi được thanh toán vào sổ ví", ex);
        }
    }

    private User createUser(String username, UserRole role) {
        return switch (role) {
            case BIDDER -> new Bidder(username);
            case SELLER -> new Seller(username);
            case ADMIN -> new Admin(username);
        };
    }

    private User createUser(String id, java.time.Instant createdAt, String username, UserRole role) {
        return switch (role) {
            case BIDDER -> new Bidder(id, createdAt, username);
            case SELLER -> new Seller(id, createdAt, username);
            case ADMIN -> new Admin(id, createdAt, username);
        };
    }

    private String normalize(String username) { return username.trim().toLowerCase(Locale.ROOT); }
}
