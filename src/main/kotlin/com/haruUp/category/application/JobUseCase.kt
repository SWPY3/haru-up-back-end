package com.haruUp.category.application

import com.haruUp.category.domain.dto.JobDetailDto
import com.haruUp.category.domain.dto.JobDto
import org.springframework.stereotype.Component

@Component
class JobUseCase(
    private val jobService: JobService,
    private val jobDetailService: JobDetailService,
) {

    /** 직업 목록 조회 유즈케이스다. */
    fun getJobList(): List<JobDto> = jobService.getJobList()

    /** 특정 직업의 세부 직업 목록 조회 유즈케이스다. */
    fun getJobDetail(jobId: Long): List<JobDetailDto> = jobDetailService.getJobDetailList(jobId)
}
