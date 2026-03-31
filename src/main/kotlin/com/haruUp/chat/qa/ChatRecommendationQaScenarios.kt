package com.haruUp.chat.qa

object ChatRecommendationQaScenarios {

    fun all(): List<ChatRecommendationQaScenario> {
        val baseScenarios = buildList {
            addAll(experiencedDeveloperPortfolio())
            addAll(jobSeekerBackendDeveloper())
            addAll(studentAppBuilder())
            addAll(plannerBeginner())
            addAll(plannerPortfolio())
            addAll(designerPortfolio())
            addAll(officeAutomation())
            addAll(selfEmployedService())
            addAll(educatorContent())
            addAll(studentResearch())
        }

        return baseScenarios + diversify(baseScenarios)
    }

    private fun experiencedDeveloperPortfolio(): List<ChatRecommendationQaScenario> {
        val expectation = ChatRecommendationQaExpectation(
            todayRequiredGroups = listOf(
                keywords("프로젝트", "성과", "kpi", "github", "readme"),
                keywords("이력서", "면접", "자기소개서", "jd", "포트폴리오")
            ),
            nextDayRequiredGroups = listOf(
                keywords("프로젝트", "성과", "kpi", "github", "readme"),
                keywords("이력서", "면접", "자기소개서", "jd", "포트폴리오")
            ),
            forbiddenKeywords = keywords("hello world", "to-do", "linkedin", "인포그래픽", "신규 서비스", "mvp"),
            avoidUnmentionedFormats = true,
            avoidUnmentionedFrameworks = true
        )

        return listOf(
            scenario(
                id = "01_dev_portfolio_a",
                title = "서비스 이직 포트폴리오 준비",
                category = "직장인",
                subCategory = "개발자",
                goal = "서비스 회사로 이직해서 사용자 가까운 기능을 만드는 일을 하고 싶어",
                desiredOutcome = "이직용 포트폴리오를 정리해서 지원할 때 바로 써먹고 싶어",
                skillLevel = "지금 백엔드 유지보수 위주로 3년차 개발자로 일하고 있어",
                recentExperience = "운영 이슈 대응과 레거시 API 수정은 많이 해봤지만 포트폴리오로 정리해본 적은 거의 없어",
                targetPeriod = "3개월 안에 이직 지원을 시작하고 싶어",
                dailyAvailableTime = "평일 하루 3시간 정도는 투자할 수 있어",
                additionalOpinion = "너무 교과서적인 미션보다는 실제 이직 준비에 도움되는 걸 원해",
                expectation = expectation
            ),
            scenario(
                id = "02_dev_portfolio_b",
                title = "경력 정리형 이직 준비",
                category = "직장인",
                subCategory = "개발자",
                goal = "운영성 개발 말고 제품 중심 팀으로 옮기고 싶어",
                desiredOutcome = "내 경력과 프로젝트를 보여줄 포트폴리오 묶음을 만들고 싶어",
                skillLevel = "서버 개발 4년차인데 문서화나 회고는 약한 편이야",
                recentExperience = "최근 2년은 장애 대응, 배치 수정, DB 쿼리 최적화 위주로 일했어",
                targetPeriod = "두 달 반 안에는 이력서와 포트폴리오를 같이 완성하고 싶어",
                dailyAvailableTime = "주중에는 2시간 반 정도, 주말에는 조금 더 가능해",
                additionalOpinion = "포트폴리오에 쓸 수 있는 실제 산출물이 남는 미션이면 좋겠어",
                expectation = expectation
            ),
            scenario(
                id = "03_dev_portfolio_c",
                title = "서비스 개발자 전환 준비",
                category = "직장인",
                subCategory = "개발자",
                goal = "유지보수 중심 회사에서 벗어나 서비스 개발 문화가 있는 곳으로 가고 싶어",
                desiredOutcome = "지원할 때 바로 첨부할 수 있는 포트폴리오와 자기소개 재료를 만들고 싶어",
                skillLevel = "실무 3년 조금 넘었고 Java랑 SQL은 익숙한데 나를 어필하는 건 약해",
                recentExperience = "배치 오류 수정, 관리자 페이지 개선, 로그 분석은 자주 했어",
                targetPeriod = "3개월 정도 안에 채용 공고를 보고 지원까지 하고 싶어",
                dailyAvailableTime = "퇴근 후 3시간 정도는 확보 가능해",
                additionalOpinion = "면접 준비랑 포트폴리오 준비가 같이 이어졌으면 좋겠어",
                expectation = expectation
            ),
            scenario(
                id = "04_dev_portfolio_d",
                title = "실무 성과 정리형 포트폴리오",
                category = "직장인",
                subCategory = "개발자",
                goal = "실무에서 해온 걸 더 설득력 있게 정리해서 이직하고 싶어",
                desiredOutcome = "프로젝트 성과가 보이는 포트폴리오 초안을 만들고 싶어",
                skillLevel = "백엔드 5년차인데 개인 프로젝트는 거의 안 했어",
                recentExperience = "최근에는 모니터링 지표 개선, 쿼리 성능 개선, 장애 원인 파악을 많이 했어",
                targetPeriod = "4개월 안에는 회사 몇 군데 지원할 수 있을 정도로 만들고 싶어",
                dailyAvailableTime = "평일 2시간에서 3시간 사이 정도 가능해",
                additionalOpinion = "새 장난감 프로젝트보다 기존 경력을 살리는 방향이 좋겠어",
                expectation = expectation
            ),
            scenario(
                id = "05_dev_portfolio_e",
                title = "지원 문서 일체 준비",
                category = "직장인",
                subCategory = "개발자",
                goal = "서비스 회사 백엔드 포지션으로 넘어가고 싶어",
                desiredOutcome = "포트폴리오, 이력서, 지원 문서에 쓸 핵심 내용을 정리하고 싶어",
                skillLevel = "3년차 서버 개발자고 운영 업무 비중이 꽤 높아",
                recentExperience = "API 수정, DB 인덱스 조정, 배치 일정 조정 같은 업무를 계속 했어",
                targetPeriod = "3개월 안에 최소 5곳은 지원하고 싶어",
                dailyAvailableTime = "하루 3시간 정도는 꾸준히 쓸 수 있어",
                additionalOpinion = "너무 뜬구름 잡는 미션 말고 바로 문서에 넣을 수 있는 걸 원해",
                expectation = expectation
            )
        )
    }

