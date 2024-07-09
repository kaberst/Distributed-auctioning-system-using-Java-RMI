import java.io.Serializable;

/**
 * Class that represents an item within the auction system.
 */
public class AuctionItem implements Serializable
{
    private Integer itemId;
    private String itemTitle;
    private String itemDescription;
    private boolean isNew;
    private double startingPrice;
    private double minimumPrice;
    private double currentPrice;
    private String usernameOfHighestBidder;
    private String emailOfHighestBidder;

    public Integer getItemID()
    {
        return itemId;
    }

    public String getItemTitle()
    {
        return itemTitle;
    }

    public String getItemDescription()
    {
        return itemDescription;
    }

    public double getStartingPrice()
    {
        return startingPrice;
    }

    public double getMinimumPrice()
    {
        return minimumPrice;
    }

    public double getCurrentPrice()
    {
        return currentPrice;
    }

    public boolean isNew()
    {
        return isNew;
    }

    public String getUsernameOfHighestBidder()
    {
        return usernameOfHighestBidder;
    }

    public String getEmailOfHighestBidder()
    {
        return emailOfHighestBidder;
    }

    public void setItemID(Integer id)
    {
        this.itemId = id;
    }

    public void setItemTitle(String title)
    {
        this.itemTitle = title;
    }

    public void setItemDescription(String description)
    {
        this.itemDescription = description;
    }

    public void isNew(boolean value)
    {
        this.isNew = value;
    }

    public void setStartingPrice(double startingPrice)
    {
        this.startingPrice = startingPrice;
    }

    public void setMinimumPrice(double minimumPrice)
    {
        this.minimumPrice = minimumPrice;
    }

    public void setCurrentPrice(double price)
    {
        this.currentPrice = price;
    }

    public void setUsernameOfHighestBidder(String username)
    {
        this.usernameOfHighestBidder = username;
    }

    public void setEmailOfHighestBidder(String emailAddress)
    {
        this.emailOfHighestBidder = emailAddress;
    }
}