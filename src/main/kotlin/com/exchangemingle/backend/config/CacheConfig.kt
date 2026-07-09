package com.exchangemingle.backend.config

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import java.time.Duration
import java.util.concurrent.TimeUnit

@Configuration
@EnableCaching
class CacheConfig {

    /**
     * L1 Cache - Caffeine (In-Memory, Ultra Fast)
     * Use for frequently accessed, small data
     */
    @Bean
    @Primary
    fun caffeineCacheManager(): CacheManager {
        val cacheManager = CaffeineCacheManager("users", "skills", "user-profiles")
        cacheManager.setCaffeine(
            Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .recordStats()
        )
        return cacheManager
    }

    /**
     * L2 Cache - Redis (Distributed, Shared across instances)
     * Use for data that needs to be shared across multiple instances
     */
    @Bean
    fun redisCacheManager(connectionFactory: RedisConnectionFactory): CacheManager {
        val config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    GenericJackson2JsonRedisSerializer()
                )
            )
            .disableCachingNullValues()

        val perCacheTtl = mapOf(
            "discovery-teachers"  to Duration.ofMinutes(5),
            "discovery-requests"  to Duration.ofMinutes(2),
            "open-requests-feed"  to Duration.ofMinutes(2)
        )

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(config)
            .withInitialCacheConfigurations(
                perCacheTtl.mapValues { (_, ttl) -> config.entryTtl(ttl) }
            )
            .transactionAware()
            .build()
    }
}