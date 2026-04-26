/**
 * Claude 클라이언트 (Claude Agent SDK 사용)
 *
 * @anthropic-ai/claude-agent-sdk의 query() 함수로
 * 에이전트 방식으로 미션 추천을 수행
 */

import { query } from "@anthropic-ai/claude-agent-sdk";

/**
 * Claude Agent SDK로 텍스트 생성
 *
 * query()는 async iterable로 메시지를 스트리밍하며,
 * "result" 필드가 있는 메시지가 최종 응답
 */
export async function claudeGenerateText(userMessage, systemMessage, options = {}) {
  const {
    model = "claude-opus-4-6",
    maxTurns = 1,
  } = options;

  const prompt = systemMessage
    ? `${systemMessage}\n\n---\n\n${userMessage}`
    : userMessage;

  const startTime = Date.now();
  let resultText = "";

  for await (const message of query({
    prompt,
    options: {
      model,
      maxTurns,
      systemPrompt: systemMessage || undefined,
      allowedTools: [],
      permissionMode: "dontAsk",
    },
  })) {
    if ("result" in message) {
      resultText = message.result;
    }
  }

  const elapsed = Date.now() - startTime;

  return {
    content: resultText,
    elapsed,
    model,
  };
}
