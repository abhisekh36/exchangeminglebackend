package com.exchangemingle.backend.config

import io.lettuce.core.ClientOptions
import io.lettuce.core.SocketOptions
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule

/**
 * Shared ObjectMapper for anything stored in Redis (cache values, RedisTemplate).
 *
 * GenericJackson2JsonRedisSerializer's no-arg constructor builds its OWN internal
 * ObjectMapper that is completely separate from Spring Boot's auto-configured one —
 * it does NOT pick up the Kotlin module or JavaTimeModule automatically. Kotlin data
 * classes (TeacherCard, OpenRequestCard, ...) have no default no-arg constructor, so
 * without the Kotlin module Jackson can still SERIALIZE them (via getters) but throws
 * "Cannot construct instance of ... (no Creators, like default constructor, exist)"
 * the moment it tries to DESERIALIZE one back out of Redis. That's exactly why a
 * cache MISS (fresh DB query, no deserialization involved) works fine, while the very
 * next request within the cache's TTL (a cache HIT, which does deserialize) 500s.
 */
fun buildRedisObjectMapper(): ObjectMapper =
    ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

@Configuration
class RedisConfig {

    @Value("\${spring.data.redis.host}")
    private lateinit var redisHost: String

    @Value("\${spring.data.redis.port}")
    private var redisPort: Int = 6379

    @Value("\${spring.data.redis.password}")
    private lateinit var redisPassword: String

    @Value("\${spring.data.redis.ssl.enabled}")
    private var sslEnabled: Boolean = false

    @Bean
    fun redisConnectionFactory(): RedisConnectionFactory {
        val redisConfig = RedisStandaloneConfiguration().apply {
            hostName = redisHost
            port = redisPort
            setPassword(redisPassword)
        }

        val socketOptions = SocketOptions.builder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()

        val clientOptions = ClientOptions.builder()
            .socketOptions(socketOptions)
            .build()

        val clientConfigBuilder = LettuceClientConfiguration.builder()
            .clientOptions(clientOptions)
            .commandTimeout(Duration.ofSeconds(5))

        if (sslEnabled) {
            clientConfigBuilder.useSsl()
        }

        return LettuceConnectionFactory(redisConfig, clientConfigBuilder.build())
    }

    @Bean
    fun redisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, Any> {
        val template = RedisTemplate<String, Any>()
        template.connectionFactory = connectionFactory
        template.keySerializer = StringRedisSerializer()
        template.valueSerializer = GenericJackson2JsonRedisSerializer(buildRedisObjectMapper())
        template.hashKeySerializer = StringRedisSerializer()
        template.hashValueSerializer = GenericJackson2JsonRedisSerializer(buildRedisObjectMapper())
        return template
    }
}