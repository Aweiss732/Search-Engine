/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, 2017
 */  

package ir;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.StringJoiner;
import java.util.StringTokenizer;
import java.io.Serializable;

public class PostingsEntry implements Comparable<PostingsEntry>, Serializable {

    public int docID;
    public double score = 0;

    /**
     *  PostingsEntries are compared by their score (only relevant
     *  in ranked retrieval).
     *
     *  The comparison is defined so that entries will be put in 
     *  descending order.
     */
    public int compareTo( PostingsEntry other ) {
       return Double.compare( other.score, score );
    }


    //
    // YOUR CODE HERE
    //
    private ArrayList<Integer> offsets = new ArrayList<>();

    public PostingsEntry(int docID) {
        this.docID = docID;;
    }

    public void addOffset(int offset) {
        offsets.add(offset);
    }

    public void addOffsets(ArrayList<Integer> newOffsets) {
        this.offsets.addAll(newOffsets);
    }

    public void setScore(double score) {
        this.score = score;
    }

    public ArrayList<Integer> getOffsets() {
        return offsets;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(docID).append(":"); // separate docID from offsets 
        for (int i = 0; i < offsets.size(); i++) {
            sb.append(offsets.get(i));
            if (i < offsets.size() - 1) {
                sb.append(","); // comma seperates offsets within the same document
            }
        }
        return sb.toString();
    }

    public static PostingsEntry fromString(String s) {
        if (s == null || s.isEmpty()) {
            throw new IllegalArgumentException("Empty string for PostingsEntry");
        }
        String[] parts = s.split(":", 2); // split into max 2 parts. separates docID from offsets 
        if (parts.length < 1) {
            throw new NumberFormatException("Invalid PostingsEntry format: " + s);
        }
        int docID = Integer.parseInt(parts[0]);
        PostingsEntry entry = new PostingsEntry(docID);
        if (parts.length > 1 && !parts[1].isEmpty()) {
            String[] offsetStrings = parts[1].split(","); // comma seperates offsets within the same document
            for (String offset : offsetStrings) {
                entry.addOffset(Integer.parseInt(offset));
                entry.score++;
            }
        }
        return entry;
    }

}

