package model.request;

import model.Request;

/**
 * Created by heat_wave on 9/29/16.
 */
public class ClientRemoveRequest extends Request {
    private String key;

    public ClientRemoveRequest(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public ClientRemoveRequest() {
    }

    @Override
    public String toString() {
        return "ClientRemoveRequest{" +
                "key='" + key + '\'' +
                '}';
    }
}
