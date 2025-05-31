package rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface FileService extends Remote {
    List<String> listFiles() throws RemoteException;

    byte[] readFile(String fileName) throws RemoteException;

    void writeFile(String fileName, byte[] data) throws RemoteException;

    void deleteFile(String fileName) throws RemoteException;

    boolean fileExists(String fileName) throws RemoteException;

    String getNodeName() throws RemoteException;
}
