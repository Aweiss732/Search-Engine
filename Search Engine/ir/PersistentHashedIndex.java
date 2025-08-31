/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, KTH, 2018
 */  

package ir;

import java.io.*;
import java.util.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;


/*
 *   Implements an inverted index as a hashtable on disk.
 *   
 *   Both the words (the dictionary) and the data (the postings list) are
 *   stored in RandomAccessFiles that permit fast (almost constant-time)
 *   disk seeks. 
 *
 *   When words are read and indexed, they are first put in an ordinary,
 *   main-memory HashMap. When all words are read, the index is committed
 *   to disk.
 */
public class PersistentHashedIndex implements Index {

    /** The directory where the persistent index files are stored. */
    public static final String INDEXDIR = "./index";

    /** The dictionary file name */
    public static final String DICTIONARY_FNAME = "dictionary";

    /** The data file name */
    public static final String DATA_FNAME = "data";

    /** The terms file name */
    public static final String TERMS_FNAME = "terms";

    /** The doc info file name */
    public static final String DOCINFO_FNAME = "docInfo";

    protected static final String DELIMITER = "<"; // need rare symbol

    /** The dictionary hash table on disk can fit this many entries. */
    public static final long TABLESIZE = 611953L; // davis table size
    //public static final long TABLESIZE = 3499999L;  // guardian table size, prime number close to 3500000

    /** The dictionary hash table is stored in this file. */
    RandomAccessFile dictionaryFile;

    /** The data (the PostingsLists) are stored in this file. */
    RandomAccessFile dataFile;

    /** Pointer to the first free memory cell in the data file. */
    long free = 0L;

    /** The cache as a main-memory hash map. */
    HashMap<String,PostingsList> index = new HashMap<String,PostingsList>();


    // ===================================================================

    /**
     *   A helper class representing one entry in the dictionary hashtable.
     */ 
    public static class Entry {
        //
        //  YOUR CODE HERE
        //
        long pointer;
        int size;

        public Entry(long pointer, int size) {
            this.pointer = pointer;
            this.size = size;
        }

        public byte[] toByte() {
            ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES + Integer.BYTES);
            buffer.putLong(pointer);
            buffer.putInt(size);
            return buffer.array();
        }

