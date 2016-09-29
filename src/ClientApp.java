import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import model.Entry;
import model.request.ClientAddRequest;
import model.request.ClientGetRequest;
import model.request.ClientRemoveRequest;
import model.response.ClientGetResponse;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Scanner;

/**
 * Created by heat_wave on 9/29/16.
 */
public class ClientApp {
    Client[] clients = new Client[6];
    Properties properties;

    public static void main(String[] args) throws IOException {
        new ClientApp().run();
    }

    private void run() throws IOException {
        System.out.println("Usage:\n" +
                "\t-type 'add N X Y' to add key-value pair X-Y to DKVS\n" +
                "\t-type 'remove N X' to remove the value associated with X\n" +
                "\t-type 'get N X' to get the value associated with X from server N\n");

        properties = new Properties();
        String propFileName = "dkvs.properties";
        InputStream inputStream = new FileInputStream(propFileName);
        properties.load(inputStream);

        for (int i = 1; i < 6; i++) {
            String address = properties.getProperty("node." + i);
            String[] addressParts = address.split(":");
            clients[i] = new Client();
            clients[i].start();
            Utils.registerClasses(clients[i].getKryo());

            clients[i].addListener(new Listener() {
                @Override
                public void received(Connection connection, Object object) {
                    super.received(connection, object);
                    if (object instanceof ClientGetResponse)
                        System.out.println(object.toString());
                }
            });
            clients[i].connect(5000, addressParts[0], Integer.parseInt(addressParts[1]));
        }

        Scanner in = new Scanner(System.in);

        while (in.hasNext()) {
            String command = in.next();
            int index = in.nextInt();
            switch (command) {
                case "add":
                    String key = in.next();
                    String value = in.next();
                    clients[index].sendTCP(new ClientAddRequest(key, value)); //nodes[1].addEntryFromClient(Entry.Type.SET, key, value);
                    break;
                case "remove":
                    key = in.next();
                    clients[index].sendTCP(new ClientRemoveRequest(key));
                    break;
                case "get":
                    key = in.next();
                    clients[index].sendTCP(new ClientGetRequest(key));
                    break;
            }
        }
    }
}
