package com.haruUp.category.domain.entity

import com.haruUp.category.domain.dto.JobDto
import com.haruUp.global.common.BaseEntity
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import lombok.NoArgsConstructor

@Entity
@NoArgsConstructor
class Job (
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id : Long,

    var jobName: String

) : BaseEntity() {

    fun toDto() : JobDto = JobDto(
        id = this.id,
        jobName = this.jobName
    )

}