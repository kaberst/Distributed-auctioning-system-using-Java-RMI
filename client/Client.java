import javax.lang.model.util.ElementScanner14;

public class Client
{
    public static void main(String[] args)
    {
        ClientService cs;
        if(args[0] != null && !args[0].equals(""))
            cs = new ClientService(args[0]);
        else 
            System.out.println("Please register as either a buyer account or a seller acount.");
    }
}