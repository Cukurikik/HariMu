/**
 * Adapter kamus (Free Dictionary API / dictionaryapi.dev).
 *
 * Endpoint: GET {baseUrl}/api/v2/entries/{lang}/{word}
 * Tanpa API key. Data berasal dari Wiktionary sehingga atribusi CC BY-SA wajib ditampilkan.
 * Layanan ini komunitas tanpa SLA (uptime 30 hari terakhir ±98%), jadi hasil di-cache 7 hari
 * dan kegagalannya harus ditampilkan sebagai kegagalan, bukan definisi kosong.
 */

import type { Env } from "../config";
import { isMockMode } from "../config";
import { ApiError } from "../errors";

export interface DictionaryMeaning {
  partOfSpeech: string;
  definitions: string[];
}

export interface DictionaryResult {
  word: string;
  phonetic?: string;
  meanings: DictionaryMeaning[];
  source: string;
  mock?: boolean;
}

const CACHE_TTL_SEC = 7 * 24 * 3600;
const USER_AGENT = "HariMu-Android/1.0 (+https://github.com/Cukurikik/HariMu)";
const SUPPORTED_LANGUAGES = ["en", "hi", "es", "fr", "ja", "ru", "de", "it", "ko", "pt", "ar", "tr"];

function baseUrl(env: Env): string {
  return (env.DICTIONARY_BASE_URL ?? "https://api.dictionaryapi.dev").replace(/\/$/, "");
}

export async function lookupWord(
  env: Env,
  word: string,
  language = "en",
): Promise<DictionaryResult> {
  const q = word.trim();
  if (!q) throw new ApiError(400, "bad_request", "Kata tidak boleh kosong");
  if (q.length > 60) throw new ApiError(400, "bad_request", "Kata terlalu panjang (maks 60)");
  const lang = SUPPORTED_LANGUAGES.includes(language) ? language : "en";

  if (isMockMode(env)) {
    return {
      word: q,
      phonetic: "/mɒk/",
      meanings: [
        {
          partOfSpeech: "noun",
          definitions: ["[MOCK] Definisi contoh — bukan data Wiktionary sungguhan."],
        },
      ],
      source: "MOCK — bukan data kamus nyata",
      mock: true,
    };
  }

  const cacheKey = `dict:${lang}:${q.toLowerCase().slice(0, 60)}`;
  const cached = await env.JOBS.get(cacheKey);
  if (cached) {
    try {
      return JSON.parse(cached) as DictionaryResult;
    } catch {
      // entri rusak: ambil ulang dari provider
    }
  }

  const url = `${baseUrl(env)}/api/v2/entries/${lang}/${encodeURIComponent(q)}`;
  let response: Response;
  try {
    response = await fetch(url, { headers: { "user-agent": USER_AGENT } });
  } catch (cause) {
    throw new ApiError(502, "provider_error", "Layanan kamus tidak dapat dihubungi", {
      retryable: true,
      details: { cause: cause instanceof Error ? cause.message : String(cause) },
    });
  }

  if (response.status === 404) {
    throw new ApiError(404, "not_found", `Kata '${q}' tidak ditemukan di kamus ${lang}`);
  }
  if (!response.ok) {
    throw new ApiError(502, "provider_error", `Layanan kamus menolak permintaan (${response.status})`, {
      retryable: response.status >= 500 || response.status === 429,
      details: { status: response.status },
    });
  }

  let data: any;
  try {
    data = await response.json();
  } catch (cause) {
    throw new ApiError(502, "provider_error", "Layanan kamus mengembalikan respons tidak valid", {
      retryable: true,
      details: { cause: cause instanceof Error ? cause.message : String(cause) },
    });
  }

  const entry = Array.isArray(data) ? data[0] : undefined;
  if (!entry) {
    throw new ApiError(404, "not_found", `Kata '${q}' tidak ditemukan`);
  }

  const meanings: DictionaryMeaning[] = (Array.isArray(entry.meanings) ? entry.meanings : [])
    .map((meaning: any) => ({
      partOfSpeech: typeof meaning?.partOfSpeech === "string" ? meaning.partOfSpeech : "",
      definitions: (Array.isArray(meaning?.definitions) ? meaning.definitions : [])
        .map((definition: any) =>
          typeof definition?.definition === "string" ? definition.definition.trim() : "",
        )
        .filter((definition: string) => definition.length > 0)
        .slice(0, 3),
    }))
    .filter((meaning: DictionaryMeaning) => meaning.definitions.length > 0)
    .slice(0, 4);

  if (meanings.length === 0) {
    throw new ApiError(404, "not_found", `Kata '${q}' tidak punya definisi yang bisa ditampilkan`);
  }

  const phonetic =
    typeof entry.phonetic === "string"
      ? entry.phonetic
      : (Array.isArray(entry.phonetics) ? entry.phonetics : []).find(
          (item: any) => typeof item?.text === "string" && item.text.trim().length > 0,
        )?.text;

  const result: DictionaryResult = {
    word: typeof entry.word === "string" ? entry.word : q,
    phonetic: typeof phonetic === "string" ? phonetic : undefined,
    meanings,
    source: "Wiktionary via dictionaryapi.dev (CC BY-SA)",
  };
  await env.JOBS.put(cacheKey, JSON.stringify(result), { expirationTtl: CACHE_TTL_SEC });
  return result;
}
