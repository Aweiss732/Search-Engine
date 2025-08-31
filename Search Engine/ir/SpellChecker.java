/*
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 *
 *   Dmytro Kalpakchi, 2018
 */

package ir;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;


public class SpellChecker {
    /** The regular inverted index to be used by the spell checker */
    Index index;

    /** K-gram index to be used by the spell checker */
    KGramIndex kgIndex;

    /** The auxiliary class for containing the value of your ranking function for a token */
    class KGramStat implements Comparable {
        double score;
        String token;

        KGramStat(String token, double score) {
            this.token = token;
            this.score = score;
        }

        public String getToken() {
            return token;
        }

        public int compareTo(Object other) {
            if (this.score == ((KGramStat)other).score) return 0;
            return this.score < ((KGramStat)other).score ? -1 : 1;
        }

        public String toString() {
            return token + ";" + score;
        }
    }

    /**
     * The threshold for Jaccard coefficient; a candidate spelling
     * correction should pass the threshold in order to be accepted
     */
    private static final double JACCARD_THRESHOLD = 0.4;


    /**
      * The threshold for edit distance for a candidate spelling
      * correction to be accepted.
      */
    private static final int MAX_EDIT_DISTANCE = 2;


    public SpellChecker(Index index, KGramIndex kgIndex) {
        this.index = index;
        this.kgIndex = kgIndex;
    }

    /**
     *  Computes the Jaccard coefficient for two sets A and B, where the size of set A is 
     *  <code>szA</code>, the size of set B is <code>szB</code> and the intersection 
     *  of the two sets contains <code>intersection</code> elements.
     */
    private double jaccard(int szA, int szB, int intersection) {
        //
        // YOUR CODE HERE
        //
        if (szA + szB - intersection == 0) {
            return 0.0;
        }
        return (double) intersection / (szA + szB - intersection);
    }

