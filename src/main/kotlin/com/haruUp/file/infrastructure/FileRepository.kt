package com.haruUp.file.infrastructure

import com.haruUp.file.domian.File
import org.springframework.data.jpa.repository.JpaRepository

interface FileRepository  : JpaRepository<File, Long>{
}