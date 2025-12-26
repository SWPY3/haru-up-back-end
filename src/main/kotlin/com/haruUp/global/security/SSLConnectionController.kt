package com.haruUp.global.security

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/actuator")
class SSLConnectionController {

    @GetMapping("/health")
    fun health(): String {
        return "OK"
    }
}