package com.haruUp.category.repository

import com.haruUp.category.domain.entity.JobDetail
import org.springframework.data.jpa.repository.JpaRepository

interface JobDetailRepository : JpaRepository<JobDetail, Long> {

    fun findByJobId(jobId: Long) : List<JobDetail>
}