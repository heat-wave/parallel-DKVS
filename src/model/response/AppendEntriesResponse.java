package model.response;

import model.Response;

/**
 * Created by heat_wave on 8/10/16.
 */
public class AppendEntriesResponse extends Response {
    int term;
    boolean success;

    public AppendEntriesResponse(int term, boolean success) {
        this.term = term;
        this.success = success;
    }

    public AppendEntriesResponse() {
    }

    public int getTerm() {

        return term;
    }

    public void setTerm(int term) {
        this.term = term;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }
}
