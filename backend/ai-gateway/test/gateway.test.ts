import { env, SELF } from "cloudflare:test";
import { resolveProvider } from "../src/providers";
import { cloudflareProvider } from "../src/providers/cloudflare";
import type { Env } from "../src/config";
import { describe, expect, it } from "vitest";

const BASE = "https://gateway.test";

async function issueToken(installId: string): Promise<string> {
  const response = await SELF.fetch(`${BASE}/v1/install`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ installId }),
  });
  expect(response.status).toBe(200);
  const body = (await response.json()) as { token: string; mode: string };
  expect(body.mode).toBe("mock");
  return body.token;
}

function auth(token: string): Record<string, string> {
  return { authorization: `Bearer ${token}` };
}

function installId(tag: string): string {
  // Unik per test supaya rate limit & kuota KV tidak saling mengganggu.
  return `${tag}_${crypto.randomUUID().replace(/-/g, "").slice(0, 20)}`;
}

describe("health & install", () => {
  it("reports mock mode", async () => {
    const response = await SELF.fetch(`${BASE}/v1/health`);
    expect(response.status).toBe(200);
    const body = (await response.json()) as { status: string; mode: string };
    expect(body.status).toBe("ok");
    expect(body.mode).toBe("mock");
  });

  it("rejects an install id that is too short", async () => {
    const response = await SELF.fetch(`${BASE}/v1/install`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ installId: "short" }),
    });
    expect(response.status).toBe(400);
    const body = (await response.json()) as { code: string; retryable: boolean };
    expect(body.code).toBe("bad_request");
    expect(body.retryable).toBe(false);
  });

  it("returns a token that authenticates quota lookups", async () => {
    const token = await issueToken(installId("ok"));
    const response = await SELF.fetch(`${BASE}/v1/quota`, { headers: auth(token) });
    expect(response.status).toBe(200);
    const body = (await response.json()) as { used: number; limit: number };
    expect(body.used).toBe(0);
    expect(body.limit).toBe(5);
  });
});

describe("authentication", () => {
  it("rejects requests without a token", async () => {
    const response = await SELF.fetch(`${BASE}/v1/quota`);
    expect(response.status).toBe(401);
    const body = (await response.json()) as { code: string };
    expect(body.code).toBe("unauthorized");
  });

  it("rejects a tampered signature", async () => {
    const token = await issueToken(installId("tamper"));
    const parts = token.split(".");
    const tampered = `${parts[0]}.${parts[1]}.${parts[2].slice(0, -2)}AA`;
    const response = await SELF.fetch(`${BASE}/v1/quota`, { headers: auth(tampered) });
    expect(response.status).toBe(401);
  });

  it("rejects a token whose install id was swapped", async () => {
    const token = await issueToken(installId("swap"));
    const parts = token.split(".");
    const swapped = `${installId("other")}.${parts[1]}.${parts[2]}`;
    const response = await SELF.fetch(`${BASE}/v1/quota`, { headers: auth(swapped) });
    expect(response.status).toBe(401);
  });
});

describe("rate limiting", () => {
  it("blocks the fourth request in the same minute", async () => {
    const token = await issueToken(installId("rl"));
    const statuses: number[] = [];
    for (let i = 0; i < 4; i += 1) {
      const response = await SELF.fetch(`${BASE}/v1/quota`, { headers: auth(token) });
      statuses.push(response.status);
      if (response.status === 429) {
        const body = (await response.json()) as { code: string; retryable: boolean };
        expect(body.code).toBe("rate_limited");
        expect(body.retryable).toBe(true);
      }
    }
    expect(statuses.slice(0, 3)).toEqual([200, 200, 200]);
    expect(statuses[3]).toBe(429);
  });
});