    /**
     * Computing Levenshtein edit distance using dynamic programming.
     * Allowed operations are:
     *      => insert (cost 1)
     *      => delete (cost 1)
     *      => substitute (cost 2)
     */
    private int editDistance(String s1, String s2) {
        //
        // YOUR CODE HERE
        //

        int m = s1.length();
        int n = s2.length();
        int[][] lev = new int[m + 1][n + 1];

        for (int i = 0; i <= m; i++) {
            lev[i][0] = i;
        }
        for (int j = 0; j <= n; j++) {
            lev[0][j] = j;
        }

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    lev[i][j] = lev[i - 1][j - 1];
                } else {
                    int substitute = lev[i - 1][j - 1] + 2;
                    int delete = lev[i - 1][j] + 1;
                    int insert = lev[i][j - 1] + 1;
                    lev[i][j] = Math.min(substitute, Math.min(delete, insert));
                }
            }
        }
        return lev[m][n];
    }

    /**
     *  Checks spelling of all terms in <code>query</code> and returns up to
     *  <code>limit</code> ranked suggestions for spelling correction.
     */
    public String[] check(Query query, int limit) {
        //
        // YOUR CODE HERE
        //

        List<String> terms = new ArrayList<>();
        for (Query.QueryTerm qt : query.queryterm) {
            terms.add(qt.term);
        }

        if (terms.isEmpty()) {
            return new String[0];
        }

        List<List<KGramStat>> corrections = new ArrayList<>();

        for (String term : terms) {
            PostingsList postings = index.getPostings(term);
            if (postings != null) {
                // term exists in index. add itself as the only candidate with score 1.0
                List<KGramStat> validTerm = new ArrayList<>();
                validTerm.add(new KGramStat(term, 1.0));
                corrections.add(validTerm);
            } else {
                // generate spelling corretions
                List<KGramStat> candidates = generateCandidatesForTerm(term);
                corrections.add(candidates);
            }
        }

        // merge corrections for multiple word queries
        List<KGramStat> mergedCorrections = mergeCorrections(corrections, limit);

        // converts to String[]
        String[] result = new String[mergedCorrections.size()];
        for (int i = 0; i < mergedCorrections.size(); i++) {
            result[i] = mergedCorrections.get(i).getToken();
        }

        return result;
    }

    private Set<String> generateKGrams(String term) {
        String padded = "^" + term + "$";
        Set<String> kgrams = new HashSet<>();
        for (int i = 0; i <= padded.length() - 2; i++) { // k = 2
            kgrams.add(padded.substring(i, i + 2));
        }
        return kgrams;
    }

    private List<KGramStat> generateCandidatesForTerm(String term) {
        Set<String> queryKGrams = generateKGrams(term);
        int querySize = queryKGrams.size();
    

        // for each k-gram in the query term, retrieve all terms containing that k-gram
        Set<Integer> candidateTokenIDs = new HashSet<>();
        for (String kgram : queryKGrams) {
            List<KGramPostingsEntry> postings = kgIndex.getPostings(kgram);
            if (postings != null) {
                for (KGramPostingsEntry entry : postings) {
                    candidateTokenIDs.add(entry.tokenID); // union operation
                }
            }
        }
    
        List<KGramStat> candidates = new ArrayList<>();
        for (Integer tokenID : candidateTokenIDs) {
            String candidateTerm = kgIndex.getTermByID(tokenID);
            if (candidateTerm == null) continue;
    
            Set<String> candidateKGrams = generateKGrams(candidateTerm);
            int candidateSize = candidateKGrams.size();
    
            // intersect kgrams in query with kgrams in candidate
            int intersectionSize = 0;
            for (String kg : queryKGrams) {
                if (candidateKGrams.contains(kg)) intersectionSize++;
            }
    
            double jaccard = jaccard(querySize, candidateSize, intersectionSize);
            if (jaccard < JACCARD_THRESHOLD) continue;
    
            int distance = editDistance(term, candidateTerm);
            if (distance > MAX_EDIT_DISTANCE) continue;
            
            PostingsList postings = index.getPostings(candidateTerm);
            double df = (postings != null) ? postings.size() : 0.0;

            double similarityScore = jaccard * (1.0 / (distance + 1.0));
            double popularityScore = 1.0 + Math.log(df + 1);
            double finalScore = similarityScore * popularityScore;

            candidates.add(new KGramStat(candidateTerm, finalScore));
        }
    
        candidates.sort(Collections.reverseOrder());
        return candidates;
    }

    /**
     *  Merging ranked candidate spelling corrections for all query terms available in
     *  <code>qCorrections</code> into one final merging of query phrases. Returns up
     *  to <code>limit</code> corrected phrases.
     */
    private List<KGramStat> mergeCorrections(List<List<KGramStat>> qCorrections, int limit) {
        //
        // YOUR CODE HERE
        //
        if (qCorrections.isEmpty()) {
            return new ArrayList<>();
        }
    
        // if term has no corrections
        for (List<KGramStat> termCorrections : qCorrections) {
            if (termCorrections.isEmpty()) {
                return new ArrayList<>();
            }
        }
    
        List<KGramStat> merged = new ArrayList<>(qCorrections.get(0));
    
        // iterate over terms
        for (int i = 1; i < qCorrections.size(); i++) {
            List<KGramStat> currentTerm = qCorrections.get(i);
            List<KGramStat> tempMerged = new ArrayList<>();
    
            // create all combinations with current phrase and new term corrections
            for (KGramStat phrase : merged) {
                for (KGramStat termCorrection : currentTerm) {
                    String combinedToken = phrase.token + " " + termCorrection.token;
                    double combinedScore = phrase.score * termCorrection.score; // product of scores
                    tempMerged.add(new KGramStat(combinedToken, combinedScore));
                }
            }
    
            // order from large to small
            Collections.sort(tempMerged, Collections.reverseOrder());
    
            // prune bad candidates
            int beamWidth = limit * 5; // change?
            int keep = Math.min(beamWidth, tempMerged.size());
            merged = new ArrayList<>(tempMerged.subList(0, keep));
        }
    
        int resultSize = Math.min(limit, merged.size());
        return merged.subList(0, resultSize);
    }
}
