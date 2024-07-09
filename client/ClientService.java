import java.util.Scanner;
import java.security.*;
import java.security.KeyPair;
import java.util.*;
import java.io.IOException;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.io.DataInputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.io.ObjectInputStream;
import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.io.InputStreamReader;
import java.rmi.RemoteException;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import javax.lang.model.util.ElementScanner14;
import java.security.Signature;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.spec.*;

/**
 * Class that represents the user's interface and means to handle user's inputs.
 */
public class ClientService 
{
    private ClientUser user;
    private ClientRequest request;
    private ServiceInterface server;
    private static final String PATH_TO_SERVER_FILE = "/home/diaconu/hdrive/SCC311/CW/Stage_3/server/";

    /**
     * Constructer that initiates the client's interface and handles the user's inputs.
     * @param role the role chosen by the user.
     */
    public ClientService(String role)
    {
        try
        {
            // User has to log in.
            System.out.print("Welcome, " + role + "! Please enter your username in order for the system to authenticate you: ");

            String username = getUsername();
            String name = "myserver";
            Registry registry = LocateRegistry.getRegistry("localhost");
            server = (ServiceInterface) registry.lookup(name);

            request = new ClientRequest();
            request.setUsername(username);
            
            if("buyer".equals(role.toLowerCase()))
                request.setClientRole(ClientRole.CLIENT_BUYER);
            else if("seller".equals(role.toLowerCase()))
                request.setClientRole(ClientRole.CLIENT_SELLER);
            else
            {
                System.out.println("You did not choose one of the two available roles (buyer or seller)");
                System.exit(0);    
            }
            
            System.out.println("Ok, " + request.getUsername() + "! Now the server will authenticate you...");
            
            // Perform challenge-response protocol using digital signature.
            String challenge = "Hi, server!";

            // Challenge server and confirm it's integrity
            byte[] signatureByteArray = server.getUserChallenge(challenge);
            Signature sign = Signature.getInstance("Ed25519");
            sign.initVerify(readServerPublicKey());
            sign.update(challenge.getBytes());
            boolean validServer = sign.verify(signatureByteArray);

            if(validServer) // if server has validated its integrity proceed with user integrity validation validation.
                System.out.println("Successfully confirmed the server's integrity! Proceeding with checking user's integrity...");
            else // if the server's integrity is compromised, stop the program.
            {
                System.out.println("Server's integrity cannot be confirmed. Program will now close.");
                System.exit(0);
            }

            try
            {
                boolean isUserValid;
                SealedObject requestSealed = encrypt(request);
                // Newly registered users do not need to validate themselves at the first log in session.
                if(server.isNewUser(requestSealed))
                {
                    System.out.println("You are new to the system, next time you log in the server will need to validate your integrity!");
                    SealedObject clientUserSealed = server.grantUserAccess(encrypt(request));
                    user = decrypt(clientUserSealed,readKey());
                    Thread.sleep(100);
                    System.out.println("\033[H\033[2J");
                    startClient();
                }
                else
                {
                    // Obtain server's challenge and confirm the user's integrity.
                    SealedObject serverSideChallenge = server.getServerChallenge(username);
                    String serverSideChallengeUnsealed = decryptChallenge(serverSideChallenge, readKey());
                    String challengeMessage = serverSideChallengeUnsealed.split("!")[0];
                    String filePathToPrivateKey = serverSideChallengeUnsealed.split(":")[1];  
                    PrivateKey userPrivateKey = readKey(filePathToPrivateKey);

                    Signature userSignature = Signature.getInstance("Ed25519");
                    userSignature.initSign(userPrivateKey);
                    userSignature.update(challengeMessage.getBytes());
                    byte[] signatureArray = userSignature.sign();

                    SecretKey key = readKey();
                    Cipher cipher = Cipher.getInstance("AES");
                    cipher.init(Cipher.ENCRYPT_MODE, key);
                    SealedObject signatureArraySealed = new SealedObject(signatureArray, cipher);

                    isUserValid = server.validateUserIntegrity(signatureArraySealed);

                    if(isUserValid) // if both user and server have validated the integrity proceed inside the system.
                    {

                        // Obtain the user's account details.
                        SealedObject clientUserSealed = server.grantUserAccess(encrypt(request));
                        user = decrypt(clientUserSealed,readKey());
                        System.out.println("Successfully confirmed the user's integrity! Redirecting to the auction system!");
                        Thread.sleep(100);
                        System.out.println("\033[H\033[2J");
                        startClient();
                    }
                    else // if user's integrity cannot be validated, stop the program.
                    {
                        System.out.println("User's integrity cannot be confirmed. Program will now close.");
                        System.exit(0);
                    }
                }

            }
            catch(Exception e)
            {
                System.out.println("Server couldn't send back a valid challenge. Program will close itself!");
                e.printStackTrace();
                System.exit(0);
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Method that handles the user's inputs and organises the interface.
     */
    public void startClient()
    {
        // Obtain the user's input.
        Scanner commands = new Scanner(System.in);
        System.out.println("Welcome to the Auctioning System! These are the available commands for your role!");
        user.displayAvailableCommands();
        request.setClientID(user.getUserID());
        System.out.println();

        // Keep the interface and the connection to the server alive as long as the user is logged in.
        while(true)
        {
            System.out.print(user.getUsername()+": ");
            String userCommand = commands.next();
            
            // Handle the command entered by the user.
            switch(userCommand)
            {
                // Log out of the system.
                case "exit":
                    System.out.println("You have logged out of the system!");
                    System.exit(0);
                    break;

                // Fetch the item with corresponding ID from the server.
                case "fetch":
                    try
                    {

                        // Obtain the ID of the active auction to be fetched.
                        System.out.print("You must provide the ID of the Auction you wish to be prompted for: ");
                        String itemID = commands.next();
                        request.setItemID(Integer.valueOf(itemID));

                        // Encrypt request.
                        SealedObject clientRequestSealed = encrypt(request);

                        // Retrieve the details of the auction that was searched for.
                        SealedObject sealedAuctionItem = server.getSpec(clientRequestSealed);

                        // If the variable is null it means there was not active auction with the ID entered.
                        if(sealedAuctionItem == null)
                        {
                            System.out.println("There is no active auction with ID: " + itemID + "\n");
                        }
                        // Prompt the user with the details of the auction he requested.
                        else
                        {
                            AuctionItem item = decryptItem(sealedAuctionItem, user.getUserKey());
                            System.out.println("--------------------------------Ongoing Auction with ID:" + item.getItemID() + "---------------------------------------");

                            System.out.print("Auctioned Item Name: " + item.getItemTitle()+"\nItem Description: "
                                            + item.getItemDescription() + "\nCurrent Price: " + item.getCurrentPrice() + "\nStarting Price: " + item.getStartingPrice()
                                            + "\nUsername of Highest Bidder: " + item.getUsernameOfHighestBidder());
                            if(item.getMinimumPrice() > 0.0)
                                System.out.println("\nReserve price: " + item.getMinimumPrice());
                            
                            System.out.print("\n");
                            System.out.println("------------------------------------------------------------------------------------------------");
                        }
                       
                    }
                    catch(Exception e)
                    {
                        e.getCause();
                    }
                    break;

                // Display the transactions made by this user during this session.
                case "transactions":
                    user.displayTransactions();
                    break;
                // List all the available auctions.
                case "list":
                    try
                    {
                        // Create the client-side request.
                        request.setClientID(user.getUserID());
                        SealedObject clientRequestSealed = encrypt(request);

                        // Obtain the list of encrypted auctions.
                        ArrayList<SealedObject> sealedAuctionItems = server.getAllSpec(clientRequestSealed);

                        // Deserialize and decrypt the details of each auction.
                        ArrayList<AuctionItem> items = new ArrayList<AuctionItem>();
                        for(SealedObject index : sealedAuctionItems)
                        {
                            items.add(decryptItem(index, user.getUserKey()));
                        }

                        // Prompt user with all the available auctions.
                        System.out.println("-------------------------------------Ongoing Auctions-------------------------------------------");
                        for(AuctionItem item : items)
                        {
                            System.out.print("Auction ID: " + item.getItemID() + "\nAuctioned Item Name: " + item.getItemTitle()+"\n Item Description: "
                                            + item.getItemDescription() + "\nCurrent Price (in £): " + item.getCurrentPrice());
                            System.out.println("\n");
                        }
                        System.out.println("------------------------------------------------------------------------------------------------");
                        
                    }
                    catch(Exception e)
                    {
                        e.getCause();
                    }
                    break;
                // Send a bidding to the server to update the specified auction.
                case "bid":
                    // Verify that the user is registered as a buyer in order to eliminate unnecessary tempering of competition.
                    if(user.getUserRole() != String.valueOf(ClientRole.CLIENT_BUYER))
                        {
                            System.out.println("Nice try!");
                            break;
                        }
                        else 
                        {
                            try
                            {
                                // Obtain the details for a bidding to happen.
                                Scanner itemDetails = new Scanner(System.in);
                                System.out.println("You must provide several details about the item you wish to auction!");
                                System.out.print("Auction ID: ");
                                request.setItemID(Integer.parseInt(itemDetails.nextLine()));
                                System.out.print("Username: ");
                                request.setUsername(itemDetails.nextLine());
                                System.out.print("Email Address: ");
                                request.setEmailAddress(itemDetails.nextLine());
                                System.out.print("Your bid: ");
                                request.setCurrentPrice(Double.parseDouble(itemDetails.next()));
                                
                                // Encrypt request
                                SealedObject sealedRequest = encrypt(request);

                                // Receive the auction's ID as an integer if the bidding process was successfull.
                                Integer addedItemID = server.bid(sealedRequest);
                                System.out.println("You have bid for auction named with ID " + addedItemID); 

                                // Update the transaction mapping for this session.
                                user.addTransaction(request.getItemID(), userCommand);
                                break;
                            }
                            catch(Exception e)
                            {
                                e.getCause();
                            }
                            break;
                        }
                // Create a new auction.
                case "auction":
                    // Verify that the user is registered as a seller in order to eliminate unnecessary tempering of listed auctions.
                    if(user.getUserRole() != String.valueOf(ClientRole.CLIENT_SELLER))
                    {
                        System.out.println("Nice try!");
                        break;
                    }
                    else 
                    {
                        try
                        {
                            // Obtain details of the item to be auctioned for.
                            Scanner itemDetails = new Scanner(System.in);
                            System.out.println("You must provide several details about the item you wish to auction!");
                            System.out.print("Item Name: ");
                            request.setItemName(itemDetails.nextLine());
                            System.out.print("Item Description: ");
                            request.setItemDescription(itemDetails.nextLine());
                            System.out.print("Starting price (in £): ");
                            request.setStartingPrice(Double.parseDouble(itemDetails.next()));
                            System.out.print("Minimum price to be matched: ");
                            request.setMinimumPrice(Double.parseDouble(itemDetails.next()));
                            if(request.getMinimumPrice() < request.getStartingPrice())
                            {
                                System.out.println("The reserve price has to be bigger than the starting price.");
                            }
                            else
                            {
                                // Encrypt the request.
                                SealedObject sealedRequest = encrypt(request);
                                Integer addedItemID = server.addAuction(sealedRequest);
                                if(addedItemID == -1)
                                    System.out.println("Item has not been added correctly");
                                else
                                    System.out.println("You have created an auction named '" + request.getItemName() +"' with ID " + addedItemID); 

                                // Update the transaction mapping for this session.
                                user.addTransaction(request.getItemID(), userCommand);
                            }

                        }
                        catch(Exception e)
                        {
                            e.getCause();
                        }
                        break;
                    }
                
                // Close an active auction.
                case "close":
                     // Verify that the user is registered as a seller in order to eliminate unnecessary tempering of listed auctions.
                    if(user.getUserRole() != String.valueOf(ClientRole.CLIENT_SELLER))
                        {
                            System.out.println("Nice try!");
                            break;
                        }
                        else 
                        {
                            try
                            {
                                // Obtain details for the closing process to begin.
                                Scanner itemDetails = new Scanner(System.in);
                                System.out.println("You must provide the ID of the auction you wish to close!");
                                System.out.print("Auction ID: ");
                                request.setItemID(Integer.parseInt(itemDetails.next()));
                                
                                // Encrypt the request.
                                SealedObject sealedRequest = encrypt(request);

                                // Retreive the auction's ID and a message stating the outcome of the operation.
                                Map<Integer, String> addedItemDetails = server.closeAuction(sealedRequest);
                                Map.Entry<Integer, String> addedItemEntry = addedItemDetails.entrySet().iterator().next();
                                Integer id = addedItemEntry.getKey();
                                String message = addedItemEntry.getValue();

                                // The server did not close the auction with said ID because it wasn't created by the logged user or it no longer was active in the first place.
                                if(id == -1)
                                {
                                    System.out.println("Auction with ID: " + id + " does not exist or you do not have permission to close it!");
                                }
                                // Successfully prompt user of the action the client performed.
                                else
                                {
                                    System.out.println("Successfully closed auction with ID: " + id);
                                    System.out.println(message);
                                    user.addTransaction(id, userCommand);
                                }
                                break;                               
                            }
                            catch(Exception e)
                            {
                                e.getCause();
                            }
                        }
                default: 
                    continue;
            }
        
        }
    }

    /**
     * Encrypt this client's request using AES keys.
     * @param request the request that is to be sent to the server.
     * @return the SealedObject containing the request.
     */
    public SealedObject encrypt(ClientRequest request)
    {
        try
        {
            SecretKey key = readKey();
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            SealedObject requestSealed = new SealedObject(request, cipher);
            
            return requestSealed;
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
     * Decrypt the user details sent by the server after logging in.
     */
    public ClientUser decrypt(SealedObject object, SecretKey key)
    {
        try
        {
            return (ClientUser) object.getObject(key);
        }
        catch(Exception ioe)
        {
            ioe.printStackTrace();
        }

        return null;
    }


    /**
     * Decrypt the server challenge sent by the server after protocol started.
     */
    public String decryptChallenge(SealedObject object, SecretKey key)
    {
        try
        {
            String map = (String) object.getObject(key);
            return map;
        }
        catch(Exception ioe)
        {
            ioe.printStackTrace();
        }

        return null;
    }

    /**
     * Decrypt a given item from an auction using a secret key.
     * @param object the sealed object containing the details of given auction item.
     * @param key the key that deserializes the sealed object.
     * @return an object containing the details of the given auction item.
     */
    public AuctionItem decryptItem(SealedObject object, SecretKey key)
    {
        try
        {
            return (AuctionItem) object.getObject(key);
        }
        catch(Exception ioe)
        {
            ioe.printStackTrace();
        }

        return null;
    }

    /**
     * Write the transaction key in the .txt file.
     */
    public synchronized void writeKey(SecretKey key)
    {
        try
        { 
            DataOutputStream dos = new DataOutputStream(new FileOutputStream(PATH_TO_SERVER_FILE + "secret-key.txt"));
            dos.write(key.getEncoded());
            dos.flush();
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Read the server's public key (only used to verify the server's integrity).
     */
    public PublicKey readServerPublicKey()
    {
        try 
        {
            FileInputStream fis = new FileInputStream(PATH_TO_SERVER_FILE + "server-public-key.txt");
            DataInputStream dis = new DataInputStream(fis);
            byte[] key = new byte[1024];
            dis.read(key);

            X509EncodedKeySpec deserializedKey = new X509EncodedKeySpec(key);
            KeyFactory keyFactory = KeyFactory.getInstance("Ed25519");
            PublicKey serverPublicKey = keyFactory.generatePublic(deserializedKey);

            return serverPublicKey;
        } 
        catch (Exception e) 
        {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Read the transaction key used for encryption.
     * @return the secret key stored on a .txt file on server's side.
     */
    public SecretKey readKey()
    {
        try 
        {
            FileInputStream fis = new FileInputStream(PATH_TO_SERVER_FILE + "secret-key.txt");
            DataInputStream dis = new DataInputStream(fis);
            int length = dis.readInt();
            byte[] key = new byte[length];
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
     * Read the transaction key used for encryption at specified file path.
     * @return the secret key stored on a .txt file on server's side.
     */
    public PrivateKey readKey(String filePath)
    {
        try 
        {
            DataInputStream dis = new DataInputStream(new FileInputStream(filePath));
            int l = dis.readInt();
            byte[] key = new byte[l];
            dis.read(key);


            PKCS8EncodedKeySpec deserializedKey = new PKCS8EncodedKeySpec(key);
            KeyFactory keyFactory = KeyFactory.getInstance("Ed25519");
            PrivateKey userPrivateKey = keyFactory.generatePrivate(deserializedKey);
            return userPrivateKey;
        } 
        catch (Exception e) 
        {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * String-ifies the username entered by the user client.
     * @return the string containing the user's username.
     */
    public String getUsername()
    {
        Scanner input = new Scanner(System.in);
        String username = input.next();

        return username;
    }
}
