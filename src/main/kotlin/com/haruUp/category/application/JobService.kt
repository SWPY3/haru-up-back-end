package com.haruUp.category.application

import com.haruUp.category.domain.dto.JobDto
import com.haruUp.category.repository.JobJpaRepository
import org.springframework.stereotype.Service

@Service
class JobService(
    private val jobRepository: JobJpaRepository
) {

    /** 직업 목록을 조회한다. */
    fun getJobList(): List<JobDto> = jobRepository.findAll().map { it.toDto() }

    /** 직업 ID로 단건 조회한다. */
    fun getJob(jobId: Long): JobDto {
        val job = jobRepository.findById(jobId)
            .orElseThrow { IllegalArgumentException("존재하지 않는 Job ID: $jobId") }

        return job.toDto()
    }
}
