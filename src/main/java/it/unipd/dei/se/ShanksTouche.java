package it.unipd.dei.se;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;

import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.MultiSimilarity;
import org.apache.lucene.search.similarities.Similarity;

import it.unipd.dei.se.analysis.ShanksAnalyzer;
import it.unipd.dei.se.index.DirectoryIndexer;
import it.unipd.dei.se.parse.ArgsParser;
import it.unipd.dei.se.parse.ArgsParser1;
import it.unipd.dei.se.parse.ArgsParser2;
import it.unipd.dei.se.parse.DocumentParser;
import it.unipd.dei.se.search.Searcher;

public class ShanksTouche {

    /**
     * {@code PARSER=0} uses {@code ArgsParser}. It considers "conclusion",
     * "premises.text", "discussionTitle/topic" and "stance" of the document
     * 
     * {@code PARSER=1} uses {@code ArgsParser1}. It considers the full source text.
     * 
     * {@code PARSER=2} uses {@code ArgsParser2}. It considers "conclusion",
     * "premises.text" and the sourceText in between.
     */
    private static int PARSER = 0;

    /**
     * {@code SIM=0} uses BM25Similarity
     *
     * {@code SIM=1} uses LMDirichletSimilarity
     *
     * {@code SIM=2} mixes BM25 and LMD
     */
    private static int SIM = 2;

    private static final int MIN_BODY_LENGTH = 0;

    private static final boolean DO_INDEX = true;

    private static final boolean DO_SEARCH = true;

    private static final boolean BENCHMARK = false;

    /**
     * The query is searched in both the document title and in the document body.
     * {@code BOOST_TITLE} allows to set a different boost for the query searched in
     * the document title.
     */
    private static float BOOST_TITLE = (float) 3.5;

    /**
     * Boost value used for synonyms
     */
    private static double BOOST_SYN = 0.15;
    /*
     * Boost value used for proximity search
     */
    private static float BOOST_PROXIMITY = 1.75f;// 0.25f;
    /**
     * Distance value used for proximity search
     */
    private static int DIST_PROXIMITY = 15;

    /**
     * Enables multiple run of the same query with random boost values
     */
    private static final boolean EXPAND_RANDOM = false;

    /**
     * Minimum boost value for synonyms
     */
    private static final double MIN_BOOST_SYN = 0.10;

    /**
     * maximum boost value for synonyms
     */
    private static final double MAX_BOOST_SYN = 0.15;

    /**
     * Number of the random query generated with random boost (deprecated)
     */
    private static final int N_RANDOM_QUERY = 5;