    private fun jobSeekerBackendDeveloper(): List<ChatRecommendationQaScenario> {
        val expectation = ChatRecommendationQaExpectation(
            todayRequiredGroups = listOf(
                keywords("spring", "api", "jpa", "db", "sql", "테스트", "엔드포인트"),
                keywords("포트폴리오", "프로젝트", "readme", "정리", "문서")
            ),
            nextDayRequiredGroups = listOf(
                keywords("spring", "api", "jpa", "db", "sql", "테스트", "엔드포인트"),
                keywords("포트폴리오", "프로젝트", "readme", "정리", "문서")
            ),
            forbiddenKeywords = keywords("react", "flutter", "swiftui", "todo", "hello world"),
            avoidUnmentionedFormats = true,
            avoidUnmentionedFrameworks = false,
            allowedFrameworkKeywords = keywords("spring", "java", "kotlin")
        )

        return listOf(
            scenario(
                id = "06_backend_jobseeker_a",
                title = "스프링 백엔드 포트폴리오 준비",
                category = "취준생",
                subCategory = "개발자",
                goal = "스프링 백엔드 직무로 취업하고 싶어",
                desiredOutcome = "지원할 때 보여줄 백엔드 프로젝트 포트폴리오를 만들고 싶어",
                skillLevel = "학원에서 스프링 기초는 배웠고 개인 프로젝트는 아직 미완성인 수준이야",
                recentExperience = "게시판 CRUD랑 로그인 기능 정도는 구현해봤어",
                targetPeriod = "4개월 안에 원서 넣을 수준까지 만들고 싶어",
                dailyAvailableTime = "하루 2시간 정도는 쓸 수 있어",
                additionalOpinion = "프론트보다 백엔드 실력이 드러나는 쪽이면 좋겠어",
                expectation = expectation
            ),
            scenario(
                id = "07_backend_jobseeker_b",
                title = "자바 스프링 취업 준비",
                category = "취준생",
                subCategory = "개발자",
                goal = "자바 스프링 기반 백엔드 회사로 들어가고 싶어",
                desiredOutcome = "깃허브와 포트폴리오에 넣을 백엔드 결과물을 정리하고 싶어",
                skillLevel = "입문은 넘겼는데 실무 수준이라고 하긴 어려워",
                recentExperience = "Spring Boot로 간단한 REST API랑 JPA 연동은 해봤어",
                targetPeriod = "3개월 반 안에는 취업 준비를 본격화하고 싶어",
                dailyAvailableTime = "평일에 2시간, 주말에는 3시간 정도 가능해",
                additionalOpinion = "테스트 코드나 문서화도 같이 챙기고 싶어",
                expectation = expectation
            ),
            scenario(
                id = "08_backend_jobseeker_c",
                title = "백엔드 학습 결과 정리",
                category = "취준생",
                subCategory = "개발자",
                goal = "백엔드 개발자로 취업하는 게 목표야",
                desiredOutcome = "스프링 프로젝트를 제대로 정리해서 지원 자료로 만들고 싶어",
                skillLevel = "기초는 알고 있는데 아직 실전 프로젝트 경험은 부족해",
                recentExperience = "토이 프로젝트에서 회원가입, 로그인, 게시글 API까지 만들어봤어",
                targetPeriod = "5개월 안에는 백엔드 포지션 지원을 시작하고 싶어",
                dailyAvailableTime = "하루 2시간 반 정도 가능해",
                additionalOpinion = "코드만이 아니라 설명도 남는 방향이면 좋겠어",
                expectation = expectation
            ),
            scenario(
                id = "09_backend_jobseeker_d",
                title = "취업용 백엔드 포트폴리오 고도화",
                category = "취준생",
                subCategory = "개발자",
                goal = "백엔드 주니어 포지션으로 들어가고 싶어",
                desiredOutcome = "지원 링크로 바로 보여줄 수 있는 백엔드 프로젝트 자료를 만들고 싶어",
                skillLevel = "스프링과 SQL은 어느 정도 익숙하지만 아직 자신감은 부족해",
                recentExperience = "예외 처리, validation, 페이징 같은 기능은 직접 붙여봤어",
                targetPeriod = "4개월 정도를 보고 준비 중이야",
                dailyAvailableTime = "하루에 2시간에서 3시간은 투자할 수 있어",
                additionalOpinion = "너무 프론트 중심 과제는 빼고 싶어",
                expectation = expectation
            ),
            scenario(
                id = "10_backend_jobseeker_e",
                title = "실전형 백엔드 지원 준비",
                category = "취준생",
                subCategory = "개발자",
                goal = "실제 서비스 백엔드 팀에 합류하고 싶어",
                desiredOutcome = "지원 전에 백엔드 프로젝트 설명과 문서를 정리하고 싶어",
                skillLevel = "스프링 부트 프로젝트 경험은 조금 있지만 완성도 높은 포트폴리오는 아직 없어",
                recentExperience = "JPA 연관관계, 간단한 테스트 코드, MySQL 연결 정도는 해봤어",
                targetPeriod = "3개월 안에 1차 지원을 해보고 싶어",
                dailyAvailableTime = "평일 2시간 정도는 안정적으로 가능해",
                additionalOpinion = "API 설명이나 README가 잘 남는 미션이면 좋겠어",
                expectation = expectation
            )
        )
    }

