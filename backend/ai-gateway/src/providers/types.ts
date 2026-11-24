/** Kontrak adapter provider. Semua provider nyata mengimplementasikan subset ini. */
export interface TtsRequest {
  text: string;
  voice: number;
  language?: string;
}

export interface ImageRequest {
  prompt: string;
  size: string;
}

export interface ImageEditRequest {
  op: string;
  bytes: Uint8Array;
  mime: string;
  params: Record<string, string>;
}

export interface AnalyzeRequest {
  bytes: Uint8Array;
  mime: string;
  question: string;
}

export interface AsrRequest {
  bytes: Uint8Array;
  mime: string;
  language?: string;
}

export interface VideoRequest {
  prompt: string;
  durationSec: number;
  aspect: string;
}

export interface ProviderResult {
  bytes: Uint8Array;
  mime: string;
}

export interface VideoStart {
  providerJobId: string;
}

export type VideoJobState = "running" | "done" | "failed";

export interface VideoPoll {
  status: VideoJobState;
  progress?: number;
  result?: ProviderResult;
  error?: string;
}

export type Capability =
  | "tts"
  | "imageGenerate"
  | "imageEdit"
  | "imageAnalyze"
  | "speechToText"
  | "video";

export interface Provider {
  readonly id: string;
  readonly mock: boolean;
  tts?(request: TtsRequest): Promise<ProviderResult>;
  imageGenerate?(request: ImageRequest): Promise<ProviderResult>;
  imageEdit?(request: ImageEditRequest): Promise<ProviderResult>;
  imageAnalyze?(request: AnalyzeRequest): Promise<{ text: string; mock?: boolean }>;
  speechToText?(request: AsrRequest): Promise<{ text: string; mock?: boolean }>;
  startVideo?(request: VideoRequest): Promise<VideoStart>;
  pollVideo?(providerJobId: string): Promise<VideoPoll>;
}

/** Adapter yang dipakai bersama beberapa provider untuk menandai capability yang tak didukung. */
export function unsupported(providerId: string, capability: Capability): never {
  throw new Error(`Provider '${providerId}' tidak mendukung '${capability}'`);
}
