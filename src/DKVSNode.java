import model.Response;
import model.response.AppendEntriesResponse;
import model.response.ElectionVoteResponse;

import java.util.List;

/**
 * Created by heat_wave on 6/19/16.
 */
public interface DKVSNode {
    void startElection();

    void connectToSiblings();
}
