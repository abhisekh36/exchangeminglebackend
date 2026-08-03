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
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule

/**
 * Shared ObjectMapper for anything stored in Redis (cache values, RedisTemplate).
 *
 * Two separate problems had to be fixed here, both stemming from the same root
 * cause: GenericJackson2JsonRedisSerializer's no-arg constructor builds its OWN
 * internal ObjectMapper, completely separate from Spring Boot's auto-configured
 * one, and that internal mapper is configured in ways a hand-built one isn't
 * unless you replicate them:
 *
 * 1. Kotlin module — Kotlin data classes (TeacherCard, OpenRequestCard, ...) have
 *    no default no-arg constructor. Without jackson-module-kotlin registered,
 *    Jackson can still SERIALIZE them (via getters) but throws
 *    "Cannot construct instance of ... (no Creators, like default constructor,
 *    exist)" the moment it DESERIALIZES one back out of Redis.
 *
 * 2. Default typing — the no-arg constructor also calls activateDefaultTyping(),
 *    which embeds an "@class" field in the cached JSON recording the concrete
 *    type. Without it, Jackson has no idea what type to build on the way back
 *    out and falls back to a generic LinkedHashMap — which is exactly what
 *    Spring's @Cacheable proxy then fails to unchecked-cast back to
 *    PagedTeacherCardResponse (ClassCastException: LinkedHashMap cannot be
 *    cast to PagedTeacherCardResponse).
 *
 * Fixing only #1 (as done previously) stops the crash on construction but still
 * leaves deserialization producing the wrong type, since there's still no type
 * metadata in the cached JSON to tell Jackson to build a PagedTeacherCardResponse
 * instead of a plain Map. Both are required together.
 */
fun buildRedisObjectMapper(): ObjectMapper {
    val mapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    // Only our own service ever writes to this cache (it's not fed by any
    // external/untrusted input), so validating against Any is safe here —
    // this mirrors what GenericJackson2JsonRedisSerializer's own no-arg
    // constructor does internally by default.
    val typeValidator = BasicPolymorphicTypeValidator.builder()
        .allowIfBaseType(Any::class.java)
        .build()

    mapper.activateDefaultTyping(typeValidator, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY)

    return mapper
}

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