    private fun studentAppBuilder(): List<ChatRecommendationQaScenario> {
        val expectation = ChatRecommendationQaExpectation(
            todayRequiredGroups = listOf(
                keywords("앱", "화면", "네비게이션", "상태", "로그인", "api"),
                keywords("프로토타입", "기능", "시나리오", "테스트", "정리")
            ),
            nextDayRequiredGroups = listOf(
                keywords("앱", "화면", "네비게이션", "상태", "로그인", "api"),
                keywords("프로토타입", "기능", "시나리오", "테스트", "정리")
            ),
            forbiddenKeywords = keywords("spring", "jpa", "nestjs", "linkedin"),
            avoidUnmentionedFormats = true,
            avoidUnmentionedFrameworks = false,
            allowedFrameworkKeywords = keywords("flutter", "react native")
        )

        return listOf(
            scenario(
                id = "11_student_app_a",
                title = "학생 앱 MVP 준비",
                category = "학생",
                subCategory = "개발자",
                goal = "대학생 대상 앱을 만들어서 공모전이랑 포트폴리오에 같이 쓰고 싶어",
                desiredOutcome = "2달 안에 핵심 기능이 보이는 앱 프로토타입을 만들고 싶어",
                skillLevel = "웹은 조금 해봤는데 앱 개발은 거의 처음이야",
                recentExperience = "React로 작은 웹 화면은 만들어봤고 모바일은 세팅만 해봤어",
                targetPeriod = "2개월 안에 MVP 느낌까지는 가고 싶어",
                dailyAvailableTime = "하루 2시간 정도는 투자할 수 있어",
                additionalOpinion = "실습 위주였으면 좋겠고 너무 큰 단위는 부담돼",
                expectation = expectation
            ),
            scenario(
                id = "12_student_app_b",
                title = "플러터 앱 프로젝트 준비",
                category = "학생",
                subCategory = "개발자",
                goal = "플러터로 학생 생활 관리 앱을 만들어보고 싶어",
                desiredOutcome = "포트폴리오에 넣을 수 있는 앱 화면과 동작 흐름을 만들고 싶어",
                skillLevel = "플러터는 입문 수준이고 다트 문법만 조금 본 상태야",
                recentExperience = "튜토리얼 따라 하면서 화면 1개랑 버튼 동작 정도는 해봤어",
                targetPeriod = "3개월 안에 시연 가능한 수준이 목표야",
                dailyAvailableTime = "하루 1시간 반에서 2시간 정도 가능해",
                additionalOpinion = "막히지 않게 하루에 하나씩 닫히는 미션이면 좋겠어",
                expectation = expectation
            ),
            scenario(
                id = "13_student_app_c",
                title = "모바일 앱 입문 포트폴리오",
                category = "학생",
                subCategory = "개발자",
                goal = "모바일 앱 개발 경험을 만들어 인턴 지원에 쓰고 싶어",
                desiredOutcome = "앱 기능 흐름이 보이는 결과물을 만들고 싶어",
                skillLevel = "웹 프론트 경험은 조금 있는데 모바일 상태관리는 아직 낯설어",
                recentExperience = "React Native로 화면 2개 정도 연결해본 적은 있어",
                targetPeriod = "3개월 정도 준비하고 싶어",
                dailyAvailableTime = "하루 2시간 가능해",
                additionalOpinion = "사용자 흐름이 자연스럽게 이어지는 미션이면 좋겠어",
                expectation = expectation
            ),
            scenario(
                id = "14_student_app_d",
                title = "학생 대상 서비스 앱 제작",
                category = "학생",
                subCategory = "개발자",
                goal = "학생들끼리 쓰는 일정 공유 앱을 만들어보고 싶어",
                desiredOutcome = "앱 화면, 기능, 설명까지 남는 프로젝트를 만들고 싶어",
                skillLevel = "앱은 처음이고 JavaScript는 어느 정도 익숙해",
                recentExperience = "웹에서 로그인 폼이랑 API 호출은 해본 적 있어",
                targetPeriod = "2달 반 안에 핵심 기능을 보여주고 싶어",
                dailyAvailableTime = "평일 하루 2시간 정도 가능해",
                additionalOpinion = "너무 백엔드 쪽으로 새지 않았으면 좋겠어",
                expectation = expectation
            ),
            scenario(
                id = "15_student_app_e",
                title = "앱 공모전 준비",
                category = "학생",
                subCategory = "개발자",
                goal = "앱 공모전에 낼 수 있을 정도로 기본 프로토타입을 만들고 싶어",
                desiredOutcome = "화면 흐름이 살아있는 앱 결과물을 만들고 싶어",
                skillLevel = "React Native는 기초만 알고 있고 아직 프로젝트 경험은 짧아",
                recentExperience = "상태관리 라이브러리는 안 써봤고 화면 배치 정도만 해봤어",
                targetPeriod = "10주 정도 보고 있어",
                dailyAvailableTime = "하루 2시간 반은 투자 가능해",
                additionalOpinion = "실제로 앱이 조금씩 완성되는 느낌이면 좋겠어",
                expectation = expectation
            )
        )
    }

    private fun plannerBeginner(): List<ChatRecommendationQaScenario> {
        val expectation = ChatRecommendationQaExpectation(
            todayRequiredGroups = listOf(
                keywords("사용자", "문제", "가설", "기능", "요구사항", "시나리오"),
                keywords("페르소나", "와이어프레임", "화면", "경쟁사", "흐름")
            ),
            nextDayRequiredGroups = listOf(
                keywords("사용자", "문제", "가설", "기능", "요구사항", "시나리오"),
                keywords("페르소나", "와이어프레임", "화면", "경쟁사", "흐름")
            ),
            forbiddenKeywords = keywords("react", "spring", "github", "sql", "api"),
            avoidUnmentionedFormats = true,
            avoidUnmentionedFrameworks = true
        )

        return listOf(
            scenario(
                id = "16_planner_beginner_a",
                title = "서비스 기획 입문",
                category = "직장인",
                subCategory = "기획자",
                goal = "언젠가 내 서비스를 기획할 수 있는 역량을 만들고 싶어",
                desiredOutcome = "서비스 기획 포트폴리오까지는 아니더라도 기획 산출물을 남기고 싶어",
                skillLevel = "기획을 해본 적은 없고 아이디어만 많은 편이야",
                recentExperience = "노션에 서비스 아이디어 메모만 조금 해본 정도야",
                targetPeriod = "4개월 안에 기본기를 만들고 싶어",
                dailyAvailableTime = "하루 1시간 정도는 가능해",
                additionalOpinion = "문서가 길어지는 것보다 한 장씩 끝나는 미션이면 좋겠어",
                expectation = expectation
            ),
            scenario(
                id = "17_planner_beginner_b",
                title = "기획 기초 다지기",
                category = "직장인",
                subCategory = "기획자",
                goal = "서비스 기획 직무로 전환하고 싶어",
                desiredOutcome = "기획서, 화면 흐름, 사용자 문제 정의 같은 결과물을 만들고 싶어",
                skillLevel = "실무 기획 경험은 전혀 없고 입문자라고 보면 돼",
                recentExperience = "아이디어를 말로만 설명해본 적은 있는데 문서화는 거의 안 했어",
                targetPeriod = "5개월 안에는 전환 준비를 하고 싶어",
                dailyAvailableTime = "평일 하루 1시간 정도 투자할 수 있어",
                additionalOpinion = "서비스를 뜬구름처럼 잡지 않게 도와주는 미션이면 좋겠어",
                expectation = expectation
            ),
            scenario(
                id = "18_planner_beginner_c",
                title = "내 서비스 기획 연습",
                category = "직장인",
                subCategory = "기획자",
                goal = "내가 떠올린 서비스 아이디어를 기획 언어로 정리하고 싶어",
                desiredOutcome = "사용자 문제와 핵심 기능이 보이는 기획 초안을 만들고 싶어",
                skillLevel = "기획서는 안 써봤고 발표 자료만 몇 번 만들어봤어",
                recentExperience = "간단한 시장 조사 메모랑 아이디어 스케치 정도는 해봤어",
                targetPeriod = "3개월 반 정도 준비하고 싶어",
                dailyAvailableTime = "하루 1시간 반 정도 가능해",
                additionalOpinion = "경쟁사 조사나 와이어프레임도 너무 크지 않게 나눠졌으면 좋겠어",
                expectation = expectation
            ),
            scenario(
                id = "19_planner_beginner_d",
                title = "주니어 기획 역량 만들기",
                category = "직장인",
                subCategory = "기획자",
                goal = "기획자로 커리어를 옮기기 전에 기본 산출물을 만들어보고 싶어",
                desiredOutcome = "사용자 흐름과 요구사항이 담긴 문서를 남기고 싶어",
                skillLevel = "아직은 비전공자 입문 단계야",
                recentExperience = "서비스 화면 캡처 보면서 기능 분석 메모 정도는 해봤어",
                targetPeriod = "4개월 안에 기획 포지션 지원 준비를 시작하고 싶어",
                dailyAvailableTime = "하루 1시간에서 1시간 반 정도 가능해",
                additionalOpinion = "기획자가 실제로 만드는 산출물 느낌이면 좋겠어",
                expectation = expectation
            ),
            scenario(
                id = "20_planner_beginner_e",
                title = "아이디어 문서화 연습",
                category = "직장인",
                subCategory = "기획자",
                goal = "막연한 아이디어를 기획 문서로 바꾸는 연습을 하고 싶어",
                desiredOutcome = "화면 흐름이나 기능 구조가 보이는 초안을 만들고 싶어",
                skillLevel = "기획 쪽은 처음이고 협업 툴도 익숙하진 않아",
                recentExperience = "메모 앱에 서비스 아이디어만 틈틈이 적어놨어",
                targetPeriod = "3개월 안에 결과물을 하나씩 쌓고 싶어",
                dailyAvailableTime = "하루 1시간 정도 투자 계획이야",
                additionalOpinion = "무조건 실습형이면 좋겠어",
                expectation = expectation
            )
        )
    }

