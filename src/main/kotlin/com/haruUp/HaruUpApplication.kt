package com.haruUp

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class HaruUpApplication

fun main(args: Array<String>) {
    runApplication<HaruUpApplication>(*args)
}
