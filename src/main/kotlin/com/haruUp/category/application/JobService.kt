package com.haruUp.category.application

import com.haruUp.category.domain.dto.JobDto
import com.haruUp.category.repository.JobJpaRepository
import org.springframework.stereotype.Service

@Service
class JobService (
    private val jobRepository : JobJpaRepository
) {

    // job 조회
    fun getJobList(): List<JobDto>{
        return jobRepository.findAll().stream()
            .map { job -> job.toDto()  }
            .toList()
    }

    fun getJob(jobId: Long): JobDto {
        val job = jobRepository.findById(jobId)
            .orElseThrow { IllegalArgumentException("존재하지 않는 Job ID: $jobId") }

        return job.toDto()
    }
}