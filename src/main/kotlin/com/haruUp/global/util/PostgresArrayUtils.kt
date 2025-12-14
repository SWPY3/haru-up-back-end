package com.haruUp.global.util

/**
 * PostgreSQL 배열 유틸리티
 */
object PostgresArrayUtils {

    /**
     * List<String>을 PostgreSQL 배열 형식 문자열로 변환
     * 예: ["외국어 공부", "일본어", "단어 학습"] → "{외국어 공부,일본어,단어 학습}"
     *
     * Native Query에서 text[] 타입 파라미터로 사용할 때 필요
     */
    fun listToPostgresArray(list: List<String>): String {
        return list.joinToString(",", "{", "}") { value ->
            // 쉼표, 중괄호, 따옴표가 포함된 경우 따옴표로 감싸고 이스케이프
            if (value.contains(",") || value.contains("{") || value.contains("}") || value.contains("\"") || value.contains("\\")) {
                "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
            } else {
                value
            }
        }
    }
}
