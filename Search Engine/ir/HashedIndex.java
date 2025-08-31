/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, 2017
 */  


package ir;

import java.util.HashMap;
import java.util.Iterator;


/**
 *   Implements an inverted index as a Hashtable from words to PostingsLists.
 */
public class HashedIndex implements Index {


    /** The index as a hashtable. */
    private HashMap<String,PostingsList> index = new HashMap<String,PostingsList>();


    /**
     *  Inserts this token in the hashtable.
     */
    public void insert( String token, int docID, int offset ) {
        //
        // YOUR CODE HERE
        //
        PostingsList postingsList = getPostings(token);

        if (postingsList == null) {
            postingsList = new PostingsList();
            postingsList.add(docID, offset);
            index.put(token, postingsList);
        } else {
            if (postingsList.get(postingsList.size() - 1).docID == docID) {
                postingsList.get(postingsList.size() - 1).addOffset(offset); // add offset to existing postingslist
                postingsList.get(postingsList.size() - 1).score++;
            } else {
                postingsList.add(docID, offset); // new docID
            }
        }
    }


    /**
     *  Returns the postings for a specific term, or null
     *  if the term is not in the index.
     */
    public PostingsList getPostings( String token ) {
        //
        // REPLACE THE STATEMENT BELOW WITH YOUR CODE
        //
        return index.get(token);
    }


    /**
     *  No need for cleanup in a HashedIndex.
     */
    public void cleanup() {
    }
}
