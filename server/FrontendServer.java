import java.security.*;
import java.security.KeyPair;
import java.util.*;
import java.io.IOException;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.DataInputStream;
import java.io.ObjectInputStream;
import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.io.InputStreamReader;
import java.rmi.RemoteException;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.security.Signature;

import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.MembershipListener;
import org.jgroups.View;
import org.jgroups.blocks.RequestOptions;
import org.jgroups.blocks.ResponseMode;
import org.jgroups.blocks.RpcDispatcher;
import org.jgroups.util.RspList;
import org.jgroups.blocks.MethodCall;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.function.Function;
import java.io.File;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jgroups.util.*;
import org.jgroups.ReceiverAdapter;

public class FrontendServer implements ServiceInterface, MembershipListener, Function
{
    private ArrayList<ClientUser> users = new ArrayList<ClientUser>();
    private KeyPair serverKeyPair;
    private String queriedUsername;
    private static String SERVER_CHALLENGE = "Hi, user";

    private JChannel channel;
    private RpcDispatcher dispatcher;
    private List<Method> methods;
    private Address mostReliableMember;

    /**
     * Constructor that initializes the server and instanciates the public-private key pair for the server.
     */
    public FrontendServer()
    {
        try
        {
            KeyPairGenerator serverKeyPairGen = KeyPairGenerator.getInstance("Ed25519");

            serverKeyPair = serverKeyPairGen.generateKeyPair();
            writeKey(serverKeyPair);     

            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            SecretKey key = keyGen.generateKey();
            writeOneTimeKey(key, "secret-key.txt");

            methods = Arrays.asList(ServiceInterfaceImplementation.class.getDeclaredMethods());

            channel = new JChannel();
            channel.connect("AuctionSystem");
            dispatcher = new RpcDispatcher(channel, this);
            dispatcher.setMembershipListener(this);

            Process p = Runtime.getRuntime().exec("sh initBackend.sh");
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    public void viewAccepted(View newView) 
    {
        System.out.printf("New view: " + newView.toString());

    }

    public void suspect(Address suspectedMember)
    {

        System.out.printf("Member crash: " + suspectedMember.toString());

        try
        {
            Process p = Runtime.getRuntime().exec("sh addBackend.sh");
        }        
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }
    
    public void block() 
    {
        System.out.printf("Block indicator\n");
    }
    
    public void unblock() 
    {
        System.out.printf("Unblock indicator\n");
    }

    public void incrementReliability()
    {
        try
        {
            MethodCall remoteCall = null;
            for(Method m : methods)
            {
                if("incrementReliability".equals(m.getName()))
                {
                    remoteCall = new MethodCall(m);
                    break;
                }
            }
            Collection<Address> reliable = new ArrayList<Address>(Arrays.asList(mostReliableMember));
            RspList<Void> responsesFromBackends = dispatcher.callRemoteMethods(reliable, remoteCall ,new RequestOptions(ResponseMode.GET_ALL, 3000));
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }
    
    public boolean isNewUser(SealedObject request) 
    {
        ClientRequest unsealedRequest = decrypt(request, readKey());
        queriedUsername = unsealedRequest.getUsername();
        for(ClientUser index : users)
        {
            if(index.getUsername().equals(queriedUsername))
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Sends a challenge to the client and the private key of the queried user.
     */
    public synchronized SealedObject getServerChallenge(String queriedUsername)
    {
        ClientUser queriedUser = null;
        for(ClientUser index : users)
        {
            if(index.getUsername().equals(queriedUsername))
            {
                
                queriedUser = index;
                break;
            }
        }

        if(queriedUser != null)
        {
            try
            {
                String challenge = SERVER_CHALLENGE + "! Your key is in file:/home/diaconu/hdrive/SCC311/CW/Stage_3/server/user-private-key.txt";
                writeOneTimeKey(queriedUser.getUserPrivateKey(), "user-private-key.txt");
                
                SecretKey key = readKey();
                Cipher cipher = Cipher.getInstance("AES");
                cipher.init(Cipher.ENCRYPT_MODE, key);
                SealedObject challengeAndKeySealed = new SealedObject(challenge, cipher);
                return challengeAndKeySealed;
            }
            catch(Exception e)
            {
                e.getStackTrace();
            }
        }

        return null;
    }

    /**
     * Verifies that user's integrity is valid.
     */
    public boolean validateUserIntegrity(SealedObject signature)
    {
        ClientUser queriedUser = null;
        // Look up the user that requested access to the system.
        for(ClientUser index : users)
        {
            if(queriedUsername.equals(index.getUsername()))
            {
                queriedUser = index;
                break;
            }
        }
        // if no user is found return 'false' 
        if(queriedUser == null)
            return false;
        else
        {
            try
            {
                // Decrypt user signature
                byte[] userSignature = (byte[]) signature.getObject(readKey());

                // Verify signature
                Signature sign = Signature.getInstance("Ed25519");
                sign.initVerify(queriedUser.getUserPublicKey());
                sign.update(SERVER_CHALLENGE.getBytes());
                return sign.verify(userSignature);
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }

            return false;
        }
    }

    /**
     * Solve the user's challenge in order to validate server's integrity.
     */
    public synchronized byte[] getUserChallenge(String challenge)
    {
        try
        {
            // Initialise the server's signature and send it over to the client that requested the integrity check.
            Signature serverSign = Signature.getInstance("Ed25519");
            serverSign.initSign(serverKeyPair.getPrivate());
            serverSign.update(challenge.getBytes());
            byte[] signatureByteArray = serverSign.sign();
            
            return signatureByteArray;
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }

        return new byte[0];
    }

    /**
     * Send the user details of the client to the client who wants to log in as this user.
     */
    public synchronized SealedObject grantUserAccess(SealedObject challenge)
    {
        SecretKey key = readKey();
        ClientRequest request = decrypt(challenge, key);
        String username = request.getUsername();
        boolean isNewUser = true;

        ClientUser user = new ClientUser(username);

        for(int i = 0; i < users.size(); ++i)
        {
            if(username.equals(users.get(i).getUsername()))
            {
                isNewUser = false;
                System.out.println("USER WAS PREVIOUSLY LOGGED: " + users.get(i).getUsername());
                user = users.get(i);
                
                break;
            }
        }

        // If the user hasn't logged in before, create a new account for him with the given details and remember it.
        if(isNewUser)
        {
            user = new ClientUser(request.getUsername());
            user.setClientRole(request.getClientRole());
            user.setUserKey(key);
            user.setKeyPair();

            users.add(user);
        }

        return encryptClientUser(user);
    }

    /**
     * Return the current price for a given auction.
     */
    public Double getCurrentPrice(SealedObject clientRequest) throws RemoteException
    {
        try
        {
            MethodCall remoteCall = null;
            for(Method m : methods)
            {
                if("getCurrentPrice".equals(m.getName()))
                {
                    remoteCall = new MethodCall(m);
                    remoteCall.setArgs(clientRequest);
                    break;
                }
            }

            RspList<Double> responsesFromBackends = dispatcher.callRemoteMethods(null, remoteCall ,new RequestOptions(ResponseMode.GET_ALL, 3000));
            
            for(Double index : responsesFromBackends.getResults())
            {
                if(index != null)
                    return index;
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }


        return -1.0;
    }   

    /**
     * Method that handles biddings. It uses the keyword 'synchronized' in order to stop temperings.
     */
    public synchronized Integer bid(SealedObject clientRequest) throws RemoteException
    {
        try
        {
            MethodCall remoteCall = null;
            for(Method m : methods)
            {
                if("bid".equals(m.getName()))
                {
                    remoteCall = new MethodCall(m);
                    remoteCall.setArgs(clientRequest);
                    break;
                }
            }

            // Remote call on all replicas for responses
            RspList<Integer> responsesFromBackends = dispatcher.callRemoteMethods(null, remoteCall ,new RequestOptions(ResponseMode.GET_ALL, 3000));
            
            // Filter out the most common answer
            Map.Entry<List<Integer>,Long> mostCommon = Stream.of(responsesFromBackends.getResults())
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
            .entrySet()
            .stream()
            .max(Map.Entry.comparingByValue()).get();
            
            // Obtain address of most common answer
            Iterator<Rsp<Integer>> addressOfMostCommon = responsesFromBackends.values().iterator();

            while(addressOfMostCommon.hasNext())
            {
                Rsp<Integer> index = addressOfMostCommon.next();

                if(index.wasReceived() && !index.wasSuspected() && index.getValue() == mostCommon.getKey().get(0))
                {
                    mostReliableMember = index.getSender();
                    incrementReliability();
                    break;
                }
            }

            return  mostCommon.getKey().get(0);
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }


        return -1;
    }

    /**
     * Return a list containing all the active auctioned items.
     */
    public ArrayList<SealedObject> getAllSpec(SealedObject userID) throws RemoteException
    {
        try
        {
            MethodCall remoteCall = null;
            for(Method m : methods)
            {
                if("getAllSpec".equals(m.getName()))
                {
                    remoteCall = new MethodCall(m);
                    remoteCall.setArgs(userID);
                    break;
                }
            }
            // Remote call on all replicas for responses
            RspList<ArrayList<SealedObject>> responsesFromBackends = dispatcher.callRemoteMethods(null, remoteCall ,new RequestOptions(ResponseMode.GET_ALL, 3000));
            
            // Filter out the most common response
            Map.Entry<List<ArrayList<SealedObject>>,Long> mostCommon = Stream.of(responsesFromBackends.getResults())
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
            .entrySet()
            .stream()
            .max(Map.Entry.comparingByValue()).get();

            // Obtain address of most common answer
            Iterator<Rsp<ArrayList<SealedObject>>> addressOfMostCommon = responsesFromBackends.values().iterator();

            while(addressOfMostCommon.hasNext())
            {
                Rsp<ArrayList<SealedObject>> index = addressOfMostCommon.next();

                if(index.wasReceived() && !index.wasSuspected() && index.getValue() == mostCommon.getKey().get(0))
                {
                    mostReliableMember = index.getSender();
                    incrementReliability();
                    break;
                }
            }

            return  mostCommon.getKey().get(0);
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }

        return null;
    }

    /* Retreive an item by @param itemId */
    public SealedObject getSpec(SealedObject clientRequest) throws RemoteException
    {
        try
        {
            MethodCall remoteCall = null;
            for(Method m : methods)
            {
                if("getSpec".equals(m.getName()))
                {
                    remoteCall = new MethodCall(m);
                    remoteCall.setArgs(clientRequest);
                    break;
                }
            }
            // Remote call on all replicas for responses
            RspList<SealedObject> responsesFromBackends = dispatcher.callRemoteMethods(null, remoteCall ,new RequestOptions(ResponseMode.GET_ALL, 3000));
                              
            // Filter out the most common response                  
            Map.Entry<List<SealedObject>,Long> mostCommon = Stream.of(responsesFromBackends.getResults())
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
            .entrySet()
            .stream()
            .max(Map.Entry.comparingByValue()).get();

            // Obtain address of most common answer
            Iterator<Rsp<SealedObject>> addressOfMostCommon = responsesFromBackends.values().iterator();

            while(addressOfMostCommon.hasNext())
            {
                Rsp<SealedObject> index = addressOfMostCommon.next();

                if(index.wasReceived() && !index.wasSuspected() && index.getValue() == mostCommon.getKey().get(0))
                {
                    mostReliableMember = index.getSender();
                    incrementReliability();
                    break;
                }
            }
            return  mostCommon.getKey().get(0);
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Create a new auction with the given details.
     */
    public Integer addAuction(SealedObject clientRequest) throws RemoteException
    {
        try
        {
            MethodCall remoteCall = null;
            for(Method m : methods)
            {
                if("addAuction".equals(m.getName()))
                {
                    remoteCall = new MethodCall(m);
                    remoteCall.setArgs(clientRequest);
                    break;
                }
            }
            // Remote call on all replicas for responses
            RspList<Integer> responsesFromBackends = dispatcher.callRemoteMethods(null, remoteCall ,new RequestOptions(ResponseMode.GET_ALL, 3000));   
                     
            Map.Entry<List<Integer>,Long> mostCommon = Stream.of(responsesFromBackends.getResults())
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
            .entrySet()
            .stream()
            .max(Map.Entry.comparingByValue()).get();

            Iterator<Rsp<Integer>> addressOfMostCommon = responsesFromBackends.values().iterator();

            while(addressOfMostCommon.hasNext())
            {
                Rsp<Integer> index = addressOfMostCommon.next();

                if(index.wasReceived() && !index.wasSuspected() && index.getValue() == mostCommon.getKey().get(0))
                {
                    mostReliableMember = index.getSender();
                    incrementReliability();
                    break;
                }
            }
            return  mostCommon.getKey().get(0);
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }

        return -1;
    }

    /**
     * Close an active auction in the system.
     */
    public Map<Integer, String> closeAuction(SealedObject clientRequest) throws RemoteException
    {
        try
        {
            MethodCall remoteCall = null;
            for(Method m : methods)
            {
                if("closeAuction".equals(m.getName()))
                {
                    remoteCall = new MethodCall(m);
                    remoteCall.setArgs(clientRequest);
                    break;
                }
            }
            // Remote call on all replicas for responses
            RspList<Map<Integer, String>> responsesFromBackends = dispatcher.callRemoteMethods(null, remoteCall ,new RequestOptions(ResponseMode.GET_ALL, 3000));
            
                       
            Map.Entry<List<Map<Integer, String>>,Long> mostCommon = Stream.of(responsesFromBackends.getResults())
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
            .entrySet()
            .stream()
            .max(Map.Entry.comparingByValue()).get();

            Iterator<Rsp<Map<Integer, String>>> addressOfMostCommon = responsesFromBackends.values().iterator();

            while(addressOfMostCommon.hasNext())
            {
                Rsp<Map<Integer, String>> index = addressOfMostCommon.next();

                if(index.wasReceived() && !index.wasSuspected() && index.getValue() == mostCommon.getKey().get(0))
                {
                    mostReliableMember = index.getSender();
                    incrementReliability();
                    break;
                }
            }
            return  mostCommon.getKey().get(0);
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }

        return null;
    }

    /* Convert the secret key from binary to javax.crypto.SecretKey */
    public SecretKey readKey()
    {
        try 
        {
            FileInputStream fis = new FileInputStream("secret-key.txt");
            DataInputStream dis = new DataInputStream(fis);
            int len = dis.readInt();
            byte[] key = new byte[len];
            dis.read(key);

            SecretKey deserializedKey = new SecretKeySpec(key, "AES");

            return deserializedKey;
        } 
        catch (Exception e) 
        {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Encrypt the user details in order to send them back to the server.
     * @param user the user details
     * @return a sealed object containing the user's details.
     */
    public SealedObject encryptClientUser(ClientUser user)
    {
        try
        {
            SecretKey key = readKey();
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            SealedObject userEncrypted = new SealedObject(user, cipher);
            
            return userEncrypted;

        }
        catch(Exception e)
        {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Decrypt a request from the client's side using a secret key ('transaction' key).
     * @param object the client's request.
     * @param key the key used for encryption.
     * @return the deserialized request.
     */
    public ClientRequest decrypt(SealedObject object, SecretKey key)
    {
        try
        {
            return (ClientRequest) object.getObject(key);
        }
        catch(Exception ioe)
        {
            ioe.printStackTrace();
        }

        return null;
    }

    /**
     * Publish the server's public key.
     * @param key the server's public key.
     */
    public static synchronized void writeKey(KeyPair key)
    {
        try
        { 
            DataOutputStream dos = new DataOutputStream(new FileOutputStream("server-public-key.txt"));
            dos.write(key.getPublic().getEncoded());
            dos.flush();
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Publish the one time transaction key.
     * @param key the server's one time transaction key.
     */
    public static synchronized void writeOneTimeKey(Key key, String filePath)
    {
        try
        { 
            byte[] keyByte = key.getEncoded();
            DataOutputStream dos = new DataOutputStream(new FileOutputStream(filePath));
            dos.writeInt(keyByte.length);
            dos.write(keyByte);
            dos.close();
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }

    }

    public void exchangeStates(List<Method> methods)
    {
        try
        {
            MethodCall remoteCall = null;
            for(Method m : methods)
            {
                if("exchangeState".equals(m.getName()))
                {
                    remoteCall = new MethodCall(m);
                    remoteCall.setArgs(methods);
                    break;
                }
            }

            RspList<Void> responsesFromBackends = dispatcher.callRemoteMethods(null, remoteCall ,new RequestOptions(ResponseMode.GET_ALL, 3000));
            
            System.out.println("State of replicas has been changed!");
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public Object apply(Object t)
    {
        return t;
    }

}
