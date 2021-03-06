package com.harrison.server

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
class WebServerApplication

fun main(args: Array<String>) {
    SpringApplication.run(WebServerApplication::class.java, *args)
}
