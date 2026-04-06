package com.exchangemingle.backend.config

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy
import io.github.bucket4j.distributed.proxy.ProxyManager
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.codec.ByteArrayCodec
import io.lettuce.core.codec.RedisCodec
import io.lettuce.core.codec.StringCodec
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.ByteBuffer
import java.time.Duration

@Configuration
class RateLimitConfig {

    @Value("\${spring.data.redis.host}")
    private lateinit var redisHost: String

    @Value("\${spring.data.redis.port}")
    private var redisPort: Int = 6379

    @Value("\${spring.data.redis.password}")
    private lateinit var redisPassword: String

    @Value("\${spring.data.redis.ssl.enabled}")
    private var sslEnabled: Boolean = false

    @Bean
    fun proxyManager(): ProxyManager<String> {
        val redisURI = RedisURI.builder()
            .withHost(redisHost)
            .withPort(redisPort)
            .withPassword(redisPassword.toCharArray())
            .withSsl(sslEnabled)
            .build()

        val redisClient = RedisClient.create(redisURI)

        val connection = redisClient.connect(object : RedisCodec<String, ByteArray> {
            private val stringCodec = StringCodec.UTF8
            private val byteArrayCodec = ByteArrayCodec.INSTANCE

            override fun decodeKey(bytes: ByteBuffer): String = stringCodec.decodeKey(bytes)
            override fun encodeKey(key: String): ByteBuffer = stringCodec.encodeKey(key)
            override fun decodeValue(bytes: ByteBuffer): ByteArray = byteArrayCodec.decodeValue(bytes)
            override fun encodeValue(value: ByteArray): ByteBuffer = byteArrayCodec.encodeValue(value)
        })

        return LettuceBasedProxyManager.builderFor(connection)
            .withExpirationStrategy(
                // Auto-calculates Redis key TTL based on each bucket's longest refill window.
                // e.g. your email bucket refills over 1 hour -> that key gets a 1-hour TTL automatically.
                ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofHours(1))
            )
            .build()
    }
}