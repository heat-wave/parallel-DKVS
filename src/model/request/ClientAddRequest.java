package model.request;

/**
 * Created by heat_wave on 9/29/16.
 */
public class ClientAddRequest {
    public String getKey() {
        return key;
    }

    public ClientAddRequest() {
    }

    private String key;

    public String getValue() {
        return value;
    }

    private String value;

    public ClientAddRequest(String key, String value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public String toString() {
        return "ClientAddRequest{" +
                "key='" + key + '\'' +
                ", value='" + value + '\'' +
                '}';
    }
}
