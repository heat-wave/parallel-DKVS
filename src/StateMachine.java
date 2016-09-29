import com.esotericsoftware.minlog.Log;
import model.Entry;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

/**
 * Created by heat_wave on 6/19/16.
 */
class StateMachine {
    private HashMap<String, String> map = new HashMap<>();
    private ArrayList<Entry> entries;
    private FileWriter fileWriter;

    private File file;

    StateMachine(File file) {
        this.file = file;

        try (Scanner in = new Scanner(file)) {

            entries = new ArrayList<>();

            Entry.Type type = null;
            String key = null;
            String value = null;

            while (in.hasNext()) {
                switch (in.next()) {
                    case "SET":
                        key = in.next();
                        value = in.next();
                        type = Entry.Type.SET;
                        map.put(key, value);
                        break;
                    case "DELETE":
                        key = in.next();
                        value = null;
                        type = Entry.Type.DELETE;
                        map.remove(key);
                        break;
                }
                int term = in.nextInt();
                int index = in.nextInt();
                entries.add(new Entry(type, key, value, term, index));
            }
        } catch (IOException e) {
            Log.error("Exception", e.getMessage());
        }
        rewriteLog();
    }

    void appendEntries(int from, ArrayList<Entry> toAppend) {
        if (toAppend.isEmpty()) {
            return; //heartbeat received
        }
        int pos = from;
        while (pos < entries.size() && entries.get(pos).equals(toAppend.get(pos - from))) {
            pos++;
        }
        entries.retainAll(entries.subList(0, pos));
        entries.addAll(toAppend.subList(pos - from, toAppend.size()));
        rewriteLog();
    }

    private void rewriteLog() {
        try {
            fileWriter = new FileWriter(file, false);
            for (Entry entry : entries) {
                fileWriter.write(entry.toString() + '\n');
            }
            fileWriter.flush();
        } catch (IOException e) {
            Log.error("Exception", e.getMessage());
        }
    }

    Entry getLastLogEntry() {
        return entries == null || entries.isEmpty() ?  null : entries.get(entries.size() - 1);
    }

    boolean checkEntryValidity(int index, int term) {
        Entry toCheck = entries != null && entries.size() > index ? entries.get(index) : null;
        return index == entries.size() || toCheck != null && toCheck.getTerm() == term;
    }

    int getLogSize() {
        return entries.size();
    }

    Entry getEntry(int index) {
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    ArrayList<Entry> getEntriesStartingWith(int startIndex) {
        ArrayList<Entry> result = new ArrayList<>();
        for (int i = startIndex; i < entries.size(); i++) {
            result.add(entries.get(i));
        }
        return result;
    }

    void addEntryFromClient(Entry entry) {
        ArrayList<Entry> toAppend = new ArrayList<>();
        toAppend.add(entry);
        appendEntries(entries.size(), toAppend);
    }

    String get(String key) {
        return map.getOrDefault(key, null);
    }
}
