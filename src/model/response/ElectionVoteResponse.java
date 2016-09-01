package model.response;

import model.Response;

/**
 * Created by heat_wave on 8/10/16.
 */
public class ElectionVoteResponse extends Response {
    int term;
    boolean voteGranted;

    public ElectionVoteResponse() {
    }

    public int getTerm() {

        return term;
    }

    public void setTerm(int term) {
        this.term = term;
    }

    public boolean isVoteGranted() {
        return voteGranted;
    }

    public void setVoteGranted(boolean voteGranted) {
        this.voteGranted = voteGranted;
    }

    public ElectionVoteResponse(int term, boolean voteGranted) {
        this.term = term;
        this.voteGranted = voteGranted;
    }

    @Override
    public String toString() {
        return "ElectionVoteResponse{" +
                "term=" + term +
                ", voteGranted=" + voteGranted +
                '}';
    }
}
