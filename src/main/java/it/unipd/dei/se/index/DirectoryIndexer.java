package it.unipd.dei.se.index;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Hashtable;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.LowerCaseFilterFactory;
import org.apache.lucene.analysis.core.StopFilterFactory;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.en.PorterStemFilterFactory;
import org.apache.lucene.analysis.standard.StandardTokenizerFactory;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.FSDirectory;

import it.unipd.dei.se.parse.ArgsParser;
import it.unipd.dei.se.parse.DocumentParser;
import it.unipd.dei.se.parse.ParsedDocument;

/**
 * Indexes documents processing a whole directory tree.
 */
public class DirectoryIndexer {

    /**
     * One megabyte
     */
    private static final int MBYTE = 1024 * 1024;

    /**
     * The index writer.
     */
    private final IndexWriter writer;

    /**
     * The class of the {@code DocumentParser} to be used.
     */
    private final Class<? extends DocumentParser> dpCls;

    /**
     * The directory (and sub-directories) where documents are stored.
     */
    private final Path docsDir;

    /**
     * The extension of the files to be indexed.
     */
    private final String extension;

    /**
     * The charset used for encoding documents.
     */
    private final Charset cs;

    /**
     * The total number of documents expected to be indexed.
     */
    private final long expectedDocs;

    /**
     * The start instant of the indexing.
     */
    private final long start;

    /**
     * The total number of indexed files.
     */
    private long filesCount;

    /**
     * The total number of indexed documents.
     */
    private long docsCount;

    /**
     * The total number of indexed bytes
     */
    private long bytesCount;

    /**
     * Creates a new indexer.
     *
     * @param analyzer        the {@code Analyzer} to be used.
     * @param similarity      the {@code Similarity} to be used.
     * @param ramBufferSizeMB the size in megabytes of the RAM buffer for indexing
     *                        documents.
     * @param indexPath       the directory where to store the index.
     * @param docsPath        the directory from which documents have to be read.
     * @param extension       the extension of the files to be indexed.
     * @param charsetName     the name of the charset used for encoding documents.
     * @param expectedDocs    the total number of documents expected to be indexed
     * @param dpCls           the class of the {@code DocumentParser} to be used.
     * @throws NullPointerException     if any of the parameters is {@code null}.
     * @throws IllegalArgumentException if any of the parameters assumes invalid
     *                                  values.
     */
    public DirectoryIndexer(final Analyzer analyzer, final Similarity similarity, final int ramBufferSizeMB,
            final String indexPath, final String docsPath, final String extension, final String charsetName,
            final long expectedDocs, final Class<? extends DocumentParser> dpCls) {

        // Parser acquisition
        if (dpCls == null) {
            throw new NullPointerException("Document parser class cannot be null.");
        }

        this.dpCls = dpCls;

        // Analyzer, similarity function and ram buffer size acquisition
        if (analyzer == null) {
            throw new NullPointerException("Analyzer cannot be null.");
        }

        if (similarity == null) {
            throw new NullPointerException("Similarity cannot be null.");
        }

        if (ramBufferSizeMB <= 0) {
            throw new IllegalArgumentException("RAM buffer size cannot be less than or equal to zero.");
        }

        // Configuration for 2 index writer
        final IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
        iwc.setSimilarity(similarity);
        iwc.setRAMBufferSizeMB(ramBufferSizeMB);
        iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        iwc.setCommitOnClose(true);

        // Setting were to write the indexes
        if (indexPath == null) {
            throw new NullPointerException("Index path cannot be null.");
        }

        if (indexPath.isEmpty()) {
            throw new IllegalArgumentException("Index path cannot be empty.");
        }

        final Path indexDir = Paths.get(indexPath);

        // if the directory does not already exist, create it
        if (Files.notExists(indexDir)) {
            try {
                Files.createDirectory(indexDir);
            } catch (Exception e) {
                throw new IllegalArgumentException(String.format("Unable to create directory %s:.",
                        indexDir.toAbsolutePath().toString(), e.getMessage()), e);
            }
        }

        if (!Files.isWritable(indexDir)) {
            throw new IllegalArgumentException(
                    String.format("Index directory %s cannot be written.", indexDir.toAbsolutePath().toString()));
        }

        if (!Files.isDirectory(indexDir)) {
            throw new IllegalArgumentException(String.format("%s expected to be a directory where to write the index.",
                    indexDir.toAbsolutePath().toString()));
        }

        // Path to catch the documents for the retrieval initialization
        if (docsPath == null) {
            throw new NullPointerException("Documents path cannot be null.");
        }

        if (docsPath.isEmpty()) {
            throw new IllegalArgumentException("Documents path cannot be empty.");
        }

        final Path docsDir = Paths.get(docsPath);
        if (!Files.isReadable(docsDir)) {
            throw new IllegalArgumentException(
                    String.format("Documents directory %s cannot be read.", docsDir.toAbsolutePath().toString()));
        }

        if (!Files.isDirectory(docsDir)) {
            throw new IllegalArgumentException(
                    String.format("%s expected to be a directory of documents.", docsDir.toAbsolutePath().toString()));
        }

        this.docsDir = docsDir;

        // Set the extension of the files to be red
        if (extension == null) {
            throw new NullPointerException("File extension cannot be null.");
        }

        if (extension.isEmpty()) {
            throw new IllegalArgumentException("File extension cannot be empty.");
        }
        this.extension = extension;


        // Setting of charset Name
        if (charsetName == null) {
            throw new NullPointerException("Charset name cannot be null.");
        }

        if (charsetName.isEmpty()) {
            throw new IllegalArgumentException("Charset name cannot be empty.");
        }

        try {
            cs = Charset.forName(charsetName);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    String.format("Unable to create the charset %s: %s.", charsetName, e.getMessage()), e);
        }

