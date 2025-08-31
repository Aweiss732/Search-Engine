package ir;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import static java.lang.Thread.sleep;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class PersistentScalableHashedIndex extends PersistentHashedIndex implements Runnable {

    public static final int MAXTOKENS = 250000;
    private static final Queue<String> mergeQueue = new LinkedList<>();
    private static final Object mergeQueueLock = new Object();
    private static int startedThreads = 0;
    private static int mergingThreads = 0;
    private static int finishedThreads = 0;
    private static volatile boolean finalMerge;
    private static int prevDocInfo = -1;
    private int threadNumber = 0;

    public PersistentScalableHashedIndex() {
        try {
            readDocInfo();
        } catch (FileNotFoundException ignored) {
        } catch (IOException e) {
            handleIOException(e);
        }
    }

    public PersistentScalableHashedIndex(int number, HashMap<String, PostingsList> index) {
        this.threadNumber = number;
        this.index = index;
        setupFileHandles(number);
    }

    private void setupFileHandles(int number) {
        try {
            dictionaryFile = new RandomAccessFile(INDEXDIR + "/" + DICTIONARY_FNAME + number, "rw");
            dataFile = new RandomAccessFile(INDEXDIR + "/" + DATA_FNAME + number, "rw");
        } catch (IOException e) {
            handleIOException(e);
        }
    }

    @Override
    public void insert(String token, int docID, int offset) {
        if (index.size() >= MAXTOKENS) {
            handleIndexOverflow(token, docID, offset);
        } else {
            super.insert(token, docID, offset);
        }
    }

    private void handleIndexOverflow(String token, int docID, int offset) {
        startNewThread();
        persistDocumentInfo();
        index.clear();
        System.out.println("Started thread " + startedThreads);
        insert(token, docID, offset);
    }

    private void startNewThread() {
        (new Thread(new PersistentScalableHashedIndex(++startedThreads, new HashMap<>(index)))).start();
    }

    private void persistDocumentInfo() {
        try {
            writeDocInfo();
        } catch (IOException e) {
            handleIOException(e);
        }
    }

    @Override
    public void cleanup() {
        executeCleanupProcess();
    }

    private void executeCleanupProcess() {
        System.err.print("Writing index to disk...");
        writeIndex(true);
        run();
        System.err.println("Everything is done");
    }

    @Override
    public void run() {
        finalMerge = false;
        try {
            handleThreadInitialization();
            processIndexWriting();
            manageMergeOperations();
            finalizeThreadOperations();
        } catch (Exception e) {
            handleException(e);
        }
    }

    private void handleThreadInitialization() throws IOException {
        if (threadNumber == 0) {
            writeDocInfo();
            setupFileHandles(0);
        }
    }

    private void processIndexWriting() {
        super.writeIndex(true);
        index.clear();
        updateMergeQueue();
    }

    private void updateMergeQueue() {
        synchronized (mergeQueueLock) {
            mergeQueue.add(threadNumber + "");
            System.out.println("Thread: " + threadNumber + " has been written, current queue size: " + mergeQueue.size());
        }
        finishedThreads++;
    }

    private void manageMergeOperations() throws InterruptedException {
        while (!finalMerge) {
            checkMergeConditions();
            processMergeIfNeeded();
        }
    }

    private void checkMergeConditions() {
        finalMerge = (mergeQueue.size() == 2 && finishedThreads > startedThreads && mergingThreads == 0) ||
                (startedThreads == 0 && finishedThreads == 1 && mergeQueue.size() == 1);
    }

    private void processMergeIfNeeded() throws InterruptedException {
        if (mergeQueue.size() > 1) {
            String[] suffixes = getMergeSuffixes();
            if (suffixes[0] == null || suffixes[1] == null) return;
            
            String merged = performFileMerge(suffixes[0], suffixes[1]);
            updateMergeQueueAfterMerge(merged);
        }
    }

    private String[] getMergeSuffixes() {
        synchronized (mergeQueueLock) {
            if (mergeQueue.size() < 2) return new String[]{null, null};
            String suffix1 = mergeQueue.poll();
            String suffix2 = mergeQueue.poll();
            finalMerge = (mergeQueue.isEmpty() && finishedThreads > startedThreads && mergingThreads == 0);
            mergingThreads++;
            return new String[]{suffix1, suffix2};
        }
    }

    private String performFileMerge(String suffix1, String suffix2) {
        try {
            return mergeFiles(finalMerge, suffix1, suffix2);
        } catch (IOException e) {
            handleIOException(e);
            return null;
        }
    }

    private void updateMergeQueueAfterMerge(String merged) {
        synchronized (mergeQueueLock) {
            mergeQueue.add(merged);
            mergingThreads--;
        }
    }

    private void finalizeThreadOperations() throws InterruptedException, IOException {
        if (threadNumber != 0) return; // check if last thread
        
        while (!finalMerge || mergingThreads > 0) sleep(1000);
        
        readDocInfo();
        handleFinalMergeOperations();
        System.out.println("Thread: " + threadNumber + " is done");
    }

    private void handleFinalMergeOperations() throws IOException, InterruptedException {
        if (startedThreads == 0 && finishedThreads == 1) {
            handleSingleThreadCase(); // needed
        } else if (finishedThreads > startedThreads) {
            performMainIndexUpdate();
        }
    }

    private void handleSingleThreadCase() throws IOException {
        System.out.println("No thread started");
        setupFileHandles(0);
    }

    private void performMainIndexUpdate() throws IOException, InterruptedException {
        closeExistingHandles();
        sleep(22000);
        deleteOldIndexFiles();
        renameMergedFiles();
        setupMainIndexHandles();
    }

    private void closeExistingHandles() throws IOException {
        if (dictionaryFile != null) dictionaryFile.close();
        if (dataFile != null) dataFile.close();
    }

    private void deleteOldIndexFiles() {
        new File(INDEXDIR + "/" + DICTIONARY_FNAME).delete();
        new File(INDEXDIR + "/" + DATA_FNAME).delete();
    }

    private void renameMergedFiles() throws IOException {
        String mergedSuffix = mergeQueue.peek();
        File mergedDict = new File(INDEXDIR + "/" + DICTIONARY_FNAME + mergedSuffix);
        File mergedData = new File(INDEXDIR + "/" + DATA_FNAME + mergedSuffix);
        File finalDict = new File(INDEXDIR + "/" + DICTIONARY_FNAME + ".tmp");
        File finalData = new File(INDEXDIR + "/" + DATA_FNAME + ".tmp");
        
        // Rename merged files to temporary names
        mergedDict.renameTo(finalDict);
        mergedData.renameTo(finalData);
        
        // Replace the main files atomically
        Files.move(finalDict.toPath(), 
                new File(INDEXDIR + "/" + DICTIONARY_FNAME).toPath(),
                StandardCopyOption.REPLACE_EXISTING);
        Files.move(finalData.toPath(), 
                new File(INDEXDIR + "/" + DATA_FNAME).toPath(),
                StandardCopyOption.REPLACE_EXISTING);
    }

    private void setupMainIndexHandles() throws FileNotFoundException {
        dictionaryFile = new RandomAccessFile(INDEXDIR + "/" + DICTIONARY_FNAME, "rw");
        dataFile = new RandomAccessFile(INDEXDIR + "/" + DATA_FNAME, "rw");
    }

    private void writeDocInfo() throws IOException {
        FileOutputStream fout = new FileOutputStream(INDEXDIR + "/docInfo", true);
        for (Map.Entry<Integer, String> entry : docNames.entrySet()) {
            Integer key = entry.getKey();
            if (entry.getKey() > prevDocInfo) {
                String info = key + ";" + entry.getValue() + ";" + docLengths.get(key) + "\n";
                fout.write(info.getBytes());
                prevDocInfo = key;
            }
        }
        fout.close();
        docNames.clear();
        docLengths.clear();
    }
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
    }

    private void handleIOException(IOException e) {
        e.printStackTrace();
    }

    private void handleException(Exception e) {
        e.printStackTrace();
    }



    private String mergeFiles(boolean finalMerge, String suffix1, String suffix2) throws IOException {
        return new MergeProcessor(finalMerge, suffix1, suffix2).merge();
    }
    
    private class MergeProcessor {
        private final boolean finalMerge;
        private final String suffix1;
        private final String suffix2;
        private final String mergedSuffix;
        private final Map<String, Boolean> doubles = new HashMap<>();
        
        private RandomAccessFile dict1;
        private RandomAccessFile dict2;
        private RandomAccessFile data1;
        private RandomAccessFile data2;
        private RandomAccessFile mergedDict;
        private BufferedWriter mergedData;
        private long freePointer = 0;
    
        public MergeProcessor(boolean finalMerge, String suffix1, String suffix2) {
            this.finalMerge = finalMerge;
            this.suffix1 = suffix1;
            this.suffix2 = suffix2;
            if (finalMerge) {
                this.mergedSuffix = ".tmp"; 
            } else {
                this.mergedSuffix = suffix1 + "-" + suffix2;
            }
        }
    
        public String merge() throws IOException {
            initializeFiles();
            try {
                mergeDataFromFirstFile();
                addRemainingFromSecondFile();
                return mergedSuffix;
            } finally {
                closeResources();
                cleanupTempFiles();
            }
        }
    
        private void initializeFiles() throws IOException {
            System.out.println("Thread " + threadNumber + ". Now merging " + suffix1 + " and " + suffix2 + " into " + mergedSuffix);
            System.out.println("Final merge: " + finalMerge);
    
            dict1 = new RandomAccessFile(INDEXDIR + "/" + DICTIONARY_FNAME + suffix1, "r");
            dict2 = new RandomAccessFile(INDEXDIR + "/" + DICTIONARY_FNAME + suffix2, "r");
            data1 = new RandomAccessFile(INDEXDIR + "/" + DATA_FNAME + suffix1, "r");
            data2 = new RandomAccessFile(INDEXDIR + "/" + DATA_FNAME + suffix2, "r");
            mergedDict = new RandomAccessFile(INDEXDIR + "/" + DICTIONARY_FNAME + mergedSuffix, "rw");
            mergedData = new BufferedWriter(new FileWriter(INDEXDIR + "/" + DATA_FNAME + mergedSuffix));
            
            mergedDict.setLength(TABLESIZE * (Long.BYTES + Integer.BYTES));
        }
    
        private void mergeDataFromFirstFile() throws IOException {
            String line;
            while ((line = data1.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                
                String[] parts = parseDataLine(line);
                String token = parts[0];
                String postingList1 = parts[1];
                
                String postingsList2 = findInSecondFile(token);
                if (postingsList2 != null) {
                    handleDoublePostings(token, postingList1, postingsList2);
                } else {
                    writePostingEntry(token, line);
                }
            }
        }
    
        private String[] parseDataLine(String line) throws IOException {
            String[] parts = line.split(DELIMITER, 2);
            if (parts.length != 2) {
                throw new IOException("Invalid line format: " + line);
            }
            return parts;
        }
    
        private String findInSecondFile(String token) throws IOException {
            long hash = hashFunction(token);
            long dictPointer = hash * (Long.BYTES + Integer.BYTES);
            Entry entry = readEntry(dict2, dictPointer);
            
            while (entry != null) {
                String postingString = readPostingString(entry);
                String[] parts = postingString.split(DELIMITER, 2);
                if (parts.length >= 1 && parts[0].equals(token)) {
                    return postingString;
                }
                dictPointer = nextDictPosition(dictPointer);
                entry = readEntry(dict2, dictPointer);
            }
            return null;
        }
    
        private String readPostingString(Entry entry) throws IOException {
            data2.seek(entry.pointer);
            byte[] buffer = new byte[entry.size];
            data2.readFully(buffer);
            return new String(buffer, StandardCharsets.UTF_8);
        }
    
        private void handleDoublePostings(String token, String postingList1, String postingList2) throws IOException {
            doubles.put(token, true);
            PostingsList mergedPostingList = mergePostingLists(
                PostingsList.fromString(postingList1), 
                PostingsList.fromString(postingList2.split(DELIMITER, 2)[1])
            );
            writePostingEntry(token, token + DELIMITER + mergedPostingList);
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
            mergedEntry.addOffsets(new ArrayList<>(e1.getOffsets()));
            mergedEntry.addOffsets(new ArrayList<>(e2.getOffsets()));
            return mergedEntry;
        }
    
        private void writePostingEntry(String token, String dataLine) throws IOException {
            long dictPointer = findFreeDictionarySlot(token);
            String dataString = dataLine + "\n";
            int size = dataString.getBytes(StandardCharsets.UTF_8).length;
            
            mergedData.write(dataString);
            writeEntry(mergedDict, new Entry(freePointer, size), dictPointer);
            freePointer += size;
        }
    
        private long findFreeDictionarySlot(String token) throws IOException {
            long hash = hashFunction(token);
            long dictPointer = hash * (Long.BYTES + Integer.BYTES);
            
            while (readEntry(mergedDict, dictPointer) != null) {
                dictPointer = nextDictPosition(dictPointer);
            }
            return dictPointer;
        }
    
        private long nextDictPosition(long current) {
            current += (Long.BYTES + Integer.BYTES);
            return current >= TABLESIZE * (Long.BYTES + Integer.BYTES) ? 0 : current;
        }
    
        private void addRemainingFromSecondFile() throws IOException {
            String line;
            data2.seek(0);
            while ((line = data2.readLine()) != null) {
                String token = line.split(DELIMITER, 2)[0];
                if (!doubles.containsKey(token)) {
                    writePostingEntry(token, line);
                }
            }
        }
    
        private void closeResources() throws IOException {
            for (Closeable res : new Closeable[]{dict1, dict2, data1, data2, mergedDict, mergedData}) {
                if (res != null) res.close();
            }
        }
    
        private void cleanupTempFiles() {
            Arrays.asList(
                new File(INDEXDIR + "/" + DICTIONARY_FNAME + suffix1),
                new File(INDEXDIR + "/" + DICTIONARY_FNAME + suffix2),
                new File(INDEXDIR + "/" + DATA_FNAME + suffix1),
                new File(INDEXDIR + "/" + DATA_FNAME + suffix2)
            ).forEach(File::delete);
        }
    }
}