package com.haruUp.file.application

import com.haruUp.file.domian.File
import com.haruUp.file.infrastructure.FileRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID

@Service
class FileService(
    private val fileRepository: FileRepository,

    @Value("\${file.upload-dir}")  // application.yml 설정 필요
    private val uploadDir: String
) {

    fun upload(file: MultipartFile): File {
        val uuid = UUID.randomUUID().toString()
        val storedName = "$uuid-${file.originalFilename}"
        val filePath = Paths.get(uploadDir, storedName)

        // 디렉토리 없으면 생성
        Files.createDirectories(filePath.parent)

        // 실제 파일 저장
        Files.copy(file.inputStream, filePath)

        // DB 저장
        val entity = File(
            originalName = file.originalFilename ?: "unknown",
            storedName = storedName,
            filePath = filePath.toString(),
            fileSize = file.size,
            contentType = file.contentType
        )

        return fileRepository.save(entity)
    }

    fun getFile(id: Long): File =
        fileRepository.findById(id)
            .orElseThrow { IllegalArgumentException("File not found: $id") }

    fun deleteFile(id: Long) {
        val file = getFile(id)

        // 로컬 파일 삭제
        val path = Path.of(file.filePath)
        Files.deleteIfExists(path)

        // DB 삭제
        fileRepository.delete(file)
    }
}