        // Number of expected document
        if (expectedDocs <= 0) {
            throw new IllegalArgumentException(
                    "The expected number of documents to be indexed cannot be less than or equal to zero.");
        }
        this.expectedDocs = expectedDocs;

        this.docsCount = 0;

        this.bytesCount = 0;

        this.filesCount = 0;

        try {
            writer = new IndexWriter(FSDirectory.open(indexDir), iwc);
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Unable to create the index writer in directory %s: %s.",
                    indexDir.toAbsolutePath().toString(), e.getMessage()), e);
        }

        this.start = System.currentTimeMillis();

    }

    /**
     * Indexes the documents.
     *
     * @throws IOException if something goes wrong while indexing.
     */
    public void index() throws IOException {

        boolean indexDiscussions = false;
        int minBodyLength = 0;

        System.out.printf("%n#### Start indexing ####%n");

        // Hash table for mapping between document id and index document id
        Hashtable<String, Integer> docIds = new Hashtable<String, Integer>();

        Files.walkFileTree(docsDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                // If we find a file of the desired extension we parse it
                if (file.getFileName().toString().endsWith(extension)) {

                    DocumentParser dp = DocumentParser.create(dpCls, Files.newBufferedReader(file, cs));

                    bytesCount += Files.size(file);

                    // Counter
                    filesCount += 1;

                    // Lucene document used to build the index
                    Document doc = null;

                    Document discussion = null;

                    String currentDiscussionId = "";
                    String previousDiscussionId = null;

                    String discussionTitle = null;
                    int discussionCardinality = 0;

                    StringBuilder discussionBody = new StringBuilder();

                    for (ParsedDocument pd : dp) {

                        //if the body length of a document is less than 5 we can say that the document is not relevant
                        if (pd.getBody().split(" ").length >= minBodyLength) {
                            // Check if the document id was already written in the index.
                            Integer counter = docIds.get(pd.getIdentifier());
                            if (counter != null) {
                                docIds.put(pd.getIdentifier(), docIds.get(pd.getIdentifier()) + 1);
                            } else {

                                if (indexDiscussions) {
                                    currentDiscussionId = pd.getIdentifier().substring(0,
                                            pd.getIdentifier().lastIndexOf("-"));
                                    if (currentDiscussionId.equals(previousDiscussionId)) {
                                        // Append to current discussion
                                        discussionBody.append(" ").append(pd.getBody());
                                        discussionCardinality++;
                                    } else {
                                        // Write previous discussion to index
                                        if (previousDiscussionId != null) {
                                            discussion = new Document();
                                            // add the document identifier
                                            discussion.add(new StringField(ParsedDocument.FIELDS.ID, previousDiscussionId,
                                                    Field.Store.YES));
                                            // add the document title
                                            discussion.add(new TitleField(discussionTitle));
                                            // add the document body
                                            discussion.add(new BodyField(discussionBody.toString()));

                                            discussion.add(new StringField("cardinality", discussionCardinality + "",
                                                    Field.Store.YES));

                                            //discussionWriter.addDocument(discussion);
                                        }

                                        // Start new discussion
                                        discussionCardinality = 1;
                                        discussionBody = new StringBuilder(pd.getBody());
                                        discussionTitle = pd.getTitle();

                                    }

                                    previousDiscussionId = currentDiscussionId;
                                }
                                docIds.put(pd.getIdentifier(), 1);

                                doc = new Document();

                                // add the document identifier
                                doc.add(new StringField(ParsedDocument.FIELDS.ID, pd.getIdentifier(), Field.Store.YES));

                                // add the document title
                                doc.add(new TitleField(pd.getTitle()));

                                // add the document body
                                doc.add(new BodyField(pd.getBody()));

                                // add the document stance
                                doc.add(new StringField(ParsedDocument.FIELDS.STANCE, pd.getStance(), Field.Store.YES));

                                writer.addDocument(doc);

                                docsCount++;

                            }

                            // print progress every 10000 indexed documents
                            if (docsCount % 10000 == 0) {
                                System.out.printf("%d document(s) (%d files, %d Mbytes) indexed in %d seconds.%n",
                                        docsCount, filesCount, bytesCount / MBYTE,
                                        (System.currentTimeMillis() - start) / 1000);
                            }
                        }
                    }

                        if (previousDiscussionId != null && indexDiscussions) {
                            discussion = new Document();
                            // add the document identifier
                            discussion
                                    .add(new StringField(ParsedDocument.FIELDS.ID, previousDiscussionId, Field.Store.YES));
                            // add the document title
                            discussion.add(new TitleField(discussionTitle));
                            // add the document body
                            discussion.add(new BodyField(discussionBody.toString()));

                            discussion.add(new StringField("cardinality", discussionCardinality + "", Field.Store.YES));

                            //discussionWriter.addDocument(discussion);
                        }
                }
                return FileVisitResult.CONTINUE;
            }
        });

        writer.commit();
        //discussionWriter.commit();

        writer.close();
        //discussionWriter.close();

        if (docsCount != expectedDocs) {
            System.out.printf("Expected to index %d documents; %d indexed instead.%n", expectedDocs, docsCount);
        }

        System.out.printf("%d document(s) (%d files, %d Mbytes) indexed in %d seconds.%n", docsCount, filesCount,
                bytesCount / MBYTE, (System.currentTimeMillis() - start) / 1000);

        System.out.printf("#### Indexing complete ####%n");
        /*
         * Set<Entry<String, Integer>> entries = docIds.entrySet(); for(Entry<String,
         * Integer> entry : entries){ if(entry.getValue() > 1)
         * System.out.printf("Document { id : %s, freq : %d }\n",entry.getKey(),
         * entry.getValue()); }
         */
    }

    /**
     * Main method of the class. Just for testing purposes.
     *
     * @param args command line arguments.
     * @throws Exception if something goes wrong while indexing.
     */
    public static void main(String[] args) throws Exception {

        final int ramBuffer = 256;
        final String docsPath = "/home/user/se/collections/touche/inputDataset";
        final String indexPath = "experiment/index-stop-stem";

        final String extension = "json";
        final int expectedDocs = 387740;
        final String charsetName = "ISO-8859-1";

        final Analyzer a = CustomAnalyzer.builder().withTokenizer(StandardTokenizerFactory.class)
                .addTokenFilter(LowerCaseFilterFactory.class).addTokenFilter(StopFilterFactory.class)
                .addTokenFilter(PorterStemFilterFactory.class).build();

        DirectoryIndexer i = new DirectoryIndexer(a, new BM25Similarity(), ramBuffer, indexPath, docsPath, extension,
                charsetName, expectedDocs, ArgsParser.class);

        i.index();

    }

}
