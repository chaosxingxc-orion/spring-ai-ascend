package com.huawei.ascend.examples.workmate.office;

import com.huawei.ascend.examples.workmate.config.WorkmateOfficeProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

@Component
public class WelcomeRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(WelcomeRegistry.class);

    private final PlaybookRegistry playbookRegistry;
    private final OfficeImportPaths importPaths;
    private final Path officeRoot;
    private volatile OfficeAssetSource loadedSource = OfficeAssetSource.BUILTIN;
    private volatile Path loadedPath;
    private WelcomeDocument document = WelcomeDocument.empty();

    public WelcomeRegistry(
            WorkmateOfficeProperties officeProperties,
            PlaybookRegistry playbookRegistry,
            OfficeImportPaths importPaths) {
        this.playbookRegistry = playbookRegistry;
        this.importPaths = importPaths;
        this.officeRoot = officeProperties.resolvedRoot();
        reloadAll();
    }

    public void reloadAll() {
        Path draftFile = importPaths.welcomeDraftFile();
        if (Files.isRegularFile(draftFile)) {
            loadFromFile(draftFile, OfficeAssetSource.DRAFT);
            return;
        }
        loadFromFile(officeRoot.resolve("welcome.yaml"), OfficeAssetSource.BUILTIN);
    }

    public OfficeAssetSource loadedSource() {
        return loadedSource;
    }

    public Path loadedPath() {
        return loadedPath;
    }

    public Path officeRoot() {
        return officeRoot;
    }

    private void loadFromFile(Path welcomeFile, OfficeAssetSource source) {
        if (!Files.isRegularFile(welcomeFile)) {
            LOG.warn("Office welcome.yaml not found: {}", welcomeFile);
            document = WelcomeDocument.empty();
            loadedSource = OfficeAssetSource.BUILTIN;
            loadedPath = officeRoot.resolve("welcome.yaml");
            return;
        }
        try (InputStream input = Files.newInputStream(welcomeFile)) {
            Yaml yaml = SafeYaml.loader();
            Object loaded = yaml.load(input);
            if (!(loaded instanceof Map<?, ?> raw)) {
                LOG.warn("Skip invalid welcome.yaml (not a map): {}", welcomeFile);
                document = WelcomeDocument.empty();
                loadedSource = source;
                loadedPath = welcomeFile;
                return;
            }
            document = parseWelcome(OfficeYaml.applyProfile(raw));
            loadedSource = source;
            loadedPath = welcomeFile;
            LOG.info("Loaded welcome config from {} ({})", welcomeFile, source);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load welcome.yaml from " + welcomeFile, ex);
        }
    }

    public WelcomeDocument parseWelcomeYaml(String yamlText) {
        if (yamlText == null || yamlText.isBlank()) {
            throw new IllegalArgumentException("welcome.yaml content required");
        }
        Object loaded = SafeYaml.loader().load(yamlText);
        if (!(loaded instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException("welcome.yaml must be a YAML map");
        }
        return parseWelcome(OfficeYaml.applyProfile(raw));
    }

    void reload(Path officeRoot) {
        reloadAll();
    }

    public WelcomeDocument document() {
        return document;
    }

    public List<PlaybookDefinition> playbooksForPlacement(String placement) {
        return playbookRegistry.listByPlacement(placement);
    }

    private WelcomeDocument parseWelcome(Map<?, ?> raw) {
        Map<?, ?> hero = mapField(raw, "hero");
        Map<?, ?> dock = mapField(raw, "dock");
        Map<?, ?> growthPlan = mapField(raw, "growthPlan");
        Map<?, ?> bestPractices = mapField(raw, "bestPractices");
        Map<?, ?> marketFeatured = mapField(raw, "marketFeatured");
        Map<?, ?> homeFeatured = mapField(raw, "homeFeatured");
        Map<?, ?> market = mapField(raw, "market");
        Map<?, ?> onboarding = mapField(raw, "onboarding");

        String bestPlacement = OfficeYaml.stringField(bestPractices, "placement");
        String featuredPlacement = OfficeYaml.stringField(marketFeatured, "placement");
        boolean bestEnabled = OfficeYaml.booleanField(bestPractices, "enabled", true);
        boolean homeFeaturedEnabled = OfficeYaml.booleanField(homeFeatured, "enabled", false);
        boolean marketFeaturedEnabled = OfficeYaml.booleanField(marketFeatured, "enabled", false);

        return new WelcomeDocument(
                new WelcomeHero(
                        OfficeYaml.stringField(hero, "headline"),
                        OfficeYaml.stringField(hero, "title"),
                        OfficeYaml.stringField(hero, "tagline")),
                new WelcomeDock(
                        OfficeYaml.stringField(dock, "placeholderNew"),
                        OfficeYaml.stringField(dock, "placeholderSession")),
                new WelcomeGrowthPlan(
                        OfficeYaml.stringField(growthPlan, "label"),
                        OfficeYaml.booleanField(growthPlan, "enabled", false)),
                new WelcomeSection(
                        OfficeYaml.stringField(bestPractices, "title"),
                        OfficeYaml.stringField(bestPractices, "moreLabel"),
                        bestPlacement == null || bestPlacement.isBlank() ? "home-best-practice" : bestPlacement,
                        bestEnabled,
                        bestEnabled
                                ? playbooksForPlacement(
                                        bestPlacement == null || bestPlacement.isBlank()
                                                ? "home-best-practice"
                                                : bestPlacement)
                                : List.of()),
                new WelcomeSection(
                        OfficeYaml.stringField(marketFeatured, "title"),
                        OfficeYaml.stringField(marketFeatured, "viewAllLabel"),
                        featuredPlacement == null || featuredPlacement.isBlank() ? "market-featured" : featuredPlacement,
                        marketFeaturedEnabled,
                        marketFeaturedEnabled
                                ? playbooksForPlacement(
                                        featuredPlacement == null || featuredPlacement.isBlank()
                                                ? "market-featured"
                                                : featuredPlacement)
                                : List.of()),
                new WelcomeHomeFeatured(homeFeaturedEnabled),
                OfficeYaml.stringField(market, "searchPlaceholder"),
                parseScenes(raw),
                parseOnboarding(onboarding));
    }

    private WelcomeOnboarding parseOnboarding(Map<?, ?> raw) {
        if (raw.isEmpty()) {
            return WelcomeOnboarding.disabled();
        }
        return new WelcomeOnboarding(
                OfficeYaml.booleanField(raw, "enabled", false),
                OfficeYaml.stringField(raw, "step1Title"),
                OfficeYaml.stringField(raw, "step1Hint"),
                OfficeYaml.stringField(raw, "step2Title"),
                OfficeYaml.stringField(raw, "step2Hint"),
                OfficeYaml.stringField(raw, "step3Title"),
                OfficeYaml.stringField(raw, "step3Hint"),
                parseInterestTags(raw),
                parseSampleTasks(raw));
    }

    @SuppressWarnings("unchecked")
    private List<WelcomeInterestTag> parseInterestTags(Map<?, ?> raw) {
        Object value = raw.get("interests");
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<WelcomeInterestTag> tags = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> tagRaw)) {
                continue;
            }
            String id = OfficeYaml.stringField(tagRaw, "id");
            String label = OfficeYaml.stringField(tagRaw, "label");
            if (id == null || id.isBlank() || label == null || label.isBlank()) {
                continue;
            }
            tags.add(new WelcomeInterestTag(id, label));
        }
        return tags;
    }

    @SuppressWarnings("unchecked")
    private List<WelcomeSampleTask> parseSampleTasks(Map<?, ?> raw) {
        Object value = raw.get("sampleTasks");
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<WelcomeSampleTask> tasks = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> taskRaw)) {
                continue;
            }
            String id = OfficeYaml.stringField(taskRaw, "id");
            String title = OfficeYaml.stringField(taskRaw, "title");
            String prompt = OfficeYaml.stringField(taskRaw, "prompt");
            if (id == null || id.isBlank() || title == null || title.isBlank() || prompt == null || prompt.isBlank()) {
                continue;
            }
            tasks.add(new WelcomeSampleTask(
                    id,
                    title,
                    prompt.trim(),
                    OfficeYaml.stringField(taskRaw, "expertId")));
        }
        return tasks;
    }

  @SuppressWarnings("unchecked")
    private List<WelcomeScene> parseScenes(Map<?, ?> raw) {
        Object value = raw.get("scenes");
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<WelcomeScene> scenes = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> sceneRaw)) {
                continue;
            }
            String id = OfficeYaml.stringField(sceneRaw, "id");
            String label = OfficeYaml.stringField(sceneRaw, "label");
            if (id == null || id.isBlank() || label == null || label.isBlank()) {
                continue;
            }
            scenes.add(new WelcomeScene(
                    id,
                    label,
                    OfficeYaml.stringField(sceneRaw, "icon"),
                    OfficeYaml.booleanField(sceneRaw, "default", false),
                    parseChips(sceneRaw)));
        }
        return scenes;
    }

    @SuppressWarnings("unchecked")
    private List<WelcomeChip> parseChips(Map<?, ?> sceneRaw) {
        Object value = sceneRaw.get("chips");
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<WelcomeChip> chips = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> chipRaw)) {
                continue;
            }
            String label = OfficeYaml.stringField(chipRaw, "label");
            if (label == null || label.isBlank()) {
                continue;
            }
            chips.add(new WelcomeChip(
                    label,
                    OfficeYaml.stringField(chipRaw, "icon"),
                    OfficeYaml.stringField(chipRaw, "initPrompt")));
        }
        return chips;
    }

    @SuppressWarnings("unchecked")
    private static Map<?, ?> mapField(Map<?, ?> raw, String key) {
        Object value = raw.get(key);
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        return Map.of();
    }

    public record WelcomeDocument(
            WelcomeHero hero,
            WelcomeDock dock,
            WelcomeGrowthPlan growthPlan,
            WelcomeSection bestPractices,
            WelcomeSection marketFeatured,
            WelcomeHomeFeatured homeFeatured,
            String marketSearchPlaceholder,
            List<WelcomeScene> scenes,
            WelcomeOnboarding onboarding) {

        static WelcomeDocument empty() {
            return new WelcomeDocument(
                    new WelcomeHero(null, null, null),
                    new WelcomeDock(null, null),
                    new WelcomeGrowthPlan(null, false),
                    new WelcomeSection(null, null, "home-best-practice", false, List.of()),
                    new WelcomeSection(null, null, "market-featured", true, List.of()),
                    new WelcomeHomeFeatured(false),
                    null,
                    List.of(),
                    WelcomeOnboarding.disabled());
        }
    }

    public record WelcomeHero(String headline, String title, String tagline) {}

    public record WelcomeDock(String placeholderNew, String placeholderSession) {}

    public record WelcomeGrowthPlan(String label, boolean enabled) {}

    public record WelcomeSection(
            String title,
            String actionLabel,
            String placement,
            boolean enabled,
            List<PlaybookDefinition> playbooks) {}

    public record WelcomeHomeFeatured(boolean enabled) {}

    public record WelcomeScene(String id, String label, String icon, boolean defaultScene, List<WelcomeChip> chips) {}

    public record WelcomeChip(String label, String icon, String initPrompt) {}

    public record WelcomeInterestTag(String id, String label) {}

    public record WelcomeSampleTask(String id, String title, String prompt, String expertId) {}

    public record WelcomeOnboarding(
            boolean enabled,
            String step1Title,
            String step1Hint,
            String step2Title,
            String step2Hint,
            String step3Title,
            String step3Hint,
            List<WelcomeInterestTag> interests,
            List<WelcomeSampleTask> sampleTasks) {

        static WelcomeOnboarding disabled() {
            return new WelcomeOnboarding(false, null, null, null, null, null, null, List.of(), List.of());
        }
    }
}
