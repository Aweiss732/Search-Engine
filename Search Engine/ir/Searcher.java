/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, 2017
 */  

package ir;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 *  Searches an index for results of a query.
 */
public class Searcher {

    /** The index to be searched by this Searcher. */
    Index index;

    /** The k-gram index to be searched by this Searcher */
    KGramIndex kgIndex;

    // for the 2.8 part
    HITSRanker hitsRanker;

    /** A file that stores all the pagerank stores, added for 2.5 */
    private HashMap<Integer, Double> pagerankMap = new HashMap<>();
    
    /** Constructor */
    public Searcher( Index index, KGramIndex kgIndex ) {
        this.index = index;
        this.kgIndex = kgIndex;
        hitsRanker = new HITSRanker("C:\\Users\\andre\\Desktop\\DD2477\\assignment2\\pagerank\\linksDavis.txt", "C:\\Users\\andre\\Desktop\\DD2477\\assignment2\\pagerank\\davisTitles.txt", index);
    }

    private void loadPageRankScores(String filename) {
        HashMap<String, Integer> nameToDocID = new HashMap<>();
        for (Map.Entry<Integer, String> entry : index.docNames.entrySet()) {
            nameToDocID.put(entry.getValue(), entry.getKey());
        }
        
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\s+");
                if (parts.length < 2) continue;
                String realName = parts[0];
                double score = Double.parseDouble(parts[1]);
                if (nameToDocID.containsKey(realName)) {
                    int docID = nameToDocID.get(realName);
                    pagerankMap.put(docID, score);
                } else {
                    System.err.println("document \"" + realName + "\" not found in index");
                }
            }
            System.err.println("Loaded PageRank scores for " + pagerankMap.size() + " documents");
        } catch (IOException e) {
            System.err.println("Error loading PageRank file: " + e.getMessage());
        }
}

    /**
     *  Searches the index for postings matching the query.
     *  @return A postings list representing the result of the query.
     */
    public PostingsList search( Query query, QueryType queryType, RankingType rankingType, NormalizationType normType ) { 
        //
        //  REPLACE THE STATEMENT BELOW WITH YOUR CODE
        //
        if (query.queryterm.size() == 0) {
            return null;
        }

        //----------------------------------------------------------------------------
        /* List<KGramPostingsEntry> postings = null;
        for (Query.QueryTerm queryTerm : query.queryterm) {
            if (queryTerm.term.length() != kgIndex.getK()) {
                System.err.println("Cannot search k-gram index: " + queryTerm.term.length() + "-gram provided instead of " + kgIndex.getK() + "-gram");
                System.exit(1);
            }

            if (postings == null) {
                postings = kgIndex.getPostings(queryTerm.term);
            } else {
                postings = kgIndex.intersect(postings, kgIndex.getPostings(queryTerm.term));
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
        } */

        //----------------------------------------------------------------------------
        boolean isWildcard = query.queryterm.stream().anyMatch(qt -> qt.term.contains("*"));
        
        Query expandedQuery;
        if (isWildcard && queryType == QueryType.RANKED_QUERY) {
            expandedQuery = expandWildcardQuery(query);
        } else {
            expandedQuery = query;
        }
        

        if (queryType == QueryType.INTERSECTION_QUERY) {
            if (isWildcard) {
                return intersectQueryWildcard(expandedQuery);
            }
            return intersectQuery(expandedQuery);

        } else if (queryType == QueryType.PHRASE_QUERY) {
            if (isWildcard) {
                return phraseQueryWildcard(expandedQuery);
            } else 
            return phraseQuery(expandedQuery);

        } else if (queryType == QueryType.RANKED_QUERY) {
            return rankedQuery(expandedQuery, rankingType, normType);
        }

        return null;
    }

    private Query expandWildcardQuery(Query originalQuery) {
        Query expandedQuery = new Query();
        for (Query.QueryTerm qt : originalQuery.queryterm) {
            if (qt.term.contains("*")) {
                List<String> expandedTerms = expandWildcardTerm(qt.term, kgIndex);
                for (String term : expandedTerms) {
                    expandedQuery.queryterm.add(expandedQuery.new QueryTerm(term, qt.weight));
                }
            } else {
                expandedQuery.queryterm.add(qt);
            }
        }
        return expandedQuery;
    }
    
    private List<String> expandWildcardTerm(String wildcardTerm, KGramIndex kgIndex) {
        Set<String> terms = new HashSet<>();
        int wildcardPos = wildcardTerm.indexOf('*');
    
        String prefix = wildcardTerm.substring(0, wildcardPos);
        String suffix = wildcardTerm.substring(wildcardPos + 1);
        
        // prefix wildcard (*less)
        if (prefix.isEmpty()) {
            String suffixKGram = suffix.length() >= 2 ? 
                suffix.substring(0, 2) : 
                suffix + "$";
            List<KGramPostingsEntry> postings = kgIndex.getPostings(suffixKGram);
            for (KGramPostingsEntry entry : postings) {
                String term = kgIndex.getTermByID(entry.tokenID);
                if (term.endsWith(suffix)) terms.add(term);
            }
        }
        // suffix wildcard (red*)
        else if (suffix.isEmpty()) {
            String prefixKGram = prefix.length() >= 2 ? 
                prefix.substring(prefix.length() - 2) : 
                "^" + prefix;
            List<KGramPostingsEntry> postings = kgIndex.getPostings(prefixKGram);
            for (KGramPostingsEntry entry : postings) {
                String term = kgIndex.getTermByID(entry.tokenID);
                if (term.startsWith(prefix)) terms.add(term);
            }
        }
        // middle wildcard (zom*ie)
        else {
            // k-grams around the *
            String prefixKGram = prefix.length() >= 2 ? 
                prefix.substring(prefix.length() - 2) : 
                "^" + prefix;
            String suffixKGram = suffix.length() >= 2 ? 
                suffix.substring(0, 2) : 
                suffix + "$";
            
            List<KGramPostingsEntry> prefixPostings = kgIndex.getPostings(prefixKGram);
            List<KGramPostingsEntry> suffixPostings = kgIndex.getPostings(suffixKGram);
            List<KGramPostingsEntry> combined = kgIndex.intersect(prefixPostings, suffixPostings);
            
            // filtering by full pattern
            String pattern = "^" + prefix + ".*" + suffix + "$";
            for (KGramPostingsEntry entry : combined) {
                String term = kgIndex.getTermByID(entry.tokenID);
                if (term.matches(pattern)) terms.add(term);
            }
        }
        
        return new ArrayList<>(terms);
    }

    private PostingsList mergePostingLists(PostingsList postingList1, PostingsList postingList2) {
        PostingsList mergedPostingList = new PostingsList();
        int i = 0, j = 0;
        
        while (i < postingList1.size() && j < postingList2.size()) {
            PostingsEntry e1 = postingList1.get(i);
            PostingsEntry e2 = postingList2.get(j);
            
            if (e1.docID == e2.docID) {
                mergedPostingList.add(mergeEntries(e1, e2));
                i++;
                j++;
            } else if (e1.docID < e2.docID) {
                mergedPostingList.add(e1);
                i++;
            } else {
                mergedPostingList.add(e2);
                j++;
            }
        }
        
        while (i < postingList1.size()) mergedPostingList.add(postingList1.get(i++));
        while (j < postingList2.size()) mergedPostingList.add(postingList2.get(j++));
        
        return mergedPostingList;
    }

    private PostingsEntry mergeEntries(PostingsEntry e1, PostingsEntry e2) {
        PostingsEntry mergedEntry = new PostingsEntry(e1.docID);
        mergedEntry.setScore(e1.score + e2.score);
        mergedEntry.addOffsets(new ArrayList<>(e1.getOffsets()));
        mergedEntry.addOffsets(new ArrayList<>(e2.getOffsets()));
        return mergedEntry;
    }

    private PostingsList intersectQueryWildcard(Query query) {
        List<PostingsList>  allPostings = new ArrayList<>();

        for (Query.QueryTerm qt : query.queryterm) {
            PostingsList newPostings = new PostingsList();
            if (qt.term.contains("*")) {
                List<String> expandedTerms = expandWildcardTerm(qt.term, kgIndex);
                for (String term : expandedTerms) {
                    newPostings = mergePostingLists(newPostings, index.getPostings(term));
                }
            } else {
                newPostings = mergePostingLists(newPostings, index.getPostings(qt.term));
            }
            allPostings.add(newPostings);
        }

        PostingsList result = allPostings.get(0);
        for (int i = 1; i < allPostings.size(); i++) {
            result = intersect(result, allPostings.get(i));
        }
        return result;
    }

    private PostingsList intersectQuery( Query query ) {
        PostingsList searchQueries = index.getPostings(query.queryterm.get(0).term);

        for (int i = 1; i < query.queryterm.size(); i++) {
            String term = query.queryterm.get(i).term;
            PostingsList nextPostingsList = index.getPostings(term);

            if (nextPostingsList != null) {
                searchQueries = intersect(searchQueries, nextPostingsList);
            } else {
                return null;
            }
            
        }

        return searchQueries;
    }

    private PostingsList intersect( PostingsList p1, PostingsList p2 ) {
        PostingsList results = new PostingsList();
        int i = 0;
        int j = 0;

        if (p1 == null || p1 == null) {
            return results;
        }

        while (i < p1.size() && j < p2.size()) {
            PostingsEntry e1 = p1.get(i);
            PostingsEntry e2 = p2.get(j);

            if (e1.docID == e2.docID) {
                results.add(e1);
                i++;
                j++;
            }
            else if (e1.docID > e2.docID) {
                j++; // advance second pointer
            }
            else {
                i++; // advance first pointer
            }
        }

        return results;
    }

    private PostingsList phraseQueryWildcard(Query query) {
        List<PostingsList>  allPostings = new ArrayList<>();

        for (Query.QueryTerm qt : query.queryterm) {
            PostingsList newPostings = new PostingsList();
            if (qt.term.contains("*")) {
                List<String> expandedTerms = expandWildcardTerm(qt.term, kgIndex);
                for (String term : expandedTerms) {
                    newPostings = mergePostingLists(newPostings, index.getPostings(term));
                }
            } else {
                newPostings = mergePostingLists(newPostings, index.getPostings(qt.term));
            }
            allPostings.add(newPostings);
        }

        PostingsList result = allPostings.get(0);
        for (int i = 1; i < allPostings.size(); i++) {
            result = phraseIntersect(result, allPostings.get(i));
        }
        return result;
        
    }

    private PostingsList phraseQuery(Query query) {
        PostingsList searchQueries = index.getPostings(query.queryterm.get(0).term);

        for (int i = 1; i < query.queryterm.size(); i++) {
            String term = query.queryterm.get(i).term;
            PostingsList nextPostingsList = index.getPostings(term);

            if (nextPostingsList != null) {
                searchQueries = phraseIntersect(searchQueries, nextPostingsList);
            } else {
                return null;
            }
        }

        return searchQueries;
    }

    private PostingsList phraseIntersect( PostingsList p1, PostingsList p2 ) {
        PostingsList results = new PostingsList();
        int i = 0;
        int j = 0;

        if (p1 == null || p1 == null) {
            return results;
        }

        while (i < p1.size() && j < p2.size()) {
            PostingsEntry e1 = p1.get(i);
            PostingsEntry e2 = p2.get(j);

            if (e1.docID == e2.docID) {
                PostingsEntry entry = new PostingsEntry(e1.docID);

                for (int k : e1.getOffsets()) {
                    for (int l : e2.getOffsets()) {
                        if (k + 1 == l) {
                            entry.addOffset(l);
                        }
                    }
                }
                
                if (entry.getOffsets().size() > 0) {
                    results.add(entry);
                }

                i++;
                j++;
            }
            else if (e1.docID > e2.docID) {
                j++; // advance second pointer
            }
            else {
                i++; // advance first pointer
            }
        }

        return results;
    }

    private PostingsList rankedQuery( Query query, RankingType rankingType, NormalizationType normType) {
        if (rankingType != RankingType.HITS) {
            if (pagerankMap.isEmpty()) {
                loadPageRankScores("C:\\Users\\andre\\Desktop\\DD2477\\assignment2\\pagerank\\pagerank_scores_davis.txt");
            }
            PostingsList results = new PostingsList();
            int N = index.docNames.size();
            HashMap<Integer, Double> scores = new HashMap<>();

            for (int i = 0; i < query.queryterm.size(); i++) {
                String term = query.queryterm.get(i).term;
                PostingsList nextPostingsList = index.getPostings(term);

                if (nextPostingsList != null) {
                    int df = nextPostingsList.size();
                    double idf = Math.log((double) N / df);
                    for (int j = 0; j < nextPostingsList.size(); j++) {
                        PostingsEntry entry = nextPostingsList.get(j);
                        int docID = entry.docID;
                        double tf = entry.score;
                        double lenD;
                        if (normType == NormalizationType.EUCLIDEAN) {
                            lenD = index.euclideanLengths.get(docID);
                        } else {
                            lenD = index.docLengths.get(docID);
                        }
                        double tf_idf = tf * idf * query.queryterm.get(i).weight / lenD;
                        if (scores.containsKey(docID)) { // add score to document if doc already exist
                            scores.replace(docID, scores.get(docID) + tf_idf);
                        } else {
                            scores.put(docID, tf_idf);
                        }
                    }
                }
            } 

            for (Integer docID : scores.keySet()) {
                double tfScore = scores.get(docID);
                double prScore = pagerankMap.getOrDefault(docID, 0.0);
                double finalScore;
                switch (rankingType) {
                    case TF_IDF:
                        finalScore = tfScore;
                        break;
                    case PAGERANK:
                        finalScore = prScore;
                        break;
                    case COMBINATION:
                        finalScore = 1 * tfScore + 500 * prScore; // change these weight values
                        break;
                    default:
                        finalScore = tfScore;
                    }
                PostingsEntry entry = new PostingsEntry(docID);
                entry.setScore(finalScore);
                results.add(entry);
            }

            results.sort();
            return results;
        } else {
            return hitsRanking(query);
        }
    }

    private PostingsList hitsRanking( Query query ) {
        Set<Integer> rootDocIDs = new HashSet<>();

        for (int i = 0; i < query.queryterm.size(); i++) {
            String term = query.queryterm.get(i).term;
            PostingsList nextPostingsList = index.getPostings(term);
            if (nextPostingsList != null) {
                for (int j = 0; j < nextPostingsList.size(); j++) {
                    PostingsEntry entry = nextPostingsList.get(j);
                    rootDocIDs.add(entry.docID);
                }
            }
        }
        PostingsList rootSet = new PostingsList();
        for (int docID : rootDocIDs) {
            rootSet.add(new PostingsEntry(docID));
        }

        return hitsRanker.rank(rootSet);
    }
}