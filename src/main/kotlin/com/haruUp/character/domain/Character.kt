package com.haruUp.character.domain

import com.haruUp.character.domain.dto.CharacterDto
import com.haruUp.global.common.BaseEntity
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import lombok.NoArgsConstructor

@Entity
@NoArgsConstructor
class Character (

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    var characterImgRef: Long? = null,

    var name: String? = null,

    var description: String? = null,


) : BaseEntity(){



    fun toDto() : CharacterDto =
        CharacterDto(
           id = this.id,
            characterImgRef = this.characterImgRef,
            name = this.name,
            description = this.description
        )
}