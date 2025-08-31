/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, 2017
 */  

package ir;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.StringTokenizer;
import java.util.Iterator;
import java.util.Map;
import java.nio.charset.*;
import java.io.*;


/**
 *  A class for representing a query as a list of words, each of which has
 *  an associated weight.
 */
public class Query {

    /**
     *  Help class to represent one query term, with its associated weight. 
     */
    class QueryTerm {
        String term;
        double weight;
        QueryTerm( String t, double w ) {
            term = t;
            weight = w;
        }
    }

    /** 
     *  Representation of the query as a list of terms with associated weights.
     *  In assignments 1 and 2, the weight of each term will always be 1.
     */
    public ArrayList<QueryTerm> queryterm = new ArrayList<QueryTerm>();

    /**  
     *  Relevance feedback constant alpha (= weight of original query terms). 
     *  Should be between 0 and 1.
     *  (only used in assignment 3).
     */
    double alpha = 0.2;

    /**  
     *  Relevance feedback constant beta (= weight of query terms obtained by
     *  feedback from the user). 
     *  (only used in assignment 3).
     */
    double beta = 1 - alpha;
    
    
    /**
     *  Creates a new empty Query 
     */
    public Query() {
    }
    
    
    /**
     *  Creates a new Query from a string of words
     */
    public Query( String queryString  ) {
        StringTokenizer tok = new StringTokenizer( queryString );
        while ( tok.hasMoreTokens() ) {
            queryterm.add( new QueryTerm(tok.nextToken(), 1.0) );
        }    
    }
    
    
    /**
     *  Returns the number of terms
     */
    public int size() {
        return queryterm.size();
    }
    
    
    /**
     *  Returns the Manhattan query length
     */
    public double length() {
        double len = 0;
        for ( QueryTerm t : queryterm ) {
            len += t.weight; 
        }
        return len;
    }
    
    
    /**
     *  Returns a copy of the Query
     */
    public Query copy() {
        Query queryCopy = new Query();
        for ( QueryTerm t : queryterm ) {
            queryCopy.queryterm.add( new QueryTerm(t.term, t.weight) );
        }
        return queryCopy;
    }
    
    
    /**
     *  Expands the Query using Relevance Feedback
     *
     *  @param results The results of the previous query.
     *  @param docIsRelevant A boolean array representing which query results the user deemed relevant.
     *  @param engine The search engine object
     */
    public void relevanceFeedback( PostingsList results, boolean[] docIsRelevant, Engine engine ) {
        //
        //  YOUR CODE HERE
        //
        if (results == null || docIsRelevant == null || engine == null) return;

        // get relevant docs
        ArrayList<Integer> relevantDocs = new ArrayList<>();
        for (int i = 0; i < docIsRelevant.length; i++) {
            if (docIsRelevant[i] && i < results.size()) {
                relevantDocs.add(results.get(i).docID);
            }
        }
        
        int R = relevantDocs.size();
        if (R == 0) return;

        // number of documents
        int N = engine.index.docNames.size();

        // calculating feedback weights
        HashMap<String, Double> feedbackWeights = new HashMap<>();
        for (int docID : relevantDocs) {
            String document = engine.index.docNames.get(docID);
            HashMap<String, Integer> terms = processDocument(document);
            if (terms == null) continue;

            for (Map.Entry<String, Integer> entry : terms.entrySet()) {
                String term = entry.getKey();
                int tf = entry.getValue(); // term frequency in document 
                
                // document frequency from postingslist
                PostingsList pl = engine.index.getPostings(term);
                int df = pl != null ? pl.size() : 0;
                if (df == 0) continue;
                
                double idf = Math.log(N / (double) df);
                double weight = (beta / R) * (tf * idf); // β*<weight of term in doc>/<number of relevant documents>
                
                feedbackWeights.merge(term, weight, Double::sum); // store weight of the term
            }
        }

        // Rocchio formula
        // scale original query
        for (QueryTerm qt : queryterm) {
            qt.weight *= alpha;
        }
        
        // adding feedback terms
        for (Map.Entry<String, Double> entry : feedbackWeights.entrySet()) {
            String term = entry.getKey();
            double weight = entry.getValue();
            
            boolean found = false;
            for (QueryTerm qt : queryterm) {
                if (qt.term.equals(term)) {
                    qt.weight += weight;
                    found = true;
                    break;
                }
            }
            if (!found) {
                queryterm.add(new QueryTerm(term, weight));
            }
        }
    }
    private HashMap<String, Integer> processDocument(String filename) {
        HashMap<String, Integer> terms = new HashMap<>();
        try (Reader reader = new InputStreamReader(new FileInputStream(filename), StandardCharsets.UTF_8)) {
            Tokenizer tok = new Tokenizer(reader, true, false, true, "patterns.txt");
            while (tok.hasMoreTokens()) {
                String token = tok.nextToken();
                terms.put(token, terms.getOrDefault(token, 0) + 1);
            }
        } catch (IOException e) {
            System.err.println("Error processing document: " + filename);
        }
        return terms;
    }
}


