package model;

/**
 * Created by heat_wave on 8/10/16.
 */
public class Entry {
    private Type type;
    private String key;
    private String value;
    private int term;
    private int index;

    public Entry(Type type, String key, String value, int term, int index) {
        this.type = type;
        this.key = key;
        this.value = value;
        this.term = term;
        this.index = index;
    }

    public Type getType() {
        return type;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public int getTerm() {
        return term;
    }

    public int getIndex() {
        return index;
    }

    public enum Type {
        SET,
        DELETE
    }

    @Override
    public String toString() {
        return type + " " + key + " " + value + " " + index + " " + term;
    }
}
