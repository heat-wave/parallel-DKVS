import com.esotericsoftware.minlog.Log;
import model.Entry;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Scanner;

/**
 * Created by heat_wave on 6/19/16.
 */
public class DKVS {

    public static void main(String[] args) throws IOException {
        Log.set(Log.LEVEL_INFO);

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
            }
        }
    }
}
