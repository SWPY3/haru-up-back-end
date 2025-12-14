package com.haruUp.category.repository

import com.haruUp.category.domain.entity.Job
import org.springframework.data.jpa.repository.JpaRepository

interface JobRepository : JpaRepository<Job, Long> {
}