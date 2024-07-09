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

/* Class that implements the ServiceInterface's methods, handles client request sent by users and verifies integrity of clients. */

public class ServiceInterfaceImplementation
{
    private ArrayList<AuctionItem> availableItems = new ArrayList<AuctionItem>();
    private Map<String, Integer> logger = new HashMap<String, Integer>();

    private JChannel channel;
    private RpcDispatcher dispatcher;

    private Integer numberOfRequestsHandledCorrectly = 0;

    /**
     * Constructor that initializes the server and instanciates the public-private key pair for the server.
     */
    public ServiceInterfaceImplementation ()
    {
        try
        {
            channel = new JChannel();
            channel.connect("AuctionSystem");
            dispatcher = new RpcDispatcher(channel, this);

            
            List<Method> methods = Arrays.asList(ServiceInterfaceImplementation.class.getDeclaredMethods());

            exchangeState(methods);
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    public void exchangeState(List<Method> methods)
    {
        try
        {
            MethodCall remoteCall = null;
            for(Method m : methods)
            {
                if("getReliability".equals(m.getName()))
                {
                    remoteCall = new MethodCall(m);
                    break;
                }
            }
    
            RspList<Integer> responsesFromBackends = dispatcher.callRemoteMethods(null, remoteCall ,new RequestOptions(ResponseMode.GET_ALL, 3000));  
            
            if(responsesFromBackends.getResults() != null)
            {
                ArrayList<Rsp<Integer>> list = new ArrayList<Rsp<Integer>>(responsesFromBackends.values());
    
                List<Integer> listOfRequestsHandled = responsesFromBackends.getResults();
                Collections.sort(listOfRequestsHandled, Collections.reverseOrder());
    
                Address mostReliableAddress = null;
                for(Rsp<Integer> index : list)
                {
                   if(index.getValue() == listOfRequestsHandled.get(0))
                   {
                        mostReliableAddress = index.getSender();
                   }
                }
    
                for(Method m : methods)
                {
                    if("getState".equals(m.getName()))
                    {
                        remoteCall = new MethodCall(m);
                        break;
                    }
                }
                Collection<Address> c = new ArrayList<Address>();
                c.add(mostReliableAddress);
                RspList<ArrayList<AuctionItem>> mostReliableState = dispatcher.callRemoteMethods(c, remoteCall ,new RequestOptions(ResponseMode.GET_ALL, 3000));  
                
                this.setState(mostReliableState.getResults().get(0));

                System.out.println(availableItems);
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }

    }

    public void incrementReliablity()
    {
        numberOfRequestsHandledCorrectly += 1;
    }

    public Integer getReliability()
    {
        return numberOfRequestsHandledCorrectly;
    }

    public Address getAddress()
    {
        return channel.getAddress();
    }

    public ArrayList<AuctionItem> getItems()
    {
        return availableItems;
    }

    public Map<String, Integer> getLogger()
    {
        return logger;
    }

    public void setItems(ArrayList<AuctionItem> items)
    {
        availableItems = items;
    }

    public void setLogger(Map<String, Integer> logger)
    {
        this.logger = logger;
    }

    public ArrayList<AuctionItem> getState()
    {
        return availableItems;
    }

    public void setState(ArrayList<AuctionItem> state)
    {
        availableItems = state;

    }
    /**
     * Return the current price for a given auction.
     */
    public Double getCurrentPrice(SealedObject clientRequest) throws RemoteException
    {
        SecretKey key = readKey();
        ClientRequest clientRequestItem = decrypt(clientRequest, key);
        AuctionItem desiredItem = null;
        // Get the item requested by the user.
        for(int i = 0; i < availableItems.size(); ++i)
        {
            if(availableItems.get(i).getItemID().equals(clientRequestItem.getItemID()))
            {
                desiredItem = availableItems.get(i);    
                break;
            }
        }

        // If there is no item with given id, return null. Otherwise return the price of the item.
        if(desiredItem != null)
        {
            System.out.println("Item with ID: " + String.valueOf(clientRequestItem.getItemID()) + " has been checked for its price!");
            return desiredItem.getCurrentPrice();
        }
        else 
        {
            System.out.println("Item with ID: " + clientRequestItem.getItemID() + " was accessed, but doesn't exist!");
        }    
        return -1.0;
    }   

    /**
     * Method that handles biddings. It uses the keyword 'synchronized' in order to stop temperings.
     */
    public synchronized Integer bid(SealedObject clientRequest) throws RemoteException
    {
        SecretKey key = readKey();
        ClientRequest clientRequestItem = decrypt(clientRequest, key);
        AuctionItem desiredItem = null;

        // Search for the request item.
        for(int i = 0; i < availableItems.size(); ++i)
        {
            if(availableItems.get(i).getItemID().equals(clientRequestItem.getItemID()))
            {
                desiredItem = availableItems.get(i);    
                break;
            }
        }
        // Update the current price and the details of the highest bidder.
        if(desiredItem != null)
        {
            System.out.println("Item with ID: " + String.valueOf(clientRequestItem.getItemID()) + " has been bidded for!");
            
            desiredItem.setCurrentPrice(clientRequestItem.getCurrentPrice());
            desiredItem.setUsernameOfHighestBidder(clientRequestItem.getUsername());
            desiredItem.setEmailOfHighestBidder(clientRequestItem.getEmailAddress());

            return 1;
        }


        return 0;
    }

    /**
     * Return a list containing all the active auctioned items.
     */
    public ArrayList<SealedObject> getAllSpec(SealedObject userID) throws RemoteException
    {
        ArrayList<SealedObject> sealedAuctionItems = new ArrayList<SealedObject>();
        for(int i = 0; i < availableItems.size(); ++i)
        {
            SealedObject sealedAuctionItem = encrypt(availableItems.get(i));
            sealedAuctionItems.add(sealedAuctionItem);
        }
        return sealedAuctionItems;
    }

    /* Retreive an item by @param itemId */
    public SealedObject getSpec(SealedObject clientRequest) throws RemoteException
    {
        SecretKey key = readKey();
        ClientRequest clientRequestItem = decrypt(clientRequest, key);
        AuctionItem desiredItem = null;
        for(int i = 0; i < availableItems.size(); ++i)
        {
            if(availableItems.get(i).getItemID().equals(clientRequestItem.getItemID()))
            {
                desiredItem = availableItems.get(i);    
                break;
            }
        }
        if(desiredItem != null)
        {
            // Hide the reserve price if the client was not the creator of this auction.
            Integer clientID = logger.get(clientRequestItem.getClientID());
            if(!desiredItem.getItemID().equals(clientID))
                desiredItem.setMinimumPrice(0.0);
            // Seal the object and send it to the client.
            SealedObject encryptyedDesiredItem = encrypt(desiredItem);
            System.out.println("Item with ID: " + String.valueOf(clientRequestItem.getItemID()) + " has been fetched!");
            return encryptyedDesiredItem;
        }
        else
        {
            System.out.println("Item with ID: " + clientRequestItem.getItemID() + " was accessed, but doesn't exist!");
        }    
        return null;
    }

    /**
     * Create a new auction with the given details.
     */
    public Integer addAuction(SealedObject clientRequest) throws RemoteException
    {
        SecretKey key = readKey();
        ClientRequest clientRequestItem = decrypt(clientRequest, key);
        AuctionItem itemToAdd = new AuctionItem();
        
        // Create and store the item on server's side
        itemToAdd.setItemTitle(clientRequestItem.getItemName());
        itemToAdd.setItemDescription(clientRequestItem.getItemDescription());
        itemToAdd.setStartingPrice(clientRequestItem.getStartingPrice());
        itemToAdd.setMinimumPrice(clientRequestItem.getMinimumPrice());
        itemToAdd.setItemID(availableItems.size() + 1);
        itemToAdd.setUsernameOfHighestBidder("");
        availableItems.add(itemToAdd);

        System.out.println("User with ID: " + clientRequestItem.getClientID() + " added an auction.");

        // Remember which user created this auction.
        logger.put(clientRequestItem.getClientID(), itemToAdd.getItemID());

        return Integer.valueOf(itemToAdd.getItemID());
    }

    /**
     * Close an active auction in the system.
     */
    public Map<Integer, String> closeAuction(SealedObject clientRequest) throws RemoteException
    {
        SecretKey key = readKey();
        ClientRequest clientRequestItem = decrypt(clientRequest, key);
        AuctionItem closedAuctionItem = new AuctionItem();
        Integer itemID = -1;
        Map<Integer, String> resultOfAuction = new HashMap<Integer, String>();
        String emailAddress = "";

        // Retrieve the auction details and check if the user who requested the closing is also the creator.
        if(clientRequestItem.getItemID() - 1 < availableItems.size())
        {
            if(logger.get(clientRequestItem.getClientID()) == Integer.valueOf(availableItems.get(clientRequestItem.getItemID() - 1).getItemID()))
            {
                closedAuctionItem = availableItems.remove(clientRequestItem.getItemID() - 1);
                itemID = clientRequestItem.getItemID();
                emailAddress = closedAuctionItem.getEmailOfHighestBidder();
            }
    
            if(itemID == -1)
            {
                System.out.println("User with ID: " + clientRequestItem.getClientID() + " attempted to close auction with non-existent ID or without permission.");
                resultOfAuction.put(-1, "");
            }
            else
            {
                System.out.println("User with ID: " + clientRequestItem.getClientID() + " closed auction with ID: " + closedAuctionItem.getItemID());
    
                String usernameOfWinner = closedAuctionItem.getUsernameOfHighestBidder() != null ? closedAuctionItem.getUsernameOfHighestBidder() : "no one";
    
                if(closedAuctionItem.getCurrentPrice() < closedAuctionItem.getMinimumPrice())
                    {
                        resultOfAuction.put(itemID, "Your auction did not reach the minimum price set by you.\n");
                    }
                else
                {
                    resultOfAuction.put(itemID, "Your auction was closed at " + closedAuctionItem.getCurrentPrice() + "£ .\nThe highest bidder was \033[1;32m"+ usernameOfWinner 
                                        +"\033[0m !\nThe winner's email address is: \033[1;32m" + emailAddress + "\033[0m !");
                }
            }   
        }
        else
        {
            resultOfAuction.put(-1,"");
        }
        
        return resultOfAuction;
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

    /* Encrypt an auction item in a javax.crypto.SealedObject and return it. */
    public SealedObject encrypt(AuctionItem desiredItem)
    {
        try
        {
            SecretKey key = readKey();
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            SealedObject desiredItemSealed = new SealedObject(desiredItem, cipher);
            
            return desiredItemSealed;
        }
        catch(NoSuchAlgorithmException nsae)
        {
            nsae.printStackTrace();
        }
        catch(NoSuchPaddingException nspe)
        {
            nspe.printStackTrace();
        }
        catch(InvalidKeyException ike)
        {
            ike.printStackTrace();
        }
        catch(IOException ioe)
        {
            ioe.printStackTrace();
        }
        catch(IllegalBlockSizeException ibse)
        {
            ibse.printStackTrace();
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

    public static void main (String[] args)
    {
        new ServiceInterfaceImplementation();
    }
}