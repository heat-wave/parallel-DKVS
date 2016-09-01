import model.Response;
import model.response.AppendEntriesResponse;
import model.response.ElectionVoteResponse;

import java.util.List;

/**
 * Created by heat_wave on 6/19/16.
 */
public interface DKVSNode {
    public ElectionVoteResponse requestVote(int term, int candidateId, int lastLogIndex, int lastLogerm);

    public AppendEntriesResponse appendEntries(int term, int leaderId, int prevLogIndex,
                                               List<String> entries, int leaderCommitIndex);

    void startElection();

    void connectToSiblings();
}
