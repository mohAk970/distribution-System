package node;

import rmi.CoordinatorService;

import java.rmi.Naming;
import java.rmi.registry.LocateRegistry;

public class Node {

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java Node <nodeName> <port> <storagePath>");
            return;
        }

        String nodeName = args[0];

        int port = Integer.parseInt(args[1]);

        String storagePath = args[2];


        try {
            LocateRegistry.createRegistry(port);
            System.out.println("[Node-" + nodeName + "] RMI registry started on port " + port);
            FileServiceImpl service = new FileServiceImpl(nodeName, storagePath);
            String serviceURL = "rmi://localhost:" + port + "/" + nodeName;
            Naming.rebind(serviceURL, service);
            System.out.println("[Node-" + nodeName + "] FileService bound at " + serviceURL);
            CoordinatorService coordinator = (CoordinatorService) Naming.lookup("rmi://localhost:1099/Coordinator");
            coordinator.registerNode(nodeName, service);
            System.out.println("[Node-" + nodeName + "] Registered with Coordinator.");
        } catch (Exception e) {
            System.err.println("[Node-" + nodeName + "] Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}