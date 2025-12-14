package com.haruUp.category.domain.dto

import com.haruUp.category.domain.entity.Job
import lombok.NoArgsConstructor

@NoArgsConstructor
class JobDto(
    var id : Long,

    var jobName: String

) {

    fun toEntity() : Job = Job(
        id = this.id,
        jobName = this.jobName
    )


}