describe("text to speech", () => {
  it("returns a real WAV container from the mock provider", async () => {
    const token = await issueToken(installId("tts"));
    const response = await SELF.fetch(`${BASE}/v1/text-to-speech`, {
      method: "POST",
      headers: { ...auth(token), "content-type": "application/json" },
      body: JSON.stringify({ text: "Halo HariMu", voice: 3 }),
    });
    expect(response.status).toBe(200);
    expect(response.headers.get("content-type")).toBe("audio/wav");
    expect(response.headers.get("x-mock")).toBe("1");
    const bytes = new Uint8Array(await response.arrayBuffer());
    expect(bytes.byteLength).toBeGreaterThan(44);
    expect(String.fromCharCode(...bytes.slice(0, 4))).toBe("RIFF");
    expect(String.fromCharCode(...bytes.slice(8, 12))).toBe("WAVE");
    expect(response.headers.get("x-quota-remaining")).toBe("4");
  });

  it("rejects an empty text", async () => {
    const token = await issueToken(installId("tts-empty"));
    const response = await SELF.fetch(`${BASE}/v1/text-to-speech`, {
      method: "POST",
      headers: { ...auth(token), "content-type": "application/json" },
      body: JSON.stringify({ text: "   " }),
    });
    expect(response.status).toBe(400);
  });
});

