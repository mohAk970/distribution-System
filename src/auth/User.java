package auth;

import java.io.Serializable;
import java.util.List;


public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private String username;
    private String password;
    private String role;

    private String department;
    private String token;
    private List<String> permissions;

    public User(String username, String password, String role, String department, String token, List<String> permissions) {
        this.username = username;
        this.password = password;
        this.role = role;
        this.department = department;
        this.token = token;
        this.permissions = permissions;
    }

    // Getters
    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getRole() {
        return role;
    }

    public String getDepartment() {
        return department;
    }

    public String getToken() {
        return token;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    // Setters
    public void setToken(String token) {
        this.token = token;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }

    @Override
    public String toString() {
        return username + " | " + role + " | " + department + " | " + permissions;
    }
}
