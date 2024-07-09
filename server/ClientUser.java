import java.util.*;
import javax.crypto.*;
import java.security.*;;
import javax.print.attribute.IntegerSyntax;
import java.io.Serializable;

/**
 * Class that stores all user-related details.
 */
public class ClientUser implements Serializable
{
    private ClientRole role;
    private String clientID;
    private String username;
    private Map<Integer, List<String>> transactions = new LinkedHashMap<Integer, List<String>>();
    private SecretKey userSecretKey;
    private KeyPair publicAndPrivateKeys;

    /**
     * Constructor that creates an instance of ClientUser that stores user details.
     * @param username the username given by the user.
     */
    public ClientUser(String username)
    {
        this.username = username;
        this.clientID = java.util.UUID.randomUUID().toString();
    }

    /**
     * Displays the available commands given this user's role in the system (either a buyer or a seller).
     */
    public void displayAvailableCommands()
    {
        System.out.println("\nexit: Will close the program and exit out of the auctioning system.");
        System.out.println("list: Will list all active auctions, the auctioned item and it's current price.");
        System.out.println("fetch: Will list an active auction, the auctioned item and it's current details. You must provide the auction's ID.");
        System.out.println("transactions: Will list all transactions made by this user.");
        if(role.equals(ClientRole.CLIENT_BUYER))
        {
            System.out.println("bid: Will bid for an auction session. You must provide the auction's ID, a price, your username and email address.\n\n");
        }
        else if(role.equals(ClientRole.CLIENT_SELLER))
        {
            System.out.println("close: Will close an auction session. You must provide the auction's ID.");
            System.out.println("auction: Will add an item to the current auction session. You must provide details of the item.\n\n");
        }
    }

    /**
     * Creates the an instance of transaction key for this user to use within the server.
     * @param secretKey
     */
    public void setUserKey(SecretKey secretKey)
    {
        try
        {
            this.userSecretKey = secretKey;
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Creates an instance of public-private key pair for the user in order to validate integrity during authentication.
     */
    public void setKeyPair()
    {
        try
        {
           KeyPairGenerator userKeyPairGen = KeyPairGenerator.getInstance("Ed25519");

           publicAndPrivateKeys = userKeyPairGen.generateKeyPair();
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     * @return this user's private key.
     */
    public PrivateKey getUserPrivateKey()
    {
        return publicAndPrivateKeys.getPrivate();
    }
    
    /**
     * @return this user's public key.
     */
    public PublicKey getUserPublicKey()
    {
        return publicAndPrivateKeys.getPublic();
    }

    /**
     * @return return the user's transaction key.
     */
    public SecretKey getUserKey()
    {
        return userSecretKey;
    }

    /**
     * Update this user's transactions over the course of the session.
     * @param itemID the auction's ID where this user did something.
     * @param commandUsed what the user did.
     */
    public void addTransaction(Integer itemID, String commandUsed)
    {
        if(transactions.get(itemID) == null)
        {
            transactions.put(itemID, new ArrayList<String>());
            transactions.get(itemID).add(commandUsed);
        }
        else
            transactions.get(itemID).add(commandUsed);
    }

    /**
     * Retrieve the transactions this user made during this session.
     */
    public void displayTransactions()
    {
        System.out.println("\nListing all transactions this user made so far: ");
        for (Integer key : transactions.keySet()) 
        {
            System.out.println("\nItem ID:" + String.valueOf(key) + " || Transaction Type:" + transactions.get(key));
        }
        System.out.println();
    }

    /**
     * @return the username of this user.
     */
    public String getUsername()
    {
        return username;
    }

    /**
     * @return the role of this user.
     */
    public String getUserRole()
    {
        return String.valueOf(role);
    }

    /**
     * @return the user's id.
     */
    public String getUserID()
    {
        return clientID;
    }

    /**
     * Sets the role of this user (either a buyer or a seller).
     */
    public void setClientRole(ClientRole role)
    {
        this.role = role;
    }
}