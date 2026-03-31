package com.haruUp.chat.qa

import com.fasterxml.jackson.databind.ObjectMapper
import com.haruUp.chat.application.service.ChatBotCompletedMission
import com.haruUp.chat.application.service.ChatBotMissionContext
import com.haruUp.chat.application.service.ChatBotMissionRecommendationService
import com.haruUp.missionembedding.dto.MissionDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.system.exitProcess

@Component
@Profile("chat-recommendation-qa")
class ChatRecommendationQaRunner(
    private val recommendationService: ChatBotMissionRecommendationService,
    private val objectMapper: ObjectMapper,
    private val applicationContext: ConfigurableApplicationContext
) : CommandLineRunner {

    override fun run(vararg args: String?) {
        runBlocking {
            val config = QaRunConfig.fromSystemProperties()
            val scenarios = ChatRecommendationQaScenarios.all()
                .filter { config.filter == null || it.id.contains(config.filter, ignoreCase = true) }
                .let { if (config.limit != null) it.take(config.limit) else it }

            if (scenarios.isEmpty()) {
                println("QA 시나리오가 없습니다.")
                shutdown(0)
                return@runBlocking
            }

            println("챗봇 추천 QA 시작")
            println("- 시나리오 수: ${scenarios.size}")
            println("- 병렬도: ${config.parallelism}")
            println("- 필터: ${config.filter ?: "없음"}")
            println("- 제한: ${config.limit ?: "없음"}")

            val dispatcher = Dispatchers.IO.limitedParallelism(config.parallelism)
            val completedCount = AtomicInteger(0)

            val results = scenarios.map { scenario ->
                async(dispatcher) {
                    val result = runCatching { executeScenario(scenario) }
                        .getOrElse { throwable ->
                            QaScenarioResult(
                                id = scenario.id,
                                title = scenario.title,
                                category = scenario.category,
                                subCategory = scenario.subCategory,
                                passed = false,
                                score = 0,
                                issues = listOf("실행 중 예외: ${throwable.message}"),
                                todayMissions = emptyList(),
                                nextDayMissions = emptyList()
                            )
                        }

                    val finished = completedCount.incrementAndGet()
                    val status = if (result.passed) "PASS" else "FAIL"
                    println("[$finished/${scenarios.size}] ${result.id} $status score=${result.score}")
                    result
                }
            }.awaitAll().sortedBy { it.id }

            val reportDir = Path.of("build", "reports", "chat-recommendation-qa")
            Files.createDirectories(reportDir)

            val jsonReport = reportDir.resolve("report.json")
            val markdownReport = reportDir.resolve("report.md")

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(jsonReport.toFile(), buildSummary(results))
            Files.writeString(markdownReport, buildMarkdownReport(results))

            val passCount = results.count { it.passed }
            val failCount = results.size - passCount
            val averageScore = if (results.isEmpty()) 0.0 else results.map { it.score }.average()

            println()
            println("QA 완료")
            println("- 통과: $passCount")
            println("- 실패: $failCount")
            println("- 평균 점수: ${"%.1f".format(averageScore)}")
            println("- JSON 리포트: $jsonReport")
            println("- Markdown 리포트: $markdownReport")

            shutdown(0)
        }
    }

    private suspend fun executeScenario(scenario: ChatRecommendationQaScenario): QaScenarioResult {
        val todayContext = scenario.toContext()
        val todayMissions = recommendationService.recommend(todayContext)
        val nextDayMissions = recommendationService.recommend(
            todayContext.copy(
                completedMissions = todayMissions.map { mission ->
                    ChatBotCompletedMission(
                        content = mission.content,
                        difficulty = mission.difficulty ?: 0
                    )
                }
            )
        )

        val evaluation = ChatRecommendationQaEvaluator.evaluate(
            scenario = scenario,
            todayMissions = todayMissions,
            nextDayMissions = nextDayMissions
        )

        return QaScenarioResult(
            id = scenario.id,
            title = scenario.title,
            category = scenario.category,
            subCategory = scenario.subCategory,
            passed = evaluation.passed,
            score = evaluation.score,
            issues = evaluation.issues,
            todayMissions = todayMissions.map { it.content },
            nextDayMissions = nextDayMissions.map { it.content }
        )
    }

    private fun buildSummary(results: List<QaScenarioResult>): QaSummaryReport {
        val passCount = results.count { it.passed }
        val failCount = results.size - passCount

        return QaSummaryReport(
            executedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            total = results.size,
            passed = passCount,
            failed = failCount,
            averageScore = if (results.isEmpty()) 0.0 else results.map { it.score }.average(),
            results = results
        )
    }

    private fun buildMarkdownReport(results: List<QaScenarioResult>): String {
        val builder = StringBuilder()
        val passCount = results.count { it.passed }
        val failCount = results.size - passCount
        val averageScore = if (results.isEmpty()) 0.0 else results.map { it.score }.average()

        builder.appendLine("# Chat Recommendation QA Report")
        builder.appendLine()
        builder.appendLine("- Executed At: ${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}")
        builder.appendLine("- Total: ${results.size}")
        builder.appendLine("- Passed: $passCount")
        builder.appendLine("- Failed: $failCount")
        builder.appendLine("- Average Score: ${"%.1f".format(averageScore)}")
        builder.appendLine()

        results.forEach { result ->
            builder.appendLine("## ${result.id} - ${result.title}")
            builder.appendLine("- Category: ${result.category} / ${result.subCategory}")
            builder.appendLine("- Status: ${if (result.passed) "PASS" else "FAIL"}")
            builder.appendLine("- Score: ${result.score}")
            if (result.issues.isNotEmpty()) {
                builder.appendLine("- Issues:")
                result.issues.forEach { issue ->
                    builder.appendLine("  - $issue")
                }
            }
            builder.appendLine("- Today Missions:")
            result.todayMissions.forEach { mission ->
                builder.appendLine("  - $mission")
            }
            builder.appendLine("- Next Day Missions:")
            result.nextDayMissions.forEach { mission ->
                builder.appendLine("  - $mission")
            }
            builder.appendLine()
        }

        return builder.toString()
    }

    private fun shutdown(exitCode: Int) {
        val actualExitCode = SpringApplication.exit(applicationContext, { exitCode })
        exitProcess(actualExitCode)
    }
}

