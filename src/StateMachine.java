import model.Entry;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

/**
 * Created by heat_wave on 6/19/16.
 */
public class StateMachine {
    private HashMap<String, String> map;
    private ArrayList<Entry> entries;
    private PrintWriter fileWriter;

    public StateMachine(File file) {
        try (FileWriter fw = new FileWriter(file);
            BufferedWriter bw = new BufferedWriter(fw);
            PrintWriter out = new PrintWriter(bw);
            Scanner in = new Scanner(file)) {

            fileWriter = out;

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
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {

        }
    }

    public void appendEntry(Entry entry) {
        fileWriter.append(entry.toString());
        fileWriter.flush();
    }

    public void appendEntries(ArrayList<Entry> toAppend) {
        int i = 0;
        while (i < toAppend.size() && i < entries.size() && entries.get(i).equals(toAppend.get(i))) {
            i++;
        }
        entries.retainAll(entries.subList(0, i));
        entries.addAll(toAppend.subList(i, toAppend.size()));
    }

    Entry getLastLogEntry() {
        return entries != null ? entries.get(entries.size() - 1) : null;
    }

    boolean checkEntryValidity(int index, int term) {
        return entries.get(index).getTerm() == term;
    }
}