describe("image endpoints", () => {
  it("returns a PNG and marks the response as mock", async () => {
    const token = await issueToken(installId("img"));
    const response = await SELF.fetch(`${BASE}/v1/image-generate`, {
      method: "POST",
      headers: { ...auth(token), "content-type": "application/json" },
      body: JSON.stringify({ prompt: "kucing oren", size: "512x512" }),
    });
    expect(response.status).toBe(200);
    expect(response.headers.get("content-type")).toBe("image/png");
    expect(response.headers.get("x-mock")).toBe("1");
    const bytes = new Uint8Array(await response.arrayBuffer());
    expect([...bytes.slice(0, 8)]).toEqual([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  });

  it("rejects an invalid size string", async () => {
    const token = await issueToken(installId("img-size"));
    const response = await SELF.fetch(`${BASE}/v1/image-generate`, {
      method: "POST",
      headers: { ...auth(token), "content-type": "application/json" },
      body: JSON.stringify({ prompt: "x", size: "besar" }),
    });
    expect(response.status).toBe(400);
  });

  it("analyses an image and reports mock output", async () => {
    const token = await issueToken(installId("vision"));
    const form = new FormData();
    form.set("image", new File([new Uint8Array([1, 2, 3])], "a.png", { type: "image/png" }));
    form.set("question", "Apa isi gambar ini?");
    const response = await SELF.fetch(`${BASE}/v1/image-analyze`, {
      method: "POST",
      headers: auth(token),
      body: form,
    });
    expect(response.status).toBe(200);
    const body = (await response.json()) as { text: string; mock: boolean };
    expect(body.mock).toBe(true);
    expect(body.text).toContain("Apa isi gambar ini?");
  });

  it("refuses an upload bigger than the configured limit", async () => {
    const token = await issueToken(installId("big"));
    const form = new FormData();
    form.set("image", new File([new Uint8Array(4096)], "big.png", { type: "image/png" }));
    form.set("question", "?");
    const response = await SELF.fetch(`${BASE}/v1/image-analyze`, {
      method: "POST",
      headers: auth(token),
      body: form,
    });
    expect(response.status).toBe(413);
    const body = (await response.json()) as { code: string };
    expect(body.code).toBe("payload_too_large");
  });

  it("edits an image through the mock provider", async () => {
    const token = await issueToken(installId("edit"));
    const form = new FormData();
    form.set("image", new File([new Uint8Array([9, 9, 9])], "a.png", { type: "image/png" }));
    form.set("prompt", "lebih tajam");
    const response = await SELF.fetch(`${BASE}/v1/image-edit/restore`, {
      method: "POST",
      headers: auth(token),
      body: form,
    });
    expect(response.status).toBe(200);
    expect(response.headers.get("content-type")).toBe("image/png");
  });
});

describe("speech to text", () => {
  it("transcribes through the mock provider", async () => {
    const token = await issueToken(installId("stt"));
    const form = new FormData();
    form.set("audio", new File([new Uint8Array(128)], "a.wav", { type: "audio/wav" }));
    const response = await SELF.fetch(`${BASE}/v1/speech-to-text`, {
      method: "POST",
      headers: auth(token),
      body: form,
    });
    expect(response.status).toBe(200);
    const body = (await response.json()) as { text: string; mock: boolean };
    expect(body.mock).toBe(true);
    expect(body.text.length).toBeGreaterThan(0);
  });
});

describe("video jobs", () => {
  it("creates a job, polls it to done and downloads the artifact", async () => {
    const token = await issueToken(installId("video"));
    const created = await SELF.fetch(`${BASE}/v1/video-generate`, {
      method: "POST",
      headers: { ...auth(token), "content-type": "application/json" },
      body: JSON.stringify({ prompt: "ombak pantai saat matahari terbenam", durationSec: 3 }),
    });
    expect(created.status).toBe(202);
    const createdBody = (await created.json()) as { jobId: string; status: string; mock: boolean };
    expect(createdBody.mock).toBe(true);
    expect(createdBody.status).toBe("queued");

    const polled = await SELF.fetch(`${BASE}/v1/jobs/${createdBody.jobId}`, { headers: auth(token) });
    expect(polled.status).toBe(200);
    const job = (await polled.json()) as {
      status: string;
      downloadUrl?: string;
      mime?: string;
    };
    expect(job.status).toBe("done");
    expect(job.mime).toBe("video/mp4");
    expect(job.downloadUrl).toBeTruthy();

    const download = await SELF.fetch(`${BASE}${job.downloadUrl}`);
    expect(download.status).toBe(200);
    expect(download.headers.get("content-type")).toBe("video/mp4");
    const bytes = new Uint8Array(await download.arrayBuffer());
    expect(bytes.byteLength).toBeGreaterThan(0);
  });

  it("refuses a download with a forged signature", async () => {
    const token = await issueToken(installId("forge"));
    const created = await SELF.fetch(`${BASE}/v1/video-generate`, {
      method: "POST",
      headers: { ...auth(token), "content-type": "application/json" },
      body: JSON.stringify({ prompt: "test" }),
    });
    const createdBody = (await created.json()) as { jobId: string };
    const polled = await SELF.fetch(`${BASE}/v1/jobs/${createdBody.jobId}`, { headers: auth(token) });
    const job = (await polled.json()) as { downloadUrl: string };
    const tampered = job.downloadUrl.replace(/sig=[^&]+/, "sig=AAAA");
    const response = await SELF.fetch(`${BASE}${tampered}`);
    expect(response.status).toBe(403);
  });

  it("hides jobs belonging to another installation", async () => {
    const owner = await issueToken(installId("owner"));
    const intruder = await issueToken(installId("intruder"));
    const created = await SELF.fetch(`${BASE}/v1/video-generate`, {
      method: "POST",
      headers: { ...auth(owner), "content-type": "application/json" },
      body: JSON.stringify({ prompt: "rahasia" }),
    });
    const createdBody = (await created.json()) as { jobId: string };
    const response = await SELF.fetch(`${BASE}/v1/jobs/${createdBody.jobId}`, {
      headers: auth(intruder),
    });
    expect(response.status).toBe(404);
  });

  it("enforces the daily quota for expensive operations", async () => {
    const token = await issueToken(installId("quota"));
    const first = await SELF.fetch(`${BASE}/v1/video-generate`, {
      method: "POST",
      headers: { ...auth(token), "content-type": "application/json" },
      body: JSON.stringify({ prompt: "pertama" }),
    });
    expect(first.status).toBe(202);
    const second = await SELF.fetch(`${BASE}/v1/video-generate`, {
      method: "POST",
      headers: { ...auth(token), "content-type": "application/json" },
      body: JSON.stringify({ prompt: "kedua" }),
    });
    expect(second.status).toBe(429);
    const body = (await second.json()) as { code: string; retryable: boolean; details?: unknown };
    expect(body.code).toBe("quota_exceeded");
    expect(body.retryable).toBe(false);
    expect(body.details).toBeTruthy();
  });
});

describe("routing", () => {
  it("returns a structured 404 for unknown endpoints", async () => {
    const token = await issueToken(installId("route"));
    const response = await SELF.fetch(`${BASE}/v1/tidak-ada`, { headers: auth(token) });
    expect(response.status).toBe(404);
    const body = (await response.json()) as { code: string; message: string; requestId: string };
    expect(body.code).toBe("not_found");
    expect(body.requestId.length).toBeGreaterThan(0);
  });

  it("answers CORS preflight", async () => {
    const response = await SELF.fetch(`${BASE}/v1/health`, { method: "OPTIONS" });
    expect(response.status).toBe(204);
    expect(response.headers.get("access-control-allow-methods")).toContain("POST");
  });
});

describe("install hardening", () => {
  it("rate-limits token issuance per IP", async () => {
    const ip = `203.0.113.${Math.floor(Math.random() * 200) + 1}`;
    const statuses: number[] = [];
    for (let i = 0; i < 6; i += 1) {
      const response = await SELF.fetch(`${BASE}/v1/install`, {
        method: "POST",
        headers: { "content-type": "application/json", "cf-connecting-ip": ip },
        body: JSON.stringify({ installId: installId(`ip${i}`) }),
      });
      statuses.push(response.status);
    }
    expect(statuses.slice(0, 4).every((code) => code === 200)).toBe(true);
    expect(statuses[4]).toBe(429);
    expect(statuses[5]).toBe(429);
  });

  it("returns 401 (not 500) for a malformed bearer signature", async () => {
    const base = installId("badsig");
    const response = await SELF.fetch(`${BASE}/v1/quota`, {
      headers: { authorization: `Bearer ${base}.eyJzdWIiOiJ4In0.%%%not-base64%%%` },
    });
    expect(response.status).toBe(401);
    const body = (await response.json()) as { code: string };
    expect(body.code).toBe("unauthorized");
  });

  it("returns 403 (not 500) for a malformed download signature", async () => {
    const response = await SELF.fetch(`${BASE}/v1/download/abc.png?exp=9999999999&sig=%%%`);
    expect(response.status).toBe(403);
    const body = (await response.json()) as { code: string };
    expect(body.code).toBe("forbidden");
  });
});

/**
 * K6: catatan "ditulis, belum dijalankan".
 * Tes di bawah memakai provider mock untuk jalur HTTP, dan binding `AI` palsu untuk adapter
 * Workers AI sehingga tidak ada panggilan jaringan/berbiaya.
 */
const TINY_PNG_B64 =
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

interface FakeCall {
  model: string;
  inputs: Record<string, unknown>;
  options?: unknown;
}

function fakeEnv(calls: FakeCall[], overrides: Record<string, unknown> = {}): Env {
  const wav = new Uint8Array(64);
  wav.set([0x52, 0x49, 0x46, 0x46], 0); // RIFF
  wav.set([0x57, 0x41, 0x56, 0x45], 8); // WAVE
  const ai = {
    run: async (model: string, inputs: unknown, options?: unknown) => {
      calls.push({ model, inputs: inputs as Record<string, unknown>, options });
      if ((options as { returnRawResponse?: boolean } | undefined)?.returnRawResponse) {
        return new Response(wav);
      }
      if (model.includes("vision")) return { response: "seekor kucing oren" };
      if (model.includes("whisper")) return { text: "halo dunia" };
      return { image: TINY_PNG_B64 };
    },
  };
  return {
    AI: ai as unknown as Env["AI"],
    ...overrides,
  } as unknown as Env;
}

describe("workers-ai adapter (K6)", () => {
  it("sends speech to aura-1 as linear16 WAV and returns RIFF bytes", async () => {
    const calls: FakeCall[] = [];
    const provider = cloudflareProvider(fakeEnv(calls));
    const result = await provider.tts!({ text: "Halo HariMu", voice: 2 });

    expect(calls).toHaveLength(1);
    expect(calls[0].model).toBe("@cf/deepgram/aura-1");
    expect(calls[0].inputs.speaker).toBe("asteria");
    expect(calls[0].inputs.encoding).toBe("linear16");
    expect(calls[0].inputs.container).toBe("wav");
    expect((calls[0].options as { returnRawResponse?: boolean }).returnRawResponse).toBe(true);
    expect(result.mime).toBe("audio/wav");
    expect(String.fromCharCode(...result.bytes.slice(0, 4))).toBe("RIFF");
  });

  it("generates images with flux-1-schnell and decodes base64 to PNG bytes", async () => {
    const calls: FakeCall[] = [];
    const provider = cloudflareProvider(fakeEnv(calls));
    const result = await provider.imageGenerate!({ prompt: "kucing oren", size: "1024x1024" });

    expect(calls[0].model).toBe("@cf/black-forest-labs/flux-1-schnell");
    expect(calls[0].inputs.prompt).toBe("kucing oren");
    expect([...result.bytes.slice(0, 8)]).toEqual([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  });

  it("edits images through img2img with the uploaded bytes as base64", async () => {
    const calls: FakeCall[] = [];
    const provider = cloudflareProvider(fakeEnv(calls));
    await provider.imageEdit!({
      op: "restore",
      bytes: new Uint8Array([1, 2, 3, 4]),
      mime: "image/png",
      params: { prompt: "lebih tajam" },
    });

    expect(calls[0].model).toBe("@cf/runwayml/stable-diffusion-v1-5-img2img");
    expect(String(calls[0].inputs.prompt)).toContain("restore");
    expect(typeof calls[0].inputs.image_b64).toBe("string");
    expect(atob(String(calls[0].inputs.image_b64))).toBe(String.fromCharCode(1, 2, 3, 4));
  });

  it("analyses images with the llama vision model and returns its text", async () => {
    const calls: FakeCall[] = [];
    const provider = cloudflareProvider(fakeEnv(calls));
    const result = await provider.imageAnalyze!({
      bytes: new Uint8Array([9, 9]),
      mime: "image/png",
      question: "Apa isi gambar?",
    });

    expect(calls[0].model).toBe("@cf/meta/llama-3.2-11b-vision-instruct");
    expect(calls[0].inputs.prompt).toBe("Apa isi gambar?");
    expect(result).toEqual({ text: "seekor kucing oren", mock: false });
  });

  it("transcribes with whisper and maps the text field", async () => {
    const calls: FakeCall[] = [];
    const provider = cloudflareProvider(fakeEnv(calls));
    const result = await provider.speechToText!({
      bytes: new Uint8Array([1, 2]),
      mime: "audio/wav",
      language: "id",
    });

    expect(calls[0].model).toBe("@cf/openai/whisper");
    expect(calls[0].inputs.language).toBe("id");
    expect(Array.isArray(calls[0].inputs.audio)).toBe(true);
    expect(result.text).toBe("halo dunia");
  });

  it("refuses video in live mode when no video provider is configured", () => {
    const liveEnv = { PROVIDER_MODE: "live" } as unknown as Env;
    expect(() => resolveProvider(liveEnv, "video")).toThrowError(/Video generation belum punya provider live/);
  });
});

describe("gateway limits & persistence (K6)", () => {
  it("reports the CORS origin in health", async () => {
    const response = await SELF.fetch(`${BASE}/v1/health`);
    const body = (await response.json()) as { corsOrigin: string };
    expect(body.corsOrigin).toBe("*");
  });

  it("rejects TTS text over the 4000 character limit", async () => {
    const token = await issueToken(installId("tts-long"));
    const response = await SELF.fetch(`${BASE}/v1/text-to-speech`, {
      method: "POST",
      headers: { ...auth(token), "content-type": "application/json" },
      body: JSON.stringify({ text: "a".repeat(4001) }),
    });
    expect(response.status).toBe(400);
    const body = (await response.json()) as { code: string };
    expect(body.code).toBe("bad_request");
  });

  it("accepts exactly 4000 characters of TTS text", async () => {
    const token = await issueToken(installId("tts-edge"));
    const response = await SELF.fetch(`${BASE}/v1/text-to-speech`, {
      method: "POST",
      headers: { ...auth(token), "content-type": "application/json" },
      body: JSON.stringify({ text: "a".repeat(4000) }),
    });
    expect(response.status).toBe(200);
    expect(response.headers.get("content-type")).toBe("audio/wav");
  });

  it("keeps job status in KV and the artifact in R2", async () => {
    const token = await issueToken(installId("jobstore"));
    const created = await SELF.fetch(`${BASE}/v1/video-generate`, {
      method: "POST",
      headers: { ...auth(token), "content-type": "application/json" },
      body: JSON.stringify({ prompt: "penyimpanan job", durationSec: 2 }),
    });
    const { jobId } = (await created.json()) as { jobId: string };
    await SELF.fetch(`${BASE}/v1/jobs/${jobId}`, { headers: auth(token) });

    const bindings = env as unknown as { JOBS: KVNamespace; ARTIFACTS: R2Bucket };
    const raw = await bindings.JOBS.get(`job:${jobId}`);
    expect(raw).toBeTruthy();
    const record = JSON.parse(String(raw)) as { status: string; installId: string; objectKey?: string };
    expect(record.status).toBe("done");
    expect(record.installId.length).toBeGreaterThan(0);
    expect(record.objectKey).toBe(`jobs/${jobId}/result`);

    const artifact = await bindings.ARTIFACTS.get(`jobs/${jobId}/result`);
    expect(artifact).not.toBeNull();
    const bytes = new Uint8Array(await artifact!.arrayBuffer());
    expect(bytes.byteLength).toBeGreaterThan(0);
  });
});

describe("public data utilities", () => {
  it("geocodes a place name in mock mode", async () => {
    const token = await issueToken(installId("geo"));
    const response = await SELF.fetch(`${BASE}/v1/geocode?q=Jakarta`, { headers: auth(token) });
    expect(response.status).toBe(200);
    const body = (await response.json()) as {
      label: string;
      latitude: number;
      longitude: number;
      mock: boolean;
      attribution: string;
    };
    expect(body.mock).toBe(true);
    expect(body.label.length).toBeGreaterThan(0);
    expect(body.latitude).toBeLessThan(0);
    expect(body.attribution).toContain("Open-Meteo");
  });

  it("rejects an empty geocode query", async () => {
    const token = await issueToken(installId("geo-empty"));
    const response = await SELF.fetch(`${BASE}/v1/geocode?q=`, { headers: auth(token) });
    expect(response.status).toBe(400);
    const body = (await response.json()) as { code: string };
    expect(body.code).toBe("bad_request");
  });

  it("serves weather for a city name without coordinates", async () => {
    const token = await issueToken(installId("weather-q"));
    const response = await SELF.fetch(`${BASE}/v1/weather?q=Bandung`, { headers: auth(token) });
    expect(response.status).toBe(200);
    const body = (await response.json()) as {
      temperature: number;
      location: string;
      attribution: string;
    };
    expect(typeof body.temperature).toBe("number");
    expect(body.location.length).toBeGreaterThan(0);
    expect(body.attribution).toContain("Open-Meteo");
  });

  it("rejects weather without a place or coordinates", async () => {
    const token = await issueToken(installId("weather-empty"));
    const response = await SELF.fetch(`${BASE}/v1/weather`, { headers: auth(token) });
    expect(response.status).toBe(400);
  });

  it("returns reference rates for a base currency", async () => {
    const token = await issueToken(installId("rates"));
    const response = await SELF.fetch(`${BASE}/v1/rates?base=IDR&symbols=USD,EUR`, { headers: auth(token) });
    expect(response.status).toBe(200);
    const body = (await response.json()) as {
      base: string;
      date: string;
      rates: Record<string, number>;
      mock: boolean;
    };
    expect(body.base).toBe("IDR");
    expect(body.date).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(Object.keys(body.rates).sort()).toEqual(["EUR", "USD"]);
    expect(body.mock).toBe(true);
  });

  it("rejects an invalid currency code", async () => {
    const token = await issueToken(installId("rates-bad"));
    const response = await SELF.fetch(`${BASE}/v1/rates?base=IDR&symbols=US`, { headers: auth(token) });
    expect(response.status).toBe(400);
    const body = (await response.json()) as { code: string };
    expect(body.code).toBe("bad_request");
  });

  it("returns dictionary meanings in mock mode", async () => {
    const token = await issueToken(installId("dict"));
    const response = await SELF.fetch(`${BASE}/v1/dictionary?word=hello`, { headers: auth(token) });
    expect(response.status).toBe(200);
    const body = (await response.json()) as {
      word: string;
      meanings: Array<{ partOfSpeech: string; definitions: string[] }>;
      mock: boolean;
      source: string;
    };
    expect(body.word).toBe("hello");
    expect(body.meanings.length).toBeGreaterThan(0);
    expect(body.meanings[0].definitions.length).toBeGreaterThan(0);
    expect(body.mock).toBe(true);
    expect(body.source).toContain("MOCK");
  });

  it("rejects an empty dictionary word", async () => {
    const token = await issueToken(installId("dict-empty"));
    const response = await SELF.fetch(`${BASE}/v1/dictionary?word=`, { headers: auth(token) });
    expect(response.status).toBe(400);
  });
});