internal object ChatRecommendationQaEvaluator {
    private val expectedDifficulties = setOf(1, 2, 3, 4, 5)
    private val genericBroadPattern = Regex(
        "(MVP|사용자\\s*테스트|배포\\s*완료|앱\\s*완성|서비스\\s*오픈|대규모\\s*리팩토링|포트폴리오\\s*사이트|웹사이트\\s*배포|동영상|영상\\s*편집|PDF\\s*버전|LinkedIn|인포그래픽)",
        RegexOption.IGNORE_CASE
    )
    private val shortBudgetBroadPattern = Regex(
        "(경쟁사\\s*3곳|페르소나\\s*2개|와이어프레임\\s*2개|사용자\\s*시나리오\\s*3개|화면\\s*3개|기술\\s*스택\\s*분석표\\s*작성)",
        RegexOption.IGNORE_CASE
    )
    private val assumedFrameworkPattern = Regex(
        "(React|Flutter|React\\s*Native|Vue|Next\\.js|Angular|SwiftUI|Spring|NestJS)",
        RegexOption.IGNORE_CASE
    )
    private val bundledActionPattern = Regex(
        "(\\+|→|->|=>|하고|한\\s*뒤|후에?|및).*(작성|정리|구현|설계|제작|도출|기록|분석|요약|적용|연결|비교|수정|보강|추가|촬영|편집)",
        RegexOption.IGNORE_CASE
    )
    private val parentheticalChecklistPattern = Regex(
        "\\([^)]*(,|/|·)[^)]*(,|/|·)[^)]*\\)"
    )
    private val scopeCuePattern = Regex(
        "(\\d+\\s*(개|장|줄|문항|페이지|회|명|곳|개사|단계|포인트|섹션|문단|컷|가지|시간|분)|a4|readme|초안|체크리스트|표|시트|화면|와이어프레임|문장|목차|bullet|불릿|kpi)",
        RegexOption.IGNORE_CASE
    )
    private val vagueOutcomePattern = Regex(
        "(정리|준비|개선|고도화|보강|업데이트|기획|분석|기록|작성|제작)$",
        RegexOption.IGNORE_CASE
    )
    private val beginnerAdvancedPattern = Regex(
        "(아키텍처|ci/?cd|배포|롤\\s*기반|2nd\\s*level\\s*cache|redis\\s*캐시|장애\\s*복구|대규모\\s*리팩토링|실시간\\s*db|사용자\\s*테스트)",
        RegexOption.IGNORE_CASE
    )
    private val experiencedBeginnerPattern = Regex(
        "(hello\\s*world|튜토리얼|기본\\s*개념|샘플\\s*앱|기초\\s*예제|todo\\s*list|to-?do\\s*list)",
        RegexOption.IGNORE_CASE
    )
    private val artifactFamilyKeywords = listOf(
        "career_project" to setOf("프로젝트", "성과", "kpi", "회고", "문제 해결", "경험", "사례"),
        "career_docs" to setOf("readme", "github", "이력서", "면접", "자기소개서", "jd", "포트폴리오", "bullet"),
        "backend" to setOf("spring", "api", "jpa", "db", "sql", "테스트", "엔드포인트", "jwt", "swagger"),
        "mobile" to setOf("앱", "화면", "네비게이션", "상태", "로그인", "프로토타입", "기능", "시나리오"),
        "planning" to setOf("사용자", "문제", "요구사항", "기획서", "기획", "흐름", "화면", "와이어프레임", "페르소나", "핵심 기능", "서비스"),
        "design" to setOf("ux", "ui", "화면", "와이어프레임", "사용자 흐름", "시안", "케이스", "피드백", "디자인 시스템", "컴포넌트", "리디자인"),
        "office" to setOf("엑셀", "함수", "피벗", "시트", "보고서", "데이터", "매크로", "템플릿"),
        "service_ops" to setOf("고객", "후기", "응대", "문의", "서비스", "체크리스트", "프로세스", "예약"),
        "education" to setOf("수업", "학습자료", "커리큘럼", "과제", "피드백", "강의", "학생", "퀴즈", "평가"),
        "research" to setOf("논문", "실험", "데이터", "분석", "가설", "연구", "문헌", "그래프", "보고서", "결과")
    )

