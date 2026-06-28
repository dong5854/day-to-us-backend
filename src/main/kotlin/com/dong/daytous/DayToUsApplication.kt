package com.dong.daytous

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class DayToUsApplication

fun main(args: Array<String>) {
    runApplication<DayToUsApplication>(*args)
}
