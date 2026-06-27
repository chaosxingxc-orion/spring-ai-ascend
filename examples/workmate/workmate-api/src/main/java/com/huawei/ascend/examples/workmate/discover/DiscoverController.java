package com.huawei.ascend.examples.workmate.discover;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/discover")
public class DiscoverController {

    private final DiscoverService discoverService;

    public DiscoverController(DiscoverService discoverService) {
        this.discoverService = discoverService;
    }

    @GetMapping
    public List<DiscoverResource> list(@RequestParam(defaultValue = "false") boolean favoritesOnly) {
        return discoverService.list(favoritesOnly);
    }

    @GetMapping("/featured")
    public List<DiscoverResource> featured() {
        return discoverService.listFeatured();
    }

    @PostMapping("/{type}/{id}/favorite")
    public DiscoverResource favorite(
            @PathVariable String type,
            @PathVariable String id,
            @RequestParam(defaultValue = "true") boolean favorite) {
        return discoverService.toggleFavorite(type, id, favorite);
    }

    @PostMapping("/launch")
    public DiscoverLaunchResponse launch(@RequestBody DiscoverLaunchRequest request) {
        return discoverService.launch(request);
    }
}
