package com.haruUp.global.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration

@EnableCaching
@Configuration
class RedisConfig {

    @Value("\${spring.redis.host}")
    lateinit var redisHost: String

    @Value("\${spring.redis.port}")
    lateinit var redisPort: String

    @Bean
    fun redisTemplate(connectionFactory : RedisConnectionFactory) : RedisTemplate<String, Any> {
       val template = RedisTemplate<String, Any>()
        template.connectionFactory = connectionFactory

        template.keySerializer = StringRedisSerializer()
        template.valueSerializer = StringRedisSerializer()
        template.hashKeySerializer = StringRedisSerializer()
        template.hashValueSerializer = StringRedisSerializer()

        template.afterPropertiesSet()
        return template
    }

    @Bean
    fun cacheManager( connectionFactory : RedisConnectionFactory) : CacheManager {
       val config = RedisCacheConfiguration.defaultCacheConfig()
           .entryTtl(Duration.ofMillis(30))
           .serializeKeysWith(
               RedisSerializationContext.SerializationPair.fromSerializer( StringRedisSerializer() )
           )
           .serializeValuesWith(
               RedisSerializationContext.SerializationPair.fromSerializer( GenericJackson2JsonRedisSerializer() )
           )

        return RedisCacheManager.builder()
            .cacheDefaults( config )
            .build()
    }
}