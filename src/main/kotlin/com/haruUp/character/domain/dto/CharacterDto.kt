package com.haruUp.character.domain.dto

import com.haruUp.character.domain.Character

class CharacterDto(
    var id: Long? = null,

    var characterImgRef: Long? = null,

    var name: String? = null,

    var description: String? = null,

) {

    fun toEntity() : Character =
        Character(
            id = this.id,
            name = this.name,
            characterImgRef = this.characterImgRef,
            description =  this.description
        )

}