    fun evaluate(
        scenario: ChatRecommendationQaScenario,
        todayMissions: List<MissionDto>,
        nextDayMissions: List<MissionDto>
    ): QaEvaluation {
        val issues = mutableListOf<String>()
        var score = 100
        val scenarioText = scenario.allContextText().lowercase()
        val dailyBudgetMinutes = extractDailyBudgetMinutes(scenario.dailyAvailableTime)

        fun penalize(points: Int, issue: String) {
            score -= points
            issues += issue
        }

        val todayInspection = evaluateMissionSet(
            label = "오늘",
            missions = todayMissions,
            issues = issues,
            dailyBudgetMinutes = dailyBudgetMinutes,
            requiredGroups = scenario.expectation.todayRequiredGroups
        )
        val nextDayInspection = evaluateMissionSet(
            label = "다음날",
            missions = nextDayMissions,
            issues = issues,
            dailyBudgetMinutes = dailyBudgetMinutes,
            requiredGroups = scenario.expectation.nextDayRequiredGroups
        )
        score -= todayInspection.penalty
        score -= nextDayInspection.penalty

        val todayTexts = todayMissions.map { it.content }
        val nextDayTexts = nextDayMissions.map { it.content }
        val todayText = todayTexts.joinToString(" ").lowercase()
        val nextDayText = nextDayTexts.joinToString(" ").lowercase()

        val duplicates = todayTexts.toSet().intersect(nextDayTexts.toSet())
        if (duplicates.isNotEmpty()) {
            penalize(15, "오늘/다음날 미션 중복: ${duplicates.joinToString()}")
        }

        if (scenario.expectation.avoidUnmentionedFormats) {
            val missingSitePreference = !containsAnyKeyword(scenarioText, setOf("포트폴리오 사이트", "웹 포트폴리오", "개인 웹사이트", "웹사이트"))
            val missingVideoPreference = !containsAnyKeyword(scenarioText, setOf("동영상", "영상", "비디오", "데모 영상"))
            val missingPdfPreference = !containsAnyKeyword(scenarioText, setOf("pdf", "슬라이드", "발표 자료"))
            val missingToolPreference = !containsAnyKeyword(scenarioText, setOf("figma", "jira"))

            if (missingSitePreference && containsAnyKeyword(todayText + " " + nextDayText, setOf("포트폴리오 사이트", "웹사이트", "웹 포트폴리오"))) {
                penalize(20, "사용자가 언급하지 않은 웹사이트 형식을 가정한 미션이 있습니다.")
            }
            if (missingVideoPreference && containsAnyKeyword(todayText + " " + nextDayText, setOf("동영상", "영상 편집", "촬영 편집", "시연 영상"))) {
                penalize(20, "사용자가 언급하지 않은 영상 형식을 가정한 미션이 있습니다.")
            }
            if (missingPdfPreference && containsAnyKeyword(todayText + " " + nextDayText, setOf("pdf", "슬라이드", "발표 자료"))) {
                penalize(20, "사용자가 언급하지 않은 PDF/슬라이드 형식을 가정한 미션이 있습니다.")
            }
            if (missingToolPreference && containsAnyKeyword(todayText + " " + nextDayText, setOf("figma", "jira"))) {
                penalize(15, "사용자가 언급하지 않은 도구를 가정한 미션이 있습니다.")
            }
        }

        if (scenario.expectation.avoidUnmentionedFrameworks &&
            !scenario.expectation.allowedFrameworkKeywords.any { scenarioText.contains(it.lowercase()) } &&
            assumedFrameworkPattern.containsMatchIn(todayText + " " + nextDayText)
        ) {
            penalize(15, "사용자가 직접 언급하지 않은 프레임워크/기술 스택을 가정한 미션이 있습니다.")
        }

        val todayMatchedKeywords = matchedKeywords(todayText, scenario.expectation.todayRequiredGroups)
        val nextDayMatchedKeywords = matchedKeywords(nextDayText, scenario.expectation.nextDayRequiredGroups)
        val scenarioArtifactFamilies = extractScenarioArtifactFamilies(scenario)
        val todayArtifactMatch = todayInspection.artifactFamilies.intersect(scenarioArtifactFamilies)
        val nextArtifactMatch = nextDayInspection.artifactFamilies.intersect(scenarioArtifactFamilies)

        val todayRequired = scenario.expectation.todayRequiredGroups.size
        val nextDayRequired = scenario.expectation.nextDayRequiredGroups.size
        if (todayMatchedKeywords.size < todayRequired) {
            penalize(
                20,
                "오늘 미션이 기대한 주제를 충분히 반영하지 못했습니다. matched=${todayMatchedKeywords.sorted()} expectedGroups=$todayRequired"
            )
        }
        if (nextDayMatchedKeywords.size < nextDayRequired) {
            penalize(
                20,
                "다음날 미션이 기대한 주제를 충분히 반영하지 못했습니다. matched=${nextDayMatchedKeywords.sorted()} expectedGroups=$nextDayRequired"
            )
        }
        if (scenarioArtifactFamilies.isNotEmpty() && todayArtifactMatch.isEmpty()) {
            penalize(10, "오늘 미션이 사용자의 목표/결과물/최근 작업 축과 충분히 연결되지 않습니다.")
        }
        if (scenarioArtifactFamilies.isNotEmpty() && nextArtifactMatch.isEmpty()) {
            penalize(10, "다음날 미션이 사용자의 목표/결과물/최근 작업 축과 충분히 연결되지 않습니다.")
        }

        val skillText = scenario.skillLevel.lowercase()
        val combinedText = "$todayText $nextDayText"
        if (isBeginnerSkill(skillText) && beginnerAdvancedPattern.containsMatchIn(combinedText)) {
            penalize(10, "입문자 수준 대비 과한 고난도 미션이 포함되어 있습니다.")
        }
        if (isExperiencedSkill(skillText) && experiencedBeginnerPattern.containsMatchIn(combinedText)) {
            penalize(10, "경력 수준 대비 지나치게 입문형인 미션이 포함되어 있습니다.")
        }

        val forbiddenMatches = scenario.expectation.forbiddenKeywords.filter { keyword ->
            todayText.contains(keyword.lowercase()) || nextDayText.contains(keyword.lowercase())
        }
        if (forbiddenMatches.isNotEmpty()) {
            penalize(20, "금지 키워드 포함: ${forbiddenMatches.joinToString()}")
        }

        val continuityOverlap = todayMatchedKeywords.intersect(nextDayMatchedKeywords)
        if (continuityOverlap.isEmpty()) {
            penalize(10, "오늘 추천과 다음날 추천 사이의 주제 연속성이 약합니다.")
        }
        val artifactOverlap = todayInspection.artifactFamilies.intersect(nextDayInspection.artifactFamilies)
        if (todayInspection.artifactFamilies.isNotEmpty() &&
            nextDayInspection.artifactFamilies.isNotEmpty() &&
            artifactOverlap.isEmpty()
        ) {
            penalize(10, "오늘 추천과 다음날 추천 사이의 산출물 연속성이 약합니다.")
        }

        if (score < 0) {
            score = 0
        }

        val passed = issues.isEmpty() || (score >= 80 && issues.none { it.startsWith("오늘 미션 개수") || it.startsWith("다음날 미션 개수") })

        return QaEvaluation(
            passed = passed,
            score = max(0, score),
            issues = issues
        )
    }

