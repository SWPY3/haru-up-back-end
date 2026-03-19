package com.haruUp.global.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.springframework.context.annotation.Configuration
import javax.annotation.PostConstruct

@Configuration
class FirebaseConfig {

    @PostConstruct
    fun init(){
        // Firebase SDK 초기화 로직 추가
        // 예: FirebaseApp.initializeApp(options)
        if(FirebaseApp.getApps().isNotEmpty()) return

        val serviceAccount = this::class.java.getResourceAsStream("/firebase-service-account.json")
            ?: throw IllegalStateException("Firebase service account file not found")

        val options = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.fromStream(serviceAccount))
            .build()

        FirebaseApp.initializeApp(options)

    }
}