package sync;
import node.SyncClient;
import rmi.CoordinatorService;
import rmi.FileService;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.time.LocalTime;
import java.util.*;

public class SyncServer {

    private static final int PORT = 5000;
    private static final String SYNC_DIR = "sync-storage";

    private static class FileVersion {
        byte[] data;
        long timestamp;

        FileVersion(byte[] data, long timestamp) {
            this.data = data;
            this.timestamp = timestamp;
        }
    }

    public static void main(String[] args) {
        startSocketServer();
        startAutoSyncThread();
    }

    private static void startSocketServer() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                System.out.println("[SyncServer] Listening on port " + PORT + "...");
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(() -> handleClient(clientSocket)).start();
                }
            } catch (IOException e) {
                System.err.println("[SyncServer] Error: " + e.getMessage());
            }
        }).start();
    }

    private static void handleClient(Socket socket) {
        try (DataInputStream in = new DataInputStream(socket.getInputStream())) {
            String section = in.readUTF();
            String fileName = in.readUTF();
            long fileLength = in.readLong();

            byte[] fileData = new byte[(int) fileLength];
            in.readFully(fileData);

            Path sectionPath = Paths.get(SYNC_DIR, section);
            Files.createDirectories(sectionPath);

            Path filePath = sectionPath.resolve(fileName);
            Files.write(filePath, fileData);

            System.out.println("[SyncServer] Received: " + section + "/" + fileName);
        } catch (IOException e) {
            System.err.println("[SyncServer] Client error: " + e.getMessage());
        }
    }

    public static void distributeToNodes() throws Exception {
        System.out.println(" [SyncServer] Starting distribution...");

        Registry registry = LocateRegistry.getRegistry("localhost", 1099);
        CoordinatorService coordinator = (CoordinatorService) registry.lookup("Coordinator");

        List<FileService> healthyNodes = coordinator.getHealthyNodes();
        if (healthyNodes.size() < 3) {
            System.out.println(" Skipping distribution: Not all 3 nodes are healthy.");
            return;
        }

        Map<String, FileVersion> latestFiles = new HashMap<>();

        Files.walk(Paths.get(SYNC_DIR))
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    try {
                        String relativePath = Paths.get(SYNC_DIR).relativize(path).toString().replace("\\", "/");
                        byte[] content = Files.readAllBytes(path);
                        long lastModified = Files.getLastModifiedTime(path).toMillis();

                        FileVersion existing = latestFiles.get(relativePath);
                        if (existing == null || lastModified > existing.timestamp) {
                            latestFiles.put(relativePath, new FileVersion(content, lastModified));
                        }
                    } catch (IOException e) {
                        System.err.println("⚠️ Error reading file: " + e.getMessage());
                    }
                });

        for (Map.Entry<String, FileVersion> entry : latestFiles.entrySet()) {
            String filePath = entry.getKey();
            FileVersion version = entry.getValue();

            for (FileService node : healthyNodes) {
                try {
                    node.writeFile(filePath, version.data);
                    System.out.println("📤 Synced " + filePath + " to node: " + node.getNodeName());
                } catch (Exception e) {
                    System.err.println("⚠️ Failed to sync " + filePath + " to node: " + node.getNodeName());
                }
            }
        }

        System.out.println("✅ [SyncServer] Distribution based on latest version completed.");
    }

    public static void deleteSyncStorage() throws IOException {
        Path syncPath = Paths.get(SYNC_DIR);
        if (Files.exists(syncPath)) {
            Files.walk(syncPath)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            System.out.println("? Deleted sync-storage folder.");
        }
    }

    private static void startAutoSyncThread() {
        new Thread(() -> {
            while (true) {
                try {
                    LocalTime now = LocalTime.now();
                    LocalTime target = LocalTime.of(23, 59);
                    long sleepMillis = java.time.Duration.between(now, target).toMillis();
                    if (sleepMillis < 0) {
                        sleepMillis += java.time.Duration.ofDays(1).toMillis();
                    }

                    System.out.println(" Waiting until 23:59 for auto-sync...");
                    Thread.sleep(sleepMillis);

                    System.out.println("[Auto-Sync] Sending files from all nodes...");

                    SyncClient.syncFolder("storage/Node1");
                    SyncClient.syncFolder("storage/Node2");
                    SyncClient.syncFolder("storage/Node3");

                    System.out.println(" [Auto-Sync] Distributing to all nodes...");
                    distributeToNodes();
                    deleteSyncStorage();

                    System.out.println(" [Auto-Sync] Completed and cleaned.");

                } catch (Exception e) {
                    System.err.println(" [Auto-Sync] Error: " + e.getMessage());
                }
            }
        }).start();
    }
}
