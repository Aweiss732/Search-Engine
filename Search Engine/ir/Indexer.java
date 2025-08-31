/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, 2017
 */  

package ir;

import java.io.*;
import java.util.*;
import java.nio.charset.*;


/**
 *   Processes a directory structure and indexes all PDF and text files.
 */
public class Indexer {

    /** The index to be built up by this Indexer. */
    Index index;

    /** K-gram index to be built up by this Indexer */
    KGramIndex kgIndex;

    /** The next docID to be generated. */
    private int lastDocID = 0;

    /** The patterns matching non-standard words (e-mail addresses, etc.) */
    String patterns_file;

    private HashMap<Integer, HashMap<String, Integer>> docTermFrequencies = new HashMap<>();
    private HashMap<String, Integer> docFrequencies = new HashMap<>();


    /* ----------------------------------------------- */


    /** Constructor */
    public Indexer( Index index, KGramIndex kgIndex, String patterns_file ) {
        this.index = index;
        this.kgIndex = kgIndex;
        this.patterns_file = patterns_file;
    }


    /** Generates a new document identifier as an integer. */
    private int generateDocID() {
        return lastDocID++;
    }



    /**
     *  Tokenizes and indexes the file @code{f}. If <code>f</code> is a directory,
     *  all its files and subdirectories are recursively processed.
     */
    public void processFiles( File f, boolean is_indexing ) {
        // do not try to index fs that cannot be read
        if (is_indexing) {
            if ( f.canRead() ) {
                if ( f.isDirectory() ) {
                    String[] fs = f.list();
                    // an IO error could occur
                    if ( fs != null ) {
                        for ( int i=0; i<fs.length; i++ ) {
                            processFiles( new File( f, fs[i] ), is_indexing );
                        }
                    }
                } else {
                    // First register the document and get a docID
                    int docID = generateDocID();
                    if ( docID%1000 == 0 ) System.err.println( "Indexed " + docID + " files" );
                    try {
                        Reader reader = new InputStreamReader( new FileInputStream(f), StandardCharsets.UTF_8 );
                        Tokenizer tok = new Tokenizer( reader, true, false, true, patterns_file );
                        int offset = 0;
                        HashMap<String, Integer> termFreq = new HashMap<>();
                        while ( tok.hasMoreTokens() ) {
                            String token = tok.nextToken();
                            insertIntoIndex( docID, token, offset++ );
                            termFreq.put(token, termFreq.getOrDefault(token, 0) + 1);
                            if (kgIndex != null) kgIndex.insert(token);
                        }
                        docTermFrequencies.put(docID, termFreq);
                        index.docNames.put( docID, f.getPath() );
                        index.docLengths.put( docID, offset );
                        for (String term : termFreq.keySet()) {
                            docFrequencies.put(term, docFrequencies.getOrDefault(term, 0) + 1);
                        }
                        reader.close();
                    } catch ( IOException e ) {
                        System.err.println( "Warning: IOException during indexing." );
                    }
                }
            }
        }
    }

    public void writeEuclideanLengths(String filename) {
        int N = index.docNames.size();
        try (PrintWriter writer = new PrintWriter(filename)) {
            for (int docID : docTermFrequencies.keySet()) {
                HashMap<String, Integer> termFreq = docTermFrequencies.get(docID);
                double sum = 0.0;
                for (Map.Entry<String, Integer> entry : termFreq.entrySet()) {
                    String term = entry.getKey();
                    int tf = entry.getValue();
                    int df = docFrequencies.getOrDefault(term, 0);
                    if (df == 0) continue;
                    double idf = Math.log((double) N / df);
                    sum += Math.pow(tf * idf, 2);
                }
                double euclideanLength = Math.sqrt(sum);
                index.euclideanLengths.put(docID, euclideanLength);
                writer.println(docID + " " + euclideanLength);
            }
        } catch (FileNotFoundException e) {
            System.err.println("Error saving Euclidean lengths: " + e.getMessage());
        }
    }


    /* ----------------------------------------------- */


    /**
     *  Indexes one token.
     */
    public void insertIntoIndex( int docID, String token, int offset ) {
        index.insert( token, docID, offset );
        if (kgIndex != null)
            kgIndex.insert(token);
    }
}

