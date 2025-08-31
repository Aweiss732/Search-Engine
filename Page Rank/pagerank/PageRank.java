import java.util.*;
import java.io.*;

public class PageRank {

    /**  
     *   Maximal number of documents. We're assuming here that we
     *   don't have more docs than we can keep in main memory.
     */
    final static int MAX_NUMBER_OF_DOCS = 2000000;

    /**
     *   Mapping from document names to document numbers.
     */
    HashMap<String,Integer> docNumber = new HashMap<String,Integer>();

    /**
     *   Mapping from document numbers to document names
     */
    String[] docName = new String[MAX_NUMBER_OF_DOCS];

    /**  
     *   A memory-efficient representation of the transition matrix.
     *   The outlinks are represented as a HashMap, whose keys are 
     *   the numbers of the documents linked from.<p>
     *
     *   The value corresponding to key i is a HashMap whose keys are 
     *   all the numbers of documents j that i links to.<p>
     *
     *   If there are no outlinks from i, then the value corresponding 
     *   key i is null.
     */
    HashMap<Integer,HashMap<Integer,Boolean>> link = new HashMap<Integer,HashMap<Integer,Boolean>>();

    /**
     *   The number of outlinks from each node.
     */
    int[] out = new int[MAX_NUMBER_OF_DOCS];

    /**
     *   The probability that the surfer will be bored, stop
     *   following links, and take a random jump somewhere.
     */
    final static double BORED = 0.15;

    /**
     *   Convergence criterion: Transition probabilities do not 
     *   change more that EPSILON from one iteration to another.
     */
    final static double EPSILON = 0.0001;

       
    /* --------------------------------------------- */
	HashMap<Integer, String> titleMapping;

    // for calculating the goodness meassures
    private double[] exactPR;
    private List<Integer> topExactIndices;

    public PageRank( String filename ) {
	int noOfDocs = readDocs( filename );
	titleMapping = loadTitleMapping("svwikiTitles.txt");
	//iterate( noOfDocs, 1000 );

	int trials = 1;
    int m = 1;
    int N1 = 17478;
    int N5 = 17478;

    double[] mc1 = monteCarlo1(noOfDocs, N1, trials);
    /* double[] mc2 = monteCarlo2(noOfDocs, m, trials);
    double[] mc4 = monteCarlo4(noOfDocs, m, trials);
    double[] mc5 = monteCarlo5(noOfDocs, N5, trials); */

    writePRToFile(mc1, "mc1_scores.txt", noOfDocs);
    /* writePRToFile(mc2, "mc2_scores.txt", noOfDocs);
    writePRToFile(mc4, "mc4_scores.txt", noOfDocs);
    writePRToFile(mc5, "mc5_scores_svwiki.txt", noOfDocs); */

    /* System.out.println("\nSum of Squared Differences (SSD):");
    System.out.printf("MC1 SSD: %.9f%n", calculateSSD(mc1));
    System.out.printf("MC2 SSD: %.9f%n", calculateSSD(mc2));
    System.out.printf("MC4 SSD: %.9f%n", calculateSSD(mc4));
    System.out.printf("MC5 SSD: %.9f%n", calculateSSD(mc5)); */
    }


    /* --------------------------------------------- */


    /**
     *   Reads the documents and fills the data structures. 
     *
     *   @return the number of documents read.
     */
    int readDocs( String filename ) {
	int fileIndex = 0;
	try {
	    System.err.print( "Reading file... " );
	    BufferedReader in = new BufferedReader( new FileReader( filename ));
	    String line;
	    while ((line = in.readLine()) != null && fileIndex<MAX_NUMBER_OF_DOCS ) {
		int index = line.indexOf( ";" );
		String title = line.substring( 0, index );
		Integer fromdoc = docNumber.get( title );
		//  Have we seen this document before?
		if ( fromdoc == null ) {	
		    // This is a previously unseen doc, so add it to the table.
		    fromdoc = fileIndex++;
		    docNumber.put( title, fromdoc );
		    docName[fromdoc] = title;
		}
		// Check all outlinks.
		StringTokenizer tok = new StringTokenizer( line.substring(index+1), "," );
		while ( tok.hasMoreTokens() && fileIndex<MAX_NUMBER_OF_DOCS ) {
		    String otherTitle = tok.nextToken();
		    Integer otherDoc = docNumber.get( otherTitle );
		    if ( otherDoc == null ) {
			// This is a previousy unseen doc, so add it to the table.
			otherDoc = fileIndex++;
			docNumber.put( otherTitle, otherDoc );
			docName[otherDoc] = otherTitle;
		    }
		    // Set the probability to 0 for now, to indicate that there is
		    // a link from fromdoc to otherDoc.
		    if ( link.get(fromdoc) == null ) {
			link.put(fromdoc, new HashMap<Integer,Boolean>());
		    }
		    if ( link.get(fromdoc).get(otherDoc) == null ) {
			link.get(fromdoc).put( otherDoc, true );
			out[fromdoc]++;
		    }
		}
	    }
	    if ( fileIndex >= MAX_NUMBER_OF_DOCS ) {
		System.err.print( "stopped reading since documents table is full. " );
	    }
	    else {
		System.err.print( "done. " );
	    }
	}
	catch ( FileNotFoundException e ) {
	    System.err.println( "File " + filename + " not found!" );
	}
	catch ( IOException e ) {
	    System.err.println( "Error reading file " + filename );
	}
	System.err.println( "Read " + fileIndex + " number of documents" );
	return fileIndex;
    }


