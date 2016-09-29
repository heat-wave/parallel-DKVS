package model.request;

/**
 * Created by heat_wave on 9/29/16.
 */
public class ClientRemoveRequest {
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