    private fun plannerPortfolio(): List<ChatRecommendationQaScenario> {
        val expectation = ChatRecommendationQaExpectation(
            todayRequiredGroups = listOf(
                keywords("포트폴리오", "케이스", "프로젝트", "문제", "요구사항"),
                keywords("이력서", "자기소개서", "기획서", "사용자", "흐름")
            ),
            nextDayRequiredGroups = listOf(
                keywords("포트폴리오", "케이스", "프로젝트", "문제", "요구사항"),
                keywords("이력서", "자기소개서", "기획서", "사용자", "흐름")
            ),
            forbiddenKeywords = keywords("react", "spring", "github", "mvp 완성", "동영상", "pdf"),
            avoidUnmentionedFormats = true,
            avoidUnmentionedFrameworks = true
        )

        return listOf(
            scenario(
                id = "21_planner_portfolio_a",
                title = "기획자 취준 포트폴리오",
                category = "취준생",
                subCategory = "기획자",
                goal = "서비스 기획자 취업을 준비하고 싶어",
                desiredOutcome = "지원할 때 보여줄 기획 포트폴리오를 만들고 싶어",
                skillLevel = "관련 인턴은 없고 동아리에서 서비스 아이디어 발표만 해봤어",
                recentExperience = "간단한 기획안 발표 자료랑 화면 스케치 정도는 해봤어",
                targetPeriod = "4개월 안에 지원서 넣을 정도로 만들고 싶어",
                dailyAvailableTime = "하루 2시간은 투자할 수 있어",
                additionalOpinion = "케이스 스터디나 문제 정의가 잘 드러나는 미션이면 좋아",
                expectation = expectation
            ),
            scenario(
                id = "22_planner_portfolio_b",
                title = "서비스 기획 취업 준비",
                category = "취준생",
                subCategory = "기획자",
                goal = "주니어 서비스 기획 포지션에 지원하고 싶어",
                desiredOutcome = "기획 포트폴리오와 이력서에 넣을 재료를 만들고 싶어",
                skillLevel = "아직 실무 경험은 없고 학교 프로젝트 경험만 있어",
                recentExperience = "사용자 인터뷰 정리랑 요구사항 문서 초안 정도는 만들어봤어",
                targetPeriod = "3개월 정도 준비하고 싶어",
                dailyAvailableTime = "하루 2시간 반 정도 가능해",
                additionalOpinion = "문제 정의랑 핵심 기능이 연결되는 방향이면 좋겠어",
                expectation = expectation
            ),
            scenario(
                id = "23_planner_portfolio_c",
                title = "기획 포지션 지원 자료 만들기",
                category = "취준생",
                subCategory = "기획자",
                goal = "서비스 기획 직무 지원서를 본격적으로 넣고 싶어",
                desiredOutcome = "프로젝트형 포트폴리오와 자기소개서 재료를 모으고 싶어",
                skillLevel = "학교 팀플에서 PM 역할은 했지만 정식 기획자로 일해본 적은 없어",
                recentExperience = "기능 정의, 화면 구조 메모, 발표 자료 작성은 몇 번 해봤어",
                targetPeriod = "3개월 반 안에는 지원을 시작할 생각이야",
                dailyAvailableTime = "하루 2시간 정도 가능해",
                additionalOpinion = "과하게 큰 문서보다는 포트폴리오에 바로 넣을 단위면 좋겠어",
                expectation = expectation
            ),
            scenario(
                id = "24_planner_portfolio_d",
                title = "주니어 기획자 이력서 준비",
                category = "취준생",
                subCategory = "기획자",
                goal = "서비스 기획자 이력서를 더 설득력 있게 만들고 싶어",
                desiredOutcome = "기획 프로젝트 결과물이 담긴 포트폴리오 초안을 만들고 싶어",
                skillLevel = "인턴 경험은 없지만 팀 프로젝트는 꾸준히 했어",
                recentExperience = "페르소나, 핵심 사용자 흐름, 화면 구조 정도는 직접 작성해봤어",
                targetPeriod = "4개월 안에 포트폴리오 완성하고 싶어",
                dailyAvailableTime = "하루 2시간 정도 투자 가능해",
                additionalOpinion = "실제로 지원 문서에 연결되는 미션이면 좋겠어",
                expectation = expectation
            ),
            scenario(
                id = "25_planner_portfolio_e",
                title = "기획 포트폴리오 사례 정리",
                category = "취준생",
                subCategory = "기획자",
                goal = "포트폴리오에서 문제 해결 과정을 잘 보여주고 싶어",
                desiredOutcome = "케이스 스터디 느낌의 기획 포트폴리오를 만들고 싶어",
                skillLevel = "주니어 수준이고 아직 포트폴리오 구조를 못 잡았어",
                recentExperience = "노션으로 프로젝트 회고 메모는 조금 해본 적이 있어",
                targetPeriod = "3개월 안에 제출 가능한 수준으로 만들고 싶어",
                dailyAvailableTime = "하루 2시간 정도 가능해",
                additionalOpinion = "문제 정의부터 솔루션까지 흐름이 보이면 좋겠어",
                expectation = expectation
            )
        )
    }

