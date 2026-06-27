package com.huawei.ascend.examples.workmate.office.dto;

import com.huawei.ascend.examples.workmate.office.PlaybookDefinition;
import com.huawei.ascend.examples.workmate.office.WelcomeRegistry;
import java.util.List;

public record WelcomeResponse(
        WelcomeHeroResponse hero,
        WelcomeDockResponse dock,
        WelcomeGrowthPlanResponse growthPlan,
        WelcomeSectionResponse bestPractices,
        WelcomeSectionResponse marketFeatured,
        WelcomeHomeFeaturedResponse homeFeatured,
        String marketSearchPlaceholder,
        List<WelcomeSceneResponse> scenes,
        WelcomeOnboardingResponse onboarding) {

    public static WelcomeResponse from(WelcomeRegistry.WelcomeDocument document) {
        return new WelcomeResponse(
                WelcomeHeroResponse.from(document.hero()),
                WelcomeDockResponse.from(document.dock()),
                WelcomeGrowthPlanResponse.from(document.growthPlan()),
                WelcomeSectionResponse.from(document.bestPractices()),
                WelcomeSectionResponse.from(document.marketFeatured()),
                WelcomeHomeFeaturedResponse.from(document.homeFeatured()),
                document.marketSearchPlaceholder(),
                document.scenes().stream().map(WelcomeSceneResponse::from).toList(),
                WelcomeOnboardingResponse.from(document.onboarding()));
    }

    public record WelcomeHeroResponse(String headline, String title, String tagline) {
        static WelcomeHeroResponse from(WelcomeRegistry.WelcomeHero hero) {
            return new WelcomeHeroResponse(hero.headline(), hero.title(), hero.tagline());
        }
    }

    public record WelcomeDockResponse(String placeholderNew, String placeholderSession) {
        static WelcomeDockResponse from(WelcomeRegistry.WelcomeDock dock) {
            return new WelcomeDockResponse(dock.placeholderNew(), dock.placeholderSession());
        }
    }

    public record WelcomeGrowthPlanResponse(String label, boolean enabled) {
        static WelcomeGrowthPlanResponse from(WelcomeRegistry.WelcomeGrowthPlan growthPlan) {
            return new WelcomeGrowthPlanResponse(growthPlan.label(), growthPlan.enabled());
        }
    }

    public record WelcomeSectionResponse(
            String title,
            String actionLabel,
            String placement,
            boolean enabled,
            List<PlaybookResponse> playbooks) {

        static WelcomeSectionResponse from(WelcomeRegistry.WelcomeSection section) {
            return new WelcomeSectionResponse(
                    section.title(),
                    section.actionLabel(),
                    section.placement(),
                    section.enabled(),
                    section.playbooks().stream().map(PlaybookResponse::from).toList());
        }
    }

    public record WelcomeHomeFeaturedResponse(boolean enabled) {
        static WelcomeHomeFeaturedResponse from(WelcomeRegistry.WelcomeHomeFeatured homeFeatured) {
            return new WelcomeHomeFeaturedResponse(homeFeatured.enabled());
        }
    }

    public record WelcomeSceneResponse(
            String id,
            String label,
            String icon,
            boolean defaultScene,
            List<WelcomeChipResponse> chips) {

        static WelcomeSceneResponse from(WelcomeRegistry.WelcomeScene scene) {
            return new WelcomeSceneResponse(
                    scene.id(),
                    scene.label(),
                    scene.icon(),
                    scene.defaultScene(),
                    scene.chips().stream().map(WelcomeChipResponse::from).toList());
        }
    }

    public record WelcomeChipResponse(String label, String icon, String initPrompt) {
        static WelcomeChipResponse from(WelcomeRegistry.WelcomeChip chip) {
            return new WelcomeChipResponse(chip.label(), chip.icon(), chip.initPrompt());
        }
    }

    public record WelcomeOnboardingResponse(
            boolean enabled,
            String step1Title,
            String step1Hint,
            String step2Title,
            String step2Hint,
            String step3Title,
            String step3Hint,
            List<WelcomeInterestTagResponse> interests,
            List<WelcomeSampleTaskResponse> sampleTasks) {

        static WelcomeOnboardingResponse from(WelcomeRegistry.WelcomeOnboarding onboarding) {
            return new WelcomeOnboardingResponse(
                    onboarding.enabled(),
                    onboarding.step1Title(),
                    onboarding.step1Hint(),
                    onboarding.step2Title(),
                    onboarding.step2Hint(),
                    onboarding.step3Title(),
                    onboarding.step3Hint(),
                    onboarding.interests().stream().map(WelcomeInterestTagResponse::from).toList(),
                    onboarding.sampleTasks().stream().map(WelcomeSampleTaskResponse::from).toList());
        }
    }

    public record WelcomeInterestTagResponse(String id, String label) {
        static WelcomeInterestTagResponse from(WelcomeRegistry.WelcomeInterestTag tag) {
            return new WelcomeInterestTagResponse(tag.id(), tag.label());
        }
    }

    public record WelcomeSampleTaskResponse(String id, String title, String prompt, String expertId) {
        static WelcomeSampleTaskResponse from(WelcomeRegistry.WelcomeSampleTask task) {
            return new WelcomeSampleTaskResponse(task.id(), task.title(), task.prompt(), task.expertId());
        }
    }
}
