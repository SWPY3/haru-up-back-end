package com.haruUp.member.controller

import com.haruUp.member.application.service.MemberService
import com.haruUp.member.application.useCase.MemberAccountUseCase
import lombok.RequiredArgsConstructor
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequiredArgsConstructor
@RequestMapping("/members/")
class MemberInfoController(
    private val memberAccountUseCase : MemberAccountUseCase
) {


    @GetMapping("/statistics/excel")
    fun downloadMemberStatisticsExcel(): ResponseEntity<ByteArray> {

        val excelBytes = memberAccountUseCase.createMemberStatisticsExcel()

        return ResponseEntity.ok()
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=사용자 정보.xlsx"
            )
            .contentType(
                MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                )
            )
            .body(excelBytes)
    }


}