package node;
import rmi.FileService;
import java.io.*;
import java.nio.file.*;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;

public class FileServiceImpl extends UnicastRemoteObject implements FileService {

    private final String nodeName;
    private final Path baseDirectory;

    public FileServiceImpl(String nodeName, String baseDirectoryPath) throws RemoteException {
        super();
        this.nodeName = nodeName;
        this.baseDirectory = Paths.get(baseDirectoryPath);
        if (!Files.exists(baseDirectory)) {
            try {
                Files.createDirectories(baseDirectory);
            } catch (IOException e) {
                System.err.println("[Node-" + nodeName + "] Failed to create base directory.");
                e.printStackTrace();
            }
        }
        createSectionFolders();
    }

    @Override
    public List<String> listFiles() throws RemoteException {
        List<String> files = new ArrayList<>();
        try (DirectoryStream<Path> sectionDirs = Files.newDirectoryStream(baseDirectory)) {
            for (Path sectionPath : sectionDirs) {
                if (Files.isDirectory(sectionPath)) {
                    String section = sectionPath.getFileName().toString();
                    try (DirectoryStream<Path> fileStream = Files.newDirectoryStream(sectionPath)) {
                        for (Path file : fileStream) {
                            if (Files.isRegularFile(file)) {
                                files.add(section + "/" + file.getFileName().toString());
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return files;
    }


    @Override
    public byte[] readFile(String fileName) throws RemoteException {
        Path filePath = resolveSectionPath(fileName);
        synchronized (filePath.toString().intern()) {
            try {
                return Files.readAllBytes(filePath);
            } catch (IOException e) {
                throw new RemoteException("Error reading file: " + fileName, e);
            }
        }
    }

    @Override
    public void writeFile(String fileName, byte[] data) throws RemoteException {
        Path filePath = resolveSectionPath(fileName);
        synchronized (filePath.toString().intern()) {
            try {
                Files.write(filePath, data);
            } catch (IOException e) {
                throw new RemoteException("Error writing file: " + fileName, e);
            }
        }
    }

    @Override
    public void deleteFile(String fileName) throws RemoteException {
        Path filePath = resolveSectionPath(fileName);
        synchronized (filePath.toString().intern()) {
            try {
                Files.deleteIfExists(filePath);
            } catch (IOException e) {
                throw new RemoteException("Error deleting file: " + fileName, e);
            }
        }
    }

    @Override
    public boolean fileExists(String fileName) throws RemoteException {
        Path filePath = resolveSectionPath(fileName);
        synchronized (filePath.toString().intern()) {
            return Files.exists(filePath);
        }
    }



    @Override
    public String getNodeName() throws RemoteException {
        return nodeName;
    }

    private void createSectionFolders() {
        String[] sections = {"qa", "dev", "design"};
        for (String section : sections) {
            Path sectionPath = baseDirectory.resolve(section);
            try {
                if (!Files.exists(sectionPath)) {
                    Files.createDirectories(sectionPath);
                    System.out.println("[Node-" + nodeName + "] Created section folder: " + sectionPath);
                }
            } catch (IOException e) {
                System.err.println("[Node-" + nodeName + "] Failed to create section folder: " + section);
                e.printStackTrace();
            }
        }
    }

    private Path resolveSectionPath(String fileName) throws RemoteException {
        String[] parts = fileName.split("/", 2);
        if (parts.length != 2) {
            throw new RemoteException("Invalid file name format. Use: <department>/<filename>");
        }
        return baseDirectory.resolve(parts[0]).resolve(parts[1]);
    }
}
