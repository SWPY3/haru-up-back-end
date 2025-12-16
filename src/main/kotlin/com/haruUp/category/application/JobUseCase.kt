package com.haruUp.category.application

import com.haruUp.category.domain.dto.JobDetailDto
import com.haruUp.category.domain.dto.JobDto
import org.springframework.stereotype.Component

@Component
class JobUseCase(
    private val jobService: JobService,
    private val jobDetailService : JobDetailService,
) {


    // job 조회
    fun getJobList() : List<JobDto> {
        return jobService.getJobList()
    }

    // jobDetail 조회
    fun getJobDetail( jobId : Long) : List<JobDetailDto> {
        return jobDetailService.getJobDetailList(jobId)
    }
}