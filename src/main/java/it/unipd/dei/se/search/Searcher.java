package it.unipd.dei.se.search;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.LowerCaseFilterFactory;
import org.apache.lucene.analysis.core.StopFilterFactory;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.standard.StandardTokenizerFactory;
import org.apache.lucene.benchmark.quality.QualityQuery;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.MultiSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.FSDirectory;

import edu.smu.tspell.wordnet.Synset;
import edu.smu.tspell.wordnet.SynsetType;
import edu.smu.tspell.wordnet.WordNetDatabase;
import it.unipd.dei.se.parse.ParsedDocument;

/**
 * Searches a document collection.
 */
public class Searcher {

    /**
     * The fields of the typical TREC topics
     */
    private static final class TOPIC_FIELDS {

        /**
         * The title of a topic.
         */
        public static final String TITLE = "title";

        /**
         * The description of a topic.
         */
        public static final String DESCRIPTION = "description";
    }

    /**
     * The identifier of the run
     */
    private final String runID;

    /**
     * The run to be written
     */
    private final PrintWriter run;

    /**
     * The index reader
     */
    private final IndexReader reader;

    /**
     * The index searcher.
     */
    private final IndexSearcher searcher;

    /**
     * The topics to be searched
     */
    private final QualityQuery[] topics;

    /**
     * The query title parser
     */
    private final QueryParser qpTitle;

    /**
     * The query title parser
     */
    private final QueryParser qpPhrases;

    /**
     * The query body parser
     */
    private final QueryParser qpBody;

    /**
     * The maximum number of documents to retrieve
     */
    private final int maxDocsRetrieved;

    /**
     * WordnetDatabase for synonyms expansion
     */
    WordNetDatabase database;

    /**
     * The total elapsed time.
     */
    private long elapsedTime = Long.MIN_VALUE;

