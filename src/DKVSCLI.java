import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Scanner;

/**
 * Created by heat_wave on 6/19/16.
 */
public class DKVSCLI {

    public static void main(String[] args) throws IOException {
        if (args.length != 1 || args[0].equals("--help")) {
            System.out.println("DKVS CLI usage:\n" +
                    "\t-type 'node X' to start DKVS node №X\n" +
                    "\t-type 'kill N' to kill DKVS node number N\n" +
                    "\t-type 'add X Y' to add key-value pair X-Y to DKVS\n" +
                    "\t-type 'remove X' to remove the value associated with X\n" +
                    "\t-type 'get X' to get the value associated with X\n" +
                    "\t-type '--help' to get this prompt\n");
        }
        Scanner in = new Scanner(System.in);

        NodeImpl [] nodes = new NodeImpl[Constants.SERVER_COUNT + 1];

        for (int i = 1; i <= 5; i++) {
            nodes[i] = new NodeImpl(i);
        }
        for (int i = 1; i <= 5; i++) {
            nodes[i].run();
        }

        while (in.hasNext()) {
            String command = in.next();
            switch (command) {
                case "kill":
                    int pos = in.nextInt();
                    nodes[pos].stop();
                    break;
                case "node":
                    pos = in.nextInt();
                    nodes[pos].run();
                    break;
                case "add":
                    int key = in.nextInt();
                    int value = in.nextInt();
                    //???
                    break;
                case "remove":
                    key = in.nextInt();
                    //???
                    break;
                case "get":
                    key = in.nextInt();
                    //???
                    break;
            }
        }
    }
}
