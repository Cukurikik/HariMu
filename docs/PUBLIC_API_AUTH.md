# Public API AUTH — Katalog API Publik yang Memerlukan Autentikasi

Dokumen ini mengumpulkan **semua API publik yang memerlukan autentikasi** (auth ≠ none) dari dua direktori: 
[publicapis.dev](https://publicapis.dev/) dan [publicapi.dev](https://publicapi.dev/).
Data mesin yang dapat dibaca ada di `docs/DATA_PUBLIC_API_AUTH.json` (subset dari `docs/DATA_PUBLIC_API.json`).

**Dibuat:** 2026-11-26  
**Total API ber-autentikasi:** 2100  
**publicapis.dev:** 1036 · **publicapi.dev:** 1064

## Ringkasan per jenis autentikasi

| Jenis Auth | Jumlah | Keterangan |
|---|---|---|
| API Key | 1783 | Kunci API (header `Authorization`/`apikey`/`X-Mashape-Key` atau query `?key=`) |
| OAuth | 311 | Alur OAuth 2.0 (token akses) atau OAuth 1.0 |
| Bearer Token | 1 | Token pembawa pada header `Authorization: Bearer …` |
| API Key + Bearer | 3 | Menerima kunci API atau token pembawa |
| Header (User-Agent) | 2 | Mewajibkan header khusus (mis. `User-Agent`), bukan kunci rahasia |
| **Total** | **2100** | |

## Metode

- **publicapis.dev**: setiap halaman resource (1705 URL dari `sitemap.xml`) diambil dan field `public_api_fields.auth` dibaca dari payload halaman. Auth kosong = tanpa autentikasi.
- **publicapi.dev**: nilai `auth` diambil dari data listing direktori (snapshot katalog 2026-11-25; apiKey / OAuth / none / dst).
- Normalisasi: `apiKey`/`apikey`/`API Key`/`Yes`/`X-Mashape-Key` → **API Key**; `OAuth` → **OAuth**; `Bearer Token` → **Bearer Token**; kombinasi → **API Key + Bearer**; `User-Agent` → **Header (User-Agent)**; `none`/kosong → tanpa auth (dikecualikan).
- Status/HTTPS/CORS adalah kondisi saat pengumpulan (2026-11-26) dan dapat berubah.

## Daftar API ber-autentikasi

### API Key (1783)

| Nama | Kategori | Situs | CORS | Tautan |
|---|---|---|---|---|
| 1ClickImpact (AI Energy) | AI | publicapi.dev | yes | https://publicapi.dev/1click-impact-ai-energy-api |
| Advanced Multilanguage AI Translator (RapidAPI) | AI | publicapis.dev | yes | https://publicapis.dev/resource/advanced-multilanguage-ai-translator-rapidapi-/labopp5q |
| Advanced Multilanguage AI Translator (RapidAPI) | AI | publicapi.dev | yes | https://publicapi.dev/advanced-multilanguage-ai-translator-rapid-api |
| Agentik | AI | publicapi.dev | yes | https://publicapi.dev/agentik-api |
| AI For Thai | AI | publicapis.dev | yes | https://publicapis.dev/resource/ai-for-thai/lonltt4a |
| AI For Thai | AI | publicapi.dev | yes | https://publicapi.dev/ai-for-thai-api |
| AI Learning Engine | AI | publicapis.dev | yes | https://publicapis.dev/resource/ai-learning-engine/z21y5mfb |
| AI Learning Engine | AI | publicapi.dev | yes | https://publicapi.dev/ai-learning-engine-api |
| AI/ML | AI | publicapis.dev | yes | https://publicapis.dev/resource/ai-ml/y0h7pqt2 |
| AI/ML | AI | publicapi.dev | yes | https://publicapi.dev/ai-ml-api |
| API Route | AI | publicapi.dev | yes | https://publicapi.dev/api-route-api |
| APIClaw | AI | publicapi.dev | yes | https://publicapi.dev/api-claw-api |
| APIModels | AI | publicapi.dev | yes | https://publicapi.dev/api-models-api |
| Asyntai | AI | publicapi.dev | yes | https://publicapi.dev/asyntai-api |
| AtmoRouter | AI | publicapi.dev | no | https://publicapi.dev/atmo-router-api |
| Azurade | AI | publicapi.dev | yes | https://publicapi.dev/azurade-api |
| Chatwith | AI | publicapis.dev | yes | https://publicapis.dev/resource/chatwith/c7zff4jf |
| Chatwith | AI | publicapi.dev | yes | https://publicapi.dev/chatwith-api |
| Cheaper Inference | AI | publicapi.dev | no | https://publicapi.dev/cheaper-inference-api |
| CitedSpy | AI | publicapis.dev | unknown | https://publicapis.dev/resource/citedspy/ev818dvh |
| CitedSpy | AI | publicapi.dev | no | https://publicapi.dev/cited-spy-api |
| Cloudmersive | AI | publicapis.dev | yes | https://publicapis.dev/resource/cloudmersive/jmtoc2zj |
| Cloudmersive | AI | publicapi.dev | yes | https://publicapi.dev/cloudmersive-api |
| cogDepot | AI | publicapis.dev | no | https://publicapis.dev/resource/cogdepot/i0pqjmrm |
| cogDepot | AI | publicapi.dev | no | https://publicapi.dev/cog-depot-api |
| Cohere | AI | publicapis.dev | yes | https://publicapis.dev/resource/cohere/yc1dcujx |
| Cohere | AI | publicapi.dev | yes | https://publicapi.dev/cohere-api |
| CustomGPT.ai | AI | publicapis.dev | yes | https://publicapis.dev/resource/customgpt-ai/mhkybqkz |
| CustomGPT.ai | AI | publicapi.dev | yes | https://publicapi.dev/custom-gpt-ai-api |
| Dialogflow | AI | publicapis.dev | unknown | https://publicapis.dev/resource/dialogflow/6lge0e2j |
| Dialogflow | AI | publicapi.dev | no | https://publicapi.dev/dialogflow-api |
| Eden | AI | publicapis.dev | yes | https://publicapis.dev/resource/eden/u2g3f3oq |
| Eden | AI | publicapi.dev | yes | https://publicapi.dev/eden-api |
| Gandr | AI | publicapis.dev | yes | https://publicapis.dev/resource/gandr/lr0c6pn6 |
| Gandr | AI | publicapi.dev | yes | https://publicapi.dev/gandr-api |
| Geekflare | AI | publicapis.dev | yes | https://publicapis.dev/resource/geekflare/o16dprnk |
| Geekflare | AI | publicapi.dev | yes | https://publicapi.dev/geekflare-api |
| Gladia | AI | publicapis.dev | no | https://publicapis.dev/resource/gladia/cdw1wsbg |
| Gladia | AI | publicapi.dev | no | https://publicapi.dev/gladia-api |
| HarnessRouter | AI | publicapis.dev | unknown | https://publicapis.dev/resource/harnessrouter/5uzsqsxn |
| HarnessRouter | AI | publicapi.dev | no | https://publicapi.dev/harness-router-api |
| HiAPI | AI | publicapi.dev | no | https://publicapi.dev/hi-api |
| HOL Registry Broker | AI | publicapis.dev | yes | https://publicapis.dev/resource/hol-registry-broker/dvjb12l3 |
| HOL Registry Broker | AI | publicapi.dev | yes | https://publicapi.dev/hol-registry-broker-api |
| Imagga | AI | publicapis.dev | unknown | https://publicapis.dev/resource/imagga/49wjpp7y |
| Imagga | AI | publicapi.dev | no | https://publicapi.dev/imagga-api |
| Inferdo | AI | publicapis.dev | unknown | https://publicapis.dev/resource/inferdo/7ukuau2q |
| Inferdo | AI | publicapi.dev | no | https://publicapi.dev/inferdo-api |
| Irisnet | AI | publicapis.dev | yes | https://publicapis.dev/resource/irisnet/0zxk1r4x |
| Irisnet | AI | publicapi.dev | yes | https://publicapi.dev/irisnet-api |
| ISMS Copilot | AI | publicapi.dev | no | https://publicapi.dev/isms-copilot-api |
| Keen IO | AI | publicapis.dev | unknown | https://publicapis.dev/resource/keen-io/fnexxuoh |
| Keen IO | AI | publicapi.dev | no | https://publicapi.dev/keen-io-api |
| Machinetutors | AI | publicapis.dev | yes | https://publicapis.dev/resource/machinetutors/4tv739fk |
| Machinetutors | AI | publicapi.dev | yes | https://publicapi.dev/machinetutors-api |
| Magic Hour | AI | publicapi.dev | yes | https://publicapi.dev/magic-hour-api |
| MessengerX.io | AI | publicapis.dev | yes | https://publicapis.dev/resource/messengerx-io/csyv5fb7 |
| MessengerX.io | AI | publicapi.dev | yes | https://publicapi.dev/messenger-x-io-api |
| NLP Cloud | AI | publicapis.dev | unknown | https://publicapis.dev/resource/nlp-cloud/ji65011g |
| NLP Cloud | AI | publicapi.dev | no | https://publicapi.dev/nlp-cloud-api |
| OpenAI | AI | publicapis.dev | yes | https://publicapis.dev/resource/openai/1hr3h7s3 |
| OpenAI | AI | publicapi.dev | yes | https://publicapi.dev/open-ai-api |
| Perspective | AI | publicapis.dev | unknown | https://publicapis.dev/resource/perspective/sez8wiez |
| Perspective | AI | publicapi.dev | no | https://publicapi.dev/perspective-api |
| Roboflow Universe | AI | publicapis.dev | yes | https://publicapis.dev/resource/roboflow-universe/2e5fbi9c |
| Roboflow Universe | AI | publicapi.dev | yes | https://publicapi.dev/roboflow-universe-api |
| RouterBase API | AI | publicapis.dev | unknown | https://publicapis.dev/resource/routerbase-api/yoaplyxk |
| SkyBiometry | AI | publicapis.dev | unknown | https://publicapis.dev/resource/skybiometry/vf5e5a4m |
| SkyBiometry | AI | publicapi.dev | no | https://publicapi.dev/sky-biometry-api |
| Spam Hunter | AI | publicapis.dev | yes | https://publicapis.dev/resource/spam-hunter/51jykgm2 |
| Spam Hunter | AI | publicapi.dev | yes | https://publicapi.dev/spam-hunter-api |
| Summarize Text with AI | AI | publicapis.dev | yes | https://publicapis.dev/resource/summarize-text-with-ai/gpgtopaz |
| Summarize Text with AI | AI | publicapi.dev | yes | https://publicapi.dev/summarize-text-with-ai-api |
| TranscriptFetch | AI | publicapis.dev | yes | https://publicapis.dev/resource/transcriptfetch/bytmq702 |
| TranscriptFetch | AI | publicapi.dev | yes | https://publicapi.dev/transcript-fetch-api |
| UnoRouter | AI | publicapis.dev | yes | https://publicapis.dev/resource/unorouter/94xrp7wk |
| UnoRouter | AI | publicapi.dev | yes | https://publicapi.dev/uno-router-api |
| Unplugg | AI | publicapis.dev | unknown | https://publicapis.dev/resource/unplugg/w7n3x19q |
| Unplugg | AI | publicapi.dev | no | https://publicapi.dev/unplugg-api |
| WaveSpeedAI | AI | publicapi.dev | yes | https://publicapi.dev/wave-speed-ai-api |
| WolframAlpha | AI | publicapis.dev | unknown | https://publicapis.dev/resource/wolframalpha/afve8ejg |
| WolframAlpha | AI | publicapi.dev | no | https://publicapi.dev/wolfram-alpha-api |
| XiuRouter | AI | publicapi.dev | yes | https://publicapi.dev/xiu-router-api |
| SniffCat - Public APIs | All | publicapis.dev | yes | https://publicapis.dev/resource/sniffcat/xfk4kedr |
| AdoptAPet | Animals | publicapi.dev | yes | https://publicapi.dev/adopt-a-pet-api |
| Cats | Animals | publicapis.dev | no | https://publicapis.dev/resource/cats/6mqznsav |
| Cats | Animals | publicapi.dev | no | https://publicapi.dev/cats-api |
| eBird | Animals | publicapis.dev | no | https://publicapis.dev/resource/ebird/mwkev1fn |
| eBird | Animals | publicapi.dev | no | https://publicapi.dev/e-bird-api |
| IUCN | Animals | publicapi.dev | no | https://publicapi.dev/iucn-api |
| Petfinder | Animals | publicapis.dev | yes | https://publicapis.dev/resource/petfinder/cmw5ypur |
| Petfinder | Animals | publicapi.dev | yes | https://publicapi.dev/petfinder-api |
| The Dog | Animals | publicapis.dev | no | https://publicapis.dev/resource/the-dog/9sh11wak |
| The Dog | Animals | publicapi.dev | no | https://publicapi.dev/the-dog-api |
| AniDB | Anime | publicapis.dev | yes | https://publicapis.dev/resource/anidb/90ak5ahj |
| AniDB | Anime | publicapi.dev | yes | https://publicapi.dev/ani-db-api |
| Danbooru Anime | Anime | publicapis.dev | yes | https://publicapis.dev/resource/danbooru-anime/86eamxjs |
| Danbooru Anime | Anime | publicapi.dev | yes | https://publicapi.dev/danbooru-anime-api |
| MangaDex | Anime | publicapis.dev | unknown | https://publicapis.dev/resource/mangadex/vriz5jw2 |
| MangaDex | Anime | publicapi.dev | no | https://publicapi.dev/manga-dex-api |
| Mangapi | Anime | publicapis.dev | unknown | https://publicapis.dev/resource/mangapi/k1f0smdt |
| Mangapi | Anime | publicapi.dev | no | https://publicapi.dev/mangapi-api |
| Waifu.it | Anime | publicapi.dev | yes | https://publicapi.dev/waifu-it-api |
| AbuseIPDB | Anti-Malware | publicapis.dev | unknown | https://publicapis.dev/resource/abuseipdb/1f8amrr3 |
| AbuseIPDB | Anti-Malware | publicapi.dev | no | https://publicapi.dev/abuse-ipdb-api |
| AlienVault Open Threat Exchange (OTX) | Anti-Malware | publicapis.dev | unknown | https://publicapis.dev/resource/alienvault-open-threat-exchange-otx-/c8qsuowa |
| AlienVault Open Threat Exchange (OTX) | Anti-Malware | publicapi.dev | no | https://publicapi.dev/alien-vault-open-threat-exchange-otx-api |
| CAPEsandbox | Anti-Malware | publicapis.dev | unknown | https://publicapis.dev/resource/capesandbox/xemseymf |
| CAPEsandbox | Anti-Malware | publicapi.dev | no | https://publicapi.dev/cap-esandbox-api |
| Dymo API | Anti-Malware | publicapis.dev | yes | https://publicapis.dev/resource/dymo-api/5e3k8ajw |
| Dymo API | Anti-Malware | publicapi.dev | yes | https://publicapi.dev/dymo-api |
| Google Safe Browsing | Anti-Malware | publicapis.dev | unknown | https://publicapis.dev/resource/google-safe-browsing/422ikaif |
| Google Safe Browsing | Anti-Malware | publicapi.dev | no | https://publicapi.dev/google-safe-browsing-api |
| MalDatabase | Anti-Malware | publicapis.dev | unknown | https://publicapis.dev/resource/maldatabase/13axewlr |
| MalDatabase | Anti-Malware | publicapi.dev | no | https://publicapi.dev/mal-database-api |
| MalShare | Anti-Malware | publicapis.dev | no | https://publicapis.dev/resource/malshare/c5a8e4dy |
| MalShare | Anti-Malware | publicapi.dev | no | https://publicapi.dev/mal-share-api |
| MalwareBazaar | Anti-Malware | publicapis.dev | unknown | https://publicapis.dev/resource/malwarebazaar/n305lxbo |
| MalwareBazaar | Anti-Malware | publicapi.dev | no | https://publicapi.dev/malware-bazaar-api |
| Metacert | Anti-Malware | publicapi.dev | no | https://publicapi.dev/metacert-api |
| NoPhishy | Anti-Malware | publicapi.dev | yes | https://publicapi.dev/no-phishy-api |
| phish.directory | Anti-Malware | publicapi.dev | no | https://publicapi.dev/phish-directory-api |
| Phisherman | Anti-Malware | publicapi.dev | no | https://publicapi.dev/phisherman-api |
| Scanii | Anti-Malware | publicapis.dev | yes | https://publicapis.dev/resource/scanii/6rg5pj49 |
| Scanii | Anti-Malware | publicapi.dev | yes | https://publicapi.dev/scanii-api |
| SniffCat | Anti-Malware | publicapis.dev | yes | https://publicapis.dev/resource/sniffcat/rcqqewls |
| SniffCat | Anti-Malware | publicapi.dev | yes | https://publicapi.dev/sniff-cat-api |
| URLScan.io | Anti-Malware | publicapis.dev | unknown | https://publicapis.dev/resource/urlscan-io/opytatrf |
| URLScan.io | Anti-Malware | publicapi.dev | no | https://publicapi.dev/url-scan-io-api |
| Verisys Antivirus API | Anti-Malware | publicapis.dev | yes | https://publicapis.dev/resource/verisys-antivirus-api/fi3sw7b5 |
| Verisys Antivirus API | Anti-Malware | publicapi.dev | yes | https://publicapi.dev/verisys-antivirus-api |
| VirusTotal | Anti-Malware | publicapis.dev | unknown | https://publicapis.dev/resource/virustotal/55ql4aad |
| VirusTotal | Anti-Malware | publicapi.dev | no | https://publicapi.dev/virus-total-api |
| Web of Trust | Anti-Malware | publicapis.dev | unknown | https://publicapis.dev/resource/web-of-trust/fk9a85qh |
| Web of Trust | Anti-Malware | publicapi.dev | no | https://publicapi.dev/web-of-trust-api |
| Améthyste | Art & Design | publicapi.dev | no | https://publicapi.dev/amethyste-api |
| Art Search | Art & Design | publicapis.dev | yes | https://publicapis.dev/resource/art-search/7q1ul1lg |
| Art Search | Art & Design | publicapi.dev | yes | https://publicapi.dev/art-search-api |
| Cooper Hewitt | Art & Design | publicapis.dev | unknown | https://publicapis.dev/resource/cooper-hewitt/k0b68cdx |
| Cooper Hewitt | Art & Design | publicapi.dev | no | https://publicapi.dev/cooper-hewitt-api |
| Europeana | Art & Design | publicapis.dev | unknown | https://publicapis.dev/resource/europeana/ixic0pgk |
| Europeana | Art & Design | publicapi.dev | no | https://publicapi.dev/europeana-api |
| GetGenAI (Packaging Compliance Review) | Art & Design | publicapis.dev | no | https://publicapis.dev/resource/getgenai-packaging-compliance-review-/zp495d6e |
| GetGenAI (Packaging Compliance Review) | Art & Design | publicapi.dev | no | https://publicapi.dev/get-gen-ai-packaging-compliance-review-api |
| Harvard Art Museums | Art & Design | publicapis.dev | unknown | https://publicapis.dev/resource/harvard-art-museums/ben369yk |
| Harvard Art Museums | Art & Design | publicapi.dev | no | https://publicapi.dev/harvard-art-museums-api |
| Iconfinder | Art & Design | publicapi.dev | no | https://publicapi.dev/iconfinder-api |
| Magnific Icons API | Art & Design | publicapis.dev | unknown | https://publicapis.dev/resource/magnific-icons-api/x8101i3f |
| Magnific Icons API | Art & Design | publicapi.dev | no | https://publicapi.dev/magnific-icons-api |
| Picsart | Art & Design | publicapis.dev | yes | https://publicapis.dev/resource/picsart/zfte6u6u |
| Picsart | Art & Design | publicapi.dev | yes | https://publicapi.dev/picsart-api |
| Pika | Art & Design | publicapis.dev | yes | https://publicapis.dev/resource/pika/2nqoni1w |
| Pika | Art & Design | publicapi.dev | yes | https://publicapi.dev/pika-api |
| Rijksmuseum | Art & Design | publicapi.dev | no | https://publicapi.dev/rijksmuseum-api |
| Word Cloud | Art & Design | publicapis.dev | unknown | https://publicapis.dev/resource/word-cloud/jha3f6vs |
| Word Cloud | Art & Design | publicapi.dev | no | https://publicapi.dev/word-cloud-api |
| Auth0 | Authentication & Authorization | publicapis.dev | yes | https://publicapis.dev/resource/auth0/yjssj1ww |
| Auth0 | Authentication & Authorization | publicapi.dev | yes | https://publicapi.dev/auth0-api |
| Clerk | Authentication & Authorization | publicapis.dev | yes | https://publicapis.dev/resource/clerk/u4tog26u |
| Clerk | Authentication & Authorization | publicapi.dev | yes | https://publicapi.dev/clerk-api |
| Corbado | Authentication & Authorization | publicapis.dev | yes | https://publicapis.dev/resource/corbado/fz5u7ibm |
| Corbado | Authentication & Authorization | publicapi.dev | yes | https://publicapi.dev/corbado-api |
| GetOTP | Authentication & Authorization | publicapis.dev | no | https://publicapis.dev/resource/getotp/yx8gk4jr |
| GetOTP | Authentication & Authorization | publicapi.dev | no | https://publicapi.dev/get-otp-api |
| Micro User Service | Authentication & Authorization | publicapi.dev | no | https://publicapi.dev/micro-user-service-api |
| MojoAuth | Authentication & Authorization | publicapis.dev | yes | https://publicapis.dev/resource/mojoauth/8iu0kqzl |
| MojoAuth | Authentication & Authorization | publicapi.dev | yes | https://publicapi.dev/mojo-auth-api |
| ReqKey | Authentication & Authorization | publicapis.dev | no | https://publicapis.dev/resource/reqkey/0oxg155t |
| SAWO Labs | Authentication & Authorization | publicapi.dev | yes | https://publicapi.dev/sawo-labs-api |
| Stytch | Authentication & Authorization | publicapis.dev | no | https://publicapis.dev/resource/stytch/049isphd |
| Stytch | Authentication & Authorization | publicapi.dev | no | https://publicapi.dev/stytch-api |
| Warrant | Authentication & Authorization | publicapis.dev | yes | https://publicapis.dev/resource/warrant/ykr0hd4k |
| Warrant | Authentication & Authorization | publicapi.dev | yes | https://publicapi.dev/warrant-api |
| Bitquery | Blockchain | publicapis.dev | yes | https://publicapis.dev/resource/bitquery/7gzj7roy |
| Bitquery | Blockchain | publicapi.dev | yes | https://publicapi.dev/bitquery-api |
| Blockscout | Blockchain | publicapis.dev | yes | https://publicapis.dev/resource/blockscout/2gyper2n |
| Blockscout | Blockchain | publicapi.dev | yes | https://publicapi.dev/blockscout-api |
| Bscscan | Blockchain | publicapis.dev | yes | https://publicapis.dev/resource/bscscan/xvddn16a |
| Bscscan | Blockchain | publicapi.dev | yes | https://publicapi.dev/bscscan-api |
| Covalent | Blockchain | publicapis.dev | unknown | https://publicapis.dev/resource/covalent/8y2bs3d5 |
| Covalent | Blockchain | publicapi.dev | no | https://publicapi.dev/covalent-api |
| Etherscan | Blockchain | publicapis.dev | yes | https://publicapis.dev/resource/etherscan/o05drm0e |
| Etherscan | Blockchain | publicapi.dev | yes | https://publicapi.dev/etherscan-api |
| GetBlock | Blockchain | publicapis.dev | unknown | https://publicapis.dev/resource/getblock/ytmbc4bl |
| GetBlock | Blockchain | publicapi.dev | no | https://publicapi.dev/get-block-api |
| Layer4 | Blockchain | publicapis.dev | yes | https://publicapis.dev/resource/layer4/zf8y0o1x |
| Layer4 | Blockchain | publicapi.dev | yes | https://publicapi.dev/layer4-api |
| Nownodes | Blockchain | publicapis.dev | unknown | https://publicapis.dev/resource/nownodes/0g00f8wc |
| Nownodes | Blockchain | publicapi.dev | no | https://publicapi.dev/nownodes-api |
| The Graph | Blockchain | publicapis.dev | unknown | https://publicapis.dev/resource/the-graph/lwuwfcy7 |
| The Graph | Blockchain | publicapi.dev | no | https://publicapi.dev/the-graph-api |
| Watchdata | Blockchain | publicapis.dev | unknown | https://publicapis.dev/resource/watchdata/1f2v4tf9 |
| Watchdata | Blockchain | publicapi.dev | no | https://publicapi.dev/watchdata-api |
| AdoptAPet | Book | publicapi.dev | yes | https://publicapi.dev/adopt-a-pet-api |
| A Bíblia Digital | Books | publicapi.dev | no | https://publicapi.dev/a-biblia-digital-api |
| Big Book | Books | publicapis.dev | yes | https://publicapis.dev/resource/big-book/7t70a9op |
| Big Book | Books | publicapi.dev | yes | https://publicapi.dev/big-book-api |
| The Bible | Books | publicapis.dev | unknown | https://publicapis.dev/resource/the-bible/rzztslh9 |
| The Bible | Books | publicapi.dev | no | https://publicapi.dev/the-bible-api |
| akta.pro | Business | publicapis.dev | yes | https://publicapis.dev/resource/akta-pro/f79ymw13 |
| Apache Superset | Business | publicapis.dev | yes | https://publicapis.dev/resource/apache-superset/silztj4y |
| Apache Superset | Business | publicapi.dev | yes | https://publicapi.dev/apache-superset-api |
| ArvanCloud | Business | publicapis.dev | no | https://publicapis.dev/resource/arvancloud/7oppbzis |
| ArvanCloud | Business | publicapi.dev | no | https://publicapi.dev/arvan-cloud-api |
| Charity Search | Business | publicapis.dev | unknown | https://publicapis.dev/resource/charity-search/foyt1x9m |
| Charity Search | Business | publicapi.dev | no | https://publicapi.dev/charity-search-api |
| Clearbit Logo | Business | publicapi.dev | no | https://publicapi.dev/clearbit-logo-api |
| CompanyData.com | Business | publicapi.dev | yes | https://publicapi.dev/company-data-com-api |
| CompanyEnrich | Business | publicapis.dev | yes | https://publicapis.dev/resource/companyenrich/yahpt4dw |
| CompanyEnrich | Business | publicapi.dev | yes | https://publicapi.dev/company-enrich-api |
| Crustdata | Business | publicapi.dev | yes | https://publicapi.dev/crustdata-api |
| Indexed | Business | publicapi.dev | no | https://publicapi.dev/indexed-api |
| Instatus | Business | publicapis.dev | unknown | https://publicapis.dev/resource/instatus/xd9l2582 |
| Instatus | Business | publicapi.dev | no | https://publicapi.dev/instatus-api |
| Listings API | Business | publicapis.dev | unknown | https://publicapis.dev/resource/listings-api/zvnv6jq3 |
| Listings API | Business | publicapi.dev | no | https://publicapi.dev/listings-api |
| Logo.dev | Business | publicapis.dev | yes | https://publicapis.dev/resource/logo-dev/t4ybepas |
| Logo.dev | Business | publicapi.dev | yes | https://publicapi.dev/logo-dev-api |
| LogoKit | Business | publicapis.dev | unknown | https://publicapis.dev/resource/logokit/tb8a1dmi |
| LogoKit | Business | publicapi.dev | no | https://publicapi.dev/logo-kit-api |
| Mailchimp | Business | publicapis.dev | unknown | https://publicapis.dev/resource/mailchimp/4m2qyuxb |
| Mailchimp | Business | publicapi.dev | no | https://publicapi.dev/mailchimp-api |
| mailjet | Business | publicapis.dev | unknown | https://publicapis.dev/resource/mailjet/tysyh8to |
| mailjet | Business | publicapi.dev | no | https://publicapi.dev/mailjet-api |
| NioLeads | Business | publicapis.dev | yes | https://publicapis.dev/resource/nioleads/1diy0nqj |
| NioLeads | Business | publicapi.dev | yes | https://publicapi.dev/nio-leads-api |
| ORB Intelligence | Business | publicapi.dev | no | https://publicapi.dev/orb-intelligence-api |
| Project Cost Estimator | Business | publicapi.dev | yes | https://publicapi.dev/project-cost-estimator-api |
| Redash | Business | publicapis.dev | yes | https://publicapis.dev/resource/redash/bo9dv2ga |
| Redash | Business | publicapi.dev | yes | https://publicapi.dev/redash-api |
| Renidly | Business | publicapis.dev | yes | https://publicapis.dev/resource/renidly/lvyu2zyr |
| Statable | Business | publicapi.dev | no | https://publicapi.dev/statable-api |
| SwiftKanban | Business | publicapis.dev | unknown | https://publicapis.dev/resource/swiftkanban/osuzjrzd |
| SwiftKanban | Business | publicapi.dev | no | https://publicapi.dev/swift-kanban-api |
| Tomba email finder | Business | publicapis.dev | yes | https://publicapis.dev/resource/tomba-email-finder/ucl132tz |
| Tomba email finder | Business | publicapi.dev | yes | https://publicapi.dev/tomba-email-finder-api |
| USPTO Trademark | Business | publicapis.dev | yes | https://publicapis.dev/resource/uspto-trademark/lwhd9g4c |
| USPTO Trademark | Business | publicapi.dev | yes | https://publicapi.dev/uspto-trademark-api |
| Zillapi | Business | publicapis.dev | yes | https://publicapis.dev/resource/zillapi/dyr2l47p |
| Zillapi | Business | publicapi.dev | yes | https://publicapi.dev/zillapi-api |
| Abstract Public Holidays | Calendar | publicapis.dev | yes | https://publicapis.dev/resource/abstract-public-holidays/cngbb7ec |
| Abstract Public Holidays | Calendar | publicapi.dev | yes | https://publicapi.dev/abstract-public-holidays-api |
| Calendarific | Calendar | publicapis.dev | unknown | https://publicapis.dev/resource/calendarific/3rknh5dy |
| Calendarific | Calendar | publicapi.dev | no | https://publicapi.dev/calendarific-api |
| Checkiday - National Holiday API | Calendar | publicapi.dev | no | https://publicapi.dev/checkiday-national-holiday-api |
| Festivo Public Holidays | Calendar | publicapis.dev | yes | https://publicapis.dev/resource/festivo-public-holidays/dpq4pp50 |
| Festivo Public Holidays | Calendar | publicapi.dev | yes | https://publicapi.dev/festivo-public-holidays-api |
| Generate iCAL | Calendar | publicapis.dev | yes | https://publicapis.dev/resource/generate-ical/k0mq6tyt |
| Generate iCAL | Calendar | publicapi.dev | yes | https://publicapi.dev/generate-i-cal-api |
| Holidays | Calendar | publicapis.dev | unknown | https://publicapis.dev/resource/holidays/qjsbvsq9 |
| Holidays | Calendar | publicapi.dev | no | https://publicapi.dev/holidays-api |
| Databricks | Cloud Storage & File Sharing | publicapis.dev | unknown | https://publicapis.dev/resource/databricks/4rheh656 |
| Databricks | Cloud Storage & File Sharing | publicapi.dev | no | https://publicapi.dev/databricks-api |
| ddownload | Cloud Storage & File Sharing | publicapis.dev | unknown | https://publicapis.dev/resource/ddownload/3vxxktdu |
| ddownload | Cloud Storage & File Sharing | publicapi.dev | no | https://publicapi.dev/ddownload-api |
| Fil One | Cloud Storage & File Sharing | publicapis.dev | yes | https://publicapis.dev/resource/fil-one/t5jwn26w |
| Fil One | Cloud Storage & File Sharing | publicapi.dev | yes | https://publicapi.dev/fil-one-api |
| Filestack | Cloud Storage & File Sharing | publicapi.dev | no | https://publicapi.dev/filestack-api |
| Gcore Storage | Cloud Storage & File Sharing | publicapis.dev | yes | https://publicapis.dev/resource/gcore-storage/ty357sdv |
| Gcore Storage | Cloud Storage & File Sharing | publicapi.dev | yes | https://publicapi.dev/gcore-storage-api |
| GoFile | Cloud Storage & File Sharing | publicapis.dev | unknown | https://publicapis.dev/resource/gofile/qizr1h4d |
| GoFile | Cloud Storage & File Sharing | publicapi.dev | no | https://publicapi.dev/go-file-api |
| Gyazo | Cloud Storage & File Sharing | publicapis.dev | unknown | https://publicapis.dev/resource/gyazo/40gmc4br |
| Gyazo | Cloud Storage & File Sharing | publicapi.dev | no | https://publicapi.dev/gyazo-api |
| Imgbb | Cloud Storage & File Sharing | publicapis.dev | unknown | https://publicapis.dev/resource/imgbb/t42kctsd |
| Imgbb | Cloud Storage & File Sharing | publicapi.dev | no | https://publicapi.dev/imgbb-api |
| Pastebin | Cloud Storage & File Sharing | publicapis.dev | unknown | https://publicapis.dev/resource/pastebin/6duwn93m |
| Pastebin | Cloud Storage & File Sharing | publicapi.dev | no | https://publicapi.dev/pastebin-api |
| Pinata | Cloud Storage & File Sharing | publicapis.dev | unknown | https://publicapis.dev/resource/pinata/bjzux20f |
| Pinata | Cloud Storage & File Sharing | publicapi.dev | no | https://publicapi.dev/pinata-api |
| Quip | Cloud Storage & File Sharing | publicapis.dev | yes | https://publicapis.dev/resource/quip/s8wucbxy |
| Quip | Cloud Storage & File Sharing | publicapi.dev | yes | https://publicapi.dev/quip-api |
| Smash | Cloud Storage & File Sharing | publicapis.dev | yes | https://publicapis.dev/resource/smash/wg04dp83 |
| Smash | Cloud Storage & File Sharing | publicapi.dev | yes | https://publicapi.dev/smash-api |
| Storj | Cloud Storage & File Sharing | publicapis.dev | unknown | https://publicapis.dev/resource/storj/oqcqhsb6 |
| Storj | Cloud Storage & File Sharing | publicapi.dev | no | https://publicapi.dev/storj-api |
| Web3 Storage | Cloud Storage & File Sharing | publicapi.dev | yes | https://publicapi.dev/web3-storage-api |
| Azure DevOps Health | Continuous Integration | publicapis.dev | no | https://publicapis.dev/resource/azure-devops-health/fe4w2zdt |
| Azure DevOps Health | Continuous Integration | publicapi.dev | no | https://publicapi.dev/azure-dev-ops-health-api |
| Bitrise | Continuous Integration | publicapis.dev | unknown | https://publicapis.dev/resource/bitrise/8lb8m33q |
| Bitrise | Continuous Integration | publicapi.dev | no | https://publicapi.dev/bitrise-api |
| CircleCI | Continuous Integration | publicapis.dev | unknown | https://publicapis.dev/resource/circleci/rltn99qw |
| CircleCI | Continuous Integration | publicapi.dev | no | https://publicapi.dev/circle-ci-api |
| Codeship | Continuous Integration | publicapis.dev | unknown | https://publicapis.dev/resource/codeship/p0ausrzn |
| Codeship | Continuous Integration | publicapi.dev | no | https://publicapi.dev/codeship-api |
| Travis CI | Continuous Integration | publicapis.dev | unknown | https://publicapis.dev/resource/travis-ci/hhud6znz |
| Travis CI | Continuous Integration | publicapi.dev | no | https://publicapi.dev/travis-ci-api |
| 0xArchive - Public APIs | Cryptocurrency | publicapis.dev | no | https://publicapis.dev/resource/0xarchive/0tfll1xb |
| 1inch | Cryptocurrency | publicapis.dev | yes | https://publicapis.dev/resource/1inch/tnsdj99n |
| 1inch | Cryptocurrency | publicapi.dev | yes | https://publicapi.dev/1inch-api |
| Alchemy Ethereum | Cryptocurrency | publicapis.dev | yes | https://publicapis.dev/resource/alchemy-ethereum/8ga6qgdj |
| Alchemy Ethereum | Cryptocurrency | publicapi.dev | yes | https://publicapi.dev/alchemy-ethereum-api |
| apilayer coinlayer | Cryptocurrency | publicapi.dev | no | https://publicapi.dev/apilayer-coinlayer-api |
| Binance | Cryptocurrency | publicapis.dev | unknown | https://publicapis.dev/resource/binance/2n3swjms |
| Binance | Cryptocurrency | publicapi.dev | no | https://publicapi.dev/binance-api |
| BitcoinAverage | Cryptocurrency | publicapi.dev | no | https://publicapi.dev/bitcoin-average-api |
| Bitfinex | Cryptocurrency | publicapis.dev | unknown | https://publicapis.dev/resource/bitfinex/b0ihuaug |
| Bitfinex | Cryptocurrency | publicapi.dev | no | https://publicapi.dev/bitfinex-api |
| Bitmex | Cryptocurrency | publicapis.dev | unknown | https://publicapis.dev/resource/bitmex/g3g2ojjg |
| Bitmex | Cryptocurrency | publicapi.dev | no | https://publicapi.dev/bitmex-api |
| Bittrex | Cryptocurrency | publicapi.dev | no | https://publicapi.dev/bittrex-api |
| Block | Cryptocurrency | publicapi.dev | no | https://publicapi.dev/block-api |
| BlockBee | Cryptocurrency | publicapis.dev | unknown | https://publicapis.dev/resource/blockbee/8l3rj2un |
| BlockBee | Cryptocurrency | publicapi.dev | no | https://publicapi.dev/block-bee-api |
| Blockchain | Cryptocurrency | publicapis.dev | unknown | https://publicapis.dev/resource/blockchain/xoxc6o9h |
| Blockchain | Cryptocurrency | publicapi.dev | no | https://publicapi.dev/blockchain-api |
| blockfrost Cardano | Cryptocurrency | publicapis.dev | unknown | https://publicapis.dev/resource/blockfrost-cardano/jzfu7sjv |
| blockfrost Cardano | Cryptocurrency | publicapi.dev | no | https://publicapi.dev/blockfrost-cardano-api |
| Brave NewCoin | Cryptocurrency | publicapis.dev | unknown | https://publicapis.dev/resource/brave-newcoin/3gxx6ihv |
| Brave NewCoin | Cryptocurrency | publicapi.dev | no | https://publicapi.dev/brave-new-coin-api |
| BtcTurk | Cryptocurrency | publicapis.dev | yes | https://publicapis.dev/resource/btcturk/atiyyntc |
| BtcTurk | Cryptocurrency | publicapi.dev | yes | https://publicapi.dev/btc-turk-api |
| Bybit | Cryptocurrency | publicapis.dev | unknown | https://publicapis.dev/resource/bybit/ztjzhw40 |
| Bybit | Cryptocurrency | publicapi.dev | no | https://publicapi.dev/bybit-api |
| ByKaranteli | Cryptocurrency | publicapis.dev | yes | https://publicapis.dev/resource/bykaranteli/q3ojty4e |
| CabalSpy | Cryptocurrency | publicapis.dev | unknown | https://publicapis.dev/resource/cabalspy/wg9vnpqa |
| Codex Data | Cryptocurrency | publicapis.dev | yes | https://publicapis.dev/resource/codex-data/kc92z7jy |
| CoinAPI | Cryptocurrency | publicapis.dev | no | https://publicapis.dev/resource/coinapi/1bl3akoi |
| CoinAPI | Cryptocurrency | publicapi.dev | no | https://publicapi.dev/coin-api |
| Coinbase | Cryptocurrency | publicapis.dev | unknown | https://publicapis.dev/resource/coinbase/8k78wezz |
| Coinbase | Cryptocurrency | publicapi.dev | no | https://publicapi.dev/coinbase-api |
| Coinbase Pro | Cryptocurrency | publicapis.dev | unknown | https://publicapis.dev/resource/coinbase-pro/ih72o6nq |
| Coinbase Pro | Cryptocurrency | publicapi.dev | no | https://publicapi.dev/coinbase-pro-api |
| CoinDCX | Cryptocurrency | publicapis.dev | unknown | https://publicapis.dev/resource/coindcx/i2kpat1d |
| CoinDCX | Cryptocurrency | publicapi.dev | no | https://publicapi.dev/coin-dcx-api |
| Coinigy | Cryptocurrency | publicapis.dev | unknown | https://publicapis.dev/resource/coinigy/gvg760em |
| Coinigy | Cryptocurrency | publicapi.dev | no | https://publicapi.dev/coinigy-api |
| Coinlib | Cryptocurrency | publicapis.dev | unknown | https://publicapis.dev/resource/coinlib/s1vuzx11 |
| Coinlib | Cryptocurrency | publicapi.dev | no | https://publicapi.dev/coinlib-api |
| CoinMarketCap | Cryptocurrency | publicapis.dev | unknown | https://publicapis.dev/resource/coinmarketcap/071dt0h6 |
| CoinMarketCap | Cryptocurrency | publicapi.dev | no | https://publicapi.dev/coin-market-cap-api |
| CoinRanking | Cryptocurrency | publicapis.dev | unknown | https://publicapis.dev/resource/coinranking/llcqk2li |
| CoinRanking | Cryptocurrency | publicapi.dev | no | https://publicapi.dev/coin-ranking-api |
| Coinremitter | Cryptocurrency | publicapis.dev | unknown | https://publicapis.dev/resource/coinremitter/v3gbesrg |
| Coinremitter | Cryptocurrency | publicapi.dev | no | https://publicapi.dev/coinremitter-api |
| CoinStats | Cryptocurrency | publicapis.dev | yes | https://publicapis.dev/resource/coinstats/ancy3m2m |
| CoinStats | Cryptocurrency | publicapi.dev | yes | https://publicapi.dev/coin-stats-api |
| CryptoMarket | Cryptocurrency | publicapi.dev | yes | https://publicapi.dev/crypto-market-api |
| dYdX | Cryptocurrency | publicapis.dev | unknown | https://publicapis.dev/resource/dydx/lkpuyyf0 |
| dYdX | Cryptocurrency | publicapi.dev | no | https://publicapi.dev/d-yd-x-api |
| Ethplorer | Cryptocurrency | publicapis.dev | unknown | https://publicapis.dev/resource/ethplorer/d636xvb9 |
| Ethplorer | Cryptocurrency | publicapi.dev | no | https://publicapi.dev/ethplorer-api |
| EXMO | Cryptocurrency | publicapis.dev | unknown | https://publicapis.dev/resource/exmo/icyagc7d |
| EXMO | Cryptocurrency | publicapi.dev | no | https://publicapi.dev/exmo-api |
| FTX | Cryptocurrency | publicapi.dev | yes | https://publicapi.dev/ftx-api |
| Gateio | Cryptocurrency | publicapis.dev | unknown | https://publicapis.dev/resource/gateio/l0qb7rcy |
| Gateio | Cryptocurrency | publicapi.dev | no | https://publicapi.dev/gateio-api |
| Hirak Exchange Rates | Cryptocurrency | publicapi.dev | no | https://publicapi.dev/hirak-exchange-rates-api |
| Huobi | Cryptocurrency | publicapis.dev | unknown | https://publicapis.dev/resource/huobi/2jxa5s6z |
| Huobi | Cryptocurrency | publicapi.dev | no | https://publicapi.dev/huobi-api |
| icy.tools | Cryptocurrency | publicapi.dev | no | https://publicapi.dev/icy-tools-api |
| Indodax | Cryptocurrency | publicapis.dev | unknown | https://publicapis.dev/resource/indodax/so0opqun |
| Indodax | Cryptocurrency | publicapi.dev | no | https://publicapi.dev/indodax-api |
| INFURA Ethereum | Cryptocurrency | publicapis.dev | yes | https://publicapis.dev/resource/infura-ethereum/wworfrkp |
| INFURA Ethereum | Cryptocurrency | publicapis.dev | yes | https://publicapis.dev/resource/infura-ethereum/dync8f5c |
| INFURA Ethereum | Cryptocurrency | publicapi.dev | yes | https://publicapi.dev/infura-ethereum-api |
| Kraken | Cryptocurrency | publicapis.dev | unknown | https://publicapis.dev/resource/kraken/psftwxwg |
| Kraken | Cryptocurrency | publicapi.dev | no | https://publicapi.dev/kraken-api |
| KuCoin | Cryptocurrency | publicapis.dev | unknown | https://publicapis.dev/resource/kucoin/tk6pbowd |
| KuCoin | Cryptocurrency | publicapi.dev | no | https://publicapi.dev/ku-coin-api |
| MadeOnSol | Cryptocurrency | publicapis.dev | yes | https://publicapis.dev/resource/madeonsol/djcc11ww |
| Mobula | Cryptocurrency | publicapis.dev | yes | https://publicapis.dev/resource/mobula/8z3g622j |
| Nomics | Cryptocurrency | publicapi.dev | yes | https://publicapi.dev/nomics-api |
| NovaDax | Cryptocurrency | publicapis.dev | unknown | https://publicapis.dev/resource/novadax/v3bgu5mz |
| NovaDax | Cryptocurrency | publicapi.dev | no | https://publicapi.dev/nova-dax-api |
| OKEx | Cryptocurrency | publicapis.dev | unknown | https://publicapis.dev/resource/okex/xvjuya07 |
| OKEx | Cryptocurrency | publicapi.dev | no | https://publicapi.dev/ok-ex-api |
| OpenSea | Cryptocurrency | publicapis.dev | no | https://publicapis.dev/resource/opensea/082grnzw |
| OpenSea | Cryptocurrency | publicapi.dev | no | https://publicapi.dev/open-sea-api |
| Poloniex | Cryptocurrency | publicapis.dev | unknown | https://publicapis.dev/resource/poloniex/0yu38piy |
| Poloniex | Cryptocurrency | publicapi.dev | no | https://publicapi.dev/poloniex-api |
| Technical Analysis | Cryptocurrency | publicapi.dev | no | https://publicapi.dev/technical-analysis-api |
| VALR | Cryptocurrency | publicapis.dev | unknown | https://publicapis.dev/resource/valr/8oyfo04r |
| VALR | Cryptocurrency | publicapi.dev | no | https://publicapi.dev/valr-api |
| WorldCoinIndex | Cryptocurrency | publicapis.dev | unknown | https://publicapis.dev/resource/worldcoinindex/ja485kbs |
| WorldCoinIndex | Cryptocurrency | publicapi.dev | no | https://publicapi.dev/world-coin-index-api |
| 1Forge | Currency Exchange | publicapis.dev | unknown | https://publicapis.dev/resource/1forge/fixtp0bf |
| 1Forge | Currency Exchange | publicapi.dev | no | https://publicapi.dev/1forge-api |
| AllRatesToday | Currency Exchange | publicapis.dev | yes | https://publicapis.dev/resource/allratestoday/obgx1siv |
| Amdoren | Currency Exchange | publicapi.dev | no | https://publicapi.dev/amdoren-api |
| apilayer fixer.io | Currency Exchange | publicapi.dev | no | https://publicapi.dev/apilayer-fixer-io-api |
| ApogeoAPI | Currency Exchange | publicapis.dev | yes | https://publicapis.dev/resource/apogeoapi/ih2tov6c |
| BCV (Banco Central de Venezuela) Exchange rate | Currency Exchange | publicapi.dev | no | https://publicapi.dev/bcv-banco-central-de-venezuela-exchange-rate-api |
| Convert Currency to Multiple Currencies | Currency Exchange | publicapis.dev | yes | https://publicapis.dev/resource/convert-currency-to-multiple-currencies/wh2lkjbl |
| Convert Currency to Multiple Currencies | Currency Exchange | publicapi.dev | yes | https://publicapi.dev/convert-currency-to-multiple-currencies-api |
| Currencyapi | Currency Exchange | publicapis.dev | yes | https://publicapis.dev/resource/currencyapi/kfstokb7 |
| Currencyapi | Currency Exchange | publicapi.dev | yes | https://publicapi.dev/currencyapi-api |
| CurrencyFreaks | Currency Exchange | publicapis.dev | yes | https://publicapis.dev/resource/currencyfreaks/a292b0b2 |
| CurrencyFreaks | Currency Exchange | publicapi.dev | yes | https://publicapi.dev/currency-freaks-api |
| Currencylayer | Currency Exchange | publicapi.dev | no | https://publicapi.dev/currencylayer-api |
| CurrencyScoop | Currency Exchange | publicapis.dev | yes | https://publicapis.dev/resource/currencyscoop/hgcpzsdv |
| CurrencyScoop | Currency Exchange | publicapi.dev | yes | https://publicapi.dev/currency-scoop-api |
| Exchange-Rate | Currency Exchange | publicapis.dev | yes | https://publicapis.dev/resource/exchange-rate/u2k80khl |
| ExchangeRate-API | Currency Exchange | publicapis.dev | yes | https://publicapis.dev/resource/exchangerate-api/agistedt |
| ExchangeRate-API | Currency Exchange | publicapi.dev | yes | https://publicapi.dev/exchange-rate-api |
| Exchangerate.host | Currency Exchange | publicapis.dev | unknown | https://publicapis.dev/resource/exchangerate-host/sb0pxhps |
| Exchangerate.host | Currency Exchange | publicapi.dev | no | https://publicapi.dev/exchangerate-host-api |
| Exchangeratesapi.io | Currency Exchange | publicapi.dev | yes | https://publicapi.dev/exchangeratesapi-io-api |
| FreeForexAPI | Currency Exchange | publicapi.dev | no | https://publicapi.dev/free-forex-api |
| FxFeed.io | Currency Exchange | publicapis.dev | yes | https://publicapis.dev/resource/fxfeed-io/qpnt3t0z |
| FxFeed.io | Currency Exchange | publicapi.dev | yes | https://publicapi.dev/fx-feed-io-api |
| FxRatesAPI | Currency Exchange | publicapis.dev | no | https://publicapis.dev/resource/fxratesapi/4ekfukyl |
| FxRatesAPI | Currency Exchange | publicapi.dev | no | https://publicapi.dev/fx-rates-api |
| FyGraph | Currency Exchange | publicapi.dev | yes | https://publicapi.dev/fy-graph-api |
| Manyapis | Currency Exchange | publicapis.dev | yes | https://publicapis.dev/resource/manyapis/9g9jpjjy |
| Manyapis | Currency Exchange | publicapi.dev | yes | https://publicapi.dev/manyapis-api |
| MetalpriceAPI | Currency Exchange | publicapis.dev | unknown | https://publicapis.dev/resource/metalpriceapi/vb4b4s47 |
| MetalpriceAPI | Currency Exchange | publicapi.dev | no | https://publicapi.dev/metalprice-api |
| RBA Exchange Rates API | Currency Exchange | publicapis.dev | yes | https://publicapis.dev/resource/rba-exchange-rates-api/q468kesx |
| RBA Exchange Rates API | Currency Exchange | publicapi.dev | yes | https://publicapi.dev/rba-exchange-rates-api |
| RealMarketAPI | Currency Exchange | publicapis.dev | unknown | https://publicapis.dev/resource/realmarketapi/0agawmq7 |
| Unirate | Currency Exchange | publicapis.dev | yes | https://publicapis.dev/resource/unirate/phti2ts7 |
| Unirate | Currency Exchange | publicapi.dev | yes | https://publicapi.dev/unirate-api |
| ApexVerify | Data Validation | publicapis.dev | no | https://publicapis.dev/resource/apexverify/g94hyci7 |
| Brevo Suit | Data Validation | publicapis.dev | yes | https://publicapis.dev/resource/brevo-suit/g8xvhmnb |
| Brevo Suit | Data Validation | publicapi.dev | yes | https://publicapi.dev/brevo-suit-api |
| Lob.com | Data Validation | publicapis.dev | unknown | https://publicapis.dev/resource/lob-com/aczngsas |
| Lob.com | Data Validation | publicapi.dev | no | https://publicapi.dev/lob-com-api |
| Nonprofit Check Plus | Data Validation | publicapis.dev | unknown | https://publicapis.dev/resource/nonprofit-check-plus/seffzv6b |
| Nonprofit Check Plus | Data Validation | publicapi.dev | no | https://publicapi.dev/nonprofit-check-plus-api |
| US Autocomplete | Data Validation | publicapis.dev | yes | https://publicapis.dev/resource/us-autocomplete/bs19v5tm |
| US Autocomplete | Data Validation | publicapi.dev | yes | https://publicapi.dev/us-autocomplete-api |
| US Extract | Data Validation | publicapis.dev | yes | https://publicapis.dev/resource/us-extract/0ke9xoqv |
| US Extract | Data Validation | publicapi.dev | yes | https://publicapi.dev/us-extract-api |
| US Street Address | Data Validation | publicapis.dev | yes | https://publicapis.dev/resource/us-street-address/ae3mkbal |
| US Street Address | Data Validation | publicapi.dev | yes | https://publicapi.dev/us-street-address-api |
| Validate UK Postcodes | Data Validation | publicapis.dev | yes | https://publicapis.dev/resource/validate-uk-postcodes/tckdkwrh |
| Validate UK Postcodes | Data Validation | publicapi.dev | yes | https://publicapi.dev/validate-uk-postcodes-api |
| VatcheckAPI | Data Validation | publicapis.dev | yes | https://publicapis.dev/resource/vatcheckapi/g1nwkjir |
| VatcheckAPI | Data Validation | publicapi.dev | yes | https://publicapi.dev/vatcheck-api |
| vatlayer | Data Validation | publicapi.dev | no | https://publicapi.dev/vatlayer-api |
| VerifyEd | Data Validation | publicapis.dev | yes | https://publicapis.dev/resource/verifyed/e92muwdc |
| Abstract Screenshot | Development | publicapis.dev | yes | https://publicapis.dev/resource/abstract-screenshot/f5w3867t |
| Abstract Screenshot | Development | publicapi.dev | yes | https://publicapi.dev/abstract-screenshot-api |
| Agent Gateway | Development | publicapis.dev | yes | https://publicapis.dev/resource/agent-gateway/2g65z7cf |
| Agent Gateway | Development | publicapi.dev | yes | https://publicapi.dev/agent-gateway-api |
| Amazonscraperapi | Development | publicapis.dev | no | https://publicapis.dev/resource/amazonscraperapi/rdesirz9 |
| AnyAPI | Development | publicapi.dev | no | https://publicapi.dev/any-api |
| API League | Development | publicapis.dev | yes | https://publicapis.dev/resource/api-league/n6f7uahk |
| API League | Development | publicapi.dev | yes | https://publicapi.dev/api-league-api |
| ApiFlash | Development | publicapis.dev | unknown | https://publicapis.dev/resource/apiflash/w7la3ukl |
| ApiFlash | Development | publicapi.dev | no | https://publicapi.dev/api-flash-api |
| ApyHub | Development | publicapi.dev | yes | https://publicapi.dev/apy-hub-api |
| ApyHub APIs: A Complete API Catalog | Development | publicapi.dev | yes | https://publicapi.dev/apy-hub-ap-is-a-complete-api-catalog-api |
| ApyHub APIs: Secure APIs for Every Application | Development | publicapi.dev | yes | https://publicapi.dev/apy-hub-ap-is-secure-ap-is-for-every-application-api |
| ApyHub: A Complete API Catalog | Development | publicapis.dev | yes | https://publicapis.dev/resource/apyhub-a-complete-api-catalog/yd71umxr |
| ApyHub: A Complete API Catalog | Development | publicapi.dev | yes | https://publicapi.dev/apy-hub-a-complete-api-catalog-api |
| ASORadar | Development | publicapis.dev | unknown | https://publicapis.dev/resource/asoradar/b321r8gy |
| AutoChangelog | Development | publicapis.dev | yes | https://publicapis.dev/resource/autochangelog/p3odgar8 |
| AutoChangelog | Development | publicapi.dev | yes | https://publicapi.dev/auto-changelog-api |
| Autom | Development | publicapis.dev | yes | https://publicapis.dev/resource/autom/olrvyjxx |
| Autom | Development | publicapi.dev | yes | https://publicapi.dev/autom-api |
| Azure DevOps | Development | publicapis.dev | unknown | https://publicapis.dev/resource/azure-devops/mceqdop6 |
| Azure DevOps | Development | publicapi.dev | no | https://publicapi.dev/azure-dev-ops-api |
| Base | Development | publicapi.dev | yes | https://publicapi.dev/base-api |
| Blague.xyz | Development | publicapi.dev | yes | https://publicapi.dev/blague-xyz-api |
| Blitapp | Development | publicapis.dev | unknown | https://publicapis.dev/resource/blitapp/3ol5b7ws |
| Blitapp | Development | publicapi.dev | no | https://publicapi.dev/blitapp-api |
| Blynk-Cloud | Development | publicapis.dev | unknown | https://publicapis.dev/resource/blynk-cloud/6cwdtks5 |
| Blynk-Cloud | Development | publicapi.dev | no | https://publicapi.dev/blynk-cloud-api |
| Brainshop.ai | Development | publicapi.dev | yes | https://publicapi.dev/brainshop-ai-api |
| Brand.dev | Development | publicapis.dev | yes | https://publicapis.dev/resource/brand-dev/ruvvchzn |
| Brand.dev | Development | publicapi.dev | yes | https://publicapi.dev/brand-dev-api |
| Brandfetch Logo API | Development | publicapis.dev | yes | https://publicapis.dev/resource/brandfetch-logo-api/tqsogzpw |
| Brave Search API | Development | publicapis.dev | no | https://publicapis.dev/resource/brave-search-api/zdq8t0zr |
| Brave Search API | Development | publicapi.dev | no | https://publicapi.dev/brave-search-api |
| BrowserCat | Development | publicapis.dev | yes | https://publicapis.dev/resource/browsercat/tq9dg58j |
| BrowserCat | Development | publicapi.dev | yes | https://publicapi.dev/browser-cat-api |
| Browshot | Development | publicapis.dev | yes | https://publicapis.dev/resource/browshot/7194139j |
| Browshot | Development | publicapi.dev | yes | https://publicapi.dev/browshot-api |
| Cache Horse | Development | publicapi.dev | yes | https://publicapi.dev/cache-horse-api |
| ChatterBox | Development | publicapi.dev | yes | https://publicapi.dev/chatter-box-api |
| Contentful Images | Development | publicapis.dev | yes | https://publicapis.dev/resource/contentful-images/zcpwuscu |
| Contentful Images | Development | publicapi.dev | yes | https://publicapi.dev/contentful-images-api |
| CoreClaw | Development | publicapis.dev | unknown | https://publicapis.dev/resource/coreclaw/5m5g03ia |
| CSC to JSON API | Development | publicapi.dev | yes | https://publicapi.dev/csc-to-json-api |
| CSV to JSON API | Development | publicapis.dev | yes | https://publicapis.dev/resource/csv-to-json-api/btcinf4f |
| Databricks | Development | publicapis.dev | yes | https://publicapis.dev/resource/databricks/z71f6ugu |
| Databricks | Development | publicapi.dev | yes | https://publicapi.dev/databricks-api |
| DATPAQ | Development | publicapis.dev | yes | https://publicapis.dev/resource/datpaq/vnugdk4p |
| Docker Hub | Development | publicapis.dev | yes | https://publicapis.dev/resource/docker-hub/kwmvg52h |
| Docker Hub | Development | publicapi.dev | yes | https://publicapi.dev/docker-hub-api |
| FireDash Fire Hydrant Public API | Development | publicapis.dev | yes | https://publicapis.dev/resource/firedash-fire-hydrant-public-api/8l4g1vr5 |
| Form Creation API | Development | publicapis.dev | yes | https://publicapis.dev/resource/form-creation-api/y8a4avqo |
| Form Creation API | Development | publicapi.dev | yes | https://publicapi.dev/form-creation-api |
| Gcore CDN | Development | publicapis.dev | yes | https://publicapis.dev/resource/gcore-cdn/7zhysaos |
| Gcore CDN | Development | publicapi.dev | yes | https://publicapi.dev/gcore-cdn-api |
| Gcore Cloud | Development | publicapis.dev | yes | https://publicapis.dev/resource/gcore-cloud/g3xyspby |
| Gcore Cloud | Development | publicapi.dev | yes | https://publicapi.dev/gcore-cloud-api |
| Gcore DNS | Development | publicapis.dev | yes | https://publicapis.dev/resource/gcore-dns/btvo1krw |
| Gcore DNS | Development | publicapi.dev | yes | https://publicapi.dev/gcore-dns-api |
| GeekFlare | Development | publicapi.dev | no | https://publicapi.dev/geek-flare-api |
| Geekflare API | Development | publicapi.dev | no | https://publicapi.dev/geekflare-api |
| Generate Full Webpage Screenshot | Development | publicapis.dev | yes | https://publicapis.dev/resource/generate-full-webpage-screenshot/83a8pr7z |
| Generate Full Webpage Screenshot | Development | publicapi.dev | yes | https://publicapi.dev/generate-full-webpage-screenshot-api |
| GETPing | Development | publicapi.dev | no | https://publicapi.dev/get-ping-api |
| Ghost | Development | publicapis.dev | yes | https://publicapis.dev/resource/ghost/yq96apw0 |
| Ghost | Development | publicapi.dev | yes | https://publicapi.dev/ghost-api |
| Glitterly | Development | publicapi.dev | yes | https://publicapi.dev/glitterly-api |
| Google Firebase | Development | publicapis.dev | yes | https://publicapis.dev/resource/google-firebase/r4ia4owc |
| Google Firebase | Development | publicapi.dev | yes | https://publicapi.dev/google-firebase-api |
| Google Fonts | Development | publicapis.dev | unknown | https://publicapis.dev/resource/google-fonts/g7bq0w3a |
| Google Fonts | Development | publicapi.dev | no | https://publicapi.dev/google-fonts-api |
| Hasura | Development | publicapis.dev | yes | https://publicapis.dev/resource/hasura/jiam1r2a |
| Hasura | Development | publicapi.dev | yes | https://publicapi.dev/hasura-api |
| Host.io | Development | publicapis.dev | yes | https://publicapis.dev/resource/host-io/z1tlmup4 |
| Host.io | Development | publicapi.dev | yes | https://publicapi.dev/host-io-api |
| httpSMS | Development | publicapis.dev | yes | https://publicapis.dev/resource/httpsms/cekixo65 |
| httpSMS | Development | publicapi.dev | yes | https://publicapi.dev/http-sms-api |
| Hunter | Development | publicapis.dev | unknown | https://publicapis.dev/resource/hunter/f6v9ejia |
| Hunter | Development | publicapi.dev | no | https://publicapi.dev/hunter-api |
| IBM Text to Speech | Development | publicapis.dev | yes | https://publicapis.dev/resource/ibm-text-to-speech/awr518u0 |
| IBM Text to Speech | Development | publicapi.dev | yes | https://publicapi.dev/ibm-text-to-speech-api |
| import.io | Development | publicapis.dev | unknown | https://publicapis.dev/resource/import-io/167nu7mk |
| import.io | Development | publicapi.dev | no | https://publicapi.dev/import-io-api |
| IP Details API | Development | publicapis.dev | unknown | https://publicapis.dev/resource/ip-details-api/md4anh5j |
| IP Details API | Development | publicapi.dev | no | https://publicapi.dev/ip-details-api |
| IP2WHOIS Information Lookup | Development | publicapis.dev | unknown | https://publicapis.dev/resource/ip2whois-information-lookup/uw2mu62d |
| IP2WHOIS Information Lookup | Development | publicapi.dev | no | https://publicapi.dev/ip2whois-information-lookup-api |
| ipfind.io | Development | publicapis.dev | yes | https://publicapis.dev/resource/ipfind-io/8u5cp9bt |
| ipfind.io | Development | publicapi.dev | yes | https://publicapi.dev/ipfind-io-api |
| JSONbin.io | Development | publicapis.dev | yes | https://publicapis.dev/resource/jsonbin-io/jkzyd1f5 |
| JSONbin.io | Development | publicapi.dev | yes | https://publicapi.dev/jso-nbin-io-api |
| JSONsilo.com | Development | publicapis.dev | yes | https://publicapis.dev/resource/jsonsilo-com/zb0uxo4d |
| JSONsilo.com | Development | publicapi.dev | yes | https://publicapi.dev/jso-nsilo-com-api |
| Logs.to | Development | publicapi.dev | no | https://publicapi.dev/logs-to-api |
| MAC address vendor lookup | Development | publicapis.dev | yes | https://publicapis.dev/resource/mac-address-vendor-lookup/69jms7eu |
| MAC address vendor lookup | Development | publicapi.dev | yes | https://publicapi.dev/mac-address-vendor-lookup-api |
| Markdown to HTML API | Development | publicapis.dev | yes | https://publicapis.dev/resource/markdown-to-html-api/kpnyjbr5 |
| Markdown to HTML API | Development | publicapi.dev | yes | https://publicapi.dev/markdown-to-html-api |
| Markdown to JSON API | Development | publicapis.dev | yes | https://publicapis.dev/resource/markdown-to-json-api/681ihrwn |
| Markdown to JSON API | Development | publicapi.dev | yes | https://publicapi.dev/markdown-to-json-api |
| MetaScrape | Development | publicapis.dev | yes | https://publicapis.dev/resource/metascrape/vris0f21 |
| MetaScrape | Development | publicapi.dev | yes | https://publicapi.dev/meta-scrape-api |
| Micro DB | Development | publicapi.dev | no | https://publicapi.dev/micro-db-api |
| NiuProxy | Development | publicapis.dev | no | https://publicapis.dev/resource/niuproxy/xwdgddyb |
| OllaDNS | Development | publicapis.dev | yes | https://publicapis.dev/resource/olladns/ttsx2anj |
| Olostep | Development | publicapis.dev | yes | https://publicapis.dev/resource/olostep/4dj1ed7x |
| Olostep | Development | publicapi.dev | yes | https://publicapi.dev/olostep-api |
| OneSignal | Development | publicapis.dev | unknown | https://publicapis.dev/resource/onesignal/581nd60n |
| OneSignal | Development | publicapi.dev | no | https://publicapi.dev/one-signal-api |
| Open Page Rank | Development | publicapis.dev | unknown | https://publicapis.dev/resource/open-page-rank/3gi4d3on |
| Open Page Rank | Development | publicapi.dev | no | https://publicapi.dev/open-page-rank-api |
| OpenAPIHub | Development | publicapis.dev | unknown | https://publicapis.dev/resource/openapihub/1raqioiw |
| OpenAPIHub | Development | publicapi.dev | no | https://publicapi.dev/open-api-hub-api |
| OpenGraphr | Development | publicapi.dev | no | https://publicapi.dev/open-graphr-api |
| OpenWeb Ninja | Development | publicapis.dev | yes | https://publicapis.dev/resource/openweb-ninja/v4c18sbo |
| PageBolt | Development | publicapis.dev | yes | https://publicapis.dev/resource/pagebolt/zwi5qpcf |
| PageBolt | Development | publicapi.dev | yes | https://publicapi.dev/page-bolt-api |
| PageCDN | Development | publicapi.dev | yes | https://publicapi.dev/page-cdn-api |
| Piloterr | Development | publicapis.dev | yes | https://publicapis.dev/resource/piloterr/rf12ilv4 |
| Piloterr | Development | publicapi.dev | yes | https://publicapi.dev/piloterr-api |
| Posthook | Development | publicapis.dev | no | https://publicapis.dev/resource/posthook/z8p4v4ae |
| Postman | Development | publicapis.dev | unknown | https://publicapis.dev/resource/postman/scn3vjib |
| Postman | Development | publicapi.dev | no | https://publicapi.dev/postman-api |
| Proxed AI | Development | publicapis.dev | yes | https://publicapis.dev/resource/proxed-ai/c6xy9xx5 |
| Proxed AI | Development | publicapi.dev | yes | https://publicapi.dev/proxed-ai-api |
| Proxmox VE API | Development | publicapis.dev | unknown | https://publicapis.dev/resource/proxmox-ve-api/f1asqmde |
| Proxmox VE API | Development | publicapi.dev | no | https://publicapi.dev/proxmox-ve-api |
| ProxyCrawl | Development | publicapis.dev | unknown | https://publicapis.dev/resource/proxycrawl/8a50j6tf |
| ProxyCrawl | Development | publicapi.dev | no | https://publicapi.dev/proxy-crawl-api |
| ProxyKingdom | Development | publicapis.dev | yes | https://publicapis.dev/resource/proxykingdom/2n61acpy |
| ProxyKingdom | Development | publicapi.dev | yes | https://publicapi.dev/proxy-kingdom-api |
| Pusher Beams | Development | publicapis.dev | unknown | https://publicapis.dev/resource/pusher-beams/cdbx14id |
| Pusher Beams | Development | publicapi.dev | no | https://publicapi.dev/pusher-beams-api |
| Pusher Channels | Development | publicapis.dev | unknown | https://publicapis.dev/resource/pusher-channels/x3ud5dbr |
| Pusher Channels | Development | publicapi.dev | no | https://publicapi.dev/pusher-channels-api |
| QR code Generator | Development | publicapis.dev | unknown | https://publicapis.dev/resource/qr-code-generator/h9ecfka4 |
| QR code Generator | Development | publicapi.dev | no | https://publicapi.dev/qr-code-generator-api |
| Random Stuff | Development | publicapi.dev | yes | https://publicapi.dev/random-stuff-api |
| Rejax | Development | publicapi.dev | no | https://publicapi.dev/rejax-api |
| Render API | Development | publicapis.dev | unknown | https://publicapis.dev/resource/render-api/u84ivpx6 |
| Render API | Development | publicapi.dev | no | https://publicapi.dev/render-api |
| SavePage.io | Development | publicapi.dev | yes | https://publicapi.dev/save-page-io-api |
| ScrapeNinja | Development | publicapis.dev | unknown | https://publicapis.dev/resource/scrapeninja/zvtwwprt |
| ScrapeNinja | Development | publicapi.dev | no | https://publicapi.dev/scrape-ninja-api |
| ScraperApi | Development | publicapis.dev | unknown | https://publicapis.dev/resource/scraperapi/wkm3mjfk |
| ScraperApi | Development | publicapi.dev | no | https://publicapi.dev/scraper-api |
| scraperBox | Development | publicapis.dev | yes | https://publicapis.dev/resource/scraperbox/zx5oxcis |
| scraperBox | Development | publicapi.dev | yes | https://publicapi.dev/scraper-box-api |
| scrapestack | Development | publicapi.dev | no | https://publicapi.dev/scrapestack-api |
| Scrapfly | Development | publicapis.dev | no | https://publicapis.dev/resource/scrapfly/i5catlng |
| Scrapfly | Development | publicapi.dev | no | https://publicapi.dev/scrapfly-api |
| ScrapingAnt | Development | publicapis.dev | unknown | https://publicapis.dev/resource/scrapingant/z3ga21em |
| ScrapingAnt | Development | publicapi.dev | no | https://publicapi.dev/scraping-ant-api |
| ScrapingDog | Development | publicapis.dev | unknown | https://publicapis.dev/resource/scrapingdog/3swxwaeh |
| ScrapingDog | Development | publicapi.dev | no | https://publicapi.dev/scraping-dog-api |
| Screenshot Scout | Development | publicapis.dev | unknown | https://publicapis.dev/resource/screenshot-scout/zxu9w53l |
| Screenshot Scout | Development | publicapi.dev | no | https://publicapi.dev/screenshot-scout-api |
| ScreenshotAPI | Development | publicapis.dev | yes | https://publicapis.dev/resource/screenshotapi/27o61881 |
| ScreenshotAPI.net | Development | publicapis.dev | yes | https://publicapis.dev/resource/screenshotapi-net/ywuj08v0 |
| ScreenshotAPI.net | Development | publicapi.dev | yes | https://publicapi.dev/screenshot-api-net-api |
| Screenshotbase | Development | publicapis.dev | yes | https://publicapis.dev/resource/screenshotbase/9vp85hn0 |
| Screenshotbase | Development | publicapi.dev | yes | https://publicapi.dev/screenshotbase-api |
| ScreenshotOne.com | Development | publicapis.dev | yes | https://publicapis.dev/resource/screenshotone-com/60iewzxp |
| ScreenshotOne.com | Development | publicapi.dev | yes | https://publicapi.dev/screenshot-one-com-api |
| Scrnify | Development | publicapis.dev | yes | https://publicapis.dev/resource/scrnify/3a3x1p4l |
| Scrnify | Development | publicapi.dev | yes | https://publicapi.dev/scrnify-api |
| SearchApi | Development | publicapis.dev | no | https://publicapis.dev/resource/searchapi/hsb20i54 |
| Sent.dm | Development | publicapis.dev | yes | https://publicapis.dev/resource/sent-dm/jstwisy0 |
| Sent.dm | Development | publicapi.dev | yes | https://publicapi.dev/sent-dm-api |
| SEO Score | Development | publicapis.dev | yes | https://publicapis.dev/resource/seo-score/fz446v1e |
| SEO Score | Development | publicapi.dev | yes | https://publicapi.dev/seo-score-api |
| SEO Tags Generator API | Development | publicapis.dev | yes | https://publicapis.dev/resource/seo-tags-generator-api/yqmublhz |
| SEO Tags Generator API | Development | publicapi.dev | yes | https://publicapi.dev/seo-tags-generator-api |
| SERP Rank Checker | Development | publicapis.dev | yes | https://publicapis.dev/resource/serp-rank-checker/xsryj6pz |
| SERP Rank Checker | Development | publicapi.dev | yes | https://publicapi.dev/serp-rank-checker-api |
| SerpApi | Development | publicapi.dev | no | https://publicapi.dev/serp-api |
| SerpApi - Search API | Development | publicapis.dev | no | https://publicapis.dev/resource/serpapi-search-api/tp04gtps |
| SerpApi - Search API | Development | publicapi.dev | no | https://publicapi.dev/serp-api-search-api |
| serpstack | Development | publicapi.dev | yes | https://publicapi.dev/serpstack-api |
| Sheet2API | Development | publicapis.dev | unknown | https://publicapis.dev/resource/sheet2api/go16uvx5 |
| Sheet2API | Development | publicapi.dev | no | https://publicapi.dev/sheet2api-api |
| Sheetsu | Development | publicapis.dev | unknown | https://publicapis.dev/resource/sheetsu/xyd8g8cg |
| Sheetsu | Development | publicapi.dev | no | https://publicapi.dev/sheetsu-api |
| SnapAPI | Development | publicapis.dev | yes | https://publicapis.dev/resource/snapapi/54bxbxll |
| SocialScrape | Development | publicapis.dev | unknown | https://publicapis.dev/resource/socialscrape/um349xxh |
| Supadata | Development | publicapis.dev | yes | https://publicapis.dev/resource/supadata/fxcra1p0 |
| Supadata | Development | publicapi.dev | yes | https://publicapi.dev/supadata-api |
| Supportivekoala | Development | publicapis.dev | yes | https://publicapis.dev/resource/supportivekoala/dtwi6iux |
| Supportivekoala | Development | publicapi.dev | yes | https://publicapi.dev/supportivekoala-api |
| Svix | Development | publicapis.dev | unknown | https://publicapis.dev/resource/svix/0p9em7wv |
| Svix | Development | publicapi.dev | no | https://publicapi.dev/svix-api |
| Talordata | Development | publicapi.dev | no | https://publicapi.dev/talordata-api |
| TalorData | Development | publicapis.dev | no | https://publicapis.dev/resource/talordata/brxwu9n1 |
| TalorData | Development | publicapi.dev | no | https://publicapi.dev/talor-data-api |
| Thordata | Development | publicapis.dev | no | https://publicapis.dev/resource/thordata/hdsayy13 |
| Tolgee | Development | publicapis.dev | no | https://publicapis.dev/resource/tolgee/o0sgt2pw |
| Tolgee | Development | publicapi.dev | no | https://publicapi.dev/tolgee-api |
| Tyk | Development | publicapis.dev | yes | https://publicapis.dev/resource/tyk/rgyh8bwn |
| Tyk | Development | publicapi.dev | yes | https://publicapi.dev/tyk-api |
| UserAgent | Development | publicapis.dev | yes | https://publicapis.dev/resource/useragent/d2mtj63y |
| UserAgent | Development | publicapi.dev | yes | https://publicapi.dev/user-agent-api |
| Utilorax | Development | publicapis.dev | yes | https://publicapis.dev/resource/utilorax/02mjatbb |
| Web Scraping API | Development | publicapis.dev | yes | https://publicapis.dev/resource/web-scraping-api/7cxhlyhu |
| Web Scraping API | Development | publicapi.dev | yes | https://publicapi.dev/web-scraping-api |
| WebScraping.AI | Development | publicapis.dev | yes | https://publicapis.dev/resource/webscraping-ai/bfwqfgqg |
| WebScraping.AI | Development | publicapi.dev | yes | https://publicapi.dev/web-scraping-ai-api |
| YADG | Development | publicapi.dev | no | https://publicapi.dev/yadg-api |
| ZenRows | Development | publicapis.dev | unknown | https://publicapis.dev/resource/zenrows/cuys5u7l |
| ZenRows | Development | publicapi.dev | no | https://publicapi.dev/zen-rows-api |
| Zuplo | Development | publicapis.dev | unknown | https://publicapis.dev/resource/zuplo/wy45gbtn |
| Zuplo | Development | publicapi.dev | no | https://publicapi.dev/zuplo-api |
| Collins | Dictionaries | publicapis.dev | unknown | https://publicapis.dev/resource/collins/bfdj3cth |
| Collins | Dictionaries | publicapi.dev | no | https://publicapi.dev/collins-api |
| Lingua Robot | Dictionaries | publicapi.dev | yes | https://publicapi.dev/lingua-robot-api |
| Merriam-Webster | Dictionaries | publicapis.dev | unknown | https://publicapis.dev/resource/merriam-webster/22z96yl7 |
| Merriam-Webster | Dictionaries | publicapi.dev | no | https://publicapi.dev/merriam-webster-api |
| OwlBot | Dictionaries | publicapi.dev | yes | https://publicapi.dev/owl-bot-api |
| Oxford | Dictionaries | publicapis.dev | no | https://publicapis.dev/resource/oxford/hltk1c1v |
| Oxford | Dictionaries | publicapi.dev | no | https://publicapi.dev/oxford-api |
| Synonyms | Dictionaries | publicapis.dev | unknown | https://publicapis.dev/resource/synonyms/4vymrour |
| Synonyms | Dictionaries | publicapi.dev | no | https://publicapi.dev/synonyms-api |
| Wordnik | Dictionaries | publicapis.dev | unknown | https://publicapis.dev/resource/wordnik/v47r7txf |
| Wordnik | Dictionaries | publicapi.dev | no | https://publicapi.dev/wordnik-api |
| WordsAPI | Dictionaries | publicapi.dev | no | https://publicapi.dev/words-api |
| WordsAPI - Public APIs | Dictionaries | publicapis.dev | unknown | https://publicapis.dev/resource/wordsapi/zswv3x4p |
| AI Meeting Minutes Suite | Documents & Productivity | publicapis.dev | yes | https://publicapis.dev/resource/ai-meeting-minutes-suite/cdii6yh6 |
| AI Meeting Minutes Suite | Documents & Productivity | publicapi.dev | yes | https://publicapi.dev/ai-meeting-minutes-suite-api |
| Airtable | Documents & Productivity | publicapis.dev | unknown | https://publicapis.dev/resource/airtable/7b6ehnws |
| Airtable | Documents & Productivity | publicapi.dev | no | https://publicapi.dev/airtable-api |
| Api2Convert | Documents & Productivity | publicapis.dev | unknown | https://publicapis.dev/resource/api2convert/58zfv2lw |
| Api2Convert | Documents & Productivity | publicapi.dev | no | https://publicapi.dev/api2convert-api |
| apilayer pdflayer | Documents & Productivity | publicapi.dev | no | https://publicapi.dev/apilayer-pdflayer-api |
| Asana | Documents & Productivity | publicapis.dev | yes | https://publicapis.dev/resource/asana/hqx9h6hn |
| Asana | Documents & Productivity | publicapi.dev | yes | https://publicapi.dev/asana-api |
| Clipform | Documents & Productivity | publicapis.dev | no | https://publicapis.dev/resource/clipform/y4aox8xo |
| Clockify | Documents & Productivity | publicapis.dev | unknown | https://publicapis.dev/resource/clockify/lylljak6 |
| Clockify | Documents & Productivity | publicapi.dev | no | https://publicapi.dev/clockify-api |
| CloudConvert | Documents & Productivity | publicapis.dev | unknown | https://publicapis.dev/resource/cloudconvert/muvrzxzi |
| CloudConvert | Documents & Productivity | publicapi.dev | no | https://publicapi.dev/cloud-convert-api |
| Cloudmersive Document and Data Conversion | Documents & Productivity | publicapis.dev | yes | https://publicapis.dev/resource/cloudmersive-document-and-data-conversion/2rakp9v6 |
| Cloudmersive Document and Data Conversion | Documents & Productivity | publicapi.dev | yes | https://publicapi.dev/cloudmersive-document-and-data-conversion-api |
| Code::Stats | Documents & Productivity | publicapis.dev | no | https://publicapis.dev/resource/code-stats/3cndl5gi |
| Code::Stats | Documents & Productivity | publicapi.dev | no | https://publicapi.dev/code-stats-api |
| Convert HTML to PDF API | Documents & Productivity | publicapis.dev | yes | https://publicapis.dev/resource/convert-html-to-pdf-api/fc2ma23m |
| Convert HTML to PDF API | Documents & Productivity | publicapi.dev | yes | https://publicapi.dev/convert-html-to-pdf-api |
| Convert RSS to JSON | Documents & Productivity | publicapis.dev | yes | https://publicapis.dev/resource/convert-rss-to-json/o3s8e9gh |
| Convert RSS to JSON | Documents & Productivity | publicapi.dev | yes | https://publicapi.dev/convert-rss-to-json-api |
| Convert Speech to Text API | Documents & Productivity | publicapis.dev | yes | https://publicapis.dev/resource/convert-speech-to-text-api/i6apkg7z |
| Convert Speech to Text API | Documents & Productivity | publicapi.dev | yes | https://publicapi.dev/convert-speech-to-text-api |
| Convert Word to PDF | Documents & Productivity | publicapis.dev | yes | https://publicapis.dev/resource/convert-word-to-pdf/j4z220gv |
| Convert Word to PDF | Documents & Productivity | publicapi.dev | yes | https://publicapi.dev/convert-word-to-pdf-api |
| ConvertHub API | Documents & Productivity | publicapis.dev | no | https://publicapis.dev/resource/converthub-api/34pck5g8 |
| ConvertHub API | Documents & Productivity | publicapi.dev | no | https://publicapi.dev/convert-hub-api |
| CraftMyPDF | Documents & Productivity | publicapis.dev | no | https://publicapis.dev/resource/craftmypdf/ugw0vshb |
| CraftMyPDF | Documents & Productivity | publicapi.dev | no | https://publicapi.dev/craft-my-pdf-api |
| CustomJS | Documents & Productivity | publicapis.dev | unknown | https://publicapis.dev/resource/customjs/mhjzm4n5 |
| CustomJS | Documents & Productivity | publicapi.dev | no | https://publicapi.dev/custom-js-api |
| CV / Resume Parsing API | Documents & Productivity | publicapis.dev | yes | https://publicapis.dev/resource/cv---resume-parsing-api/mcakpgrd |
| CV / Resume Parsing API | Documents & Productivity | publicapi.dev | yes | https://publicapi.dev/cv-resume-parsing-api |
| DocSpring | Documents & Productivity | publicapis.dev | yes | https://publicapis.dev/resource/docspring/bvog50v7 |
| File Conversions API | Documents & Productivity | publicapis.dev | yes | https://publicapis.dev/resource/file-conversions-api/y6rzv4gr |
| File Conversions API | Documents & Productivity | publicapi.dev | yes | https://publicapi.dev/file-conversions-api |
| Flowdash | Documents & Productivity | publicapis.dev | unknown | https://publicapis.dev/resource/flowdash/gjo46ztb |
| Flowdash | Documents & Productivity | publicapi.dev | no | https://publicapi.dev/flowdash-api |
| fynk | Documents & Productivity | publicapis.dev | yes | https://publicapis.dev/resource/fynk/itammzjk |
| fynk | Documents & Productivity | publicapi.dev | yes | https://publicapi.dev/fynk-api |
| Html2PDF | Documents & Productivity | publicapis.dev | unknown | https://publicapis.dev/resource/html2pdf/vcd9hbkz |
| Html2PDF | Documents & Productivity | publicapi.dev | no | https://publicapi.dev/html2pdf-api |
| iLovePDF | Documents & Productivity | publicapis.dev | yes | https://publicapis.dev/resource/ilovepdf/1b6yy8kd |
| iLovePDF | Documents & Productivity | publicapi.dev | yes | https://publicapi.dev/i-love-pdf-api |
| Invoice Parsing API | Documents & Productivity | publicapis.dev | yes | https://publicapis.dev/resource/invoice-parsing-api/84crowvz |
| Invoice Parsing API | Documents & Productivity | publicapi.dev | yes | https://publicapi.dev/invoice-parsing-api |
| MConverter | Documents & Productivity | publicapis.dev | unknown | https://publicapis.dev/resource/mconverter/w8wfq6jg |
| MConverter | Documents & Productivity | publicapi.dev | no | https://publicapi.dev/m-converter-api |
| Mercury | Documents & Productivity | publicapi.dev | no | https://publicapi.dev/mercury-api |
| Monday | Documents & Productivity | publicapis.dev | unknown | https://publicapis.dev/resource/monday/1t7adkoa |
| Monday | Documents & Productivity | publicapi.dev | no | https://publicapi.dev/monday-api |
| Nutrient Data Extraction API | Documents & Productivity | publicapis.dev | unknown | https://publicapis.dev/resource/nutrient-data-extraction-api/lo4mdkyj |
| OCR AI | Documents & Productivity | publicapi.dev | no | https://publicapi.dev/ocr-ai-api |
| PandaDoc | Documents & Productivity | publicapis.dev | no | https://publicapis.dev/resource/pandadoc/li3eltiu |
| PandaDoc | Documents & Productivity | publicapi.dev | no | https://publicapi.dev/panda-doc-api |
| PaperClean | Documents & Productivity | publicapis.dev | yes | https://publicapis.dev/resource/paperclean/oihf6mkz |
| Paraphrase Text API | Documents & Productivity | publicapis.dev | yes | https://publicapis.dev/resource/paraphrase-text-api/y7xtekes |
| Paraphrase Text API | Documents & Productivity | publicapi.dev | yes | https://publicapi.dev/paraphrase-text-api |
| Pdfblocks | Documents & Productivity | publicapis.dev | yes | https://publicapis.dev/resource/pdfblocks/4xwkdwp8 |
| Pdfblocks | Documents & Productivity | publicapi.dev | yes | https://publicapi.dev/pdfblocks-api |
| PDFBolt | Documents & Productivity | publicapis.dev | yes | https://publicapis.dev/resource/pdfbolt/6r85em8d |
| PDFBolt | Documents & Productivity | publicapi.dev | yes | https://publicapi.dev/pdf-bolt-api |
| PDFEndpoint | Documents & Productivity | publicapis.dev | no | https://publicapis.dev/resource/pdfendpoint/nrn7jy3a |
| PDFEndpoint | Documents & Productivity | publicapi.dev | no | https://publicapi.dev/pdf-endpoint-api |
| PDFGate | Documents & Productivity | publicapis.dev | yes | https://publicapis.dev/resource/pdfgate/ot8fqix8 |
| PDFGate | Documents & Productivity | publicapi.dev | no | https://publicapi.dev/pdf-gate-api |
| PrexView | Documents & Productivity | publicapis.dev | unknown | https://publicapis.dev/resource/prexview/x1xzp97d |
| PrexView | Documents & Productivity | publicapi.dev | no | https://publicapi.dev/prex-view-api |
| RenderPDF.io | Documents & Productivity | publicapis.dev | yes | https://publicapis.dev/resource/renderpdf-io/d656f3xk |
| RenderPDF.io | Documents & Productivity | publicapi.dev | yes | https://publicapi.dev/render-pdf-io-api |
| Restpack | Documents & Productivity | publicapis.dev | unknown | https://publicapis.dev/resource/restpack/ocvkqvgv |
| Restpack | Documents & Productivity | publicapi.dev | no | https://publicapi.dev/restpack-api |
| Smart Image Enhancement API | Documents & Productivity | publicapi.dev | no | https://publicapi.dev/smart-image-enhancement-api |
| Vertopal | Documents & Productivity | publicapis.dev | no | https://publicapis.dev/resource/vertopal/bm92cddh |
| Vertopal | Documents & Productivity | publicapi.dev | no | https://publicapi.dev/vertopal-api |
| WAV to MP3 API | Documents & Productivity | publicapis.dev | yes | https://publicapis.dev/resource/wav-to-mp3-api/nyc3g5pt |
| WAV to MP3 API | Documents & Productivity | publicapi.dev | yes | https://publicapi.dev/wav-to-mp3-api |
| WebPDF | Documents & Productivity | publicapi.dev | no | https://publicapi.dev/web-pdf-api |
| Abstract Email Validation | Email | publicapis.dev | yes | https://publicapis.dev/resource/abstract-email-validation/zdlp9npp |
| Abstract Email Validation | Email | publicapi.dev | yes | https://publicapi.dev/abstract-email-validation-api |
| Academic Email API | Email | publicapis.dev | yes | https://publicapis.dev/resource/academic-email-api/q4gbs6re |
| Academic Email API | Email | publicapi.dev | yes | https://publicapi.dev/academic-email-api |
| apilayer mailboxlayer | Email | publicapi.dev | no | https://publicapi.dev/apilayer-mailboxlayer-api |
| Atomic Mail | Email | publicapis.dev | unknown | https://publicapis.dev/resource/atomic-mail/qbk9olxj |
| Cloudmersive Validate | Email | publicapis.dev | yes | https://publicapis.dev/resource/cloudmersive-validate/clag1cq7 |
| Cloudmersive Validate | Email | publicapi.dev | yes | https://publicapi.dev/cloudmersive-validate-api |
| Emailvalidation | Email | publicapis.dev | yes | https://publicapis.dev/resource/emailvalidation/53gx7tqp |
| Emailvalidation | Email | publicapi.dev | yes | https://publicapi.dev/emailvalidation-api |
| Enveloop | Email | publicapi.dev | yes | https://publicapi.dev/enveloop-api |
| FreeCustom.Email | Email | publicapis.dev | yes | https://publicapis.dev/resource/freecustom-email/sva0xebo |
| FreeCustom.Email | Email | publicapi.dev | yes | https://publicapi.dev/free-custom-email-api |
| ImprovMX | Email | publicapis.dev | unknown | https://publicapis.dev/resource/improvmx/0op6h1qe |
| ImprovMX | Email | publicapi.dev | no | https://publicapi.dev/improv-mx-api |
| IndiePitcher | Email | publicapis.dev | yes | https://publicapis.dev/resource/indiepitcher/6l372u08 |
| IndiePitcher | Email | publicapi.dev | yes | https://publicapi.dev/indie-pitcher-api |
| MailboxValidator | Email | publicapis.dev | unknown | https://publicapis.dev/resource/mailboxvalidator/tqy8xljz |
| MailboxValidator | Email | publicapi.dev | no | https://publicapi.dev/mailbox-validator-api |
| MailChannels | Email | publicapis.dev | no | https://publicapis.dev/resource/mailchannels/vxgpt103 |
| MailTester Ninja | Email | publicapis.dev | yes | https://publicapis.dev/resource/mailtester-ninja/a7fjvevz |
| Mailtrap | Email | publicapis.dev | unknown | https://publicapis.dev/resource/mailtrap/375a7zg6 |
| Mailtrap | Email | publicapi.dev | no | https://publicapi.dev/mailtrap-api |
| MailValid | Email | publicapis.dev | yes | https://publicapis.dev/resource/mailvalid/ue2h2nfy |
| NoParam | Email | publicapis.dev | unknown | https://publicapis.dev/resource/noparam/mf5qh40l |
| NoParam | Email | publicapi.dev | no | https://publicapi.dev/no-param-api |
| Notilify | Email | publicapis.dev | yes | https://publicapis.dev/resource/notilify/cy0ch5u8 |
| Notilify | Email | publicapi.dev | yes | https://publicapi.dev/notilify-api |
| Proweblook Email Checker | Email | publicapis.dev | yes | https://publicapis.dev/resource/proweblook-email-checker/hrfc3mg6 |
| Proweblook Email Checker | Email | publicapi.dev | yes | https://publicapi.dev/proweblook-email-checker-api |
| Resend | Email | publicapis.dev | yes | https://publicapis.dev/resource/resend/ppnciohf |
| Resend | Email | publicapi.dev | yes | https://publicapi.dev/resend-api |
| Sendgrid | Email | publicapis.dev | unknown | https://publicapis.dev/resource/sendgrid/qkf60rpw |
| Sendgrid | Email | publicapi.dev | no | https://publicapi.dev/sendgrid-api |
| Sendinblue | Email | publicapis.dev | unknown | https://publicapis.dev/resource/sendinblue/1vevga3p |
| Sendinblue | Email | publicapi.dev | no | https://publicapi.dev/sendinblue-api |
| Sweego | Email | publicapis.dev | yes | https://publicapis.dev/resource/sweego/wsp6ric9 |
| Sweego | Email | publicapi.dev | yes | https://publicapi.dev/sweego-api |
| Sweep Emails | Email | publicapis.dev | no | https://publicapis.dev/resource/sweep-emails/ti7se3mv |
| Sweep Emails | Email | publicapi.dev | no | https://publicapi.dev/sweep-emails-api |
| Temporary and Disposable emails API | Email | publicapis.dev | yes | https://publicapis.dev/resource/temporary-and-disposable-emails-api/lduzo2kx |
| Temporary and Disposable emails API | Email | publicapi.dev | yes | https://publicapi.dev/temporary-and-disposable-emails-api |
| Trueguard | Email | publicapis.dev | no | https://publicapis.dev/resource/trueguard/pdhnb2fp |
| Trueguard | Email | publicapi.dev | no | https://publicapi.dev/trueguard-api |
| Valid Email | Email | publicapi.dev | no | https://publicapi.dev/valid-email-api |
| Verifier | Email | publicapi.dev | yes | https://publicapi.dev/verifier-api |
| Zyntra | Email | publicapis.dev | unknown | https://publicapis.dev/resource/zyntra/yftx80m6 |
| Zyntra | Email | publicapi.dev | no | https://publicapi.dev/zyntra-api |
| Multilingual AI Zodiac | Entertainment | publicapis.dev | yes | https://publicapis.dev/resource/multilingual-ai-zodiac/udqjk9zj |
| Multilingual AI Zodiac | Entertainment | publicapi.dev | yes | https://publicapi.dev/multilingual-ai-zodiac-api |
| World Fun Facts (RapidAPI) | Entertainment | publicapis.dev | yes | https://publicapis.dev/resource/world-fun-facts-rapidapi-/j9ob39bd |
| World Fun Facts (RapidAPI) | Entertainment | publicapi.dev | yes | https://publicapi.dev/world-fun-facts-rapid-api |
| 1ClickImpact | Environment | publicapis.dev | yes | https://publicapis.dev/resource/1clickimpact/fqqjkfon |
| 1ClickImpact | Environment | publicapi.dev | yes | https://publicapi.dev/1click-impact-api |
| BreezoMeter Pollen | Environment | publicapis.dev | unknown | https://publicapis.dev/resource/breezometer-pollen/yf7v8scx |
| BreezoMeter Pollen | Environment | publicapi.dev | no | https://publicapi.dev/breezo-meter-pollen-api |
| Carbon Interface | Environment | publicapis.dev | yes | https://publicapis.dev/resource/carbon-interface/z6bbefs2 |
| Carbon Interface | Environment | publicapi.dev | yes | https://publicapi.dev/carbon-interface-api |
| Climatiq | Environment | publicapis.dev | yes | https://publicapis.dev/resource/climatiq/hwl1uwm7 |
| Climatiq | Environment | publicapi.dev | yes | https://publicapi.dev/climatiq-api |
| Cloverly | Environment | publicapis.dev | unknown | https://publicapis.dev/resource/cloverly/2oyc8fbo |
| Cloverly | Environment | publicapi.dev | no | https://publicapi.dev/cloverly-api |
| IQAir | Environment | publicapis.dev | unknown | https://publicapis.dev/resource/iqair/7rozfqb7 |
| IQAir | Environment | publicapi.dev | no | https://publicapi.dev/iq-air-api |
| OpenAQ | Environment | publicapis.dev | unknown | https://publicapis.dev/resource/openaq/cyfd1rfb |
| OpenAQ | Environment | publicapi.dev | no | https://publicapi.dev/open-aq-api |
| PM25.in | Environment | publicapi.dev | no | https://publicapi.dev/pm25-in-api |
| PVWatts | Environment | publicapi.dev | no | https://publicapi.dev/pv-watts-api |
| Srp Energy | Environment | publicapis.dev | no | https://publicapis.dev/resource/srp-energy/05pjijkr |
| Srp Energy | Environment | publicapi.dev | no | https://publicapi.dev/srp-energy-api |
| Thames Water Open Data | Environment | publicapis.dev | unknown | https://publicapis.dev/resource/thames-water-open-data/lkem8gc0 |
| Thames Water Open Data | Environment | publicapi.dev | no | https://publicapi.dev/thames-water-open-data-api |
| WattBuy | Environment | publicapis.dev | yes | https://publicapis.dev/resource/wattbuy/jv7jesd1 |
| WattBuy | Environment | publicapi.dev | yes | https://publicapi.dev/watt-buy-api |
| Website Carbon | Environment | publicapi.dev | yes | https://publicapi.dev/website-carbon-api |
| SeatGeek | Events | publicapis.dev | unknown | https://publicapis.dev/resource/seatgeek/7ex8w7m5 |
| SeatGeek | Events | publicapi.dev | no | https://publicapi.dev/seat-geek-api |
| Ticketmaster | Events | publicapis.dev | unknown | https://publicapis.dev/resource/ticketmaster/s6dwgvny |
| Ticketmaster | Events | publicapi.dev | no | https://publicapi.dev/ticketmaster-api |
| Abstract VAT Validation | Finance | publicapis.dev | yes | https://publicapis.dev/resource/abstract-vat-validation/581r8pgp |
| Abstract VAT Validation | Finance | publicapi.dev | yes | https://publicapi.dev/abstract-vat-validation-api |
| Adanos | Finance | publicapis.dev | yes | https://publicapis.dev/resource/adanos/l4rfqjn9 |
| Aegwah Actuals | Finance | publicapis.dev | no | https://publicapis.dev/resource/aegwah-actuals/ko7py7xa |
| Agent Toolbelt | Finance | publicapis.dev | unknown | https://publicapis.dev/resource/agent-toolbelt/ksrhrvxq |
| Aletheia | Finance | publicapis.dev | yes | https://publicapis.dev/resource/aletheia/f5vza2bi |
| Aletheia | Finance | publicapi.dev | yes | https://publicapi.dev/aletheia-api |
| Alpaca | Finance | publicapi.dev | yes | https://publicapi.dev/alpaca-api |
| Alpha Vantage | Finance | publicapis.dev | unknown | https://publicapis.dev/resource/alpha-vantage/xmko8i3y |
| Alpha Vantage | Finance | publicapi.dev | no | https://publicapi.dev/alpha-vantage-api |
| AlphaAI | Finance | publicapis.dev | unknown | https://publicapis.dev/resource/alphaai/ibf7iovr |
| apilayer marketstack | Finance | publicapi.dev | no | https://publicapi.dev/apilayer-marketstack-api |
| APIllow | Finance | publicapis.dev | yes | https://publicapis.dev/resource/apillow/s6pw952q |
| Atom Finance | Finance | publicapi.dev | yes | https://publicapi.dev/atom-finance-api |
| Bank Data API | Finance | publicapi.dev | no | https://publicapi.dev/bank-data-api |
| Billplz | Finance | publicapis.dev | unknown | https://publicapis.dev/resource/billplz/vwln0y6h |
| Billplz | Finance | publicapi.dev | no | https://publicapi.dev/billplz-api |
| Boleto.Cloud | Finance | publicapis.dev | unknown | https://publicapis.dev/resource/boleto-cloud/nofvcn1j |
| Boleto.Cloud | Finance | publicapi.dev | no | https://publicapi.dev/boleto-cloud-api |
| Citi | Finance | publicapis.dev | unknown | https://publicapis.dev/resource/citi/8f1bo8k2 |
| Citi | Finance | publicapi.dev | no | https://publicapi.dev/citi-api |
| Earnings Feed | Finance | publicapis.dev | no | https://publicapis.dev/resource/earnings-feed/2ntdwsdj |
| Earnings Feed | Finance | publicapi.dev | no | https://publicapi.dev/earnings-feed-api |
| eunormia E-Invoice API | Finance | publicapis.dev | unknown | https://publicapis.dev/resource/eunormia-e-invoice-api/2r090qx3 |
| Filingrail | Finance | publicapis.dev | unknown | https://publicapis.dev/resource/filingrail/rp7xj2wd |
| Finage | Finance | publicapis.dev | unknown | https://publicapis.dev/resource/finage/vfwc00j3 |
| Finage | Finance | publicapi.dev | no | https://publicapi.dev/finage-api |
| Financial Data | Finance | publicapis.dev | unknown | https://publicapis.dev/resource/financial-data/okei2uj8 |
| Financial Data | Finance | publicapi.dev | no | https://publicapi.dev/financial-data-api |
| Financial Modeling Prep | Finance | publicapis.dev | unknown | https://publicapis.dev/resource/financial-modeling-prep/mqpunage |
| Financial Modeling Prep | Finance | publicapi.dev | no | https://publicapi.dev/financial-modeling-prep-api |
| FinFeedAPI | Finance | publicapis.dev | unknown | https://publicapis.dev/resource/finfeedapi/vlz7xlke |
| FinFeedAPI | Finance | publicapi.dev | no | https://publicapi.dev/fin-feed-api |
| Finnhub | Finance | publicapis.dev | unknown | https://publicapis.dev/resource/finnhub/rz6hp2xz |
| Finnhub | Finance | publicapi.dev | no | https://publicapi.dev/finnhub-api |
| FinSignals | Finance | publicapis.dev | yes | https://publicapis.dev/resource/finsignals/cxok77kx |
| Finturest IBAN | Finance | publicapi.dev | no | https://publicapi.dev/finturest-iban-api |
| FRED | Finance | publicapis.dev | yes | https://publicapis.dev/resource/fred/lkp48qak |
| FRED | Finance | publicapi.dev | yes | https://publicapi.dev/fred-api |
| FXMacroData | Finance | publicapis.dev | yes | https://publicapis.dev/resource/fxmacrodata/yzay9olf |
| FXMacroData | Finance | publicapi.dev | yes | https://publicapi.dev/fx-macro-data-api |
| German Tax Calculator | Finance | publicapis.dev | no | https://publicapis.dev/resource/german-tax-calculator/ski6bhrh |
| Hotstoks | Finance | publicapi.dev | yes | https://publicapi.dev/hotstoks-api |
| IBANforge | Finance | publicapis.dev | no | https://publicapis.dev/resource/ibanforge/cvc2w4g2 |
| IEX Cloud | Finance | publicapis.dev | yes | https://publicapis.dev/resource/iex-cloud/ltmm9f85 |
| IEX Cloud | Finance | publicapi.dev | yes | https://publicapi.dev/iex-cloud-api |
| IG | Finance | publicapis.dev | unknown | https://publicapis.dev/resource/ig/3qr7eagh |
| IG | Finance | publicapi.dev | no | https://publicapi.dev/ig-api |
| IIN API | Finance | publicapis.dev | yes | https://publicapis.dev/resource/iin-api/vyom33uj |
| IIN API | Finance | publicapi.dev | yes | https://publicapi.dev/iin-api |
| Intrinio | Finance | publicapis.dev | unknown | https://publicapis.dev/resource/intrinio/tz6msdgn |
| Intrinio | Finance | publicapi.dev | no | https://publicapi.dev/intrinio-api |
| Kite Connect | Finance | publicapis.dev | no | https://publicapis.dev/resource/kite-connect/uogx40tu |
| Kite Connect | Finance | publicapi.dev | no | https://publicapi.dev/kite-connect-api |
| Klarna | Finance | publicapis.dev | unknown | https://publicapis.dev/resource/klarna/kbzxv6e8 |
| Klarna | Finance | publicapi.dev | no | https://publicapi.dev/klarna-api |
| MercadoPago | Finance | publicapis.dev | unknown | https://publicapis.dev/resource/mercadopago/y25rhhvp |
| MercadoPago | Finance | publicapi.dev | no | https://publicapi.dev/mercado-pago-api |
| Metal Sentinel | Finance | publicapis.dev | unknown | https://publicapis.dev/resource/metal-sentinel/4u9hfhit |
| Mono | Finance | publicapis.dev | unknown | https://publicapis.dev/resource/mono/fzd3eap2 |
| Mono | Finance | publicapi.dev | no | https://publicapi.dev/mono-api |
| Moov | Finance | publicapis.dev | unknown | https://publicapis.dev/resource/moov/dnzv8df1 |
| Moov | Finance | publicapi.dev | no | https://publicapi.dev/moov-api |
| Nasdaq Data Link | Finance | publicapis.dev | unknown | https://publicapis.dev/resource/nasdaq-data-link/pa9yvsej |
| Nordigen | Finance | publicapi.dev | no | https://publicapi.dev/nordigen-api |
| Open Bank Project | Finance | publicapis.dev | yes | https://publicapis.dev/resource/open-bank-project/kdvqizp2 |
| Open Bank Project | Finance | publicapi.dev | yes | https://publicapi.dev/open-bank-project-api |
| OpenFIGI | Finance | publicapis.dev | yes | https://publicapis.dev/resource/openfigi/s71tif4x |
| OpenFIGI | Finance | publicapi.dev | yes | https://publicapi.dev/open-figi-api |
| ParityVend | Finance | publicapis.dev | yes | https://publicapis.dev/resource/parityvend/5tvqbdiu |
| ParityVend | Finance | publicapi.dev | yes | https://publicapi.dev/parity-vend-api |
| Plaid | Finance | publicapis.dev | unknown | https://publicapis.dev/resource/plaid/cnlm8omn |
| Plaid | Finance | publicapi.dev | no | https://publicapi.dev/plaid-api |
| Polygon | Finance | publicapis.dev | unknown | https://publicapis.dev/resource/polygon/uo7ptbxk |
| Polygon | Finance | publicapi.dev | no | https://publicapi.dev/polygon-api |
| Polymarket | Finance | publicapis.dev | yes | https://publicapis.dev/resource/polymarket/kdiy0cnu |
| Real Time Finance | Finance | publicapis.dev | unknown | https://publicapis.dev/resource/real-time-finance/oydszro7 |
| Real Time Finance | Finance | publicapi.dev | no | https://publicapi.dev/real-time-finance-api |
| Realie Property Data API | Finance | publicapis.dev | no | https://publicapis.dev/resource/realie-property-data-api/jk0qblhe |
| Realie Property Data API | Finance | publicapi.dev | no | https://publicapi.dev/realie-property-data-api |
| RentCast | Finance | publicapis.dev | yes | https://publicapis.dev/resource/rentcast/3e4c618x |
| RentCast | Finance | publicapi.dev | yes | https://publicapi.dev/rent-cast-api |
| Repetiti | Finance | publicapi.dev | yes | https://publicapi.dev/repetiti-api |
| Salary API | Finance | publicapis.dev | yes | https://publicapis.dev/resource/salary-api/5g4f5pw7 |
| SiftingIO | Finance | publicapis.dev | yes | https://publicapis.dev/resource/siftingio/db32gicd |
| SmartAPI | Finance | publicapis.dev | unknown | https://publicapis.dev/resource/smartapi/6etgsj8v |
| SmartAPI | Finance | publicapi.dev | no | https://publicapi.dev/smart-api |
| StockData | Finance | publicapis.dev | yes | https://publicapis.dev/resource/stockdata/vuyf6xzx |
| StockData | Finance | publicapi.dev | yes | https://publicapi.dev/stock-data-api |
| Stripe | Finance | publicapis.dev | unknown | https://publicapis.dev/resource/stripe/djw2cebb |
| Stripe | Finance | publicapi.dev | no | https://publicapi.dev/stripe-api |
| Styvio | Finance | publicapi.dev | no | https://publicapi.dev/styvio-api |
| Sugra API | Finance | publicapis.dev | yes | https://publicapis.dev/resource/sugra-api/9p4apcah |
| Sugra API | Finance | publicapi.dev | yes | https://publicapi.dev/sugra-api |
| Svensk Skatte-API | Finance | publicapis.dev | unknown | https://publicapis.dev/resource/svensk-skatte-api/otr9kmyj |
| Tax Data API | Finance | publicapi.dev | no | https://publicapi.dev/tax-data-api |
| TradeWatch | Finance | publicapis.dev | yes | https://publicapis.dev/resource/tradewatch/tnsovibn |
| TradeWatch | Finance | publicapi.dev | yes | https://publicapi.dev/trade-watch-api |
| Twelve Data | Finance | publicapis.dev | unknown | https://publicapis.dev/resource/twelve-data/a2d6lgjq |
| Twelve Data | Finance | publicapi.dev | no | https://publicapi.dev/twelve-data-api |
| Validate IBAN API | Finance | publicapis.dev | yes | https://publicapis.dev/resource/validate-iban-api/gi3uo8fc |
| Validate IBAN API | Finance | publicapi.dev | yes | https://publicapi.dev/validate-iban-api |
| Validate Swift/BIC | Finance | publicapis.dev | yes | https://publicapis.dev/resource/validate-swift-bic/vv88cgcr |
| Validate Swift/BIC | Finance | publicapi.dev | yes | https://publicapi.dev/validate-swift-bic-api |
| WealthTools \| Personal Finance API | Finance | publicapis.dev | yes | https://publicapis.dev/resource/wealthtools-personal-finance-api/sf91urak |
| xChangeApi | Finance | publicapis.dev | yes | https://publicapis.dev/resource/xchangeapi/aaxwaaby |
| xChangeApi | Finance | publicapi.dev | yes | https://publicapi.dev/x-change-api |
| Yahoo Finance | Finance | publicapi.dev | yes | https://publicapi.dev/yahoo-finance-api |
| Chomp | Food & Drink | publicapis.dev | unknown | https://publicapis.dev/resource/chomp/a0bcyk90 |
| Chomp | Food & Drink | publicapi.dev | no | https://publicapi.dev/chomp-api |
| DietlyAPI | Food & Drink | publicapis.dev | yes | https://publicapis.dev/resource/dietlyapi/8ph9ngem |
| Edamam nutrition | Food & Drink | publicapis.dev | unknown | https://publicapis.dev/resource/edamam-nutrition/8dvxkxhg |
| Edamam nutrition | Food & Drink | publicapi.dev | no | https://publicapi.dev/edamam-nutrition-api |
| Edamam recipes | Food & Drink | publicapis.dev | unknown | https://publicapis.dev/resource/edamam-recipes/5antqo1k |
| Edamam recipes | Food & Drink | publicapi.dev | no | https://publicapi.dev/edamam-recipes-api |
| Kroger | Food & Drink | publicapis.dev | unknown | https://publicapis.dev/resource/kroger/bcg9pkee |
| Kroger | Food & Drink | publicapi.dev | no | https://publicapi.dev/kroger-api |
| RecipeAPI | Food & Drink | publicapis.dev | yes | https://publicapis.dev/resource/recipeapi/zfucuda1 |
| Spoonacular | Food & Drink | publicapis.dev | unknown | https://publicapis.dev/resource/spoonacular/s3q8hfn4 |
| Spoonacular | Food & Drink | publicapi.dev | no | https://publicapi.dev/spoonacular-api |
| Systembolaget | Food & Drink | publicapi.dev | no | https://publicapi.dev/systembolaget-api |
| Tasty | Food & Drink | publicapis.dev | unknown | https://publicapis.dev/resource/tasty/3gwqg7zr |
| Tasty | Food & Drink | publicapi.dev | no | https://publicapi.dev/tasty-api |
| TheCocktailDB | Food & Drink | publicapis.dev | yes | https://publicapis.dev/resource/thecocktaildb/cxfkfqcn |
| TheCocktailDB | Food & Drink | publicapi.dev | yes | https://publicapi.dev/the-cocktail-db-api |
| TheMealDB | Food & Drink | publicapis.dev | yes | https://publicapis.dev/resource/themealdb/47cv7893 |
| TheMealDB | Food & Drink | publicapi.dev | yes | https://publicapi.dev/the-meal-db-api |
| What&#x27;s on the menu? | Food & Drink | publicapis.dev | unknown | https://publicapis.dev/resource/what-s-on-the-menu-/4xxnz6ha |
| What&#x27;s on the menu? | Food & Drink | publicapi.dev | no | https://publicapi.dev/what-s-on-the-menu-api |
| Your Move - Nutrition API | Food & Drink | publicapis.dev | yes | https://publicapis.dev/resource/your-move-nutrition-api/qky4tet8 |
| Zestful | Food & Drink | publicapis.dev | yes | https://publicapis.dev/resource/zestful/ezzjx8kr |
| Zestful | Food & Drink | publicapi.dev | yes | https://publicapi.dev/zestful-api |
| Brawl Stars | Games & Comics | publicapis.dev | unknown | https://publicapis.dev/resource/brawl-stars/7xq7odwi |
| Brawl Stars | Games & Comics | publicapi.dev | no | https://publicapi.dev/brawl-stars-api |
| Clash of Clans | Games & Comics | publicapis.dev | unknown | https://publicapis.dev/resource/clash-of-clans/0l2rjesv |
| Clash of Clans | Games & Comics | publicapi.dev | no | https://publicapi.dev/clash-of-clans-api |
| Clash Royale | Games & Comics | publicapis.dev | unknown | https://publicapis.dev/resource/clash-royale/nqidzps2 |
| Clash Royale | Games & Comics | publicapi.dev | no | https://publicapi.dev/clash-royale-api |
| Destiny The Game | Games & Comics | publicapis.dev | unknown | https://publicapis.dev/resource/destiny-the-game/soxozyoj |
| Destiny The Game | Games & Comics | publicapi.dev | no | https://publicapi.dev/destiny-the-game-api |
| Dota 2 | Games & Comics | publicapis.dev | unknown | https://publicapis.dev/resource/dota-2/jscatt27 |
| Dota 2 | Games & Comics | publicapi.dev | no | https://publicapi.dev/dota-2-api |
| Fortnite | Games & Comics | publicapi.dev | no | https://publicapi.dev/fortnite-api |
| Game Brain | Games & Comics | publicapis.dev | yes | https://publicapis.dev/resource/game-brain/3prsg5ck |
| Game Brain | Games & Comics | publicapi.dev | yes | https://publicapi.dev/game-brain-api |
| Giant Bomb | Games & Comics | publicapis.dev | unknown | https://publicapis.dev/resource/giant-bomb/zqpla6wq |
| Giant Bomb | Games & Comics | publicapi.dev | no | https://publicapi.dev/giant-bomb-api |
| Guild Wars 2 | Games & Comics | publicapis.dev | unknown | https://publicapis.dev/resource/guild-wars-2/knrgit5i |
| Guild Wars 2 | Games & Comics | publicapi.dev | no | https://publicapi.dev/guild-wars-2-api |
| Halo | Games & Comics | publicapis.dev | unknown | https://publicapis.dev/resource/halo/vvmnwjyn |
| Halo | Games & Comics | publicapi.dev | no | https://publicapi.dev/halo-api |
| Hearthstone | Games & Comics | publicapi.dev | no | https://publicapi.dev/hearthstone-api |
| Humble Bundle | Games & Comics | publicapis.dev | unknown | https://publicapis.dev/resource/humble-bundle/xba1u7ib |
| Humble Bundle | Games & Comics | publicapi.dev | no | https://publicapi.dev/humble-bundle-api |
| Humor | Games & Comics | publicapis.dev | unknown | https://publicapis.dev/resource/humor/jf4plwn9 |
| Humor | Games & Comics | publicapi.dev | no | https://publicapi.dev/humor-api |
| Hypixel | Games & Comics | publicapis.dev | unknown | https://publicapis.dev/resource/hypixel/y6466mdk |
| Hypixel | Games & Comics | publicapi.dev | no | https://publicapi.dev/hypixel-api |
| iGaming Tools | Games & Comics | publicapi.dev | no | https://publicapi.dev/i-gaming-tools-api |
| IGDB.com | Games & Comics | publicapis.dev | unknown | https://publicapis.dev/resource/igdb-com/k3xxwpr8 |
| IGDB.com | Games & Comics | publicapi.dev | no | https://publicapi.dev/igdb-com-api |
| Jokes One | Games & Comics | publicapis.dev | yes | https://publicapis.dev/resource/jokes-one/yrdp9c5o |
| Jokes One | Games & Comics | publicapi.dev | yes | https://publicapi.dev/jokes-one-api |
| KrakenKeys API - Free Steam game price comparison API | Games & Comics | publicapis.dev | no | https://publicapis.dev/resource/krakenkeys-api-free-steam-game-price-comparison-api/em6fay9j |
| Marvel | Games & Comics | publicapi.dev | no | https://publicapi.dev/marvel-api |
| mod.io | Games & Comics | publicapis.dev | unknown | https://publicapis.dev/resource/mod-io/8csa5gfi |
| mod.io | Games & Comics | publicapi.dev | no | https://publicapi.dev/mod-io-api |
| Mojang | Games & Comics | publicapi.dev | no | https://publicapi.dev/mojang-api |
| Multilingual Truth or Dare Game | Games & Comics | publicapis.dev | yes | https://publicapis.dev/resource/multilingual-truth-or-dare-game/h228r3nu |
| Multilingual Truth or Dare Game | Games & Comics | publicapi.dev | yes | https://publicapi.dev/multilingual-truth-or-dare-game-api |
| Nookipedia | Games & Comics | publicapis.dev | unknown | https://publicapis.dev/resource/nookipedia/35f1932f |
| PandaScore | Games & Comics | publicapis.dev | unknown | https://publicapis.dev/resource/pandascore/98kquxp2 |
| PandaScore | Games & Comics | publicapi.dev | no | https://publicapi.dev/panda-score-api |
| Poker Odds Equity EV Calculator &amp; Advice | Games & Comics | publicapis.dev | yes | https://publicapis.dev/resource/poker-odds-equity-ev-calculator-and-advice/atesgbp8 |
| Poker Odds Equity EV Calculator &amp; Advice | Games & Comics | publicapi.dev | yes | https://publicapi.dev/poker-odds-equity-ev-calculator-advice-api |
| PUBG | Games & Comics | publicapis.dev | yes | https://publicapis.dev/resource/pubg/i8rvqhoc |
| PUBG | Games & Comics | publicapi.dev | yes | https://publicapi.dev/pubg-api |
| quizapi.io | Games & Comics | publicapis.dev | yes | https://publicapis.dev/resource/quizapi-io/ipvbqbz5 |
| quizapi.io | Games & Comics | publicapi.dev | yes | https://publicapi.dev/quizapi-io-api |
| QuizBase | Games & Comics | publicapis.dev | yes | https://publicapis.dev/resource/quizbase/yg36jian |
| RAWG.io | Games & Comics | publicapis.dev | unknown | https://publicapis.dev/resource/rawg-io/x92sfrud |
| RAWG.io | Games & Comics | publicapi.dev | no | https://publicapi.dev/rawg-io-api |
| Riot Games | Games & Comics | publicapis.dev | unknown | https://publicapis.dev/resource/riot-games/a7elz00b |
| Riot Games | Games & Comics | publicapi.dev | no | https://publicapi.dev/riot-games-api |
| Steam | Games & Comics | publicapis.dev | no | https://publicapis.dev/resource/steam/pnt0jcse |
| SuperHeroes | Games & Comics | publicapi.dev | no | https://publicapi.dev/super-heroes-api |
| TCGApi | Games & Comics | publicapis.dev | no | https://publicapis.dev/resource/tcgapi/hy0okjtc |
| Tebex | Games & Comics | publicapis.dev | no | https://publicapis.dev/resource/tebex/19klw2xg |
| Tebex | Games & Comics | publicapi.dev | no | https://publicapi.dev/tebex-api |
| Wargaming.net | Games & Comics | publicapis.dev | no | https://publicapis.dev/resource/wargaming-net/flsjdx45 |
| Wargaming.net | Games & Comics | publicapi.dev | no | https://publicapi.dev/wargaming-net-api |
| Abstract IP Geolocation | Geocoding | publicapis.dev | yes | https://publicapis.dev/resource/abstract-ip-geolocation/np8jjh89 |
| Abstract IP Geolocation | Geocoding | publicapi.dev | yes | https://publicapi.dev/abstract-ip-geolocation-api |
| Actinia Grass GIS | Geocoding | publicapis.dev | unknown | https://publicapis.dev/resource/actinia-grass-gis/55whp0og |
| Actinia Grass GIS | Geocoding | publicapi.dev | no | https://publicapi.dev/actinia-grass-gis-api |
| Apiip | Geocoding | publicapis.dev | yes | https://publicapis.dev/resource/apiip/6iwtmiol |
| Apiip | Geocoding | publicapi.dev | yes | https://publicapi.dev/apiip-api |
| apilayer ipstack | Geocoding | publicapi.dev | no | https://publicapi.dev/apilayer-ipstack-api |
| APlace.io | Geocoding | publicapi.dev | yes | https://publicapi.dev/a-place-io-api |
| Battuta | Geocoding | publicapi.dev | no | https://publicapi.dev/battuta-api |
| BigDataCloud | Geocoding | publicapi.dev | no | https://publicapi.dev/big-data-cloud-api |
| BigDataCloud&#x27;s IP Geolocation API | Geocoding | publicapis.dev | yes | https://publicapis.dev/resource/bigdatacloud-s-ip-geolocation-api/fagoii3u |
| BigDataCloud&#x27;s IP Geolocation API | Geocoding | publicapi.dev | yes | https://publicapi.dev/big-data-cloud-s-ip-geolocation-api |
| BigDataCloud&#x27;s Network Engineering API | Geocoding | publicapis.dev | yes | https://publicapis.dev/resource/bigdatacloud-s-network-engineering-api/01km0kdc |
| BigDataCloud&#x27;s Network Engineering API | Geocoding | publicapi.dev | yes | https://publicapi.dev/big-data-cloud-s-network-engineering-api |
| BigDataCloud&#x27;s Phone and Email Verification API | Geocoding | publicapis.dev | yes | https://publicapis.dev/resource/bigdatacloud-s-phone-and-email-verification-api/gas2o77p |
| BigDataCloud&#x27;s Phone and Email Verification API | Geocoding | publicapi.dev | yes | https://publicapi.dev/big-data-cloud-s-phone-and-email-verification-api |
| BigDataCloud&#x27;s Reverse Geocoding API | Geocoding | publicapis.dev | yes | https://publicapis.dev/resource/bigdatacloud-s-reverse-geocoding-api/vczuszx6 |
| BigDataCloud&#x27;s Reverse Geocoding API | Geocoding | publicapi.dev | yes | https://publicapi.dev/big-data-cloud-s-reverse-geocoding-api |
| Bing Maps | Geocoding | publicapis.dev | unknown | https://publicapis.dev/resource/bing-maps/b81oozwv |
| Bing Maps | Geocoding | publicapi.dev | no | https://publicapi.dev/bing-maps-api |
| CountryStateCity | Geocoding | publicapis.dev | yes | https://publicapis.dev/resource/countrystatecity/vnlf3on6 |
| CountryStateCity | Geocoding | publicapi.dev | yes | https://publicapi.dev/country-state-city-api |
| Geoapify | Geocoding | publicapis.dev | yes | https://publicapis.dev/resource/geoapify/th8p7nc8 |
| Geoapify | Geocoding | publicapi.dev | yes | https://publicapi.dev/geoapify-api |
| Geocod.io | Geocoding | publicapis.dev | unknown | https://publicapis.dev/resource/geocod-io/8zmpmfqa |
| Geocod.io | Geocoding | publicapi.dev | no | https://publicapi.dev/geocod-io-api |
| Geocodify.com | Geocoding | publicapis.dev | yes | https://publicapis.dev/resource/geocodify-com/jgpeeqi7 |
| Geocodify.com | Geocoding | publicapi.dev | yes | https://publicapi.dev/geocodify-com-api |
| GeoDataSource | Geocoding | publicapis.dev | unknown | https://publicapis.dev/resource/geodatasource/q3ak3z65 |
| GeoDataSource | Geocoding | publicapi.dev | no | https://publicapi.dev/geo-data-source-api |
| GeoDB Cities | Geocoding | publicapi.dev | no | https://publicapi.dev/geo-db-cities-api |
| Geolocated | Geocoding | publicapis.dev | yes | https://publicapis.dev/resource/geolocated/7zq3dvg4 |
| Geolocated | Geocoding | publicapi.dev | yes | https://publicapi.dev/geolocated-api |
| Google Earth Engine | Geocoding | publicapis.dev | unknown | https://publicapis.dev/resource/google-earth-engine/6rkeo4bv |
| Google Earth Engine | Geocoding | publicapi.dev | no | https://publicapi.dev/google-earth-engine-api |
| Google Maps | Geocoding | publicapis.dev | unknown | https://publicapis.dev/resource/google-maps/t8di0tgu |
| Google Maps | Geocoding | publicapi.dev | no | https://publicapi.dev/google-maps-api |
| HERE Maps | Geocoding | publicapis.dev | unknown | https://publicapis.dev/resource/here-maps/7agec5pa |
| HERE Maps | Geocoding | publicapi.dev | no | https://publicapi.dev/here-maps-api |
| Hirak IP to Country | Geocoding | publicapi.dev | no | https://publicapi.dev/hirak-ip-to-country-api |
| IP Geolocation API by IPWhois.io | Geocoding | publicapi.dev | yes | https://publicapi.dev/ip-geolocation-api-by-ip-whois-io-api |
| IP2Location | Geocoding | publicapis.dev | unknown | https://publicapis.dev/resource/ip2location/js44cn3z |
| IP2Location | Geocoding | publicapi.dev | no | https://publicapi.dev/ip2location-api |
| IP2Location.io | Geocoding | publicapis.dev | unknown | https://publicapis.dev/resource/ip2location-io/vta63dru |
| IP2Location.io | Geocoding | publicapi.dev | no | https://publicapi.dev/ip2location-io-api |
| IP2Proxy | Geocoding | publicapis.dev | unknown | https://publicapis.dev/resource/ip2proxy/t2mivl24 |
| IP2Proxy | Geocoding | publicapi.dev | no | https://publicapi.dev/ip2proxy-api |
| ipapi.com | Geocoding | publicapi.dev | no | https://publicapi.dev/ipapi-com-api |
| ipgeolocation | Geocoding | publicapi.dev | yes | https://publicapi.dev/ipgeolocation-api |
| IPGeolocation | Geocoding | publicapis.dev | yes | https://publicapis.dev/resource/ipgeolocation/ffaajilu |
| IPInfoDB | Geocoding | publicapis.dev | unknown | https://publicapis.dev/resource/ipinfodb/mv6esfz4 |
| IPInfoDB | Geocoding | publicapi.dev | no | https://publicapi.dev/ip-info-db-api |
| ipstack | Geocoding | publicapi.dev | no | https://publicapi.dev/ipstack-api |
| IPWho | Geocoding | publicapi.dev | yes | https://publicapi.dev/ip-who-api |
| IPWho IP Geolocation API | Geocoding | publicapis.dev | yes | https://publicapis.dev/resource/ipwho-ip-geolocation-api/qhw2y8hp |
| IPWhois | Geocoding | publicapis.dev | yes | https://publicapis.dev/resource/ipwhois/y9ujg8uz |
| Kakao Maps | Geocoding | publicapis.dev | unknown | https://publicapis.dev/resource/kakao-maps/xwja5yaj |
| Kakao Maps | Geocoding | publicapi.dev | no | https://publicapi.dev/kakao-maps-api |
| keycdn IP Location Finder | Geocoding | publicapis.dev | unknown | https://publicapis.dev/resource/keycdn-ip-location-finder/m9rs2izd |
| keycdn IP Location Finder | Geocoding | publicapi.dev | no | https://publicapi.dev/keycdn-ip-location-finder-api |
| LocationIQ | Geocoding | publicapis.dev | yes | https://publicapis.dev/resource/locationiq/thwh5iv9 |
| LocationIQ | Geocoding | publicapi.dev | yes | https://publicapi.dev/location-iq-api |
| Longdo Map | Geocoding | publicapis.dev | yes | https://publicapis.dev/resource/longdo-map/f539wzsk |
| Longdo Map | Geocoding | publicapi.dev | yes | https://publicapi.dev/longdo-map-api |
| Mapbase | Geocoding | publicapis.dev | yes | https://publicapis.dev/resource/mapbase/xqwetfam |
| Mapbox | Geocoding | publicapis.dev | unknown | https://publicapis.dev/resource/mapbox/x6qq5xrn |
| Mapbox | Geocoding | publicapi.dev | no | https://publicapi.dev/mapbox-api |
| MapQuest | Geocoding | publicapis.dev | no | https://publicapis.dev/resource/mapquest/exk9fdbj |
| MapQuest | Geocoding | publicapi.dev | no | https://publicapi.dev/map-quest-api |
| Metadapi Zip Code | Geocoding | publicapis.dev | yes | https://publicapis.dev/resource/metadapi-zip-code/qshxo7o1 |
| One Map, Singapore | Geocoding | publicapis.dev | unknown | https://publicapis.dev/resource/one-map-singapore/5kpfgqsv |
| One Map, Singapore | Geocoding | publicapi.dev | no | https://publicapi.dev/one-map-singapore-api |
| OpenCage | Geocoding | publicapis.dev | yes | https://publicapis.dev/resource/opencage/1s69ole9 |
| OpenCage | Geocoding | publicapi.dev | yes | https://publicapi.dev/open-cage-api |
| openrouteservice.org | Geocoding | publicapis.dev | unknown | https://publicapis.dev/resource/openrouteservice-org/4dnjffbg |
| openrouteservice.org | Geocoding | publicapi.dev | no | https://publicapi.dev/openrouteservice-org-api |
| Pointpin | Geocoding | publicapis.dev | yes | https://publicapis.dev/resource/pointpin/spb5vqw8 |
| positionstack | Geocoding | publicapi.dev | no | https://publicapi.dev/positionstack-api |
| Proweblook IP Checker | Geocoding | publicapis.dev | yes | https://publicapis.dev/resource/proweblook-ip-checker/qilx7vak |
| Proweblook IP Checker | Geocoding | publicapi.dev | yes | https://publicapi.dev/proweblook-ip-checker-api |
| RoadGoat Cities | Geocoding | publicapi.dev | no | https://publicapi.dev/road-goat-cities-api |
| SpotSense | Geocoding | publicapi.dev | no | https://publicapi.dev/spot-sense-api |
| Telize | Geocoding | publicapis.dev | yes | https://publicapis.dev/resource/telize/qj7qz1gn |
| Telize | Geocoding | publicapi.dev | yes | https://publicapi.dev/telize-api |
| The IP API | Geocoding | publicapis.dev | unknown | https://publicapis.dev/resource/the-ip-api/dsq7be2i |
| Timezones Dictionary API | Geocoding | publicapis.dev | yes | https://publicapis.dev/resource/timezones-dictionary-api/kacjbep3 |
| Timezones Dictionary API | Geocoding | publicapi.dev | yes | https://publicapi.dev/timezones-dictionary-api |
| TomTom | Geocoding | publicapis.dev | yes | https://publicapis.dev/resource/tomtom/ysk3ckwv |
| TomTom | Geocoding | publicapi.dev | yes | https://publicapi.dev/tom-tom-api |
| Uebermaps | Geocoding | publicapis.dev | unknown | https://publicapis.dev/resource/uebermaps/2d9v175y |
| Uebermaps | Geocoding | publicapi.dev | no | https://publicapi.dev/uebermaps-api |
| US ZipCode | Geocoding | publicapis.dev | yes | https://publicapis.dev/resource/us-zipcode/qqmstou7 |
| US ZipCode | Geocoding | publicapi.dev | yes | https://publicapi.dev/us-zip-code-api |
| Utah AGRC | Geocoding | publicapis.dev | unknown | https://publicapis.dev/resource/utah-agrc/uad2smec |
| Utah AGRC | Geocoding | publicapi.dev | no | https://publicapi.dev/utah-agrc-api |
| What3Words | Geocoding | publicapis.dev | unknown | https://publicapis.dev/resource/what3words/fye1ac52 |
| What3Words | Geocoding | publicapi.dev | no | https://publicapi.dev/what3words-api |
| Yandex.Maps Geocoder | Geocoding | publicapis.dev | unknown | https://publicapis.dev/resource/yandex-maps-geocoder/phqr3s81 |
| Yandex.Maps Geocoder | Geocoding | publicapi.dev | no | https://publicapi.dev/yandex-maps-geocoder-api |
| ZipCodeAPI | Geocoding | publicapis.dev | unknown | https://publicapis.dev/resource/zipcodeapi/v7kcuv0n |
| ZipCodeAPI | Geocoding | publicapi.dev | no | https://publicapi.dev/zip-code-api |
| Zipcodestack | Geocoding | publicapis.dev | unknown | https://publicapis.dev/resource/zipcodestack/s6h9yo90 |
| Zipcodestack | Geocoding | publicapi.dev | no | https://publicapi.dev/zipcodestack-api |
| Apiverket | Government | publicapis.dev | yes | https://publicapis.dev/resource/apiverket/imtkr3la |
| BuildData | Government | publicapis.dev | unknown | https://publicapis.dev/resource/builddata/0umd179e |
| City, Nantes Open Data | Government | publicapis.dev | unknown | https://publicapis.dev/resource/city-nantes-open-data/zsamzp3d |
| City, Nantes Open Data | Government | publicapi.dev | no | https://publicapi.dev/city-nantes-open-data-api |
| Code.gov | Government | publicapis.dev | unknown | https://publicapis.dev/resource/code-gov/eipldbpd |
| Code.gov | Government | publicapi.dev | no | https://publicapi.dev/code-gov-api |
| Conversor IAE CNAE | Government | publicapis.dev | no | https://publicapis.dev/resource/conversor-iae-cnae/59ruhni0 |
| CourtMesh | Government | publicapis.dev | no | https://publicapis.dev/resource/courtmesh/usi3rzul |
| Data.gov | Government | publicapis.dev | unknown | https://publicapis.dev/resource/data-gov/v176ad97 |
| Data.gov | Government | publicapi.dev | no | https://publicapi.dev/data-gov-api |
| Deutscher Bundestag DIP | Government | publicapis.dev | unknown | https://publicapis.dev/resource/deutscher-bundestag-dip/a74wyf15 |
| Deutscher Bundestag DIP | Government | publicapi.dev | no | https://publicapi.dev/deutscher-bundestag-dip-api |
| FEC | Government | publicapis.dev | unknown | https://publicapis.dev/resource/fec/zqjrch87 |
| FEC | Government | publicapi.dev | no | https://publicapi.dev/fec-api |
| Gun Policy | Government | publicapi.dev | no | https://publicapi.dev/gun-policy-api |
| National Park Service, US | Government | publicapis.dev | yes | https://publicapis.dev/resource/national-park-service-us/osjl8nfq |
| National Park Service, US | Government | publicapi.dev | yes | https://publicapi.dev/national-park-service-us-api |
| Open Government, Estonia | Government | publicapis.dev | unknown | https://publicapis.dev/resource/open-government-estonia/jsedsnlj |
| Open Government, Estonia | Government | publicapi.dev | no | https://publicapi.dev/open-government-estonia-api |
| Open Government, France | Government | publicapis.dev | unknown | https://publicapis.dev/resource/open-government-france/wq9u6urc |
| Open Government, France | Government | publicapi.dev | no | https://publicapi.dev/open-government-france-api |
| Open Government, India | Government | publicapis.dev | unknown | https://publicapis.dev/resource/open-government-india/3ufi6rt1 |
| Open Government, India | Government | publicapi.dev | no | https://publicapi.dev/open-government-india-api |
| Open Government, Korea | Government | publicapis.dev | unknown | https://publicapis.dev/resource/open-government-korea/953lip01 |
| Open Government, Korea | Government | publicapi.dev | no | https://publicapi.dev/open-government-korea-api |
| Open Government, Luxembourg | Government | publicapis.dev | unknown | https://publicapis.dev/resource/open-government-luxembourg/4w5g962z |
| Open Government, Luxembourg | Government | publicapi.dev | no | https://publicapi.dev/open-government-luxembourg-api |
| Open Government, New South Wales | Government | publicapis.dev | unknown | https://publicapis.dev/resource/open-government-new-south-wales/sp8qscog |
| Open Government, New South Wales | Government | publicapi.dev | no | https://publicapi.dev/open-government-new-south-wales-api |
| Open Government, Portugal | Government | publicapis.dev | yes | https://publicapis.dev/resource/open-government-portugal/xnqeaeps |
| Open Government, Portugal | Government | publicapi.dev | yes | https://publicapi.dev/open-government-portugal-api |
| Open Government, Thailand | Government | publicapis.dev | unknown | https://publicapis.dev/resource/open-government-thailand/cge8d3un |
| Open Government, Thailand | Government | publicapi.dev | no | https://publicapi.dev/open-government-thailand-api |
| PolitiData | Government | publicapis.dev | unknown | https://publicapis.dev/resource/politidata/45luhhtw |
| ProcureData | Government | publicapis.dev | unknown | https://publicapis.dev/resource/procuredata/mr25mmpv |
| USA.gov | Government | publicapi.dev | no | https://publicapi.dev/usa-gov-api |
| Atmospore Pollen forecasts | Health | publicapis.dev | unknown | https://publicapis.dev/resource/atmospore-pollen-forecasts/mnbeote6 |
| CMS.gov | Health | publicapis.dev | unknown | https://publicapis.dev/resource/cms-gov/ezocp2es |
| CMS.gov | Health | publicapi.dev | no | https://publicapi.dev/cms-gov-api |
| FoodData Central | Health | publicapis.dev | unknown | https://publicapis.dev/resource/fooddata-central/6wa6bckg |
| FoodData Central | Health | publicapi.dev | no | https://publicapi.dev/food-data-central-api |
| Infermedica | Health | publicapis.dev | yes | https://publicapis.dev/resource/infermedica/o17xobjd |
| Infermedica | Health | publicapi.dev | yes | https://publicapi.dev/infermedica-api |
| Lexigram | Health | publicapis.dev | unknown | https://publicapis.dev/resource/lexigram/ndnroqew |
| Lexigram | Health | publicapi.dev | no | https://publicapi.dev/lexigram-api |
| Nutritionix | Health | publicapis.dev | unknown | https://publicapis.dev/resource/nutritionix/qjhq5i4w |
| Nutritionix | Health | publicapi.dev | no | https://publicapi.dev/nutritionix-api |
| openFDA | Health | publicapis.dev | unknown | https://publicapis.dev/resource/openfda/xh1snrc4 |
| openFDA | Health | publicapi.dev | no | https://publicapi.dev/open-fda-api |
| Adzuna | Jobs | publicapis.dev | unknown | https://publicapis.dev/resource/adzuna/zfo37r23 |
| Adzuna | Jobs | publicapi.dev | no | https://publicapi.dev/adzuna-api |
| Careerjet | Jobs | publicapis.dev | unknown | https://publicapis.dev/resource/careerjet/aqnoragq |
| Careerjet | Jobs | publicapi.dev | no | https://publicapi.dev/careerjet-api |
| CV - Job match score API | Jobs | publicapis.dev | yes | https://publicapis.dev/resource/cv-job-match-score-api/6dlpk1md |
| Fantastic.jobs | Jobs | publicapis.dev | yes | https://publicapis.dev/resource/fantastic-jobs/q997r0g4 |
| Fantastic.jobs | Jobs | publicapi.dev | yes | https://publicapi.dev/fantastic-jobs-api |
| Findwork | Jobs | publicapis.dev | unknown | https://publicapis.dev/resource/findwork/4w1179ji |
| Findwork | Jobs | publicapi.dev | no | https://publicapi.dev/findwork-api |
| Job Application API | Jobs | publicapis.dev | unknown | https://publicapis.dev/resource/job-application-api/agii0yyd |
| jobdata API | Jobs | publicapis.dev | unknown | https://publicapis.dev/resource/jobdata-api/8t8b9pda |
| jobdata API | Jobs | publicapi.dev | no | https://publicapi.dev/jobdata-api |
| Jobs2Careers | Jobs | publicapi.dev | no | https://publicapi.dev/jobs2careers-api |
| Jobspipe | Jobs | publicapis.dev | unknown | https://publicapis.dev/resource/jobspipe/pntfsgvd |
| Jobven | Jobs | publicapis.dev | yes | https://publicapis.dev/resource/jobven/ww6arqzw |
| Jobvetta | Jobs | publicapis.dev | yes | https://publicapis.dev/resource/jobvetta/r05e2xns |
| Jooble | Jobs | publicapis.dev | unknown | https://publicapis.dev/resource/jooble/75jiq4hc |
| Jooble | Jobs | publicapi.dev | no | https://publicapi.dev/jooble-api |
| Juju | Jobs | publicapis.dev | unknown | https://publicapis.dev/resource/juju/07ffia7q |
| Juju | Jobs | publicapi.dev | no | https://publicapi.dev/juju-api |
| OkJob | Jobs | publicapi.dev | no | https://publicapi.dev/ok-job-api |
| Reed | Jobs | publicapis.dev | unknown | https://publicapis.dev/resource/reed/d7od8cve |
| Reed | Jobs | publicapi.dev | no | https://publicapi.dev/reed-api |
| Techmap&#x27;s Job Postings | Jobs | publicapis.dev | unknown | https://publicapis.dev/resource/techmap-s-job-postings/iwecsub8 |
| Techmap&#x27;s Job Postings | Jobs | publicapi.dev | no | https://publicapi.dev/techmap-s-job-postings-api |
| The Muse | Jobs | publicapis.dev | unknown | https://publicapis.dev/resource/the-muse/jxfyswhe |
| The Muse | Jobs | publicapi.dev | no | https://publicapi.dev/the-muse-api |
| TheirStack&#x27;s Job Postings | Jobs | publicapis.dev | unknown | https://publicapis.dev/resource/theirstack-s-job-postings/heq3ho0o |
| TheirStack&#x27;s Job Postings | Jobs | publicapi.dev | no | https://publicapi.dev/their-stack-s-job-postings-api |
| USAJOBS | Jobs | publicapis.dev | unknown | https://publicapis.dev/resource/usajobs/qfaak58j |
| USAJOBS | Jobs | publicapi.dev | no | https://publicapi.dev/usajobs-api |
| WhatJobs | Jobs | publicapis.dev | unknown | https://publicapis.dev/resource/whatjobs/2qdx6c2m |
| WhatJobs | Jobs | publicapi.dev | no | https://publicapi.dev/what-jobs-api |
| ZipRecruiter | Jobs | publicapi.dev | no | https://publicapi.dev/zip-recruiter-api |
| AI For Thai | Machine Learning | publicapi.dev | yes | https://publicapi.dev/ai-for-thai-api |
| AI/ML API | Machine Learning | publicapi.dev | yes | https://publicapi.dev/ai-ml-api |
| Cloudmersive | Machine Learning | publicapi.dev | yes | https://publicapi.dev/cloudmersive-api |
| Cohere | Machine Learning | publicapi.dev | yes | https://publicapi.dev/cohere-api |
| Dialogflow | Machine Learning | publicapi.dev | no | https://publicapi.dev/dialogflow-api |
| Eden | Machine Learning | publicapi.dev | yes | https://publicapi.dev/eden-api |
| Gladia | Machine Learning | publicapi.dev | no | https://publicapi.dev/gladia-api |
| Hirak FaceAPI | Machine Learning | publicapi.dev | no | https://publicapi.dev/hirak-face-api |
| Imagga | Machine Learning | publicapi.dev | no | https://publicapi.dev/imagga-api |
| Inferdo | Machine Learning | publicapi.dev | no | https://publicapi.dev/inferdo-api |
| IPS Online | Machine Learning | publicapi.dev | no | https://publicapi.dev/ips-online-api |
| Irisnet | Machine Learning | publicapi.dev | yes | https://publicapi.dev/irisnet-api |
| jsonAi.cloud | Machine Learning | publicapi.dev | no | https://publicapi.dev/json-ai-cloud-api |
| Keen IO | Machine Learning | publicapi.dev | no | https://publicapi.dev/keen-io-api |
| Machinetutors | Machine Learning | publicapi.dev | yes | https://publicapi.dev/machinetutors-api |
| MessengerX.io | Machine Learning | publicapi.dev | yes | https://publicapi.dev/messenger-x-io-api |
| NLP Cloud | Machine Learning | publicapi.dev | no | https://publicapi.dev/nlp-cloud-api |
| OpenAI | Machine Learning | publicapi.dev | yes | https://publicapi.dev/open-ai-api |
| Perspective | Machine Learning | publicapi.dev | no | https://publicapi.dev/perspective-api |
| Roboflow Universe | Machine Learning | publicapi.dev | yes | https://publicapi.dev/roboflow-universe-api |
| SkyBiometry | Machine Learning | publicapi.dev | no | https://publicapi.dev/sky-biometry-api |
| Summarize Text with AI | Machine Learning | publicapi.dev | yes | https://publicapi.dev/summarize-text-with-ai-api |
| Time Door | Machine Learning | publicapi.dev | yes | https://publicapi.dev/time-door-api |
| Unplugg | Machine Learning | publicapi.dev | no | https://publicapi.dev/unplugg-api |
| WolframAlpha | Machine Learning | publicapi.dev | no | https://publicapi.dev/wolfram-alpha-api |
| AI Mastering | Music | publicapis.dev | yes | https://publicapis.dev/resource/ai-mastering/wqbkjig6 |
| AI Mastering | Music | publicapi.dev | yes | https://publicapi.dev/ai-mastering-api |
| AudD | Music | publicapis.dev | unknown | https://publicapis.dev/resource/audd/3bteo3ch |
| audiometa.io | Music | publicapis.dev | no | https://publicapis.dev/resource/audiometa-io/wlceksvl |
| Freesound | Music | publicapis.dev | unknown | https://publicapis.dev/resource/freesound/wmf07442 |
| Freesound | Music | publicapi.dev | no | https://publicapi.dev/freesound-api |
| KSoft.Si Lyrics | Music | publicapi.dev | no | https://publicapi.dev/k-soft-si-lyrics-api |
| LastFm | Music | publicapis.dev | unknown | https://publicapis.dev/resource/lastfm/ate0wt07 |
| LastFm | Music | publicapi.dev | no | https://publicapi.dev/last-fm-api |
| Mubert AI Music API | Music | publicapis.dev | unknown | https://publicapis.dev/resource/mubert-ai-music-api/ip46xwr4 |
| MusicGPT | Music | publicapis.dev | yes | https://publicapis.dev/resource/musicgpt/zh8c6c88 |
| Musixmatch | Music | publicapis.dev | yes | https://publicapis.dev/resource/musixmatch/hjutkbdp |
| Musixmatch | Music | publicapi.dev | no | https://publicapi.dev/musixmatch-api |
| Napster | Music | publicapi.dev | yes | https://publicapi.dev/napster-api |
| Phishin | Music | publicapis.dev | no | https://publicapis.dev/resource/phishin/2gql9gjn |
| Phishin | Music | publicapi.dev | no | https://publicapi.dev/phishin-api |
| Songkick | Music | publicapis.dev | unknown | https://publicapis.dev/resource/songkick/jjjjmxum |
| Songkick | Music | publicapi.dev | no | https://publicapi.dev/songkick-api |
| Songlink / Odesli | Music | publicapis.dev | yes | https://publicapis.dev/resource/songlink---odesli/3mlfnmo8 |
| Songlink / Odesli | Music | publicapi.dev | yes | https://publicapi.dev/songlink-odesli-api |
| TasteDive | Music | publicapis.dev | unknown | https://publicapis.dev/resource/tastedive/2gzmlzh0 |
| TasteDive | Music | publicapi.dev | no | https://publicapi.dev/taste-dive-api |
| TheAudioDB | Music | publicapis.dev | unknown | https://publicapis.dev/resource/theaudiodb/0sp79xfb |
| TheAudioDB | Music | publicapi.dev | no | https://publicapi.dev/the-audio-db-api |
| Vagalume | Music | publicapi.dev | no | https://publicapi.dev/vagalume-api |
| apilayer mediastack | News | publicapi.dev | no | https://publicapi.dev/apilayer-mediastack-api |
| APITube News API | News | publicapis.dev | yes | https://publicapis.dev/resource/apitube-news-api/m0zdprb1 |
| Associated Press | News | publicapis.dev | unknown | https://publicapis.dev/resource/associated-press/layb2p6m |
| Associated Press | News | publicapi.dev | no | https://publicapi.dev/associated-press-api |
| Currents | News | publicapis.dev | yes | https://publicapis.dev/resource/currents/b6si6wkw |
| Currents | News | publicapi.dev | yes | https://publicapi.dev/currents-api |
| Finlight | News | publicapis.dev | unknown | https://publicapis.dev/resource/finlight/ldtdeclc |
| Finlight | News | publicapi.dev | no | https://publicapi.dev/finlight-api |
| Free News API | News | publicapis.dev | no | https://publicapis.dev/resource/free-news-api/289okbp2 |
| GNews | News | publicapis.dev | yes | https://publicapis.dev/resource/gnews/q9itxzxx |
| GNews | News | publicapi.dev | yes | https://publicapi.dev/g-news-api |
| HackerNews Node | News | publicapis.dev | no | https://publicapis.dev/resource/hackernews-node/97izdee0 |
| HackerNews Node | News | publicapi.dev | no | https://publicapi.dev/hacker-news-node-api |
| HasData: Structured Web Data APIs | News | publicapis.dev | no | https://publicapis.dev/resource/hasdata-structured-web-data-apis/o75udhua |
| MarketAux | News | publicapis.dev | yes | https://publicapis.dev/resource/marketaux/pfwrr5sd |
| MarketAux | News | publicapi.dev | yes | https://publicapi.dev/market-aux-api |
| New York Times | News | publicapis.dev | unknown | https://publicapis.dev/resource/new-york-times/2p2txjtp |
| New York Times | News | publicapi.dev | no | https://publicapi.dev/new-york-times-api |
| News | News | publicapis.dev | unknown | https://publicapis.dev/resource/news/b06i948c |
| News | News | publicapi.dev | no | https://publicapi.dev/news-api |
| NewsAgent Data | News | publicapis.dev | yes | https://publicapis.dev/resource/newsagent-data/ac4hhcwy |
| NewsAPI.ai | News | publicapis.dev | yes | https://publicapis.dev/resource/newsapi-ai/b44jsnsl |
| NewsAPI.ai | News | publicapi.dev | yes | https://publicapi.dev/news-api-ai-api |
| NewsData | News | publicapis.dev | unknown | https://publicapis.dev/resource/newsdata/4n4r1sms |
| NewsData | News | publicapi.dev | no | https://publicapi.dev/news-data-api |
| NewsDataHub | News | publicapis.dev | yes | https://publicapis.dev/resource/newsdatahub/mccqjw3x |
| NewsDataHub API | News | publicapi.dev | yes | https://publicapi.dev/news-data-hub-api |
| NewsMesh | News | publicapis.dev | yes | https://publicapis.dev/resource/newsmesh/5brlfkdy |
| NewsX | News | publicapis.dev | unknown | https://publicapis.dev/resource/newsx/wy6m6yho |
| NewsX | News | publicapi.dev | no | https://publicapi.dev/news-x-api |
| The Guardian | News | publicapis.dev | unknown | https://publicapis.dev/resource/the-guardian/wb9946bf |
| The Guardian | News | publicapi.dev | no | https://publicapi.dev/the-guardian-api |
| The Old Reader | News | publicapis.dev | unknown | https://publicapis.dev/resource/the-old-reader/rha6qo38 |
| The Old Reader | News | publicapi.dev | no | https://publicapi.dev/the-old-reader-api |
| TheNews | News | publicapis.dev | yes | https://publicapis.dev/resource/thenews/d289h5p1 |
| TheNews | News | publicapi.dev | yes | https://publicapi.dev/the-news-api |
| Trove | News | publicapis.dev | unknown | https://publicapis.dev/resource/trove/bz523ycw |
| Trove | News | publicapi.dev | no | https://publicapi.dev/trove-api |
| Webz.io News API | News | publicapis.dev | no | https://publicapis.dev/resource/webz-io-news-api/gy4xslmf |
| World News | News | publicapis.dev | yes | https://publicapis.dev/resource/world-news/bpjkv3ls |
| World News | News | publicapi.dev | yes | https://publicapi.dev/world-news-api |
| Black History Facts | Open Data | publicapis.dev | yes | https://publicapis.dev/resource/black-history-facts/esanw4gb |
| Black History Facts | Open Data | publicapi.dev | yes | https://publicapi.dev/black-history-facts-api |
| CARTO | Open Data | publicapis.dev | unknown | https://publicapis.dev/resource/carto/jzadxzsw |
| CARTO | Open Data | publicapi.dev | no | https://publicapi.dev/carto-api |
| CrawlGraph | Open Data | publicapis.dev | no | https://publicapis.dev/resource/crawlgraph/c4ucbfip |
| DataStream | Open Data | publicapis.dev | yes | https://publicapis.dev/resource/datastream/x09bbqve |
| DataStream | Open Data | publicapi.dev | yes | https://publicapi.dev/data-stream-api |
| Enigma Public | Open Data | publicapis.dev | yes | https://publicapis.dev/resource/enigma-public/6s1f3aea |
| Enigma Public | Open Data | publicapi.dev | yes | https://publicapi.dev/enigma-public-api |
| Generate Link Preview (including checking for malicious links) | Open Data | publicapis.dev | yes | https://publicapis.dev/resource/generate-link-preview-including-checking-for-malicious-links-/jrztb7wm |
| Generate Link Preview (including checking for malicious links) | Open Data | publicapi.dev | yes | https://publicapi.dev/generate-link-preview-including-checking-for-malicious-links-api |
| Joshua Project | Open Data | publicapis.dev | unknown | https://publicapis.dev/resource/joshua-project/sqb7oku9 |
| Joshua Project | Open Data | publicapi.dev | no | https://publicapi.dev/joshua-project-api |
| Kaggle | Open Data | publicapis.dev | unknown | https://publicapis.dev/resource/kaggle/9bxtuh2d |
| Kaggle | Open Data | publicapi.dev | no | https://publicapi.dev/kaggle-api |
| LinkPreview | Open Data | publicapis.dev | yes | https://publicapis.dev/resource/linkpreview/rmrds6gn |
| LinkPreview | Open Data | publicapi.dev | yes | https://publicapi.dev/link-preview-api |
| Nasdaq Data Link | Open Data | publicapi.dev | no | https://publicapi.dev/nasdaq-data-link-api |
| OpenCorporates | Open Data | publicapis.dev | unknown | https://publicapis.dev/resource/opencorporates/nyd3aqpf |
| OpenCorporates | Open Data | publicapi.dev | no | https://publicapi.dev/open-corporates-api |
| PeakMetrics | Open Data | publicapi.dev | no | https://publicapi.dev/peak-metrics-api |
| Recreation Information Database | Open Data | publicapis.dev | unknown | https://publicapis.dev/resource/recreation-information-database/9g5ctnoj |
| Recreation Information Database | Open Data | publicapi.dev | no | https://publicapi.dev/recreation-information-database-api |
| Scoop.it | Open Data | publicapis.dev | unknown | https://publicapis.dev/resource/scoop-it/63byy50f |
| Scoop.it | Open Data | publicapi.dev | no | https://publicapi.dev/scoop-it-api |
| Udemy Coupons, Courses &amp; Instructors Data | Open Data | publicapis.dev | yes | https://publicapis.dev/resource/udemy-coupons-courses-and-instructors-data/48b9vin5 |
| UniDb | Open Data | publicapis.dev | yes | https://publicapis.dev/resource/unidb/0of7yd0p |
| UniDb | Open Data | publicapi.dev | yes | https://publicapi.dev/uni-db-api |
| UPC database | Open Data | publicapis.dev | unknown | https://publicapis.dev/resource/upc-database/mmb351z6 |
| UPC database | Open Data | publicapi.dev | no | https://publicapi.dev/upc-database-api |
| OpenSERP Cloud | Open Source Projects | publicapis.dev | no | https://publicapis.dev/resource/openserp-cloud/war82uhf |
| TIPO | Patent | publicapis.dev | unknown | https://publicapis.dev/resource/tipo/yp2ci7xk |
| TIPO | Patent | publicapi.dev | no | https://publicapi.dev/tipo-api |
| Dev.to | Personality | publicapis.dev | unknown | https://publicapis.dev/resource/dev-to/og4a1idj |
| Dev.to | Personality | publicapi.dev | no | https://publicapi.dev/dev-to-api |
| FavQs.com | Personality | publicapis.dev | unknown | https://publicapis.dev/resource/favqs-com/shtkd2fi |
| FavQs.com | Personality | publicapi.dev | no | https://publicapi.dev/fav-qs-com-api |
| Human Design Hub | Personality | publicapis.dev | no | https://publicapis.dev/resource/human-design-hub/8tkxuks3 |
| Sentino Personality API | Personality | publicapis.dev | no | https://publicapis.dev/resource/sentino-personality-api/4tggbltg |
| Udemy(instructor) | Personality | publicapis.dev | unknown | https://publicapis.dev/resource/udemy-instructor-/rlt44l5o |
| Udemy(instructor) | Personality | publicapi.dev | no | https://publicapi.dev/udemy-instructor-api |
| Vedika | Personality | publicapis.dev | yes | https://publicapis.dev/resource/vedika/hlh8284v |
| Abstract Phone Validation | Phone | publicapis.dev | yes | https://publicapis.dev/resource/abstract-phone-validation/d5b5fmwd |
| Abstract Phone Validation | Phone | publicapi.dev | yes | https://publicapi.dev/abstract-phone-validation-api |
| apilayer numverify | Phone | publicapi.dev | no | https://publicapi.dev/apilayer-numverify-api |
| CheckThatPhone | Phone | publicapis.dev | unknown | https://publicapis.dev/resource/checkthatphone/thimiy5d |
| Cloudmersive Validate | Phone | publicapis.dev | yes | https://publicapis.dev/resource/cloudmersive-validate/6f2sui4g |
| Cloudmersive Validate | Phone | publicapi.dev | yes | https://publicapi.dev/cloudmersive-validate-api |
| MobileAPI | Phone | publicapis.dev | yes | https://publicapis.dev/resource/mobileapi/5nm2pn6c |
| NumlookupAPI | Phone | publicapis.dev | yes | https://publicapis.dev/resource/numlookupapi/tb9iid1h |
| NumlookupAPI | Phone | publicapi.dev | yes | https://publicapi.dev/numlookup-api |
| Proweblook Phone Number Checker | Phone | publicapis.dev | yes | https://publicapis.dev/resource/proweblook-phone-number-checker/1f7tshwf |
| Proweblook Phone Number Checker | Phone | publicapi.dev | yes | https://publicapi.dev/proweblook-phone-number-checker-api |
| Proweblook Whatsapp Contact Checker | Phone | publicapis.dev | yes | https://publicapis.dev/resource/proweblook-whatsapp-contact-checker/0fsajgxw |
| Proweblook Whatsapp Contact Checker | Phone | publicapi.dev | yes | https://publicapi.dev/proweblook-whatsapp-contact-checker-api |
| Veriphone | Phone | publicapis.dev | yes | https://publicapis.dev/resource/veriphone/93m45hok |
| Veriphone | Phone | publicapi.dev | yes | https://publicapi.dev/veriphone-api |
| APITemplate.io | Photography | publicapis.dev | yes | https://publicapis.dev/resource/apitemplate-io/9anksl5q |
| APITemplate.io | Photography | publicapi.dev | yes | https://publicapi.dev/api-template-io-api |
| Bruzu | Photography | publicapi.dev | yes | https://publicapi.dev/bruzu-api |
| Change Image Background API | Photography | publicapis.dev | yes | https://publicapis.dev/resource/change-image-background-api/vxu4t6du |
| CheetahO | Photography | publicapis.dev | unknown | https://publicapis.dev/resource/cheetaho/cfx90ud7 |
| CheetahO | Photography | publicapi.dev | no | https://publicapi.dev/cheetah-o-api |
| Convert Images to PDF API | Photography | publicapis.dev | yes | https://publicapis.dev/resource/convert-images-to-pdf-api/n9nt0ywz |
| Dagpi | Photography | publicapis.dev | unknown | https://publicapis.dev/resource/dagpi/pvrta2qj |
| Dagpi | Photography | publicapi.dev | no | https://publicapi.dev/dagpi-api |
| Duply | Photography | publicapis.dev | yes | https://publicapis.dev/resource/duply/higepl89 |
| Duply | Photography | publicapi.dev | yes | https://publicapi.dev/duply-api |
| DynaPictures | Photography | publicapis.dev | yes | https://publicapis.dev/resource/dynapictures/2wilst8e |
| DynaPictures | Photography | publicapi.dev | yes | https://publicapi.dev/dyna-pictures-api |
| Generate Image Thumbnails API | Photography | publicapis.dev | yes | https://publicapis.dev/resource/generate-image-thumbnails-api/usywb778 |
| Giphy | Photography | publicapis.dev | unknown | https://publicapis.dev/resource/giphy/v9zjqzgi |
| Giphy | Photography | publicapi.dev | no | https://publicapi.dev/giphy-api |
| Image Upload | Photography | publicapi.dev | no | https://publicapi.dev/image-upload-api |
| ObjectCut | Photography | publicapis.dev | yes | https://publicapis.dev/resource/objectcut/1acdbmet |
| ObjectCut | Photography | publicapi.dev | yes | https://publicapi.dev/object-cut-api |
| Pexafy Stock Photos | Photography | publicapis.dev | yes | https://publicapis.dev/resource/pexafy-stock-photos/bnnpcd0c |
| Pexels | Photography | publicapis.dev | yes | https://publicapis.dev/resource/pexels/3c2o18fq |
| Pexels | Photography | publicapi.dev | yes | https://publicapi.dev/pexels-api |
| PhotoRoom | Photography | publicapis.dev | unknown | https://publicapis.dev/resource/photoroom/cj53yofu |
| PhotoRoom | Photography | publicapi.dev | no | https://publicapi.dev/photo-room-api |
| Pixabay | Photography | publicapis.dev | unknown | https://publicapis.dev/resource/pixabay/vkbr13gj |
| Pixabay | Photography | publicapi.dev | no | https://publicapi.dev/pixabay-api |
| PixelVault | Photography | publicapis.dev | no | https://publicapis.dev/resource/pixelvault/lvcg0lhi |
| Poof | Photography | publicapis.dev | yes | https://publicapis.dev/resource/poof/l6qrj229 |
| Remove Background from Images API | Photography | publicapis.dev | yes | https://publicapis.dev/resource/remove-background-from-images-api/eydar78y |
| Remove.bg | Photography | publicapis.dev | unknown | https://publicapis.dev/resource/remove-bg/iitb20ow |
| Remove.bg | Photography | publicapi.dev | no | https://publicapi.dev/remove-bg-api |
| ShotOG | Photography | publicapis.dev | yes | https://publicapis.dev/resource/shotog/q23nx5o0 |
| Sirv | Photography | publicapis.dev | unknown | https://publicapis.dev/resource/sirv/p9j3hz4a |
| Sirv | Photography | publicapi.dev | no | https://publicapi.dev/sirv-api |
| Smart Adaptive Ultra-Fast Image Compressor &amp; Converter | Photography | publicapis.dev | yes | https://publicapis.dev/resource/smart-adaptive-ultra-fast-image-compressor-and-converter/rs0pcb8e |
| Templated | Photography | publicapis.dev | yes | https://publicapis.dev/resource/templated/hx3evc3j |
| Templated | Photography | publicapi.dev | yes | https://publicapi.dev/templated-api |
| Wallhaven | Photography | publicapis.dev | unknown | https://publicapis.dev/resource/wallhaven/35xkkn4v |
| Wallhaven | Photography | publicapi.dev | no | https://publicapi.dev/wallhaven-api |
| Listen Notes Podcast API | Podcasts | publicapis.dev | yes | https://publicapis.dev/resource/listen-notes-podcast-api/rwldek5n |
| Particle | Podcasts | publicapis.dev | no | https://publicapis.dev/resource/particle/hl8vhcb6 |
| PodcastIndex | Podcasts | publicapis.dev | unknown | https://publicapis.dev/resource/podcastindex/vabm7pve |
| PodcastIndex | Podcasts | publicapi.dev | no | https://publicapi.dev/podcast-index-api |
| Podchaser \| Podcast Data API | Podcasts | publicapis.dev | unknown | https://publicapis.dev/resource/podchaser-podcast-data-api/67sqhail |
| Taddy Podcasts | Podcasts | publicapis.dev | yes | https://publicapis.dev/resource/taddy-podcasts/404j11wt |
| Taddy Podcasts | Podcasts | publicapi.dev | yes | https://publicapi.dev/taddy-podcasts-api |
| CatchDoms Expired Domains API | Programming | publicapis.dev | yes | https://publicapis.dev/resource/catchdoms-expired-domains-api/xls4f0fu |
| Codeforces | Programming | publicapis.dev | unknown | https://publicapis.dev/resource/codeforces/x98dgwfl |
| Codeforces | Programming | publicapi.dev | no | https://publicapi.dev/codeforces-api |
| Hackerearth | Programming | publicapis.dev | unknown | https://publicapis.dev/resource/hackerearth/cieyd12h |
| Hackerearth | Programming | publicapis.dev | unknown | https://publicapis.dev/resource/hackerearth/hc5tb29z |
| Hackerearth | Programming | publicapi.dev | no | https://publicapi.dev/hackerearth-api |
| Judge0 CE | Programming | publicapis.dev | unknown | https://publicapis.dev/resource/judge0-ce/i17ilqls |
| Judge0 CE | Programming | publicapi.dev | no | https://publicapi.dev/judge0-ce-api |
| Mintlify | Programming | publicapi.dev | yes | https://publicapi.dev/mintlify-api |
| arcsecond.io | Science & Math | publicapis.dev | unknown | https://publicapis.dev/resource/arcsecond-io/41tyebtq |
| arcsecond.io | Science & Math | publicapi.dev | no | https://publicapi.dev/arcsecond-io-api |
| CORE | Science & Math | publicapis.dev | unknown | https://publicapis.dev/resource/core/fabumb8n |
| CORE | Science & Math | publicapi.dev | no | https://publicapi.dev/core-api |
| Materials Platform for Data Science | Science & Math | publicapis.dev | no | https://publicapis.dev/resource/materials-platform-for-data-science/bltbkszf |
| Materials Platform for Data Science | Science & Math | publicapi.dev | no | https://publicapi.dev/materials-platform-for-data-science-api |
| Numbers | Science & Math | publicapis.dev | no | https://publicapis.dev/resource/numbers/js2rgag2 |
| Application Environment Verification | Security | publicapis.dev | yes | https://publicapis.dev/resource/application-environment-verification/q28lkh7m |
| Application Environment Verification | Security | publicapi.dev | yes | https://publicapi.dev/application-environment-verification-api |
| ArkForge Trust Layer | Security | publicapis.dev | yes | https://publicapis.dev/resource/arkforge-trust-layer/v2pb7z5f |
| BinaryEdge | Security | publicapis.dev | yes | https://publicapis.dev/resource/binaryedge/pigoeacz |
| BinaryEdge | Security | publicapi.dev | yes | https://publicapi.dev/binary-edge-api |
| Botd | Security | publicapis.dev | yes | https://publicapis.dev/resource/botd/0pocp9j1 |
| Botd | Security | publicapi.dev | yes | https://publicapi.dev/botd-api |
| Bugcrowd | Security | publicapis.dev | unknown | https://publicapis.dev/resource/bugcrowd/cvkalvb3 |
| Bugcrowd | Security | publicapi.dev | no | https://publicapi.dev/bugcrowd-api |
| Censys | Security | publicapis.dev | no | https://publicapis.dev/resource/censys/gqtty9q4 |
| Censys | Security | publicapi.dev | no | https://publicapi.dev/censys-api |
| Complete Criminal Checks | Security | publicapis.dev | yes | https://publicapis.dev/resource/complete-criminal-checks/9rcvlrsh |
| Complete Criminal Checks | Security | publicapi.dev | yes | https://publicapi.dev/complete-criminal-checks-api |
| CRXcavator | Security | publicapi.dev | no | https://publicapi.dev/cr-xcavator-api |
| DomScan | Security | publicapis.dev | yes | https://publicapis.dev/resource/domscan/g2llv7qc |
| FingerprintJS Pro | Security | publicapis.dev | yes | https://publicapis.dev/resource/fingerprintjs-pro/pwsthp8w |
| FingerprintJS Pro | Security | publicapi.dev | yes | https://publicapi.dev/fingerprint-js-pro-api |
| FraudLabs Pro | Security | publicapis.dev | unknown | https://publicapis.dev/resource/fraudlabs-pro/xmjkjm7n |
| FraudLabs Pro | Security | publicapi.dev | no | https://publicapi.dev/fraud-labs-pro-api |
| FullHunt | Security | publicapis.dev | unknown | https://publicapis.dev/resource/fullhunt/aryuz3rv |
| FullHunt | Security | publicapi.dev | no | https://publicapi.dev/full-hunt-api |
| GitGuardian | Security | publicapis.dev | no | https://publicapis.dev/resource/gitguardian/mf19ry90 |
| GitGuardian | Security | publicapi.dev | no | https://publicapi.dev/git-guardian-api |
| GreyNoise | Security | publicapis.dev | unknown | https://publicapis.dev/resource/greynoise/qt8e06v6 |
| GreyNoise | Security | publicapi.dev | no | https://publicapi.dev/grey-noise-api |
| HackerOne | Security | publicapis.dev | unknown | https://publicapis.dev/resource/hackerone/t4g29jtl |
| HackerOne | Security | publicapi.dev | no | https://publicapi.dev/hacker-one-api |
| HaveIBeenPwned | Security | publicapis.dev | unknown | https://publicapis.dev/resource/haveibeenpwned/1a50lwso |
| HaveIBeenPwned | Security | publicapi.dev | no | https://publicapi.dev/have-i-been-pwned-api |
| Intelligence X | Security | publicapis.dev | unknown | https://publicapis.dev/resource/intelligence-x/tgxme6z8 |
| Intelligence X | Security | publicapi.dev | no | https://publicapi.dev/intelligence-x-api |
| IPASIS | Security | publicapis.dev | yes | https://publicapis.dev/resource/ipasis/xaqgz63w |
| LeakInsight | Security | publicapis.dev | unknown | https://publicapis.dev/resource/leakinsight/lhum2mzn |
| LoginRadius | Security | publicapis.dev | yes | https://publicapis.dev/resource/loginradius/h0fqrgbi |
| LoginRadius | Security | publicapi.dev | yes | https://publicapi.dev/login-radius-api |
| Opsis | Security | publicapis.dev | no | https://publicapis.dev/resource/opsis/mnmus1zx |
| Privacy.com | Security | publicapis.dev | unknown | https://publicapis.dev/resource/privacy-com/aione1hj |
| Privacy.com | Security | publicapi.dev | no | https://publicapi.dev/privacy-com-api |
| Pulsedive | Security | publicapis.dev | unknown | https://publicapis.dev/resource/pulsedive/fqvw2s2s |
| Pulsedive | Security | publicapi.dev | no | https://publicapi.dev/pulsedive-api |
| SecurityTrails | Security | publicapis.dev | unknown | https://publicapis.dev/resource/securitytrails/p67zx0vx |
| SecurityTrails | Security | publicapi.dev | no | https://publicapi.dev/security-trails-api |
| Shodan | Security | publicapis.dev | unknown | https://publicapis.dev/resource/shodan/um6fqxga |
| Shodan | Security | publicapi.dev | no | https://publicapi.dev/shodan-api |
| Spyse | Security | publicapis.dev | unknown | https://publicapis.dev/resource/spyse/cq4cgvjo |
| Spyse | Security | publicapi.dev | no | https://publicapi.dev/spyse-api |
| Threat Jammer | Security | publicapi.dev | no | https://publicapi.dev/threat-jammer-api |
| Virus and Malware Scan API by attachmentAV | Security | publicapis.dev | no | https://publicapis.dev/resource/virus-and-malware-scan-api-by-attachmentav/nn87ldec |
| VulDB | Security | publicapis.dev | unknown | https://publicapis.dev/resource/vuldb/d9p3mt9e |
| VulDB | Security | publicapi.dev | no | https://publicapi.dev/vul-db-api |
| Whoisfreaks | Security | publicapis.dev | no | https://publicapis.dev/resource/whoisfreaks/32oy1apf |
| Whoisfreaks | Security | publicapi.dev | no | https://publicapi.dev/whoisfreaks-api |
| Best Buy | Shopping | publicapis.dev | unknown | https://publicapis.dev/resource/best-buy/r5o8ufzc |
| Best Buy | Shopping | publicapi.dev | no | https://publicapi.dev/best-buy-api |
| Canopy | Shopping | publicapis.dev | unknown | https://publicapis.dev/resource/canopy/j7m2zha5 |
| Canopy | Shopping | publicapi.dev | no | https://publicapi.dev/canopy-api |
| Dummy Products | Shopping | publicapi.dev | yes | https://publicapi.dev/dummy-products-api |
| E-Commerce APIs | Shopping | publicapis.dev | yes | https://publicapis.dev/resource/e-commerce-apis/hx84d0v8 |
| Lazada | Shopping | publicapis.dev | unknown | https://publicapis.dev/resource/lazada/pmukcp36 |
| Lazada | Shopping | publicapi.dev | no | https://publicapi.dev/lazada-api |
| Mercadolibre | Shopping | publicapis.dev | unknown | https://publicapis.dev/resource/mercadolibre/6jrclaiz |
| Mercadolibre | Shopping | publicapi.dev | no | https://publicapi.dev/mercadolibre-api |
| Octopart | Shopping | publicapi.dev | no | https://publicapi.dev/octopart-api |
| OLX Poland | Shopping | publicapis.dev | unknown | https://publicapis.dev/resource/olx-poland/968xdgh2 |
| OLX Poland | Shopping | publicapi.dev | no | https://publicapi.dev/olx-poland-api |
| Platzi Fake Store | Shopping | publicapis.dev | unknown | https://publicapis.dev/resource/platzi-fake-store/ye2e5c1v |
| Platzi Fake Store | Shopping | publicapi.dev | no | https://publicapi.dev/platzi-fake-store-api |
| ScoutAPI | Shopping | publicapis.dev | unknown | https://publicapis.dev/resource/scoutapi/c09z5wbp |
| Shopee | Shopping | publicapis.dev | unknown | https://publicapis.dev/resource/shopee/th2rp7mx |
| Shopee | Shopping | publicapi.dev | no | https://publicapi.dev/shopee-api |
| ShopSavvy | Shopping | publicapis.dev | yes | https://publicapis.dev/resource/shopsavvy/strgk6xs |
| WooCommerce | Shopping | publicapis.dev | yes | https://publicapis.dev/resource/woocommerce/e79q8w40 |
| WooCommerce | Shopping | publicapi.dev | yes | https://publicapi.dev/woo-commerce-api |
| Ayrshare | Social | publicapis.dev | yes | https://publicapis.dev/resource/ayrshare/vgnpt69r |
| Ayrshare | Social | publicapi.dev | yes | https://publicapi.dev/ayrshare-api |
| BulkPublish | Social | publicapis.dev | yes | https://publicapis.dev/resource/bulkpublish/vl8ob623 |
| Dangerous Discord Database | Social | publicapi.dev | no | https://publicapi.dev/dangerous-discord-database-api |
| Feedframer | Social | publicapis.dev | yes | https://publicapis.dev/resource/feedframer/tenpvjk9 |
| GetXAPI | Social | publicapis.dev | no | https://publicapis.dev/resource/getxapi/ix08f44d |
| graph.org | Social | publicapi.dev | no | https://publicapi.dev/graph-org-api |
| Hashtag | Social | publicapis.dev | unknown | https://publicapis.dev/resource/hashtag/q655bsy0 |
| Hashtag | Social | publicapi.dev | no | https://publicapi.dev/hashtag-api |
| Meetup | Social | publicapis.dev | unknown | https://publicapis.dev/resource/meetup/pocmpq1g |
| Meetup.com | Social | publicapi.dev | no | https://publicapi.dev/meetup-com-api |
| Mirrorfly Chat API | Social | publicapis.dev | unknown | https://publicapis.dev/resource/mirrorfly-chat-api/2uaqr8zv |
| Revolt | Social | publicapis.dev | unknown | https://publicapis.dev/resource/revolt/a41tqfvc |
| Revolt | Social | publicapi.dev | no | https://publicapi.dev/revolt-api |
| Social Media Management API | Social | publicapis.dev | no | https://publicapis.dev/resource/social-media-management-api/g1er43ni |
| SocialClaw | Social | publicapis.dev | yes | https://publicapis.dev/resource/socialclaw/vndcsiy1 |
| SocialData API | Social | publicapis.dev | no | https://publicapis.dev/resource/socialdata-api/srcwnxx8 |
| SocialData API | Social | publicapi.dev | no | https://publicapi.dev/social-data-api |
| TamTam | Social | publicapis.dev | unknown | https://publicapis.dev/resource/tamtam/aa50f8fd |
| TamTam | Social | publicapi.dev | no | https://publicapi.dev/tam-tam-api |
| Telegram Bot | Social | publicapis.dev | unknown | https://publicapis.dev/resource/telegram-bot/cmblaazq |
| Telegram Bot | Social | publicapi.dev | no | https://publicapi.dev/telegram-bot-api |
| TikTok LIVE | Social | publicapis.dev | yes | https://publicapis.dev/resource/tiktok-live/52ci3n30 |
| TwitterApi.IO | Social | publicapis.dev | no | https://publicapis.dev/resource/twitterapi-io/f5dgxrct |
| WoopSocial | Social | publicapis.dev | no | https://publicapis.dev/resource/woopsocial/z08p7fe3 |
| Xquik API | Social | publicapis.dev | unknown | https://publicapis.dev/resource/xquik-api/30zjufuk |
| 5DollarFootballAPI | Sports & Fitness | publicapis.dev | yes | https://publicapis.dev/resource/5dollarfootballapi/k2h4djiz |
| American Football Highlights API | Sports & Fitness | publicapis.dev | unknown | https://publicapis.dev/resource/american-football-highlights-api/1ituihn9 |
| American Football Highlights API | Sports & Fitness | publicapi.dev | no | https://publicapi.dev/american-football-highlights-api |
| API-FOOTBALL | Sports & Fitness | publicapis.dev | yes | https://publicapis.dev/resource/api-football/jzfpdqbs |
| API-FOOTBALL | Sports & Fitness | publicapi.dev | yes | https://publicapi.dev/api-football-api |
| ApiMedic | Sports & Fitness | publicapi.dev | no | https://publicapi.dev/api-medic-api |
| Basketball Highlights API | Sports & Fitness | publicapis.dev | unknown | https://publicapis.dev/resource/basketball-highlights-api/wg84citb |
| Basketball Highlights API | Sports & Fitness | publicapi.dev | no | https://publicapi.dev/basketball-highlights-api |
| Blacktop Motorsport API | Sports & Fitness | publicapis.dev | yes | https://publicapis.dev/resource/blacktop-motorsport-api/q5mm2dyk |
| Canadian Football League (CFL) | Sports & Fitness | publicapi.dev | no | https://publicapi.dev/canadian-football-league-cfl-api |
| Cloudbet | Sports & Fitness | publicapis.dev | yes | https://publicapis.dev/resource/cloudbet/sury3ibo |
| Cloudbet | Sports & Fitness | publicapi.dev | yes | https://publicapi.dev/cloudbet-api |
| CollegeFootballData.com | Sports & Fitness | publicapis.dev | unknown | https://publicapis.dev/resource/collegefootballdata-com/jlfymilv |
| CollegeFootballData.com | Sports & Fitness | publicapi.dev | no | https://publicapi.dev/college-football-data-com-api |
| Cricket API | Sports & Fitness | publicapis.dev | yes | https://publicapis.dev/resource/cricket-api/k1oeqm3z |
| Football | Sports & Fitness | publicapis.dev | unknown | https://publicapis.dev/resource/football/97zysele |
| Football | Sports & Fitness | publicapi.dev | no | https://publicapi.dev/football-api |
| Football Highlights | Sports & Fitness | publicapis.dev | unknown | https://publicapis.dev/resource/football-highlights/isalhbcq |
| Football Highlights | Sports & Fitness | publicapi.dev | no | https://publicapi.dev/football-highlights-api |
| Football-Data | Sports & Fitness | publicapis.dev | unknown | https://publicapis.dev/resource/football-data/4gymhkjx |
| Football-Data | Sports & Fitness | publicapi.dev | no | https://publicapi.dev/football-data-api |
| Hockey Highlights | Sports & Fitness | publicapis.dev | unknown | https://publicapis.dev/resource/hockey-highlights/a4jbocuz |
| Hockey Highlights | Sports & Fitness | publicapi.dev | no | https://publicapi.dev/hockey-highlights-api |
| iSports API | Sports & Fitness | publicapis.dev | yes | https://publicapis.dev/resource/isports-api/pxkkdscw |
| JCDecaux Bike | Sports & Fitness | publicapis.dev | unknown | https://publicapis.dev/resource/jcdecaux-bike/r3ir4j8e |
| JCDecaux Bike | Sports & Fitness | publicapi.dev | no | https://publicapi.dev/jc-decaux-bike-api |
| Live Tennis API | Sports & Fitness | publicapis.dev | yes | https://publicapis.dev/resource/live-tennis-api/t08g9k5b |
| Lumify | Sports & Fitness | publicapis.dev | no | https://publicapis.dev/resource/lumify/fhwr6xup |
| NBA Data | Sports & Fitness | publicapis.dev | unknown | https://publicapis.dev/resource/nba-data/j4lvxj63 |
| NBA Data | Sports & Fitness | publicapi.dev | no | https://publicapi.dev/nba-data-api |
| Odds-API.io | Sports & Fitness | publicapis.dev | yes | https://publicapis.dev/resource/odds-api-io/tttmza7d |
| Premier League Standings | Sports & Fitness | publicapis.dev | unknown | https://publicapis.dev/resource/premier-league-standings/a1vm5x9p |
| Premier League Standings | Sports & Fitness | publicapi.dev | no | https://publicapi.dev/premier-league-standings-api |
| SkipOdds | Sports & Fitness | publicapis.dev | yes | https://publicapis.dev/resource/skipodds/p1h07oyl |
| Soccer / Football API | Sports & Fitness | publicapis.dev | yes | https://publicapis.dev/resource/soccer---football-api/4ojb5dfm |
| Sport Data | Sports & Fitness | publicapi.dev | no | https://publicapi.dev/sport-data-api |
| Sport Highlights | Sports & Fitness | publicapis.dev | unknown | https://publicapis.dev/resource/sport-highlights/sye6o99x |
| Sport Highlights | Sports & Fitness | publicapi.dev | no | https://publicapi.dev/sport-highlights-api |
| Sport Vision | Sports & Fitness | publicapi.dev | yes | https://publicapi.dev/sport-vision-api |
| Sportmonks Cricket | Sports & Fitness | publicapis.dev | unknown | https://publicapis.dev/resource/sportmonks-cricket/xeybk130 |
| Sportmonks Cricket | Sports & Fitness | publicapi.dev | no | https://publicapi.dev/sportmonks-cricket-api |
| Sportmonks Football | Sports & Fitness | publicapis.dev | unknown | https://publicapis.dev/resource/sportmonks-football/tac2uv2j |
| Sportmonks Football | Sports & Fitness | publicapi.dev | no | https://publicapi.dev/sportmonks-football-api |
| Sports API (Tennis / Cricket / Soccer) | Sports & Fitness | publicapis.dev | yes | https://publicapis.dev/resource/sports-api-tennis---cricket---soccer-/acabs9xj |
| SportsDataIO | Sports & Fitness | publicapis.dev | unknown | https://publicapis.dev/resource/sportsdataio/5zmzn2ib |
| Table Tennis API | Sports & Fitness | publicapis.dev | yes | https://publicapis.dev/resource/table-tennis-api/33dujyzz |
| Tennis API | Sports & Fitness | publicapis.dev | yes | https://publicapis.dev/resource/tennis-api/95a0udgq |
| TheSportsDB | Sports & Fitness | publicapis.dev | yes | https://publicapis.dev/resource/thesportsdb/rwwsrao7 |
| TheSportsDB | Sports & Fitness | publicapi.dev | yes | https://publicapi.dev/the-sports-db-api |
| Wger | Sports & Fitness | publicapis.dev | unknown | https://publicapis.dev/resource/wger/tqy0s1ni |
| Wger | Sports & Fitness | publicapi.dev | no | https://publicapi.dev/wger-api |
| Ymove Exercise API | Sports & Fitness | publicapi.dev | yes | https://publicapi.dev/ymove-exercise-api |
| Your Move - Exercise API | Sports & Fitness | publicapis.dev | yes | https://publicapis.dev/resource/your-move-exercise-api/6dwyqnmx |
| DummyJSON | Test Data | publicapi.dev | no | https://publicapi.dev/dummy-json-api |
| FakeJSON | Test Data | publicapi.dev | yes | https://publicapi.dev/fake-json-api |
| GeneradorDNI | Test Data | publicapis.dev | unknown | https://publicapis.dev/resource/generadordni/a1g5ffok |
| GeneradorDNI | Test Data | publicapi.dev | no | https://publicapi.dev/generador-dni-api |
| Mailsac | Test Data | publicapis.dev | unknown | https://publicapis.dev/resource/mailsac/sd58xvqp |
| Mailsac | Test Data | publicapi.dev | no | https://publicapi.dev/mailsac-api |
| Mockaroo | Test Data | publicapis.dev | unknown | https://publicapis.dev/resource/mockaroo/4lk72qsd |
| Mockaroo | Test Data | publicapi.dev | no | https://publicapi.dev/mockaroo-api |
| Random Identity | Test Data | publicapis.dev | yes | https://publicapis.dev/resource/random-identity/5mwb5w7v |
| Random Identity | Test Data | publicapi.dev | yes | https://publicapi.dev/random-identity-api |
| Random Profiles | Test Data | publicapis.dev | yes | https://publicapis.dev/resource/random-profiles/le7r3e8l |
| Randommer | Test Data | publicapis.dev | yes | https://publicapis.dev/resource/randommer/i8if0gyv |
| Randommer | Test Data | publicapi.dev | yes | https://publicapi.dev/randommer-api |
| AI Text Moderation, Toxicity &amp; Sentiment Analyzer | Text Analysis | publicapis.dev | yes | https://publicapis.dev/resource/ai-text-moderation-toxicity-and-sentiment-analyzer/z34mqyed |
| AI Translate text API | Text Analysis | publicapis.dev | yes | https://publicapis.dev/resource/ai-translate-text-api/bya8tx7o |
| AI Translate text API | Text Analysis | publicapi.dev | yes | https://publicapi.dev/ai-translate-text-api |
| Analyse Keywords API | Text Analysis | publicapis.dev | yes | https://publicapis.dev/resource/analyse-keywords-api/c6bfcgcw |
| Aylien Text Analysis | Text Analysis | publicapi.dev | no | https://publicapi.dev/aylien-text-analysis-api |
| Brainiall Text-to-Speech | Text Analysis | publicapis.dev | yes | https://publicapis.dev/resource/brainiall-text-to-speech/9utw3mpn |
| Chatpdf | Text Analysis | publicapi.dev | yes | https://publicapi.dev/chatpdf-api |
| Cloudmersive Natural Language Processing | Text Analysis | publicapis.dev | yes | https://publicapis.dev/resource/cloudmersive-natural-language-processing/oaaq13x0 |
| Cloudmersive Natural Language Processing | Text Analysis | publicapi.dev | yes | https://publicapi.dev/cloudmersive-natural-language-processing-api |
| Convert Text to Speech API | Text Analysis | publicapis.dev | yes | https://publicapis.dev/resource/convert-text-to-speech-api/pn1vdk6g |
| Detect Language | Text Analysis | publicapis.dev | unknown | https://publicapis.dev/resource/detect-language/uxo8fnmq |
| Detect Language | Text Analysis | publicapi.dev | no | https://publicapi.dev/detect-language-api |
| Detect Language API | Text Analysis | publicapis.dev | yes | https://publicapis.dev/resource/detect-language-api/68xrv3qs |
| ELI | Text Analysis | publicapis.dev | unknown | https://publicapis.dev/resource/eli/2d171v6f |
| ELI | Text Analysis | publicapi.dev | no | https://publicapi.dev/eli-api |
| Fuzzy Text Search API | Text Analysis | publicapis.dev | yes | https://publicapis.dev/resource/fuzzy-text-search-api/yvvaexi6 |
| Fuzzy Text Search API | Text Analysis | publicapi.dev | yes | https://publicapi.dev/fuzzy-text-search-api |
| Google Cloud Natural | Text Analysis | publicapis.dev | unknown | https://publicapis.dev/resource/google-cloud-natural/oc2nrjn2 |
| Google Cloud Natural | Text Analysis | publicapi.dev | no | https://publicapi.dev/google-cloud-natural-api |
| Hirak OCR | Text Analysis | publicapi.dev | no | https://publicapi.dev/hirak-ocr-api |
| Hirak Translation | Text Analysis | publicapi.dev | no | https://publicapi.dev/hirak-translation-api |
| Lecto Translation | Text Analysis | publicapis.dev | yes | https://publicapis.dev/resource/lecto-translation/iqt8u4sl |
| Lecto Translation | Text Analysis | publicapi.dev | yes | https://publicapi.dev/lecto-translation-api |
| Proofread and Grammar Check API | Text Analysis | publicapi.dev | yes | https://publicapi.dev/proofread-and-grammar-check-api |
| Safe text | Text Analysis | publicapis.dev | yes | https://publicapis.dev/resource/safe-text/as24464h |
| Safe text | Text Analysis | publicapi.dev | yes | https://publicapi.dev/safe-text-api |
| Sentiment Analysis | Text Analysis | publicapi.dev | yes | https://publicapi.dev/sentiment-analysis-api |
| Aftership | Tracking | publicapis.dev | yes | https://publicapis.dev/resource/aftership/hgnko4f2 |
| Aftership | Tracking | publicapi.dev | yes | https://publicapi.dev/aftership-api |
| Correios | Tracking | publicapis.dev | unknown | https://publicapis.dev/resource/correios/m03x0fux |
| Correios | Tracking | publicapi.dev | no | https://publicapi.dev/correios-api |
| IndianPincodes | Tracking | publicapi.dev | no | https://publicapi.dev/indian-pincodes-api |
| Pixela | Tracking | publicapis.dev | yes | https://publicapis.dev/resource/pixela/hjby9nqd |
| Pixela | Tracking | publicapi.dev | yes | https://publicapi.dev/pixela-api |
| PostNord | Tracking | publicapis.dev | unknown | https://publicapis.dev/resource/postnord/hq3fw0br |
| PostNord | Tracking | publicapi.dev | no | https://publicapi.dev/post-nord-api |
| UPS | Tracking | publicapis.dev | unknown | https://publicapis.dev/resource/ups/jccalvmo |
| UPS | Tracking | publicapi.dev | no | https://publicapi.dev/ups-api |
| WeCanTrack | Tracking | publicapis.dev | yes | https://publicapis.dev/resource/wecantrack/u6kh108b |
| WeCanTrack | Tracking | publicapi.dev | yes | https://publicapi.dev/we-can-track-api |
| ADS-B Exchange | Transportation | publicapis.dev | unknown | https://publicapis.dev/resource/ads-b-exchange/369wzc8m |
| ADS-B Exchange | Transportation | publicapi.dev | no | https://publicapi.dev/ads-b-exchange-api |
| AIS Hub | Transportation | publicapis.dev | unknown | https://publicapis.dev/resource/ais-hub/m7erioa9 |
| AIS Hub | Transportation | publicapi.dev | no | https://publicapi.dev/ais-hub-api |
| AZ511 | Transportation | publicapis.dev | unknown | https://publicapis.dev/resource/az511/8aldqqex |
| AZ511 | Transportation | publicapi.dev | no | https://publicapi.dev/az511-api |
| Bay Area Rapid Transit | Transportation | publicapi.dev | no | https://publicapi.dev/bay-area-rapid-transit-api |
| BlaBlaCar | Transportation | publicapi.dev | no | https://publicapi.dev/bla-bla-car-api |
| Boston MBTA Transit | Transportation | publicapis.dev | unknown | https://publicapis.dev/resource/boston-mbta-transit/ft8ajsrz |
| Boston MBTA Transit | Transportation | publicapi.dev | no | https://publicapi.dev/boston-mbta-transit-api |
| Compare Flight Prices | Transportation | publicapi.dev | no | https://publicapi.dev/compare-flight-prices-api |
| CTS | Transportation | publicapis.dev | yes | https://publicapis.dev/resource/cts/7nlsmsph |
| CTS | Transportation | publicapi.dev | yes | https://publicapi.dev/cts-api |
| Flight Fare Search | Transportation | publicapis.dev | yes | https://publicapis.dev/resource/flight-fare-search/v8x9ekse |
| Flight Fare Search | Transportation | publicapi.dev | yes | https://publicapi.dev/flight-fare-search-api |
| GraphHopper | Transportation | publicapis.dev | unknown | https://publicapis.dev/resource/graphhopper/ivkp5sdo |
| GraphHopper | Transportation | publicapi.dev | no | https://publicapi.dev/graph-hopper-api |
| Impala Hotel Bookings | Transportation | publicapi.dev | no | https://publicapi.dev/impala-hotel-bookings-api |
| Izi | Transportation | publicapis.dev | unknown | https://publicapis.dev/resource/izi/d1iowbs8 |
| Izi | Transportation | publicapi.dev | no | https://publicapi.dev/izi-api |
| Land Transport Authority DataMall, Singapore | Transportation | publicapis.dev | unknown | https://publicapis.dev/resource/land-transport-authority-datamall-singapore/ecuexexi |
| Land Transport Authority DataMall, Singapore | Transportation | publicapi.dev | no | https://publicapi.dev/land-transport-authority-data-mall-singapore-api |
| Navitia | Transportation | publicapis.dev | unknown | https://publicapis.dev/resource/navitia/5tgzdut0 |
| Navitia | Transportation | publicapi.dev | no | https://publicapi.dev/navitia-api |
| Open Charge Map | Transportation | publicapis.dev | yes | https://publicapis.dev/resource/open-charge-map/kcfrgieq |
| Open Charge Map | Transportation | publicapi.dev | yes | https://publicapi.dev/open-charge-map-api |
| PlateAPI | Transportation | publicapis.dev | no | https://publicapis.dev/resource/plateapi/qirddtqc |
| Railway Transport for France | Transportation | publicapi.dev | no | https://publicapi.dev/railway-transport-for-france-api |
| Road511 | Transportation | publicapis.dev | yes | https://publicapis.dev/resource/road511/gvaa83xo |
| Sabre for Developers | Transportation | publicapis.dev | unknown | https://publicapis.dev/resource/sabre-for-developers/bkoqapei |
| Sabre for Developers | Transportation | publicapi.dev | no | https://publicapi.dev/sabre-for-developers-api |
| Schiphol Airport | Transportation | publicapis.dev | unknown | https://publicapis.dev/resource/schiphol-airport/tprvclzw |
| Schiphol Airport | Transportation | publicapi.dev | no | https://publicapi.dev/schiphol-airport-api |
| StayingAPI | Transportation | publicapis.dev | yes | https://publicapis.dev/resource/stayingapi/z2dl2ola |
| Swedavia Airports | Transportation | publicapis.dev | no | https://publicapis.dev/resource/swedavia-airports/8hqozp7l |
| Swedavia Airports | Transportation | publicapi.dev | no | https://publicapi.dev/swedavia-airports-api |
| Tankerkoenig | Transportation | publicapis.dev | yes | https://publicapis.dev/resource/tankerkoenig/uv02rvvc |
| Tankerkoenig | Transportation | publicapi.dev | yes | https://publicapi.dev/tankerkoenig-api |
| Trackr TMS | Transportation | publicapis.dev | no | https://publicapis.dev/resource/trackr-tms/xehdy1yz |
| Transport for Bordeaux, France | Transportation | publicapis.dev | unknown | https://publicapis.dev/resource/transport-for-bordeaux-france/f3mln5jn |
| Transport for Bordeaux, France | Transportation | publicapi.dev | no | https://publicapi.dev/transport-for-bordeaux-france-api |
| Transport for Chicago, US | Transportation | publicapis.dev | unknown | https://publicapis.dev/resource/transport-for-chicago-us/u1b5jfax |
| Transport for Chicago, US | Transportation | publicapi.dev | no | https://publicapi.dev/transport-for-chicago-us-api |
| Transport for Finland | Transportation | publicapis.dev | unknown | https://publicapis.dev/resource/transport-for-finland/r8cpvso3 |
| Transport for Finland | Transportation | publicapi.dev | no | https://publicapi.dev/transport-for-finland-api |
| Transport for Germany | Transportation | publicapi.dev | no | https://publicapi.dev/transport-for-germany-api |
| Transport for Honolulu, US | Transportation | publicapis.dev | unknown | https://publicapis.dev/resource/transport-for-honolulu-us/vdkt46zh |
| Transport for Honolulu, US | Transportation | publicapi.dev | no | https://publicapi.dev/transport-for-honolulu-us-api |
| Transport for Lisbon, Portugal | Transportation | publicapis.dev | unknown | https://publicapis.dev/resource/transport-for-lisbon-portugal/fl6mhp8z |
| Transport for Lisbon, Portugal | Transportation | publicapi.dev | no | https://publicapi.dev/transport-for-lisbon-portugal-api |
| Transport for London, England | Transportation | publicapis.dev | unknown | https://publicapis.dev/resource/transport-for-london-england/91ni26zi |
| Transport for London, England | Transportation | publicapi.dev | no | https://publicapi.dev/transport-for-london-england-api |
| Transport for Manchester, England | Transportation | publicapi.dev | no | https://publicapi.dev/transport-for-manchester-england-api |
| Transport for Ottawa, Canada | Transportation | publicapis.dev | unknown | https://publicapis.dev/resource/transport-for-ottawa-canada/czbpm6hx |
| Transport for Ottawa, Canada | Transportation | publicapi.dev | no | https://publicapi.dev/transport-for-ottawa-canada-api |
| Transport for Switzerland | Transportation | publicapis.dev | unknown | https://publicapis.dev/resource/transport-for-switzerland/ngzj9ewx |
| Transport for The Netherlands | Transportation | publicapis.dev | unknown | https://publicapis.dev/resource/transport-for-the-netherlands/1aiyjeij |
| Transport for UK | Transportation | publicapis.dev | unknown | https://publicapis.dev/resource/transport-for-uk/uynpdxyk |
| Transport for UK | Transportation | publicapi.dev | no | https://publicapi.dev/transport-for-uk-api |
| Tripadvisor | Transportation | publicapis.dev | unknown | https://publicapis.dev/resource/tripadvisor/slaoqsni |
| Tripadvisor | Transportation | publicapi.dev | no | https://publicapi.dev/tripadvisor-api |
| Bly.to | URL Shorteners | publicapis.dev | yes | https://publicapis.dev/resource/bly-to/x5mw3pg6 |
| ClickMeter | URL Shorteners | publicapi.dev | no | https://publicapi.dev/click-meter-api |
| Clico | URL Shorteners | publicapi.dev | no | https://publicapi.dev/clico-api |
| Cutt.ly | URL Shorteners | publicapis.dev | unknown | https://publicapis.dev/resource/cutt-ly/ju59yjyn |
| Cutt.ly | URL Shorteners | publicapi.dev | no | https://publicapi.dev/cutt-ly-api |
| Kutt | URL Shorteners | publicapi.dev | yes | https://publicapi.dev/kutt-api |
| Linkly | URL Shorteners | publicapis.dev | yes | https://publicapis.dev/resource/linkly/3bbpcuv9 |
| Manyapis | URL Shorteners | publicapis.dev | yes | https://publicapis.dev/resource/manyapis/dk8r7jmo |
| Manyapis.com | URL Shorteners | publicapi.dev | yes | https://publicapi.dev/manyapis-com-api |
| Rebrandly | URL Shorteners | publicapis.dev | unknown | https://publicapis.dev/resource/rebrandly/8gcocqjw |
| Rebrandly | URL Shorteners | publicapi.dev | no | https://publicapi.dev/rebrandly-api |
| Shrtlnk | URL Shorteners | publicapi.dev | yes | https://publicapi.dev/shrtlnk-api |
| T.LY | URL Shorteners | publicapis.dev | yes | https://publicapis.dev/resource/t-ly/g0hehpjh |
| TinyURL | URL Shorteners | publicapis.dev | no | https://publicapis.dev/resource/tinyurl/n1r652aq |
| TinyURL | URL Shorteners | publicapi.dev | no | https://publicapi.dev/tiny-url-api |
| UrlBae | URL Shorteners | publicapi.dev | yes | https://publicapi.dev/url-bae-api |
| Auto.dev | Vehicle | publicapis.dev | unknown | https://publicapis.dev/resource/auto-dev/dli9i48t |
| Car List API | Vehicle | publicapis.dev | yes | https://publicapis.dev/resource/car-list-api/m4j245cf |
| CarAPI | Vehicle | publicapis.dev | no | https://publicapis.dev/resource/carapi/6fdtj9g8 |
| CarsXE | Vehicle | publicapis.dev | no | https://publicapis.dev/resource/carsxe/empu9den |
| CarVector | Vehicle | publicapis.dev | yes | https://publicapis.dev/resource/carvector/5lfofecj |
| Helipaddy sites | Vehicle | publicapis.dev | unknown | https://publicapis.dev/resource/helipaddy-sites/c8wbp6rf |
| Helipaddy sites | Vehicle | publicapi.dev | no | https://publicapi.dev/helipaddy-sites-api |
| Kelley Blue Book | Vehicle | publicapis.dev | no | https://publicapis.dev/resource/kelley-blue-book/a0l2npvp |
| Kelley Blue Book | Vehicle | publicapi.dev | no | https://publicapi.dev/kelley-blue-book-api |
| Mercedes-Benz | Vehicle | publicapis.dev | no | https://publicapis.dev/resource/mercedes-benz/74hairmt |
| Mercedes-Benz | Vehicle | publicapi.dev | no | https://publicapi.dev/mercedes-benz-api |
| TrafficEye.AI | Vehicle | publicapis.dev | unknown | https://publicapis.dev/resource/trafficeye-ai/h9s9ukeg |
| Compress Video API | Video | publicapis.dev | yes | https://publicapis.dev/resource/compress-video-api/qrpmxqb9 |
| Extract Audio from Video API | Video | publicapis.dev | yes | https://publicapis.dev/resource/extract-audio-from-video-api/dc0o6wny |
| Gcore Streaming | Video | publicapis.dev | yes | https://publicapis.dev/resource/gcore-streaming/e2aan35t |
| Gcore Streaming | Video | publicapi.dev | yes | https://publicapi.dev/gcore-streaming-api |
| Generate Thumbnail from video API | Video | publicapis.dev | yes | https://publicapis.dev/resource/generate-thumbnail-from-video-api/sjjtna4q |
| Generate Thumbnail from video API | Video | publicapi.dev | yes | https://publicapi.dev/generate-thumbnail-from-video-api |
| GIF from Video API | Video | publicapis.dev | yes | https://publicapis.dev/resource/gif-from-video-api/yqu29rxb |
| Hunt | Video | publicapis.dev | yes | https://publicapis.dev/resource/hunt/rppob45n |
| Hyperserve | Video | publicapis.dev | yes | https://publicapis.dev/resource/hyperserve/pnsk4n7g |
| iLoveVideoEditor | Video | publicapis.dev | no | https://publicapis.dev/resource/ilovevideoeditor/r5mol3js |
| IMDb | Video | publicapis.dev | unknown | https://publicapis.dev/resource/imdb/uemu7ug6 |
| IMDb | Video | publicapi.dev | no | https://publicapi.dev/im-db-api |
| JSON2Video | Video | publicapis.dev | no | https://publicapis.dev/resource/json2video/y984hoqc |
| JSON2Video | Video | publicapi.dev | no | https://publicapi.dev/json2video-api |
| Mux | Video | publicapis.dev | unknown | https://publicapis.dev/resource/mux/ohxsz5v8 |
| Mux | Video | publicapi.dev | no | https://publicapi.dev/mux-api |
| Open Movie Database | Video | publicapis.dev | unknown | https://publicapis.dev/resource/open-movie-database/hbtair0t |
| Open Movie Database | Video | publicapi.dev | no | https://publicapi.dev/open-movie-database-api |
| Rendi | Video | publicapis.dev | no | https://publicapis.dev/resource/rendi/f3mxrynw |
| Rendobar | Video | publicapis.dev | no | https://publicapis.dev/resource/rendobar/z1jcaw5r |
| Shotstack | Video | publicapis.dev | unknown | https://publicapis.dev/resource/shotstack/53q97hc5 |
| Shotstack | Video | publicapi.dev | no | https://publicapi.dev/shotstack-api |
| Simkl | Video | publicapis.dev | unknown | https://publicapis.dev/resource/simkl/wiibxdmb |
| Simkl | Video | publicapi.dev | no | https://publicapi.dev/simkl-api |
| The Lord of the Rings | Video | publicapis.dev | unknown | https://publicapis.dev/resource/the-lord-of-the-rings/jjziyr22 |
| The Lord of the Rings | Video | publicapi.dev | no | https://publicapi.dev/the-lord-of-the-rings-api |
| The Vampire Diaries | Video | publicapis.dev | yes | https://publicapis.dev/resource/the-vampire-diaries/l06c6yvd |
| The Vampire Diaries | Video | publicapi.dev | yes | https://publicapi.dev/the-vampire-diaries-api |
| TMDb | Video | publicapis.dev | unknown | https://publicapis.dev/resource/tmdb/zzzvrgaj |
| TMDb | Video | publicapi.dev | no | https://publicapi.dev/tm-db-api |
| TrailerAddict | Video | publicapi.dev | no | https://publicapi.dev/trailer-addict-api |
| Trakt | Video | publicapis.dev | yes | https://publicapis.dev/resource/trakt/66p35dfz |
| Trakt | Video | publicapi.dev | yes | https://publicapi.dev/trakt-api |
| TranscriptAPI | Video | publicapis.dev | unknown | https://publicapis.dev/resource/transcriptapi/0m2j0iws |
| TVDB | Video | publicapis.dev | unknown | https://publicapis.dev/resource/tvdb/ui3sy4wf |
| TVDB | Video | publicapi.dev | no | https://publicapi.dev/tvdb-api |
| uNoGS | Video | publicapis.dev | yes | https://publicapis.dev/resource/unogs/73kt4m6p |
| uNoGS | Video | publicapi.dev | yes | https://publicapi.dev/u-no-gs-api |
| Video Thumbnail API | Video | publicapis.dev | yes | https://publicapis.dev/resource/video-thumbnail-api/jwaz71be |
| Watchmode | Video | publicapis.dev | unknown | https://publicapis.dev/resource/watchmode/k4qrv0w6 |
| Watchmode | Video | publicapi.dev | no | https://publicapi.dev/watchmode-api |
| AccuWeather | Weather | publicapis.dev | unknown | https://publicapis.dev/resource/accuweather/41xfy1oo |
| AccuWeather | Weather | publicapi.dev | no | https://publicapi.dev/accu-weather-api |
| Aemet | Weather | publicapis.dev | unknown | https://publicapis.dev/resource/aemet/634pmbiw |
| Aemet | Weather | publicapi.dev | no | https://publicapi.dev/aemet-api |
| apilayer weatherstack | Weather | publicapi.dev | no | https://publicapi.dev/apilayer-weatherstack-api |
| APIXU | Weather | publicapis.dev | unknown | https://publicapis.dev/resource/apixu/ob126zth |
| APIXU | Weather | publicapi.dev | no | https://publicapi.dev/apixu-api |
| AQICN | Weather | publicapis.dev | unknown | https://publicapis.dev/resource/aqicn/4bvhdbrf |
| AQICN | Weather | publicapi.dev | no | https://publicapi.dev/aqicn-api |
| ColorfulClouds | Weather | publicapis.dev | yes | https://publicapis.dev/resource/colorfulclouds/mzyuu6fk |
| ColorfulClouds | Weather | publicapi.dev | yes | https://publicapi.dev/colorful-clouds-api |
| Data Commons | Weather | publicapis.dev | unknown | https://publicapis.dev/resource/data-commons/tog9i8ls |
| Euskalmet | Weather | publicapis.dev | unknown | https://publicapis.dev/resource/euskalmet/o1rdrxbu |
| Euskalmet | Weather | publicapi.dev | no | https://publicapi.dev/euskalmet-api |
| GISTDA | Weather | publicapis.dev | unknown | https://publicapis.dev/resource/gistda/170mh3tt |
| GribStream | Weather | publicapis.dev | unknown | https://publicapis.dev/resource/gribstream/k7bgl2vo |
| HG Weather | Weather | publicapis.dev | yes | https://publicapis.dev/resource/hg-weather/3a5shqha |
| HG Weather | Weather | publicapi.dev | yes | https://publicapi.dev/hg-weather-api |
| Korea Meteorological Administration | Weather | publicapis.dev | unknown | https://publicapis.dev/resource/korea-meteorological-administration/xap535oc |
| Korea Meteorological Administration | Weather | publicapi.dev | no | https://publicapi.dev/korea-meteorological-administration-api |
| Met Office DataPoint | Weather | publicapis.dev | unknown | https://publicapis.dev/resource/met-office-datapoint/awpoppao |
| Met Office DataPoint | Weather | publicapi.dev | no | https://publicapi.dev/met-office-data-point-api |
| Meteoblue | Weather | publicapis.dev | yes | https://publicapis.dev/resource/meteoblue/sbxbbpp1 |
| Meteosource | Weather | publicapis.dev | yes | https://publicapis.dev/resource/meteosource/b6aqi3et |
| Meteosource | Weather | publicapi.dev | no | https://publicapi.dev/meteosource-api |
| Micro Weather | Weather | publicapi.dev | no | https://publicapi.dev/micro-weather-api |
| Oikolab | Weather | publicapis.dev | yes | https://publicapis.dev/resource/oikolab/xtdnu6ft |
| Oikolab | Weather | publicapi.dev | yes | https://publicapi.dev/oikolab-api |
| OpenUV | Weather | publicapis.dev | unknown | https://publicapis.dev/resource/openuv/q9ay4lio |
| OpenUV | Weather | publicapi.dev | no | https://publicapi.dev/open-uv-api |
| OpenWeatherMap | Weather | publicapis.dev | unknown | https://publicapis.dev/resource/openweathermap/394ottxv |
| OpenWeatherMap | Weather | publicapi.dev | no | https://publicapi.dev/open-weather-map-api |
| QWeather | Weather | publicapis.dev | yes | https://publicapis.dev/resource/qweather/l5im4fvj |
| QWeather | Weather | publicapi.dev | yes | https://publicapi.dev/q-weather-api |
| Storm Glass | Weather | publicapis.dev | yes | https://publicapis.dev/resource/storm-glass/rwqebptv |
| Storm Glass | Weather | publicapi.dev | yes | https://publicapi.dev/storm-glass-api |
| Tomorrow | Weather | publicapis.dev | unknown | https://publicapis.dev/resource/tomorrow/foqhghfg |
| Tomorrow | Weather | publicapi.dev | no | https://publicapi.dev/tomorrow-api |
| Visual Crossing | Weather | publicapis.dev | yes | https://publicapis.dev/resource/visual-crossing/zx4xwi9u |
| Visual Crossing | Weather | publicapi.dev | yes | https://publicapi.dev/visual-crossing-api |
| WeatherAPI | Weather | publicapis.dev | yes | https://publicapis.dev/resource/weatherapi/zqo5nqqz |
| WeatherAPI | Weather | publicapi.dev | yes | https://publicapi.dev/weather-api |
| Weatherbit | Weather | publicapis.dev | unknown | https://publicapis.dev/resource/weatherbit/phodl2c0 |
| Weatherbit | Weather | publicapi.dev | no | https://publicapi.dev/weatherbit-api |
| Yandex.Weather | Weather | publicapis.dev | no | https://publicapis.dev/resource/yandex-weather/bzqiscdg |
| Yandex.Weather | Weather | publicapi.dev | no | https://publicapi.dev/yandex-weather-api |

### OAuth (311)

| Nama | Kategori | Situs | CORS | Tautan |
|---|---|---|---|---|
| Clarifai | AI | publicapis.dev | unknown | https://publicapis.dev/resource/clarifai/s2602w42 |
| Clarifai | AI | publicapi.dev | no | https://publicapi.dev/clarifai-api |
| AniAPI | Anime | publicapi.dev | yes | https://publicapi.dev/ani-api |
| AniList | Anime | publicapis.dev | unknown | https://publicapis.dev/resource/anilist/n4ellt8t |
| AniList | Anime | publicapi.dev | no | https://publicapi.dev/ani-list-api |
| Kitsu | Anime | publicapis.dev | yes | https://publicapis.dev/resource/kitsu/byerplhi |
| Kitsu | Anime | publicapi.dev | yes | https://publicapi.dev/kitsu-api |
| MyAnimeList | Anime | publicapis.dev | unknown | https://publicapis.dev/resource/myanimelist/bcqm7aya |
| MyAnimeList | Anime | publicapi.dev | no | https://publicapi.dev/my-anime-list-api |
| Nekos API | Anime | publicapis.dev | yes | https://publicapis.dev/resource/nekos-api/5kmgfaxy |
| Nekos API | Anime | publicapi.dev | yes | https://publicapi.dev/nekos-api |
| Shikimori | Anime | publicapis.dev | unknown | https://publicapis.dev/resource/shikimori/59jmnbg0 |
| Shikimori | Anime | publicapi.dev | no | https://publicapi.dev/shikimori-api |
| Dribbble | Art & Design | publicapis.dev | unknown | https://publicapis.dev/resource/dribbble/wjujxenu |
| Dribbble | Art & Design | publicapi.dev | no | https://publicapi.dev/dribbble-api |
| Noun Project | Art & Design | publicapis.dev | unknown | https://publicapis.dev/resource/noun-project/ci7g79fn |
| Noun Project | Art & Design | publicapi.dev | no | https://publicapi.dev/noun-project-api |
| Savee | Art & Design | publicapi.dev | yes | https://publicapi.dev/savee-api |
| Kinde | Authentication & Authorization | publicapis.dev | no | https://publicapis.dev/resource/kinde/z85lgucq |
| Kinde | Authentication & Authorization | publicapi.dev | no | https://publicapi.dev/kinde-api |
| Bhagavad Gita | Books | publicapi.dev | yes | https://publicapi.dev/bhagavad-gita-api |
| Ganjoor | Books | publicapis.dev | yes | https://publicapis.dev/resource/ganjoor/xw15vvpe |
| Ganjoor | Books | publicapi.dev | yes | https://publicapi.dev/ganjoor-api |
| Google Books | Books | publicapis.dev | unknown | https://publicapis.dev/resource/google-books/5bp7jowa |
| Google Books | Books | publicapi.dev | no | https://publicapi.dev/google-books-api |
| Freelancer | Business | publicapis.dev | unknown | https://publicapis.dev/resource/freelancer/40x4ipcf |
| Freelancer | Business | publicapi.dev | no | https://publicapi.dev/freelancer-api |
| Gmail | Business | publicapis.dev | unknown | https://publicapis.dev/resource/gmail/ebnk5q45 |
| Gmail | Business | publicapi.dev | no | https://publicapi.dev/gmail-api |
| Google Analytics | Business | publicapis.dev | unknown | https://publicapis.dev/resource/google-analytics/c7w3s8ai |
| Google Analytics | Business | publicapi.dev | no | https://publicapi.dev/google-analytics-api |
| Smartsheet | Business | publicapis.dev | no | https://publicapis.dev/resource/smartsheet/zh3ucoch |
| Smartsheet | Business | publicapi.dev | no | https://publicapi.dev/smartsheet-api |
| Square | Business | publicapis.dev | unknown | https://publicapis.dev/resource/square/ztvu41m2 |
| Square | Business | publicapi.dev | no | https://publicapi.dev/square-api |
| Trello | Business | publicapis.dev | unknown | https://publicapis.dev/resource/trello/43bhzht6 |
| Trello | Business | publicapi.dev | no | https://publicapi.dev/trello-api |
| Google Calendar | Calendar | publicapis.dev | unknown | https://publicapis.dev/resource/google-calendar/wi9hzl3a |
| Google Calendar | Calendar | publicapi.dev | no | https://publicapi.dev/google-calendar-api |
| Box | Cloud Storage & File Sharing | publicapis.dev | unknown | https://publicapis.dev/resource/box/jr8l4c4l |
| Box | Cloud Storage & File Sharing | publicapi.dev | no | https://publicapi.dev/box-api |
| Dropbox | Cloud Storage & File Sharing | publicapis.dev | unknown | https://publicapis.dev/resource/dropbox/m20h1kir |
| Dropbox | Cloud Storage & File Sharing | publicapi.dev | no | https://publicapi.dev/dropbox-api |
| Google Drive | Cloud Storage & File Sharing | publicapis.dev | unknown | https://publicapis.dev/resource/google-drive/72e3cm51 |
| Google Drive | Cloud Storage & File Sharing | publicapi.dev | no | https://publicapi.dev/google-drive-api |
| OneDrive | Cloud Storage & File Sharing | publicapis.dev | unknown | https://publicapis.dev/resource/onedrive/wkqzen1a |
| OneDrive | Cloud Storage & File Sharing | publicapi.dev | no | https://publicapi.dev/one-drive-api |
| Buddy | Continuous Integration | publicapis.dev | unknown | https://publicapis.dev/resource/buddy/s0dufq99 |
| Buddy | Continuous Integration | publicapi.dev | no | https://publicapi.dev/buddy-api |
| Trading View | Cryptocurrency | publicapis.dev | unknown | https://publicapis.dev/resource/trading-view/qd3yewb5 |
| Trading View | Cryptocurrency | publicapi.dev | no | https://publicapi.dev/trading-view-api |
| apilayer userstack | Development | publicapi.dev | no | https://publicapi.dev/apilayer-userstack-api |
| Bitbucket | Development | publicapis.dev | unknown | https://publicapis.dev/resource/bitbucket/0z8awfec |
| Bitbucket | Development | publicapi.dev | no | https://publicapi.dev/bitbucket-api |
| GitHub | Development | publicapis.dev | yes | https://publicapis.dev/resource/github/f1xbii06 |
| GitHub | Development | publicapi.dev | yes | https://publicapi.dev/git-hub-api |
| Gitlab | Development | publicapis.dev | unknown | https://publicapis.dev/resource/gitlab/mp4c0r41 |
| Gitlab | Development | publicapi.dev | no | https://publicapi.dev/gitlab-api |
| Gitter | Development | publicapi.dev | no | https://publicapi.dev/gitter-api |
| Google Docs | Development | publicapis.dev | unknown | https://publicapis.dev/resource/google-docs/fuc0beyo |
| Google Docs | Development | publicapi.dev | no | https://publicapi.dev/google-docs-api |
| Google Keep | Development | publicapis.dev | unknown | https://publicapis.dev/resource/google-keep/8p1n71gi |
| Google Keep | Development | publicapi.dev | no | https://publicapi.dev/google-keep-api |
| Google Sheets | Development | publicapis.dev | unknown | https://publicapis.dev/resource/google-sheets/cq7xgqxf |
| Google Sheets | Development | publicapi.dev | no | https://publicapi.dev/google-sheets-api |
| Google Slides | Development | publicapis.dev | unknown | https://publicapis.dev/resource/google-slides/yhfyp2bt |
| Google Slides | Development | publicapi.dev | no | https://publicapi.dev/google-slides-api |
| Gorest | Development | publicapis.dev | unknown | https://publicapis.dev/resource/gorest/bax3ddvr |
| Gorest | Development | publicapi.dev | no | https://publicapi.dev/gorest-api |
| Heroku | Development | publicapis.dev | yes | https://publicapis.dev/resource/heroku/6nvvtm2i |
| Heroku | Development | publicapi.dev | yes | https://publicapi.dev/heroku-api |
| Insomnia | Development | publicapis.dev | unknown | https://publicapis.dev/resource/insomnia/yancmmbv |
| Insomnia | Development | publicapi.dev | no | https://publicapi.dev/insomnia-api |
| Netlify | Development | publicapis.dev | unknown | https://publicapis.dev/resource/netlify/pvu3p4ng |
| Netlify | Development | publicapi.dev | no | https://publicapi.dev/netlify-api |
| PipeDream | Development | publicapis.dev | unknown | https://publicapis.dev/resource/pipedream/rpwrj0yg |
| PipeDream | Development | publicapi.dev | no | https://publicapi.dev/pipe-dream-api |
| SonarQube | Development | publicapis.dev | unknown | https://publicapis.dev/resource/sonarqube/p7o7b37t |
| SonarQube | Development | publicapi.dev | no | https://publicapi.dev/sonar-qube-api |
| StackExchange | Development | publicapis.dev | unknown | https://publicapis.dev/resource/stackexchange/uvn1l6bf |
| StackExchange | Development | publicapi.dev | no | https://publicapi.dev/stack-exchange-api |
| ClickUp | Documents & Productivity | publicapis.dev | unknown | https://publicapis.dev/resource/clickup/h6ko9lzt |
| ClickUp | Documents & Productivity | publicapi.dev | no | https://publicapi.dev/click-up-api |
| JIRA | Documents & Productivity | publicapis.dev | unknown | https://publicapis.dev/resource/jira/7f6jyrwq |
| JIRA | Documents & Productivity | publicapi.dev | no | https://publicapi.dev/jira-api |
| Mattermost | Documents & Productivity | publicapis.dev | unknown | https://publicapis.dev/resource/mattermost/pivpqli4 |
| Mattermost | Documents & Productivity | publicapi.dev | no | https://publicapi.dev/mattermost-api |
| Notion | Documents & Productivity | publicapis.dev | unknown | https://publicapis.dev/resource/notion/mwngdfkq |
| Notion | Documents & Productivity | publicapi.dev | no | https://publicapi.dev/notion-api |
| Pocket | Documents & Productivity | publicapis.dev | unknown | https://publicapis.dev/resource/pocket/ud3iyde5 |
| Pocket | Documents & Productivity | publicapi.dev | no | https://publicapi.dev/pocket-api |
| Podio | Documents & Productivity | publicapis.dev | unknown | https://publicapis.dev/resource/podio/b09pp1b4 |
| Podio | Documents & Productivity | publicapi.dev | no | https://publicapi.dev/podio-api |
| Todoist | Documents & Productivity | publicapis.dev | unknown | https://publicapis.dev/resource/todoist/5dfrbjee |
| Todoist | Documents & Productivity | publicapi.dev | no | https://publicapi.dev/todoist-api |
| Zube | Documents & Productivity | publicapis.dev | unknown | https://publicapis.dev/resource/zube/cfu9er0i |
| Zube | Documents & Productivity | publicapi.dev | no | https://publicapi.dev/zube-api |
| Eventbrite | Events | publicapis.dev | unknown | https://publicapis.dev/resource/eventbrite/o503v3s4 |
| Eventbrite | Events | publicapi.dev | no | https://publicapi.dev/eventbrite-api |
| Banco do Brasil | Finance | publicapis.dev | yes | https://publicapis.dev/resource/banco-do-brasil/1om9wqb8 |
| Banco do Brasil | Finance | publicapi.dev | yes | https://publicapi.dev/banco-do-brasil-api |
| Front Accounting APIs | Finance | publicapis.dev | yes | https://publicapis.dev/resource/front-accounting-apis/6la9joif |
| Front Accounting APIs | Finance | publicapi.dev | yes | https://publicapi.dev/front-accounting-ap-is-api |
| Tradier | Finance | publicapis.dev | yes | https://publicapis.dev/resource/tradier/dh8159cb |
| Tradier | Finance | publicapi.dev | yes | https://publicapi.dev/tradier-api |
| YNAB | Finance | publicapis.dev | yes | https://publicapis.dev/resource/ynab/597vhp2a |
| YNAB | Finance | publicapi.dev | yes | https://publicapi.dev/ynab-api |
| Zoho Books | Finance | publicapis.dev | unknown | https://publicapis.dev/resource/zoho-books/hdb75n9q |
| Zoho Books | Finance | publicapi.dev | no | https://publicapi.dev/zoho-books-api |
| Untappd | Food & Drink | publicapis.dev | unknown | https://publicapis.dev/resource/untappd/iswfto5i |
| Untappd | Food & Drink | publicapi.dev | no | https://publicapi.dev/untappd-api |
| Battle.net | Games & Comics | publicapis.dev | yes | https://publicapis.dev/resource/battle-net/mfqhkelj |
| Battle.net | Games & Comics | publicapi.dev | yes | https://publicapi.dev/battle-net-api |
| Eve Online | Games & Comics | publicapi.dev | no | https://publicapi.dev/eve-online-api |
| Lichess | Games & Comics | publicapis.dev | unknown | https://publicapis.dev/resource/lichess/11vgfz1o |
| Lichess | Games & Comics | publicapi.dev | no | https://publicapi.dev/lichess-api |
| Mario Kart Tour | Games & Comics | publicapi.dev | no | https://publicapi.dev/mario-kart-tour-api |
| Path of Exile | Games & Comics | publicapis.dev | unknown | https://publicapis.dev/resource/path-of-exile/509nm63v |
| Path of Exile | Games & Comics | publicapi.dev | no | https://publicapi.dev/path-of-exile-api |
| SpaceTradersAPI | Games & Comics | publicapis.dev | yes | https://publicapis.dev/resource/spacetradersapi/98gnudmd |
| SpaceTradersAPI | Games & Comics | publicapi.dev | yes | https://publicapi.dev/space-traders-api |
| OpenStreetMap | Geocoding | publicapis.dev | unknown | https://publicapis.dev/resource/openstreetmap/bkj451ib |
| OpenStreetMap | Geocoding | publicapi.dev | no | https://publicapi.dev/open-street-map-api |
| Gazette Data, UK | Government | publicapis.dev | unknown | https://publicapis.dev/resource/gazette-data-uk/wdwuc8xs |
| Gazette Data, UK | Government | publicapi.dev | no | https://publicapi.dev/gazette-data-uk-api |
| Open Government, Greece | Government | publicapis.dev | unknown | https://publicapis.dev/resource/open-government-greece/lqdsmnsu |
| Open Government, Greece | Government | publicapi.dev | no | https://publicapi.dev/open-government-greece-api |
| OpenRegistry | Government | publicapis.dev | unknown | https://publicapis.dev/resource/openregistry/vs5w2gw4 |
| UK Companies House | Government | publicapis.dev | unknown | https://publicapis.dev/resource/uk-companies-house/hh55txli |
| UK Companies House | Government | publicapi.dev | no | https://publicapi.dev/uk-companies-house-api |
| Orion Health | Health | publicapi.dev | no | https://publicapi.dev/orion-health-api |
| Arbeitsamt | Jobs | publicapis.dev | unknown | https://publicapis.dev/resource/arbeitsamt/hdc8790p |
| Arbeitsamt | Jobs | publicapi.dev | no | https://publicapi.dev/arbeitsamt-api |
| Upwork | Jobs | publicapis.dev | unknown | https://publicapis.dev/resource/upwork/gs32m331 |
| Upwork | Jobs | publicapi.dev | no | https://publicapi.dev/upwork-api |
| Clarifai | Machine Learning | publicapi.dev | no | https://publicapi.dev/clarifai-api |
| 7digital | Music | publicapis.dev | unknown | https://publicapis.dev/resource/7digital/rxt68z1o |
| 7digital | Music | publicapi.dev | no | https://publicapi.dev/7digital-api |
| Audiomack | Music | publicapis.dev | unknown | https://publicapis.dev/resource/audiomack/51zypny1 |
| Audiomack | Music | publicapi.dev | no | https://publicapi.dev/audiomack-api |
| Bandcamp | Music | publicapis.dev | unknown | https://publicapis.dev/resource/bandcamp/oqbs72u7 |
| Bandcamp | Music | publicapi.dev | no | https://publicapi.dev/bandcamp-api |
| Deezer | Music | publicapis.dev | unknown | https://publicapis.dev/resource/deezer/127nmb2g |
| Deezer | Music | publicapi.dev | no | https://publicapi.dev/deezer-api |
| Discogs | Music | publicapis.dev | unknown | https://publicapis.dev/resource/discogs/62dgplds |
| Discogs | Music | publicapis.dev | unknown | https://publicapis.dev/resource/discogs/gt7co86z |
| Discogs | Music | publicapi.dev | no | https://publicapi.dev/discogs-api |
| Genius | Music | publicapis.dev | unknown | https://publicapis.dev/resource/genius/ro8ulu1u |
| Genius | Music | publicapi.dev | no | https://publicapi.dev/genius-api |
| Jamendo | Music | publicapis.dev | unknown | https://publicapis.dev/resource/jamendo/7qgkegg7 |
| Jamendo | Music | publicapi.dev | no | https://publicapi.dev/jamendo-api |
| KKBOX | Music | publicapis.dev | unknown | https://publicapis.dev/resource/kkbox/ku4v60rq |
| KKBOX | Music | publicapi.dev | no | https://publicapi.dev/kkbox-api |
| Mixcloud | Music | publicapis.dev | yes | https://publicapis.dev/resource/mixcloud/1bk6ekes |
| Mixcloud | Music | publicapi.dev | yes | https://publicapi.dev/mixcloud-api |
| SoundCloud | Music | publicapis.dev | unknown | https://publicapis.dev/resource/soundcloud/ho3etrgu |
| SoundCloud | Music | publicapi.dev | no | https://publicapi.dev/sound-cloud-api |
| Spotify | Music | publicapis.dev | unknown | https://publicapis.dev/resource/spotify/eudzna0k |
| Spotify | Music | publicapi.dev | no | https://publicapi.dev/spotify-api |
| Feedbin | News | publicapis.dev | unknown | https://publicapis.dev/resource/feedbin/ftg7uhlq |
| Feedbin | News | publicapi.dev | no | https://publicapi.dev/feedbin-api |
| NPR One | News | publicapi.dev | no | https://publicapi.dev/npr-one-api |
| GENESIS | Open Data | publicapis.dev | unknown | https://publicapis.dev/resource/genesis/pa54l0ps |
| GENESIS | Open Data | publicapi.dev | no | https://publicapi.dev/genesis-api |
| Socrata | Open Data | publicapis.dev | yes | https://publicapis.dev/resource/socrata/r0rk2x9y |
| Socrata | Open Data | publicapi.dev | yes | https://publicapi.dev/socrata-api |
| Wikidata | Open Data | publicapis.dev | unknown | https://publicapis.dev/resource/wikidata/vx1mn86s |
| Wikidata | Open Data | publicapi.dev | no | https://publicapi.dev/wikidata-api |
| Yelp | Open Data | publicapis.dev | unknown | https://publicapis.dev/resource/yelp/6eg39tck |
| Yelp | Open Data | publicapi.dev | no | https://publicapi.dev/yelp-api |
| Creative Commons Catalog | Open Source Projects | publicapis.dev | yes | https://publicapis.dev/resource/creative-commons-catalog/zzimd5ax |
| Creative Commons Catalog | Open Source Projects | publicapi.dev | yes | https://publicapi.dev/creative-commons-catalog-api |
| EPO | Patent | publicapis.dev | unknown | https://publicapis.dev/resource/epo/woih1k9z |
| EPO | Patent | publicapi.dev | no | https://publicapi.dev/epo-api |
| Medium | Personality | publicapis.dev | unknown | https://publicapis.dev/resource/medium/885dtu3h |
| Medium | Personality | publicapi.dev | no | https://publicapi.dev/medium-api |
| Flickr | Photography | publicapis.dev | unknown | https://publicapis.dev/resource/flickr/rsgl1kdk |
| Flickr | Photography | publicapi.dev | no | https://publicapi.dev/flickr-api |
| Getty Images | Photography | publicapis.dev | unknown | https://publicapis.dev/resource/getty-images/z7pai93i |
| Getty Images | Photography | publicapi.dev | no | https://publicapi.dev/getty-images-api |
| Gfycat | Photography | publicapi.dev | no | https://publicapi.dev/gfycat-api |
| Google Photos | Photography | publicapis.dev | unknown | https://publicapis.dev/resource/google-photos/cq40z5uj |
| Google Photos | Photography | publicapi.dev | no | https://publicapi.dev/google-photos-api |
| Imgur | Photography | publicapis.dev | unknown | https://publicapis.dev/resource/imgur/3d1t1jbu |
| Imgur | Photography | publicapi.dev | no | https://publicapi.dev/imgur-api |
| shutterstock | Photography | publicapis.dev | unknown | https://publicapis.dev/resource/shutterstock/6qiyevvo |
| shutterstock | Photography | publicapi.dev | no | https://publicapi.dev/shutterstock-api |
| Unsplash | Photography | publicapis.dev | unknown | https://publicapis.dev/resource/unsplash/u1kn2xax |
| Unsplash | Photography | publicapi.dev | no | https://publicapi.dev/unsplash-api |
| Webdam | Photography | publicapis.dev | unknown | https://publicapis.dev/resource/webdam/xflqby7w |
| Webdam | Photography | publicapi.dev | no | https://publicapi.dev/webdam-api |
| Spotify | Podcasts | publicapis.dev | unknown | https://publicapis.dev/resource/spotify/e3iewt3a |
| Spotify | Podcasts | publicapi.dev | no | https://publicapi.dev/spotify-api |
| Expired Domains API \| Karma.Domains | Programming | publicapis.dev | unknown | https://publicapis.dev/resource/expired-domains-api-karma-domains/q46lack0 |
| NASA ADS | Science & Math | publicapis.dev | yes | https://publicapis.dev/resource/nasa-ads/7jerivaf |
| NASA ADS | Science & Math | publicapi.dev | yes | https://publicapi.dev/nasa-ads-api |
| Open Science Framework | Science & Math | publicapis.dev | unknown | https://publicapis.dev/resource/open-science-framework/p0vxnk2r |
| BitWarden | Security | publicapis.dev | unknown | https://publicapis.dev/resource/bitwarden/34fgdq96 |
| BitWarden | Security | publicapi.dev | no | https://publicapi.dev/bit-warden-api |
| Digi-Key | Shopping | publicapis.dev | unknown | https://publicapis.dev/resource/digi-key/k29znd4y |
| Digi-Key | Shopping | publicapi.dev | no | https://publicapi.dev/digi-key-api |
| eBay | Shopping | publicapis.dev | unknown | https://publicapis.dev/resource/ebay/x0bzkvf6 |
| eBay | Shopping | publicapi.dev | no | https://publicapi.dev/e-bay-api |
| Etsy | Shopping | publicapis.dev | unknown | https://publicapis.dev/resource/etsy/9sgqlznl |
| Etsy | Shopping | publicapi.dev | no | https://publicapi.dev/etsy-api |
| Flipkart Marketplace | Shopping | publicapis.dev | yes | https://publicapis.dev/resource/flipkart-marketplace/xcc2qccn |
| Flipkart Marketplace | Shopping | publicapi.dev | yes | https://publicapi.dev/flipkart-marketplace-api |
| Printful | Shopping | publicapis.dev | unknown | https://publicapis.dev/resource/printful/3n8nndfw |
| Printful | Shopping | publicapi.dev | no | https://publicapi.dev/printful-api |
| Printify | Shopping | publicapis.dev | unknown | https://publicapis.dev/resource/printify/7gr5sd63 |
| Printify | Shopping | publicapi.dev | no | https://publicapi.dev/printify-api |
| Rappi | Shopping | publicapis.dev | unknown | https://publicapis.dev/resource/rappi/teabhsg1 |
| Rappi | Shopping | publicapi.dev | no | https://publicapi.dev/rappi-api |
| Shopify | Shopping | publicapis.dev | unknown | https://publicapis.dev/resource/shopify/999zy0b4 |
| Tokopedia | Shopping | publicapis.dev | unknown | https://publicapis.dev/resource/tokopedia/g2vcjf9p |
| Tokopedia | Shopping | publicapi.dev | no | https://publicapi.dev/tokopedia-api |
| Blogger | Social | publicapis.dev | unknown | https://publicapis.dev/resource/blogger/gm3nd093 |
| Blogger | Social | publicapi.dev | no | https://publicapi.dev/blogger-api |
| Cisco Spark | Social | publicapi.dev | no | https://publicapi.dev/cisco-spark-api |
| Discord | Social | publicapis.dev | unknown | https://publicapis.dev/resource/discord/j67bou1c |
| Discord | Social | publicapi.dev | no | https://publicapi.dev/discord-api |
| Disqus | Social | publicapis.dev | unknown | https://publicapis.dev/resource/disqus/f4ppuniq |
| Disqus | Social | publicapi.dev | no | https://publicapi.dev/disqus-api |
| Facebook | Social | publicapis.dev | unknown | https://publicapis.dev/resource/facebook/gncx940p |
| Facebook | Social | publicapi.dev | no | https://publicapi.dev/facebook-api |
| Foursquare | Social | publicapis.dev | unknown | https://publicapis.dev/resource/foursquare/dw44486t |
| Foursquare | Social | publicapi.dev | no | https://publicapi.dev/foursquare-api |
| Full Contact | Social | publicapis.dev | unknown | https://publicapis.dev/resource/full-contact/zwaxok7n |
| Full Contact | Social | publicapi.dev | no | https://publicapi.dev/full-contact-api |
| Instagram | Social | publicapis.dev | unknown | https://publicapis.dev/resource/instagram/0jczovfx |
| Instagram | Social | publicapi.dev | no | https://publicapi.dev/instagram-api |
| Kakao | Social | publicapis.dev | unknown | https://publicapis.dev/resource/kakao/1d8ixvp1 |
| Kakao | Social | publicapi.dev | no | https://publicapi.dev/kakao-api |
| Line | Social | publicapis.dev | unknown | https://publicapis.dev/resource/line/txlf45vn |
| Line | Social | publicapi.dev | no | https://publicapi.dev/line-api |
| LinkedIn | Social | publicapis.dev | unknown | https://publicapis.dev/resource/linkedin/eid374yf |
| LinkedIn | Social | publicapi.dev | no | https://publicapi.dev/linked-in-api |
| Microsoft Graph | Social | publicapis.dev | unknown | https://publicapis.dev/resource/microsoft-graph/slsaccrk |
| Microsoft Graph | Social | publicapi.dev | no | https://publicapi.dev/microsoft-graph-api |
| NAVER | Social | publicapis.dev | unknown | https://publicapis.dev/resource/naver/6llxcrld |
| NAVER | Social | publicapi.dev | no | https://publicapi.dev/naver-api |
| Pinterest | Social | publicapis.dev | unknown | https://publicapis.dev/resource/pinterest/otj41ikx |
| Pinterest | Social | publicapi.dev | no | https://publicapi.dev/pinterest-api |
| Product Hunt | Social | publicapis.dev | unknown | https://publicapis.dev/resource/product-hunt/yor2pmt2 |
| Product Hunt | Social | publicapi.dev | no | https://publicapi.dev/product-hunt-api |
| Reddit | Social | publicapis.dev | unknown | https://publicapis.dev/resource/reddit/nw0pv1mj |
| Reddit | Social | publicapi.dev | no | https://publicapi.dev/reddit-api |
| Saidit | Social | publicapi.dev | no | https://publicapi.dev/saidit-api |
| Slack | Social | publicapis.dev | unknown | https://publicapis.dev/resource/slack/c9d3olpu |
| Slack | Social | publicapi.dev | no | https://publicapi.dev/slack-api |
| Telegram MTProto | Social | publicapis.dev | unknown | https://publicapis.dev/resource/telegram-mtproto/2y7bpotu |
| Telegram MTProto | Social | publicapi.dev | no | https://publicapi.dev/telegram-mt-proto-api |
| TikTok | Social | publicapis.dev | unknown | https://publicapis.dev/resource/tiktok/t8iun93a |
| TikTok | Social | publicapi.dev | no | https://publicapi.dev/tik-tok-api |
| Trash Nothing | Social | publicapis.dev | yes | https://publicapis.dev/resource/trash-nothing/hmxwk2ly |
| Trash Nothing | Social | publicapi.dev | yes | https://publicapi.dev/trash-nothing-api |
| Tumblr | Social | publicapis.dev | unknown | https://publicapis.dev/resource/tumblr/3ld8qdx3 |
| Tumblr | Social | publicapi.dev | no | https://publicapi.dev/tumblr-api |
| Twitch | Social | publicapis.dev | unknown | https://publicapis.dev/resource/twitch/azyevw5g |
| Twitch | Social | publicapi.dev | no | https://publicapi.dev/twitch-api |
| Twitter | Social | publicapis.dev | no | https://publicapis.dev/resource/twitter/2jnl5rfo |
| Twitter | Social | publicapi.dev | no | https://publicapi.dev/twitter-api |
| vk | Social | publicapis.dev | unknown | https://publicapis.dev/resource/vk/qf9ul8nj |
| vk | Social | publicapi.dev | no | https://publicapi.dev/vk-api |
| Webex | Social | publicapis.dev | yes | https://publicapis.dev/resource/webex/76dsx1ic |
| Webex | Social | publicapi.dev | yes | https://publicapi.dev/webex-api |
| Zoom | Social | publicapis.dev | unknown | https://publicapis.dev/resource/zoom/mmpd18u4 |
| Fitbit | Sports & Fitness | publicapis.dev | unknown | https://publicapis.dev/resource/fitbit/egecq2iz |
| Fitbit | Sports & Fitness | publicapi.dev | no | https://publicapi.dev/fitbit-api |
| Strava | Sports & Fitness | publicapis.dev | unknown | https://publicapis.dev/resource/strava/miy0fxfi |
| Strava | Sports & Fitness | publicapi.dev | no | https://publicapi.dev/strava-api |
| Tredict | Sports & Fitness | publicapis.dev | unknown | https://publicapis.dev/resource/tredict/h4nk0utx |
| Tredict | Sports & Fitness | publicapi.dev | no | https://publicapi.dev/tredict-api |
| apilayer languagelayer | Text Analysis | publicapi.dev | no | https://publicapi.dev/apilayer-languagelayer-api |
| Code Detection API | Text Analysis | publicapis.dev | unknown | https://publicapis.dev/resource/code-detection-api/79tbn96u |
| Code Detection API | Text Analysis | publicapi.dev | no | https://publicapi.dev/code-detection-api |
| Semantria | Text Analysis | publicapis.dev | unknown | https://publicapis.dev/resource/semantria/m4hgd926 |
| Semantria | Text Analysis | publicapi.dev | no | https://publicapi.dev/semantria-api |
| Tisane | Text Analysis | publicapis.dev | yes | https://publicapis.dev/resource/tisane/37sawzb7 |
| Tisane | Text Analysis | publicapi.dev | yes | https://publicapi.dev/tisane-api |
| Watson Natural Language Understanding | Text Analysis | publicapis.dev | unknown | https://publicapis.dev/resource/watson-natural-language-understanding/qqphjm08 |
| Watson Natural Language Understanding | Text Analysis | publicapi.dev | no | https://publicapi.dev/watson-natural-language-understanding-api |
| Amadeus for Developers | Transportation | publicapis.dev | unknown | https://publicapis.dev/resource/amadeus-for-developers/q99407fi |
| Amadeus for Developers | Transportation | publicapi.dev | no | https://publicapi.dev/amadeus-for-developers-api |
| apilayer aviationstack | Transportation | publicapi.dev | no | https://publicapi.dev/apilayer-aviationstack-api |
| BIC-Boxtech | Transportation | publicapis.dev | unknown | https://publicapis.dev/resource/bic-boxtech/45hi6x6z |
| BIC-Boxtech | Transportation | publicapi.dev | no | https://publicapi.dev/bic-boxtech-api |
| Grab | Transportation | publicapis.dev | unknown | https://publicapis.dev/resource/grab/awsoi8mx |
| Grab | Transportation | publicapi.dev | no | https://publicapi.dev/grab-api |
| Transport for Sao Paulo, Brazil | Transportation | publicapis.dev | unknown | https://publicapis.dev/resource/transport-for-sao-paulo-brazil/kjq6fkjh |
| Transport for Sao Paulo, Brazil | Transportation | publicapi.dev | no | https://publicapi.dev/transport-for-sao-paulo-brazil-api |
| Transport for Sweden | Transportation | publicapis.dev | unknown | https://publicapis.dev/resource/transport-for-sweden/mpz2y5fn |
| Transport for Sweden | Transportation | publicapi.dev | no | https://publicapi.dev/transport-for-sweden-api |
| Transport for Vancouver, Canada | Transportation | publicapis.dev | unknown | https://publicapis.dev/resource/transport-for-vancouver-canada/ke16z82u |
| Transport for Vancouver, Canada | Transportation | publicapi.dev | no | https://publicapi.dev/transport-for-vancouver-canada-api |
| Transport for Washington, US | Transportation | publicapis.dev | unknown | https://publicapis.dev/resource/transport-for-washington-us/5jh1ntup |
| Transport for Washington, US | Transportation | publicapi.dev | no | https://publicapi.dev/transport-for-washington-us-api |
| Uber | Transportation | publicapis.dev | yes | https://publicapis.dev/resource/uber/72j0mxo4 |
| Uber | Transportation | publicapi.dev | yes | https://publicapi.dev/uber-api |
| Bitly | URL Shorteners | publicapis.dev | unknown | https://publicapis.dev/resource/bitly/idtwd10a |
| Bitly | URL Shorteners | publicapi.dev | no | https://publicapi.dev/bitly-api |
| Smartcar | Vehicle | publicapis.dev | yes | https://publicapis.dev/resource/smartcar/iuj08tv8 |
| Smartcar | Vehicle | publicapi.dev | yes | https://publicapi.dev/smartcar-api |
| Dailymotion | Video | publicapis.dev | unknown | https://publicapis.dev/resource/dailymotion/7v7et5qz |
| Dailymotion | Video | publicapi.dev | no | https://publicapi.dev/dailymotion-api |
| Vimeo | Video | publicapis.dev | unknown | https://publicapis.dev/resource/vimeo/egb33bim |
| Vimeo | Video | publicapi.dev | no | https://publicapi.dev/vimeo-api |
| YouTube | Video | publicapis.dev | unknown | https://publicapis.dev/resource/youtube/4124d96l |
| YouTube | Video | publicapi.dev | no | https://publicapi.dev/you-tube-api |
| Foreca | Weather | publicapis.dev | unknown | https://publicapis.dev/resource/foreca/hu2gr38u |
| Foreca | Weather | publicapi.dev | no | https://publicapi.dev/foreca-api |

### Bearer Token (1)

| Nama | Kategori | Situs | CORS | Tautan |
|---|---|---|---|---|
| Thordata SERP API | Development | publicapi.dev | no | https://publicapi.dev/thordata-serp-api |

### API Key + Bearer (3)

| Nama | Kategori | Situs | CORS | Tautan |
|---|---|---|---|---|
| SearchApi | Development | publicapi.dev | no | https://publicapi.dev/search-api |
| Webz.io News Search API | News | publicapi.dev | no | https://publicapi.dev/webz-io-news-search-api |
| Nexscope Ecommerce Data and Creative APIs | Shopping | publicapi.dev | no | https://publicapi.dev/nexscope-ecommerce-data-and-creative-ap-is-api |

### Header (User-Agent) (2)

| Nama | Kategori | Situs | CORS | Tautan |
|---|---|---|---|---|
| Meteorologisk Institutt | Weather | publicapis.dev | unknown | https://publicapis.dev/resource/meteorologisk-institutt/vs4uhery |
| Meteorologisk Institutt | Weather | publicapi.dev | no | https://publicapi.dev/meteorologisk-institutt-api |

---

File rujukan lengkap (termasuk entri tanpa autentikasi): `docs/DATA_PUBLIC_API.json`.
