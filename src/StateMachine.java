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
    private HashMap<String, String> map;
    private ArrayList<Entry> entries;
    private FileWriter fileWriter;

    private File file;

    StateMachine(File file) {
        this.file = file;

        try (FileWriter fw = new FileWriter(file);
            Scanner in = new Scanner(file)) {

            entries = new ArrayList<>();

            fileWriter = fw;

            Entry.Type type = null;
            String key = null;
            String value = null;

            while (in.hasNext()) {
                switch (in.next()) {
                    case "set":
                        key = in.next();
                        value = in.next();
                        type = Entry.Type.SET;
                        map.put(key, value);
                        break;
                    case "delete":
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
        Entry toCheck = entries != null ? entries.get(index) : null;
        return toCheck != null && toCheck.getTerm() == term;
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
}
