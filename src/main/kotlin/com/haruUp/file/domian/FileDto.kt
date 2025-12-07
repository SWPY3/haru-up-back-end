package com.haruUp.file.domian

import com.haruUp.global.common.BaseEntity


class FileDto (

    var id: Long? = null,
    var originalName: String,             // 원본 파일명
    var storedName: String,               // 서버에 저장된 파일명(UUID 등)
    var filePath: String,                 // 저장 경로
    var fileSize: Long,                    // 파일 크기
    var contentType: String? = null,       // MIME 타입

) : BaseEntity() {

    fun toEntity() : File = File(
        id = this.id,
        originalName = this.originalName,
        storedName = this.storedName,
        filePath = this.filePath,
        fileSize = this.fileSize,
        contentType = this.contentType
    )
}