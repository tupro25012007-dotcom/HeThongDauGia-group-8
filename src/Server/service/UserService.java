package Server.service;

import Server.repository.AuctionStore;
import Shared.exception.AuctionException;
import Shared.model.User;
import java.security.*;
import java.sql.SQLException;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class UserService {
    private final AuctionStore store;
    private final SecureRandom random = new SecureRandom();
    public UserService(AuctionStore store) { this.store = store; }

    public User register(String name, String password, User.Role role) throws SQLException {
        if (normalize(name).equals("admin")) throw new AuctionException("Tên này dành cho quản trị hệ thống.");
        if (role == User.Role.ADMIN) throw new AuctionException("Chỉ máy chủ được tạo tài khoản quản trị.");
        return create(name, password, role);
    }
    public synchronized User create(String name, String password, User.Role role) throws SQLException {
        name = normalize(name);
        if (!name.matches("[a-z0-9_.-]{3,32}"))
            throw new AuctionException("Tên tài khoản cần 3–32 ký tự thường, số, dấu chấm, gạch ngang hoặc gạch dưới.");
        if (store.account(name) != null) throw new AuctionException("Tên tài khoản đã được sử dụng.");
        store.addAccount(credentials(name, password, role));
        return User.of(name, role);
    }
    private AuctionStore.Account credentials(String name, String password, User.Role role) {
        if (password.length() < 8 || password.length() > 128)
            throw new AuctionException("Mật khẩu cần từ 8 đến 128 ký tự.");
        byte[] salt = new byte[16]; random.nextBytes(salt);
        var enc = Base64.getEncoder();
        return new AuctionStore.Account(name, role, enc.encodeToString(salt), enc.encodeToString(hash(password, salt)));
    }
    public void rename(User user, String display) throws SQLException {
        display = display.strip();
        if (display.isEmpty() || display.length() > 60 || display.chars().anyMatch(Character::isISOControl))
            throw new AuctionException("Tên hiển thị cần từ 1 đến 60 ký tự.");
        store.displayName(user.id(), display);
    }
    public void changePassword(User user, String current, String next) throws SQLException {
        login(user.id(), current);
        if (current.equals(next)) throw new AuctionException("Chọn mật khẩu khác mật khẩu hiện tại.");
        store.password(credentials(user.id(), next, user.role()));
    }
    public User login(String name, String password) throws SQLException {
        var account = store.account(normalize(name));
        if (password.length() > 128 || account == null || !MessageDigest.isEqual(Base64.getDecoder().decode(account.hash()),
                hash(password, Base64.getDecoder().decode(account.salt()))))
            throw new AuctionException("Tên đăng nhập hoặc mật khẩu chưa đúng.");
        if (store.profiles(account.name()).getFirst().locked()) throw new AuctionException("Tài khoản đã bị khóa.");
        return User.of(account.name(), account.role());
    }
    private static String normalize(String value) { return value.strip().toLowerCase(Locale.ROOT); }
    private static byte[] hash(String password, byte[] salt) {
        var spec = new PBEKeySpec(password.toCharArray(), salt, 210_000, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (GeneralSecurityException ex) { throw new IllegalStateException(ex); }
        finally { spec.clearPassword(); }
    }
}
