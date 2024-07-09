import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import org.jgroups.JChannel;
import org.jgroups.View;

public class Server extends FrontendServer
{
    public Server()
    {
        super();
    }
    public static void main(String[] args) 
    {
        try 
        {

            Server s = new Server();
            String name = "myserver";
            int portNumber = 1112;
            ServiceInterface stub = (ServiceInterface) UnicastRemoteObject.exportObject(s, portNumber);
            Registry registry = LocateRegistry.getRegistry();
            registry.rebind(name, stub);
            System.out.println("Server ready, listening to port: " + portNumber);
        } 
        catch (Exception e) 
        {
            System.err.println("Exception:");
            e.printStackTrace();
        }
    }
}