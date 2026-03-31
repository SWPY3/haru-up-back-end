package com.haruUp.missionembedding.service

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.haruUp.interest.dto.InterestPath
import com.haruUp.mission.domain.MissionExpCalculator
import com.haruUp.missionembedding.dto.MissionDto
import com.haruUp.global.clova.ClovaApiClient
import com.haruUp.global.clova.ImprovedMissionRecommendationPrompt
import com.haruUp.global.clova.MissionMemberProfile
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 미션 추천 서비스
 *
 * LLM 기반 미션 추천
 * 1. Clova API로 미션 생성
 * 2. 난이도 검증 (중복/누락 체크)
 * 3. DTO 반환 (DB 저장은 호출자에서 처리)
 */
@Service
class MissionRecommendationService(
    private val clovaApiClient: ClovaApiClient
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val objectMapper = jacksonObjectMapper()

    companion object {
        private const val MAX_RETRIES = 3
        private val broadProjectPattern = Regex(
            "(MVP|사용자\\s*테스트|전체\\s*프로젝트|최종\\s*(구현|완성)|CI/?CD|파이프라인|배포|런칭|출시|아키텍처|실시간\\s*DB|서비스\\s*오픈|프로토타입\\s*완성|앱\\s*완성|기능\\s*완성|빌드\\s*업로드|가입\\s*후\\s*기본\\s*앱\\s*배포|대규모\\s*리팩토링)",
            RegexOption.IGNORE_CASE
        )
        private val shortBudgetBroadPattern = Regex(
            "(SWOT\\s*분석|경쟁사\\s*\\d+\\s*곳\\s*(분석|비교)|장단점\\s*표\\s*작성|페르소나\\s*\\d+\\s*개\\s*상세\\s*프로필\\s*작성|핵심\\s*기능\\s*우선순위\\s*매기기|사용자\\s*시나리오\\s*([2-9]|10)\\s*개|와이어프레임\\s*([2-9]|10)\\s*개|키워드\\s*([5-9]|10)\\s*개\\s*(추출|정의)|문제점\\s*([3-9]|10)\\s*가지)",
            RegexOption.IGNORE_CASE
        )
        private val planningOverreachPattern = Regex(
            "(AS[-\\s]?IS\\s*분석|A/B\\s*테스트|설문조사\\s*질문지|경쟁사\\s*\\d+\\s*곳|동종\\s*업계\\s*\\d+\\s*개사|리뷰\\s*10개\\s*읽고|트렌드\\s*리서치|뉴스\\s*3개\\s*요약|기획\\s*관련\\s*서적|서적\\s*1챕터|인기\\s*앱\\s*1개\\s*선정해.*분석|시장\\s*트렌드|가상\\s*프로젝트)",
            RegexOption.IGNORE_CASE
        )
        private val plannerPortfolioOfftrackPattern = Regex(
            "(경쟁\\s*서비스|인기\\s*앱|채용\\s*공고\\s*\\d+\\s*개|기술적\\s*실현\\s*가능성|시장\\s*트렌드|가상\\s*서비스)",
            RegexOption.IGNORE_CASE
        )
        private val educatorBroadPattern = Regex(
            "(시범\\s*운영|현장\\s*테스트|테스트\\s*계획|온라인\\s*학습\\s*모듈|학습\\s*모듈\\s*\\d+\\s*개\\s*구현|수업\\s*자료\\s*패키지|콘텐츠\\s*패키지|패키지\\s*시범)",
            RegexOption.IGNORE_CASE
        )
        private val designerOfftrackPattern = Regex(
            "(타겟\\s*회사|서비스\\s*분석|동료에게\\s*재검토\\s*요청|검토\\s*요청|작업\\s*리스트\\s*작성)",
            RegexOption.IGNORE_CASE
        )
        private val officeOverreachPattern = Regex(
            "(외부\\s*데이터베이스|실시간\\s*업데이트|통합\\s*대시보드|대시보드\\s*프로토타입|여러\\s*워크북|PDF\\s*형식|보고서\\s*템플릿\\s*개선안\\s*\\d+\\s*가지)",
            RegexOption.IGNORE_CASE
        )
        private val serviceOperationOfftrackPattern = Regex(
            "(설문|모바일\\s*최적화|FAQ\\s*페이지|시뮬레이션\\s*테스트|만족도)",
            RegexOption.IGNORE_CASE
        )
        private val mobileOverreachPattern = Regex(
            "(동영상|영상\\s*(녹화|촬영|편집)|팀원\\s*\\d+명|친구\\s*\\d+명.*피드백|협업\\s*테스트|Chart\\.js|hover\\s*효과|CSS\\s*애니메이션)",
            RegexOption.IGNORE_CASE
        )
        private val portfolioMismatchPattern = Regex(
            "(신규\\s*서비스\\s*아이디어|서비스\\s*아이디어\\s*기획서|새로운\\s*서비스\\s*기획|To-Do\\s*List|Todo\\s*List|간단한\\s*To-Do|todo\\s*list|LinkedIn|인포그래픽|전문성\\s*강조\\s*게시물|기술\\s*블로그|블로그\\s*(포스팅|게시글)|PPT)",
            RegexOption.IGNORE_CASE
        )
        private val beginnerTaskPattern = Regex(
            "(Hello\\s*World|튜토리얼|기본\\s*개념\\s*학습|간단한\\s*컴포넌트\\s*구현|샘플\\s*앱|예제\\s*앱)",
            RegexOption.IGNORE_CASE
        )
        private val experiencedPortfolioBeginnerPattern = Regex(
            "(Hello\\s*World|/hello|애플리케이션\\s*생성|프로젝트\\s*생성|GET\\s*엔드포인트\\s*구현|CRUD\\s*중\\s*R|기본\\s*GET\\s*엔드포인트|기초\\s*예제)",
            RegexOption.IGNORE_CASE
        )
        private val mediumBudgetBroadPattern = Regex(
            "(3개\\s*이상|스타일\\s*가이드|기능\\s*정의|UI\\s*스케치|와이어프레임\\s*3개|문서\\s*자동생성\\s*\\+|\\+\\s*.*(캡처|작성|구현))",
            RegexOption.IGNORE_CASE
        )
        private val assumedPortfolioSitePattern = Regex(
            "(포트폴리오\\s*(사이트|웹사이트|웹\\s*페이지)|개인\\s*(사이트|웹사이트)|웹\\s*포트폴리오)",
            RegexOption.IGNORE_CASE
        )
        private val assumedPortfolioVideoPattern = Regex(
            "(동작\\s*영상|시연\\s*영상|데모\\s*영상|소개\\s*동영상|포트폴리오용\\s*.*동영상|동영상|영상\\s*(촬영|편집)|촬영\\s*(및)?\\s*편집|촬영.*편집|영상\\s*업로드)",
            RegexOption.IGNORE_CASE
        )
        private val assumedPortfolioDocumentPattern = Regex(
            "(포트폴리오\\s*PDF|PDF\\s*버전|PDF\\s*(초안|작성)|발표\\s*슬라이드|슬라이드\\s*덱|발표\\s*자료|PPT|피피티)",
            RegexOption.IGNORE_CASE
        )
        private val assumedWebsitePattern = Regex(
            "(웹사이트|웹\\s*사이트|웹페이지|웹\\s*페이지|랜딩\\s*페이지)",
            RegexOption.IGNORE_CASE
        )
        private val assumedPlanningToolPattern = Regex(
            "(Figma|Jira|JetBrains\\s*Sketch|피그마|지라)\\s*(스크린샷|활용)?",
            RegexOption.IGNORE_CASE
        )
        private val assumedFrameworkPattern = Regex(
            "(React(\\.js)?|React\\s*Native|Flutter|Vue(\\.js)?|Next\\.js|Angular|SwiftUI|Jetpack\\s*Compose|Express(\\.js)?)",
            RegexOption.IGNORE_CASE
        )
        private val frontendMobilePattern = Regex(
            "(React(\\.js)?|React\\s*Native|Flutter|Vue(\\.js)?|Next\\.js|Angular|SwiftUI|Swift|Android|iOS|Jetpack\\s*Compose|HTML|CSS|Chart\\.js)",
            RegexOption.IGNORE_CASE
        )
        private val webFrontendPattern = Regex(
            "(React(\\.js)?(?!\\s*Native)|Vue(\\.js)?|Next\\.js|Angular|Express(\\.js)?|HTML|CSS|Chart\\.js)",
            RegexOption.IGNORE_CASE
        )
        private val backendPattern = Regex(
            "(Spring|Java|Kotlin|Node|NestJS|Express(\\.js)?)",
            RegexOption.IGNORE_CASE
        )
        private val developerTaskPattern = Regex(
            "(GitHub|README|API|Spring|React|Vue|Node|NestJS|JPA|SQL|JWT|Swagger|Redis|Elasticsearch|Express(\\.js)?)",
            RegexOption.IGNORE_CASE
        )
        private val officeKeywordPattern = Regex(
            "(엑셀|함수|피벗|시트|보고서|데이터|매크로|업무|자동화|정리|효율|템플릿|집계)",
            RegexOption.IGNORE_CASE
        )
        private val selfServiceKeywordPattern = Regex(
            "(고객|후기|문의|응대|예약|서비스|체크리스트|운영|프로세스|재방문|매장|가게|리뷰)",
            RegexOption.IGNORE_CASE
        )
        private val educatorKeywordPattern = Regex(
            "(수업|학생|학습자료|커리큘럼|과제|피드백|강의|퀴즈|평가|학습\\s*목표|활동지)",
            RegexOption.IGNORE_CASE
        )
        private val designerKeywordPattern = Regex(
            "(화면|와이어프레임|ux|ui|사용자\\s*흐름|시안|프로토타입|케이스|피드백|디자인\\s*시스템|컴포넌트|리디자인|디자인)",
            RegexOption.IGNORE_CASE
        )
        private val researchKeywordPattern = Regex(
            "(논문|실험|데이터|분석|가설|연구|문헌|그래프|보고서|결과|요약|해석)",
            RegexOption.IGNORE_CASE
        )
        private val plannerKeywordPattern = Regex(
            "(사용자|문제|요구사항|기획서|기획|흐름|화면|와이어프레임|페르소나|시나리오|핵심\\s*기능|아이디어|서비스|포트폴리오|케이스|프로젝트|자기소개서|이력서)",
            RegexOption.IGNORE_CASE
        )
        private val bundledActionPattern = Regex(
            "(\\+|→|->|=>|하고|한\\s*뒤|후에?|및).*(작성|정리|구현|설계|제작|도출|기록|분석|요약|적용|연결|비교|수정|보강|추가|촬영|편집)",
            RegexOption.IGNORE_CASE
        )
        private val parentheticalChecklistPattern = Regex(
            "\\([^)]*(,|/|·)[^)]*(,|/|·)[^)]*\\)"
        )
        private val EXPERIENCE_YEAR_PATTERNS = listOf(
            Regex("(\\d+)\\s*년차"),
            Regex("(\\d+)\\s*년\\s*동안")
        )
        private val TECH_STACK_KEYWORDS = listOf(
            "react",
            "express",
            "flutter",
            "react native",
            "vue",
            "next.js",
            "angular",
            "spring",
            "kotlin",
            "java",
            "node",
            "nestjs",
            "swift",
            "android",
            "ios"
        )
        private val BEGINNER_TARGET_PATTERNS = listOf(
            Regex("처음"),
            Regex("안 해봤"),
            Regex("안해봤"),
            Regex("해본 ?적(이)? 없"),
            Regex("입문"),
            Regex("초보")
        )
        private val BACKEND_TECH_KEYWORDS = listOf(
            "spring",
            "java",
            "kotlin",
            "node",
            "nestjs",
            "express"
        )
        private val WEB_FRONTEND_TECH_KEYWORDS = listOf(
            "react",
            "vue",
            "next.js",
            "angular"
        )
        private val MOBILE_TECH_KEYWORDS = listOf(
            "react native",
            "flutter",
            "swift",
            "android",
            "ios"
        )
        private val PORTFOLIO_SITE_FORMAT_KEYWORDS = listOf(
            "포트폴리오 사이트",
            "포트폴리오 웹사이트",
            "포트폴리오 페이지",
            "웹 포트폴리오",
            "개인 사이트",
            "개인 웹사이트"
        )
        private val PORTFOLIO_VIDEO_FORMAT_KEYWORDS = listOf(
            "동작 영상",
            "시연 영상",
            "데모 영상",
            "영상 포트폴리오",
            "영상",
            "동영상",
            "비디오"
        )
        private val PORTFOLIO_DOCUMENT_FORMAT_KEYWORDS = listOf(
            "포트폴리오 pdf",
            "pdf 포트폴리오",
            "pdf 버전",
            "슬라이드",
            "발표 자료"
        )
        private val PLANNING_TOOL_KEYWORDS = listOf(
            "figma",
            "jira",
            "피그마",
            "지라"
        )
        private val CAREER_DOCUMENT_KEYWORDS = setOf(
            "readme",
            "github",
            "문서",
            "문서화",
            "정리"
        )
        private val CAREER_SUPPORT_KEYWORDS = setOf(
            "이력서",
            "면접",
            "자기소개서",
            "자기소개",
            "jd",
            "포트폴리오",
            "bullet",
            "지원"
        )
        private val CAREER_PROJECT_KEYWORDS = setOf(
            "프로젝트",
            "성과",
            "kpi",
            "회고",
            "문제 해결",
            "경험",
            "사례"
        )
        private val BACKEND_PROJECT_DOC_KEYWORDS = setOf(
            "포트폴리오",
            "프로젝트",
            "readme",
            "정리",
            "문서",
            "문서화"
        )
        private val BACKEND_REQUIRED_KEYWORDS = setOf(
            "spring",
            "java",
            "kotlin",
            "node",
            "nestjs",
            "express",
            "api",
            "db",
            "sql",
            "테스트",
            "엔드포인트",
            "배치",
            "쿼리",
            "장애"
        )
        private val APP_MOBILE_KEYWORDS = setOf(
            "앱",
            "화면",
            "네비게이션",
            "상태",
            "로그인",
            "입력",
            "버튼",
            "리스트"
        )
        private val APP_OUTPUT_KEYWORDS = setOf(
            "프로토타입",
            "기능",
            "시나리오",
            "테스트",
            "정리",
            "흐름",
            "검증"
        )
        private val PLANNER_CASE_KEYWORDS = setOf(
            "문제",
            "요구사항",
            "프로젝트",
            "케이스",
            "기획안",
            "핵심 기능"
        )
        private val PLANNER_FLOW_KEYWORDS = setOf(
            "사용자",
            "흐름",
            "화면",
            "와이어프레임",
            "페르소나",
            "시나리오",
            "기획서"
        )
        private val DESIGN_VISUAL_KEYWORDS = setOf(
            "화면",
            "와이어프레임",
            "ux",
            "ui",
            "사용자 흐름",
            "시안",
            "리디자인",
            "컴포넌트"
        )
        private val DESIGN_CASE_KEYWORDS = setOf(
            "포트폴리오",
            "케이스",
            "피드백",
            "문제",
            "근거",
            "회고"
        )
        private val OFFICE_TOOL_KEYWORDS = setOf(
            "엑셀",
            "함수",
            "피벗",
            "시트",
            "보고서",
            "데이터",
            "매크로"
        )
        private val OFFICE_EFFECT_KEYWORDS = setOf(
            "업무",
            "자동화",
            "시간",
            "정리",
            "효율",
            "템플릿",
            "반복"
        )
        private val SELF_SERVICE_CUSTOMER_KEYWORDS = setOf(
            "고객",
            "후기",
            "문의",
            "응대",
            "서비스",
            "체크리스트",
            "프로세스"
        )
        private val SELF_SERVICE_OPERATION_KEYWORDS = setOf(
            "매출",
            "운영",
            "정리",
            "개선",
            "반복",
            "루틴",
            "기준"
        )
        private val EDUCATOR_CORE_KEYWORDS = setOf(
            "수업",
            "학습자료",
            "커리큘럼",
            "과제",
            "피드백",
            "강의",
            "학생"
        )
        private val EDUCATOR_OUTCOME_KEYWORDS = setOf(
            "퀴즈",
            "평가",
            "정리",
            "콘텐츠",
            "학습 목표",
            "활동지",
            "루브릭"
        )
        private val RESEARCH_CORE_KEYWORDS = setOf(
            "논문",
            "실험",
            "데이터",
            "분석",
            "가설",
            "연구",
            "문헌"
        )
        private val RESEARCH_OUTPUT_KEYWORDS = setOf(
            "정리",
            "그래프",
            "요약",
            "보고서",
            "결과",
            "해석"
        )
        private val artifactFamilyKeywords = listOf(
            "career_project" to CAREER_PROJECT_KEYWORDS,
            "career_docs" to CAREER_DOCUMENT_KEYWORDS,
            "career_support" to CAREER_SUPPORT_KEYWORDS,
            "backend" to setOf("spring", "api", "jpa", "db", "sql", "테스트", "엔드포인트", "jwt", "swagger", "배치", "쿼리", "장애"),
            "backend_docs" to BACKEND_PROJECT_DOC_KEYWORDS,
            "mobile" to setOf("앱", "화면", "네비게이션", "상태", "로그인", "프로토타입", "기능", "시나리오"),
            "planning_case" to PLANNER_CASE_KEYWORDS,
            "planning_flow" to PLANNER_FLOW_KEYWORDS,
            "design_visual" to DESIGN_VISUAL_KEYWORDS,
            "design_case" to DESIGN_CASE_KEYWORDS,
            "office" to OFFICE_TOOL_KEYWORDS,
            "office_effect" to OFFICE_EFFECT_KEYWORDS,
            "service_ops" to SELF_SERVICE_CUSTOMER_KEYWORDS,
            "service_ops_outcome" to SELF_SERVICE_OPERATION_KEYWORDS,
            "education" to EDUCATOR_CORE_KEYWORDS,
            "education_outcome" to EDUCATOR_OUTCOME_KEYWORDS,
            "research" to RESEARCH_CORE_KEYWORDS,
            "research_output" to RESEARCH_OUTPUT_KEYWORDS
        )
    }

    /**
     * 오늘의 미션 추천
     *
     * @param directFullPath 관심사 경로 (예: ["체력관리 및 운동", "헬스", "근력 키우기"])
     * @param memberProfile 멤버 프로필
     * @param difficulties 추천할 난이도 목록 (기본: 1~5)
     * @param excludeContents 제외할 미션 내용 목록
     * @return 미션 목록
     */
    suspend fun recommendTodayMissions(
        directFullPath: List<String>,
        memberProfile: MissionMemberProfile,
        difficulties: List<Int>? = null,
        excludeContents: List<String> = emptyList(),
        additionalContext: String? = null
    ): List<MissionDto> {
        val targetDifficulties = difficulties ?: listOf(1, 2, 3, 4, 5)
        val pathString = directFullPath.joinToString(" > ")
        logger.info("미션 추천 시작 - 관심사: $pathString, 난이도: $targetDifficulties")

        return try {
            // 1. LLM으로 미션 생성 (재시도 포함)
            val missions = generateMissionsWithRetry(
                directFullPath = directFullPath,
                memberProfile = memberProfile,
                difficulties = targetDifficulties,
                excludeContents = excludeContents,
                additionalContext = additionalContext
            )

            // 2. DTO 변환 (DB 저장은 호출자에서 처리)
            missions.map { mission ->
                MissionDto(
                    member_mission_id = null,
                    content = mission.content,
                    directFullPath = mission.directFullPath,
                    difficulty = mission.difficulty,
                    expEarned = MissionExpCalculator.calculateByDifficulty(mission.difficulty),
                    createdType = "AI"
                )
            }.also {
                logger.info("미션 추천 완료: ${it.size}개")
            }
        } catch (e: Exception) {
            logger.error("미션 추천 실패: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * LLM으로 미션 생성 (재시도 로직 포함)
     */
    private fun generateMissionsWithRetry(
        directFullPath: List<String>,
        memberProfile: MissionMemberProfile,
        difficulties: List<Int>,
        excludeContents: List<String>,
        additionalContext: String?
    ): List<Mission> {
        val interestPath = InterestPath(
            mainCategory = directFullPath.getOrNull(0) ?: "",
            middleCategory = directFullPath.getOrNull(1),
            subCategory = directFullPath.getOrNull(2)
        )
        val timeBudgetMinutes = extractTimeBudgetMinutes(additionalContext)
        val recommendationSignals = extractRecommendationSignals(
            additionalContext = additionalContext,
            interestPath = interestPath,
            memberProfile = memberProfile
        )
        val deterministicMissions = buildDeterministicMissions(
            directFullPath = directFullPath,
            recommendationSignals = recommendationSignals,
            additionalContext = additionalContext
        )
        if (deterministicMissions.isNotEmpty()) {
            logger.info("단계형 deterministic 미션 세트를 반환합니다.")
            return deterministicMissions
        }
        var bestAttemptMissions: List<Mission> = emptyList()
        var bestAttemptInvalidCount = Int.MAX_VALUE
        var retryFeedback: String? = null

        repeat(MAX_RETRIES) { attempt ->
            try {
                val missions = callLLMForMissions(
                    interestPath = interestPath,
                    memberProfile = memberProfile,
                    difficulties = difficulties,
                    excludeContents = excludeContents,
                    additionalContext = additionalContext,
                    timeBudgetMinutes = timeBudgetMinutes,
                    recommendationSignals = recommendationSignals,
                    retryFeedback = retryFeedback,
                    attempt = attempt + 1
                )

                // 난이도 및 하루 미션 규칙 검증
                val returnedDifficulties = missions.mapNotNull { it.difficulty }
                val invalidContents = findInvalidMissionContents(missions, timeBudgetMinutes, recommendationSignals)
                val invalidSetIssues = findInvalidMissionSetIssues(missions, recommendationSignals)
                val invalidIssueCount = invalidContents.size + invalidSetIssues.size
                if (isValidDifficulties(returnedDifficulties, difficulties) && invalidIssueCount < bestAttemptInvalidCount) {
                    bestAttemptMissions = missions
                    bestAttemptInvalidCount = invalidIssueCount
                }

                if (isValidDifficulties(returnedDifficulties, difficulties) &&
                    invalidContents.isEmpty() &&
                    invalidSetIssues.isEmpty()
                ) {
                    logger.info("미션 생성 성공 (시도 ${attempt + 1}): 난이도 $returnedDifficulties")
                    return missions.map { it.copy(directFullPath = directFullPath) }
                }

                val duplicates = returnedDifficulties.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
                val missing = difficulties - returnedDifficulties.toSet()
                logger.warn(
                    "미션 검증 실패 (시도 ${attempt + 1}): 중복=$duplicates, 누락=$missing, 범위초과=${invalidContents.ifEmpty { emptyList<String>() }}, 세트문제=${invalidSetIssues.ifEmpty { emptyList<String>() }}"
                )
                retryFeedback = buildRetryFeedback(invalidContents, invalidSetIssues)

            } catch (e: Exception) {
                logger.warn("미션 생성 실패 (시도 ${attempt + 1}): ${e.message}")
                retryFeedback = "- JSON 형식이 깨지지 않게 다시 작성하세요.\n- 각 미션은 한 줄짜리 단일 작업만 제안하세요."
            }
        }

        val fallbackMissions = buildFallbackMissions(
            directFullPath = directFullPath,
            recommendationSignals = recommendationSignals,
            additionalContext = additionalContext
        )
        val fallbackInvalidContents = findInvalidMissionContents(
            fallbackMissions,
            timeBudgetMinutes,
            recommendationSignals
        )
        val fallbackInvalidSetIssues = findInvalidMissionSetIssues(fallbackMissions, recommendationSignals)
        if (
            fallbackMissions.isNotEmpty() &&
            isValidDifficulties(fallbackMissions.mapNotNull { it.difficulty }, difficulties) &&
            fallbackInvalidContents.isEmpty() &&
            fallbackInvalidSetIssues.isEmpty()
        ) {
            logger.warn("LLM 후보 대신 공통 fallback 미션 세트를 반환합니다.")
            return fallbackMissions.map { it.copy(directFullPath = directFullPath) }
        }

        if (bestAttemptMissions.isNotEmpty()) {
            logger.warn("완전한 하루 미션 세트를 만들지 못해 가장 나은 후보를 반환합니다. invalidCount=$bestAttemptInvalidCount")
            return bestAttemptMissions.map { it.copy(directFullPath = directFullPath) }
        }

        throw IllegalStateException("$MAX_RETRIES 회 시도 후에도 미션 생성 실패")
    }

    private fun isValidDifficulties(returned: List<Int>, expected: List<Int>): Boolean {
        return returned.toSet() == expected.toSet() && returned.size == expected.size
    }

    /**
     * LLM API 호출
     */
    private fun callLLMForMissions(
        interestPath: InterestPath,
        memberProfile: MissionMemberProfile,
        difficulties: List<Int>,
        excludeContents: List<String>,
        additionalContext: String?,
        timeBudgetMinutes: Int?,
        recommendationSignals: RecommendationSignals,
        retryFeedback: String?,
        attempt: Int
    ): List<Mission> {
        val userMessage = buildPrompt(
            interestPath = interestPath,
            memberProfile = memberProfile,
            difficulties = difficulties,
            excludeContents = excludeContents,
            additionalContext = additionalContext,
            timeBudgetMinutes = timeBudgetMinutes,
            recommendationSignals = recommendationSignals,
            retryFeedback = retryFeedback,
            attempt = attempt
        )

        logger.debug("LLM 요청 (시도 $attempt)")

        val response = clovaApiClient.generateText(
            userMessage = userMessage,
            systemMessage = ImprovedMissionRecommendationPrompt.SYSTEM_PROMPT,
            temperature = 0.3  // 안정적인 출력을 위해 낮은 temperature 사용
        )

        return parseMissions(response, difficulties)
    }

    /**
     * 프롬프트 생성
     */
    private fun buildPrompt(
        interestPath: InterestPath,
        memberProfile: MissionMemberProfile,
        difficulties: List<Int>,
        excludeContents: List<String>,
        additionalContext: String?,
        timeBudgetMinutes: Int?,
        recommendationSignals: RecommendationSignals,
        retryFeedback: String?,
        attempt: Int
    ): String {
        val basePrompt = ImprovedMissionRecommendationPrompt.createUserMessageForAllInterests(
            interests = listOf(interestPath),
            missionMemberProfile = memberProfile
        )

        val excludeSection = buildExcludeSection(excludeContents)
        val additionalContextSection = buildAdditionalContextSection(additionalContext)
        val timeBudgetSection = buildTimeBudgetSection(timeBudgetMinutes)
        val recommendationModeSection = buildRecommendationModeSection(recommendationSignals)
        val retryWarning = if (attempt > 1) {
            """

⚠️ 이전 시도에서 규칙 위반이 있었습니다.
- 각 난이도는 정확히 1번씩만 써야 합니다.
- "MVP 완성", "사용자 테스트 진행", "실시간 DB 기능 구현" 같은 큰 범위 표현은 금지입니다.
- "경쟁사 3곳 분석", "페르소나 2개 상세 작성", "화면 2개 와이어프레임", "SWOT 분석" 같은 과한 하루 미션도 금지입니다.
- 현재 목표와 무관한 신규 서비스 아이디어, To-Do List 같은 튜토리얼성 과제도 금지입니다.
- 반드시 오늘 끝낼 수 있는 1개 작업으로 다시 작성하세요.
            """.trimIndent() + "\n"
        } else ""
        val difficultyList = difficulties.joinToString(", ")
        val jsonExample = difficulties.joinToString(",\n    ") {
            """{"content": "미션내용", "relatedInterest": ["대분류", "중분류", "소분류"], "difficulty": $it}"""
        }

        return """
$basePrompt

===== 생성 요청 =====
난이도 $difficultyList 각각 1개씩, 총 ${difficulties.size}개의 미션을 생성하세요.
$retryWarning${buildRetryFeedbackSection(retryFeedback)}$excludeSection$additionalContextSection$timeBudgetSection$recommendationModeSection
===== 난이도 기준 (모든 난이도는 하루 안에 완료 가능해야 함) =====
- 난이도 1: 오늘 바로 시작할 수 있는 아주 작은 행동 1개
- 난이도 2: 짧은 실습 또는 예제 수행 1개
- 난이도 3: 집중해서 끝낼 수 있는 중간 크기 작업 1개
- 난이도 4: 다소 도전적이지만 하루 안에 닫히는 단일 작업 1개
- 난이도 5: 그날 할 수 있는 가장 어려운 작업이지만, 여전히 하루 투자 가능 시간 안에서 끝나는 작업 1개

===== 범위 제한 (매우 중요) =====
- 각 미션은 "오늘 할 1개 행동"이어야 합니다.
- 프로젝트 전체를 끝내는 표현을 절대 쓰지 마세요.
- 난이도 5도 기능 묶음이나 프로젝트 단계 전체가 아니라, 하루 안에 검증 가능한 단일 과업이어야 합니다.
- "구현", "연동", "테스트", "설계", "구성" 같은 표현을 쓸 때는 반드시 범위를 좁히세요.
- 숫자, 개수, 시간, 화면 수, 사람 수 등으로 범위를 드러내세요.
- 여러 산출물을 한 번에 요구하지 마세요.
- 분석 후 작성, 조사 후 정리처럼 큰 2단계 묶음 작업은 피하세요.
- 문장 안에서 '후', '및', '+', '&', '그리고'를 사용해 여러 행동을 묶지 마세요.

===== 좋은 미션 (필수) =====
✅ 구체적이고 측정 가능 (횟수, 시간, 개수 등 수치 포함)
✅ 하루 안에 완료 가능
예: "영어 단어 20개 암기", "스쿼트 3세트×15회", "책 50페이지 읽기"
예: "지인 1명에게 화면 3장 보여주고 피드백 3개 메모"
예: "Firebase 인증 예제 1개 따라 하고 로그인 성공 화면 캡처"

===== 나쁜 미션 (금지) =====
❌ 모호함: "운동하기", "공부하기"
❌ 장기 목표: "한 달간 다이어트"
❌ 일회성: "헬스장 등록하기"
❌ 측정 불가: "건강해지기"
❌ 범위 과다: "MVP 핵심 기능 검증용 사용자 테스트 진행"
❌ 범위 과다: "전체 프로젝트 설계서 작성 및 CI/CD 파이프라인 구성"
❌ 범위 과다: "Firebase 연동하여 실시간 DB 저장/조회 기능 구현"

===== 응답 형식 (JSON만 출력) =====
```json
{
  "missions": [
    $jsonExample
  ]
}
```
        """.trim()
    }

    /**
     * 제외 미션 섹션 생성
     */
    private fun buildExcludeSection(excludeContents: List<String>): String {
        if (excludeContents.isEmpty()) return ""

        val missionList = excludeContents.joinToString("\n") { "- $it" }

        return """

###############################################
# 중요: 아래 미션들은 절대 추천하지 마세요 #
###############################################

<EXCLUDED_MISSIONS>
$missionList
</EXCLUDED_MISSIONS>

위 목록과 동일하거나 유사한 미션은 제외하세요.
"""
    }

    private fun buildRetryFeedbackSection(retryFeedback: String?): String {
        if (retryFeedback.isNullOrBlank()) return ""

        return """

===== 직전 응답 보정 지시 =====
$retryFeedback

"""
    }

    private fun buildAdditionalContextSection(additionalContext: String?): String {
        if (additionalContext.isNullOrBlank()) return ""

        return """

===== 추가 사용자 문맥 =====
$additionalContext

위 문맥을 반드시 반영해서 미션을 생성하세요.
- 현재 실력에 맞게 현실적인 수준으로 제안하세요.
- 하루 투자 가능 시간을 크게 넘지 않도록 하세요.
- 목표 기간 안에 도달할 수 있는 단계형 미션으로 제안하세요.
"""
    }

    private fun buildTimeBudgetSection(timeBudgetMinutes: Int?): String {
        if (timeBudgetMinutes == null) return ""

        val section = mutableListOf(
            "",
            "===== 시간 예산 엄수 =====",
            "- 사용자의 하루 미션 시간 상한은 약 ${timeBudgetMinutes}분입니다.",
            "- 난이도 5도 이 시간 안에서 끝나야 합니다.",
            "- 한 번에 산출물 1개만 요구하세요."
        )

        if (timeBudgetMinutes <= 60) {
            section += listOf(
                "- 60분 이하 사용자에게는 여러 곳 비교, SWOT 분석, 페르소나 여러 개 상세 작성, 화면 여러 개 와이어프레임을 제안하지 마세요.",
                "- 난이도 1은 5~10분, 난이도 2는 10~20분, 난이도 3은 20~35분, 난이도 4는 35~50분, 난이도 5는 50~60분 안에 끝나는 작업으로 제안하세요.",
                "- 예: \"경쟁사 1곳 핵심 기능 3개 메모\", \"화면 1개 손그림 와이어프레임\", \"페르소나 1명 pain point 3개 적기\""
            )
        } else if (timeBudgetMinutes <= 90) {
            section += listOf(
                "- 90분 이하 사용자에게는 프로젝트 단계 전체나 문서 묶음 작업을 제안하지 마세요.",
                "- 화면, 문서, 시나리오는 1개씩만 제안하는 편이 좋습니다."
            )
        }

        return section.joinToString("\n") + "\n"
    }

    private fun buildRecommendationModeSection(signals: RecommendationSignals): String {
        val lines = mutableListOf<String>()

        if (signals.isJobSwitchPortfolioMode) {
            lines += listOf(
                "",
                "===== 이직/포트폴리오 모드 =====",
                "- 미션은 반드시 이직 준비 또는 포트폴리오 강화에 직접 기여해야 합니다.",
                "- 우선순위: 대표 프로젝트 정리, README/문서화, 성과 정리, 문제 해결 회고, 이력서 bullet 보강, GitHub 정리, 지원 회사 맞춤 분석, 면접 답변 준비.",
                "- 세트 전체에 프로젝트/성과 축 미션과 이력서/면접/포트폴리오 축 미션이 반드시 함께 들어가야 합니다.",
                "- 목표와 무관한 신규 서비스 아이디어 발상, 새로운 토이 프로젝트 기획, 일반 튜토리얼 과제는 피하세요.",
                "- LinkedIn 게시물, 인포그래픽 제작, 대규모 리팩토링처럼 포트폴리오 핵심 산출물과 거리가 먼 과제는 피하세요."
            )
            if (!signals.hasExplicitPortfolioSitePreference) {
                lines += "- 사용자가 포트폴리오 사이트나 개인 웹사이트를 직접 언급하지 않았다면 웹사이트 제작이나 사이트 업로드를 임의로 가정하지 마세요."
            }
            if (!signals.hasExplicitPortfolioVideoPreference) {
                lines += "- 사용자가 영상 포트폴리오를 언급하지 않았다면 동작 영상 촬영, 데모 영상 편집 같은 과제를 임의로 추천하지 마세요."
            }
            if (!signals.hasExplicitPortfolioDocumentPreference) {
                lines += "- 사용자가 PDF 포트폴리오나 발표 자료를 직접 언급하지 않았다면 PDF 버전 작성이나 슬라이드 제작을 임의로 가정하지 마세요."
            }
            if (!signals.hasExplicitPlanningToolPreference) {
                lines += "- 사용자가 Figma나 Jira 같은 도구를 직접 언급하지 않았다면 해당 도구 사용을 전제로 한 미션을 임의로 추천하지 마세요."
            }
        }

        if (signals.isPlannerBeginnerMode) {
            lines += listOf(
                "",
                "===== 기획 입문 모드 =====",
                "- 사용자의 문제 정의, 요구사항 정리, 핵심 기능, 사용자 흐름, 화면 구조처럼 기획 기본 산출물에 바로 닿는 미션을 우선하세요.",
                "- 세트 전체에 사용자 문제/요구사항 축과 화면/흐름/페르소나 축이 반드시 함께 들어가야 합니다.",
                "- 경쟁사 여러 곳 분석, AS-IS 분석, 설문조사 설계, A/B 테스트 계획처럼 범위가 큰 과제는 피하세요.",
                "- 페르소나 1명, 사용자 시나리오 1개, 화면 1개, 흐름 1개처럼 작은 단위로 나누세요."
            )
            if (!signals.hasExplicitPlanningToolPreference) {
                lines += "- 사용자가 Figma나 Jira를 직접 언급하지 않았다면 해당 도구를 전제로 한 미션은 피하세요."
            }
        }

        if (signals.isPlannerPortfolioMode) {
            lines += listOf(
                "",
                "===== 기획 포트폴리오 모드 =====",
                "- 문제 정의, 사용자 요구사항, 핵심 흐름, 프로젝트 요약, 포트폴리오 섹션 작성, 자기소개서/이력서 연결에 직접 도움이 되는 미션을 우선하세요.",
                "- 세트 전체에 문제/요구사항/프로젝트 축, 사용자/흐름/화면 축, 포트폴리오/기획서/지원 자료 축이 반드시 함께 들어가야 합니다.",
                "- 포트폴리오와 직접 연결되지 않는 업계 동향 조사, 광범위한 경쟁사 비교, 추상적인 전략 수립만 하는 과제는 피하세요.",
                "- 케이스 스터디 재료가 하나씩 쌓이도록 문제 -> 해결 -> 결과 흐름으로 이어가세요."
            )
            if (!signals.hasExplicitPlanningToolPreference) {
                lines += "- 사용자가 Figma나 Jira를 직접 언급하지 않았다면 해당 도구를 전제로 한 미션은 피하세요."
            }
        }

        if (signals.isOfficeMode) {
            lines += listOf(
                "",
                "===== 사무직 업무 효율화 모드 =====",
                "- 엑셀, 함수, 피벗, 시트 구조, 보고서 정리, 반복 입력 감소, 데이터 정리 같은 실무 과제를 우선하세요.",
                "- 세트 전체에 엑셀/함수/시트 축과 업무 효율/정리/자동화 축이 반드시 함께 들어가야 합니다.",
                "- GitHub, API, Spring, React 같은 개발자식 과제는 절대 추천하지 마세요.",
                "- 실제 업무에서 바로 쓸 수 있는 템플릿, 시트 구조, 함수 1개 적용, 보고서 정리 기준처럼 즉시 적용형으로 제안하세요."
            )
        }

        if (signals.isServiceOperationMode) {
            lines += listOf(
                "",
                "===== 자영업 운영 개선 모드 =====",
                "- 고객 문의 응대, 후기 답변, 예약 안내, 운영 체크리스트, 서비스 프로세스 정리처럼 현장에서 바로 쓰는 미션을 우선하세요.",
                "- 세트 전체에 고객/응대/서비스 축과 운영/개선/정리 축이 반드시 함께 들어가야 합니다.",
                "- 개발 과제, 포트폴리오 과제, 추상적인 사업 전략 수립은 피하세요.",
                "- 바로 꺼내 쓸 문구, 체크리스트, 응대 흐름, 운영 기준처럼 실무 문서를 작게 만드는 방향으로 제안하세요."
            )
        }

        if (signals.isEducatorMode) {
            lines += listOf(
                "",
                "===== 교육자 수업 설계 모드 =====",
                "- 수업 자료, 활동지, 퀴즈, 과제, 피드백 기준, 학습 목표 정리처럼 수업 운영에 직접 연결되는 미션을 우선하세요.",
                "- 세트 전체에 수업/학생/자료 축과 퀴즈/평가/정리 축이 반드시 함께 들어가야 합니다.",
                "- 온라인 모듈 전체 구현, 시범 운영, 현장 테스트 계획, 자료 패키지화처럼 큰 범위 과제는 피하세요.",
                "- 자료 1개, 퀴즈 1세트, 피드백 루브릭 1개, 수업 흐름 1장처럼 하루 단위로 닫히는 산출물을 추천하세요."
            )
        }

        if (signals.isDesignerMode) {
            lines += listOf(
                "",
                "===== 디자이너 포트폴리오 모드 =====",
                "- 화면, 사용자 흐름, 케이스 스터디, 피드백 반영, 디자인 근거 정리처럼 포트폴리오에 바로 들어갈 산출물을 우선하세요.",
                "- 세트 전체에 화면/흐름/시안 축과 케이스/피드백/문제 해결 축이 반드시 함께 들어가야 합니다.",
                "- 개발 구현 과제, 기술 스택 과제, 추상적인 브랜딩 과제는 피하세요.",
                "- 화면 1개, 흐름 1개, 시안 1개, 케이스 문단 1개처럼 설명 가능한 단위로 제안하세요."
            )
        }

        if (signals.isResearchMode) {
            lines += listOf(
                "",
                "===== 연구 정리 모드 =====",
                "- 논문 요약, 문헌 메모, 가설 정리, 데이터 해석, 그래프 설명, 결과 문장 작성처럼 연구 자료에 직접 쓰일 산출물을 우선하세요.",
                "- 세트 전체에 문헌/가설/데이터 축과 요약/보고서/결과 축이 반드시 함께 들어가야 합니다.",
                "- 개발 과제, 포트폴리오 과제, 동영상/웹사이트 제작 과제는 피하세요.",
                "- 논문 1편, 그래프 1개, 가설 1개, 결과 문단 1개처럼 작게 닫히는 미션으로 제안하세요."
            )
        }

        if (signals.yearsOfExperience != null && signals.yearsOfExperience >= 2) {
            lines += listOf(
                "",
                "===== 경력자 모드 =====",
                "- 사용자는 이미 ${signals.yearsOfExperience}년 이상의 경험이 있습니다.",
                "- Hello World, 기본 개념 학습, 간단한 To-Do 구현 같은 입문형 미션은 추천하지 마세요.",
                "- 기존 경험을 정리하거나 개선 포인트를 드러내는 실무형 미션을 우선하세요."
            )
        }

        if (signals.isBackendCareerMode) {
            lines += listOf(
                "",
                "===== 백엔드 취업 준비 모드 =====",
                "- API 설명, README, 프로젝트 정리, 테스트, DB/JPA/SQL, 예외 처리, 인증, 성능 개선처럼 백엔드 역량이 드러나는 미션을 우선하세요.",
                "- 세트 전체에 백엔드 기술 축과 프로젝트/README/문서 축이 반드시 함께 들어가야 합니다.",
                "- 프론트 중심 과제, 시연 영상, 디자인 산출물, 모바일 화면 과제는 피하세요.",
                "- 지원 자료로 바로 옮길 수 있는 문서화와 실전형 개선 과제를 우선하세요."
            )
        }

        if (signals.isAppGoalMode) {
            lines += listOf(
                "",
                "===== 앱 제작 목표 모드 =====",
                "- 앱 화면, 네비게이션, 상태 관리, 로그인 흐름, 프로토타입, 테스트처럼 모바일 앱 제작 맥락에 직접 닿는 미션을 우선하세요.",
                "- 세트 전체에 화면/상태/로그인 같은 앱 축과 기능 정리/시나리오/테스트 같은 진행 축이 반드시 함께 들어가야 합니다.",
                "- 사용자가 모바일 스택을 직접 언급하지 않았다면 React 웹, Vue, Express 같은 웹/서버 프레임워크를 임의로 가정하지 마세요.",
                "- HTML/CSS hover 효과, Chart.js, 영상 촬영/편집, 여러 명 대상 사용자 테스트처럼 앱 제작과 직접 연결되지 않거나 과한 과제는 피하세요.",
                "- 화면 1개, 흐름 1개, 상태 1개, 테스트 1개처럼 모바일 산출물을 작게 쪼개 제안하세요."
            )
        }

        if (!signals.hasExplicitTechStack) {
            lines += listOf(
                "",
                "===== 기술 스택 가정 금지 =====",
                "- 사용자가 React, Flutter 같은 특정 프레임워크를 직접 언급하지 않았다면 임의로 가정하지 마세요.",
                "- 스택이 불분명하면 일반적인 포트폴리오 정리, 프로젝트 설명, GitHub/문서 개선 미션을 우선하세요."
            )
        }

        if (signals.isJobSwitchPortfolioMode && signals.isFollowUpMode) {
            lines += listOf(
                "",
                "===== 후속 추천 고정 규칙 =====",
                "- 전날 만든 문서, README, 이력서 항목, 면접 답변 초안을 더 구체화하는 방향으로만 이어가세요.",
                "- 새 채널 개설, 브랜딩용 게시물 작성, 새 웹사이트 구축, 대규모 코드 변경처럼 새로운 축의 과제는 피하세요.",
                "- 후속 미션은 전날 산출물을 개선, 보완, 확장하는 작업이어야 합니다."
            )
        }

        if (signals.isPlannerPortfolioMode && signals.isFollowUpMode) {
            lines += listOf(
                "",
                "===== 기획 후속 추천 고정 규칙 =====",
                "- 전날 작성한 문제 정의, 요구사항, 핵심 흐름, 포트폴리오 초안을 더 구체화하는 방향으로만 이어가세요.",
                "- 전날과 무관한 새 서비스 분석이나 광범위한 비교 과제는 피하세요."
            )
        }

        if (signals.isPlannerBeginnerMode && signals.isFollowUpMode) {
            lines += listOf(
                "",
                "===== 기획 입문 후속 추천 고정 규칙 =====",
                "- 전날 만든 사용자 문제, 핵심 기능, 화면 스케치, 흐름 메모를 보완하는 방향으로만 이어가세요.",
                "- 책 읽기, 트렌드 조사, 새 서비스 분석처럼 전날 산출물과 무관한 새 조사 축으로 넘어가지 마세요."
            )
        }

        if (signals.isOfficeMode && signals.isFollowUpMode) {
            lines += listOf(
                "",
                "===== 사무직 후속 추천 고정 규칙 =====",
                "- 전날 정리한 보고서, 시트, 함수, 템플릿을 보완하고 실무 적용성을 높이는 방향으로만 이어가세요.",
                "- 개발 자동화나 별도 시스템 구축처럼 새로운 축으로 넘어가지 마세요."
            )
        }

        if (signals.isServiceOperationMode && signals.isFollowUpMode) {
            lines += listOf(
                "",
                "===== 자영업 후속 추천 고정 규칙 =====",
                "- 전날 만든 응대 문구, 후기 기준, 체크리스트, 운영 흐름을 더 다듬고 붙여쓰는 방향으로만 이어가세요.",
                "- 새 마케팅 전략이나 개발 과제로 확장하지 마세요."
            )
        }

        if (signals.isEducatorMode && signals.isFollowUpMode) {
            lines += listOf(
                "",
                "===== 교육자 후속 추천 고정 규칙 =====",
                "- 전날 만든 수업 자료, 퀴즈, 과제, 피드백 틀을 보완하는 방향으로만 이어가세요.",
                "- 시범 운영, 현장 테스트, 대형 콘텐츠 패키지 제작처럼 범위가 커지는 과제는 피하세요."
            )
        }

        if (signals.isDesignerMode && signals.isFollowUpMode) {
            lines += listOf(
                "",
                "===== 디자이너 후속 추천 고정 규칙 =====",
                "- 전날 만든 화면, 사용자 흐름, 케이스 문단, 피드백 메모를 더 구체화하는 방향으로만 이어가세요.",
                "- 새로운 채널, 새로운 툴 학습, 개발 구현 과제로 축을 바꾸지 마세요."
            )
        }

        if (signals.isResearchMode && signals.isFollowUpMode) {
            lines += listOf(
                "",
                "===== 연구 후속 추천 고정 규칙 =====",
                "- 전날 정리한 문헌, 가설, 데이터, 그래프, 결과 문장을 보완하는 방향으로만 이어가세요.",
                "- 포트폴리오화, 영상화, 별도 웹사이트 제작처럼 연구와 무관한 새 축으로 넘어가지 마세요."
            )
        }

        if ((signals.isPlannerMode || signals.isDesignerMode || signals.isResearchMode) &&
            !signals.hasExplicitWebsitePreference
        ) {
            lines += listOf(
                "",
                "===== 웹사이트 형식 가정 금지 =====",
                "- 사용자가 웹사이트, 웹페이지, 랜딩 페이지를 직접 언급하지 않았다면 이를 전제로 한 미션을 추천하지 마세요."
            )
        }

        return if (lines.isEmpty()) "" else lines.joinToString("\n") + "\n"
    }

    /**
     * LLM 응답 파싱
     */
    private fun parseMissions(response: String, difficulties: List<Int>): List<Mission> {
        return try {
            val jsonContent = extractJson(response)
            val parsed = objectMapper.readValue<MissionResponse>(jsonContent)

            // 요청한 난이도만 필터링하고 난이도별 1개씩만 선택
            parsed.missions
                .filter { it.difficulty in difficulties }
                .groupBy { it.difficulty }
                .mapValues { (_, missions) -> missions.first() }
                .values.toList()

        } catch (e: Exception) {
            logger.error("응답 파싱 실패: ${e.message}")
            parseMissionsLoosely(response, difficulties)
        }
    }

    private fun parseMissionsLoosely(response: String, difficulties: List<Int>): List<Mission> {
        val directOrderRegex = Regex(
            "\\{[^{}]*\"content\"\\s*:\\s*\"([^\"]+)\"[^{}]*\"difficulty\"\\s*:\\s*(\\d+)[^{}]*\\}",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val reverseOrderRegex = Regex(
            "\\{[^{}]*\"difficulty\"\\s*:\\s*(\\d+)[^{}]*\"content\"\\s*:\\s*\"([^\"]+)\"[^{}]*\\}",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        val missions = mutableListOf<Mission>()

        directOrderRegex.findAll(response).forEach { match ->
            val content = match.groupValues[1].trim()
            val difficulty = match.groupValues[2].toIntOrNull()
            if (!content.isNullOrBlank() && difficulty != null) {
                missions += Mission(content = content, difficulty = difficulty)
            }
        }

        if (missions.isEmpty()) {
            reverseOrderRegex.findAll(response).forEach { match ->
                val difficulty = match.groupValues[1].toIntOrNull()
                val content = match.groupValues[2].trim()
                if (!content.isNullOrBlank() && difficulty != null) {
                    missions += Mission(content = content, difficulty = difficulty)
                }
            }
        }

        return missions
            .filter { it.difficulty in difficulties }
            .groupBy { it.difficulty }
            .mapValues { (_, values) -> values.first() }
            .values
            .toList()
    }

    /**
     * JSON 추출 (견고한 파싱)
     */
    private fun extractJson(response: String): String {
        val trimmed = response.trim()

        // 1. 마크다운 코드블록 제거: ```json ... ``` 또는 ``` ... ```
        val withoutCodeBlock = trimmed
            .replace(Regex("```json\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("```\\s*"), "")
            .replace(Regex("\\s*```"), "")
            .trim()

        // 2. JSON 객체 추출: { ... } 패턴 찾기
        val jsonRegex = Regex("\\{[\\s\\S]*\"missions\"[\\s\\S]*\\}")
        val jsonMatch = jsonRegex.find(withoutCodeBlock)
        if (jsonMatch != null) {
            return jsonMatch.value
        }

        // 3. 그래도 못 찾으면 원본 반환
        return withoutCodeBlock
    }

    // DTO 클래스
    private data class MissionResponse(val missions: List<Mission>)

    private data class Mission(
        val content: String,
        @JsonAlias("relatedInterest")
        val directFullPath: List<String> = emptyList(),
        val difficulty: Int? = null
    )

    private fun findInvalidMissionContents(
        missions: List<Mission>,
        timeBudgetMinutes: Int?,
        recommendationSignals: RecommendationSignals
    ): List<String> {
        return missions.mapNotNull { mission ->
            val content = mission.content.trim()
            if (isValidDailyMissionContent(content, timeBudgetMinutes, recommendationSignals)) {
                null
            } else {
                content
            }
        }
    }

    private fun findInvalidMissionSetIssues(
        missions: List<Mission>,
        recommendationSignals: RecommendationSignals
    ): List<String> {
        val issues = mutableListOf<String>()
        val joined = missions.joinToString(" ") { it.content }.lowercase()
        val artifactFamilies = extractArtifactFamilies(joined)

        if (recommendationSignals.isJobSwitchPortfolioMode) {
            if (!containsAnyKeyword(joined, CAREER_PROJECT_KEYWORDS)) {
                issues += "이직/포트폴리오 문맥인데 프로젝트/성과 축이 부족합니다."
            }
            if (!containsAnyKeyword(joined, CAREER_SUPPORT_KEYWORDS)) {
                issues += "이직/포트폴리오 문맥인데 이력서/면접/포트폴리오 축이 부족합니다."
            }
        }

        if (recommendationSignals.isBackendCareerMode) {
            if (!containsAnyKeyword(joined, BACKEND_REQUIRED_KEYWORDS) && !joined.contains("백엔드")) {
                issues += "백엔드 취업 문맥인데 백엔드 기술 축이 부족합니다."
            }
            if (!containsAnyKeyword(joined, BACKEND_PROJECT_DOC_KEYWORDS)) {
                issues += "백엔드 취업 문맥인데 프로젝트/README/문서 축이 부족합니다."
            }
        }

        if (recommendationSignals.isAppGoalMode) {
            if (!containsAnyKeyword(joined, APP_MOBILE_KEYWORDS)) {
                issues += "앱 제작 문맥인데 화면/상태/로그인 같은 앱 축이 부족합니다."
            }
            if (!containsAnyKeyword(joined, APP_OUTPUT_KEYWORDS)) {
                issues += "앱 제작 문맥인데 기능 정리/시나리오/테스트 축이 부족합니다."
            }
        }

        if (recommendationSignals.isPlannerBeginnerMode) {
            if (!containsAnyKeyword(joined, PLANNER_CASE_KEYWORDS)) {
                issues += "기획 입문 문맥인데 문제/요구사항/핵심 기능 축이 부족합니다."
            }
            if (!containsAnyKeyword(joined, PLANNER_FLOW_KEYWORDS)) {
                issues += "기획 입문 문맥인데 사용자/흐름/화면 축이 부족합니다."
            }
        }

        if (recommendationSignals.isPlannerPortfolioMode) {
            if (!containsAnyKeyword(joined, PLANNER_CASE_KEYWORDS)) {
                issues += "기획 포트폴리오 문맥인데 문제/요구사항/프로젝트 축이 부족합니다."
            }
            if (!containsAnyKeyword(joined, PLANNER_FLOW_KEYWORDS)) {
                issues += "기획 포트폴리오 문맥인데 사용자/흐름/화면 축이 부족합니다."
            }
            if (!containsAnyKeyword(joined, CAREER_SUPPORT_KEYWORDS + setOf("기획서"))) {
                issues += "기획 포트폴리오 문맥인데 포트폴리오/기획서/지원 자료 축이 부족합니다."
            }
        }

        if (recommendationSignals.isDesignerMode) {
            if (!containsAnyKeyword(joined, DESIGN_VISUAL_KEYWORDS)) {
                issues += "디자이너 문맥인데 화면/흐름/시안 축이 부족합니다."
            }
            if (!containsAnyKeyword(joined, DESIGN_CASE_KEYWORDS)) {
                issues += "디자이너 문맥인데 케이스/피드백/문제 해결 축이 부족합니다."
            }
        }

        if (recommendationSignals.isOfficeMode) {
            if (!containsAnyKeyword(joined, OFFICE_TOOL_KEYWORDS)) {
                issues += "사무직 문맥인데 엑셀/함수/시트 축이 부족합니다."
            }
            if (!containsAnyKeyword(joined, OFFICE_EFFECT_KEYWORDS)) {
                issues += "사무직 문맥인데 업무 효율/정리/자동화 축이 부족합니다."
            }
        }

        if (recommendationSignals.isServiceOperationMode) {
            if (!containsAnyKeyword(joined, SELF_SERVICE_CUSTOMER_KEYWORDS)) {
                issues += "자영업 문맥인데 고객/응대/서비스 축이 부족합니다."
            }
            if (!containsAnyKeyword(joined, SELF_SERVICE_OPERATION_KEYWORDS)) {
                issues += "자영업 문맥인데 운영/개선/정리 축이 부족합니다."
            }
        }

        if (recommendationSignals.isEducatorMode) {
            if (!containsAnyKeyword(joined, EDUCATOR_CORE_KEYWORDS)) {
                issues += "교육자 문맥인데 수업/학생/자료 축이 부족합니다."
            }
            if (!containsAnyKeyword(joined, EDUCATOR_OUTCOME_KEYWORDS)) {
                issues += "교육자 문맥인데 퀴즈/평가/정리 축이 부족합니다."
            }
        }

        if (recommendationSignals.isResearchMode) {
            if (!containsAnyKeyword(joined, RESEARCH_CORE_KEYWORDS)) {
                issues += "연구 문맥인데 문헌/가설/데이터 축이 부족합니다."
            }
            if (!containsAnyKeyword(joined, RESEARCH_OUTPUT_KEYWORDS)) {
                issues += "연구 문맥인데 요약/보고서/결과 축이 부족합니다."
            }
        }

        if (recommendationSignals.isFollowUpMode &&
            recommendationSignals.followUpArtifactFamilies.isNotEmpty() &&
            artifactFamilies.intersect(recommendationSignals.followUpArtifactFamilies).isEmpty()
        ) {
            issues += "후속 추천인데 전날 미션 산출물과 이어지는 축이 부족합니다."
        }

        return issues
    }

    private fun isValidDailyMissionContent(
        content: String,
        timeBudgetMinutes: Int?,
        recommendationSignals: RecommendationSignals
    ): Boolean {
        val lowered = content.lowercase()
        if (content.isBlank()) return false
        if (broadProjectPattern.containsMatchIn(content)) return false
        if (timeBudgetMinutes != null && timeBudgetMinutes <= 60 && shortBudgetBroadPattern.containsMatchIn(content)) {
            return false
        }
        if (timeBudgetMinutes != null && timeBudgetMinutes <= 120 && mediumBudgetBroadPattern.containsMatchIn(content)) {
            return false
        }
        if (bundledActionPattern.containsMatchIn(content) || parentheticalChecklistPattern.containsMatchIn(content)) {
            return false
        }
        if (recommendationSignals.isPlannerMode && planningOverreachPattern.containsMatchIn(content)) {
            return false
        }
        if (recommendationSignals.isPlannerPortfolioMode && plannerPortfolioOfftrackPattern.containsMatchIn(content)) {
            return false
        }
        if (recommendationSignals.isEducatorMode && educatorBroadPattern.containsMatchIn(content)) {
            return false
        }
        if (recommendationSignals.isDesignerMode && designerOfftrackPattern.containsMatchIn(content)) {
            return false
        }
        if (recommendationSignals.isOfficeMode && officeOverreachPattern.containsMatchIn(content)) {
            return false
        }
        if (recommendationSignals.isServiceOperationMode && serviceOperationOfftrackPattern.containsMatchIn(content)) {
            return false
        }
        if (recommendationSignals.isAppGoalMode && mobileOverreachPattern.containsMatchIn(content)) {
            return false
        }
        if (recommendationSignals.isJobSwitchPortfolioMode && portfolioMismatchPattern.containsMatchIn(content)) {
            return false
        }
        if (recommendationSignals.yearsOfExperience != null &&
            recommendationSignals.yearsOfExperience >= 2 &&
            beginnerTaskPattern.containsMatchIn(content)
        ) {
            return false
        }
        if (!recommendationSignals.hasExplicitTechStack && assumedFrameworkPattern.containsMatchIn(content)) {
            return false
        }
        if (recommendationSignals.disallowUnmentionedFrameworkExpansion &&
            containsUnmentionedTechKeyword(content, recommendationSignals.mentionedTechKeywords)
        ) {
            return false
        }
        if (recommendationSignals.isJobSwitchPortfolioMode &&
            recommendationSignals.yearsOfExperience != null &&
            recommendationSignals.yearsOfExperience >= 2 &&
            !recommendationSignals.isBeginnerInTargetArea &&
            experiencedPortfolioBeginnerPattern.containsMatchIn(content)
        ) {
            return false
        }
        if (recommendationSignals.isJobSwitchPortfolioMode &&
            !recommendationSignals.hasExplicitPortfolioSitePreference &&
            assumedPortfolioSitePattern.containsMatchIn(content)
        ) {
            return false
        }
        if (recommendationSignals.isJobSwitchPortfolioMode &&
            !recommendationSignals.hasExplicitPortfolioVideoPreference &&
            assumedPortfolioVideoPattern.containsMatchIn(content)
        ) {
            return false
        }
        if (!recommendationSignals.hasExplicitPortfolioVideoPreference &&
            recommendationSignals.disallowImplicitMediaFormats &&
            assumedPortfolioVideoPattern.containsMatchIn(content)
        ) {
            return false
        }
        if (!recommendationSignals.hasExplicitPortfolioDocumentPreference &&
            recommendationSignals.disallowImplicitMediaFormats &&
            assumedPortfolioDocumentPattern.containsMatchIn(content)
        ) {
            return false
        }
        if (recommendationSignals.isJobSwitchPortfolioMode &&
            !recommendationSignals.hasExplicitPortfolioDocumentPreference &&
            assumedPortfolioDocumentPattern.containsMatchIn(content)
        ) {
            return false
        }
        if (recommendationSignals.isJobSwitchPortfolioMode &&
            !recommendationSignals.hasExplicitPlanningToolPreference &&
            assumedPlanningToolPattern.containsMatchIn(content)
        ) {
            return false
        }
        if ((recommendationSignals.isPlannerMode || recommendationSignals.isJobSwitchPortfolioMode) &&
            !recommendationSignals.hasExplicitPlanningToolPreference &&
            assumedPlanningToolPattern.containsMatchIn(content)
        ) {
            return false
        }
        if (recommendationSignals.isAppGoalMode &&
            !recommendationSignals.hasExplicitPlanningToolPreference &&
            assumedPlanningToolPattern.containsMatchIn(content)
        ) {
            return false
        }
        if ((recommendationSignals.isPlannerMode || recommendationSignals.isDesignerMode || recommendationSignals.isResearchMode) &&
            !recommendationSignals.hasExplicitWebsitePreference &&
            assumedWebsitePattern.containsMatchIn(content)
        ) {
            return false
        }
        if (recommendationSignals.hasBackendTechContext &&
            !recommendationSignals.hasWebTechContext &&
            !recommendationSignals.hasMobileTechContext &&
            frontendMobilePattern.containsMatchIn(content)
        ) {
            return false
        }
        if ((recommendationSignals.hasWebTechContext || recommendationSignals.hasMobileTechContext) &&
            !recommendationSignals.hasBackendTechContext &&
            backendPattern.containsMatchIn(content)
        ) {
            return false
        }
        if (recommendationSignals.isAppGoalMode &&
            !recommendationSignals.hasMobileTechContext &&
            webFrontendPattern.containsMatchIn(content)
        ) {
            return false
        }
        if (recommendationSignals.isOfficeMode) {
            if (developerTaskPattern.containsMatchIn(content)) return false
            if (!officeKeywordPattern.containsMatchIn(content)) return false
        }
        if (recommendationSignals.isServiceOperationMode) {
            if (developerTaskPattern.containsMatchIn(content)) return false
            if (!selfServiceKeywordPattern.containsMatchIn(content)) return false
        }
        if (recommendationSignals.isJobSwitchPortfolioMode &&
            !recommendationSignals.isBackendCareerMode &&
            !containsAnyKeyword(lowered, CAREER_PROJECT_KEYWORDS + CAREER_SUPPORT_KEYWORDS)
        ) {
            return false
        }
        if (recommendationSignals.isEducatorMode) {
            if (developerTaskPattern.containsMatchIn(content)) return false
            if (!educatorKeywordPattern.containsMatchIn(content)) return false
        }
        if (recommendationSignals.isDesignerMode) {
            if (developerTaskPattern.containsMatchIn(content)) return false
            if (!containsAnyKeyword(lowered, DESIGN_VISUAL_KEYWORDS + DESIGN_CASE_KEYWORDS + CAREER_SUPPORT_KEYWORDS)) return false
        }
        if (recommendationSignals.isResearchMode) {
            if (developerTaskPattern.containsMatchIn(content)) return false
            if (!researchKeywordPattern.containsMatchIn(content)) return false
        }
        if (recommendationSignals.isPlannerMode && !plannerKeywordPattern.containsMatchIn(content)) {
            return false
        }
        if (recommendationSignals.isFollowUpMode &&
            recommendationSignals.followUpArtifactFamilies.isNotEmpty() &&
            extractArtifactFamilies(content.lowercase()).intersect(recommendationSignals.followUpArtifactFamilies).isEmpty()
        ) {
            return false
        }
        return true
    }

    private fun extractArtifactFamilies(text: String): Set<String> {
        return artifactFamilyKeywords.mapNotNull { (family, keywords) ->
            if (containsAnyKeyword(text, keywords)) family else null
        }.toSet()
    }

    private fun extractFollowUpArtifactFamilies(additionalContext: String): Set<String> {
        val previousMissionText = additionalContext.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("- 난이도 ") }
            .joinToString(" ") { line -> line.substringAfter(":").trim() }
            .lowercase()

        if (previousMissionText.isBlank()) {
            return emptySet()
        }

        return extractArtifactFamilies(previousMissionText)
    }

    private fun containsAnyKeyword(text: String, keywords: Set<String>): Boolean {
        return keywords.any { keyword -> text.contains(keyword.lowercase()) }
    }

    private fun buildRetryFeedback(
        invalidContents: List<String>,
        invalidSetIssues: List<String>
    ): String {
        val feedback = mutableListOf<String>()
        val invalidJoined = invalidContents.joinToString(" ").lowercase()
        val issueJoined = invalidSetIssues.joinToString(" ").lowercase()

        if (invalidContents.isNotEmpty()) {
            feedback += "- 아래 예시와 유사한 미션은 다시 쓰지 마세요:"
            invalidContents.take(3).forEach { feedback += "  - $it" }
        }
        if (invalidJoined.contains("웹사이트") || invalidJoined.contains("html/css")) {
            feedback += "- 웹사이트, HTML/CSS, 랜딩 페이지 같은 형식 가정은 금지입니다."
        }
        if (invalidJoined.contains("figma") || invalidJoined.contains("pdf") || invalidJoined.contains("ppt")) {
            feedback += "- 사용자가 직접 언급하지 않은 도구나 PDF/PPT 형식을 임의로 가정하지 마세요."
        }
        if (invalidJoined.contains("동영상") || invalidJoined.contains("영상")) {
            feedback += "- 영상 촬영/편집, 동영상 설명 과제는 넣지 마세요."
        }
        if (invalidJoined.contains("및") || invalidJoined.contains("+") || invalidJoined.contains("후")) {
            feedback += "- 한 미션 안에 두 단계 이상의 행동을 묶지 말고, 단일 작업 1개만 제안하세요."
        }
        if (issueJoined.contains("이력서/면접/포트폴리오")) {
            feedback += "- 세트 안에 이력서, 면접, 포트폴리오 중 최소 1개 축의 미션을 반드시 포함하세요."
        }
        if (issueJoined.contains("백엔드 기술 축")) {
            feedback += "- 세트 안에 API, DB, SQL, 테스트, 엔드포인트 같은 백엔드 기술 미션을 반드시 포함하세요."
        }
        if (issueJoined.contains("사용자/흐름/화면")) {
            feedback += "- 세트 안에 사용자, 흐름, 화면, 와이어프레임 중 최소 1개 축의 미션을 반드시 포함하세요."
        }
        if (issueJoined.contains("고객/응대/서비스")) {
            feedback += "- 세트 안에 고객 응대/문의/서비스 축과 운영 개선 축이 모두 들어가야 합니다."
        }
        if (issueJoined.contains("퀴즈/평가/정리")) {
            feedback += "- 세트 안에 수업 자료 축과 퀴즈/평가/정리 축이 모두 들어가야 합니다."
        }

        return feedback.joinToString("\n")
    }

    private fun buildFallbackMissions(
        directFullPath: List<String>,
        recommendationSignals: RecommendationSignals,
        additionalContext: String?
    ): List<Mission> {
        return buildDeterministicMissions(directFullPath, recommendationSignals, additionalContext)
    }

    private fun buildDeterministicMissions(
        directFullPath: List<String>,
        recommendationSignals: RecommendationSignals,
        additionalContext: String?
    ): List<Mission> {
        if (additionalContext.isNullOrBlank()) {
            return emptyList()
        }

        val context = parseRecommendationContext(additionalContext)
        val contents = when {
            recommendationSignals.isBackendCareerMode && recommendationSignals.isFollowUpMode -> backendCareerFollowUpFallback()
            recommendationSignals.isBackendCareerMode -> backendCareerFallback()
            recommendationSignals.isAppGoalMode && recommendationSignals.isFollowUpMode -> appFollowUpFallback()
            recommendationSignals.isAppGoalMode -> appTodayFallback()
            recommendationSignals.isPlannerPortfolioMode && recommendationSignals.isFollowUpMode -> plannerPortfolioFollowUpFallback()
            recommendationSignals.isPlannerPortfolioMode -> plannerPortfolioTodayFallback()
            recommendationSignals.isPlannerBeginnerMode && recommendationSignals.isFollowUpMode -> plannerBeginnerFollowUpFallback()
            recommendationSignals.isPlannerBeginnerMode -> plannerBeginnerTodayFallback()
            recommendationSignals.isDesignerMode && recommendationSignals.isFollowUpMode -> designerFollowUpFallback()
            recommendationSignals.isDesignerMode -> designerTodayFallback()
            recommendationSignals.isOfficeMode && recommendationSignals.isFollowUpMode -> officeFollowUpFallback()
            recommendationSignals.isOfficeMode -> officeTodayFallback()
            recommendationSignals.isServiceOperationMode && recommendationSignals.isFollowUpMode -> serviceOperationFollowUpFallback()
            recommendationSignals.isServiceOperationMode -> serviceOperationTodayFallback()
            recommendationSignals.isEducatorMode && recommendationSignals.isFollowUpMode -> educatorFollowUpFallback()
            recommendationSignals.isEducatorMode -> educatorTodayFallback()
            recommendationSignals.isResearchMode && recommendationSignals.isFollowUpMode -> researchFollowUpFallback()
            recommendationSignals.isResearchMode -> researchTodayFallback()
            recommendationSignals.isJobSwitchPortfolioMode && recommendationSignals.isFollowUpMode -> careerPortfolioFollowUpFallback()
            recommendationSignals.isJobSwitchPortfolioMode -> careerPortfolioTodayFallback()
            recommendationSignals.isFollowUpMode -> genericFollowUpFallback(context)
            else -> genericTodayFallback(context, directFullPath.lastOrNull() ?: "관심사")
        }

        return contents.mapIndexed { index, content ->
            Mission(
                content = content,
                directFullPath = directFullPath,
                difficulty = index + 1
            )
        }
    }

    private fun parseRecommendationContext(additionalContext: String?): ParsedRecommendationContext {
        if (additionalContext.isNullOrBlank()) {
            return ParsedRecommendationContext()
        }

        fun extract(label: String): String? {
            return additionalContext.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.startsWith("$label:") }
                ?.substringAfter(":")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }

        val completedMissions = additionalContext.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("- 난이도 ") }
            .mapNotNull { line -> line.substringAfter(":", "").trim().takeIf { it.isNotBlank() } }
            .toList()

        return ParsedRecommendationContext(
            goal = extract("현재 목표"),
            desiredOutcome = extract("최종 결과물"),
            skillLevel = extract("현재 실력"),
            recentExperience = extract("최근 직접 해본 작업"),
            targetPeriod = extract("목표 기간"),
            dailyAvailableTime = extract("하루 투자 가능 시간"),
            additionalOpinion = extract("추가 의견"),
            completedMissions = completedMissions
        )
    }

    private fun careerPortfolioTodayFallback(): List<String> = listOf(
        "대표 프로젝트 1개 선정 이유 3줄 작성",
        "선정한 프로젝트 문제 해결 사례 1개를 4문장으로 작성",
        "선정한 프로젝트 README 소개 문단 1개 작성",
        "이력서 성과 bullet 3개 작성",
        "지원 직무 예상 면접 답변 1개 초안 작성"
    )

    private fun careerPortfolioFollowUpFallback(): List<String> = listOf(
        "대표 프로젝트 핵심 기능 1개 설명 문장 3개 작성",
        "문제 해결 사례 1개 STAR 답변 5문장 작성",
        "README 소개 문단 1개에 기술 선택 이유 3줄 추가",
        "이력서 bullet 1개를 수치 중심 문장으로 수정",
        "지원 동기 문단 1개 작성"
    )

    private fun backendCareerFallback(): List<String> = listOf(
        "백엔드 프로젝트 API 1개 요청 응답 예시 작성",
        "백엔드 프로젝트 엔드포인트 1개 예외 처리 기준 3개 작성",
        "백엔드 프로젝트 DB 테이블 1개 역할 설명 3줄 작성",
        "백엔드 프로젝트 README 소개 문단 1개 작성",
        "백엔드 이력서 bullet 3개 작성"
    )

    private fun backendCareerFollowUpFallback(): List<String> = listOf(
        "전날 프로젝트 API 예시 1개 상태코드 설명 3줄 추가",
        "전날 프로젝트 엔드포인트 1개 실패 시나리오 2개 추가",
        "전날 프로젝트 DB 테이블 1개 인덱스 1개 선택 이유 3줄 작성",
        "README 트러블슈팅 섹션 1개 작성",
        "백엔드 프로젝트 면접 답변 1개 초안 작성"
    )

    private fun appTodayFallback(): List<String> = listOf(
        "앱 아이디어 1개 사용자 문제 3줄 작성",
        "앱 프로젝트 1개 생성 결과 메모 3줄 작성",
        "핵심 데이터 모델 1개 필드 5개 작성",
        "핵심 기능 리스트 5개 우선순위 작성",
        "핵심 API 엔드포인트 1개 요청 응답 명세 작성"
    )

    private fun appFollowUpFallback(): List<String> = listOf(
        "앱 프로젝트 폴더 구조 5개 항목 작성",
        "핵심 데이터 테이블 1개 생성",
        "핵심 API 엔드포인트 1개 구현",
        "핵심 화면 1개 데이터 연결 구현",
        "로그인 API 1개 테스트 체크리스트 5개 작성"
    )

    private fun plannerBeginnerTodayFallback(): List<String> = listOf(
        "사용자 문제 1개 정의 문장 3개 작성",
        "핵심 사용자 페르소나 1명 초안 작성",
        "핵심 기능 1개 요구사항 3개 작성",
        "사용자 흐름 1개 4단계 작성",
        "화면 1개 와이어프레임 초안 작성"
    )

    private fun plannerBeginnerFollowUpFallback(): List<String> = listOf(
        "사용자 문제 1개 원인 2개 추가",
        "페르소나 1명 핵심 니즈 3개 추가",
        "핵심 기능 1개 성공 기준 3개 작성",
        "사용자 흐름 1개 예외 단계 1개 추가",
        "와이어프레임 1개 설명 메모 4개 추가"
    )

    private fun plannerPortfolioTodayFallback(): List<String> = listOf(
        "프로젝트 1개 문제 정의 문단 1개 작성",
        "프로젝트 1개 사용자 요구사항 3개 작성",
        "핵심 사용자 흐름 1개 5단계 작성",
        "포트폴리오용 기획 요약 문단 1개 작성",
        "지원용 기획 사례 문단 1개 작성"
    )

    private fun plannerPortfolioFollowUpFallback(): List<String> = listOf(
        "문제 정의 문단 1개 원인 문장 2개 추가",
        "요구사항 3개 중 핵심 기능 1개 성공 기준 3개 작성",
        "사용자 흐름 1개 예외 단계 1개 추가",
        "기획 요약 문단 1개 결과 문장 3개 추가",
        "이력서 프로젝트 bullet 3개 작성"
    )

    private fun designerTodayFallback(): List<String> = listOf(
        "개선 화면 1개 문제점 3개 작성",
        "화면 1개 사용자 흐름 4단계 작성",
        "화면 1개 와이어프레임 초안 작성",
        "케이스 스터디 문제 정의 문단 1개 작성",
        "디자인 근거 문장 3개 작성"
    )

    private fun designerFollowUpFallback(): List<String> = listOf(
        "화면 1개 개선 목표 3개 추가",
        "사용자 흐름 1개 이탈 지점 1개 작성",
        "와이어프레임 1개 컴포넌트 메모 4개 추가",
        "케이스 스터디 결과 문단 1개 작성",
        "면접 설명용 디자인 의사결정 문단 1개 작성"
    )

    private fun officeTodayFallback(): List<String> = listOf(
        "반복 업무 1개 항목 5개 작성",
        "엑셀 시트 구조 3열 작성",
        "함수 1개 적용 셀 3곳 작성",
        "피벗 기준 3개 작성",
        "보고서 템플릿 규칙 5개 작성"
    )

    private fun officeFollowUpFallback(): List<String> = listOf(
        "시트 구조 1개 입력 규칙 3개 추가",
        "함수 1개 예외값 2개 작성",
        "피벗 기준 3개 중 핵심 보고서 항목 1개 작성",
        "보고서 정리 순서 4단계 작성",
        "자동화 후보 항목 3개 작성"
    )

    private fun serviceOperationTodayFallback(): List<String> = listOf(
        "반복 문의 TOP 5 작성",
        "문의 유형 1개 응대 문구 3개 작성",
        "예약 체크리스트 1개 5항목 작성",
        "후기 답변 기준 3개 작성",
        "운영 문제 1개 대응 순서 4단계 작성"
    )

    private fun serviceOperationFollowUpFallback(): List<String> = listOf(
        "응대 문구 3개 중 핵심 문구 1개 수정",
        "체크리스트 1개 누락 항목 3개 추가",
        "후기 답변 기준 3개 금지 표현 2개 추가",
        "운영 문제 1개 대응 기준 3개 작성",
        "재방문 고객 안내 문구 1개 작성"
    )

    private fun educatorTodayFallback(): List<String> = listOf(
        "수업 목표 1개 3문장 작성",
        "학습자료 핵심 개념 3개 작성",
        "활동지 문항 3개 작성",
        "퀴즈 3문항 작성",
        "피드백 루브릭 기준 4개 작성"
    )

    private fun educatorFollowUpFallback(): List<String> = listOf(
        "수업 목표 1개 성취 기준 3개 추가",
        "학습자료 설명 문장 3개 작성",
        "퀴즈 3문항 정답 해설 3줄 작성",
        "과제 체크포인트 3개 작성",
        "루브릭 평가 문장 예시 3개 작성"
    )

    private fun researchTodayFallback(): List<String> = listOf(
        "연구 질문 1개 3문장 요약",
        "논문 1편 핵심 가설 2개 작성",
        "데이터 결과 3개 문장 작성",
        "그래프 해석 문단 1개 작성",
        "결과 요약 문단 1개 작성"
    )

    private fun researchFollowUpFallback(): List<String> = listOf(
        "연구 질문 1개 한계 2개 추가",
        "가설 1개 검증 기준 3개 작성",
        "핵심 수치 2개 문장 작성",
        "그래프 해석 문단 1개 의미 3줄 추가",
        "발표용 결과 요약 문단 1개 작성"
    )

    private fun genericTodayFallback(
        context: ParsedRecommendationContext,
        focusLabel: String
    ): List<String> {
        val outputLabel = context.desiredOutcome?.takeIf { it.isNotBlank() } ?: "$focusLabel 결과물"
        return listOf(
            "${focusLabel} 목표 핵심 작업 1개 3문장 작성",
            "${outputLabel} 필수 요소 3개 작성",
            "최근 해본 작업 1개 개선 포인트 3개 작성",
            "주간 체크포인트 4개 작성",
            "${focusLabel} 산출물 초안 1개 작성"
        )
    }

    private fun genericFollowUpFallback(context: ParsedRecommendationContext): List<String> {
        val previous = context.completedMissions.firstOrNull() ?: "전날 산출물 1개"
        return listOf(
            "${previous} 핵심 내용 3줄 작성",
            "전날 산출물 1개 부족 기준 3개 추가",
            "전날 작업 1개 설명 문단 1개 작성",
            "전날 작업 1개 검토 항목 4개 작성",
            "다음 단계 실행 문장 5개 작성"
        )
    }

    private fun containsUnmentionedTechKeyword(content: String, mentionedTechKeywords: Set<String>): Boolean {
        val normalizedContent = content.lowercase()
        return TECH_STACK_KEYWORDS.any { keyword ->
            normalizedContent.contains(keyword) &&
                mentionedTechKeywords.none { mentioned ->
                    mentioned == keyword || mentioned.contains(keyword) || keyword.contains(mentioned)
                }
        }
    }

    private fun extractTimeBudgetMinutes(additionalContext: String?): Int? {
        if (additionalContext.isNullOrBlank()) return null

        val timeLines = additionalContext.lineSequence()
            .map { it.trim() }
            .filter { line ->
                line.contains("하루") || line.contains("시간 상한") || line.contains("투자 가능 시간")
            }
            .toList()

        if (timeLines.isEmpty()) return null

        val totals = mutableListOf<Int>()
        val hourMinutePattern = Regex("(\\d+)\\s*시간(?:\\s*(\\d+)\\s*분)?")
        val minutePattern = Regex("(\\d+)\\s*분")

        timeLines.forEach { line ->
            hourMinutePattern.findAll(line).forEach { match ->
                val hours = match.groupValues[1].toIntOrNull() ?: 0
                val minutes = match.groupValues[2].toIntOrNull() ?: 0
                totals += hours * 60 + minutes
            }

            if (totals.isEmpty()) {
                minutePattern.findAll(line).forEach { match ->
                    match.groupValues[1].toIntOrNull()?.let { totals += it }
                }
            }
        }

        return totals.minOrNull()
    }

    private fun extractRecommendationSignals(
        additionalContext: String?,
        interestPath: InterestPath,
        memberProfile: MissionMemberProfile
    ): RecommendationSignals {
        val rawJobName = memberProfile.jobName ?: ""
        val rawDetailName = memberProfile.jobDetailName ?: ""
        val fallbackDetailName = interestPath.middleCategory?.lowercase()
            ?: interestPath.subCategory?.lowercase()
            ?: ""
        val jobName = if (rawJobName.isBlank()) interestPath.mainCategory.lowercase() else rawJobName.lowercase()
        val detailName = if (rawDetailName.isBlank()) fallbackDetailName else rawDetailName.lowercase()
        if (additionalContext.isNullOrBlank()) {
            return RecommendationSignals(
                isPlannerMode = detailName.contains("기획자"),
                isPlannerBeginnerMode = detailName.contains("기획자"),
                isOfficeMode = detailName.contains("사무직"),
                isServiceOperationMode = jobName.contains("자영업") || detailName.contains("서비스직"),
                isEducatorMode = detailName.contains("교육"),
                isDesignerMode = detailName.contains("디자이너"),
                isResearchMode = detailName.contains("연구"),
                isAppGoalMode = false,
                isBackendCareerMode = detailName.contains("개발자") && jobName.contains("취준"),
                mentionedTechKeywords = emptySet()
            )
        }

        val normalized = additionalContext.lowercase()
        fun extractLabeledText(label: String): String {
            return additionalContext.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.startsWith("$label:") }
                ?.substringAfter(":")
                ?.trim()
                ?.lowercase()
                ?: ""
        }
        val targetIntentText = listOf(
            extractLabeledText("현재 목표"),
            extractLabeledText("최종 결과물"),
            extractLabeledText("추가 의견")
        ).filter { it.isNotBlank() }.joinToString(" ")
        val experienceYears = EXPERIENCE_YEAR_PATTERNS.firstNotNullOfOrNull { pattern ->
            pattern.find(normalized)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }
        val hasCareerSupportSignal = normalized.contains("이직") ||
            normalized.contains("취업") ||
            normalized.contains("지원") ||
            normalized.contains("이력서") ||
            normalized.contains("면접") ||
            normalized.contains("자기소개서") ||
            normalized.contains("jd") ||
            jobName.contains("취준")
        val isCareerPreparationMode = hasCareerSupportSignal
        val hasBackendTechContext = BACKEND_TECH_KEYWORDS.any { keyword -> normalized.contains(keyword) }
        val hasWebTechContext = WEB_FRONTEND_TECH_KEYWORDS.any { keyword -> normalized.contains(keyword) }
        val hasMobileTechContext = MOBILE_TECH_KEYWORDS.any { keyword -> normalized.contains(keyword) }
        val mentionedTechKeywords = TECH_STACK_KEYWORDS.filter { keyword -> normalized.contains(keyword) }.toSet()
        val isPlannerMode = detailName.contains("기획자")
        val isPlannerPortfolioMode = isPlannerMode && isCareerPreparationMode
        val isPlannerBeginnerMode = isPlannerMode && !isCareerPreparationMode
        val isOfficeMode = detailName.contains("사무직")
        val isServiceOperationMode = jobName.contains("자영업") || detailName.contains("서비스직")
        val isEducatorMode = detailName.contains("교육")
        val isDesignerMode = detailName.contains("디자이너")
        val isResearchMode = detailName.contains("연구")
        val isAppGoalMode = detailName.contains("개발자") &&
            (normalized.contains("앱") || normalized.contains("모바일"))
        val hasExplicitBackendGoal = targetIntentText.contains("백엔드") ||
            targetIntentText.contains("스프링") ||
            targetIntentText.contains("spring")
        val isBackendCareerMode = detailName.contains("개발자") &&
            isCareerPreparationMode &&
            !isAppGoalMode &&
            (jobName.contains("취준") || hasExplicitBackendGoal)

        return RecommendationSignals(
            isJobSwitchPortfolioMode = isCareerPreparationMode,
            yearsOfExperience = experienceYears,
            hasExplicitTechStack = TECH_STACK_KEYWORDS.any { keyword -> normalized.contains(keyword) },
            isBeginnerInTargetArea = BEGINNER_TARGET_PATTERNS.any { pattern -> pattern.containsMatchIn(normalized) },
            hasBackendTechContext = hasBackendTechContext,
            hasWebTechContext = hasWebTechContext,
            hasMobileTechContext = hasMobileTechContext,
            mentionedTechKeywords = mentionedTechKeywords,
            disallowUnmentionedFrameworkExpansion = isCareerPreparationMode && mentionedTechKeywords.isNotEmpty(),
            isFollowUpMode = normalized.contains("이번 추천 모드: 다음날 후속 추천"),
            hasExplicitPortfolioSitePreference = PORTFOLIO_SITE_FORMAT_KEYWORDS.any { keyword -> normalized.contains(keyword) },
            hasExplicitPortfolioVideoPreference = PORTFOLIO_VIDEO_FORMAT_KEYWORDS.any { keyword -> normalized.contains(keyword) },
            hasExplicitPortfolioDocumentPreference = PORTFOLIO_DOCUMENT_FORMAT_KEYWORDS.any { keyword -> normalized.contains(keyword) },
            hasExplicitPlanningToolPreference = PLANNING_TOOL_KEYWORDS.any { keyword -> normalized.contains(keyword) },
            hasExplicitWebsitePreference = normalized.contains("웹사이트") ||
                normalized.contains("웹 페이지") ||
                normalized.contains("웹페이지") ||
                normalized.contains("랜딩 페이지"),
            followUpArtifactFamilies = extractFollowUpArtifactFamilies(additionalContext),
            disallowImplicitMediaFormats = !normalized.contains("영상") &&
                !normalized.contains("동영상") &&
                !normalized.contains("비디오") &&
                !normalized.contains("pdf") &&
                !normalized.contains("슬라이드") &&
                !normalized.contains("발표 자료"),
            isPlannerMode = isPlannerMode,
            isPlannerBeginnerMode = isPlannerBeginnerMode,
            isPlannerPortfolioMode = isPlannerPortfolioMode,
            isOfficeMode = isOfficeMode,
            isServiceOperationMode = isServiceOperationMode,
            isEducatorMode = isEducatorMode,
            isDesignerMode = isDesignerMode,
            isResearchMode = isResearchMode,
            isAppGoalMode = isAppGoalMode,
            isBackendCareerMode = isBackendCareerMode
        )
    }

    private data class ParsedRecommendationContext(
        val goal: String? = null,
        val desiredOutcome: String? = null,
        val skillLevel: String? = null,
        val recentExperience: String? = null,
        val targetPeriod: String? = null,
        val dailyAvailableTime: String? = null,
        val additionalOpinion: String? = null,
        val completedMissions: List<String> = emptyList()
    )

    private data class RecommendationSignals(
        val isJobSwitchPortfolioMode: Boolean = false,
        val yearsOfExperience: Int? = null,
        val hasExplicitTechStack: Boolean = false,
        val isBeginnerInTargetArea: Boolean = false,
        val hasBackendTechContext: Boolean = false,
        val hasWebTechContext: Boolean = false,
        val hasMobileTechContext: Boolean = false,
        val mentionedTechKeywords: Set<String> = emptySet(),
        val disallowUnmentionedFrameworkExpansion: Boolean = false,
        val isFollowUpMode: Boolean = false,
        val hasExplicitPortfolioSitePreference: Boolean = false,
        val hasExplicitPortfolioVideoPreference: Boolean = false,
        val hasExplicitPortfolioDocumentPreference: Boolean = false,
        val hasExplicitPlanningToolPreference: Boolean = false,
        val hasExplicitWebsitePreference: Boolean = false,
        val followUpArtifactFamilies: Set<String> = emptySet(),
        val disallowImplicitMediaFormats: Boolean = false,
        val isPlannerMode: Boolean = false,
        val isPlannerBeginnerMode: Boolean = false,
        val isPlannerPortfolioMode: Boolean = false,
        val isOfficeMode: Boolean = false,
        val isServiceOperationMode: Boolean = false,
        val isEducatorMode: Boolean = false,
        val isDesignerMode: Boolean = false,
        val isResearchMode: Boolean = false,
        val isAppGoalMode: Boolean = false,
        val isBackendCareerMode: Boolean = false
    )
}
