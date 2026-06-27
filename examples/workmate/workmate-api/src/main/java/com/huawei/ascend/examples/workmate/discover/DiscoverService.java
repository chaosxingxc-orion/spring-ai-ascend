package com.huawei.ascend.examples.workmate.discover;

import com.huawei.ascend.examples.workmate.office.ExpertRegistry;
import com.huawei.ascend.examples.workmate.office.PlaybookDefinition;
import com.huawei.ascend.examples.workmate.office.PlaybookRegistry;
import com.huawei.ascend.examples.workmate.office.SkillRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DiscoverService {

    private final DiscoverStore store;
    private final PlaybookRegistry playbookRegistry;
    private final ExpertRegistry expertRegistry;
    private final SkillRegistry skillRegistry;

    public DiscoverService(
            DiscoverStore store,
            PlaybookRegistry playbookRegistry,
            ExpertRegistry expertRegistry,
            SkillRegistry skillRegistry) {
        this.store = store;
        this.playbookRegistry = playbookRegistry;
        this.expertRegistry = expertRegistry;
        this.skillRegistry = skillRegistry;
    }

    public List<DiscoverResource> list(boolean favoritesOnly) {
        List<DiscoverResource> resources = new ArrayList<>();
        for (String key : store.favorites()) {
            toResource(key).ifPresent(resources::add);
        }
        for (DiscoverStore.UsedEntry entry : store.used()) {
            toResource(entry.key())
                    .map(resource -> new DiscoverResource(
                            resource.type(),
                            resource.id(),
                            resource.title(),
                            resource.subtitle(),
                            entry.lastUsedAt(),
                            store.favorites().contains(entry.key())))
                    .ifPresent(candidate -> {
                        if (resources.stream().noneMatch(item -> itemKey(item).equals(entry.key()))) {
                            resources.add(candidate);
                        }
                    });
        }
        if (favoritesOnly) {
            return resources.stream().filter(DiscoverResource::favorite).toList();
        }
        resources.sort(Comparator.comparing(DiscoverResource::lastUsedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return resources;
    }

    public List<DiscoverResource> listFeatured() {
        List<DiscoverResource> featured = new ArrayList<>();
        for (PlaybookDefinition playbook : playbookRegistry.listByPlacement("market-featured")) {
            String key = key("playbook", playbook.id());
            featured.add(new DiscoverResource(
                    "playbook",
                    playbook.id(),
                    playbook.title(),
                    playbook.description(),
                    null,
                    store.favorites().contains(key)));
        }
        return featured;
    }

    public DiscoverResource toggleFavorite(String type, String id, boolean favorite) {
        String key = key(type, id);
        store.toggleFavorite(key, favorite);
        return toResource(key)
                .map(resource -> new DiscoverResource(
                        resource.type(),
                        resource.id(),
                        resource.title(),
                        resource.subtitle(),
                        resource.lastUsedAt(),
                        favorite))
                .orElseThrow(() -> new IllegalArgumentException("Unknown discover resource: " + key));
    }

    public DiscoverLaunchResponse launch(DiscoverLaunchRequest request) {
        DiscoverLaunchResponse response = resolveLaunch(request.type(), request.id());
        store.recordUsed(key(request.type(), request.id()), response.title(), response.type());
        return response;
    }

    private DiscoverLaunchResponse resolveLaunch(String type, String id) {
        return switch (type) {
            case "playbook" -> playbookRegistry.findPlaybook(id)
                    .map(this::fromPlaybook)
                    .orElseThrow(() -> new IllegalArgumentException("Playbook not found: " + id));
            case "expert" -> expertRegistry.findExpert(id)
                    .map(expert -> new DiscoverLaunchResponse(
                            "expert",
                            expert.id(),
                            expert.name(),
                            expert.defaultInitPrompt() == null ? "" : expert.defaultInitPrompt(),
                            expert.id()))
                    .orElseThrow(() -> new IllegalArgumentException("Expert not found: " + id));
            case "skill" -> skillRegistry.findSkill(id)
                    .map(skill -> new DiscoverLaunchResponse(
                            "skill",
                            skill.id(),
                            skill.name(),
                            "使用技能 /" + skill.id(),
                            null))
                    .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + id));
            default -> throw new IllegalArgumentException("Unsupported discover type: " + type);
        };
    }

    private DiscoverLaunchResponse fromPlaybook(PlaybookDefinition playbook) {
        return new DiscoverLaunchResponse(
                "playbook",
                playbook.id(),
                playbook.title(),
                playbook.initPrompt(),
                playbook.expertId());
    }

    private java.util.Optional<DiscoverResource> toResource(String key) {
        int split = key.indexOf(':');
        if (split <= 0) {
            return java.util.Optional.empty();
        }
        String type = key.substring(0, split);
        String id = key.substring(split + 1);
        try {
            DiscoverLaunchResponse launch = resolveLaunch(type, id);
            return java.util.Optional.of(new DiscoverResource(
                    launch.type(),
                    launch.id(),
                    launch.title(),
                    launch.type(),
                    null,
                    store.favorites().contains(key)));
        } catch (IllegalArgumentException ex) {
            return java.util.Optional.empty();
        }
    }

    private static String key(String type, String id) {
        return type + ":" + id;
    }

    private static String itemKey(DiscoverResource resource) {
        return key(resource.type(), resource.id());
    }
}
