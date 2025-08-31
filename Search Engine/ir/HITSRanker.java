/**
 *   Computes the Hubs and Authorities for an every document in a query-specific
 *   link graph, induced by the base set of pages.
 *
 *   @author Dmytro Kalpakchi
 */

package ir;

import java.util.*;
import java.io.*;


public class HITSRanker {

    /**
     *   Max number of iterations for HITS
     */
    final static int MAX_NUMBER_OF_STEPS = 1000;

    /**
     *   Convergence criterion: hub and authority scores do not 
     *   change more that EPSILON from one iteration to another.
     */
    final static double EPSILON = 0.000001;

    /**
     *   The inverted index
     */
    Index index;

    /**
     *   Mapping from the titles to internal document ids used in the links file
     */
    HashMap<String,Integer> titleToId = new HashMap<String,Integer>();
    HashMap<Integer, String> idToTitle = new HashMap<Integer, String>();

    /**
     *   Sparse vector containing hub scores
     */
    HashMap<Integer,Double> hubs;

    /**
     *   Sparse vector containing authority scores
     */
    HashMap<Integer,Double> authorities;

    HashMap<Integer, List<Integer>> outLinks = new HashMap<>();
    HashMap<Integer, List<Integer>> inLinks = new HashMap<>();
    final static double ALPHA = 0.5;

    
    /* --------------------------------------------- */

    /**
     * Constructs the HITSRanker object
     * 
     * A set of linked documents can be presented as a graph.
     * Each page is a node in graph with a distinct nodeID associated with it.
     * There is an edge between two nodes if there is a link between two pages.
     * 
     * Each line in the links file has the following format:
     *  nodeID;outNodeID1,outNodeID2,...,outNodeIDK
     * This means that there are edges between nodeID and outNodeIDi, where i is between 1 and K.
     * 
     * Each line in the titles file has the following format:
     *  nodeID;pageTitle
     *  
     * NOTE: nodeIDs are consistent between these two files, but they are NOT the same
     *       as docIDs used by search engine's Indexer
     *
     * @param      linksFilename   File containing the links of the graph
     * @param      titlesFilename  File containing the mapping between nodeIDs and pages titles
     * @param      index           The inverted index
     */
    public HITSRanker( String linksFilename, String titlesFilename, Index index ) {
        this.index = index;
        readDocs( linksFilename, titlesFilename );
    }


    /* --------------------------------------------- */

    /**
     * A utility function that gets a file name given its path.
     * For example, given the path "davisWiki/hello.f",
     * the function will return "hello.f".
     *
     * @param      path  The file path
     *
     * @return     The file name.
     */
    private String getFileName( String path ) {
        String result = "";
        StringTokenizer tok = new StringTokenizer( path, "\\/" );
        while ( tok.hasMoreTokens() ) {
            result = tok.nextToken();
        }
        return result;
    }


    /**
     * Reads the files describing the graph of the given set of pages.
     *
     * @param      linksFilename   File containing the links of the graph
     * @param      titlesFilename  File containing the mapping between nodeIDs and pages titles
     */
    void readDocs( String linksFilename, String titlesFilename ) {
        //
        // YOUR CODE HERE
        //

        // read title-id mapping
        try (BufferedReader br = new BufferedReader(new FileReader(titlesFilename))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(";", 2);
                if (parts.length < 2) continue;
                int nodeID = Integer.parseInt(parts[0]);
                String title = parts[1];
                titleToId.put(title, nodeID);
                idToTitle.put(nodeID, title);
            }
        } catch (IOException e) {
            System.err.println("Error reading titles file: " + e.getMessage());
        }

