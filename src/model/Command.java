package model;

/**
 * Created by heat_wave on 8/10/16.
 */
public class Command {

    private enum Type {
        PUT,
        REMOVE
    }

    private String[] args = new String[2];
}
