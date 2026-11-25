import type { Provider, ProviderResult } from "./types";

/**
 * Provider MOCK — dipakai untuk tes dan pengembangan tanpa kunci API.
 *
 * Semua output ditandai `mock: true` di respons JSON sehingga klien dan katalog tidak bisa
 * mengklaim hasilnya sebagai keluaran model sungguhan. Gambar hanya PNG 1×1, audio hanya nada
 * 440 Hz, video hanya byte placeholder yang tidak bisa diputar.
 */
const TINY_PNG_BASE64 =
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

function decodeBase64(base64: string): Uint8Array {
  const binary = atob(base64);
  const out = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) out[i] = binary.charCodeAt(i);
  return out;
}

/** WAV PCM 16-bit mono dengan nada sinus supaya header & isinya benar-benar valid. */
export function mockWav(seconds = 0.6, frequency = 440, sampleRate = 16000): Uint8Array {
  const samples = Math.floor(seconds * sampleRate);
  const dataSize = samples * 2;
  const buffer = new ArrayBuffer(44 + dataSize);
  const view = new DataView(buffer);
  const write = (offset: number, text: string) => {
    for (let i = 0; i < text.length; i += 1) view.setUint8(offset + i, text.charCodeAt(i));
  };
  write(0, "RIFF");
  view.setUint32(4, 36 + dataSize, true);
  write(8, "WAVE");
  write(12, "fmt ");
  view.setUint32(16, 16, true);
  view.setUint16(20, 1, true);
  view.setUint16(22, 1, true);
  view.setUint32(24, sampleRate, true);
  view.setUint32(28, sampleRate * 2, true);
  view.setUint16(32, 2, true);
  view.setUint16(34, 16, true);
  write(36, "data");
  view.setUint32(40, dataSize, true);
  for (let i = 0; i < samples; i += 1) {
    const fade = Math.min(1, Math.min(i, samples - i) / (sampleRate * 0.05));
    const value = Math.sin((2 * Math.PI * frequency * i) / sampleRate) * 0.25 * fade;
    view.setInt16(44 + i * 2, Math.round(value * 32767), true);
  }
  return new Uint8Array(buffer);
}

const mockVideoBytes = (): Uint8Array => {
  const text = "HARIMU_MOCK_VIDEO_PLACEHOLDER\n";
  const repeated = text.repeat(64);
  return new TextEncoder().encode(repeated);
};

export const mockProvider: Provider = {
  id: "mock",
  mock: true,

  async tts(request) {
    const voiceShift = [0, 70, 140, 210, 280][Math.min(4, Math.max(0, request.voice - 1))] ?? 0;
    return { bytes: mockWav(0.4 + Math.min(1.6, request.text.length / 200), 420 + voiceShift), mime: "audio/wav" };
  },

  async imageGenerate(): Promise<ProviderResult> {
    return { bytes: decodeBase64(TINY_PNG_BASE64), mime: "image/png" };
  },

  async imageEdit(): Promise<ProviderResult> {
    return { bytes: decodeBase64(TINY_PNG_BASE64), mime: "image/png" };
  },

  async imageAnalyze(request) {
    return {
      text:
        `[MOCK] Gambar ${request.bytes.byteLength} byte (${request.mime}) diterima. ` +
        `Pertanyaan: "${request.question}". Tidak ada model vision yang dipanggil.`,
      mock: true,
    };
  },

  async speechToText(request) {
    return {
      text: `[MOCK] Audio ${request.bytes.byteLength} byte (${request.mime}) — transkrip contoh, bukan hasil model.`,
      mock: true,
    };
  },

  async chat(request) {
    const last = request.messages[request.messages.length - 1]?.content ?? "";
    return {
      text: `[MOCK] Balasan contoh untuk: "${last.slice(0, 120)}". Tidak ada model yang dipanggil.`,
      mock: true,
    };
  },

  async translate(request) {
    return {
      text: `[MOCK] ${request.targetLang}: ${request.text.slice(0, 200)}`,
      sourceLang: request.sourceLang ?? "auto",
      mock: true,
    };
  },

  async weather(request) {
    return {
      temperature: 27.5,
      condition: "Mock — cerah",
      location: `${request.latitude},${request.longitude}`,
    };
  },

  async startVideo(request) {
    const id = `mock-${crypto.randomUUID()}`;
    return { providerJobId: `${id}?duration=${request.durationSec}` };
  },

  async pollVideo() {
    return {
      status: "done",
      progress: 100,
      result: { bytes: mockVideoBytes(), mime: "video/mp4" },
    };
  },
};
