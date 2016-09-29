import com.esotericsoftware.kryo.Kryo;
import model.Entry;
import model.request.*;
import model.response.AppendEntriesResponse;
import model.response.ClientGetResponse;
import model.response.ElectionVoteResponse;

import java.util.ArrayList;

/**
 * Created by heat_wave on 9/29/16.
 */
public class Utils {
    private Utils() {}

    public static void registerClasses(Kryo kryo) {
        kryo.register(ArrayList.class);
        kryo.register(String.class);
        kryo.register(Entry.class);
        kryo.register(Entry.Type.class);
        kryo.register(ElectionVoteRequest.class);
        kryo.register(ElectionVoteResponse.class);
        kryo.register(AppendEntriesRequest.class);
        kryo.register(AppendEntriesResponse.class);
        kryo.register(ClientGetRequest.class);
        kryo.register(ClientGetResponse.class);
        kryo.register(ClientAddRequest.class);
        kryo.register(ClientRemoveRequest.class);
    }
}
