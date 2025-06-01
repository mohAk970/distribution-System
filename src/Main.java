import auth.User;
import node.SyncClient;
import rmi.CoordinatorService;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.util.*;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        try {
            CoordinatorService coordinator = (CoordinatorService) Naming.lookup("rmi://localhost:1099/Coordinator");
            Scanner scanner = new Scanner(System.in);

            System.out.print("Enter role (manager/employee): ");
            String role = scanner.nextLine().trim().toLowerCase();

            if (role.equals("manager")) {
                handleManagerMenu(scanner, coordinator);
            } else if (role.equals("employee")) {
                handleEmployeeMenu(scanner, coordinator);
            } else {
                System.out.println(" Invalid role.");
            }

        } catch (Exception e) {
            System.err.println(" Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleManagerMenu(Scanner scanner, CoordinatorService coordinator) {
        try {
            System.out.print("Username: ");
            String username = scanner.nextLine();
            System.out.print("Password: ");
            String password = scanner.nextLine();

            String token = coordinator.login(username, password);

            while (true) {
                System.out.println("\n[Manager Menu]\n1. Register new employee\n2. Sync files manually\n3. Exit");
                System.out.print("Choose an option: ");
                String choice = scanner.nextLine();

                switch (choice) {
                    case "1":
                        try {
                            System.out.print("Username: ");
                            String newUser = scanner.nextLine();
                            System.out.print("Password: ");
                            String newPass = scanner.nextLine();
                            System.out.print("Role (employee): ");
                            String role = scanner.nextLine().toLowerCase();

                            if (!role.equals("employee")) {
                                System.out.println("Only employees can be registered.");
                                break;
                            }

                            System.out.print("Department (qa/dev/design): ");
                            String department = scanner.nextLine().toLowerCase();
                            System.out.print("Permissions (comma-separated: read,write,delete,edit): ");
                            String permissions = scanner.nextLine();

                            List<String> permsList = Arrays.asList(permissions.split(","));
                            User user = new User(newUser, newPass, role, department, "", permsList);

                            if (coordinator.registerUser(user)) {
                                System.out.println(" User registered.");
                            } else {
                                System.out.println("User registration failed. Possibly username already exists.");
                            }

                        } catch (Exception e) {
                            System.err.println(" Manager error: " + e.getMessage());
                        }
                        break;

                    case "2":
                        System.out.println(" Syncing all nodes manually...");
                        SyncClient.syncFolder("storage/Node1");
                        SyncClient.syncFolder("storage/Node2");
                        SyncClient.syncFolder("storage/Node3");

                        try {
                            sync.SyncServer.distributeToNodes();
                            sync.SyncServer.deleteSyncStorage();
                            System.out.println(" Manual sync completed and cleaned.");
                        } catch (Exception e) {
                            System.err.println(" Manual sync failed: " + e.getMessage());
                        }
                        break;

                    case "3":
                        coordinator.logout(token);
                        System.out.println(" Logged out.");
                        return;

                    default:
                        System.out.println(" Invalid option.");
                }
            }

        } catch (Exception e) {
            System.err.println(" Manager login/session error: " + e.getMessage());
        }
    }

    private static void handleEmployeeMenu(Scanner scanner, CoordinatorService coordinator) {
        try {
            System.out.print("Username: ");
            String username = scanner.nextLine();
            System.out.print("Password: ");
            String password = scanner.nextLine();

            String token = coordinator.login(username, password);
            System.out.println(" Login successful. Token: " + token);

            User user = coordinator.getUserInfo(token);
            Set<String> perms = new HashSet<>(user.getPermissions());

            while (true) {
                System.out.println("\n[Employee Menu]");
                int optionNumber = 1;
                Map<Integer, String> menu = new LinkedHashMap<>();

                if (perms.contains("write")) {
                    System.out.println(optionNumber + ". Add a file");
                    menu.put(optionNumber++, "write");
                }
                if (perms.contains("read")) {
                    System.out.println(optionNumber + ". Read a file");
                    menu.put(optionNumber++, "read");
                }
                if (perms.contains("delete")) {
                    System.out.println(optionNumber + ". Delete a file");
                    menu.put(optionNumber++, "delete");
                }
                if (perms.contains("edit")) {
                    System.out.println(optionNumber + ". Edit a file");
                    menu.put(optionNumber++, "edit");
                }
                System.out.println(optionNumber + ". Request a file from another department");
                menu.put(optionNumber++, "request");
                System.out.println(optionNumber + ". Logout");
                menu.put(optionNumber, "logout");

                System.out.print("Choose an option: ");
                int choice = Integer.parseInt(scanner.nextLine());

                String action = menu.get(choice);
                if (action == null) {
                    System.out.println(" Invalid option.");
                    continue;
                }

                switch (action) {
                    case "write":
                        System.out.print("Enter file name (section/filename.txt): ");
                        String writeName = scanner.nextLine();
                        System.out.print("Enter file content: ");
                        String writeContent = scanner.nextLine();
                        try {
                            boolean written = coordinator.writeFile(token, writeName, writeContent.getBytes());
                            System.out.println(written ? " File written." : " Write failed.");
                        } catch (Exception e) {
                            System.out.println(" Write error: " + e.getMessage());
                        }
                        break;

                    case "read":
                        System.out.print("Enter file name (section/filename.txt): ");
                        String readName = scanner.nextLine();
                        try {
                            byte[] data = coordinator.readFile(token, readName);
                            System.out.println(" File content:\n" + new String(data));
                        } catch (Exception e) {
                            System.out.println(" Read error: " + e.getMessage());
                        }
                        break;

                    case "delete":
                        System.out.print("Enter file name (section/filename.txt): ");
                        String deleteName = scanner.nextLine();
                        try {
                            boolean deleted = coordinator.deleteFile(token, deleteName);
                            System.out.println(deleted ? " File deleted." : " Delete failed.");
                        } catch (Exception e) {
                            System.out.println("Delete error: " + e.getMessage());
                        }
                        break;

                    case "edit":
                        System.out.print("Enter file name to edit (section/filename.txt): ");
                        String editFile = scanner.nextLine();

                        byte[] existingBytes;
                        try {
                            existingBytes = coordinator.readFile(token, editFile);
                        } catch (Exception e) {
                            System.out.println(" Read error: " + e.getMessage());
                            break;
                        }

                        List<String> lines = new ArrayList<>(List.of(new String(existingBytes).split("\n")));

                        while (true) {
                            System.out.println("\n Current content:");
                            for (int i = 0; i < lines.size(); i++) {
                                System.out.printf("%d: %s%n", i + 1, lines.get(i));
                            }

                            System.out.println("\nOptions: [a] Add line, [d] Delete line, [r] Replace line, [s] Save & Exit");
                            System.out.print("Choice: ");
                            String cmd = scanner.nextLine().trim();

                            switch (cmd) {
                                case "a":
                                    System.out.print("Enter new line to add: ");
                                    lines.add(scanner.nextLine());
                                    break;
                                case "d":
                                    System.out.print("Enter line number to delete: ");
                                    int del = Integer.parseInt(scanner.nextLine()) - 1;
                                    if (del >= 0 && del < lines.size()) lines.remove(del);
                                    break;
                                case "r":
                                    System.out.print("Enter line number to replace: ");
                                    int rep = Integer.parseInt(scanner.nextLine()) - 1;
                                    if (rep >= 0 && rep < lines.size()) {
                                        System.out.print("Enter new content: ");
                                        lines.set(rep, scanner.nextLine());
                                    }
                                    break;
                                case "s":
                                    String updated = String.join("\n", lines);
                                    try {
                                        boolean edited = coordinator.editFile(token, editFile, updated.getBytes());
                                        System.out.println(edited ? "File updated." : " Update failed.");
                                    } catch (Exception e) {
                                        System.out.println(" Update error: " + e.getMessage());
                                    }
                                    break;
                                default:
                                    System.out.println(" Invalid option.");
                            }
                            if (cmd.equals("s")) break;
                        }
                        break;

                    case "request":
                        System.out.print("Enter file name (just the file name, e.g. report.txt): ");
                        String reqName = scanner.nextLine();
                        try {
                            byte[] data = coordinator.requestFile(token, reqName);
                            System.out.println(" File content:\n" + new String(data));
                        } catch (RemoteException e) {
                            switch (e.getMessage()) {
                                case "ACCESS_DENIED_SELF_SECTION":
                                    System.out.println(" You cannot request files from your own department unless you have READ permission.");
                                    break;
                                case "FILE_NOT_FOUND":
                                    System.out.println(" File not found in any department. Please check the file name.");
                                    break;
                                default:
                                    System.out.println(" Request failed: " + e.getMessage());
                            }
                        }
                        break;

                    case "logout":
                        coordinator.logout(token);
                        System.out.println(" Logged out successfully.");
                        return;
                }
            }

        } catch (Exception e) {
            System.err.println(" Login or session error: " + e.getMessage());
        }
    }
}
