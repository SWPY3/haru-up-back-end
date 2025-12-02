package com.haruUp.interest.controller

import com.haruUp.global.common.ApiResponse
import com.haruUp.interest.application.InterestService
import com.haruUp.interest.domain.InterestDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/interests")
@Tag(name = "Interest", description = "관심사 관리 API")
class InterestController(
    private val interestService: InterestService
) {

    @Operation(
        summary = "관심사 트리 조회",
        description = """
            관심사 계층 구조를 조회합니다.
            - 파라미터 없음: 대분류(depth=1) 목록 반환
            - parent_id: 해당 부모의 자식 관심사 목록 반환
            - depth: 해당 깊이의 모든 관심사 목록 반환
            - parent_id + depth: 해당 부모의 특정 깊이 자식 목록 반환
        """
    )
    @GetMapping
    fun getInterests(
        @Parameter(description = "부모 관심사 ID (중/소분류 조회 시 사용)")
        @RequestParam(required = false) parentId: Long?,
        @Parameter(description = "관심사 깊이 (1:대분류, 2:중분류, 3:소분류)")
        @RequestParam(required = false) depth: Int?
    ): ApiResponse<List<InterestDto>> {
        val interests = interestService.getInterests(parentId, depth)
        return ApiResponse.success(interests)
    }

    @Operation(summary = "관심사 AI 추천데이터 가져오기", description = "관심사 데이터를 AI를 통해 가져옵니다.")
    @GetMapping("/ai-recommend")
    fun getAiRecommendInterests(
        @Parameter(description = "부모 관심사 ID (중/소분류 조회 시 사용)")
        @RequestParam(required = false) parentId: Long?,
        @Parameter(description = "사용자가 입력한 관심사 메시지")
        @RequestParam(required = false) message: String?
    ): ApiResponse<List<InterestDto>> {
        val interests = interestService.getAiRecommendInterests(parentId, message)
        return ApiResponse.success(interests)
    }
}