        public static Entry fromByte(byte[] bytes) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            long pointer = buffer.getLong();
            int size = buffer.getInt();
            if (pointer == 0 && size == 0) {
                return null; // empty entry
            }
            return new Entry(pointer, size);
        }

    }


    // ==================================================================

    
    /**
     *  Constructor. Opens the dictionary file and the data file.
     *  If these files don't exist, they will be created. 
     */
    public PersistentHashedIndex() {
        try {
            dictionaryFile = new RandomAccessFile( INDEXDIR + "/" + DICTIONARY_FNAME, "rw" );
            dataFile = new RandomAccessFile( INDEXDIR + "/" + DATA_FNAME, "rw" );
        } catch ( IOException e ) {
            e.printStackTrace();
        }

        try {
            readDocInfo();
        } catch ( FileNotFoundException e ) {
        } catch ( IOException e ) {
            e.printStackTrace();
        }
    }

    /**
     *  Writes data to the data file at a specified place.
     *
     *  @return The number of bytes written.
     */ 
    int writeData( String dataString, long ptr ) {
        try {
            dataFile.seek( ptr ); 
            byte[] data = dataString.getBytes();
            dataFile.write( data );
            return data.length;
        } catch ( IOException e ) {
            e.printStackTrace();
            return -1;
        }
    }


    /**
     *  Reads data from the data file
     */ 
    String readData( long ptr, int size ) {
        try {
            dataFile.seek( ptr );
            byte[] data = new byte[size];
            dataFile.readFully( data );
            return new String(data);
        } catch ( IOException e ) {
            e.printStackTrace();
            return null;
        }
    }


    // ==================================================================
    //
    //  Reading and writing to the dictionary file.

    /*
     *  Writes an entry to the dictionary hash table file. 
     *
     *  @param entry The key of this entry is assumed to have a fixed length
     *  @param ptr   The place in the dictionary file to store the entry
     */
    void writeEntry( RandomAccessFile dictfile, Entry entry, long ptr) {
        //
        //  YOUR CODE HERE
        //
        try {
            dictfile.seek(ptr);
            dictfile.write(entry.toByte());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     *  Reads an entry from the dictionary file.
     *
     *  @param ptr The place in the dictionary file where to start reading.
     */
    Entry readEntry( RandomAccessFile dictfile, long ptr ) {   
        //
        //  REPLACE THE STATEMENT BELOW WITH YOUR CODE 
        //
        try {
            dictfile.seek(ptr);
            byte[] buffer = new byte[Long.BYTES + Integer.BYTES]; 
            dictfile.readFully(buffer);
            return Entry.fromByte(buffer);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }


    // ==================================================================

    /**
     *  Writes the document names and document lengths to file.
     *
     * @throws IOException  { exception_description }
     */
    private void writeDocInfo() throws IOException {
        FileOutputStream fout = new FileOutputStream( INDEXDIR + "/docInfo" );
        for ( Map.Entry<Integer,String> entry : docNames.entrySet() ) {
            Integer key = entry.getKey();
            String docInfoEntry = key + ";" + entry.getValue() + ";" + docLengths.get(key) + "\n";
            fout.write( docInfoEntry.getBytes() );
        }
        fout.close();
    }


    /**
     *  Reads the document names and document lengths from file, and
     *  put them in the appropriate data structures.
     *
     * @throws     IOException  { exception_description }
     */
    private void readDocInfo() throws IOException {
        File file = new File( INDEXDIR + "/docInfo" );
        FileReader freader = new FileReader(file);
        try ( BufferedReader br = new BufferedReader(freader) ) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] data = line.split(";");
                docNames.put( Integer.parseInt(data[0]), data[1] );
                docLengths.put( Integer.parseInt(data[0]), Integer.parseInt(data[2]) );
            }
        }
        freader.close();

        try (BufferedReader br = new BufferedReader(new FileReader("./index/euclidean_lengths.txt"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(" ");
                int docID = Integer.parseInt(parts[0]);
                double len = Double.parseDouble(parts[1]);
                euclideanLengths.put(docID, len);
            }
        } catch (IOException e) {
            System.err.println("Error loading Euclidean lengths: " + e.getMessage());
        }
    }


    /**
     *  Write the index to files.
     */
    public void writeIndex(boolean scalable) {
        int collisions = 0;
        try {
            // Write the 'docNames' and 'docLengths' hash maps to a file
            if (!scalable) {
                writeDocInfo(); // dont run this if scalable hashed index
            }

            // Write the dictionary and the postings list

            // 
            //  YOUR CODE HERE
            //
            dictionaryFile.setLength(0);
            dictionaryFile.setLength(TABLESIZE * (Long.BYTES + Integer.BYTES));
            for (Map.Entry<String, PostingsList> entry : index.entrySet()) {
                String term = entry.getKey();
                PostingsList postingList = entry.getValue();
                String postingString = term + DELIMITER + postingList + "\n";

                long dataPointer = free;
                long hash = hashFunction(term);
                long dictPointer = hash * (Long.BYTES + Integer.BYTES);
                int size = writeData(postingString, free);
                free += size;

                Entry dictEntry = new Entry(dataPointer, size);

                while(true) {
                    Entry existingEntry = readEntry(dictionaryFile, dictPointer);
                    if (existingEntry == null) {
                        writeEntry(dictionaryFile, dictEntry, dictPointer);
                        break;
                    } else {
                        collisions++;
                        dictPointer += (Long.BYTES + Integer.BYTES);
                        if (dictPointer >= TABLESIZE * (Long.BYTES + Integer.BYTES)) {
                            dictPointer = 0; // wrap around
                        }
                    }
                }
            }
        } catch ( IOException e ) {
            e.printStackTrace();
        }
        System.err.println( collisions + " collisions." );
    }

    public long hashFunction(String term) {
        return (term.hashCode() & 0x7FFFFFFF) % TABLESIZE;
    }


    // ==================================================================


    /**
     *  Returns the postings for a specific term, or null
     *  if the term is not in the index.
     */
    public PostingsList getPostings( String token ) {
        //
        //  REPLACE THE STATEMENT BELOW WITH YOUR CODE
        //
        long hash = hashFunction(token);
        long dictPointer = hash * (Long.BYTES + Integer.BYTES);

        while(true) {
            Entry entry = readEntry(dictionaryFile, dictPointer);
            if(entry == null) {
                return null;
            }
            String postingString = readData(entry.pointer, entry.size);
            String[] parts = postingString.split(DELIMITER, 2);  // Split into term and postings
            if (parts.length == 2 && parts[0].equals(token)) {
                return PostingsList.fromString(parts[1]);
            }
            dictPointer += (Long.BYTES + Integer.BYTES);
            if (dictPointer >= TABLESIZE * (Long.BYTES + Integer.BYTES)) {
                dictPointer = 0; // wrap around
            }
        }
    }


    /**
     *  Inserts this token in the main-memory hashtable.
     */
    public void insert( String token, int docID, int offset ) {
        //
        // YOUR CODE HERE
        //
        PostingsList postingsList = index.get(token);

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
     *  Write index to file after indexing is done.
     */
    public void cleanup() {
        System.err.println( index.keySet().size() + " unique words" );
        System.err.print( "Writing index to disk..." );
        writeIndex(false);
        System.err.println( "done!" );
    }
}