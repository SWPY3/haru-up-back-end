package com.haruUp.global.security

import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController("/actuator")
class SSLConnectionController {

    @RequestMapping("/health")
    fun sslConnection() : String{
       return "OK";
    }
}