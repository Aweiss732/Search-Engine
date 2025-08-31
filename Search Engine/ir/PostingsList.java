/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, 2017
 */  

package ir;

import java.util.ArrayList;
import java.util.StringJoiner;

public class PostingsList {
    
    /** The postings list */
    public ArrayList<PostingsEntry> list = new ArrayList<PostingsEntry>();


    /** Number of postings in this list. */
    public int size() {
    return list.size();
    }

    /** Returns the ith posting. */
    public PostingsEntry get( int i ) {
    return list.get( i );
    }

    // 
    //  YOUR CODE HERE
    //

    public void add(int docID, int offset) {
        for (PostingsEntry postingEntry : list) {
            if (postingEntry.docID == docID) {
                postingEntry.addOffset(offset);
                postingEntry.score++;
                return;
            }
        }

        PostingsEntry newEntry = new PostingsEntry(docID);
        newEntry.addOffset(offset);
        newEntry.score++;
        list.add(newEntry);
    }

    public void add(PostingsEntry entry) {
        list.add(entry);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            sb.append(list.get(i).toString());
            if (i < list.size() - 1) {
                sb.append("-");
            }
        }
        return sb.toString();
    }
    
    public static PostingsList fromString(String s) {
        PostingsList postingsList = new PostingsList();
        s = s.trim();
        if (!s.isEmpty()) {
            String[] entryStrings = s.split("-");
            for (String entryString : entryStrings) {
                if (!entryString.matches("^\\d+:.*")) {
                    throw new NumberFormatException("Invalid PostingsEntry: " + entryString);
                }
                PostingsEntry entry = PostingsEntry.fromString(entryString);
                postingsList.add(entry);
            }
        }
        return postingsList;
    }

    public void sort() {
        java.util.Collections.sort(list);
    }
}

