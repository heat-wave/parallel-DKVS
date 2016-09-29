package model.response;

import model.Response;

/**
 * Created by heat_wave on 9/29/16.
 */
public class ClientGetResponse extends Response{
    private String value;

    public ClientGetResponse() {
    }

    @Override
    public String toString() {
        return "ClientGetResponse{" +
                "value='" + value + '\'' +
                '}';
    }

    public ClientGetResponse(String value) {
        this.value = value;
    }
}
