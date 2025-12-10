package com.haruUp.interest.repository

import com.haruUp.interest.entity.CreatedSourceType
import com.haruUp.interest.entity.Interest
import com.haruUp.interest.model.InterestLevel
import com.haruUp.interest.model.InterestNode
import com.haruUp.interest.model.InterestPath
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * InterestRepository의 실제 JPA 구현체
 *
 * 기존 Interest Entity와 연결하여 실제 DB 사용
 */
@Repository
@Primary
class InterestJpaRepositoryImpl(
    private val jpaRepository: InterestEntityJpaRepository
) : InterestRepository {

    override fun save(interest: InterestNode): InterestNode {
        val entity = interest.toEntity()
        val saved = jpaRepository.save(entity)
        return saved.toInterestNode()
    }

    override fun findById(id: String): InterestNode? {
        val longId = id.toLongOrNull() ?: return null
        return jpaRepository.findById(longId)
            .map { it.toInterestNode() }
            .orElse(null)
    }

    override fun findByNameAndLevel(name: String, level: InterestLevel): InterestNode? {
        val levelStr = level.name
        return jpaRepository.findByLevelAndDeletedFalse(levelStr)
            .firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?.toInterestNode()
    }

    override fun findByPath(path: InterestPath): InterestNode? {
        val depth = path.depth()
        val levelStr = when (depth) {
            1 -> "MAIN"
            2 -> "MIDDLE"
            3 -> "SUB"
            else -> return null
        }

        val allAtLevel = jpaRepository.findByLevelAndDeletedFalse(levelStr)

        return when (depth) {
            1 -> allAtLevel.firstOrNull {
                it.name.equals(path.mainCategory, ignoreCase = true)
            }
            2 -> {
                // 부모(대분류) 찾기
                val parent = jpaRepository.findByLevelAndDeletedFalse("MAIN")
                    .firstOrNull { it.name.equals(path.mainCategory, ignoreCase = true) }
                    ?: return null

                // 중분류 찾기
                allAtLevel.firstOrNull {
                    it.parentId == parent.id &&
                    it.name.equals(path.middleCategory, ignoreCase = true)
                }
            }
            3 -> {
                // 대분류 찾기
                val mainCat = jpaRepository.findByLevelAndDeletedFalse("MAIN")
                    .firstOrNull { it.name.equals(path.mainCategory, ignoreCase = true) }
                    ?: return null

                // 중분류 찾기
                val middleCat = jpaRepository.findByLevelAndDeletedFalse("MIDDLE")
                    .firstOrNull {
                        it.parentId == mainCat.id &&
                        it.name.equals(path.middleCategory, ignoreCase = true)
                    }
                    ?: return null

                // 소분류 찾기
                allAtLevel.firstOrNull {
                    it.parentId == middleCat.id &&
                    it.name.equals(path.subCategory, ignoreCase = true)
                }
            }
            else -> null
        }?.toInterestNode()
    }

    override fun findByLevel(level: InterestLevel): List<InterestNode> {
        val levelStr = level.name
        return jpaRepository.findByLevelAndDeletedFalse(levelStr)
            .map { it.toInterestNode() }
    }

    override fun findPopularByLevel(level: InterestLevel, limit: Int): List<InterestNode> {
        val levelStr = level.name
        return jpaRepository.findByLevelAndDeletedFalse(levelStr)
            .sortedByDescending { it.usageCount }
            .take(limit)
            .map { it.toInterestNode() }
    }

    override fun findByParentId(parentId: String): List<InterestNode> {
        val longId = parentId.toLongOrNull() ?: return emptyList()
        return jpaRepository.findByParentIdAndDeletedFalse(longId)
            .map { it.toInterestNode() }
    }

    /**
     * Interest Entity → InterestNode Model 변환
     */
    private fun Interest.toInterestNode(): InterestNode {
        return InterestNode(
            id = this.id.toString(),
            name = this.name,
            level = levelStringToEnum(this.level),
            parentId = this.parentId?.toString(),
            isEmbedded = false, // 임베딩 여부는 별도 관리
            isUserGenerated = this.createdSource == CreatedSourceType.USER,
            usageCount = this.usageCount,
            createdBy = null, // 기존 Entity에 없음
            createdAt = this.createdAt?.atZone(ZoneId.systemDefault())?.toLocalDateTime()
                ?: LocalDateTime.now(),
            embeddedAt = null
        ).also { node ->
            // 부모 이름 조회 (필요시)
            if (this.parentId != null) {
                node.parentName = jpaRepository.findById(this.parentId!!)
                    .map { it.name }
                    .orElse(null)
            }
        }
    }

    /**
     * InterestNode Model → Interest Entity 변환
     */
    private fun InterestNode.toEntity(): Interest {
        return Interest(
            id = this.id.toLongOrNull(),
            parentId = this.parentId?.toLongOrNull(),
            level = this.level.name,
            name = this.name,
            createdSource = if (this.isUserGenerated) CreatedSourceType.USER else CreatedSourceType.SYSTEM,
            usageCount = this.usageCount
        )
    }

    /**
     * String → InterestLevel Enum 변환
     */
    private fun levelStringToEnum(level: String): InterestLevel {
        return when (level.uppercase()) {
            "MAIN" -> InterestLevel.MAIN
            "MIDDLE" -> InterestLevel.MIDDLE
            "SUB" -> InterestLevel.SUB
            else -> throw IllegalArgumentException("Invalid level: $level")
        }
    }
}
