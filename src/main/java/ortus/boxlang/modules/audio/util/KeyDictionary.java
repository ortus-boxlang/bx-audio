/**
 * [BoxLang]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
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
package ortus.boxlang.modules.audio.util;

import ortus.boxlang.runtime.scopes.Key;

/**
 * This class is used to store all the keys used in the dictionary for this module.
 */
public class KeyDictionary {

	public static final Key	moduleName	= new Key( "bx-audio" );

	public static final Key	audio		= Key.of( "audio" );
	public static final Key	bitDepth	= Key.of( "bitDepth" );
	public static final Key	channels	= Key.of( "channels" );
	public static final Key	frames		= Key.of( "frames" );
	public static final Key	peak		= Key.of( "peak" );
	public static final Key	rms			= Key.of( "rms" );
	public static final Key	sampleRate	= Key.of( "sampleRate" );
	public static final Key	source		= Key.of( "source" );
	public static final Key	audios		= Key.of( "audios" );
	public static final Key	curve		= Key.of( "curve" );
	public static final Key	db			= Key.of( "db" );
	public static final Key	fadeIn		= Key.of( "fadeIn" );
	public static final Key	fadeOut		= Key.of( "fadeOut" );
	public static final Key	pan			= Key.of( "pan" );
	public static final Key	targetDb	= Key.of( "targetDb" );
	public static final Key	amountDb	= Key.of( "amountDb" );
	public static final Key	attack		= Key.of( "attack" );
	public static final Key	beatsPerBar	= Key.of( "beatsPerBar" );
	public static final Key	bpm			= Key.of( "bpm" );
	public static final Key	drive		= Key.of( "drive" );
	public static final Key	level		= Key.of( "level" );
	public static final Key	recovery	= Key.of( "recovery" );
	public static final Key	release		= Key.of( "release" );
	public static final Key	sound		= Key.of( "sound" );
	public static final Key	tail		= Key.of( "tail" );
	public static final Key	thresholdDb	= Key.of( "thresholdDb" );
	public static final Key	times		= Key.of( "times" );
	public static final Key	timeline	= Key.of( "timeline" );
	public static final Key	track		= Key.of( "track" );
	public static final Key	trigger		= Key.of( "trigger" );
	public static final Key	at			= Key.of( "at" );
	public static final Key	step		= Key.of( "step" );
	public static final Key	start		= Key.of( "start" );
	public static final Key	end			= Key.of( "end" );

}
