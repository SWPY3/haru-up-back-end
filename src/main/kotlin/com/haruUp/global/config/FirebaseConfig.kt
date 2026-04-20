package com.haruUp.global.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import javax.annotation.PostConstruct

@Configuration
class FirebaseConfig {

    private val log = LoggerFactory.getLogger(FirebaseConfig::class.java)

    @PostConstruct
    fun init() {
        if (FirebaseApp.getApps().isNotEmpty()) {
            log.info("FirebaseApp already initialized")
            return
        }

        val resourcePath = "/firebase-service-account.json"

        try {
            val resourceUrl = this::class.java.getResource(resourcePath)
            log.info("Firebase resource url = {}", resourceUrl)

            this::class.java.getResourceAsStream(resourcePath).use { inputStream ->
                if (inputStream == null) {
                    throw IllegalStateException("Firebase service account file not found in classpath: $resourcePath")
                }

                log.info("Firebase service account file found in classpath")

                val credentials = GoogleCredentials.fromStream(inputStream)
                log.info("GoogleCredentials loaded successfully")

                val options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .build()

                FirebaseApp.initializeApp(options)
                log.info("Firebase initialized successfully")
            }
        } catch (e: Exception) {
            log.error("Firebase initialization failed", e)
            throw IllegalStateException("Failed to initialize Firebase", e)
        }
    }
}