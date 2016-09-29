package model.request;

import model.Request;

/**
 * Created by heat_wave on 9/29/16.
 */
public class ClientGetRequest extends Request {
    private String key;

    public ClientGetRequest() {
    }

    public String getKey() {

        return key;
    }

    public ClientGetRequest(String key) {
        this.key = key;
    }

    @Override
    public String toString() {
        return "ClientGetRequest{" +
                "key='" + key + '\'' +
                '}';
    }
}