        // read links
        outLinks.clear();
        inLinks.clear();
        try (BufferedReader br = new BufferedReader(new FileReader(linksFilename))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(";", 2);
                if (parts.length < 2) continue;
                int nodeID = Integer.parseInt(parts[0]);
                String[] outlinks = parts[1].split(",");

                List<Integer> outList = new ArrayList<>();
                for (String outStr : outlinks) {
                    outStr = outStr.trim();
                    if (outStr.isEmpty()) continue;
                    try {
                        int outNode = Integer.parseInt(outStr);
                        outList.add(outNode);
                        inLinks.computeIfAbsent(outNode, k -> new ArrayList<>()).add(nodeID);
                    } catch (NumberFormatException e) {
                    }
                }
                outLinks.put(nodeID, outList);
            }
        } catch (IOException e) {
            System.err.println("Error reading links file: " + e.getMessage());
        }
    }

    /**
     * Perform HITS iterations until convergence
     *
     * @param      titles  The titles of the documents in the root set
     */
    private void iterate(String[] titles, boolean titlesIsBaseSet) {
        //
        // YOUR CODE HERE
        //
        Set<Integer> baseSet = new HashSet<>();

        for (String title : titles) {
            Integer nodeID = titleToId.get(title);
            if (nodeID != null) {
                baseSet.add(nodeID);
            }
        }

        if (!titlesIsBaseSet) {
            baseSet.addAll(outLinks.keySet());
            baseSet.addAll(inLinks.keySet());
        }

        // initialize
        hubs = new HashMap<>();
        authorities = new HashMap<>();
        for (int node : baseSet) {
            hubs.put(node, 1.0);
            authorities.put(node, 1.0);
        }

        int steps = 0;
        double maxDelta;

        do {
            HashMap<Integer, Double> newAuthorities = new HashMap<>();
            // update authority score
            for (int node : baseSet) {
                double auth = 0.0;
                for (int inNode : inLinks.getOrDefault(node, Collections.emptyList())) {
                    auth += hubs.getOrDefault(inNode, 0.0);
                }
                newAuthorities.put(node, auth);
            }

            HashMap<Integer, Double> newHubs = new HashMap<>();
            // update hub score
            for (int node : baseSet) {
                double hub = 0.0;
                for (int outNode : outLinks.getOrDefault(node, Collections.emptyList())) {
                    hub += authorities.getOrDefault(outNode, 0.0);
                }
                newHubs.put(node, hub);
            }

            // normalizing
            double authNorm = computeNorm(newAuthorities);
            for (int node : newAuthorities.keySet()) {
                newAuthorities.put(node, newAuthorities.get(node) / authNorm);
            }

            double hubNorm = computeNorm(newHubs);
            for (int node : newHubs.keySet()) {
                newHubs.put(node, newHubs.get(node) / hubNorm);
            }

            // convergence critereon
            maxDelta = 0.0;
            for (int node : baseSet) {
                double authDelta = Math.abs(newAuthorities.get(node) - authorities.get(node));
                double hubDelta = Math.abs(newHubs.get(node) - hubs.get(node));
                maxDelta = Math.max(maxDelta, Math.max(authDelta, hubDelta));
            }

            authorities = newAuthorities;
            hubs = newHubs;

            steps++;
        } while (steps < MAX_NUMBER_OF_STEPS && maxDelta > EPSILON);
    }

    private double computeNorm(HashMap<Integer, Double> vector) {
        double sum = 0.0;
        for (double val : vector.values()) {
            sum += val * val;
        }
        double norm = Math.sqrt(sum);
        return norm == 0.0 ? 1.0 : norm;
    }


    /**
     * Rank the documents in the subgraph induced by the documents present
     * in the postings list `post`.
     *
     * @param      post  The list of postings fulfilling a certain information need
     *
     * @return     A list of postings ranked according to the hub and authority scores.
     */
    PostingsList rank(PostingsList post) {
        //
        // YOUR CODE HERE
        //
        if (post == null || post.size() == 0) {
            return new PostingsList();
        }
    
        // root set nodeIDs from the post's docIDs
        Set<Integer> rootSet = new HashSet<>();
        for (int j = 0; j < post.size(); j++) {
            PostingsEntry entry = post.get(j);
            int docID = entry.docID;
            String docName = index.docNames.get(docID);
            Integer nodeID = titleToId.get(getFileName(docName));
            if (nodeID != null) {
                rootSet.add(nodeID);
            }
        }
    
        if (rootSet.isEmpty()) {
            return new PostingsList();
        }
    
        // get base set by adding inlinks and outlinks of root set, not needed
        Set<Integer> baseSet = new HashSet<>(rootSet);
        for (int node : rootSet) {
            baseSet.addAll(outLinks.getOrDefault(node, Collections.emptyList()));
            baseSet.addAll(inLinks.getOrDefault(node, Collections.emptyList()));
        }
    
        // titles array for iterate method
        String[] titles = new String[baseSet.size()];
        int i = 0;
        for (int nodeID : baseSet) {
            String title = idToTitle.get(nodeID);
            if (title != null) {
                titles[i++] = title;
            }
        }
        titles = Arrays.copyOf(titles, i); // trim nulls
    
        iterate(titles, true);
    
        PostingsList results = new PostingsList();
        for (int nodeID : baseSet) {
            String title = idToTitle.get(nodeID);
            if (title == null) continue;
    
            // find docID in the index that corresponds to this title
            for (Map.Entry<Integer, String> entry : index.docNames.entrySet()) {
                if (entry.getValue().endsWith(title)) {
                    double hubScore = hubs.getOrDefault(nodeID, 0.0);
                    double authScore = authorities.getOrDefault(nodeID, 0.0);
                    double combinedScore = ALPHA * hubScore + (1-ALPHA) * authScore;
                    //double combinedScore = (5 * hubScore) * (5 * authScore);

                    PostingsEntry pe = new PostingsEntry(entry.getKey());
                    pe.setScore(combinedScore);
                    results.add(pe);
                    break;
                }
            }
        }

        results.sort();
        return results;
    }


    /**
     * Sort a hash map by values in the descending order
     *
     * @param      map    A hash map to sorted
     *
     * @return     A hash map sorted by values
     */
    private HashMap<Integer,Double> sortHashMapByValue(HashMap<Integer,Double> map) {
        if (map == null) {
            return null;
        } else {
            List<Map.Entry<Integer,Double> > list = new ArrayList<Map.Entry<Integer,Double> >(map.entrySet());
      
            Collections.sort(list, new Comparator<Map.Entry<Integer,Double>>() {
                public int compare(Map.Entry<Integer,Double> o1, Map.Entry<Integer,Double> o2) { 
                    return (o2.getValue()).compareTo(o1.getValue()); 
                } 
            }); 
              
            HashMap<Integer,Double> res = new LinkedHashMap<Integer,Double>(); 
            for (Map.Entry<Integer,Double> el : list) { 
                res.put(el.getKey(), el.getValue()); 
            }
            return res;
        }
    } 


    /**
     * Write the first `k` entries of a hash map `map` to the file `fname`.
     *
     * @param      map        A hash map
     * @param      fname      The filename
     * @param      k          A number of entries to write
     */
    void writeToFile(HashMap<Integer,Double> map, String fname, int k) {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(fname));
            
            if (map != null) {
                int i = 0;
                for (Map.Entry<Integer,Double> e : map.entrySet()) {
                    i++;
                    writer.write(e.getKey() + ": " + String.format("%.5g%n", e.getValue()));
                    if (i >= k) break;
                }
            }
            writer.close();
        } catch (IOException e) {}
    }


    /**
     * Rank all the documents in the links file. Produces two files:
     *  hubs_top_30.txt with documents containing top 30 hub scores
     *  authorities_top_30.txt with documents containing top 30 authority scores
     */
    void rank() {
        iterate(titleToId.keySet().toArray(new String[0]), false);
        HashMap<Integer,Double> sortedHubs = sortHashMapByValue(hubs);
        HashMap<Integer,Double> sortedAuthorities = sortHashMapByValue(authorities);
        writeToFile(sortedHubs, "hubs_top_30.txt", 30);
        writeToFile(sortedAuthorities, "authorities_top_30.txt", 30);
    }


    /* --------------------------------------------- */


    public static void main( String[] args ) {
        if ( args.length != 2 ) {
            System.err.println( "Please give the names of the link and title files" );
        }
        else {
            HITSRanker hr = new HITSRanker( args[0], args[1], null );
            hr.rank();
        }
    }
} 