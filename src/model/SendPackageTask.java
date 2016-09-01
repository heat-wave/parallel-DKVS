package model;

import com.esotericsoftware.kryonet.Client;

import java.util.concurrent.Callable;

/**
 * Created by heat_wave on 8/29/16.
 */
public class SendPackageTask implements Callable<Response> {
    private Client client;
    private Request request;

    @Override
    public Response call() throws Exception {
        return null;
    }

    public SendPackageTask(Client client, Request request) {
        client.sendTCP(request);
    }
}
