package com.haruUp.category.application

import com.haruUp.category.domain.dto.JobDetailDto
import com.haruUp.category.domain.entity.Job
import com.haruUp.category.domain.entity.JobDetail
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


    fun getJobDetail(jobDetailId : Long) : JobDetailDto {
        val jobDetail = jobDetailRepository.findById(jobDetailId)
            .orElseThrow { IllegalArgumentException("존재 하지 않는 jobDeatilId 입니다.") }


        return jobDetail.toDto()
    }



}