    private fun evaluateMissionSet(
        label: String,
        missions: List<MissionDto>,
        issues: MutableList<String>,
        dailyBudgetMinutes: Int?,
        requiredGroups: List<Set<String>>
    ): MissionSetInspection {
        var penalty = 0
        val texts = missions.map { it.content }
        val joined = texts.joinToString(" ").lowercase()

        if (missions.size != 5) {
            penalty += 30
            issues += "$label 미션 개수 오류: ${missions.size}"
        }

        val returnedDifficulties = missions.mapNotNull { it.difficulty }.toSet()
        if (returnedDifficulties != expectedDifficulties) {
            penalty += 25
            issues += "$label 난이도 구성이 1~5가 아닙니다: ${returnedDifficulties.sorted()}"
        }

        if (texts.any { it.isBlank() }) {
            penalty += 20
            issues += "${label}에 빈 미션이 있습니다."
        }

        val normalizedTexts = texts.map { normalizeMission(it) }
        if (normalizedTexts.distinct().size != normalizedTexts.size) {
            penalty += 15
            issues += "${label} 안에서 거의 같은 미션이 반복됩니다."
        }

        var irrelevantMissionCount = 0
        var vagueMissionCount = 0

        texts.forEachIndexed { index, content ->
            val lowered = content.lowercase()
            if (genericBroadPattern.containsMatchIn(content)) {
                penalty += 8
                issues += "${label} ${index + 1}번 미션이 범위가 큽니다: $content"
            }
            if (dailyBudgetMinutes != null && dailyBudgetMinutes <= 60 && shortBudgetBroadPattern.containsMatchIn(content)) {
                penalty += 6
                issues += "${label} ${index + 1}번 미션이 1시간 예산 대비 큽니다: $content"
            }
            if (bundledActionPattern.containsMatchIn(content) || parentheticalChecklistPattern.containsMatchIn(content)) {
                penalty += 6
                issues += "${label} ${index + 1}번 미션이 여러 행동/산출물을 한 번에 묶고 있습니다: $content"
            }
            if (!scopeCuePattern.containsMatchIn(content) && vagueOutcomePattern.containsMatchIn(content)) {
                vagueMissionCount += 1
            }
            if (matchedKeywords(lowered, requiredGroups).isEmpty()) {
                irrelevantMissionCount += 1
            }
        }

        if (vagueMissionCount >= 2) {
            penalty += 8
            issues += "${label}에 범위나 산출물이 모호한 미션이 여러 개 있습니다."
        }
        if (irrelevantMissionCount >= 2) {
            penalty += 10
            issues += "${label}에 현재 시나리오와 직접 연결되지 않는 미션이 여러 개 있습니다. irrelevant=$irrelevantMissionCount/${missions.size}"
        }

        return MissionSetInspection(
            penalty = penalty,
            artifactFamilies = extractArtifactFamilies(joined)
        )
    }

