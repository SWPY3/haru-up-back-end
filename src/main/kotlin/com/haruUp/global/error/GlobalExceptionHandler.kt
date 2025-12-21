package com.haruUp.global.error

import com.haruUp.global.common.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.BindException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(
        ex: BusinessException,
        request: jakarta.servlet.http.HttpServletRequest
    ): ResponseEntity<ApiResponse<Nothing>> {

        // 🔥 SSE 요청은 GlobalExceptionHandler에서 처리하지 않음
        if (request.getHeader("Accept")?.contains("text/event-stream") == true) {
            throw ex
        }

        val status = when (ex.errorCode) {
            ErrorCode.UNAUTHORIZED -> HttpStatus.UNAUTHORIZED
            ErrorCode.FORBIDDEN -> HttpStatus.FORBIDDEN
            ErrorCode.MEMBER_NOT_FOUND -> HttpStatus.NOT_FOUND
            ErrorCode.MEMBER_DUPLICATE_EMAIL -> HttpStatus.BAD_REQUEST
            ErrorCode.RATE_LIMIT_EXCEEDED -> HttpStatus.TOO_MANY_REQUESTS
            else -> HttpStatus.BAD_REQUEST
        }

        val body = ApiResponse.failure<Nothing>(ex.message)
        return ResponseEntity.status(status).body(body)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class, BindException::class)
    fun handleValidationExceptions(
        ex: Exception,
        request: jakarta.servlet.http.HttpServletRequest
    ): ResponseEntity<ApiResponse<Nothing>> {

        // 🔥 SSE 요청 제외
        if (request.getHeader("Accept")?.contains("text/event-stream") == true) {
            throw ex
        }

        val errorMessage = when (ex) {
            is MethodArgumentNotValidException ->
                ex.bindingResult.allErrors.joinToString(", ") { it.defaultMessage ?: "Invalid input" }
            is BindException ->
                ex.bindingResult.allErrors.joinToString(", ") { it.defaultMessage ?: "Invalid input" }
            else -> "Invalid input"
        }

        val body = ApiResponse.failure<Nothing>(errorMessage)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body)
    }

    @ExceptionHandler(Exception::class)
    fun handleException(
        ex: Exception,
        request: jakarta.servlet.http.HttpServletRequest
    ): ResponseEntity<ApiResponse<Nothing>> {

        // ⭐ 가장 중요한 부분
        if (request.getHeader("Accept")?.contains("text/event-stream") == true) {
            throw ex   // ← SSE는 컨트롤러/Emitter에서 처리
        }

        val body = ApiResponse.failure<Nothing>(ErrorCode.INTERNAL_SERVER_ERROR.message)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body)
    }
}