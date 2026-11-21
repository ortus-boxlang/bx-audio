"""
Derives src/main/resources/kokoro-phonemes.json, the table KokoroPhonemes uses to turn what the Voices phonemizer says into what Kokoro was
trained on.

Kokoro (v1.0) was trained on espeak-ng phonemes. bx-audio has no espeak-ng, only the Voices library's own dictionary, whose phonemes differ in a
few systematic ways. The biggest is that it puts a stress mark on every function word. Run the same sentences through both and, for each function
word, keep the espeak-ng spelling that most often stands where the Voices spelling does.

This is a development tool, run by hand when the dictionary or the model changes; the module never needs Python. It needs:

    pip install kokoro-onnx            (for its espeak-ng tokenizer and the model's vocabulary)
    a folder with one sentence per line:                       sentences.txt
    the Voices phonemes of each sentence, one line per sentence, phonemes joined with |, by the Java side of the tool: tools/kokoro/Phonemes.java
    the same for the function words alone                      words.txt, words-<lang>.txt

  python derive-words.py <sentences.txt> <words.txt> <us-phonemes.txt> <us-words.txt> <gb-phonemes.txt> <gb-words.txt> <out.json>
"""
import collections, json, os, sys

import kokoro_onnx
from kokoro_onnx.tokenizer import Tokenizer

PUNCT = set(",.;:!?()\"“”—…")
sentences, words, us_lines, us_words, gb_lines, gb_words, out = sys.argv[1:8]
vocab = json.load(open(os.path.join(os.path.dirname(kokoro_onnx.__file__), "config.json")))["vocab"]
tok = Tokenizer()


def strip(w):
	return "".join(c for c in w if c not in "ˈˌ" and c not in PUNCT)


def table(lang, espeak_lang, lines_file, words_file):
	function = [w.strip() for w in open(words)]
	function_ipa = [l.rstrip("\n") for l in open(words_file)]
	keys = {strip("".join(ph for ph in ipa.split("|"))): w for w, ipa in zip(function, function_ipa)}
	counts = collections.defaultdict(collections.Counter)
	for text, ipa in zip(open(sentences), open(lines_file)):
		reference = tok.phonemize(text.strip(), espeak_lang).split()
		ours = "".join(c for c in "".join(ipa.rstrip("\n").split("|")) if c in vocab).split()
		if len(reference) != len(ours):
			continue
		for a, b in zip(ours, reference):
			if strip(a) in keys:
				counts[strip(a)][b.strip("".join(PUNCT))] += 1
	result = {}
	for key, c in counts.items():
		best, n = c.most_common(1)[0]
		if n >= 2 and n / sum(c.values()) >= 0.5:
			result[key] = best
	return {"function": sorted(keys), "words": result}


json.dump({
	"vocab": vocab,
	"en_US": table("en_US", "en-us", us_lines, us_words),
	"en_GB": table("en_GB", "en-gb", gb_lines, gb_words),
}, open(out, "w"), ensure_ascii=False, indent="\t", sort_keys=True)
print("wrote", out)