    private fun matchedKeywords(text: String, requiredGroups: List<Set<String>>): Set<String> {
        return requiredGroups.mapNotNull { group ->
            group.firstOrNull { keyword -> text.contains(keyword.lowercase()) }
        }.toSet()
    }

    private fun containsAnyKeyword(text: String, keywords: Set<String>): Boolean {
        return keywords.any { keyword -> text.contains(keyword.lowercase()) }
    }

    private fun extractArtifactFamilies(text: String): Set<String> {
        return artifactFamilyKeywords.mapNotNull { (family, keywords) ->
            if (containsAnyKeyword(text, keywords)) family else null
        }.toSet()
    }

    private fun extractScenarioArtifactFamilies(scenario: ChatRecommendationQaScenario): Set<String> {
        return extractArtifactFamilies(
            listOf(
                scenario.goal,
                scenario.desiredOutcome,
                scenario.recentExperience,
                scenario.subCategory
            ).joinToString(" ").lowercase()
        )
    }

    private fun isBeginnerSkill(text: String): Boolean {
        return listOf("입문", "초보", "처음", "기초", "해본 적 없어", "경험은 부족").any { text.contains(it) }
    }

    private fun isExperiencedSkill(text: String): Boolean {
        return listOf("년차", "실무", "현업", "경험이 있어", "익숙", "중급", "숙련").any { text.contains(it) }
    }