    /* --------------------------------------------- */
	HashMap<Integer, String> loadTitleMapping(String filename) {
        HashMap<Integer, String> mapping = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(";");
                int docID = Integer.parseInt(parts[0]);
                String realName = parts[1];
                mapping.put(docID, realName);
            }
            System.err.println("Loaded title mapping for " + mapping.size() + " documents");
        } catch (IOException e) {
            System.err.println("Error reading title mapping file: " + filename);
        }
        return mapping;
    }

    /*
     *   Chooses a probability vector a, and repeatedly computes
     *   aP, aP^2, aP^3... until aP^i = aP^(i+1).
     */
    void iterate( int numberOfDocs, int maxIterations ) {

		// YOUR CODE HERE
		if (numberOfDocs == 0) return;

		double EPSILON_pagerank = 0.0000001;
		double[] prev = new double[numberOfDocs];
		Arrays.fill(prev, 1.0 / numberOfDocs);
		double[] next = new double[numberOfDocs];

		List<Integer> sinkPages = new ArrayList<>();
		for (int i = 0; i < numberOfDocs; i++) {
			if (out[i] == 0) {
				sinkPages.add(i);
			}
		}

		int iter = 0;
		double maxDiff;
		do {
			double sinkSum = 0.0;
			for (int i : sinkPages) {
				sinkSum += prev[i];
			}
			double sinkContribution = (1.0 - BORED) * sinkSum / numberOfDocs;

			Arrays.fill(next, BORED / numberOfDocs + sinkContribution);

			for (int i : link.keySet()) {
				HashMap<Integer, Boolean> outlinks = link.get(i);
				if (outlinks != null && out[i] > 0) {
					double contribution = (1.0 - BORED) * prev[i] / out[i];
					for (int j : outlinks.keySet()) {
						next[j] += contribution;
					}
				}
			}
			double sum = 0.0;
			for (int j = 0; j < numberOfDocs; j++) {
				sum += next[j];
			}
			// Normalize each entry so the sum becomes 1.0
			for (int j = 0; j < numberOfDocs; j++) {
				next[j] /= sum;
			}

			maxDiff = 0.0;
			for (int j = 0; j < numberOfDocs; j++) {
				double diff = Math.abs(next[j] - prev[j]);
				if (diff > maxDiff) {
					maxDiff = diff;
				}
			}

			double[] temp = prev;
			prev = next;
			next = temp;

			iter++;
		} while (iter < maxIterations && maxDiff >= EPSILON_pagerank);

		try (PrintWriter writer = new PrintWriter("pagerank_scores.txt")) {
            for (int docId = 0; docId < numberOfDocs; docId++) {
				String internalName = docName[docId];
                String realName = internalName;
                try {
                    int id = Integer.parseInt(internalName);
                    if (titleMapping.containsKey(id)) {
                        realName = titleMapping.get(id);
                    }
                } catch (NumberFormatException e) {
                    // just use the original internal name
                }
                writer.printf("%s%s %.6f%n", "C:\\Users\\andre\\Desktop\\DD2477\\davisWiki\\", realName, prev[docId]);
            }
        } catch (FileNotFoundException e) {
            System.err.println("Error writing pagerank scores: " + e.getMessage());
        }

		List<Integer> sortedIndices = new ArrayList<>();
		for (int i = 0; i < numberOfDocs; i++) {
			sortedIndices.add(i);
		}
		final double[] finalRanks = prev;
		sortedIndices.sort((a, b) -> Double.compare(finalRanks[b], finalRanks[a]));

		System.out.println("Top 30 Pages by PageRank:");
		for (int i = 0; i < 30 && i < sortedIndices.size(); i++) {
			int docId = sortedIndices.get(i);
			String originalId = docName[docId];
			System.out.printf("%d. ID: %s, PageRank: %.6f%n", i+1, originalId, prev[docId]);
		}

        // for the goodness meassure
        this.topExactIndices = sortedIndices.subList(0, Math.min(30, sortedIndices.size()));
        this.exactPR = prev.clone();
	}

	private double[] monteCarlo1(int numberOfDocs, int N, int trials) {
        double[] averagePR = new double[numberOfDocs];
        for (int t = 0; t < trials; t++) {
            int[] counts = new int[numberOfDocs];
            for (int run = 0; run < N; run++) {
                int current = (int) (Math.random() * numberOfDocs);
                while (true) {
                    if (Math.random() < BORED) {
                        counts[current]++;
                        break;
                    }
                    if (out[current] == 0) {
                        current = (int) (Math.random() * numberOfDocs);
                        continue;
                    }
                    HashMap<Integer, Boolean> outlinks = link.get(current);
                    if (outlinks == null || outlinks.isEmpty()) {
                        counts[current]++;
                        break;
                    }
                    List<Integer> links = new ArrayList<>(outlinks.keySet());
                    current = links.get((int) (Math.random() * links.size()));
                }
            }
            double[] pr = new double[numberOfDocs];
            for (int j = 0; j < numberOfDocs; j++) {
                pr[j] = (double) counts[j] / N;
            }
            for (int j = 0; j < numberOfDocs; j++) {
                averagePR[j] += pr[j];
            }
        }
        for (int j = 0; j < numberOfDocs; j++) {
            averagePR[j] /= trials;
        }
		List<Integer> sortedIndices = new ArrayList<>();
		for (int i = 0; i < numberOfDocs; i++) {
			sortedIndices.add(i);
		}
		final double[] finalRanks = averagePR;
		sortedIndices.sort((a, b) -> Double.compare(finalRanks[b], finalRanks[a]));

		System.out.println("Top 30 Pages by PageRank - MC 1:");
		for (int i = 0; i < 30 && i < sortedIndices.size(); i++) {
			int docId = sortedIndices.get(i);
			String originalId = docName[docId];
			System.out.printf("%d. ID: %s, PageRank: %.6f%n", i+1, originalId, averagePR[docId]);
		}
        return averagePR;
    }

    private double[] monteCarlo2(int numberOfDocs, int m, int trials) {
        double[] averagePR = new double[numberOfDocs];
        int N = m * numberOfDocs;
        for (int t = 0; t < trials; t++) {
            int[] counts = new int[numberOfDocs];
            for (int page = 0; page < numberOfDocs; page++) {
                for (int run = 0; run < m; run++) {
                    int current = page;
                    while (true) {
                        if (Math.random() < BORED) {
                            counts[current]++;
                            break;
                        }
                        if (out[current] == 0) {
                            current = (int) (Math.random() * numberOfDocs);
                            continue;
                        }
                        HashMap<Integer, Boolean> outlinks = link.get(current);
                        if (outlinks == null || outlinks.isEmpty()) {
                            counts[current]++;
                            break;
                        }
                        List<Integer> links = new ArrayList<>(outlinks.keySet());
                        current = links.get((int) (Math.random() * links.size()));
                    }
                }
            }
            double[] pr = new double[numberOfDocs];
            for (int j = 0; j < numberOfDocs; j++) {
                pr[j] = (double) counts[j] / N;
            }
            for (int j = 0; j < numberOfDocs; j++) {
                averagePR[j] += pr[j];
            }
        }
        for (int j = 0; j < numberOfDocs; j++) {
            averagePR[j] /= trials;
        }
		List<Integer> sortedIndices = new ArrayList<>();
		for (int i = 0; i < numberOfDocs; i++) {
			sortedIndices.add(i);
		}
		final double[] finalRanks = averagePR;
		sortedIndices.sort((a, b) -> Double.compare(finalRanks[b], finalRanks[a]));

		System.out.println("Top 30 Pages by PageRank - MC 2:");
		for (int i = 0; i < 30 && i < sortedIndices.size(); i++) {
			int docId = sortedIndices.get(i);
			String originalId = docName[docId];
			System.out.printf("%d. ID: %s, PageRank: %.6f%n", i+1, originalId, averagePR[docId]);
		}
        return averagePR;
    }

    private double[] monteCarlo4(int numberOfDocs, int m, int trials) {
        double[] averagePR = new double[numberOfDocs];
        int N = m * numberOfDocs;
        for (int t = 0; t < trials; t++) {
            int totalVisits = 0;
            int[] visits = new int[numberOfDocs];
            for (int page = 0; page < numberOfDocs; page++) {
                for (int run = 0; run < m; run++) {
                    int current = page;
                    while (true) {
                        visits[current]++;
                        totalVisits++;
                        if (out[current] == 0) {
                            break;
                        }
                        if (Math.random() < BORED) {
                            break;
                        }
                        HashMap<Integer, Boolean> outlinks = link.get(current);
                        if (outlinks == null || outlinks.isEmpty()) {
                            break;
                        }
                        List<Integer> links = new ArrayList<>(outlinks.keySet());
                        current = links.get((int) (Math.random() * links.size()));
                    }
                }
            }
            double[] pr = new double[numberOfDocs];
            if (totalVisits == 0) {
                continue;
            }
            for (int j = 0; j < numberOfDocs; j++) {
                pr[j] = (double) visits[j] / totalVisits;
            }
            for (int j = 0; j < numberOfDocs; j++) {
                averagePR[j] += pr[j];
            }
        }
        for (int j = 0; j < numberOfDocs; j++) {
            averagePR[j] /= trials;
        }
		List<Integer> sortedIndices = new ArrayList<>();
		for (int i = 0; i < numberOfDocs; i++) {
			sortedIndices.add(i);
		}
		final double[] finalRanks = averagePR;
		sortedIndices.sort((a, b) -> Double.compare(finalRanks[b], finalRanks[a]));

		System.out.println("Top 30 Pages by PageRank - MC 4:");
		for (int i = 0; i < 30 && i < sortedIndices.size(); i++) {
			int docId = sortedIndices.get(i);
			String originalId = docName[docId];
			System.out.printf("%d. ID: %s, PageRank: %.6f%n", i+1, originalId, averagePR[docId]);
		}
        return averagePR;
    }

    private double[] monteCarlo5(int numberOfDocs, int N, int trials) {
        double[] averagePR = new double[numberOfDocs];
        for (int t = 0; t < trials; t++) {
            int totalVisits = 0;
            int[] visits = new int[numberOfDocs];
            for (int run = 0; run < N; run++) {
                int current = (int) (Math.random() * numberOfDocs);
                while (true) {
                    visits[current]++;
                    totalVisits++;
                    if (out[current] == 0) {
                        break;
                    }
                    if (Math.random() < BORED) {
                        break;
                    }
                    HashMap<Integer, Boolean> outlinks = link.get(current);
                    if (outlinks == null || outlinks.isEmpty()) {
                        break;
                    }
                    List<Integer> links = new ArrayList<>(outlinks.keySet());
                    current = links.get((int) (Math.random() * links.size()));
                }
            }
            double[] pr = new double[numberOfDocs];
            if (totalVisits == 0) {
                continue;
            }
            for (int j = 0; j < numberOfDocs; j++) {
                pr[j] = (double) visits[j] / totalVisits;
            }
            for (int j = 0; j < numberOfDocs; j++) {
                averagePR[j] += pr[j];
            }
        }
        for (int j = 0; j < numberOfDocs; j++) {
            averagePR[j] /= trials;
        }
		List<Integer> sortedIndices = new ArrayList<>();
		for (int i = 0; i < numberOfDocs; i++) {
			sortedIndices.add(i);
		}
		final double[] finalRanks = averagePR;
		sortedIndices.sort((a, b) -> Double.compare(finalRanks[b], finalRanks[a]));

		System.out.println("Top 30 Pages by PageRank - MC 5:");
		for (int i = 0; i < 30 && i < sortedIndices.size(); i++) {
			int docId = sortedIndices.get(i);
			String originalId = docName[docId];
			System.out.printf("%d. ID: %s, PageRank: %.6f%n", i+1, originalId, averagePR[docId]);
		}
        return averagePR;
    }

	private void writePRToFile(double[] pr, String filename, int numberOfDocs) {
        try (PrintWriter writer = new PrintWriter(filename)) {
            for (int docId = 0; docId < numberOfDocs; docId++) {
                String internalName = docName[docId];
                String realName = internalName;
                try {
                    int id = Integer.parseInt(internalName);
                    if (titleMapping.containsKey(id)) {
                        realName = titleMapping.get(id);
                    }
                } catch (NumberFormatException e) {
                    // Use the original name if parsing fails
                }
                writer.printf("%s%s %.6f%n", "C:\\Users\\jahnf\\Desktop\\DD2477\\davisWiki\\", realName, pr[docId]);
            }
        } catch (FileNotFoundException e) {
            System.err.println("Error writing to " + filename + ": " + e.getMessage());
        }
    }

    private double calculateSSD(double[] mcEstimates) {
        double ssd = 0.0;
        for (int idx : topExactIndices) {
            double diff = exactPR[idx] - mcEstimates[idx];
            ssd += diff * diff;
        }
        return ssd;
    }

    /* --------------------------------------------- */


    public static void main( String[] args ) {
	if ( args.length != 1 ) {
	    System.err.println( "Please give the name of the link file" );
	}
	else {
	    new PageRank( args[0] );
	}
    }
}