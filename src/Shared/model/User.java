package Shared.model;
//import Shared.exception.AuthenticationExcecption;
import Shared.model.UserRole;
import javax.naming.AuthenticationException;

public abstract class User extends Entity {
    private String username;
    private final UserRole role;
    protected User(String username, UserRole role) {
        super();
        if (username == null) {
            throw new IllegalArgumentException("Username không được để trống");
        }
        if (role == null) {
            throw new IllegalArgumentException("Role không được để trống");
        }
        this.username = username.trim();
        this.role = role;
    }
    public UserRole getRole() {
        return role;
    }
    public String getUsername() {
        return username;
    }
    @Override
    public void printInfo() {
        System.out.println("ID: " + getId());
        System.out.println("Username: " + username);
        System.out.println("Role: " + role);
        System.out.println("Created at: " + getCreatedAt());
    }
//    public void login(String password) throws AuthenticationException {
//        if (!this.password.equals(password)) {
//            throw new AuthenticationException("Mật khẩu không chính xác");
//        }
//        System.out.pritnln("Đăng nhập thành công");
//    }
}