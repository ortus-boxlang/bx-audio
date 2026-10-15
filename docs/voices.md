# Voices

These are the voices `audioSpeak()` can use. They are [Piper](https://github.com/rhasspy/piper) voices from the [rhasspy/piper-voices](https://huggingface.co/rhasspy/piper-voices) collection, and none of them is bundled with the module: each is downloaded the first time you use it, into the `voices` folder of the module's speech home.

```bash
bx-audio voices                       # this list, from the command line
bx-audio install joe                  # download a voice ahead of time
bx-audio speak "Hello" --voice=joe     # or just use it, it installs itself
```

```js
audio = audioSpeak( text="Hello from BoxLang", voice="joe" );
audioSave( audio, "hello.mp3" );
```

## Licenses

Only voices whose model card states an open license are listed: CC0, public domain, CC BY, CC BY-SA, Apache 2.0 or the Unlicense. The license column is the identifier we map the card's wording to, and the card itself stays the authority, so read it when it matters (the link in the last column leads to the training data). A voice licensed CC BY asks for credit, and CC BY-SA also asks that what you make from it is shared under the same terms. Voices whose card says non-commercial, names a custom license, says only "See URL", or says nothing are not listed.

## Available voices

| Voice | Language | Quality | License | Download | Training data |
|---|---|---|---|---|---|
| `en_US-bryce-medium` | en_US | medium | Public-Domain | 64 MB | not stated |
| `en_US-joe-medium` | en_US | medium | CC0-1.0 | 63 MB | [source](https://github.com/OHF-Voice/voice-datasets) |
| `en_US-john-medium` | en_US | medium | Public-Domain | 64 MB | [source](https://librivox.org) |
| `en_US-kathleen-low` | en_US | low | CC0-1.0 | 63 MB | [source](https://github.com/rhasspy/dataset-voice-kathleen) |
| `en_US-kristin-medium` | en_US | medium | Public-Domain | 64 MB | [source](https://librivox.org) |
| `en_US-ljspeech-high` | en_US | high | Public-Domain | 114 MB | [source](https://keithito.com/LJ-Speech-Dataset/) |
| `en_US-ljspeech-medium` | en_US | medium | Public-Domain | 64 MB | [source](https://keithito.com/LJ-Speech-Dataset/) |
| `en_US-mike-medium` | en_US | medium | CC0-1.0 | 63 MB | [source](https://github.com/OHF-Voice/voice-datasets) |
| `en_US-norman-medium` | en_US | medium | Public-Domain | 64 MB | [source](https://librivox.org) |
| `en_US-reza_ibrahim-medium` | en_US | medium | CC0-1.0 | 64 MB | [source](https://huggingface.co/datasets/mah92/Hedayatfar-Persian-Quran-Audio-Dataset) |
| `en_US-sam-medium` | en_US | medium | Apache-2.0 | 63 MB | [source](https://github.com/Sam-Accenture-Non-Binary-Voice/non-binary-voice-files) |

Use a voice by its full id, or by its short name when only one voice has that name (`joe`, but not `ljspeech`, which comes in two qualities).

## Not offered

Every voice of the collection that is not offered, and why. `bx-audio voices --excluded` prints the same list, and `audioVoices( status="excluded" )` returns it.

| Why | Voices |
|---|---|
| License is not open or not clear | 73 |
| Needs the GPL 3 British dictionary | 7 |
| Several speakers | 2 |
| Language not supported yet | 84 |

| Voice | Language | License on the card | Why |
|---|---|---|---|
| `ar_JO-kareem-low` | ar_JO | See URL | The model card only says 'See URL', so the license is not clear |
| `ar_JO-kareem-medium` | ar_JO | See URL | The model card only says 'See URL', so the license is not clear |
| `bg_BG-dimitar-medium` | bg_BG | CC0 | Not supported yet: the Voices engine has no phonemizer for Bulgarian |
| `bn_BD-google-medium` | bn_BD | Attribution-ShareAlike 4.0 International | Not supported yet: the Voices engine has no phonemizer for Bengali |
| `ca_ES-upc_ona-medium` | ca_ES | CC BY-SA 3.0 ES | Not an open license we accept: CC BY-SA 3.0 ES |
| `ca_ES-upc_ona-x_low` | ca_ES | CC BY-SA 3.0 ES | Not an open license we accept: CC BY-SA 3.0 ES |
| `ca_ES-upc_pau-x_low` | ca_ES | CC BY-SA 3.0 ES | Not an open license we accept: CC BY-SA 3.0 ES |
| `cs_CZ-jirka-low` | cs_CZ | CC0 | Not supported yet: the Voices engine has no phonemizer for Czech |
| `cs_CZ-jirka-medium` | cs_CZ | CC0 | Not supported yet: the Voices engine has no phonemizer for Czech |
| `cs_CZ-kasandra-medium` | cs_CZ | Creative Commons Attribution 4.0 International (CC BY 4.0) | Not an open license we accept: Creative Commons Attribution 4.0 International (CC |
| `cy_GB-bu_tts-medium` | cy_GB | CC-BY | Not an open license we accept: CC-BY |
| `cy_GB-gwryw_gogleddol-medium` | cy_GB | See URL | The model card only says 'See URL', so the license is not clear |
| `da_DK-talesyntese-medium` | da_DK | CC0 | Not supported yet: the Voices engine has no phonemizer for Danish |
| `de_DE-eva_k-x_low` | de_DE | See URL | The model card only says 'See URL', so the license is not clear |
| `de_DE-karlsson-low` | de_DE | See URL | The model card only says 'See URL', so the license is not clear |
| `de_DE-kerstin-low` | de_DE | CC0 | Not supported yet: the Voices engine has no phonemizer for German |
| `de_DE-mls-medium` | de_DE | CC-BY 4.0 | Not supported yet: the Voices engine has no phonemizer for German |
| `de_DE-pavoque-low` | de_DE | http://creativecommons.org/licenses/by-nc-sa/4.0/ | Non-commercial license |
| `de_DE-ramona-low` | de_DE | See URL | The model card only says 'See URL', so the license is not clear |
| `de_DE-thorsten-high` | de_DE | CC0 | Not supported yet: the Voices engine has no phonemizer for German |
| `de_DE-thorsten-low` | de_DE | CC0 | Not supported yet: the Voices engine has no phonemizer for German |
| `de_DE-thorsten-medium` | de_DE | CC0 | Not supported yet: the Voices engine has no phonemizer for German |
| `de_DE-thorsten_emotional-medium` | de_DE | CC0 | Not supported yet: the Voices engine has no phonemizer for German |
| `el_GR-joy-medium` | el_GR | CC BY-NC 4.0 | Non-commercial license |
| `el_GR-rapunzelina-low` | el_GR | CC0 | Not supported yet: the Voices engine has no phonemizer for Greek |
| `el_GR-rapunzelina-medium` | el_GR | CC0 | Not supported yet: the Voices engine has no phonemizer for Greek |
| `en_GB-alan-low` | en_GB | See URL | The model card only says 'See URL', so the license is not clear |
| `en_GB-alan-medium` | en_GB | See URL | The model card only says 'See URL', so the license is not clear |
| `en_GB-alba-medium` | en_GB | https://creativecommons.org/licenses/by/4.0/ | Needs the British English dictionary of the Voices engine, which is licensed GPL 3 |
| `en_GB-aru-medium` | en_GB | https://creativecommons.org/licenses/by/4.0/ | Needs the British English dictionary of the Voices engine, which is licensed GPL 3 |
| `en_GB-cori-high` | en_GB | public domain | Needs the British English dictionary of the Voices engine, which is licensed GPL 3 |
| `en_GB-cori-medium` | en_GB | public domain | Needs the British English dictionary of the Voices engine, which is licensed GPL 3 |
| `en_GB-jenny_dioco-medium` | en_GB | See URL | The model card only says 'See URL', so the license is not clear |
| `en_GB-northern_english_male-medium` | en_GB | CC-BY-SA 4.0 International | Needs the British English dictionary of the Voices engine, which is licensed GPL 3 |
| `en_GB-semaine-medium` | en_GB | http://creativecommons.org/licenses/by-nc-sa/4.0/ | Non-commercial license |
| `en_GB-southern_english_female-low` | en_GB | CC-BY-SA 4.0 International | Needs the British English dictionary of the Voices engine, which is licensed GPL 3 |
| `en_GB-vctk-medium` | en_GB | https://creativecommons.org/licenses/by/4.0/ | Needs the British English dictionary of the Voices engine, which is licensed GPL 3 |
| `en_US-amy-low` | en_US | See URL | The model card only says 'See URL', so the license is not clear |
| `en_US-amy-medium` | en_US | See URL | The model card only says 'See URL', so the license is not clear |
| `en_US-arctic-medium` | en_US | See LICENSE file | The model card only says 'See LICENSE file', so the license is not clear |
| `en_US-danny-low` | en_US | See URL | The model card only says 'See URL', so the license is not clear |
| `en_US-hfc_female-medium` | en_US | https://creativecommons.org/licenses/by-nc-sa/4.0/deed.en | Non-commercial license |
| `en_US-hfc_male-medium` | en_US | https://creativecommons.org/licenses/by-nc-sa/4.0/deed.en | Non-commercial license |
| `en_US-kusal-medium` | en_US | See URL | The model card only says 'See URL', so the license is not clear |
| `en_US-l2arctic-medium` | en_US | https://creativecommons.org/licenses/by-nc/4.0/ | Non-commercial license |
| `en_US-lessac-high` | en_US | https://www.cstr.ed.ac.uk/projects/blizzard/2013/lessac_blizzard2013/license.html | Custom license (Blizzard 2013 data), not an open license |
| `en_US-lessac-low` | en_US | https://www.cstr.ed.ac.uk/projects/blizzard/2013/lessac_blizzard2013/license.html | Custom license (Blizzard 2013 data), not an open license |
| `en_US-lessac-medium` | en_US | https://www.cstr.ed.ac.uk/projects/blizzard/2013/lessac_blizzard2013/license.html | Custom license (Blizzard 2013 data), not an open license |
| `en_US-libritts-high` | en_US | CC BY 4.0 | 904 speakers, not supported yet |
| `en_US-libritts_r-medium` | en_US | CC BY 4.0 | 904 speakers, not supported yet |
| `en_US-ryan-high` | en_US | CC BY-NC-SA 4.0 | Non-commercial license |
| `en_US-ryan-low` | en_US | CC BY-NC-SA 4.0 | Non-commercial license |
| `en_US-ryan-medium` | en_US | CC BY-NC-SA 4.0 | Non-commercial license |
| `es_AR-daniela-high` | es_AR | Attribution-ShareAlike 4.0 International | Not supported yet: the Voices engine has no phonemizer for Spanish |
| `es_ES-carlfm-x_low` | es_ES | Public domain | Not supported yet: the Voices engine has no phonemizer for Spanish |
| `es_ES-davefx-medium` | es_ES | CC0 | Not supported yet: the Voices engine has no phonemizer for Spanish |
| `es_ES-mls_10246-low` | es_ES | CC BY 4.0 | Not supported yet: the Voices engine has no phonemizer for Spanish |
| `es_ES-mls_9972-low` | es_ES | CC BY 4.0 | Not supported yet: the Voices engine has no phonemizer for Spanish |
| `es_ES-sharvard-medium` | es_ES | http://creativecommons.org/licenses/by/3.0/ | Not supported yet: the Voices engine has no phonemizer for Spanish |
| `es_MX-ald-medium` | es_MX | http://unlicense.org | Not supported yet: the Voices engine has no phonemizer for Spanish |
| `es_MX-ald-x_low` | es_MX | none | The model card states no license |
| `es_MX-claude-high` | es_MX | apache-2.0 | Not supported yet: the Voices engine has no phonemizer for Spanish |
| `et_EE-news-medium` | et_EE | CC-BY (META-SHARE "Available - Unrestricted Use") | Not an open license we accept: CC-BY (META-SHARE "Available - Unrestricted Use") |
| `eu_ES-antton-medium` | eu_ES | Creative Commons Attribution 4.0 | Not an open license we accept: Creative Commons Attribution 4.0 |
| `eu_ES-maider-medium` | eu_ES | Creative Commons Attribution 4.0 | Not an open license we accept: Creative Commons Attribution 4.0 |
| `fa_IR-amir-medium` | fa_IR | CC0 | Not supported yet: the Voices engine has no phonemizer for Farsi |
| `fa_IR-ganji-medium` | fa_IR | CC0 | Not supported yet: the Voices engine has no phonemizer for Farsi |
| `fa_IR-ganji_adabi-medium` | fa_IR | CC0 | Not supported yet: the Voices engine has no phonemizer for Farsi |
| `fa_IR-gyro-medium` | fa_IR | See URL | The model card only says 'See URL', so the license is not clear |
| `fa_IR-reza_ibrahim-medium` | fa_IR | CC0 | Not supported yet: the Voices engine has no phonemizer for Farsi |
| `fi_FI-harri-low` | fi_FI | CC0 | Not supported yet: the Voices engine has no phonemizer for Finnish |
| `fi_FI-harri-medium` | fi_FI | CC0 | Not supported yet: the Voices engine has no phonemizer for Finnish |
| `fr_FR-gilles-low` | fr_FR | CC0 | Not supported yet: the Voices engine has no phonemizer for French |
| `fr_FR-mls-medium` | fr_FR | CC-BY 4.0 | Not supported yet: the Voices engine has no phonemizer for French |
| `fr_FR-mls_1840-low` | fr_FR | CC BY 4.0 | Not supported yet: the Voices engine has no phonemizer for French |
| `fr_FR-siwis-low` | fr_FR | CC-BY 4.0 | Not supported yet: the Voices engine has no phonemizer for French |
| `fr_FR-siwis-medium` | fr_FR | CC-BY 4.0 | Not supported yet: the Voices engine has no phonemizer for French |
| `fr_FR-tom-medium` | fr_FR | AGPLv3 | Not an open license we accept: AGPLv3 |
| `fr_FR-upmc-medium` | fr_FR | http://creativecommons.org/licenses/by-sa/4.0/ | Not supported yet: the Voices engine has no phonemizer for French |
| `he_IL-saspeech-medium` | he_IL | See dataset URL | The model card only says 'See dataset URL', so the license is not clear |
| `hi_IN-pratham-medium` | hi_IN | http://creativecommons.org/licenses/by-nc-sa/4.0/ | Non-commercial license |
| `hi_IN-priyamvada-medium` | hi_IN | http://creativecommons.org/licenses/by-nc-sa/4.0/ | Non-commercial license |
| `hi_IN-rohan-medium` | hi_IN | https://www.iitm.ac.in/donlab/indictts/downloads/license.pdf | Not an open license we accept: https://www.iitm.ac.in/donlab/indictts/downloads/l |
| `hu_HU-anna-medium` | hu_HU | CC0 | Not supported yet: the Voices engine has no phonemizer for Hungarian |
| `hu_HU-berta-medium` | hu_HU | CC0 | Not supported yet: the Voices engine has no phonemizer for Hungarian |
| `hu_HU-imre-medium` | hu_HU | CC0 | Not supported yet: the Voices engine has no phonemizer for Hungarian |
| `hy_AM-gor-medium` | hy_AM | GPL 2.0 | Not an open license we accept: GPL 2.0 |
| `id_ID-news_tts-medium` | id_ID | See URL | The model card only says 'See URL', so the license is not clear |
| `is_IS-bui-medium` | is_IS | See URL | The model card only says 'See URL', so the license is not clear |
| `is_IS-salka-medium` | is_IS | See URL | The model card only says 'See URL', so the license is not clear |
| `is_IS-steinn-medium` | is_IS | See URL | The model card only says 'See URL', so the license is not clear |
| `is_IS-ugla-medium` | is_IS | See URL | The model card only says 'See URL', so the license is not clear |
| `it_IT-paola-medium` | it_IT | See URL | The model card only says 'See URL', so the license is not clear |
| `it_IT-riccardo-x_low` | it_IT | See URL | The model card only says 'See URL', so the license is not clear |
| `it_IT-serena-high` | it_IT | CC-BY-4.0 | Not supported yet: the Voices engine has no phonemizer for Italian |
| `it_IT-serena-medium` | it_IT | CC-BY-4.0 | Not supported yet: the Voices engine has no phonemizer for Italian |
| `ja_JP-hi_fi_captain-medium` | ja_JP | https://creativecommons.org/licenses/by-nc-sa/4.0/deed.en | Non-commercial license |
| `ka_GE-natia-medium` | ka_GE | See LICENSE file | The model card only says 'See LICENSE file', so the license is not clear |
| `kk_KZ-iseke-x_low` | kk_KZ | CC-BY-4.0 | Not supported yet: the Voices engine has no phonemizer for Kazakh |
| `kk_KZ-issai-high` | kk_KZ | CC-BY-4.0 | Not supported yet: the Voices engine has no phonemizer for Kazakh |
| `kk_KZ-raya-x_low` | kk_KZ | CC-BY-4.0 | Not supported yet: the Voices engine has no phonemizer for Kazakh |
| `ko_KR-kss-medium` | ko_KR | https://creativecommons.org/licenses/by-nc-sa/4.0/ | Non-commercial license |
| `ku_TR-berfin_renas-medium` | ku_TR | ** [CC BY-NC 4.0](https://creativecommons.org/licenses/by-nc/4.0/) — free for non-commercial use with attribution; commercial use restricted due to [Blizzard 2013 base model](https://www.cstr.ed.ac.uk/projects/blizzard/2013/lessac_blizzard2013/license.html) | Non-commercial license |
| `lb_LU-marylux-medium` | lb_LU | http://creativecommons.org/licenses/by-nc-sa/4.0/ | Non-commercial license |
| `lt_LT-reginute1-medium` | lt_LT | CC-BY-4.0 | Not supported yet: the Voices engine has no phonemizer for Lithuanian |
| `lv_LV-aivars-medium` | lv_LV | CC0 1.0 Universal | Not supported yet: the Voices engine has no phonemizer for Latvian |
| `ml_IN-arjun-medium` | ml_IN | See URL | The model card only says 'See URL', so the license is not clear |
| `ml_IN-meera-medium` | ml_IN | See URL | The model card only says 'See URL', so the license is not clear |
| `mr_IN-google-medium` | mr_IN | Attribution-ShareAlike 4.0 International | Not supported yet: the Voices engine has no phonemizer for Marathi |
| `ne_NP-chitwan-medium` | ne_NP | CC0 | Not supported yet: the Voices engine has no phonemizer for Nepali |
| `ne_NP-google-medium` | ne_NP | CC-BY-SA-4.0 International | Not supported yet: the Voices engine has no phonemizer for Nepali |
| `ne_NP-google-x_low` | ne_NP | CC-BY-SA-4.0 International | Not supported yet: the Voices engine has no phonemizer for Nepali |
| `nl_BE-nathalie-medium` | nl_BE | CC0 | Not supported yet: the Voices engine has no phonemizer for Dutch |
| `nl_BE-nathalie-x_low` | nl_BE | CC0 | Not supported yet: the Voices engine has no phonemizer for Dutch |
| `nl_BE-rdh-medium` | nl_BE | CC0 1.0 Universal | Not supported yet: the Voices engine has no phonemizer for Dutch |
| `nl_BE-rdh-x_low` | nl_BE | CC0 1.0 Universal | Not supported yet: the Voices engine has no phonemizer for Dutch |
| `nl_NL-alex-medium` | nl_NL | CC0 | Not supported yet: the Voices engine has no phonemizer for Dutch |
| `nl_NL-mls-medium` | nl_NL | CC-BY 4.0 | Not supported yet: the Voices engine has no phonemizer for Dutch |
| `nl_NL-mls_5809-low` | nl_NL | CC BY 4.0 | Not supported yet: the Voices engine has no phonemizer for Dutch |
| `nl_NL-mls_7432-low` | nl_NL | CC BY 4.0 | Not supported yet: the Voices engine has no phonemizer for Dutch |
| `nl_NL-pim-medium` | nl_NL | CC0 | Not supported yet: the Voices engine has no phonemizer for Dutch |
| `nl_NL-ronnie-medium` | nl_NL | CC0 | Not supported yet: the Voices engine has no phonemizer for Dutch |
| `no_NO-nvcc-medium` | no_NO | CC0 | Not supported yet: the Voices engine has no phonemizer for Norwegian |
| `no_NO-talesyntese-medium` | no_NO | CC0 | Not supported yet: the Voices engine has no phonemizer for Norwegian |
| `pl_PL-bass-high` | pl_PL | none | The model card states no license |
| `pl_PL-darkman-medium` | pl_PL | CC0 | Not supported yet: the Voices engine has no phonemizer for Polish |
| `pl_PL-gosia-medium` | pl_PL | CC0 | Not supported yet: the Voices engine has no phonemizer for Polish |
| `pl_PL-mc_speech-medium` | pl_PL | CC0 | Not supported yet: the Voices engine has no phonemizer for Polish |
| `pl_PL-mls_6892-low` | pl_PL | CC BY 4.0 | Not supported yet: the Voices engine has no phonemizer for Polish |
| `pt_BR-cadu-medium` | pt_BR | CC0 | Not supported yet: the Voices engine has no phonemizer for Portuguese |
| `pt_BR-edresson-low` | pt_BR | CC BY 4.0 | Not supported yet: the Voices engine has no phonemizer for Portuguese |
| `pt_BR-faber-medium` | pt_BR | CC0 | Not supported yet: the Voices engine has no phonemizer for Portuguese |
| `pt_BR-jeff-medium` | pt_BR | CC0 | Not supported yet: the Voices engine has no phonemizer for Portuguese |
| `pt_PT-tugão-medium` | pt_PT | CC0 | Not supported yet: the Voices engine has no phonemizer for Portuguese |
| `ro_RO-mihai-medium` | ro_RO | CC0 | Not supported yet: the Voices engine has no phonemizer for Romanian |
| `ru_RU-denis-medium` | ru_RU | CC0 | Not supported yet: the Voices engine has no phonemizer for Russian |
| `ru_RU-dmitri-medium` | ru_RU | CC0 | Not supported yet: the Voices engine has no phonemizer for Russian |
| `ru_RU-irina-medium` | ru_RU | Unknown | Not an open license we accept: Unknown |
| `ru_RU-ruslan-medium` | ru_RU | https://creativecommons.org/licenses/by-nc-sa/4.0/ | Non-commercial license |
| `sk_SK-lili-medium` | sk_SK | CC0 | Not supported yet: the Voices engine has no phonemizer for Slovak |
| `sl_SI-artur-medium` | sl_SI | https://creativecommons.org/licenses/by/4.0/ | Not supported yet: the Voices engine has no phonemizer for Slovenian |
| `sq_AL-edon-medium` | sq_AL | CC0 | Not supported yet: the Voices engine has no phonemizer for Albanian |
| `sr_RS-serbski_institut-medium` | sr_RS | https://creativecommons.org/licenses/by-nc-sa/4.0/ | Non-commercial license |
| `sv_SE-alma-medium` | sv_SE | none | The model card states no license |
| `sv_SE-lisa-medium` | sv_SE | none | The model card states no license |
| `sv_SE-nst-medium` | sv_SE | CC0 | Not supported yet: the Voices engine has no phonemizer for Swedish |
| `sw_CD-lanfrica-medium` | sw_CD | See URL | The model card only says 'See URL', so the license is not clear |
| `te_IN-maya-medium` | te_IN | www.iitm.ac.in/donlab/indictts/downloads/license.pdf | Not an open license we accept: www.iitm.ac.in/donlab/indictts/downloads/license.p |
| `te_IN-padmavathi-medium` | te_IN | CC-BY-4.0 | Not supported yet: the Voices engine has no phonemizer for Telugu |
| `te_IN-venkatesh-medium` | te_IN | CC-BY-4.0 | Not supported yet: the Voices engine has no phonemizer for Telugu |
| `th_TH-tsync2-medium` | th_TH | CC BY-NC-SA 3.0 (**non-commercial**) | Non-commercial license |
| `tr_TR-dfki-medium` | tr_TR | https://creativecommons.org/licenses/by-nc-sa/4.0/ | Non-commercial license |
| `uk_UA-lada-x_low` | uk_UA | Apache 2.0 | Not supported yet: the Voices engine has no phonemizer for Ukrainian |
| `uk_UA-mykyta-high` | uk_UA | Apache 2.0 | Not supported yet: the Voices engine has no phonemizer for Ukrainian |
| `uk_UA-oleksa-high` | uk_UA | Apache 2.0 | Not supported yet: the Voices engine has no phonemizer for Ukrainian |
| `uk_UA-tetiana-high` | uk_UA | Apache 2.0 | Not supported yet: the Voices engine has no phonemizer for Ukrainian |
| `uk_UA-ukrainian_tts-medium` | uk_UA | CC0 | Not supported yet: the Voices engine has no phonemizer for Ukrainian |
| `ur_PK-aegis_female-medium` | ur_PK | none | The model card states no license |
| `ur_PK-fasih-medium` | ur_PK | none | The model card states no license |
| `vi_VN-25hours_single-low` | vi_VN | Unknown | Not an open license we accept: Unknown |
| `vi_VN-vais1000-medium` | vi_VN | https://creativecommons.org/licenses/by/4.0/ | Not supported yet: the Voices engine has no phonemizer for Vietnamese |
| `vi_VN-vivos-x_low` | vi_VN | CC BY-NC-SA 4.0 | Non-commercial license |
| `zh_CN-chaowen-medium` | zh_CN | CC0 | Not supported yet: the Voices engine has no phonemizer for Chinese |
| `zh_CN-huayan-medium` | zh_CN | Unknown | Not an open license we accept: Unknown |
| `zh_CN-huayan-x_low` | zh_CN | Unknown | Not an open license we accept: Unknown |
| `zh_CN-xiao_ya-medium` | zh_CN | Non-commercial use (see https://www.data-baker.com/data/index/TNtts/) | Non-commercial license |

British English voices (`en_GB`) need the `en_uk` dictionary of the Voices engine, which is licensed GPL 3, so they are not offered. Other languages need a phonemizer the engine does not have yet.

## Updating this list

This page and `voices.json` are generated. Run `boxlang tools/update-voices.bxs` in the module repository to read the current voices and their model cards again, and review the difference.