    private fun designerPortfolio(): List<ChatRecommendationQaScenario> {
        val expectation = ChatRecommendationQaExpectation(
            todayRequiredGroups = listOf(
                keywords("화면", "와이어프레임", "ux", "ui", "사용자 흐름", "시안"),
                keywords("포트폴리오", "케이스", "피드백", "디자인 시스템", "문제")
            ),
            nextDayRequiredGroups = listOf(
                keywords("화면", "와이어프레임", "ux", "ui", "사용자 흐름", "시안"),
                keywords("포트폴리오", "케이스", "피드백", "디자인 시스템", "문제")
            ),
            forbiddenKeywords = keywords("spring", "api", "sql", "github", "jpa"),
            avoidUnmentionedFormats = true,
            avoidUnmentionedFrameworks = true
        )

        return listOf(
            scenario(
                id = "26_designer_portfolio_a",
                title = "UIUX 포트폴리오 준비",
                category = "직장인",
                subCategory = "디자이너",
                goal = "uiux 디자이너 포지션으로 이직하고 싶어",
                desiredOutcome = "포트폴리오에 들어갈 케이스 스터디를 정리하고 싶어",
                skillLevel = "실무 2년차인데 포트폴리오는 오래 업데이트를 못 했어",
                recentExperience = "운영 중인 화면 개선, 배너 디자인, 간단한 사용자 흐름 정리는 해봤어",
                targetPeriod = "3개월 안에 포트폴리오 업데이트를 끝내고 싶어",
                dailyAvailableTime = "하루 2시간 정도 가능해",
                additionalOpinion = "단순 비주얼보다 문제 해결이 보였으면 좋겠어",
                expectation = expectation
            ),
            scenario(
                id = "27_designer_portfolio_b",
                title = "케이스 스터디 정리",
                category = "직장인",
                subCategory = "디자이너",
                goal = "서비스 디자인 쪽으로 더 좋은 회사로 옮기고 싶어",
                desiredOutcome = "케이스 스터디 중심 포트폴리오를 다시 만들고 싶어",
                skillLevel = "2년 정도 서비스 디자인 경험이 있어",
                recentExperience = "화면 리디자인, 사용자 피드백 반영, 컴포넌트 정리는 조금 해봤어",
                targetPeriod = "4개월 안에 이직 지원을 시작하고 싶어",
                dailyAvailableTime = "하루 2시간 반 가능해",
                additionalOpinion = "결과물뿐 아니라 의사결정 과정이 보였으면 좋겠어",
                expectation = expectation
            ),
            scenario(
                id = "28_designer_portfolio_c",
                title = "디자인 산출물 체계화",
                category = "직장인",
                subCategory = "디자이너",
                goal = "운영 디자인에서 서비스 프로덕트 디자인으로 넘어가고 싶어",
                desiredOutcome = "포트폴리오용 케이스와 화면 자료를 체계적으로 정리하고 싶어",
                skillLevel = "피그마는 익숙하고 실무 3년차야",
                recentExperience = "화면 개선 제안서, 사용자 흐름 스케치, 컴포넌트 문서화는 해봤어",
                targetPeriod = "3개월 반 정도 준비하려고 해",
                dailyAvailableTime = "하루 2시간 정도 투자 가능해",
                additionalOpinion = "하루 단위로 닫히는 디자인 미션이면 좋겠어",
                expectation = expectation
            ),
            scenario(
                id = "29_designer_portfolio_d",
                title = "프로덕트 디자인 이직 준비",
                category = "직장인",
                subCategory = "디자이너",
                goal = "프로덕트 디자이너 이직 준비를 하고 싶어",
                desiredOutcome = "포트폴리오와 이력서에 쓸 디자인 근거를 정리하고 싶어",
                skillLevel = "주니어 후반 정도 실력이라고 생각해",
                recentExperience = "리뉴얼 화면, AB 테스트 제안, 사용자 반응 메모는 해봤어",
                targetPeriod = "3개월 안에 지원 가능한 상태가 목표야",
                dailyAvailableTime = "하루 2시간 정도 쓸 수 있어",
                additionalOpinion = "피드백이나 회고도 같이 남으면 좋겠어",
                expectation = expectation
            ),
            scenario(
                id = "30_designer_portfolio_e",
                title = "디자인 포트폴리오 업데이트",
                category = "직장인",
                subCategory = "디자이너",
                goal = "서비스 디자인 포트폴리오를 다시 정비하고 싶어",
                desiredOutcome = "면접에서 설명하기 좋은 사례 중심 포트폴리오를 만들고 싶어",
                skillLevel = "실무 경험은 있는데 말로 설명하는 게 약한 편이야",
                recentExperience = "최근에는 기존 화면 개선과 사용자 피드백 정리를 주로 했어",
                targetPeriod = "4개월 안에는 새 포트폴리오를 완성하고 싶어",
                dailyAvailableTime = "하루 2시간에서 3시간 사이 가능해",
                additionalOpinion = "너무 툴 사용법 위주 말고 생각이 보이는 미션이면 좋겠어",
                expectation = expectation
            )
        )
    }

    private fun officeAutomation(): List<ChatRecommendationQaScenario> {
        val expectation = ChatRecommendationQaExpectation(
            todayRequiredGroups = listOf(
                keywords("엑셀", "함수", "피벗", "시트", "보고서", "데이터", "매크로"),
                keywords("업무", "자동화", "시간", "정리", "효율")
            ),
            nextDayRequiredGroups = listOf(
                keywords("엑셀", "함수", "피벗", "시트", "보고서", "데이터", "매크로"),
                keywords("업무", "자동화", "시간", "정리", "효율")
            ),
            forbiddenKeywords = keywords("github", "api", "spring", "react", "포트폴리오"),
            avoidUnmentionedFormats = false,
            avoidUnmentionedFrameworks = true
        )

        return listOf(
            scenario(
                id = "31_office_automation_a",
                title = "엑셀 자동화 역량 향상",
                category = "직장인",
                subCategory = "사무직",
                goal = "반복되는 엑셀 업무를 줄이고 싶어",
                desiredOutcome = "실무에서 바로 쓰는 자동화 템플릿이나 정리 방식을 만들고 싶어",
                skillLevel = "기본 함수는 아는데 고급 기능은 약해",
                recentExperience = "매주 보고서 만들 때 필터, 정렬, 단순 수식 정도만 써봤어",
                targetPeriod = "2개월 안에 체감될 정도로 빨라지고 싶어",
                dailyAvailableTime = "하루 1시간 정도 가능해",
                additionalOpinion = "업무에 바로 적용할 수 있는 미션이면 좋겠어",
                expectation = expectation
            ),
            scenario(
                id = "32_office_automation_b",
                title = "보고서 업무 정리",
                category = "직장인",
                subCategory = "사무직",
                goal = "보고서 만들 때 매번 헤매는 시간을 줄이고 싶어",
                desiredOutcome = "보고서용 엑셀 구조와 정리 기준을 만들고 싶어",
                skillLevel = "엑셀은 중급 아래 정도라고 생각해",
                recentExperience = "월간 실적 정리, 피벗 테이블 기초 사용 정도는 해봤어",
                targetPeriod = "3개월 안에 업무 시간이 줄었으면 좋겠어",
                dailyAvailableTime = "하루 1시간 정도 투자 가능해",
                additionalOpinion = "너무 개발자스러운 과제는 빼고 싶어",
                expectation = expectation
            ),
            scenario(
                id = "33_office_automation_c",
                title = "데이터 정리 효율화",
                category = "직장인",
                subCategory = "사무직",
                goal = "수작업 정리를 줄이고 데이터를 더 깔끔하게 다루고 싶어",
                desiredOutcome = "엑셀 기반 업무 자동화 습관을 만들고 싶어",
                skillLevel = "sum, if 정도는 쓰지만 아직 복잡한 건 어려워",
                recentExperience = "사내 매출 자료 정리와 간단한 표 작성은 자주 했어",
                targetPeriod = "10주 정도 생각하고 있어",
                dailyAvailableTime = "하루 1시간 가능해",
                additionalOpinion = "바로 써먹을 수 있게 시트나 함수 단위였으면 좋겠어",
                expectation = expectation
            ),
            scenario(
                id = "34_office_automation_d",
                title = "실무 엑셀 숙련도 올리기",
                category = "직장인",
                subCategory = "사무직",
                goal = "실무에서 엑셀을 더 빠르고 정확하게 쓰고 싶어",
                desiredOutcome = "자주 쓰는 보고서와 데이터 정리 방식의 틀을 만들고 싶어",
                skillLevel = "기초는 되는데 실무에서 막상 쓰려면 시간이 많이 걸려",
                recentExperience = "vlookup은 따라 해봤고 표 정리는 꾸준히 해왔어",
                targetPeriod = "2개월 반 정도 안에 개선하고 싶어",
                dailyAvailableTime = "평일 하루 1시간 정도 가능해",
                additionalOpinion = "짧게 끝나는 미션이면 꾸준히 하기에 좋겠어",
                expectation = expectation
            ),
            scenario(
                id = "35_office_automation_e",
                title = "반복 업무 단축",
                category = "직장인",
                subCategory = "사무직",
                goal = "반복 입력과 집계 업무를 덜 힘들게 하고 싶어",
                desiredOutcome = "내 업무에 맞는 정리 방식과 엑셀 팁을 쌓고 싶어",
                skillLevel = "완전 초보는 아니지만 자동화는 거의 안 해봤어",
                recentExperience = "주간 보고서, 거래처 목록 정리, 기본 그래프 정도는 만들어봤어",
                targetPeriod = "3개월 안에 업무 체감 속도를 높이고 싶어",
                dailyAvailableTime = "하루 1시간 정도 쓸 수 있어",
                additionalOpinion = "매크로나 피벗도 필요하면 조금씩 배우고 싶어",
                expectation = expectation
            )
        )
    }

