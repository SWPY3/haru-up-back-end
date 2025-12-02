package com.haruUp.interest.domain

import com.haruUp.global.common.BaseEntity
import jakarta.persistence.*

@Entity
@Table(name = "interest")
class Interest(

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null,

    @Column(name = "parent_id", nullable = true)
    var parentId: Long? = null,

    @Column(nullable = false)
    var depth: Int = 0,

    @Column(name = "interest_name", nullable = false, length = 255)
    var interestName: String = "",

    @Column(name = "normalized_key", nullable = true, length = 255)
    var normalizedKey: String? = null,

    @Column(name = "created_source", nullable = false)
    @Enumerated(EnumType.STRING)
    var createdSource: CreatedSourceType = CreatedSourceType.SYSTEM

) : BaseEntity() {

    // JPA가 사용할 기본 생성자
    protected constructor() : this(null, null, 0, "", null, CreatedSourceType.SYSTEM)

    fun toDto(): InterestDto = InterestDto(
        id = this.id,
        parentId = this.parentId,
        depth = this.depth,
        interestName = this.interestName,
        normalizedKey = this.normalizedKey,
        createdSource = this.createdSource
    )
}
