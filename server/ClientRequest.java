import java.io.Serializable;
import javax.crypto.*;

/* Class that represents a client's request. */
public class ClientRequest implements Serializable 
{
    private String clientID;
    private String username;
    private ClientRole userRole;
    private String emailAddress;
    private Integer itemID;
    private String itemName;
    private String itemDescription;
    private Double startingPrice;
    private Double minimumPrice;
    private Double currentPrice;
    private SecretKey secretKey;
    
    public synchronized String getClientID()
    {
        return clientID;
    }
    
    public String getUsername()
    {
        return username;
    }

    public synchronized String getEmailAddress()
    {
        return emailAddress;
    }

    public ClientRole getClientRole()
    {
        return userRole;
    }

    public Integer getItemID()
    {
        return itemID;
    }

    public SecretKey getUserSecretKey()
    {
        return secretKey;
    }

    public String getItemName()
    {
        return itemName;
    }

    public String getItemDescription()
    {
        return itemDescription;
    }

    public Double getStartingPrice()
    {
        return startingPrice;
    }

    public Double getMinimumPrice()
    {
        return minimumPrice;
    }

    public Double getCurrentPrice()
    {
        return currentPrice;
    }

    public synchronized void setClientID(String clientID)
    {
        this.clientID = clientID;
    }

    public synchronized void setUsername(String username)
    {
        this.username = username;
    }

    public synchronized void setEmailAddress(String emailAddress)
    {
        this.emailAddress = emailAddress;
    }

    public synchronized void setClientRole(ClientRole role)
    {
        this.userRole = role;
    }

    public synchronized void setItemID(Integer itemID)
    {
        this.itemID = itemID;
    }

    public synchronized void setItemName(String itemName)
    {
        this.itemName = itemName;
    }

    public synchronized void setItemDescription(String itemDescription)
    {
        this.itemDescription = itemDescription;
    }

    public synchronized void setStartingPrice(Double startingPrice)
    {
        this.startingPrice = startingPrice;
    }

    public synchronized void setMinimumPrice(Double minimumPrice)
    {
        this.minimumPrice = minimumPrice;
    }

    public synchronized void setCurrentPrice(Double currentPrice)
    {
        this.currentPrice = currentPrice;
    }

    public synchronized void setUserSecretKey(SecretKey key)
    {
        this.secretKey = key;
    }
}
