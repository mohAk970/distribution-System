package auth;

import java.io.*;
import java.util.*;

public class UserManager {

    private static final String FILE_PATH = "data/users.txt";
    private final Map<String, User> users = new HashMap<>();

    public UserManager() {
        loadUsers();
    }

    public void loadUsers() {
        File file = new File("data/users.txt");
        if (!file.exists()) return;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(";");
                if (parts.length < 3) continue;

                String username = parts[0];
                String password = parts[1];
                String role = parts[2];

                String department = parts.length > 3 ? parts[3] : "";
                String token = parts.length > 4 ? parts[4] : "";
                String permissionsString = parts.length > 5 ? parts[5] : "";

                List<String> permissions = new ArrayList<>();
                if (!permissionsString.isEmpty()) {
                    permissions = Arrays.asList(permissionsString.split(","));
                }

                // تحقق إضافي: إذا كان موظفًا، يجب أن يكون له قسم وصلاحيات
                if (role.equals("employee")) {
                    if (department.isEmpty() || permissions.isEmpty()) {
                        System.err.println("️ Ignoring invalid employee entry (missing department or permissions): " + username);
                        continue;
                    }
                }

                User user = new User(username, password, role, department, token, permissions);
                users.put(username, user);
            }

        } catch (IOException e) {
            System.err.println("❌ Error loading users: " + e.getMessage());
            e.printStackTrace();
        }
    }
    public void saveUsers() {
        File file = new File("data/users.txt");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            for (User user : users.values()) {
                if (user.getRole().equals("manager")) {
                    writer.write(String.format("%s;%s;%s;;;%n",
                            user.getUsername(),
                            user.getPassword(),
                            user.getRole()
                    ));
                } else {
                    String permissionsStr = String.join(",", user.getPermissions());
                    writer.write(String.format("%s;%s;%s;%s;%s;%s%n",
                            user.getUsername(),
                            user.getPassword(),
                            user.getRole(),
                            user.getDepartment(),
                            user.getToken() == null ? "" : user.getToken(),
                            permissionsStr
                    ));
                }
            }
        } catch (IOException e) {
            System.err.println("❌ Error saving users: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void saveUsersPublic() {
        saveUsers();
    }



    public boolean registerUser(User user) {
        if (users.containsKey(user.getUsername())) {
            System.out.println("[UserManager] Username already exists: " + user.getUsername());
            return false;
        }

        users.put(user.getUsername(), user);
        return true;
    }


    public User login(String username, String password) {
        User user = users.get(username);
        if (user != null && user.getPassword().equals(password)) {
            String token = UUID.randomUUID().toString();
            user.setToken(token);
            saveUsers();
            return user;
        }
        return null;
    }
    public User getUserByToken(String token) {
        for (User user : users.values()) {
            if (token != null && token.equals(user.getToken())) {
                return user;
            }
        }
        return null;
    }


}
