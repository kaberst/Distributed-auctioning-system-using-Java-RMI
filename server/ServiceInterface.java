import java.rmi.RemoteException;
import java.rmi.Remote;
import javax.crypto.*;
import java.util.*;

/* Interface used for remote access to the server's facilities. */
public interface ServiceInterface extends Remote
{
    public ArrayList<SealedObject> getAllSpec(SealedObject userID) throws RemoteException;
    public SealedObject getSpec(SealedObject clientRequest) throws RemoteException;
    public Integer addAuction(SealedObject clientRequest) throws RemoteException;
    public Map<Integer, String> closeAuction(SealedObject clientRequest) throws RemoteException;
    public Double getCurrentPrice(SealedObject clientRequest) throws RemoteException;
    public Integer bid(SealedObject clientRequest) throws RemoteException;
    public SealedObject grantUserAccess(SealedObject challenge) throws RemoteException;
    public byte[] getUserChallenge(String challenge) throws RemoteException;
    public SealedObject getServerChallenge(String queriedUsername) throws RemoteException;
    public boolean validateUserIntegrity(SealedObject signature) throws RemoteException;
    public boolean isNewUser(SealedObject request) throws RemoteException;
}