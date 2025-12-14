package com.haruUp.category.domain.entity

import com.haruUp.category.domain.dto.JobDetailDto
import com.haruUp.global.common.BaseEntity
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id

@Entity
class JobDetail(

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id : Long,

    var jobId : Long,

    var jobDetailName : String
) : BaseEntity() {

    fun toDto(): JobDetailDto = JobDetailDto(
        id = this.id,
        jobId = this.jobId,
        jobDetailName = jobDetailName
    )

}