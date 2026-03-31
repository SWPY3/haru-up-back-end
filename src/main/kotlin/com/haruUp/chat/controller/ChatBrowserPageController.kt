package com.haruUp.chat.controller

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
@Profile("chat-browser")
class ChatBrowserPageController {

    @GetMapping("/")
    fun root(): String = "redirect:/chat-test.html"
}