    private fun selfEmployedService(): List<ChatRecommendationQaScenario> {
        val expectation = ChatRecommendationQaExpectation(
            todayRequiredGroups = listOf(
                keywords("고객", "후기", "응대", "문의", "서비스", "체크리스트", "프로세스"),
                keywords("매출", "운영", "정리", "개선", "반복")
            ),
            nextDayRequiredGroups = listOf(
                keywords("고객", "후기", "응대", "문의", "서비스", "체크리스트", "프로세스"),
                keywords("매출", "운영", "정리", "개선", "반복")
            ),
            forbiddenKeywords = keywords("github", "spring", "react", "포트폴리오", "api"),
            avoidUnmentionedFormats = false,
            avoidUnmentionedFrameworks = true
        )

        return listOf(
            scenario(
                id = "36_self_service_a",
                title = "자영업 운영 정리",
                category = "자영업",
                subCategory = "서비스직",
                goal = "가게 운영을 좀 더 체계적으로 하고 싶어",
                desiredOutcome = "고객 응대와 운영 정리에 도움이 되는 체크리스트를 만들고 싶어",
                skillLevel = "실무 감각은 있는데 문서로 정리하는 건 약한 편이야",
                recentExperience = "예약 문의 답변, 후기 확인, 재방문 고객 관리 정도는 혼자 해왔어",
                targetPeriod = "2개월 안에 운영 방식을 좀 더 안정화하고 싶어",
                dailyAvailableTime = "하루 1시간 정도는 투자 가능해",
                additionalOpinion = "현장에서 바로 쓰는 방식이면 좋아",
                expectation = expectation
            ),
            scenario(
                id = "37_self_service_b",
                title = "고객 응대 개선",
                category = "자영업",
                subCategory = "서비스직",
                goal = "고객 문의 대응이 더 매끄러워졌으면 좋겠어",
                desiredOutcome = "반복 문의 대응 문구와 운영 프로세스를 정리하고 싶어",
                skillLevel = "서비스 운영은 익숙하지만 체계화는 아직 부족해",
                recentExperience = "카카오톡 문의 답변과 후기 관리, 예약 일정 조정은 계속 하고 있어",
                targetPeriod = "8주 안에 눈에 띄게 정리됐으면 좋겠어",
                dailyAvailableTime = "하루 1시간 정도 쓸 수 있어",
                additionalOpinion = "문구나 체크리스트처럼 바로 꺼내 쓸 수 있으면 좋아",
                expectation = expectation
            ),
            scenario(
                id = "38_self_service_c",
                title = "재방문 운영 루틴 만들기",
                category = "자영업",
                subCategory = "서비스직",
                goal = "재방문 고객 관리 루틴을 만들고 싶어",
                desiredOutcome = "고객 응대와 운영 흐름을 정리한 자료를 만들고 싶어",
                skillLevel = "운영 경험은 충분하지만 분석적으로 본 적은 많지 않아",
                recentExperience = "단골 고객 메모, 후기 답변, 예약 일정 관리는 종종 정리하고 있어",
                targetPeriod = "3개월 안에 운영 습관을 잡고 싶어",
                dailyAvailableTime = "하루 1시간 정도 가능해",
                additionalOpinion = "너무 큰 전략보다 오늘 바로 바꿀 수 있는 게 좋겠어",
                expectation = expectation
            ),
            scenario(
                id = "39_self_service_d",
                title = "가게 서비스 프로세스 정리",
                category = "자영업",
                subCategory = "서비스직",
                goal = "매장 운영이 사람에 따라 들쑥날쑥한 걸 줄이고 싶어",
                desiredOutcome = "응대 기준과 운영 체크리스트를 정리하고 싶어",
                skillLevel = "경험은 있지만 문서화는 거의 처음이야",
                recentExperience = "오픈 마감 루틴, 고객 응대, 문의 대응은 계속 반복하고 있어",
                targetPeriod = "2개월 반 정도 생각 중이야",
                dailyAvailableTime = "하루 1시간 정도 가능해",
                additionalOpinion = "운영 흐름이 한눈에 보이면 좋겠어",
                expectation = expectation
            ),
            scenario(
                id = "40_self_service_e",
                title = "고객 경험 개선 루틴",
                category = "자영업",
                subCategory = "서비스직",
                goal = "고객 만족도를 높이는 방향으로 운영을 개선하고 싶어",
                desiredOutcome = "후기, 문의, 응대 흐름을 정리한 실무 자료를 만들고 싶어",
                skillLevel = "현장 경험은 많지만 체계적인 개선은 처음 시도해봐",
                recentExperience = "후기 답변 템플릿 없이 그때그때 응대한 적이 많아",
                targetPeriod = "3개월 안에 운영 기준을 만들고 싶어",
                dailyAvailableTime = "하루 1시간 정도 확보 가능해",
                additionalOpinion = "작게 시작해서 실제 운영에 붙는 미션이면 좋겠어",
                expectation = expectation
            )
        )
    }

