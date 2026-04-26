/**
 * Clova Studio API 클라이언트
 *
 * 백엔드 ClovaApiClient.kt와 동일한 방식으로 호출
 */

const CLOVA_API_URL = process.env.CLOVA_API_URL || "https://clovastudio.stream.ntruss.com";
const CLOVA_API_KEY = process.env.CLOVA_API_KEY;
const CLOVA_API_GATEWAY_KEY = process.env.CLOVA_API_GATEWAY_KEY;

function generateRequestId() {
  return `test-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

/**
 * Clova HCX-007 chat completion (thinking 모드)
 */
export async function clovaChatCompletion(messages, options = {}) {
  if (!CLOVA_API_KEY) throw new Error("CLOVA_API_KEY 환경변수가 설정되지 않았습니다.");

  const {
    model = "HCX-007",
    maxTokens = 2048,
    temperature = 0.5,
    topK = 0,
    topP = 0.8,
    repeatPenalty = 1.1,
    seed = 0,
  } = options;

  const useThinking = model === "HCX-007";
  const apiVersion = model === "HCX-007" ? "v3" : "v1";

  const body = useThinking
    ? {
        messages,
        topP,
        topK,
        maxCompletionTokens: maxTokens,
        temperature,
        repetitionPenalty: repeatPenalty,
        seed,
        thinking: { effort: "low" },
      }
    : {
        messages,
        topP,
        topK,
        maxTokens,
        temperature,
        repeatPenalty,
        seed,
      };

  const headers = {
    "Content-Type": "application/json",
    Authorization: `Bearer ${CLOVA_API_KEY}`,
    "X-NCP-CLOVASTUDIO-REQUEST-ID": generateRequestId(),
  };
  if (CLOVA_API_GATEWAY_KEY) {
    headers["X-NCP-APIGW-API-KEY"] = CLOVA_API_GATEWAY_KEY;
  }

  const url = `${CLOVA_API_URL}/${apiVersion}/chat-completions/${model}`;

  const startTime = Date.now();
  const res = await fetch(url, {
    method: "POST",
    headers,
    body: JSON.stringify(body),
  });

  if (!res.ok) {
    const errText = await res.text();
    throw new Error(`Clova API 오류 (${res.status}): ${errText}`);
  }

  const data = await res.json();
  const elapsed = Date.now() - startTime;

  const content = data?.result?.message?.content;
  if (!content) throw new Error(`Clova 응답에 content 없음: ${JSON.stringify(data)}`);

  return {
    content,
    elapsed,
    inputLength: data?.result?.inputLength,
    outputLength: data?.result?.outputLength,
    stopReason: data?.result?.stopReason,
  };
}

/**
 * Clova 간단 텍스트 생성
 */
export async function clovaGenerateText(userMessage, systemMessage, options = {}) {
  const messages = [];
  if (systemMessage) messages.push({ role: "system", content: systemMessage });
  messages.push({ role: "user", content: userMessage });

  return clovaChatCompletion(messages, options);
}
