package com.haruUp.global.clova

import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * Clova Embedding API Client
 *
 * - Clova Studio의 CLIR Embedding (Dolphin) 모델 사용
 * - 한국어 특화 임베딩 모델
 * - 벡터 차원: 1024
 *
 * API 문서: https://api.ncloud-docs.com/docs/clovastudio-embedding
 */
@Component
class ClovaEmbeddingClient(
    private val clovaRestClient: RestClient,
    private val generateRequestId: () -> String
) {

    companion object {
        private const val EMBEDDING_ENDPOINT = "/testapp/v1/api-tools/embedding/clir-emb-dolphin"
        const val VECTOR_SIZE = 1024  // Clova Embedding 벡터 차원
    }

    /**
     * 단일 텍스트 임베딩
     *
     * @param text 임베딩할 텍스트
     * @return 1024차원 임베딩 벡터
     */
    suspend fun createEmbedding(text: String): List<Float> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val request = ClovaEmbeddingRequest(text = text)

        val response = clovaRestClient.post()
            .uri(EMBEDDING_ENDPOINT)
            .header("X-NCP-CLOVASTUDIO-REQUEST-ID", generateRequestId())
            .body(request)
            .retrieve()
            .body<ClovaEmbeddingResponse>()
            ?: throw RuntimeException("Clova Embedding API 응답이 null입니다.")

        return@withContext response.result.embedding
    }

    /**
     * 배치 임베딩 (여러 텍스트를 순차적으로 처리)
     *
     * Clova API는 배치를 지원하지 않으므로 순차 처리
     *
     * @param texts 임베딩할 텍스트 목록
     * @return 각 텍스트의 임베딩 벡터 목록
     */
    suspend fun createEmbeddingBatch(texts: List<String>): List<List<Float>> {
        return texts.map { text ->
            createEmbedding(text)
        }
    }
}

/**
 * Clova Embedding API 요청
 */
data class ClovaEmbeddingRequest(
    val text: String
)

/**
 * Clova Embedding API 응답
 */
data class ClovaEmbeddingResponse(
    val status: EmbeddingStatus,
    val result: EmbeddingResult
)

data class EmbeddingStatus(
    val code: String,
    val message: String
)

data class EmbeddingResult(
    val embedding: List<Float>
)
