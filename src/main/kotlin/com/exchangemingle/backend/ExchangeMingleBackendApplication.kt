package com.exchangemingle.backend

import de.codecentric.boot.admin.server.config.EnableAdminServer
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableAdminServer
@EnableScheduling
class ExchangeMingleBackendApplication

fun main(args: Array<String>) {
	runApplication<ExchangeMingleBackendApplication>(*args)
}