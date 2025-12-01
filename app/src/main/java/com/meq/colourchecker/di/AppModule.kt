package com.meq.colourchecker.di

import com.meq.colourchecker.domain.DetectionUseCase
import com.meq.colourchecker.processing.ColorCheckerDetector
import com.meq.colourchecker.processing.OpenCvColorCheckerDetector
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DetectorModule {
    @Binds
    @Singleton
    abstract fun bindColorCheckerDetector(
        impl: OpenCvColorCheckerDetector
    ): ColorCheckerDetector
}

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {
    @Provides
    @Singleton
    fun provideDetectionUseCase(
        detector: ColorCheckerDetector,
        logger: com.meq.colourchecker.util.Logger
    ): DetectionUseCase = DetectionUseCase(detector, logger)

    @Provides
    @Singleton
    fun provideColorCheckerLocator(logger: com.meq.colourchecker.util.Logger) =
        com.meq.colourchecker.processing.ColorCheckerLocator(logger)

    @Provides
    @Singleton
    fun provideImageQualityAnalyzer() = com.meq.colourchecker.processing.ImageQualityAnalyzer()

    @Provides
    @Singleton
    fun providePatchAnalyzer() = com.meq.colourchecker.processing.PatchAnalyzer()

    @Provides
    @Singleton
    fun provideDetectionScorer() =
        com.meq.colourchecker.processing.DetectionScorer()
}
