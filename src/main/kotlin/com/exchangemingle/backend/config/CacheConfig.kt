package com.exchangemingle.backend.config

import com.github.benmanes.caffeine.cache.Caffeine
import org.slf4j.LoggerFactory
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.CachingConfigurer
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.cache.interceptor.CacheErrorHandler
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
class CacheConfig : CachingConfigurer {

    private val logger = LoggerFactory.getLogger(CacheConfig::class.java)

    /**
     * A cache is an optimization, never a dependency. If Redis has a bad moment —
     * a dropped connection, a timeout, a serialization mismatch we haven't
     * anticipated, anything — the request must still succeed by falling through
     * to the live @Cacheable method body instead of surfacing a 500 to the user.
     * This is what was turning transient Redis hiccups into "Couldn't load
     * teachers" / "Couldn't load requests" screens.
     */
    override fun errorHandler(): CacheErrorHandler = object : CacheErrorHandler {
        override fun handleCacheGetError(exception: RuntimeException, cache: Cache, key: Any) {
            logger.warn("Cache GET failed for '${cache.name}' key=$key — falling through to live data: ${exception.message}")
        }
        override fun handleCachePutError(exception: RuntimeException, cache: Cache, key: Any, value: Any?) {
            logger.warn("Cache PUT failed for '${cache.name}' key=$key — response already served fine, just won't be cached: ${exception.message}")
        }
        override fun handleCacheEvictError(exception: RuntimeException, cache: Cache, key: Any) {
            logger.warn("Cache EVICT failed for '${cache.name}' key=$key: ${exception.message}")
        }
        override fun handleCacheClearError(exception: RuntimeException, cache: Cache) {
            logger.warn("Cache CLEAR failed for '${cache.name}': ${exception.message}")
        }
    }

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
                    GenericJackson2JsonRedisSerializer(buildRedisObjectMapper())
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