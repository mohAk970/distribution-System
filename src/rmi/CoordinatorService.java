package rmi;

import auth.User;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;


public interface CoordinatorService extends Remote {
    void registerNode(String nodeName, FileService nodeService) throws RemoteException;
    boolean registerUser(User user) throws RemoteException;
    String login(String username, String password) throws RemoteException;
    User getUserByToken(String token) throws RemoteException;
    User getUserInfo(String token) throws RemoteException;

    byte[] readFile(String token, String fileName) throws RemoteException;
    boolean writeFile(String token, String fileName, byte[] data) throws RemoteException;
    boolean deleteFile(String token, String fileName) throws RemoteException;
    boolean editFile(String token, String filename, byte[] newData) throws RemoteException;

    byte[] requestFile(String token, String filename) throws RemoteException;
    List<FileService> getHealthyNodes() throws RemoteException;


    void logout(String token) throws RemoteException; // ✅ جديد

   // List<String> listUserFiles(String token) throws RemoteException;


}
