# Sound sources

Every sound shipped by this mod is cut from a recording taken from freesound.org under the
Creative Commons Zero (CC0 1.0) public-domain dedication, which permits use, modification and
redistribution without attribution. The recordists are credited here anyway, because they deserve
it. The files in this directory are the recordings as downloaded (Freesound's high-quality Vorbis
previews); `devtools/art/build.py sounds` cuts and normalizes them into
`src/main/resources/assets/aberrantmobs/sounds/` (see `SOUNDS` there for the edits). Each page's
licence was read on 2026-09-14 before the file was taken.

| File | Title | Recordist | Freesound page | License |
|---|---|---|---|---|
| `266014-skitter-fast-crawling-bug.ogg` | Fast Crawling Bug | dasrealized | https://freesound.org/s/266014/ | CC0 1.0 |
| `651488-skitter-alien-bug-crawl.ogg` | DCA Alien Bug Crawl.aif | darcyadam | https://freesound.org/s/651488/ | CC0 1.0 |
| `321488-dig-scraping-stone.ogg` | Scraping Stone | dslrguide | https://freesound.org/s/321488/ | CC0 1.0 |
| `667284-dig-stone-scrape.ogg` | Stone Scrape | alegemaate | https://freesound.org/s/667284/ | CC0 1.0 |
| `467700-click-insectoid-monster.ogg` | insectoid monster clicking | LucasDuff | https://freesound.org/s/467700/ | CC0 1.0 |
| `553374-hiss-snake.ogg` | Snake Hiss | xoiziox | https://freesound.org/s/553374/ | CC0 1.0 |
| `807383-screech-monster-a.ogg` | Monster screech | Elth2010 | https://freesound.org/s/807383/ | CC0 1.0 |
| `443328-snap-quick-clack.ogg` | Quick Clack | effectator | https://freesound.org/s/443328/ | CC0 1.0 |
| `355052-crunch-bone.ogg` | Bone Crunch.wav | GreatNate98 | https://freesound.org/s/355052/ | CC0 1.0 |
| `150479-egg-crack.ogg` | Egg Crack | davdud101 | https://freesound.org/s/150479/ | CC0 1.0 |
| `734841-dying-beast.ogg` | dyingBeast | QuantumFellow | https://freesound.org/s/734841/ | CC0 1.0 |
| `844096-heavy-breathing-beast.ogg` | heavy_breathing_beast | perspektywa_tn | https://freesound.org/s/844096/ | CC0 1.0 |

| Shipped sound | Built from |
|---|---|
| `skitter1` | the fast crawling bug, 0.1 to 1.3 s |
| `skitter2` | the alien bug crawl, 0.5 to 1.7 s |
| `dig_loud` | the scraping stone, its first 1.2 s |
| `dig_quiet` | the stone scrape, whole, at half gain |
| `click` | the insectoid clicking, 0.25 to 0.9 s: two clicks |
| `hiss` | the snake hiss, 0.45 to 1.8 s |
| `screech` | the monster screech, its first 1.6 s |
| `grab` | the quick clack, whole: the pincers meeting |
| `bite` | the bone crunch, 0.05 to 1.5 s |
| `crack` | the egg crack, whole: plating giving |
| `death` | the dying beast, whole |
| `chitter` | taure’s chitter, 10.8 to 14.8 s, an alternate for the click event |
| `breath` | the low Geofón rumble, 0.6 to 4.8 s |

Additional sources verified 2026-09-16:

| File | Title | Recordist | Freesound page | License |
|---|---|---|---|---|
| `387068-chitter.ogg` | chitter.wav | taure | https://freesound.org/people/taure/sounds/387068/ | CC0 1.0 |
| `744788-low-rumble.ogg` | Monster breathing snoring growling | lori.mortimer | https://freesound.org/people/lori.mortimer/sounds/744788/ | CC0 1.0 |

The low rumble records vibrations from a rowing machine with a LOM Geofón.
Both additions use endpoint fades and peak normalization only. The old breath
source is retained for reproducibility of earlier versions. Runtime keeps D-0012’s
rare, irregular idle timing. Listening suitability is pending Rusty’s review;
signal measurements and successful decoding do not establish scariness.
