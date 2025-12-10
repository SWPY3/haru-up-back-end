package com.haruUp.interest.entity

import com.haruUp.interest.dto.InterestEntityDto
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

    @Column(name = "level", nullable = false, length = 20)
    var level: String = "MAIN",  // "MAIN", "MIDDLE", "SUB"

    @Column(name = "name", nullable = false, length = 255)
    var name: String = "",

    @Column(name = "created_source", nullable = false)
    @Enumerated(EnumType.STRING)
    var createdSource: CreatedSourceType = CreatedSourceType.SYSTEM,

    @Column(name = "usage_count", nullable = false)
    var usageCount: Int = 0

) : BaseEntity() {

    // JPA가 사용할 기본 생성자
    protected constructor() : this(null, null, "MAIN", "", CreatedSourceType.SYSTEM, 0)

    fun toDto(): InterestEntityDto = InterestEntityDto(
        id = this.id,
        parentId = this.parentId,
        level = this.level,
        name = this.name,
        createdSource = this.createdSource
    )
}
