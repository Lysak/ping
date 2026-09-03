# Thin task runner over Gradle / adb. Gradle stays the build system.
APP_ID := com.lysak.ping
MAIN_ACTIVITY := $(APP_ID)/.MainActivity

# Resolve a JDK 17+ when the calling shell has none (agent hooks, bare terminals).
# Order: caller's JAVA_HOME -> macOS java_home -> Android Studio JBR -> Homebrew.
ifeq ($(strip $(JAVA_HOME)),)
JAVA_HOME := $(shell \
	/usr/libexec/java_home -v 17 2>/dev/null \
	|| ([ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ] \
		&& echo "/Applications/Android Studio.app/Contents/jbr/Contents/Home") \
	|| ([ -d "$(HOME)/.sdkman/candidates/java/current" ] && echo "$(HOME)/.sdkman/candidates/java/current") \
	|| ([ -d /opt/homebrew/opt/openjdk@17 ] && echo /opt/homebrew/opt/openjdk@17) \
	|| true)
endif
export JAVA_HOME

GRADLE := ./gradlew

.DEFAULT_GOAL := help
.PHONY: help build assemble install run uninstall test itest gate check verify \
        format lint lint-android detekt clean logcat devices

help: ## Show this help
	@grep -hE '^[a-z-]+:.*?## ' $(MAKEFILE_LIST) | awk -F':.*?## ' '{printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

build: assemble ## Alias for assemble

assemble: ## Build the debug APK
	$(GRADLE) assembleDebug

install: ## Install the debug build on the connected device
	$(GRADLE) installDebug

run: install ## Install and launch the app
	adb shell am start -n $(MAIN_ACTIVITY)

uninstall: ## Remove the app from the device
	adb uninstall $(APP_ID) || true

test: ## Run JVM unit tests (incl. Konsist architecture guards)
	$(GRADLE) testDebugUnitTest -PwithArchTest

itest: ## Run instrumented tests (needs a device/emulator)
	$(GRADLE) connectedDebugAndroidTest

detekt: ## Static analysis (detekt + Compose rules)
	$(GRADLE) detekt

lint-android: ## Android Lint (insets, battery, a11y, API levels)
	$(GRADLE) lintDebug

gate: ## Fast quality gate: apply formatting, then detekt + unit tests (no Konsist). Used by the agent Stop hook.
	$(GRADLE) spotlessApply detekt testDebugUnitTest

check: gate ## Alias for the fast gate

verify: ## Full gate, NO auto-fix: formatting check + detekt + unit tests + Konsist + Android Lint.
	$(GRADLE) spotlessCheck detekt testDebugUnitTest lintDebug -PwithArchTest

format: ## Apply spotless (ktlint) formatting
	$(GRADLE) spotlessApply

lint: ## Verify formatting without changing files
	$(GRADLE) spotlessCheck

clean: ## Clean Gradle build outputs
	$(GRADLE) clean

logcat: ## Tail logcat for this app only
	adb logcat --pid=$$(adb shell pidof -s $(APP_ID))

devices: ## List connected devices
	adb devices -l
