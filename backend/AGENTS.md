# Home_IOT Backend Agent Playbook

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 0) 작업 경로
- Backend workspace: `C:\Users\honeybadger\Desktop\Home_IOT\backend`
- Spring Boot root: `C:\Users\honeybadger\Desktop\Home_IOT\backend\server`
- Frontend root: `C:\Users\honeybadger\Desktop\Home_IOT\client\api-client-ui`

## 1) 기본 목표
- 홈 IoT 도메인에 맞는 장치 중심 백엔드를 안전하게 구현한다.
- 핵심 책임은 장치 등록, 상태 저장, 명령 처리, MQTT 연동, OTA 기록이다.

## 2) 작업 원칙
- 장치/상태/명령/telemetry/ota 책임을 분리한다.
- 실시간 명령은 MQTT, 등록/조회/이력은 REST 우선으로 둔다.
- 계약은 `snake_case` JSON을 기본으로 한다.
- 장치 명령은 수신만으로 완료 처리하지 않고, reported state 반영 후 완료 처리한다.

## 3) 완료 기준
- 요청 동작이 구현된다.
- `server`에서 빌드가 성공한다.
- 영향 문서가 함께 갱신된다.
- API/DB/설정/배포/문서 영향표를 보고한다.
