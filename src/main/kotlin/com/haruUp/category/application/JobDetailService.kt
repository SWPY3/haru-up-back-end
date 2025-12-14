package com.haruUp.category.application

import com.haruUp.category.domain.dto.JobDetailDto
import com.haruUp.category.repository.JobDetailRepository
import org.springframework.stereotype.Service

@Service
class JobDetailService (
    private val jobDetailRepository: JobDetailRepository
) {

    // jobDetail 조회
    fun getJobDetailList( jobId : Long) : List<JobDetailDto>{
       return jobDetailRepository.findByJobId(jobId)
           .stream()
           .map { jobDetail -> jobDetail.toDto() }
           .toList()
    }



}
