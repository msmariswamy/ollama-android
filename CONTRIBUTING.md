## Testing

### TDD Workflow
1. Write a failing test that captures the expected behavior
2. Implement the minimal production code to make the test pass
3. Refactor if needed, keeping tests green

### Run JVM Unit Tests
```bash
./gradlew test
```
Tests run on the JVM without a device. Covers: model POJOs, network utilities, repositories, and ViewModels.

### Run Instrumented Tests (DAO)
```bash
./gradlew connectedAndroidTest
```
Requires a connected Android device or running emulator. Tests Room DAO operations using an in-memory database.
