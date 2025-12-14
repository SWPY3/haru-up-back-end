package com.haruUp.category.domain.dto

import com.haruUp.category.domain.entity.JobDetail
import com.haruUp.global.common.BaseEntity

class JobDetailDto (
    var id : Long,

    var jobId : Long,

    var jobDetailName : String
) : BaseEntity() {

    fun toEntity() : JobDetail = JobDetail(
        id = this.id,
        jobId = this.jobId,
       jobDetailName = this.jobDetailName
    )
}
