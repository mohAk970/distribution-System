package node;
import rmi.CoordinatorService;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.Socket;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import rmi.FileService;

public class SyncClient {

    private static final int PORT = 5000;

    public static void syncFolder(String nodeFolder) {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            CoordinatorService coordinator = (CoordinatorService) registry.lookup("Coordinator");

            List<FileService> healthyNodes = coordinator.getHealthyNodes();
            if (healthyNodes.size() < 3) {
                System.out.println("️ Sync aborted from [" + nodeFolder + "]: All 3 nodes must be ONLINE.");
                return;
            }

            File rootDir = new File(nodeFolder);
            if (!rootDir.exists() || !rootDir.isDirectory()) {
                System.out.println(" Directory not found: " + nodeFolder);
                return;
            }

            for (File sectionDir : rootDir.listFiles()) {
                if (sectionDir.isDirectory()) {
                    String section = sectionDir.getName();

                    for (File file : sectionDir.listFiles()) {
                        if (file.isFile()) {
                            sendFile(section, file);
                        }
                    }
                }
            }

        } catch (Exception e) {
            System.err.println(" SyncClient error: " + e.getMessage());
        }
    }

    private static void sendFile(String section, File file) {
        try (Socket socket = new Socket("localhost", PORT);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             FileInputStream fis = new FileInputStream(file)) {

            out.writeUTF(section);
            out.writeUTF(file.getName());
            out.writeLong(file.length());

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) > 0) {
                out.write(buffer, 0, bytesRead);
            }

            System.out.println("[SyncClient] Sent: " + section + "/" + file.getName());

        } catch (Exception e) {
            System.err.println(" Failed to send file: " + file.getName() + " - " + e.getMessage());
        }
    }
}