INSERT INTO member_mission
(member_id, mission_id, is_completed, mission_status, exp_earned, target_date, deleted, created_at, updated_at, mission_level)
VALUES (1, 101, false, 'ACTIVE', 20, CURRENT_DATE, false, NOW(), NOW(), 1);

INSERT INTO member_mission
(member_id, mission_id, is_completed, mission_status, exp_earned, target_date, deleted, created_at, updated_at, mission_level)
VALUES (1, 102, false, 'ACTIVE', 15, CURRENT_DATE, false, NOW(), NOW(), 2);

INSERT INTO member_mission
(member_id, mission_id, is_completed, mission_status, exp_earned, target_date, deleted, created_at, updated_at, mission_level)
VALUES (1, 103, false, 'ACTIVE', 10, CURRENT_DATE, false, NOW(), NOW(), 3);

INSERT INTO member_mission
(member_id, mission_id, is_completed, mission_status, exp_earned, target_date, deleted, created_at, updated_at, mission_level)
VALUES (1, 104, false, 'ACTIVE', 30, CURRENT_DATE, false, NOW(), NOW(), 4);

INSERT INTO member_mission
(member_id, mission_id, is_completed, mission_status, exp_earned, target_date, deleted, created_at, updated_at, mission_level)
VALUES (1, 105, false, 'ACTIVE', 5, CURRENT_DATE, false, NOW(), NOW(), 5);


INSERT INTO member_mission
(member_id, mission_id, is_completed, mission_status, exp_earned, target_date, deleted, created_at, updated_at, mission_level)
VALUES (1, 201, false, 'POSTPONED', 0, CURRENT_DATE - INTERVAL '1 day', false, NOW(), NOW(), 1);

INSERT INTO member_mission
(member_id, mission_id, is_completed, mission_status, exp_earned, target_date, deleted, created_at, updated_at, mission_level)
VALUES (1, 202, false, 'POSTPONED', 0, CURRENT_DATE - INTERVAL '1 day', false, NOW(), NOW(),3);
