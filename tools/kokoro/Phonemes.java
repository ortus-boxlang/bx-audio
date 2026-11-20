import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.pitest.voices.Chorus;
import org.pitest.voices.ChorusConfig;
import org.pitest.voices.Language;
import org.pitest.voices.g2p.core.G2PModel;
import org.pitest.voices.g2p.core.PiperPhonemizer;

/**
 * A development tool for tools/kokoro/derive-words.py: prints what the Voices phonemizer says for each line of a file, one line of output per line
 * of input, the phonemes joined with |. It is what KokoroEngine does to a sentence before KokoroPhonemes rewrites it.
 *
 * java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "<the jars of ~/.bxaudio/engine/voices-0.0.9>" tools/kokoro/Phonemes.java en_US <work
 * folder> <file>
 *
 * The language is en_US or en_GB, and the work folder is a scratch folder the engine may write to.
 */
public class Phonemes {

	public static void main( String[] args ) throws Exception {
		boolean									british		= args[ 0 ].equals( "en_GB" );
		org.pitest.voices.g2p.core.Dictionary	dictionary	= british ? org.pitest.voices.uk.EnUkDictionary.en_uk()
		    : org.pitest.voices.us.EnUsDictionary.en_us();
		ChorusConfig							config		= ChorusConfig.chorusConfig( dictionary ).withBase( Path.of( args[ 1 ] ) );
		Chorus									chorus		= new Chorus( config );
		// the Chorus makes its grapheme to phoneme model on demand and keeps it private, as PiperEngine.phonemize relies on
		Method									g2p			= Chorus.class.getDeclaredMethod( "g2p" );
		g2p.setAccessible( true );
		PiperPhonemizer phonemizer = new PiperPhonemizer( ( G2PModel ) g2p.invoke( chorus ), config.expansions(), config.trace() );
		for ( String line : Files.readAllLines( Path.of( args[ 2 ] ) ) ) {
			List<String> phonemes = phonemizer.phonemize( british ? Language.en_GB : Language.en_US, line );
			System.out.println( String.join( "|", phonemes ) );
		}
		chorus.close();
	}

}
