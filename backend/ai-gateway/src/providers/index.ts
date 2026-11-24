import type { Env } from "../config";
import { isMockMode } from "../config";
import { ApiError } from "../errors";
import { mockProvider } from "./mock";
import { openaiProvider } from "./openai";
import { replicateVideoProvider } from "./replicate";
import type { Capability, Provider } from "./types";

export function resolveProvider(env: Env, capability: Capability): Provider {
  if (isMockMode(env)) return mockProvider;

  if (capability === "video") return replicateVideoProvider(env);

  const provider = openaiProvider(env);
  const supported: Record<Exclude<Capability, "video">, boolean> = {
    tts: typeof provider.tts === "function",
    imageGenerate: typeof provider.imageGenerate === "function",
    imageEdit: typeof provider.imageEdit === "function",
    imageAnalyze: typeof provider.imageAnalyze === "function",
    speechToText: typeof provider.speechToText === "function",
  };
  if (!supported[capability]) {
    throw new ApiError(501, "provider_not_configured", `Provider aktif tidak mendukung '${capability}'`);
  }
  return provider;
}

export * from "./types";
export { mockProvider } from "./mock";
