/*
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 *
 *   Dmytro Kalpakchi, 2018
 */

package ir;

import java.io.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.io.Serializable; // for writing persitent


public class KGramIndex implements Serializable {

    /** Mapping from term ids to actual term strings */
    HashMap<Integer,String> id2term = new HashMap<Integer,String>();

    /** Mapping from term strings to term ids */
    HashMap<String,Integer> term2id = new HashMap<String,Integer>();

    /** Index from k-grams to list of term ids that contain the k-gram */
    HashMap<String,List<KGramPostingsEntry>> index = new HashMap<String,List<KGramPostingsEntry>>();

    /** The ID of the last processed term */
    int lastTermID = -1;

    /** Number of symbols to form a K-gram */
    int K = 3;

    public KGramIndex(int k) {
        K = k;
        if (k <= 0) {
            System.err.println("The K-gram index can't be constructed for a negative K value");
            System.exit(1);
        }
        try {
            readKGramIndex();
        } catch ( FileNotFoundException e ) {
        } catch ( IOException e ) {
            e.printStackTrace();
        }
    }

    /** Generate the ID for an unknown term */
    private int generateTermID() {
        return ++lastTermID;
    }

    public int getK() {
        return K;
    }


    /**
     *  Get intersection of two postings lists
     */
    public List<KGramPostingsEntry> intersect(List<KGramPostingsEntry> p1, List<KGramPostingsEntry> p2) {
        // 
        // YOUR CODE HERE
        //
        List<KGramPostingsEntry> results = new ArrayList<>();
        int i = 0, j = 0;
        while (i < p1.size() && j < p2.size()) {
            KGramPostingsEntry e1 = p1.get(i);
            KGramPostingsEntry e2 = p2.get(j);
            if (e1.tokenID == e2.tokenID) {
                results.add(e1);
                i++;
                j++;
            } else if (e1.tokenID < e2.tokenID) {
                i++;
            } else {
                j++;
            }
        }
        return results;
    }


    /** Inserts all k-grams from a token into the index. */
    public void insert( String token ) {
        //
        // YOUR CODE HERE
        //

        Integer termID = getIDByTerm(token);
        if (termID != null) {
            return;
        }

        termID = generateTermID();
        term2id.put(token, termID);
        id2term.put(termID, token);

        String paddedString = "^" + token + "$";
        List<String> kgrams = new ArrayList<>();
        for (int i = 0; i <= paddedString.length() - K; i++) {
            String kgram = paddedString.substring(i, i + K);
            if (!kgrams.contains(kgram)) {
                kgrams.add(kgram);
                index.computeIfAbsent(kgram, k -> new ArrayList<>()).add(new KGramPostingsEntry(termID));
            }
        }
    }

    /** Get postings for the given k-gram */
    public List<KGramPostingsEntry> getPostings(String kgram) {
        //
        // YOUR CODE HERE
        //

        return index.getOrDefault(kgram, new ArrayList<>());
    }

    /** Get id of a term */
    public Integer getIDByTerm(String term) {
        return term2id.get(term);
    }

    /** Get a term by the given id */
    public String getTermByID(Integer id) {
        return id2term.get(id);
    }

    private static HashMap<String,String> decodeArgs( String[] args ) {
        HashMap<String,String> decodedArgs = new HashMap<String,String>();
        int i=0, j=0;
        while ( i < args.length ) {
            if ( "-p".equals( args[i] )) {
                i++;
                if ( i < args.length ) {
                    decodedArgs.put("patterns_file", args[i++]);
                }
            } else if ( "-f".equals( args[i] )) {
                i++;
                if ( i < args.length ) {
                    decodedArgs.put("file", args[i++]);
                }
            } else if ( "-k".equals( args[i] )) {
                i++;
                if ( i < args.length ) {
                    decodedArgs.put("k", args[i++]);
                }
            } else if ( "-kg".equals( args[i] )) {
                i++;
                if ( i < args.length ) {
                    decodedArgs.put("kgram", args[i++]);
                }
            } else {
                System.err.println( "Unknown option: " + args[i] );
                break;
            }
        }
        return decodedArgs;
    }

    public void writeKGramIndex() {
        try (ObjectOutputStream oos = new ObjectOutputStream(
        new FileOutputStream("./index/kgram_index.txt"))) {
        oos.writeInt(K);
        oos.writeObject(term2id);
        oos.writeObject(id2term);
        oos.writeObject(index);
        oos.writeInt(lastTermID);
    } catch (IOException e) {
        e.printStackTrace();
    }
    }

    private void readKGramIndex() throws IOException {
        File file = new File("./index/kgram_index.txt");
        if (!file.exists()) return;

        try (ObjectInputStream ois = new ObjectInputStream(
            new FileInputStream(file))) {

            int savedK = ois.readInt();
            if (savedK != K) {
                System.err.println("K value mismatch - not loading existing index");
                return;
            }

            term2id = (HashMap<String, Integer>) ois.readObject();
            id2term = (HashMap<Integer, String>) ois.readObject();
            index = (HashMap<String, List<KGramPostingsEntry>>) ois.readObject();
            lastTermID = ois.readInt();
        } catch (ClassNotFoundException | IOException e) {
            throw new IOException("Failed to read kgram index", e);
        }
    }

    public static void main(String[] arguments) throws FileNotFoundException, IOException {
        HashMap<String,String> args = decodeArgs(arguments);

        int k = Integer.parseInt(args.getOrDefault("k", "3"));
        KGramIndex kgIndex = new KGramIndex(k);

        File f = new File(args.get("file"));
        Reader reader = new InputStreamReader( new FileInputStream(f), StandardCharsets.UTF_8 );
        Tokenizer tok = new Tokenizer( reader, true, false, true, args.get("patterns_file") );
        while ( tok.hasMoreTokens() ) {
            String token = tok.nextToken();
            kgIndex.insert(token);
        }

        String[] kgrams = args.get("kgram").split(" ");
        List<KGramPostingsEntry> postings = null;
        for (String kgram : kgrams) {
            if (kgram.length() != k) {
                System.err.println("Cannot search k-gram index: " + kgram.length() + "-gram provided instead of " + k + "-gram");
                System.exit(1);
            }

            if (postings == null) {
                postings = kgIndex.getPostings(kgram);
            } else {
                postings = kgIndex.intersect(postings, kgIndex.getPostings(kgram));
            }
        }
        if (postings == null) {
            System.err.println("Found 0 posting(s)");
        } else {
            int resNum = postings.size();
            System.err.println("Found " + resNum + " posting(s)");
            if (resNum > 10) {
                System.err.println("The first 10 of them are:");
                resNum = 10;
            }
            for (int i = 0; i < resNum; i++) {
                System.err.println(kgIndex.getTermByID(postings.get(i).tokenID));
            }
        }
    }
}