    /**
     * Creates a new searcher.
     *
     * @param analyzer         the {@code Analyzer} to be used.
     * @param similarity       the {@code Similarity} to be used.
     * @param indexPath        the directory where containing the index to be
     *                         searched.
     * @param topicsFile       the file containing the topics to search for.
     * @param expectedTopics   the total number of topics expected to be searched.
     * @param runID            the identifier of the run to be created.
     * @param runPath          the path where to store the run.
     * @param maxDocsRetrieved the maximum number of documents to be retrieved.
     * @throws NullPointerException     if any of the parameters is {@code null}.
     * @throws IllegalArgumentException if any of the parameters assumes invalid
     *                                  values.
     */
    public Searcher(final Analyzer analyzer, final Similarity similarity, final String indexPath,
            final String topicsFile, final int expectedTopics, final String runID, final String runPath,
            final int maxDocsRetrieved) {

        if (analyzer == null) {
            throw new NullPointerException("Analyzer cannot be null.");
        }

        if (similarity == null) {
            throw new NullPointerException("Similarity cannot be null.");
        }

        if (indexPath == null) {
            throw new NullPointerException("Index path cannot be null.");
        }

        if (indexPath.isEmpty()) {
            throw new IllegalArgumentException("Index path cannot be empty.");
        }

        final Path indexDir = Paths.get(indexPath);
        if (!Files.isReadable(indexDir)) {
            throw new IllegalArgumentException(
                    String.format("Index directory %s cannot be read.", indexDir.toAbsolutePath().toString()));
        }

        if (!Files.isDirectory(indexDir)) {
            throw new IllegalArgumentException(String.format("%s expected to be a directory where to search the index.",
                    indexDir.toAbsolutePath().toString()));
        }

        try {
            reader = DirectoryReader.open(FSDirectory.open(indexDir));
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Unable to create the index reader for directory %s: %s.",
                    indexDir.toAbsolutePath().toString(), e.getMessage()), e);
        }

        searcher = new IndexSearcher(reader);
        searcher.setSimilarity(similarity);

        if (topicsFile == null) {
            throw new NullPointerException("Topics file cannot be null.");
        }

        if (topicsFile.isEmpty()) {
            throw new IllegalArgumentException("Topics file cannot be empty.");
        }

        try {
            BufferedReader in = Files.newBufferedReader(Paths.get(topicsFile), StandardCharsets.UTF_8);

            topics = new ToucheTopicsReader().readQueries(in);

            in.close();
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    String.format("Unable to process topic file %s: %s.", topicsFile, e.getMessage()), e);
        }

        if (expectedTopics <= 0) {
            throw new IllegalArgumentException(
                    "The expected number of topics to be searched cannot be less than or equal to zero.");
        }

        if (topics.length != expectedTopics) {
            System.out.printf("Expected to search for %s topics; %s topics found instead.", expectedTopics,
                    topics.length);
        }

        qpPhrases = new QueryParser("", analyzer);
        qpTitle = new QueryParser(ParsedDocument.FIELDS.TITLE, analyzer);
        qpBody = new QueryParser(ParsedDocument.FIELDS.BODY, analyzer);

        if (runID == null) {
            throw new NullPointerException("Run identifier cannot be null.");
        }

        if (runID.isEmpty()) {
            throw new IllegalArgumentException("Run identifier cannot be empty.");
        }

        this.runID = runID;

        if (runPath == null) {
            throw new NullPointerException("Run path cannot be null.");
        }

        if (runPath.isEmpty()) {
            throw new IllegalArgumentException("Run path cannot be empty.");
        }

        final Path runDir = Paths.get(runPath);
        if (!Files.isWritable(runDir)) {
            throw new IllegalArgumentException(
                    String.format("Run directory %s cannot be written.", runDir.toAbsolutePath().toString()));
        }

        if (!Files.isDirectory(runDir)) {
            throw new IllegalArgumentException(String.format("%s expected to be a directory where to write the run.",
                    runDir.toAbsolutePath().toString()));
        }

        Path runFile = runDir.resolve(runID + ".txt");
        try {
            run = new PrintWriter(Files.newBufferedWriter(runFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE));
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    String.format("Unable to open run file %s: %s.", runFile.toAbsolutePath(), e.getMessage()), e);
        }

        if (maxDocsRetrieved <= 0) {
            throw new IllegalArgumentException(
                    "The maximum number of documents to be retrieved cannot be less than or equal to zero.");
        }

        this.maxDocsRetrieved = maxDocsRetrieved;

        System.setProperty("wordnet.database.dir", "./src/main/resources/dict");
        this.database = WordNetDatabase.getFileInstance();
    }

    /**
     * Returns the total elapsed time.
     *
     * @return the total elapsed time.
     */
    public long getElapsedTime() {
        return elapsedTime;
    }

    /**
     * /** Searches for the specified topics.
     *
     * @throws IOException    if something goes wrong while searching.
     * @throws ParseException if something goes wrong while parsing topics.
     */
    public void search(final int approach) throws IOException, ParseException {
        System.out.printf("%n#### Start searching ####%n");

        // the start time of the searching
        final long start = System.currentTimeMillis();

        final Set<String> idField = new HashSet<>();
        idField.add(ParsedDocument.FIELDS.ID);

        TopDocs docs = null;
        ScoreDoc[] sd = null;
        String docID = null;

        try {
            for (QualityQuery t : topics) {

                System.out.printf("Searching for topic %s.%n", t.getQueryID());

                String tokenizedTitle = qpTitle.parse(QueryParserBase.escape(t.getValue(TOPIC_FIELDS.TITLE)))
                        .toString("title"); // expand

                String[] titleTerms = tokenizedTitle.split("\\s+");

                String tokenizedDescription = qpBody.parse(QueryParserBase.escape(t.getValue(TOPIC_FIELDS.DESCRIPTION)))
                        .toString("body");

                String[] descriptionTerms = tokenizedDescription.split("\\s+");

                Query recallMaximizer = buildRecallQuery(titleTerms, descriptionTerms, 0);
                Query ndcgMaximizer = buildNdcgQuery(titleTerms, descriptionTerms, 0);

                if (approach == 2) {
                    recallMaximizer = buildRecallQuery(titleTerms, descriptionTerms, 1);
                    ndcgMaximizer = buildNdcgQuery(titleTerms, descriptionTerms, 1);
                }

                switch (approach) {
                case 3:
                    searcher.setSimilarity(new LMDirichletSimilarity());
                    docs = searcher.search(ndcgMaximizer, maxDocsRetrieved);
                    break;
                case 4:
                    searcher.setSimilarity(new MultiSimilarity(
                            new Similarity[] { new BM25Similarity(), new LMDirichletSimilarity() }));
                    docs = searcher.search(ndcgMaximizer, maxDocsRetrieved);

                    break;

                default:
                    // Maximize recall
                    searcher.setSimilarity(new MultiSimilarity(
                            new Similarity[] { new BM25Similarity(), new LMDirichletSimilarity() }));
                    docs = searcher.search(recallMaximizer, maxDocsRetrieved);
                    break;
                }

                sd = docs.scoreDocs; // Result of the search

                if (approach == 1 || approach == 2) {
                    // Create the new query for maximizing NDCG_5
                    searcher.setSimilarity(new LMDirichletSimilarity());

                    ArrayList<ScoreDoc> newRanking = new ArrayList<ScoreDoc>();

                    for (ScoreDoc scoreDoc : sd) {
                        float newScore = searcher.explain(ndcgMaximizer, scoreDoc.doc).getValue().floatValue();
                        newRanking.add(new ScoreDoc(scoreDoc.doc, newScore));
                    }
                    // Sort the ArrayList
                    newRanking.sort((o1, o2) -> Float.valueOf(o2.score).compareTo(o1.score));

                    docs = new TopDocs(null, newRanking.toArray(new ScoreDoc[0]));

                    sd = docs.scoreDocs;
                }

                for (int i = 0, n = sd.length; i < n; i++) {
                    docID = reader.document(sd[i].doc, idField).get(ParsedDocument.FIELDS.ID);

                    run.printf(Locale.ENGLISH, "%s\tQ0\t%s\t%d\t%.6f\t%s%n", t.getQueryID(), docID, i, sd[i].score,
                            runID);
                }

                run.flush();

            }
        } finally {
            run.close();

            reader.close();
        }

        elapsedTime = System.currentTimeMillis() - start;

        System.out.printf("%d topic(s) searched in %d seconds.", topics.length, elapsedTime / 1000);

        System.out.printf("#### Searching complete ####%n");
    }

    /**
     * /** Searches for the specified topics.
     *
     * @throws IOException    if something goes wrong while searching.
     * @throws ParseException if something goes wrong while parsing topics.
     */
    public void searchOld(float boostTitle, double boostSyn, boolean expandRandom, double minBoostSyn,
            double maxBoostSyn, int nRandomQuery, boolean rerank, float pBoost, int pDist)
            throws IOException, ParseException {

        System.out.printf("%n#### Start searching ####%n");

        // the start time of the searching
        final long start = System.currentTimeMillis();

        final Set<String> idField = new HashSet<>();
        idField.add(ParsedDocument.FIELDS.ID);

        BooleanQuery.Builder bq = null;
        Query q = null;
        TopDocs docs = null;
        ScoreDoc[] sd = null;
        String docID = null;

        // SYNONYMS PROPERTY
        System.setProperty("wordnet.database.dir", "./src/main/resources/dict");
        try {
            for (QualityQuery t : topics) {

                System.out.printf("Searching for topic %s.%n", t.getQueryID());

                bq = new BooleanQuery.Builder();

                String tokenizedTitle = qpTitle.parse(QueryParserBase.escape(t.getValue(TOPIC_FIELDS.TITLE)))
                        .toString("title"); // expand

                String[] titleTerms = tokenizedTitle.split("\\s+");

                Query titleQuery = buildTitleQuery(titleTerms, "title", (float) boostSyn, boostTitle, pBoost, pDist, 0);

                Query bodyQuery = buildTitleQuery(titleTerms, "body", (float) boostSyn, 1.0f, pBoost, pDist, 0);

                if (!(expandRandom)) {

                    bq.add(titleQuery, BooleanClause.Occur.SHOULD);
                    bq.add(bodyQuery, BooleanClause.Occur.SHOULD);
                    // bq.add(descriptionQuery, BooleanClause.Occur.SHOULD); // QUERY DESCRIPTION

                    q = bq.build();
                    System.out.println(q);

                    docs = searcher.search(q, maxDocsRetrieved);

                } else {

                    Map<Integer, Float> scoreDocsMap = new HashMap<>();

                    for (int i = 0; i < nRandomQuery; i++) {

                        bq = new BooleanQuery.Builder();

                        q = bq.build();

                        System.out.println("QUERY #" + i + ":\n" + q);

                        ScoreDoc[] sDocs = searcher.search(q, maxDocsRetrieved).scoreDocs;

                        // topDocs.add(searcher.search(q, maxDocsRetrieved));

                        for (int j = 0; j < sDocs.length; j++) {
                            Float current = scoreDocsMap.get(sDocs[j].doc);
                            if (current == null) {
                                scoreDocsMap.put(sDocs[j].doc, sDocs[j].score);
                            } else {
                                scoreDocsMap.put(sDocs[j].doc, current + sDocs[j].score);
                            }
                        }
                    }

                    //
                    ArrayList<ScoreDoc> scoreDocs = new ArrayList<>();

                    for (Map.Entry<Integer, Float> entry : scoreDocsMap.entrySet()) {
                        scoreDocs.add(new ScoreDoc(entry.getKey(), entry.getValue()));
                    }

                    scoreDocs.sort((o1, o2) -> Float.valueOf(o2.score).compareTo(o1.score));

                    docs = new TopDocs(null, scoreDocs.subList(0, maxDocsRetrieved).toArray(new ScoreDoc[0]));

                }

                sd = docs.scoreDocs;

                /*
                 * if (rerank) { // RERANKING discussionSd = discussions.scoreDocs;
                 * 
                 * // HashMap of discussion's boost based on discussion rank Map<String, Float>
                 * discussionRankBoost = new HashMap<>(); float firstScore =
                 * discussionSd[0].score; float boostThreshold = firstScore - 0.25f *
                 * firstScore; // Count eligible int topKEligible = 0; for (ScoreDoc scoreDoc :
                 * discussionSd) { if (scoreDoc.score > boostThreshold) topKEligible++; else
                 * break; }
                 * 
                 * for (int j = 0; j < topKEligible; j++) { // float boost = (float)
                 * 2.0-((float)j/topKEligible); float boost = discussionSd[j].score * 0.1f;
                 * String discussionID = discussionReader.document(discussionSd[j].doc, idField)
                 * .get(ParsedDocument.FIELDS.ID); discussionRankBoost.put(discussionID, boost);
                 * System.out.println("Discussion " +
                 * discussionReader.document(discussionSd[j].doc,
                 * idField).get(ParsedDocument.FIELDS.ID) + " boost : " + boost); }
                 * 
                 * // Apply discussion boost to the document run ArrayList<ScoreDoc> newSD = new
                 * ArrayList<>(); for (ScoreDoc sDoc : sd) { // Get document discussion id
                 * String documentID = reader.document(sDoc.doc,
                 * idField).get(ParsedDocument.FIELDS.ID); String discussionID =
                 * documentID.substring(0, documentID.lastIndexOf("-"));
                 * 
                 * // Get discussion boost from the map Float boost =
                 * discussionRankBoost.get(discussionID); if (boost != null) { sDoc.score *=
                 * boost; } newSD.add(sDoc); } // Re-sort run
                 * 
                 * newSD.sort((o1, o2) -> Float.valueOf(o2.score).compareTo(o1.score)); sd =
                 * newSD.toArray(new ScoreDoc[0]); }
                 */
                for (int i = 0, n = sd.length; i < n; i++) {
                    docID = reader.document(sd[i].doc, idField).get(ParsedDocument.FIELDS.ID);

                    run.printf(Locale.ENGLISH, "%s\tQ0\t%s\t%d\t%.6f\t%s%n", t.getQueryID(), docID, i, sd[i].score,
                            runID);
                }

                /*
                 * if (rerank) { for (int i = 0, n = discussionSd.length; i < n; i++) { docID =
                 * discussionReader.document(discussionSd[i].doc,
                 * idField).get(ParsedDocument.FIELDS.ID);
                 * 
                 * discussionRun.printf(Locale.ENGLISH, "%s\tQ0\t%s\t%d\t%.6f\t%s%n",
                 * t.getQueryID(), docID, i, discussionSd[i].score, runID); } }
                 */
                run.flush();

            }
        } finally

        {
            run.close();
            // discussionRun.close();

            reader.close();
            // discussionReader.close();
        }

        elapsedTime = System.currentTimeMillis() - start;

        System.out.printf("%d topic(s) searched in %d seconds.", topics.length, elapsedTime / 1000);

        System.out.printf("#### Searching complete ####%n");
    }

    /**
     * /** Searches for the specified topics.
     *
     * @throws IOException    if something goes wrong while searching.
     * @throws ParseException if something goes wrong while parsing topics.
     */
    public void search2(int topKrerank) throws IOException, ParseException {

        System.out.printf("%n#### Start searching ####%n");

        // the start time of the searching
        final long start = System.currentTimeMillis();

        final Set<String> idField = new HashSet<>();
        idField.add(ParsedDocument.FIELDS.ID);

        TopDocs docs = null;
        ScoreDoc[] sd = null;
        String docID = null;

        // SYNONYMS PROPERTY
        System.setProperty("wordnet.database.dir", "./src/main/resources/dict");
        try {
            for (QualityQuery t : topics) {

                System.out.printf("Searching for topic %s.%n", t.getQueryID());

                String tokenizedTitle = qpTitle.parse(QueryParserBase.escape(t.getValue(TOPIC_FIELDS.TITLE)))
                        .toString("title"); // expand

                String[] titleTerms = tokenizedTitle.split("\\s+");

                String tokenizedDescription = qpBody.parse(QueryParserBase.escape(t.getValue(TOPIC_FIELDS.DESCRIPTION)))
                        .toString("body");

                String[] descriptionTerms = tokenizedDescription.split("\\s+");

                // Maximize recall
                Query recallMaximizer = buildRecallQuery(titleTerms, descriptionTerms, 0);

                // Search
                searcher.setSimilarity(
                        new MultiSimilarity(new Similarity[] { new BM25Similarity(), new LMDirichletSimilarity() }));

                docs = searcher.search(recallMaximizer, maxDocsRetrieved);

                sd = docs.scoreDocs; // Result of the search

                // Create the new query for maximizing NDCG_5
                Query ndcgMaximizer = buildNdcgQuery(titleTerms, descriptionTerms, 0);
                searcher.setSimilarity(new LMDirichletSimilarity());

                int topK = Math.min(topKrerank, sd.length) - 1;

                ArrayList<ScoreDoc> newRanking = new ArrayList<ScoreDoc>();
                // Add topKrerank results to the ArrayList with the new score
                for (int i = 0; i < topK; i++) {
                    float newScore = searcher.explain(ndcgMaximizer, sd[i].doc).getValue().floatValue();
                    newRanking.add(new ScoreDoc(sd[i].doc, newScore));
                }
                // Sort the ArrayList
                newRanking.sort((o1, o2) -> Float.valueOf(o2.score).compareTo(o1.score));
                // Add the remaining results to the ArrayList
                for (int j = topK; j < sd.length; j++) {
                    newRanking.add(sd[j]);
                }
                // Increase the new scores so they are higher than the follower
                float scoreOffset = sd[topK].score;
                for (int z = 0; z < topK; z++) {
                    newRanking.set(z, new ScoreDoc(newRanking.get(z).doc, newRanking.get(z).score + scoreOffset));
                }

                docs = new TopDocs(null, newRanking.toArray(new ScoreDoc[0]));

                sd = docs.scoreDocs;

                for (int i = 0, n = sd.length; i < n; i++) {
                    docID = reader.document(sd[i].doc, idField).get(ParsedDocument.FIELDS.ID);

                    run.printf(Locale.ENGLISH, "%s\tQ0\t%s\t%d\t%.6f\t%s%n", t.getQueryID(), docID, i, sd[i].score,
                            runID);
                }

                run.flush();

            }
        } finally

        {
            run.close();
            // discussionRun.close();

            reader.close();
            // discussionReader.close();
        }

        elapsedTime = System.currentTimeMillis() - start;

        System.out.printf("%d topic(s) searched in %d seconds.", topics.length, elapsedTime / 1000);

        System.out.printf("#### Searching complete ####%n");
    }

    public void search3() throws IOException, ParseException {

        System.out.printf("%n#### Start searching ####%n");

        // the start time of the searching
        final long start = System.currentTimeMillis();

        final Set<String> idField = new HashSet<>();
        idField.add(ParsedDocument.FIELDS.ID);

        TopDocs docs = null;
        ScoreDoc[] sd = null;
        String docID = null;

        try {
            for (QualityQuery t : topics) {

                System.out.printf("Searching for topic %s.%n", t.getQueryID());

                String tokenizedTitle = qpTitle.parse(QueryParserBase.escape(t.getValue(TOPIC_FIELDS.TITLE)))
                        .toString("title"); // expand

                String[] titleTerms = tokenizedTitle.split("\\s+");

                String tokenizedDescription = qpBody.parse(QueryParserBase.escape(t.getValue(TOPIC_FIELDS.DESCRIPTION)))
                        .toString("body");

                String[] descriptionTerms = tokenizedDescription.split("\\s+");

                Query ndcgMaximizer = buildNdcgQuery(titleTerms, descriptionTerms, 0);
                searcher.setSimilarity(new LMDirichletSimilarity());

                docs = searcher.search(ndcgMaximizer, maxDocsRetrieved);

                sd = docs.scoreDocs; // Result of the search

                for (int i = 0, n = sd.length; i < n; i++) {
                    docID = reader.document(sd[i].doc, idField).get(ParsedDocument.FIELDS.ID);

                    run.printf(Locale.ENGLISH, "%s\tQ0\t%s\t%d\t%.6f\t%s%n", t.getQueryID(), docID, i, sd[i].score,
                            runID);
                }

                run.flush();

            }
        } finally

        {
            run.close();
            // discussionRun.close();

            reader.close();
            // discussionReader.close();
        }

        elapsedTime = System.currentTimeMillis() - start;

        System.out.printf("%d topic(s) searched in %d seconds.", topics.length, elapsedTime / 1000);

        System.out.printf("#### Searching complete ####%n");
    }

    public void search4() throws IOException, ParseException {

        System.out.printf("%n#### Start searching ####%n");

        // the start time of the searching
        final long start = System.currentTimeMillis();

        final Set<String> idField = new HashSet<>();
        idField.add(ParsedDocument.FIELDS.ID);

        TopDocs docs = null;
        ScoreDoc[] sd = null;
        String docID = null;

        try {
            for (QualityQuery t : topics) {

                System.out.printf("Searching for topic %s.%n", t.getQueryID());

                String tokenizedTitle = qpTitle.parse(QueryParserBase.escape(t.getValue(TOPIC_FIELDS.TITLE)))
                        .toString("title"); // expand

                String[] titleTerms = tokenizedTitle.split("\\s+");

                String tokenizedDescription = qpBody.parse(QueryParserBase.escape(t.getValue(TOPIC_FIELDS.DESCRIPTION)))
                        .toString("body");

                String[] descriptionTerms = tokenizedDescription.split("\\s+");

                // Maximize recall
                Query recallMaximizer = buildRecallQuery(titleTerms, descriptionTerms, 0);

                // Search
                searcher.setSimilarity(
                        new MultiSimilarity(new Similarity[] { new BM25Similarity(), new LMDirichletSimilarity() }));

                docs = searcher.search(recallMaximizer, maxDocsRetrieved);

                sd = docs.scoreDocs; // Result of the search

                for (int i = 0, n = sd.length; i < n; i++) {
                    docID = reader.document(sd[i].doc, idField).get(ParsedDocument.FIELDS.ID);

                    run.printf(Locale.ENGLISH, "%s\tQ0\t%s\t%d\t%.6f\t%s%n", t.getQueryID(), docID, i, sd[i].score,
                            runID);
                }

                run.flush();

            }
        } finally

        {
            run.close();
            // discussionRun.close();

            reader.close();
            // discussionReader.close();
        }

        elapsedTime = System.currentTimeMillis() - start;

        System.out.printf("%d topic(s) searched in %d seconds.", topics.length, elapsedTime / 1000);

        System.out.printf("#### Searching complete ####%n");
    }

    /**
     * Searches for the specified topics.
     *
     * @throws IOException    if something goes wrong while searching.
     * @throws ParseException if something goes wrong while parsing topics.
     */
    public void searchBaseline() throws IOException, ParseException {

        System.out.printf("%n#### Start searching ####%n");

        // the start time of the searching
        final long start = System.currentTimeMillis();

        final Set<String> idField = new HashSet<>();
        idField.add(ParsedDocument.FIELDS.ID);

        TopDocs docs = null;
        ScoreDoc[] sd = null;
        String docID = null;

        // SYNONYMS PROPERTY
        System.setProperty("wordnet.database.dir", "./src/main/resources/dict");
        try {
            for (QualityQuery t : topics) {

                System.out.printf("Searching for topic %s.%n", t.getQueryID());

                String tokenizedTitle = qpTitle.parse(QueryParserBase.escape(t.getValue(TOPIC_FIELDS.TITLE)))
                        .toString("title"); // expand

                String[] titleTerms = tokenizedTitle.split("\\s+");

                String tokenizedDescription = qpBody.parse(QueryParserBase.escape(t.getValue(TOPIC_FIELDS.DESCRIPTION)))
                        .toString("body");

                String[] descriptionTerms = tokenizedDescription.split("\\s+");

                // Maximize recall
                Query baselineQuery = buildBaselineQuery(titleTerms, descriptionTerms);

                // Search
                searcher.setSimilarity(new BM25Similarity());

                docs = searcher.search(baselineQuery, maxDocsRetrieved);

                sd = docs.scoreDocs; // Result of the search

                for (int i = 0, n = sd.length; i < n; i++) {
                    docID = reader.document(sd[i].doc, idField).get(ParsedDocument.FIELDS.ID);

                    run.printf(Locale.ENGLISH, "%s\tQ0\t%s\t%d\t%.6f\t%s%n", t.getQueryID(), docID, i, sd[i].score,
                            runID);
                }

                run.flush();

            }
        } finally

        {
            run.close();
            // discussionRun.close();

            reader.close();
            // discussionReader.close();
        }

        elapsedTime = System.currentTimeMillis() - start;

        System.out.printf("%d topic(s) searched in %d seconds.", topics.length, elapsedTime / 1000);

        System.out.printf("#### Searching complete ####%n");
    }

    public Query buildBaselineQuery(String[] titleTerms, String[] descriptionTerms) throws ParseException {

        // BEST SET WITH DESCRIPTION (SAME AS NO DESCRIPTION)
        float bt = 1.0f;
        float bs = 0.0f;
        float bp = 0.0f;
        int dp = 0;

        Query titleQuery = buildTitleQuery(titleTerms, "title", bs, bt, bp, dp, 0);

        Query bodyQuery = buildTitleQuery(titleTerms, "body", bs, 1.0f, bp, dp, 0);

        BooleanQuery.Builder bq = new BooleanQuery.Builder();

        bq.add(titleQuery, BooleanClause.Occur.SHOULD);
        bq.add(bodyQuery, BooleanClause.Occur.SHOULD);
        // bq.add(descriptionQuery, BooleanClause.Occur.SHOULD);

        return bq.build();
    }

    public Query buildRecallQuery(String[] titleTerms, String[] descriptionTerms, int pCriteria) throws ParseException {

        // BEST SET WITH DESCRIPTION (SAME AS NO DESCRIPTION)
        //float bt = 3.5f;
        //float bs = 0.15f;
        //float bp = 1.75f;
        //int dp = 12;

        // NEW QREL PARAMS
        float bt = 0.3f;
        float bs = 0.20f;
        float bp = 0.75f;
        int dp = 12;

        Query titleQuery = buildTitleQuery(titleTerms, "title", bs, bt, bp, dp, pCriteria);

        Query bodyQuery = buildTitleQuery(titleTerms, "body", bs, 1.0f, bp, dp, pCriteria);

        BooleanQuery.Builder bq = new BooleanQuery.Builder();

        bq.add(titleQuery, BooleanClause.Occur.SHOULD);
        bq.add(bodyQuery, BooleanClause.Occur.SHOULD);
        // bq.add(descriptionQuery, BooleanClause.Occur.SHOULD);

        return bq.build();
    }

    public Query buildNdcgQuery(String[] titleTerms, String[] descriptionTerms, int pCriteria) throws ParseException {

        // BEST SET WITH DESCRIPTION
        /*
         * float bt = 0.3f; float bs = 0.05f; float bp = 1.75f; int dp = 15;
         */

        // BEST SET WITH NO DESCRIPTION
        //float bt = 0.0f;
        //float bs = 0.05f;
        //float bp = 0.75f;
        //int dp = 15;

        // NEW QREL PARAMS
        float bt = 0.15f;
        float bs = 0.05f;
        float bp = 0.75f;
        int dp = 17;

        Query titleQuery = buildTitleQuery(titleTerms, "title", bs, bt, bp, dp, pCriteria);

        Query bodyQuery = buildTitleQuery(titleTerms, "body", bs, 1.0f, bp, dp, pCriteria);

        BooleanQuery.Builder bq = new BooleanQuery.Builder();

        bq.add(titleQuery, BooleanClause.Occur.SHOULD);
        bq.add(bodyQuery, BooleanClause.Occur.SHOULD);
        // bq.add(descriptionQuery, BooleanClause.Occur.SHOULD);

        return bq.build();
    }

    public Query buildTitleQuery(String[] terms, String fieldName, float sBoost, float tBoost, float pBoost, int pDist, int pCriteria)
            throws ParseException {

        BooleanQuery.Builder qb = new BooleanQuery.Builder();
        QueryParser qp = new QueryParser(fieldName, qpPhrases.getAnalyzer());

        // Synonyms and Title boost
        StringBuilder titleWithSynonyms = new StringBuilder("(");
        for (String t : terms) {
            titleWithSynonyms.append(t + " ");
            if (sBoost != 0) {
                String[] syn = getSynonyms(t, sBoost);

                for (String s : syn) {
                    titleWithSynonyms.append(" " + s + "^" + sBoost);
                }
            }
        }
        titleWithSynonyms.append(")^" + tBoost);
        qb.add(qp.parse(titleWithSynonyms.toString()), BooleanClause.Occur.SHOULD);

        // Proximity
        if (pBoost != 0) {
            Query pq = proximityQuery(terms, fieldName, pDist, pBoost);
            if(pCriteria == 1) pq = proximityQuery2(terms, fieldName, pDist, pBoost);
            qb.add(pq, BooleanClause.Occur.SHOULD);
        }

        return qb.build();
    }

    public String[] getSynonyms(String term, float synBoost) {

        Synset[] synsets1 = database.getSynsets(term, SynsetType.NOUN);
        Synset[] synsets2 = database.getSynsets(term, SynsetType.ADJECTIVE);
        Synset[] synsets = ArrayUtils.addAll(synsets1, synsets2);

        ArrayList<String> al = new ArrayList<String>();

        if (synsets.length > 0) {

            // add elements to al, including duplicates
            for (int i = 0; i < synsets.length; i++) {

                String[] wordForms = synsets[i].getWordForms();

                // String type = synsets[i].getType().toString();

                for (int j = 0; j < wordForms.length; j++) {
                    if (wordForms[j].contains(" ") || wordForms[j].toLowerCase().equals(term))
                        continue; // Skip all synonyms made of multiple words
                    al.add(wordForms[j].toLowerCase());
                }

            }

            Set<String> set = new HashSet<>(al);
            al.clear();
            al.addAll(set);

        }

        return al.toArray(new String[0]);

    }


    public Query proximityQuery(String[] terms, String fieldName, int dist, float boost) throws ParseException {

        // terms = [vaping, cigarettes, safe]
        BooleanQuery.Builder qb = new BooleanQuery.Builder();

        QueryParser qp = new QueryParser(fieldName, qpPhrases.getAnalyzer());

        for (int i = 0; i < terms.length - 1; i++) {
            String t1 = terms[i];
            for (int j = i + 1; j < terms.length; j++) {
                String t2 = terms[j];
                if (t1.equals(t2))
                    continue;
                String phrase = "\"" + t1 + " " + t2 + "\"~" + dist; // "vaping cigarettes"~dist
                qb.add(qp.parse(phrase), BooleanClause.Occur.SHOULD);
            }
        }

        BoostQuery boosQ = new BoostQuery(qb.build(), boost);

        return boosQ;
    }

    public Query proximityQuery2(String[] terms, String fieldName, int dist, float boost) throws ParseException {

        // terms = [vaping, cigarettes, safe]
        BooleanQuery.Builder qb = new BooleanQuery.Builder();

        QueryParser qp = new QueryParser(fieldName, qpPhrases.getAnalyzer());

        for (int i = 0; i < terms.length - 1; i++) {
            String t1 = terms[i];
            String t2 = terms[i + 1];
            if (t1.equals(t2))
                continue;
            String phrase = "\"" + t1 + " " + t2 + "\"~" + dist; // "vaping cigarettes"~dist
            qb.add(qp.parse(phrase), BooleanClause.Occur.SHOULD);
        }

        BoostQuery boosQ = new BoostQuery(qb.build(), boost);

        return boosQ;
    }

    public Query expandSynonyms(String tokenizedQuery, WordNetDatabase database, double boost) throws ParseException {

        BooleanQuery.Builder qb = new BooleanQuery.Builder();

        String[] terms = tokenizedQuery.split("\\s+");

        for (String term : terms) {

            // Get synonyms

            // Get the synsets containing the word form=capicity

            Synset[] synsets1 = database.getSynsets(term, SynsetType.NOUN);
            Synset[] synsets2 = database.getSynsets(term, SynsetType.ADJECTIVE);
            Synset[] synsets = ArrayUtils.addAll(synsets1, synsets2);
            // Display the word forms and definitions for synsets retrieved

            ArrayList<String> al = new ArrayList<String>();
            al.add(term);

            if (synsets.length > 0) {

                // add elements to al, including duplicates
                for (int i = 0; i < synsets.length; i++) {

                    String[] wordForms = synsets[i].getWordForms();

                    for (int j = 0; j < wordForms.length; j++) {
                        if (wordForms[j].contains(" "))
                            continue;
                        al.add(wordForms[j].toLowerCase());
                    }

                }

            }

            Set<String> set = new HashSet<>(al);
            al.clear();
            al.addAll(set);

            if (al.size() > 1) {
                StringBuilder queryBuilder = new StringBuilder();

                for (String syn : al) {
                    if (syn.contains(" ")) {
                        syn = "\"" + syn + "\"";
                        continue;
                    }

                    if (syn.equals(term)) {
                        queryBuilder.append(syn + " ");
                    } else {
                        queryBuilder.append(syn + "^" + boost + " ");
                    }

                }
                qb.add(qpBody.parse(queryBuilder.toString()), BooleanClause.Occur.SHOULD);
                // qb.add(sq.build(), BooleanClause.Occur.SHOULD);

            } else {
                qb.add(qpBody.parse(term), BooleanClause.Occur.SHOULD);
            }

        }

        return qb.build();
    }

    public Query expandSynRandomBoost(String[] terms, WordNetDatabase database, double minBoost, double maxBoost)
            throws ParseException {

        BooleanQuery.Builder qb = new BooleanQuery.Builder();

        for (String term : terms) {

            // Get synonyms
            String truncTerm = term.substring(term.lastIndexOf("body:") + 5);

            // Get the synsets containing the word form=capicity

            Synset[] synsets1 = database.getSynsets(truncTerm, SynsetType.NOUN);
            Synset[] synsets2 = database.getSynsets(truncTerm, SynsetType.ADJECTIVE);
            Synset[] synsets = ArrayUtils.addAll(synsets1, synsets2);
            // Display the word forms and definitions for synsets retrieved

            ArrayList<String> al = new ArrayList<String>();
            al.add(truncTerm);

            if (synsets.length > 0) {

                // add elements to al, including duplicates
                for (int i = 0; i < synsets.length; i++) {

                    String[] wordForms = synsets[i].getWordForms();

                    for (int j = 0; j < wordForms.length; j++) {
                        if (wordForms[j].contains(" "))
                            continue;
                        al.add(wordForms[j].toLowerCase());
                    }

                }

            }

            Set<String> set = new HashSet<>(al);
            al.clear();
            al.addAll(set);

            if (al.size() > 1) {
                StringBuilder queryBuilder = new StringBuilder();

                for (String syn : al) {
                    if (syn.contains(" ")) {
                        syn = "\"" + syn + "\"";
                        continue;
                    }

                    if (syn.equals(truncTerm)) {
                        queryBuilder.append(syn + " ");
                    } else {
                        Random r = new Random();
                        // double randomBoost = minBoost + (maxBoost - minBoost) * r.nextDouble();
                        // queryBuilder.append(syn + "^" + randomBoost + " ");

                        double doBoost = r.nextDouble();
                        if (doBoost < 0.5)
                            queryBuilder.append(syn + "^" + maxBoost + " ");
                        else
                            queryBuilder.append(syn + "^" + 0 + " ");
                    }

                }
                qb.add(qpBody.parse(queryBuilder.toString()), BooleanClause.Occur.SHOULD);
                // qb.add(sq.build(), BooleanClause.Occur.SHOULD);

            } else {
                qb.add(qpBody.parse(term), BooleanClause.Occur.SHOULD);
            }

        }

        return qb.build();
    }

    public Query expandTitleSynonyms(String[] terms, WordNetDatabase database, double boost, double titleBoost)
            throws ParseException {

        BooleanQuery.Builder qb = new BooleanQuery.Builder();

        for (String term : terms) {

            // Get synonyms
            String truncTerm = term;// term.substring(term.lastIndexOf("title:") + 6);

            // Get the synsets containing the word form=capicity

            Synset[] synsets1 = database.getSynsets(truncTerm, SynsetType.NOUN);
            Synset[] synsets2 = database.getSynsets(truncTerm, SynsetType.ADJECTIVE);
            Synset[] synsets = ArrayUtils.addAll(synsets1, synsets2);
            // Display the word forms and definitions for synsets retrieved
            // System.out.println("Synonyms found : " + synsets.length);

            ArrayList<String> al = new ArrayList<String>();
            al.add(truncTerm);

            if (synsets.length > 0) {

                // add elements to al, including duplicates
                for (int i = 0; i < synsets.length; i++) {

                    String[] wordForms = synsets[i].getWordForms();

                    // String type = synsets[i].getType().toString();

                    for (int j = 0; j < wordForms.length; j++) {
                        if (wordForms[j].contains(" "))
                            continue; // Skip all synonyms made of multiple words
                        al.add(wordForms[j].toLowerCase());
                    }

                }

            }

            Set<String> set = new HashSet<>(al);
            al.clear();
            al.addAll(set);

            if (al.size() > 1) {
                StringBuilder queryBuilder = new StringBuilder();
                for (String syn : al) {
                    // PRINT SYNONYMS
                    if (syn.equals(truncTerm)) {
                        queryBuilder.append(syn + " ");
                    } else {
                        queryBuilder.append(syn + "^" + boost + " ");
                    }

                }
                qb.add(qpTitle.parse(queryBuilder.toString()), BooleanClause.Occur.SHOULD);
                // qb.add(sq.build(), BooleanClause.Occur.SHOULD);

            } else {
                qb.add(qpTitle.parse("title:" + truncTerm), BooleanClause.Occur.SHOULD);
            }

            // System.out.println("SynonimQuery : " + sq.build());

        }

        Query q = qb.build();

        q = qpTitle.parse("(" + q.toString() + ")^" + titleBoost);

        return q;
    }

    /**
     * Main method of the class. Just for testing purposes.
     *
     * @param args command line arguments.
     * @throws Exception if something goes wrong while indexing.
     */
    public static void main(String[] args) throws Exception {

        final String topics = "/home/filippo/se/collections/touche/inputDataset/topics-task-1.xml";

        final String indexPath = "experiment/index-p0-stop-nostem";

        final String runPath = "experiment";

        final String runID = "seupd2021-shanks-touche";

        final int maxDocsRetrieved = 30;

        final Analyzer a = CustomAnalyzer.builder().withTokenizer(StandardTokenizerFactory.class)
                .addTokenFilter(LowerCaseFilterFactory.class).addTokenFilter(StopFilterFactory.class).build();

        Searcher s = new Searcher(a, new BM25Similarity(), indexPath, topics, 50, runID, runPath, maxDocsRetrieved);

        s.searchOld(2f, 0.4, false, 0.0, 0.0, 1, false, 0f, 5);

    }

}