    /**
     * Main method of the class.
     *
     * @param args command line arguments. If provided, {@code args[0]} contains the
     *             path the the index directory; {@code args[1]} contains the path
     *             to the run file; {@code args[2]} (OPTIONAL) contains the number of runs to be execute.
     * @throws Exception if something goes wrong while indexing and searching.
     */
    public static void main(String[] args) throws Exception {

        /**
         * Check the number of the input arguments
         */
        if (args.length < 2) {
            throw new Exception(
                    "Some input argument is missing. \n\t REQUESTED ARGUMENTS : [path to inputDataset], [path to outputDir]");
        }

        for (String a : args) {
            System.out.println("Arg : " + a);
        }

        // args[0] input is the inputDataset directory path
        final String inputDataset = args[0];

        // args[1] input is output directory path
        final String output = args[1];

        final Path inputDatasetDir = Paths.get(inputDataset);
        final Path outputDir = Paths.get(output);

        // if the directory does not already exist, create it
        if (Files.notExists(inputDatasetDir)) {
            throw new Exception("The inputDataset directory does not exist!");
        }
        if (Files.notExists(outputDir)) {
            throw new Exception("The output directory does not exist!");
        }

        /**
         * 1 -> RUN 1 - Re-Ranking approach based on maxNdcg and maxRecall (default)
         * 2 -> RUN 2 - Re-Ranking approach based on maxNdcg and maxRecall
         * 3 -> RUN 3 - maxNdcg using LMD similarity
         * 4 -> RUN 4 - maxNdcg using MULTI similarity
         * 5 -> RUN 5 - maxRecall using MULTI similarity
         */
        final int APPROACH;
        if (args.length > 2) {
            APPROACH = Integer.parseInt(args[2]);
            if (APPROACH > 5 || APPROACH < 1)
                throw new Exception("The provided approach number is not valid!");
        } else {
            APPROACH = 1;
        }

        System.out.println("Producing run-"+APPROACH);

        final int ramBuffer = 2048;

        Class<? extends DocumentParser> parser;

        if (BENCHMARK) {
            // float[] bt = { 0f, 0.5f, 1f, 1.25f, 1.5f, 2f, 2.5f, 3f };
            float[] bt = { 0f, 0.15f, 0.3f, 3.5f, 5.0f };
            float[] bs = { 0f, 0.05f, 0.1f, 0.15f, 0.20f, 0.25f };
            float[] bp = { 0.75f, 1f, 1.25f, 1.75f };
            int[] dist = { 12, 15, 17 };

            for (float boost_title : bt) {
                for (float boost_synonym : bs) {
                    for (float boost_proximity : bp) {
                        for (int p_dist : dist) {
                            if (boost_proximity == 0)
                                continue;
                            // SET PARAMS
                            BOOST_TITLE = boost_title;
                            BOOST_SYN = boost_synonym;
                            BOOST_PROXIMITY = boost_proximity;
                            DIST_PROXIMITY = p_dist;

                            // String runID = "seupd2021-shanks-touche";
                            String indexPath = "experiment/index";
                            String runID = "shanks-"; // shanks-[parser]-[similarity]-[boost_title]-[boost_syn]-[boost_prox]

                            switch (PARSER) {
                            case 1:
                                indexPath += "-P1";
                                runID += "-P1";
                                parser = ArgsParser1.class;
                                break;
                            case 2:
                                indexPath += "-P2";
                                parser = ArgsParser2.class;
                                runID += "-P2";
                                break;
                            default:
                                indexPath += "-P0";
                                parser = ArgsParser.class;
                                runID += "-P0";
                                break;
                            }

                            Similarity sim = null;

                            switch (SIM) {
                            case 0:
                                sim = new BM25Similarity();
                                runID += "-BM25";
                                indexPath += "-BM25";
                                break;
                            case 1:
                                sim = new LMDirichletSimilarity();
                                runID += "-LMD";
                                indexPath += "-LMD";
                                break;
                            case 2:
                                sim = new MultiSimilarity(
                                        new Similarity[] { new BM25Similarity(), new LMDirichletSimilarity() });
                                runID += "-MULTI";
                                indexPath += "-MULTI";
                                break;

                            default:
                                sim = new BM25Similarity();
                                break;
                            }

                            if (BOOST_TITLE != 1) {
                                runID += String.format("-BT(%1.2f)", BOOST_TITLE);
                            }

                            if (BOOST_SYN != 0) {
                                runID += "-EXP";
                                runID += String.format("-BS(%1.2f)", BOOST_SYN);

                                /*
                                 * if (EXPAND_RANDOM) { runID += String.format("-RAND-(%1.2f-%1.2f)",
                                 * MIN_BOOST_SYN, MAX_BOOST_SYN); } else { runID += String.format("-BS(%1.2f)",
                                 * BOOST_SYN); }
                                 * 
                                 * if (EXPAND_RANDOM && N_RANDOM_QUERY > 1) { runID +=
                                 * String.format("-#Q-%d-(0.5)", N_RANDOM_QUERY); }
                                 */
                            }

                            if (BOOST_PROXIMITY != 0) {
                                runID += String.format("-BP(%1.2f)", BOOST_PROXIMITY);
                                runID += String.format("-DP(%d)", DIST_PROXIMITY);
                            }

                            indexPath += "-stop-nostem";

                            if (MIN_BODY_LENGTH != 0) {
                                indexPath += String.format("-MBL(%d)", MIN_BODY_LENGTH);
                                runID += String.format("-MBL(%d)", MIN_BODY_LENGTH);
                            }

                            long timestamp = new Date().getTime() / 1000;

                            ShanksAnalyzer ha = new ShanksAnalyzer();

                            final String topics = inputDataset + "/topics-task-1.xml";

                            final int maxDocsRetrieved = 1000;

                            final int expectedTopics = 50;

                            // searching
                            if (DO_SEARCH) {
                                runID += "_" + timestamp;
                                final Searcher s = new Searcher(ha, sim, indexPath, topics, expectedTopics, runID,
                                        outputDir.toString(), maxDocsRetrieved);
                                s.searchOld(BOOST_TITLE, BOOST_SYN, EXPAND_RANDOM, MIN_BOOST_SYN, MAX_BOOST_SYN,
                                        N_RANDOM_QUERY, false, BOOST_PROXIMITY, DIST_PROXIMITY);
                            }
                        }
                    }
                }
            }
        } else {
            // String runID = "seupd2021-shanks-touche";
            String indexPath = "experiment/index";
            String runID = "shanks-run-"+APPROACH; // shanks-[parser]-[similarity]-[boost_title]-[boost_syn]-[boost_prox]

            //Create new object ArgsParser
            parser = ArgsParser.class;

            Similarity sim = null;

            //Set the similarity chosen
            switch (APPROACH) {
            case 3:
                sim = new LMDirichletSimilarity();
                break;
            default:
                sim = new MultiSimilarity(new Similarity[] { new BM25Similarity(), new LMDirichletSimilarity() });
                break;
            }

            final String extension = "json";
            final int expectedDocs = 387740;
            final String charsetName = "ISO-8859-1";

            //Create new object ShanksAnalyzer
            ShanksAnalyzer shanksA = new ShanksAnalyzer();


            final String topics = inputDataset + "/topics.xml";

            final int maxDocsRetrieved = 1000;

            final int expectedTopics = 50;

            // indexing
            if (DO_INDEX) {
                final DirectoryIndexer i = new DirectoryIndexer(shanksA, sim, ramBuffer, indexPath, inputDataset,
                        extension, charsetName, expectedDocs, parser);
                i.index();
            }

            // searching
            if (DO_SEARCH) {
                final Searcher s = new Searcher(shanksA, sim, indexPath, topics, expectedTopics, runID,
                        outputDir.toString(), maxDocsRetrieved);

                s.search(APPROACH);
            }
        }
    }

}