    private fun educatorContent(): List<ChatRecommendationQaScenario> {
        val expectation = ChatRecommendationQaExpectation(
            todayRequiredGroups = listOf(
                keywords("수업", "학습자료", "커리큘럼", "과제", "피드백", "강의", "학생"),
                keywords("퀴즈", "평가", "정리", "콘텐츠", "학습 목표")
            ),
            nextDayRequiredGroups = listOf(
                keywords("수업", "학습자료", "커리큘럼", "과제", "피드백", "강의", "학생"),
                keywords("퀴즈", "평가", "정리", "콘텐츠", "학습 목표")
            ),
            forbiddenKeywords = keywords("github", "spring", "react", "포트폴리오", "api"),
            avoidUnmentionedFormats = false,
            avoidUnmentionedFrameworks = true
        )

        return listOf(
            scenario(
                id = "41_educator_a",
                title = "수업 자료 고도화",
                category = "직장인",
                subCategory = "교육 종사자",
                goal = "학생들이 따라오기 쉬운 수업 구성을 만들고 싶어",
                desiredOutcome = "강의 자료와 과제 흐름이 정리된 커리큘럼 초안을 만들고 싶어",
                skillLevel = "수업 경험은 있지만 체계적인 자료 설계는 부족해",
                recentExperience = "강의안과 활동지 정도는 만들어봤고 학생 피드백은 구두로만 받았어",
                targetPeriod = "3개월 안에 수업 퀄리티를 끌어올리고 싶어",
                dailyAvailableTime = "하루 1시간 반 정도 가능해",
                additionalOpinion = "학생이 실제로 따라가기 쉬운 방향이면 좋겠어",
                expectation = expectation
            ),
            scenario(
                id = "42_educator_b",
                title = "학습자료 체계화",
                category = "직장인",
                subCategory = "교육 종사자",
                goal = "수업 준비 시간을 줄이면서 내용은 더 좋아지게 하고 싶어",
                desiredOutcome = "학습자료와 피드백 기준이 정리된 틀을 만들고 싶어",
                skillLevel = "강의는 자주 하지만 과제 설계는 늘 즉흥적으로 해왔어",
                recentExperience = "퀴즈, 과제 공지, 수업 자료 정리는 해봤어",
                targetPeriod = "10주 정도 안에 정리하고 싶어",
                dailyAvailableTime = "하루 1시간 정도 가능해",
                additionalOpinion = "문서가 길지 않고 실제 수업에 바로 쓰였으면 좋겠어",
                expectation = expectation
            ),
            scenario(
                id = "43_educator_c",
                title = "커리큘럼 설계 정리",
                category = "직장인",
                subCategory = "교육 종사자",
                goal = "수업 목표와 과제 연결이 더 분명했으면 좋겠어",
                desiredOutcome = "커리큘럼과 평가 요소가 정리된 자료를 만들고 싶어",
                skillLevel = "교육 경력은 있지만 설계 문서는 늘 약했어",
                recentExperience = "강의 계획표, 과제 피드백 메모 정도는 계속 써왔어",
                targetPeriod = "3개월 정도 보고 있어",
                dailyAvailableTime = "하루 1시간 반 정도 가능해",
                additionalOpinion = "학생 입장에서 이해 쉬운 구성으로 이어졌으면 좋겠어",
                expectation = expectation
            ),
            scenario(
                id = "44_educator_d",
                title = "수업 운영 효율화",
                category = "직장인",
                subCategory = "교육 종사자",
                goal = "수업 준비와 피드백 시간을 줄이고 싶어",
                desiredOutcome = "재사용 가능한 수업 자료와 피드백 틀을 만들고 싶어",
                skillLevel = "실무는 익숙하지만 온라인 콘텐츠 설계는 약해",
                recentExperience = "활동지, 간단한 퀴즈, 학생 발표 피드백은 해봤어",
                targetPeriod = "2개월 반 안에 효과를 보고 싶어",
                dailyAvailableTime = "하루 1시간 정도 가능해",
                additionalOpinion = "하루에 하나씩 완성되는 형태가 좋겠어",
                expectation = expectation
            ),
            scenario(
                id = "45_educator_e",
                title = "학생 참여도 개선",
                category = "직장인",
                subCategory = "교육 종사자",
                goal = "학생 참여도가 높아지는 수업 구조를 만들고 싶어",
                desiredOutcome = "활동, 자료, 평가가 연결된 강의 설계 초안을 만들고 싶어",
                skillLevel = "강의 경험은 있지만 설계 기준은 아직 감에 많이 의존해",
                recentExperience = "토론 주제 정리, 퀴즈 제작, 간단한 활동지 작성은 해봤어",
                targetPeriod = "3개월 안에 구조를 바꾸고 싶어",
                dailyAvailableTime = "하루 1시간에서 1시간 반 정도 가능해",
                additionalOpinion = "학생 피드백이 실제로 반영되는 미션이면 좋겠어",
                expectation = expectation
            )
        )
    }

