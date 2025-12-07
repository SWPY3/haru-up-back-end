package com.haruUp.file.domian

import com.haruUp.global.common.BaseEntity
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import lombok.NoArgsConstructor

@Entity
@NoArgsConstructor
@Table(name = "files")
class File(

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    var originalName: String,             // 원본 파일명
    var storedName: String,               // 서버에 저장된 파일명(UUID 등)
    var filePath: String,                 // 저장 경로
    var fileSize: Long,                    // 파일 크기
    var contentType: String? = null,       // MIME 타입

) : BaseEntity() {

    fun toDto() : FileDto = FileDto(
        id = this.id,
        originalName = this.originalName,
        storedName = this.storedName,
        filePath = this.filePath,
        fileSize = this.fileSize,
        contentType = this.contentType
    )
}