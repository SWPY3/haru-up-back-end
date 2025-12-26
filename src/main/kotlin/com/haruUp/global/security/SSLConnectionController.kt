package com.haruUp.global.security

import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class SSLConnectionController {

    @RequestMapping("/actuator/health")
    fun sslConnection() : String{
       return "OK";
    }
}