    private fun studentResearch(): List<ChatRecommendationQaScenario> {
        val expectation = ChatRecommendationQaExpectation(
            todayRequiredGroups = listOf(
                keywords("논문", "실험", "데이터", "분석", "가설", "연구", "문헌"),
                keywords("정리", "그래프", "요약", "보고서", "결과")
            ),
            nextDayRequiredGroups = listOf(
                keywords("논문", "실험", "데이터", "분석", "가설", "연구", "문헌"),
                keywords("정리", "그래프", "요약", "보고서", "결과")
            ),
            forbiddenKeywords = keywords("github", "react", "spring", "포트폴리오 사이트", "동영상"),
            avoidUnmentionedFormats = true,
            avoidUnmentionedFrameworks = true
        )

        return listOf(
            scenario(
                id = "46_student_research_a",
                title = "졸업 연구 정리",
                category = "학생",
                subCategory = "연구직",
                goal = "졸업 연구를 더 체계적으로 정리하고 싶어",
                desiredOutcome = "실험 내용과 분석 결과가 보이는 연구 자료를 만들고 싶어",
                skillLevel = "연구는 처음은 아니지만 정리 방식이 늘 들쭉날쭉해",
                recentExperience = "문헌 읽기, 실험 메모, 엑셀 정리 정도는 계속 하고 있어",
                targetPeriod = "3개월 안에 지도교수님께 보여드릴 수준으로 만들고 싶어",
                dailyAvailableTime = "하루 2시간 정도 가능해",
                additionalOpinion = "논문 읽기와 데이터 정리가 같이 이어지면 좋겠어",
                expectation = expectation
            ),
            scenario(
                id = "47_student_research_b",
                title = "논문 기반 연구 정리",
                category = "학생",
                subCategory = "연구직",
                goal = "읽은 논문을 바탕으로 내 연구 방향을 잡고 싶어",
                desiredOutcome = "문헌 요약과 데이터 분석 결과를 정리한 자료를 만들고 싶어",
                skillLevel = "연구실 생활은 익숙하지 않고 아직 배우는 단계야",
                recentExperience = "논문 2~3편 요약과 실험 노트 정리는 해봤어",
                targetPeriod = "4개월 정도 생각하고 있어",
                dailyAvailableTime = "하루 2시간 정도는 투자 가능해",
                additionalOpinion = "실험보다 정리와 해석이 좀 더 필요한 상태야",
                expectation = expectation
            ),
            scenario(
                id = "48_student_research_c",
                title = "데이터 분석 정리",
                category = "학생",
                subCategory = "연구직",
                goal = "연구 데이터를 해석해서 발표 자료로 이어가고 싶어",
                desiredOutcome = "그래프와 결과 요약이 정리된 자료를 만들고 싶어",
                skillLevel = "기초 통계는 알고 있지만 연구 문서화는 아직 익숙하지 않아",
                recentExperience = "실험 결과 표 정리와 간단한 그래프 작성 정도는 해봤어",
                targetPeriod = "2개월 반 정도 안에 중간 발표 준비를 하고 싶어",
                dailyAvailableTime = "하루 1시간 반에서 2시간 정도 가능해",
                additionalOpinion = "하루 안에 닫히는 분석 미션이면 좋겠어",
                expectation = expectation
            ),
            scenario(
                id = "49_student_research_d",
                title = "문헌 조사와 가설 정리",
                category = "학생",
                subCategory = "연구직",
                goal = "연구 가설을 더 분명하게 만들고 싶어",
                desiredOutcome = "문헌 정리와 가설 메모가 연결된 연구 초안을 만들고 싶어",
                skillLevel = "연구 초반 단계고 실험 설계는 아직 미숙해",
                recentExperience = "문헌 목록 만들기와 핵심 문장 정리는 해봤어",
                targetPeriod = "3개월 안에 실험 방향을 잡고 싶어",
                dailyAvailableTime = "하루 2시간 정도 가능해",
                additionalOpinion = "논문 읽고 끝나는 게 아니라 내 연구로 연결됐으면 좋겠어",
                expectation = expectation
            ),
            scenario(
                id = "50_student_research_e",
                title = "연구 결과 보고서 준비",
                category = "학생",
                subCategory = "연구직",
                goal = "실험 결과를 정리해서 보고서에 쓸 수 있게 만들고 싶어",
                desiredOutcome = "결과 요약, 그래프, 해석 문장이 담긴 자료를 만들고 싶어",
                skillLevel = "연구 1년차 정도이고 실험보다 정리가 더 어렵게 느껴져",
                recentExperience = "실험 기록, 데이터 파일 정리, 결과 해석 메모는 조금씩 해왔어",
                targetPeriod = "3개월 안에 보고서 초안을 쓰고 싶어",
                dailyAvailableTime = "하루 2시간 가능해",
                additionalOpinion = "정리와 해석이 균형 있게 이어졌으면 좋겠어",
                expectation = expectation
            )
        )
    }

    private fun scenario(
        id: String,
        title: String,
        category: String,
        subCategory: String,
        goal: String,
        desiredOutcome: String,
        skillLevel: String,
        recentExperience: String,
        targetPeriod: String,
        dailyAvailableTime: String,
        additionalOpinion: String? = null,
        expectation: ChatRecommendationQaExpectation
    ): ChatRecommendationQaScenario {
        return ChatRecommendationQaScenario(
            id = id,
            title = title,
            category = category,
            subCategory = subCategory,
            goal = goal,
            desiredOutcome = desiredOutcome,
            skillLevel = skillLevel,
            recentExperience = recentExperience,
            targetPeriod = targetPeriod,
            dailyAvailableTime = dailyAvailableTime,
            additionalOpinion = additionalOpinion,
            expectation = expectation
        )
    }

    private fun diversify(baseScenarios: List<ChatRecommendationQaScenario>): List<ChatRecommendationQaScenario> {
        val startIndex = baseScenarios.size + 1

        return baseScenarios.mapIndexed { index, scenario ->
            val variantNo = index % 5
            scenario.copy(
                id = "${startIndex + index}_${scenario.id.substringAfter('_')}",
                title = "${scenario.title} 변형",
                goal = diversifyText(scenario.goal, variantNo, "goal"),
                desiredOutcome = diversifyText(scenario.desiredOutcome, variantNo, "outcome"),
                skillLevel = diversifyText(scenario.skillLevel, variantNo, "skill"),
                recentExperience = diversifyText(scenario.recentExperience, variantNo, "recent"),
                targetPeriod = diversifyText(scenario.targetPeriod, variantNo, "period"),
                dailyAvailableTime = diversifyText(scenario.dailyAvailableTime, variantNo, "time"),
                additionalOpinion = scenario.additionalOpinion?.let { diversifyText(it, variantNo, "extra") }
            )
        }
    }

    private fun diversifyText(text: String, variantNo: Int, field: String): String {
        return when (field) {
            "goal" -> when (variantNo) {
                0 -> "요즘에는 ${text}"
                1 -> text.replace("하고 싶어", "해보고 싶어").replace("가고 싶어", "가보고 싶어")
                2 -> "개인적으로 ${text}"
                3 -> text.replace("싶어", "목표야")
                else -> "일단은 ${text}"
            }

            "outcome" -> when (variantNo) {
                0 -> "최종적으로는 ${text}"
                1 -> text.replace("만들고 싶어", "만들어두고 싶어").replace("정리하고 싶어", "정리해두고 싶어")
                2 -> "결과적으로 ${text}"
                3 -> text.replace("싶어", "바라고 있어")
                else -> "가능하면 ${text}"
            }

            "skill" -> when (variantNo) {
                0 -> "솔직히 ${text}"
                1 -> text.replace("있어", "있는 편이야").replace("없어", "없는 편이야")
                2 -> "체감상 ${text}"
                3 -> text.replace("정도", "정도쯤")
                else -> "지금 기준으로는 ${text}"
            }

            "recent" -> when (variantNo) {
                0 -> "실제로는 ${text}"
                1 -> text.replace("해봤어", "건드려본 적 있어").replace("했어", "해본 편이야")
                2 -> "최근에는 ${text}"
                3 -> text.replace("정도는", "정도는 일단")
                else -> "돌이켜보면 ${text}"
            }

            "period" -> when (variantNo) {
                0 -> "대략 ${text}"
                1 -> text.replace("안에", "정도 안에")
                2 -> "가능하면 ${text}"
                3 -> text.replace("정도", "남짓")
                else -> "늦어도 ${text}"
            }

            "time" -> when (variantNo) {
                0 -> text.replace("하루", "평일 기준 하루")
                1 -> text.replace("정도", "남짓")
                2 -> "보통 ${text}"
                3 -> text.replace("가능해", "가능한 편이야").replace("투자할 수 있어", "쓸 수 있어")
                else -> "현실적으로 ${text}"
            }

            "extra" -> when (variantNo) {
                0 -> "가능하면 $text"
                1 -> text.replace("좋겠어", "좋겠어 진짜")
                2 -> "개인적으로는 $text"
                3 -> text.replace("원해", "바라고 있어")
                else -> "가능하면 $text"
            }

            else -> text
        }
    }

    private fun keywords(vararg values: String): Set<String> = values.map { it.lowercase() }.toSet()
}
