package coordinator;
import auth.User;
import auth.UserManager;
import rmi.CoordinatorService;
import rmi.FileService;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.Naming;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Coordinator extends UnicastRemoteObject implements CoordinatorService {

    private final Map<String, FileService> registeredNodes = new ConcurrentHashMap<>();
    private final Map<String, Boolean> nodeHealthStatus = new ConcurrentHashMap<>();
    private final UserManager userManager = new UserManager();
    private final Map<String, Integer> nodeRequestCount = new HashMap<>();

    public Coordinator() throws RemoteException {
        super();
        startNodeHealthMonitor();
    }

    private void startNodeHealthMonitor() {
        Thread healthMonitor = new Thread(() -> {
            while (true) {
                try {
                    checkAllNodesHealth();
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        healthMonitor.setDaemon(true);
        healthMonitor.start();
        System.out.println("[Coordinator] Node health monitor started.");
    }

    private void checkAllNodesHealth() {
        for (String nodeName : new HashSet<>(registeredNodes.keySet())) {
            try {
                FileService node = registeredNodes.get(nodeName);
                if (node != null) {
                    node.getNodeName();
                    nodeHealthStatus.put(nodeName, true);
                }
            } catch (Exception e) {
                if (nodeHealthStatus.getOrDefault(nodeName, true)) {
                    System.out.println("[Health Monitor] Node " + nodeName + " went offline.");
                }
                nodeHealthStatus.put(nodeName, false);
            }
        }
    }

    public List<FileService> getHealthyNodes() {
        List<FileService> healthyNodes = new ArrayList<>();
        for (Map.Entry<String, FileService> entry : registeredNodes.entrySet()) {
            String nodeName = entry.getKey();
            FileService node = entry.getValue();
            if (isNodeHealthy(nodeName, node)) {
                healthyNodes.add(node);
            }
        }
        return healthyNodes;
    }

    private boolean isNodeHealthy(String nodeName, FileService node) {
        try {
            node.getNodeName();
            nodeHealthStatus.put(nodeName, true);
            return true;
        } catch (Exception e) {
            if (nodeHealthStatus.getOrDefault(nodeName, true)) {
                System.out.println("[Node Check] Node " + nodeName + " is currently offline.");
            }
            nodeHealthStatus.put(nodeName, false);
            return false;
        }
    }

    @Override
    public synchronized void registerNode(String nodeName, FileService nodeService) throws RemoteException {
        registeredNodes.put(nodeName, nodeService);
        nodeHealthStatus.put(nodeName, true);
        System.out.println("[Coordinator] Registered node: " + nodeName);
    }

    @Override
    public synchronized boolean registerUser(User user) throws RemoteException {
        boolean result = userManager.registerUser(user);
        if (result) {
            userManager.saveUsers();
            userManager.loadUsers();
            System.out.println("[Coordinator] Registered user: " + user.getUsername());
        } else {
            System.out.println("[Coordinator] Failed to register user.");
        }
        return result;
    }

    @Override
    public synchronized String login(String username, String password) throws RemoteException {
        User user = userManager.login(username, password);
        if (user != null) {
            return user.getToken();
        }
        throw new RemoteException("Invalid username or password.");
    }

    @Override
    public synchronized User getUserByToken(String token) throws RemoteException {
        return userManager.getUserByToken(token);
    }

    @Override
    public synchronized byte[] readFile(String token, String filename) throws RemoteException {
        User user = userManager.getUserByToken(token);
        if (user == null) throw new RemoteException(" Invalid token.");
        if (!user.getPermissions().contains("read")) throw new RemoteException("❌ Access denied: read.");

        String[] parts = filename.split("/", 2);
        if (parts.length != 2 || !user.getDepartment().equals(parts[0]))
            throw new RemoteException(" Access denied: department mismatch.");

        List<FileService> healthyNodes = getHealthyNodes();
        if (healthyNodes.isEmpty()) {
            throw new RemoteException(" No healthy nodes available.");
        }

        for (FileService node : healthyNodes) {
            try {
                if (node.fileExists(filename)) {
                    System.out.println(" Reading from healthy node: " + node.getNodeName());
                    return node.readFile(filename);
                }
            } catch (Exception e) {
                continue;
            }
        }

        throw new RemoteException(" File not found in any healthy node.");
    }

    @Override
    public synchronized boolean writeFile(String token, String fileName, byte[] data) throws RemoteException {
        User user = userManager.getUserByToken(token);
        if (user == null || !user.getPermissions().contains("write")) return false;

        String[] parts = fileName.split("/", 2);
        if (parts.length != 2 || !user.getDepartment().equalsIgnoreCase(parts[0])) return false;

        List<FileService> healthyNodes = getHealthyNodes();
        if (healthyNodes.isEmpty()) {
            System.err.println("❌ No healthy nodes available for writing.");
            return false;
        }

        Collections.shuffle(healthyNodes);

        for (FileService node : healthyNodes) {
            try {
                node.writeFile(fileName, data);
                System.out.println(" Written to healthy node: " + node.getNodeName());
                return true;
            } catch (Exception e) {
                continue;
            }
        }

        System.err.println(" Failed to write to any healthy node.");
        return false;
    }

    @Override
    public synchronized boolean deleteFile(String token, String filename) throws RemoteException {
        User user = userManager.getUserByToken(token);
        if (user == null || !user.getPermissions().contains("delete")) return false;

        String[] parts = filename.split("/", 2);
        if (parts.length != 2 || !user.getDepartment().equalsIgnoreCase(parts[0])) return false;

        List<FileService> healthyNodes = getHealthyNodes();
        boolean deleted = false;

        for (FileService node : healthyNodes) {
            try {
                if (node.fileExists(filename)) {
                    node.deleteFile(filename);
                    System.out.println("️ Deleted from healthy node: " + node.getNodeName());
                    deleted = true;
                }
            } catch (Exception e) {
                continue;
            }
        }

        return deleted;
    }

    @Override
    public synchronized boolean editFile(String token, String filename, byte[] newData) throws RemoteException {
        User user = userManager.getUserByToken(token);
        if (user == null || !user.getPermissions().contains("edit")) return false;

        String[] parts = filename.split("/", 2);
        if (parts.length != 2 || !user.getDepartment().equalsIgnoreCase(parts[0])) return false;

        List<FileService> healthyNodes = getHealthyNodes();
        boolean edited = false;

        for (FileService node : healthyNodes) {
            try {
                if (node.fileExists(filename)) {
                    node.writeFile(filename, newData);
                    System.out.println(" Edited file in healthy node: " + node.getNodeName());
                    edited = true;
                }
            } catch (Exception e) {
                continue;

            }
        }

        return edited;
    }
    @Override
    public synchronized byte[] requestFile(String token, String filename) throws RemoteException {
        User user = userManager.getUserByToken(token);
        if (user == null) throw new RemoteException("Invalid token.");

        String username = user.getUsername();
        String userSection = user.getDepartment();
        boolean hasReadPermission = user.getPermissions().contains("read");

        System.out.println("[Coordinator] " + username + " from [" + userSection + "] requested file: " + filename);

        List<FileService> healthyNodes = getHealthyNodes();
        if (healthyNodes.isEmpty()) {
            throw new RemoteException(" No healthy nodes available.");
        }

        List<FileService> sortedNodes = new ArrayList<>(healthyNodes);
        sortedNodes.sort(Comparator.comparingInt(n -> {
            try {
                return nodeRequestCount.getOrDefault(n.getNodeName(), 0);
            } catch (RemoteException e) {
                return Integer.MAX_VALUE;
            }
        }));

        for (FileService node : sortedNodes) {
            try {
                String nodeName = node.getNodeName();
                List<String> allFiles = node.listFiles();

                for (String path : allFiles) {
                    if (path.endsWith("/" + filename)) {
                        String section = path.split("/")[0];
                        if (section.equals(userSection) && !hasReadPermission) {
                            throw new RemoteException("ACCESS_DENIED_SELF_SECTION");
                        }
                        nodeRequestCount.put(nodeName, nodeRequestCount.getOrDefault(nodeName, 0) + 1);
                        System.out.println(" Found in [" + section + "] on healthy node: " + nodeName);
                        return node.readFile(path);
                    }
                }

            } catch (RemoteException e) {
                System.err.println(" Skipping node (RemoteException): " + e.getMessage());
            } catch (Exception e) {
                System.err.println(" Skipping node (Other error): " + e.getMessage());
            }
        }

        throw new RemoteException("FILE_NOT_FOUND");
    }

    @Override
    public synchronized User getUserInfo(String token) throws RemoteException {
        return userManager.getUserByToken(token);
    }

    @Override
    public synchronized void logout(String token) throws RemoteException {
        User user = userManager.getUserByToken(token);
        if (user != null) {
            user.setToken(null);
            userManager.saveUsersPublic();
            System.out.println("[Coordinator] User '" + user.getUsername() + "' logged out.");
        }
    }

    public void printSystemStatus() {
        System.out.println("\n=== System Status ===");
        System.out.println("Total registered nodes: " + registeredNodes.size());

        List<FileService> healthyNodes = getHealthyNodes();
        System.out.println("Healthy nodes: " + healthyNodes.size());

        for (Map.Entry<String, Boolean> entry : nodeHealthStatus.entrySet()) {
            String status = entry.getValue() ? " ONLINE" : " OFFLINE";
            System.out.println("Node " + entry.getKey() + ": " + status);
        }
        System.out.println("==================\n");
    }

    public static void main(String[] args) {
        try {
            System.out.println("[Coordinator] Starting RMI registry...");
            LocateRegistry.createRegistry(1099);

            Coordinator coordinator = new Coordinator();
            Naming.rebind("rmi://localhost:1099/Coordinator", coordinator);
            System.out.println("[Coordinator] Ready at rmi://localhost:1099/Coordinator");

            Timer statusTimer = new Timer(true);
            statusTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    coordinator.printSystemStatus();
                }
            }, 60000, 60000);

        } catch (Exception e) {
            System.err.println("[Coordinator] Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}