package com.haruUp.character.controller

import com.haruUp.character.application.CharacterUseCase
import com.haruUp.character.domain.dto.CharacterDto
import com.haruUp.global.common.ApiResponse
import com.haruUp.global.security.MemberPrincipal
import io.lettuce.core.json.JsonObject
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


@RestController
@RequestMapping("/api/character")
class CharacterController(
    private val characterUseCase: CharacterUseCase
) {

    // 캐릭터 조회
    @GetMapping("/list")
    fun getCharacterList(
        @AuthenticationPrincipal principal: MemberPrincipal
    ): List<CharacterDto> {
        return characterUseCase.characterList()
    }

    // member 캐릭터 선택
    @PostMapping("/selected")
    fun selectedCharacter(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @RequestBody request: SelectCharacterRequest   // ✅ DTO로 받기
    ): ApiResponse<String> {

        characterUseCase.createInitialCharacter(
            principal.id,
            request.characterId
        )
        return ApiResponse.success("OK")
    }









    data class SelectCharacterRequest(
        val characterId: Long
    )

}
