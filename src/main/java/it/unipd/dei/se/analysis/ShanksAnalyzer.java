package it.unipd.dei.se.analysis;


import static it.unipd.dei.se.analysis.AnalyzerUtil.consumeTokenStream;

import java.io.IOException;
import java.io.Reader;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.en.EnglishPossessiveFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;

public class ShanksAnalyzer extends Analyzer {

	/**
	 * Creates a new instance of the analyzer.
	 */
	public ShanksAnalyzer() {
		super();
	}

	@Override
	protected TokenStreamComponents createComponents(String fieldName) {

		final Tokenizer source = new StandardTokenizer();

		TokenStream tokens = new LowerCaseFilter(source);

		tokens = new EnglishPossessiveFilter(tokens);

		tokens = new StopFilter(tokens, AnalyzerUtil.loadStopList("personalStopWords.txt"));

		return new TokenStreamComponents(source, tokens);
	}

	@Override
	protected Reader initReader(String fieldName, Reader reader) {
		// return new HTMLStripCharFilter(reader);

		return super.initReader(fieldName, reader);
	}

	@Override
	protected TokenStream normalize(String fieldName, TokenStream in) {
		return new LowerCaseFilter(in);
	}

	/**
	 * Main method of the class.
	 *
	 * @param args command line arguments.
	 *
	 * @throws IOException if something goes wrong while processing the text.
	 */
	public static void main(String[] args) throws IOException {

		// text to analyze
		final String text = "I now live in Rome where I met my wife Alice back in 2010 during a beautiful afternoon. " + "Occasionally, I fly to New York to visit the United Nations where I would like to work. The last " + "time I was there in March 2019, the flight was very inconvenient, leaving at 4:00 am, and expensive," + " over 1,500 dollars.";

		//final String text = "This is my simple test."+"I'm testing it.";
		// use the analyzer to process the text and print diagnostic information about each token
		consumeTokenStream(new ShanksAnalyzer(), text);


	}

}
