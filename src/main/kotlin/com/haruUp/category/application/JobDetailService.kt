package com.haruUp.category.application

import com.haruUp.category.domain.dto.JobDetailDto
import com.haruUp.category.repository.JobDetailRepository
import org.springframework.stereotype.Service

@Service
class JobDetailService(
    private val jobDetailRepository: JobDetailRepository
) {

    /** 직업 ID 기준 세부 직업 목록을 조회한다. */
    fun getJobDetailList(jobId: Long): List<JobDetailDto> =
        jobDetailRepository.findByJobId(jobId).map { it.toDto() }

    /** 세부 직업 ID로 단건 조회한다. */
    fun getJobDetail(jobDetailId: Long): JobDetailDto {
        val jobDetail = jobDetailRepository.findById(jobDetailId)
            .orElseThrow { IllegalArgumentException("존재 하지 않는 jobDeatilId 입니다.") }

        return jobDetail.toDto()
    }
}
