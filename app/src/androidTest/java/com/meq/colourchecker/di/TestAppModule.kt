package com.meq.colourchecker.di

import com.meq.colourchecker.processing.AnalysisFrame
import com.meq.colourchecker.processing.ColorCheckerDetector
import com.meq.colourchecker.processing.DetectorResult
import com.meq.colourchecker.processing.DetectionOutput
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * Test module that replaces the production detector module.
 * Provides a fake detector implementation for testing.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DetectorModule::class]
)
object TestDetectorModule {

    @Provides
    @Singleton
    fun provideColorCheckerDetector(): ColorCheckerDetector {
        return FakeColorCheckerDetectorForAndroidTests()
    }

}

/**
 * Simple fake detector for Android instrumentation tests.
 * Always returns a passing result for testing UI flows.
 */
class FakeColorCheckerDetectorForAndroidTests : ColorCheckerDetector {
    override suspend fun detect(frame: AnalysisFrame): DetectionOutput {
        return DetectionOutput(
            result = DetectorResult(
                confidence = 0.95f,
                failureReason = null,
                needsInput = false
            ),
            metrics = null
        )
    }
}
