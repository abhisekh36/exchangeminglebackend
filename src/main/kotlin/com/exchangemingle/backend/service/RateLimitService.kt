package com.exchangemingle.backend.service

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import io.github.bucket4j.BucketConfiguration
import io.github.bucket4j.Refill
import io.github.bucket4j.distributed.proxy.ProxyManager
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.function.Supplier

@Service
class RateLimitService(
    private val proxyManager: ProxyManager<String>
) {

    companion object {
        private const val AUTH_REQUESTS_PER_MINUTE = 5
        private const val EMAIL_REQUESTS_PER_HOUR = 3
        private const val API_REQUESTS_PER_MINUTE = 100
    }

    /**
     * Rate limit for authentication endpoints (login, register)
     * 5 requests per minute
     */
    fun resolveAuthBucket(key: String): Bucket {
        return proxyManager.builder().build(
            key,
            getAuthBucketConfiguration()
        )
    }

    /**
     * Rate limit for email operations (verification, reset)
     * 3 requests per hour
     */
    fun resolveEmailBucket(key: String): Bucket {
        return proxyManager.builder().build(
            key,
            getEmailBucketConfiguration()
        )
    }

    /**
     * Rate limit for general API requests
     * 100 requests per minute
     */
    fun resolveApiBucket(key: String): Bucket {
        return proxyManager.builder().build(
            key,
            getApiBucketConfiguration()
        )
    }

    private fun getAuthBucketConfiguration(): Supplier<BucketConfiguration> {
        return Supplier {
            BucketConfiguration.builder()
                .addLimit(
                    Bandwidth.classic(
                        AUTH_REQUESTS_PER_MINUTE.toLong(),
                        Refill.intervally(AUTH_REQUESTS_PER_MINUTE.toLong(), Duration.ofMinutes(1))
                    )
                )
                .build()
        }
    }

    private fun getEmailBucketConfiguration(): Supplier<BucketConfiguration> {
        return Supplier {
            BucketConfiguration.builder()
                .addLimit(
                    Bandwidth.classic(
                        EMAIL_REQUESTS_PER_HOUR.toLong(),
                        Refill.intervally(EMAIL_REQUESTS_PER_HOUR.toLong(), Duration.ofHours(1))
                    )
                )
                .build()
        }
    }

    private fun getApiBucketConfiguration(): Supplier<BucketConfiguration> {
        return Supplier {
            BucketConfiguration.builder()
                .addLimit(
                    Bandwidth.classic(
                        API_REQUESTS_PER_MINUTE.toLong(),
                        Refill.intervally(API_REQUESTS_PER_MINUTE.toLong(), Duration.ofMinutes(1))
                    )
                )
                .build()
        }
    }
}