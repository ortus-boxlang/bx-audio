/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package ortus.boxlang.modules.audio.bifs;

import com.fasterxml.jackson.jr.ob.JSON;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ortus.boxlang.modules.audio.codec.AudioFiles;
import ortus.boxlang.modules.audio.dsp.Dsp;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.Audios;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

@BoxBIF( description = "Join named sounds into one audio sprite with a map of where each starts, the way Howler.js and game engines load effects" )
public class AudioSprite extends BIF {

	private static final Key	sounds	= Key.of( "sounds" );
	private static final Key	gap		= Key.of( "gap" );
	private static final Key	path	= Key.of( "path" );

	/**
	 * Constructor
	 */
	public AudioSprite() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "struct", sounds ),
		    new Argument( false, "double", gap, 0.1 ),
		    new Argument( false, "string", path, "" )
		};
	}

	/**
	 * Join named sounds into one audio sprite with a map of where each starts, the way Howler.js and game engines load effects
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.sounds A struct of name to audio buffer. They are joined in the order of the struct, so use an ordered struct, written with square
	 *                  brackets [ jump: a, coin: b ], when the order matters.
	 *
	 * @argument.gap Seconds of silence between sounds. Defaults to 0.1.
	 *
	 * @argument.path A file to write the sprite audio to; a .json map is written next to it in the Howler.js format. Empty to only return it.
	 *
	 * @return A struct with audio (the joined buffer) and sprites, a struct of name to { start, end, duration } in seconds. When a path is given, also
	 *         path and mapPath.
	 */
	public IStruct _invoke( IBoxContext context, ArgumentsScope arguments ) {
		List<AudioBuffer>	parts	= new ArrayList<>();
		IStruct				sprites	= new Struct( IStruct.TYPES.LINKED );
		Struct				map		= new Struct( IStruct.TYPES.LINKED );
		double				clock	= 0;
		AudioBuffer			first	= null;
		IStruct				given	= arguments.getAsStruct( sounds );
		if ( given.isEmpty() ) {
			throw new BoxRuntimeException( "sounds must have at least one sound" );
		}
		for ( var entry : given.entrySet() ) {
			AudioBuffer sound = Audios.require( entry.getValue(), "sounds." + entry.getKey().getName() );
			if ( first == null ) {
				first = sound;
			} else if ( sound.getChannels() != first.getChannels() || sound.getSampleRate() != first.getSampleRate() ) {
				throw new BoxRuntimeException( "All sounds in a sprite must have the same channels and sample rate: [" + entry.getKey().getName() + "] is "
				    + sound.getChannels() + " ch " + sound.getSampleRate() + " Hz but the first is " + first.getChannels() + " ch " + first.getSampleRate()
				    + " Hz. Use audioChannels() and audioResample()." );
			}
			if ( !parts.isEmpty() && arguments.getAsDouble( gap ) > 0 ) {
				AudioBuffer silence = AudioBuffer.silenceSeconds( arguments.getAsDouble( gap ), first.getChannels(), first.getSampleRate() );
				parts.add( silence );
				clock += silence.getDurationSeconds();
			}
			sprites.put( entry.getKey(),
			    Struct.ofNonConcurrent( "start", clock, "end", clock + sound.getDurationSeconds(), "duration", sound.getDurationSeconds() ) );
			map.put( entry.getKey().getName(), new Array( new Object[] { Math.round( clock * 1000 ), Math.round( sound.getDurationSeconds() * 1000 ) } ) );
			parts.add( sound );
			clock += sound.getDurationSeconds();
		}
		AudioBuffer	joined	= Dsp.concat( parts );
		IStruct		result	= Struct.ofNonConcurrent( "audio", joined, "sprites", sprites );
		String		file	= arguments.getAsString( path );
		if ( !file.isBlank() ) {
			Path target = Path.of( file ).toAbsolutePath();
			try {
				Files.createDirectories( target.getParent() );
			} catch ( IOException e ) {
				throw new BoxRuntimeException( "Could not create the folder of [" + target + "]: " + e.getMessage(), e );
			}
			AudioFiles.write( joined, target, 16, 0 );
			String	name	= target.getFileName().toString();
			Path	mapFile	= target.resolveSibling( name.contains( "." ) ? name.substring( 0, name.lastIndexOf( '.' ) ) + ".json" : name + ".json" );
			try {
				Map<String, Object> howler = new LinkedHashMap<>();
				howler.put( "src", List.of( name ) );
				Map<String, Object> spriteMap = new LinkedHashMap<>();
				map.forEach( ( k, v ) -> spriteMap.put( k.getName(), ( ( Array ) v ).toArray() ) );
				howler.put( "sprite", spriteMap );
				Files.writeString( mapFile, JSON.std.with( JSON.Feature.PRETTY_PRINT_OUTPUT ).asString( howler ) + "\n" );
			} catch ( IOException e ) {
				throw new BoxRuntimeException( "Could not write [" + mapFile + "]: " + e.getMessage(), e );
			}
			result.put( Key.of( "path" ), target.toString() );
			result.put( Key.of( "mapPath" ), mapFile.toString() );
		}
		return result;
	}

}
