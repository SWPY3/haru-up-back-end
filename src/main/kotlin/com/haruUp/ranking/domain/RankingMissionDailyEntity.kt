package com.haruUp.ranking.domain

import com.haruUp.global.common.BaseEntity
import com.haruUp.member.domain.type.MemberGender
import io.hypersistence.utils.hibernate.type.array.ListArrayType
import jakarta.persistence.*
import org.hibernate.annotations.Type
import java.time.LocalDate

/**
 * 미션 인기 랭킹 데이터 (일별 수집)
 *
 * 배치에서 선택된 미션 데이터를 수집하여 저장
 * 인기차트 API에서 필터링 조회에 사용
 */
@Entity
@Table(
    name = "ranking_mission_daily",
    indexes = [
        Index(name = "idx_ranking_daily_date", columnList = "ranking_date"),
        Index(name = "idx_ranking_daily_label", columnList = "label_name"),
        Index(name = "idx_ranking_daily_filter", columnList = "ranking_date, birth_dt, gender, job_id, job_detail_id")
    ]
)
class RankingMissionDailyEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "ranking_date", nullable = false)
    val rankingDate: LocalDate,

    @Column(name = "member_mission_id", nullable = false, unique = true)
    val memberMissionId: Long,

    @Column(name = "mission_id", nullable = false)
    val missionId: Long,

    @Column(name = "label_name", length = 100)
    val labelName: String? = null,

    /**
     * 관심사 전체 경로 배열
     * interest_embeddings.full_path에서 가져옴
     * 예: ["외국어 공부", "영어", "단어 학습"]
     */
    @Type(ListArrayType::class)
    @Column(name = "interest_full_path", columnDefinition = "TEXT[]")
    val interestFullPath: List<String>? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10)
    val gender: MemberGender? = null,

    @Column(name = "birth_dt")
    val birthDt: LocalDate? = null,

    @Column(name = "job_id")
    val jobId: Long? = null,

    @Column(name = "job_detail_id")
    val jobDetailId: Long? = null

) : BaseEntity()
