package model;

import java.util.Random;
import java.util.TimerTask;

/**
 * Created by heat_wave on 8/23/16.
 */
public class HeartbeatTask extends TimerTask {
    private int rate;

    protected HeartbeatTask(int remoteId) {
        super();
        this.rate = new Random().nextInt(150) + 150; //bounds rate between 150 and 300 ms
    }

    public int getRate() {
        return rate;
    }

    @Override
    public void run() {

    }
}