    private fun normalizeMission(text: String): String {
        return text.lowercase()
            .replace(Regex("\\s+"), "")
            .replace(Regex("[()\\[\\],.·:/]"), "")
    }

    private fun extractDailyBudgetMinutes(input: String): Int? {
        val hourMinutePattern = Regex("(\\d+)\\s*시간(?:\\s*(\\d+)\\s*분)?")
        val minutePattern = Regex("(\\d+)\\s*분")

        hourMinutePattern.find(input)?.let { match ->
            val hours = match.groupValues[1].toIntOrNull() ?: 0
            val minutes = match.groupValues[2].toIntOrNull() ?: 0
            return hours * 60 + minutes
        }

        minutePattern.find(input)?.let { match ->
            return match.groupValues[1].toIntOrNull()
        }

        return null
    }
}

internal data class MissionSetInspection(
    val penalty: Int,
    val artifactFamilies: Set<String>
)

private data class QaRunConfig(
    val filter: String?,
    val limit: Int?,
    val parallelism: Int
) {
    companion object {
        fun fromSystemProperties(): QaRunConfig {
            return QaRunConfig(
                filter = System.getProperty("qa.filter")?.takeIf { it.isNotBlank() },
                limit = System.getProperty("qa.limit")?.toIntOrNull(),
                parallelism = System.getProperty("qa.parallelism")?.toIntOrNull()?.coerceAtLeast(1) ?: 2
            )
        }
    }
}

data class ChatRecommendationQaScenario(
    val id: String,
    val title: String,
    val category: String,
    val subCategory: String,
    val goal: String,
    val desiredOutcome: String,
    val skillLevel: String,
    val recentExperience: String,
    val targetPeriod: String,
    val dailyAvailableTime: String,
    val additionalOpinion: String? = null,
    val expectation: ChatRecommendationQaExpectation
) {
    fun toContext(): ChatBotMissionContext {
        return ChatBotMissionContext(
            category = category,
            subCategory = subCategory,
            goal = goal,
            desiredOutcome = desiredOutcome,
            skillLevel = skillLevel,
            recentExperience = recentExperience,
            targetPeriod = targetPeriod,
            dailyAvailableTime = dailyAvailableTime,
            additionalOpinion = additionalOpinion
        )
    }

    fun allContextText(): String {
        return listOfNotNull(
            category,
            subCategory,
            goal,
            desiredOutcome,
            skillLevel,
            recentExperience,
            targetPeriod,
            dailyAvailableTime,
            additionalOpinion
        ).joinToString(" ")
    }
}

data class ChatRecommendationQaExpectation(
    val todayRequiredGroups: List<Set<String>>,
    val nextDayRequiredGroups: List<Set<String>>,
    val forbiddenKeywords: Set<String> = emptySet(),
    val avoidUnmentionedFormats: Boolean = false,
    val avoidUnmentionedFrameworks: Boolean = false,
    val allowedFrameworkKeywords: Set<String> = emptySet()
)

data class QaEvaluation(
    val passed: Boolean,
    val score: Int,
    val issues: List<String>
)

data class QaScenarioResult(
    val id: String,
    val title: String,
    val category: String,
    val subCategory: String,
    val passed: Boolean,
    val score: Int,
    val issues: List<String>,
    val todayMissions: List<String>,
    val nextDayMissions: List<String>
)

data class QaSummaryReport(
    val executedAt: String,
    val total: Int,
    val passed: Int,
    val failed: Int,
    val averageScore: Double,
    val results: List<QaScenarioResult>
)
